import crypto from "node:crypto";
import { Firestore, FieldValue } from "@google-cloud/firestore";

const HOUR_MS = 60 * 60 * 1000;
const CREDIT_TTL_MS = 24 * HOUR_MS;

export class StoreError extends Error {
  constructor(message, status = 400) { super(message); this.status = status; }
}

const stableId = (installationId, idempotencyKey) =>
  crypto.createHash("sha256").update(`${installationId}:${idempotencyKey}`).digest("hex");

function checkRewardSession(session, expected, now) {
  if (!session) throw new StoreError("Unknown ad session.", 404);
  if (expected.installationId && session.installationId !== expected.installationId) throw new StoreError("Ad session not found.", 404);
  if (expected.provider && (session.provider ?? "admob") !== expected.provider) throw new StoreError("Ad provider mismatch.", 403);
  if (expected.testAd !== undefined && session.testAd !== expected.testAd) throw new StoreError("Ad environment mismatch.", 403);
  if (!session.verified && session.expiresAt <= now) throw new StoreError("Ad session expired.", 410);
}

export class MemoryStore {
  constructor(now = () => Date.now()) {
    this.now = now;
    this.installations = new Map();
    this.sessions = new Map();
    this.uploads = new Map();
    this.jobs = new Map();
    this.transactions = new Set();
    this.nonces = new Map();
    this.dailyProviderSpend = new Map();
    this.rateLimits = new Map();
  }

