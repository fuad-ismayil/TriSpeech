import crypto from "node:crypto";
import { execFile } from "node:child_process";
import { promisify } from "node:util";
import express from "express";
import ffprobeStatic from "ffprobe-static";
import { CloudTasksClient } from "@google-cloud/tasks";
import { authMiddleware, httpError, verifyAppCheck, verifyRegistrationRequest } from "./auth.js";
import { loadTokenMixKeys } from "./config.js";
import { marketReward } from "./economics.js";
import { AmbiguousProviderError, ProviderPool } from "./provider.js";
import { createStore, StoreError } from "./store.js";
import { UploadStorage } from "./storage.js";
import { verifyAdMobSsv } from "./ssv.js";

const app = express();
app.disable("x-powered-by");
const store = createStore();
const storage = new UploadStorage();
const authenticate = authMiddleware(store, { requireAppCheck: process.env.REQUIRE_APP_CHECK === "true" });
const execFileAsync = promisify(execFile);
const ffprobeExecutable = typeof ffprobeStatic === "string" ? ffprobeStatic : ffprobeStatic?.path;
if (typeof ffprobeExecutable !== "string" || !ffprobeExecutable) {
  throw new Error("ffprobe-static did not provide an executable path.");
}
const onlineEnabled = () => process.env.ONLINE_SERVICE_ENABLED !== "false";
const developmentAccessMode = process.env.DEVELOPMENT_ACCESS_MODE === "true";
const provider = new ProviderPool({
  endpoint: process.env.TOKENMIX_ENDPOINT ?? "https://api.tokenmix.ai/v1/audio/transcriptions",
  keys: loadTokenMixKeys()
});
const economics = parseJson(process.env.MARKET_ECONOMICS_JSON, { ZZ: { conservativeEcpm: 2.34, allInMinuteCost: 0.0035 } });
const targetMargin = Number(process.env.TARGET_MARGIN ?? 0.25);
const defaultMarket = String(process.env.DEFAULT_MARKET ?? "ZZ").toUpperCase();
const trustedMarketHeader = process.env.TRUSTED_MARKET_HEADER;
const activeDispatches = new Set();
const maxConcurrency = Math.min(20, Math.max(1, Number(process.env.PROVIDER_MAX_CONCURRENCY ?? 20)));
const providerDailyBudget = Math.max(0, Number(process.env.PROVIDER_DAILY_BUDGET_USD ?? 10));
const providerMinuteCost = Math.max(0, Number(process.env.PROVIDER_MINUTE_COST_USD ?? 0.0035));

app.get("/healthz", (_req, res) => res.json({ ok: true }));

app.get("/callbacks/admob/ssv", async (req, res, next) => {
  try {
    const verified = await verifyAdMobSsv(req.originalUrl);
    await store.grantAdReward(verified.sessionId, verified.transactionId);
    res.status(200).send("ok");
  } catch (error) { next(error); }
});

app.put(
  "/v1/uploads/:id/content",
  express.raw({ type: () => true, limit: "25mb", verify: (req, _res, buffer) => { req.rawBody = Buffer.from(buffer); } }),
  authenticate,
  async (req, res, next) => {
    try {
      const upload = await store.upload(req.params.id);
      if (!upload || upload.installationId !== req.installationId || upload.complete) throw httpError("Upload not found.", 404);
      if (!Buffer.isBuffer(req.body) || req.body.length !== upload.sizeBytes) throw httpError("Upload size mismatch.", 400);
      await storage.write(upload.id, req.body);
      await store.markUploadStored(upload.id, req.installationId);
      res.status(204).end();
    } catch (error) { next(error); }
  }
);

app.use(express.json({ limit: "128kb", verify: (req, _res, buffer) => { req.rawBody = Buffer.from(buffer); } }));

app.post("/v1/installations/register", async (req, res, next) => {
  try {
    if (process.env.REQUIRE_APP_CHECK === "true") {
      const appCheckToken = req.get("X-Firebase-AppCheck");
      if (!appCheckToken) throw httpError("App Check is required.", 401);
      await verifyAppCheck(appCheckToken);
    }
    const { installationId, publicKey } = verifyRegistrationRequest(req);
    await store.register(installationId, publicKey);
    res.status(201).json({ installationId });
  } catch (error) { next(error); }
});

app.get("/v1/config", authenticate, (_req, res) => res.json({
  onlineEnabled: onlineEnabled(), model: "gpt-4o-mini-transcribe", maxUploadBytes: 25 * 1024 * 1024,
  maxDurationSeconds: 600, accessMode: developmentAccessMode ? "development-preview" : "rewarded-credit",
  supportUrl: "https://kofe.al/@fuadismayil"
}));

