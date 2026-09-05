import test from "node:test";
import assert from "node:assert/strict";
import crypto from "node:crypto";
import http from "node:http";

test("signed reward -> credit -> existing transcription route, with replay and mode rejection", async () => {
  let providerRequests = 0;
  const mockProvider = http.createServer(async (req, res) => {
    providerRequests++;
    let payload = "";
    for await (const chunk of req) payload += chunk.toString();
    assert.match(payload, /gpt-4o-mini-transcribe/);
    res.setHeader("Content-Type", "application/json");
    res.end(JSON.stringify({ text: "A test transcript.", language: "en", segments: [] }));
  });
  await new Promise(resolve => mockProvider.listen(0, "127.0.0.1", resolve));
  Object.assign(process.env, { NODE_ENV: "test", USE_MEMORY_STORE: "true", DEVELOPMENT_ACCESS_MODE: "false",
    AD_PROVIDER: "startio", STARTIO_CLIENT_REWARDS_ENABLED: "true", STARTIO_TEST_REWARDS: "true", REQUIRE_APP_CHECK: "false",
    TOKENMIX_KEY_1: "test-only-fake-key", TOKENMIX_ENDPOINT: `http://127.0.0.1:${mockProvider.address().port}/v1/audio/transcriptions`,
    UPLOAD_BUCKET: "", CLOUD_TASKS_QUEUE: "", PROVIDER_DAILY_BUDGET_USD: "1" });
  const { app } = await import("../src/server.js");
  const server = http.createServer(app);
  await new Promise(resolve => server.listen(0, "127.0.0.1", resolve));
  const url = `http://127.0.0.1:${server.address().port}`;
  const { publicKey, privateKey } = crypto.generateKeyPairSync("ec", { namedCurve: "prime256v1" });
  const installationId = crypto.randomUUID();
  async function request(method, path, value, key = crypto.randomUUID()) {
    const body = value === undefined ? undefined : Buffer.isBuffer(value) ? value : JSON.stringify(value);
    const timestamp = String(Date.now()), nonce = crypto.randomUUID();
    const hash = crypto.createHash("sha256").update(body ?? "").digest("hex");
    const signature = crypto.sign("sha256", Buffer.from([method, path, timestamp, nonce, key, hash].join("\n")), privateKey).toString("base64url");
    return fetch(url + path, { method, body, headers: { "Content-Type": "application/json", "X-Installation-Id": installationId,
      "X-Request-Timestamp": timestamp, "X-Request-Nonce": nonce, "Idempotency-Key": key, "X-Body-SHA256": hash, "X-Request-Signature": signature } });
  }
  try {
    assert.equal((await request("POST", "/v1/installations/register", { installationId, publicKey: publicKey.export({ format: "der", type: "spki" }).toString("base64url") })).status, 201);
    const config = await (await request("GET", "/v1/config")).json();
    assert.equal(config.accessMode, "rewarded-credit");
    assert.equal(config.rewardSeconds, 60);
    const session = await (await request("POST", "/v1/ad-sessions", {})).json();
    assert.equal((await request("POST", "/v1/startio/completions", { sessionId: session.sessionId, testAd: false })).status, 403);
    const grant = () => request("POST", "/v1/startio/completions", { sessionId: session.sessionId, testAd: true }, session.sessionId);
    assert.equal((await (await grant()).json()).availableSeconds, 60);
    assert.equal((await (await grant()).json()).availableSeconds, 60);
    assert.equal((await request("GET", "/callbacks/admob/ssv")).status, 404);
    // Reward mode reaches upload validation instead of the old unconditional preview-only 503.
    assert.equal((await request("POST", "/v1/transcription-jobs", { model: "gpt-4o-mini-transcribe", uploadId: "missing" })).status, 404);
    // One second of PCM WAV exercises ffprobe, credit reservation and provider dispatch.
    const wav = Buffer.alloc(44 + 32000);
    wav.write("RIFF", 0); wav.writeUInt32LE(wav.length - 8, 4); wav.write("WAVEfmt ", 8);
    wav.writeUInt32LE(16, 16); wav.writeUInt16LE(1, 20); wav.writeUInt16LE(1, 22);
    wav.writeUInt32LE(16000, 24); wav.writeUInt32LE(32000, 28); wav.writeUInt16LE(2, 32); wav.writeUInt16LE(16, 34);
    wav.write("data", 36); wav.writeUInt32LE(32000, 40);
    const upload = await (await request("POST", "/v1/uploads", { fileName: "test.wav", sizeBytes: wav.length, mediaType: "audio/wav" })).json();
    assert.equal((await request("PUT", upload.uploadPath, wav)).status, 204);
    assert.equal((await request("POST", `/v1/uploads/${upload.uploadId}/complete`, {})).status, 200);
    const jobKey = crypto.randomUUID();
    const payload = { model: "gpt-4o-mini-transcribe", language: "en", uploadId: upload.uploadId };
    const job = await (await request("POST", "/v1/transcription-jobs", payload, jobKey)).json();
    assert.equal((await (await request("POST", "/v1/transcription-jobs", payload, jobKey)).json()).jobId, job.jobId);
    let status;
    for (let i = 0; i < 40; i++) {
      status = await (await request("GET", `/v1/transcription-jobs/${job.jobId}`)).json();
      if (["succeeded", "failed"].includes(status.status)) break;
      await new Promise(resolve => setTimeout(resolve, 50));
    }
    assert.equal(status.status, "succeeded", JSON.stringify(status));
    assert.equal(status.result.text, "A test transcript.");
    assert.equal(providerRequests, 1);
    assert.equal((await (await request("GET", "/v1/credits")).json()).availableSeconds, 59);
  } finally {
    server.closeAllConnections(); await new Promise(resolve => server.close(resolve));
    mockProvider.closeAllConnections(); await new Promise(resolve => mockProvider.close(resolve));
  }
});