  async register(id, publicKey) {
    const existing = this.installations.get(id);
    if (existing && existing.publicKey !== publicKey) throw new StoreError("Installation identity conflict.", 409);
    if (!existing) this.installations.set(id, { publicKey, creditSeconds: 0, creditExpiresAt: 0, adTimes: [], jobTimes: [], uploadTimes: [], outstandingJobs: 0 });
    return this.installations.get(id);
  }
  async installation(id) { return this.installations.get(id); }
  async consumeNonce(id, nonce) {
    const key = `${id}:${nonce}`;
    if (this.nonces.has(key)) return false;
    this.nonces.set(key, this.now() + 10 * 60 * 1000);
    return true;
  }
  async rateLimit(id, bucket, limit, windowMs) {
    const key = `${id}:${bucket}`; const now = this.now(); const current = this.rateLimits.get(key);
    const next = !current || current.resetAt <= now ? { count: 1, resetAt: now + windowMs } : { ...current, count: current.count + 1 };
    this.rateLimits.set(key, next);
    if (next.count > limit) throw new StoreError("Too many requests. Try again later.", 429);
  }
  async credits(id) {
    const installation = this.installations.get(id);
    if (!installation) throw new StoreError("Unknown installation.", 401);
    if (installation.creditExpiresAt <= this.now()) installation.creditSeconds = 0;
    return { availableSeconds: installation.creditSeconds, expiresAtEpochMs: installation.creditExpiresAt || null, lastGrantSessionId: installation.lastGrantSessionId ?? null };
  }
  async createAdSession(id, idempotencyKey, rewardSeconds, source = { provider: "admob", testAd: false }) {
    const installation = this.installations.get(id);
    const now = this.now();
    const sessionId = stableId(id, idempotencyKey);
    if (this.sessions.has(sessionId)) return this.sessions.get(sessionId);
    installation.adTimes = installation.adTimes.filter((time) => time > now - HOUR_MS);
    const active = installation.activeAdSessionId && this.sessions.get(installation.activeAdSessionId);
    if (active && active.expiresAt > now && !active.verified && active.provider === source.provider && active.testAd === source.testAd) return active;
    if (installation.adTimes.length >= 6) throw new StoreError("Too many ad sessions. Try again later.", 429);
    const session = { id: sessionId, installationId: id, rewardSeconds, ...source, createdAt: now, expiresAt: now + 15 * 60 * 1000, verified: false };
    installation.adTimes.push(now);
    installation.activeAdSessionId = sessionId;
    this.sessions.set(sessionId, session);
    return session;
  }
  async grantAdReward(sessionId, transactionId, expected = {}) {
    const session = this.sessions.get(sessionId);
    checkRewardSession(session, expected, this.now());
    if (this.transactions.has(transactionId)) return { duplicate: true };
    if (session.verified) return { duplicate: true };
    const installation = this.installations.get(session.installationId);
    if (installation.creditExpiresAt <= this.now()) installation.creditSeconds = 0;
    installation.creditSeconds += session.rewardSeconds;
    installation.creditExpiresAt = Math.max(installation.creditExpiresAt, this.now() + CREDIT_TTL_MS);
    installation.lastGrantSessionId = sessionId;
    installation.activeAdSessionId = null;
    session.verified = true;
    session.rewardEvidence = expected.rewardEvidence ?? "provider-verified";
    session.transactionId = transactionId;
    this.transactions.add(transactionId);
    return { duplicate: false, rewardSeconds: session.rewardSeconds };
  }
  async createUpload(id, idempotencyKey, data) {
    const uploadId = stableId(id, idempotencyKey);
    if (this.uploads.has(uploadId)) return this.uploads.get(uploadId);
    const installation = this.installations.get(id); const now = this.now();
    installation.uploadTimes = installation.uploadTimes.filter((time) => time > now - 10 * 60 * 1000);
    const outstanding = [...this.uploads.values()].filter((upload) => upload.installationId === id && !upload.released && upload.expiresAt > now).length;
    if (installation.uploadTimes.length >= 5 || outstanding >= 2) throw new StoreError("Too many outstanding uploads. Try again later.", 429);
    installation.uploadTimes.push(now);
    this.uploads.set(uploadId, { id: uploadId, installationId: id, ...data, createdAt: now, expiresAt: now + 24 * HOUR_MS, contentStored: false, complete: false, released: false });
    return this.uploads.get(uploadId);
  }
  async upload(id) { return this.uploads.get(id); }
  async markUploadStored(id, installationId) { const upload = this.uploads.get(id); if (!upload || upload.installationId !== installationId) throw new StoreError("Upload not found.", 404); if (upload.expiresAt <= this.now()) throw new StoreError("Upload expired.", 410); upload.contentStored = true; }
  async completeUpload(id, installationId) {
    const upload = this.uploads.get(id);
    if (!upload || upload.installationId !== installationId) throw new StoreError("Upload not found.", 404);
    if (upload.expiresAt <= this.now()) throw new StoreError("Upload expired.", 410);
    if (!upload.contentStored) throw new StoreError("Upload content is incomplete.", 409);
    upload.complete = true;
    return upload;
  }
  async createJob(installationId, idempotencyKey, data) {
    const jobId = stableId(installationId, idempotencyKey);
    if (this.jobs.has(jobId)) return this.jobs.get(jobId);
    const upload = this.uploads.get(data.uploadId);
    if (!upload || upload.installationId !== installationId || !upload.complete || upload.expiresAt <= this.now()) throw new StoreError("Completed upload not found.", 404);
    if (upload?.jobId && upload.jobId !== jobId) throw new StoreError("This upload already belongs to another job.", 409);
    const installation = this.installations.get(installationId);
    const now = this.now();
    installation.jobTimes = installation.jobTimes.filter((time) => time > now - 10 * 60 * 1000);
    if (installation.jobTimes.length >= 5) throw new StoreError("Too many transcription attempts. Try again later.", 429);
    if (installation.outstandingJobs >= 2) throw new StoreError("Too many outstanding transcription jobs.", 429);
    installation.jobTimes.push(now); installation.outstandingJobs += 1; upload.jobId = jobId;
    const job = { id: jobId, installationId, ...data, status: "queued", createdAt: now, reservedSeconds: 0 };
    this.jobs.set(jobId, job);
    return job;
  }
  async job(id) { return this.jobs.get(id); }
  async reserveJob(id, seconds, requireCredit = true) {
    const job = this.jobs.get(id); const installation = this.installations.get(job.installationId);
    if (job.status !== "queued") return { ...job, dispatchClaimed: false };
    if (installation.activeJobId && installation.activeJobId !== id) throw new StoreError("Another transcription is still processing.", 429);
    if (requireCredit) {
      if (installation.creditExpiresAt <= this.now()) installation.creditSeconds = 0;
      if (installation.creditSeconds < seconds) throw new StoreError(`This audio needs ${seconds} seconds of online credit.`, 402);
      installation.creditSeconds -= seconds;
      job.reservedSeconds = seconds;
    }
    installation.activeJobId = id; job.status = "processing";
    return { ...job, dispatchClaimed: true };
  }
  async reserveProviderCost(id, estimatedCost, dailyBudget) {
    const job = this.jobs.get(id); if (job.providerCostReserved) return;
    const day = new Date(this.now()).toISOString().slice(0, 10); const used = this.dailyProviderSpend.get(day) ?? 0;
    if (used + estimatedCost > dailyBudget) throw new StoreError("The provider budget ceiling has been reached. Your credit was preserved.", 503);
    this.dailyProviderSpend.set(day, used + estimatedCost); job.providerCostReserved = estimatedCost;
  }
  async finishJob(id, patch) {
    const job = this.jobs.get(id); if (["succeeded", "failed", "cancelled"].includes(job.status)) return job;
    Object.assign(job, patch, { finishedAt: this.now() });
    const installation = this.installations.get(job.installationId);
    installation.outstandingJobs = Math.max(0, installation.outstandingJobs - 1);
    const upload = this.uploads.get(job.uploadId); if (upload) upload.released = true;
    if (installation.activeJobId === id) installation.activeJobId = null;
    return job;
  }
  async releaseReservation(id) {
    const job = this.jobs.get(id); if (!job?.reservedSeconds) return;
    const installation = this.installations.get(job.installationId);
    installation.creditSeconds += job.reservedSeconds; job.reservedSeconds = 0;
  }
}