app.get("/v1/credits", authenticate, async (req, res, next) => {
  try { res.json(await store.credits(req.installationId)); } catch (error) { next(error); }
});

app.post("/v1/ad-sessions", authenticate, async (req, res, next) => {
  try {
    if (!onlineEnabled()) throw httpError("Online transcription is temporarily unavailable.", 503);
    const reward = marketReward(marketFor(req), economics, targetMargin);
    if (reward < 30) throw httpError("Online access is not currently sustainable in this market.", 503);
    const session = await store.createAdSession(req.installationId, req.get("Idempotency-Key"), reward);
    res.status(201).json({ sessionId: session.id, rewardSeconds: session.rewardSeconds, expiresAtEpochMs: session.expiresAt });
  } catch (error) { next(error); }
});

app.post("/v1/uploads", authenticate, async (req, res, next) => {
  try {
    const { fileName, sizeBytes, mediaType } = req.body ?? {};
    if (!Number.isInteger(sizeBytes) || sizeBytes <= 0 || sizeBytes > 25 * 1024 * 1024) throw httpError("Invalid upload size.");
    if (!ALLOWED_MEDIA.has(mediaType)) throw httpError("Unsupported audio format.");
    const upload = await store.createUpload(req.installationId, req.get("Idempotency-Key"), { fileName: safeName(fileName), sizeBytes, mediaType });
    res.status(201).json({ uploadId: upload.id, uploadPath: `/v1/uploads/${upload.id}/content`, complete: upload.complete === true });
  } catch (error) { next(error); }
});

app.post("/v1/uploads/:id/complete", authenticate, async (req, res, next) => {
  try { await store.completeUpload(req.params.id, req.installationId); res.json({ uploadId: req.params.id, complete: true }); } catch (error) { next(error); }
});

app.post("/v1/transcription-jobs", authenticate, async (req, res, next) => {
  try {
    if (!onlineEnabled()) throw httpError("Online transcription is temporarily unavailable.", 503);
    if (!developmentAccessMode) throw httpError("Online preview access is not enabled on this server.", 503);
    const { uploadId, language, model } = req.body ?? {};
    if (model !== "gpt-4o-mini-transcribe") throw httpError("Unsupported online model.");
    const upload = await store.upload(uploadId);
    if (!upload || !upload.complete || upload.installationId !== req.installationId) throw httpError("Completed upload not found.", 404);
    const job = await store.createJob(req.installationId, req.get("Idempotency-Key"), { uploadId, language: language || "auto" });
    await enqueue(job.id);
    res.status(202).json({ jobId: job.id, status: job.status });
  } catch (error) { next(error); }
});

app.get("/v1/transcription-jobs/:id", authenticate, async (req, res, next) => {
  try {
    const job = await store.job(req.params.id);
    if (!job || job.installationId !== req.installationId) throw httpError("Job not found.", 404);
    const safe = { jobId: job.id, status: job.status, message: job.message };
    if (job.status === "succeeded") safe.result = job.result;
    res.json(safe);
  } catch (error) { next(error); }
});

app.post("/v1/transcription-jobs/:id/cancel", authenticate, async (req, res, next) => {
  try {
    const job = await store.job(req.params.id);
    if (!job || job.installationId !== req.installationId) throw httpError("Job not found.", 404);
    if (job.status !== "queued") throw httpError("A provider request already started and cannot be safely cancelled.", 409);
    await store.finishJob(job.id, { status: "cancelled", message: "Cancelled before provider dispatch." });
    res.json({ jobId: job.id, status: "cancelled" });
  } catch (error) { next(error); }
});

app.post("/internal/jobs/:id/dispatch", async (req, res, next) => {
  try {
    if (!process.env.INTERNAL_TASK_TOKEN || !timingSafe(req.get("X-Internal-Task-Token"), process.env.INTERNAL_TASK_TOKEN)) throw httpError("Forbidden.", 403);
    await dispatchJob(req.params.id);
    res.status(204).end();
  } catch (error) { next(error); }
});

app.get("/internal/status", async (req, res, next) => {
  try {
    if (!process.env.INTERNAL_TASK_TOKEN || !timingSafe(req.get("X-Internal-Task-Token"), process.env.INTERNAL_TASK_TOKEN)) throw httpError("Forbidden.", 403);
    res.json({ onlineEnabled: onlineEnabled(), activeProviderJobs: activeDispatches.size, maxConcurrency, keys: provider.keys.map(({ alias, priority }) => ({ alias, priority, ...provider.health.get(alias) })) });
  } catch (error) { next(error); }
});

app.use((error, _req, res, _next) => {
  const status = error.status ?? (error instanceof StoreError ? error.status : 500);
  if (status >= 500) console.error(JSON.stringify({ event: "request_failed", message: error.message }));
  res.status(status).json({ message: status >= 500 ? "Temporary service error." : error.message });
});

async function enqueue(jobId, delaySeconds = 0) {
  if (process.env.CLOUD_TASKS_QUEUE && process.env.WORKER_URL) {
    const client = new CloudTasksClient();
    const parent = client.queuePath(process.env.FIREBASE_PROJECT_ID, process.env.CLOUD_TASKS_LOCATION, process.env.CLOUD_TASKS_QUEUE);
    await client.createTask({ parent, task: { scheduleTime: delaySeconds ? { seconds: Math.floor(Date.now() / 1000) + delaySeconds } : undefined, httpRequest: { httpMethod: "POST", url: `${process.env.WORKER_URL}/internal/jobs/${jobId}/dispatch`, headers: { "X-Internal-Task-Token": process.env.INTERNAL_TASK_TOKEN }, body: Buffer.from("{}").toString("base64") } } });
  } else {
    setTimeout(() => dispatchJob(jobId).catch((error) => console.error(JSON.stringify({ event: "dispatch_failed", jobId, message: error.message }))), delaySeconds * 1000);
  }
}

async function dispatchJob(jobId) {
  if (activeDispatches.has(jobId)) return;
  if (activeDispatches.size >= maxConcurrency) { await enqueue(jobId, 5); return; }
  const job = await store.job(jobId); if (!job || job.status !== "queued") return;
  activeDispatches.add(jobId);
  let localFile; let reserved = false; let deferred = false;
  try {
    const upload = await store.upload(job.uploadId);
    localFile = await storage.localFile(job.uploadId);
    const durationSeconds = await probeDuration(localFile);
    if (!(durationSeconds > 0) || durationSeconds > 600) throw httpError("Online audio must be no longer than 10 minutes.", 400);
    await store.reserveJob(jobId, Math.ceil(durationSeconds), !developmentAccessMode); reserved = !developmentAccessMode;
    await store.reserveProviderCost(jobId, providerMinuteCost * durationSeconds / 60, providerDailyBudget);
    const result = await provider.transcribe({ filePath: localFile, fileName: upload.fileName, mediaType: upload.mediaType, language: job.language, logicalJobId: jobId });
    const { keyAlias: _keyAlias, ...publicResult } = result;
    await store.finishJob(jobId, { status: "succeeded", result: publicResult, providerKeyAlias: result.keyAlias, durationSeconds });
  } catch (error) {
    if (!reserved && error instanceof StoreError && error.status === 429) {
      deferred = true;
      await enqueue(jobId, 5);
      return;
    }
    const ambiguous = error instanceof AmbiguousProviderError;
    if (reserved && !ambiguous) await store.releaseReservation(jobId);
    await store.finishJob(jobId, { status: "failed", outcome: ambiguous ? "needs_reconciliation" : "definitive", message: ambiguous ? "The provider result is being reconciled; no duplicate request was sent." : error.message });
  } finally {
    activeDispatches.delete(jobId);
    if (!deferred && job.uploadId) await storage.remove(job.uploadId, localFile).catch(() => {});
  }
}

async function probeDuration(filePath) {
  const { stdout } = await execFileAsync(ffprobeExecutable, ["-v", "error", "-show_entries", "format=duration", "-of", "default=noprint_wrappers=1:nokey=1", filePath], { timeout: 15_000 });
  return Number(String(stdout).trim());
}

function parseJson(value, fallback) { try { return value ? JSON.parse(value) : fallback; } catch { return fallback; } }
function safeName(value) { return String(value ?? "audio").replace(/[^a-zA-Z0-9._ -]/g, "_").slice(0, 120); }
function timingSafe(left, right) { const a = Buffer.from(left ?? ""); const b = Buffer.from(right ?? ""); return a.length === b.length && crypto.timingSafeEqual(a, b); }
function marketFor(req) { const candidate = trustedMarketHeader ? req.get(trustedMarketHeader) : null; return /^[A-Z]{2}$/.test(candidate ?? "") ? candidate : defaultMarket; }
const ALLOWED_MEDIA = new Set(["audio/wav", "audio/mpeg", "audio/mp4", "audio/flac", "audio/ogg", "audio/webm"]);

const port = Number(process.env.PORT ?? 8080);
if (process.env.NODE_ENV !== "test") app.listen(port, () => console.log(JSON.stringify({ event: "listening", port })));
export { app, dispatchJob };