export class FirestoreStore {
  constructor(now = () => Date.now()) { this.db = new Firestore({ projectId: process.env.FIREBASE_PROJECT_ID }); this.now = now; }
  i(id) { return this.db.collection("installations").doc(id); }
  async register(id, publicKey) {
    const ref = this.i(id);
    await this.db.runTransaction(async (tx) => {
      const snapshot = await tx.get(ref);
      if (snapshot.exists && snapshot.data().publicKey !== publicKey) throw new StoreError("Installation identity conflict.", 409);
      if (!snapshot.exists) tx.create(ref, { publicKey, createdAt: this.now(), creditSeconds: 0, creditExpiresAt: 0, adTimes: [], jobTimes: [], uploadTimes: [], outstandingJobs: 0, uploadReservations: {} });
    });
    return (await ref.get()).data();
  }
  async installation(id) { return (await this.i(id).get()).data(); }
  async consumeNonce(id, nonce) {
    const ref = this.db.collection("requestNonces").doc(stableId(id, nonce));
    try { await ref.create({ installationId: id, expiresAt: this.now() + 10 * 60 * 1000 }); return true; } catch (error) { if (error.code === 6 || error.code === "already-exists") return false; throw error; }
  }
  async rateLimit(id, bucket, limit, windowMs) { const ref = this.db.collection("rateLimits").doc(stableId(id, bucket)); const now = this.now(); await this.db.runTransaction(async (tx) => { const snap = await tx.get(ref); const current = snap.data(); const next = !current || current.resetAt <= now ? { count: 1, resetAt: now + windowMs, installationId: id, bucket } : { ...current, count: current.count + 1 }; if (next.count > limit) throw new StoreError("Too many requests. Try again later.", 429); tx.set(ref, next); }); }
  async credits(id) {
    const ref = this.i(id); const snapshot = await ref.get();
    if (!snapshot.exists) throw new StoreError("Unknown installation.", 401);
    const data = snapshot.data(); const expired = data.creditExpiresAt <= this.now();
    // Read-only: zeroing here could erase a concurrent reward transaction.
    return { availableSeconds: expired ? 0 : data.creditSeconds, expiresAtEpochMs: data.creditExpiresAt || null, lastGrantSessionId: data.lastGrantSessionId ?? null };
  }
  async createAdSession(id, idempotencyKey, rewardSeconds, source = { provider: "admob", testAd: false }) {
    const requestedSessionId = stableId(id, idempotencyKey); const requestedRef = this.db.collection("adSessions").doc(requestedSessionId); const installRef = this.i(id); const now = this.now();
    let selectedSessionId = requestedSessionId;
    await this.db.runTransaction(async (tx) => {
      const [existing, installSnap] = await Promise.all([tx.get(requestedRef), tx.get(installRef)]);
      if (existing.exists) return;
      const data = installSnap.data(); const times = (data.adTimes ?? []).filter((time) => time > now - HOUR_MS);
      if (data.activeAdSessionId) {
        const activeRef = this.db.collection("adSessions").doc(data.activeAdSessionId);
        const active = await tx.get(activeRef);
        if (active.exists && active.data().expiresAt > now && !active.data().verified && active.data().provider === source.provider && active.data().testAd === source.testAd) { selectedSessionId = data.activeAdSessionId; return; }
      }
      if (times.length >= 6) throw new StoreError("Too many ad sessions. Try again later.", 429);
      tx.create(requestedRef, { installationId: id, rewardSeconds, ...source, createdAt: now, expiresAt: now + 15 * 60 * 1000, verified: false });
      tx.update(installRef, { adTimes: [...times, now], activeAdSessionId: requestedSessionId });
    });
    const selectedRef = this.db.collection("adSessions").doc(selectedSessionId);
    return { id: selectedSessionId, ...(await selectedRef.get()).data() };
  }
  async grantAdReward(sessionId, transactionId, expected = {}) {
    const sessionRef = this.db.collection("adSessions").doc(sessionId); const transactionRef = this.db.collection("adTransactions").doc(transactionId);
    return this.db.runTransaction(async (tx) => {
      const [sessionSnap, transactionSnap] = await Promise.all([tx.get(sessionRef), tx.get(transactionRef)]);
      checkRewardSession(sessionSnap.data(), expected, this.now());
      if (transactionSnap.exists || sessionSnap.data()?.verified) return { duplicate: true };
      const session = sessionSnap.data();
      const installRef = this.i(session.installationId); const installSnap = await tx.get(installRef); const install = installSnap.data();
      const current = install.creditExpiresAt > this.now() ? install.creditSeconds : 0;
      tx.update(installRef, { creditSeconds: current + session.rewardSeconds, creditExpiresAt: Math.max(install.creditExpiresAt, this.now() + CREDIT_TTL_MS), lastGrantSessionId: sessionId, activeAdSessionId: FieldValue.delete() });
      tx.update(sessionRef, { verified: true, transactionId, verifiedAt: this.now(), rewardEvidence: expected.rewardEvidence ?? "provider-verified" });
      tx.create(transactionRef, { sessionId, installationId: session.installationId, createdAt: this.now() });
      return { duplicate: false, rewardSeconds: session.rewardSeconds };
    });
  }
  async createUpload(id, idempotencyKey, data) {
    const uploadId = stableId(id, idempotencyKey); const ref = this.db.collection("uploads").doc(uploadId); const installRef = this.i(id); const now = this.now();
    await this.db.runTransaction(async (tx) => { const [existing, installSnap] = await Promise.all([tx.get(ref), tx.get(installRef)]); if (existing.exists) return; const install = installSnap.data(); const times = (install.uploadTimes ?? []).filter((time) => time > now - 10 * 60 * 1000); const reservations = Object.fromEntries(Object.entries(install.uploadReservations ?? {}).filter(([, expiresAt]) => expiresAt > now)); if (times.length >= 5 || Object.keys(reservations).length >= 2) throw new StoreError("Too many outstanding uploads. Try again later.", 429); const expiresAt = now + 24 * HOUR_MS; tx.create(ref, { installationId: id, ...data, createdAt: now, expiresAt, contentStored: false, complete: false }); tx.update(installRef, { uploadTimes: [...times, now], uploadReservations: { ...reservations, [uploadId]: expiresAt } }); });
    return { id: uploadId, ...(await ref.get()).data() };
  }
  async upload(id) { const snap = await this.db.collection("uploads").doc(id).get(); return snap.exists ? { id, ...snap.data() } : null; }
  async markUploadStored(id, installationId) { const upload = await this.upload(id); if (!upload || upload.installationId !== installationId) throw new StoreError("Upload not found.", 404); if (upload.expiresAt <= this.now()) throw new StoreError("Upload expired.", 410); await this.db.collection("uploads").doc(id).update({ contentStored: true }); }
  async completeUpload(id, installationId) { const ref = this.db.collection("uploads").doc(id); const upload = await this.upload(id); if (!upload || upload.installationId !== installationId) throw new StoreError("Upload not found.", 404); if (upload.expiresAt <= this.now()) throw new StoreError("Upload expired.", 410); if (!upload.contentStored) throw new StoreError("Upload content is incomplete.", 409); await ref.update({ complete: true }); return { ...upload, complete: true }; }
  async createJob(installationId, idempotencyKey, data) {
    const jobId = stableId(installationId, idempotencyKey); const jobRef = this.db.collection("jobs").doc(jobId); const installRef = this.i(installationId); const now = this.now();
    await this.db.runTransaction(async (tx) => {
      const uploadRef = this.db.collection("uploads").doc(data.uploadId); const [jobSnap, installSnap, uploadSnap] = await Promise.all([tx.get(jobRef), tx.get(installRef), tx.get(uploadRef)]); if (jobSnap.exists) return;
      const install = installSnap.data(); const upload = uploadSnap.data(); const times = (install.jobTimes ?? []).filter((time) => time > now - 10 * 60 * 1000);
      if (!upload || upload.installationId !== installationId || !upload.complete || upload.expiresAt <= now) throw new StoreError("Completed upload not found.", 404);
      if (upload.jobId && upload.jobId !== jobId) throw new StoreError("This upload already belongs to another job.", 409);
      if (times.length >= 5) throw new StoreError("Too many transcription attempts. Try again later.", 429);
      if ((install.outstandingJobs ?? 0) >= 2) throw new StoreError("Too many outstanding transcription jobs.", 429);
      tx.create(jobRef, { installationId, ...data, status: "queued", createdAt: now, reservedSeconds: 0 });
      tx.update(uploadRef, { jobId });
      tx.update(installRef, { jobTimes: [...times, now], outstandingJobs: FieldValue.increment(1) });
    });
    return { id: jobId, ...(await jobRef.get()).data() };
  }
  async job(id) { const snap = await this.db.collection("jobs").doc(id).get(); return snap.exists ? { id, ...snap.data() } : null; }
  async reserveJob(id, seconds, requireCredit = true) {
    const jobRef = this.db.collection("jobs").doc(id);
    const dispatchClaimed = await this.db.runTransaction(async (tx) => { const jobSnap = await tx.get(jobRef); const job = jobSnap.data(); if (job.status !== "queued") return false; const installRef = this.i(job.installationId); const installSnap = await tx.get(installRef); const install = installSnap.data(); if (install.activeJobId && install.activeJobId !== id) throw new StoreError("Another transcription is still processing.", 429); const credit = install.creditExpiresAt > this.now() ? install.creditSeconds : 0; if (requireCredit && credit < seconds) throw new StoreError(`This audio needs ${seconds} seconds of online credit.`, 402); tx.update(installRef, { ...(requireCredit ? { creditSeconds: credit - seconds } : {}), activeJobId: id }); tx.update(jobRef, { reservedSeconds: requireCredit ? seconds : 0, status: "processing", startedAt: this.now() }); return true; });
    return { ...(await this.job(id)), dispatchClaimed };
  }
  async reserveProviderCost(id, estimatedCost, dailyBudget) { const jobRef = this.db.collection("jobs").doc(id); const day = new Date(this.now()).toISOString().slice(0, 10); const budgetRef = this.db.collection("providerBudgets").doc(day); await this.db.runTransaction(async (tx) => { const [jobSnap, budgetSnap] = await Promise.all([tx.get(jobRef), tx.get(budgetRef)]); if (jobSnap.data()?.providerCostReserved) return; const used = budgetSnap.data()?.reservedUsd ?? 0; if (used + estimatedCost > dailyBudget) throw new StoreError("The provider budget ceiling has been reached. Your credit was preserved.", 503); tx.set(budgetRef, { reservedUsd: used + estimatedCost, updatedAt: this.now() }, { merge: true }); tx.update(jobRef, { providerCostReserved: estimatedCost }); }); }
  async finishJob(id, patch) { const jobRef = this.db.collection("jobs").doc(id); await this.db.runTransaction(async (tx) => { const jobSnap = await tx.get(jobRef); const job = jobSnap.data(); if (["succeeded", "failed", "cancelled"].includes(job?.status)) return; const installRef = this.i(job.installationId); const installSnap = await tx.get(installRef); const reservations = { ...(installSnap.data()?.uploadReservations ?? {}) }; delete reservations[job.uploadId]; tx.update(jobRef, { ...patch, finishedAt: this.now() }); tx.update(installRef, { outstandingJobs: FieldValue.increment(-1), uploadReservations: reservations, ...(job.status === "processing" ? { activeJobId: FieldValue.delete() } : {}) }); }); return this.job(id); }
  async releaseReservation(id) { const jobRef = this.db.collection("jobs").doc(id); await this.db.runTransaction(async (tx) => { const jobSnap = await tx.get(jobRef); const job = jobSnap.data(); if (!job?.reservedSeconds) return; tx.update(this.i(job.installationId), { creditSeconds: FieldValue.increment(job.reservedSeconds) }); tx.update(jobRef, { reservedSeconds: 0 }); }); }
}

export function createStore() {
  if (process.env.USE_MEMORY_STORE === "true" || process.env.NODE_ENV !== "production") return new MemoryStore();
  return new FirestoreStore();
}
