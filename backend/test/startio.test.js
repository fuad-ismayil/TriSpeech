import test from "node:test";
import assert from "node:assert/strict";
import { MemoryStore } from "../src/store.js";
import { rewardConfig } from "../src/config.js";

test("Start.io requires explicit opt-in and rejects a preview bypass", () => {
  assert.equal(rewardConfig({ AD_PROVIDER: "startio" }).clientRewards, false);
  assert.throws(() => rewardConfig({ AD_PROVIDER: "startio", STARTIO_CLIENT_REWARDS_ENABLED: "true", DEVELOPMENT_ACCESS_MODE: "true" }), /cannot be combined/);
  assert.throws(() => rewardConfig({ STARTIO_REWARD_SECONDS: "NaN" }), /integer/);
  assert.throws(() => rewardConfig({ AD_PROVIDER: "startio", STARTIO_CLIENT_REWARDS_ENABLED: "true", USE_MEMORY_STORE: "true" }), /Firestore/);
  assert.equal(rewardConfig({ AD_PROVIDER: "startio", STARTIO_TEST_REWARDS: "true", STARTIO_CLIENT_REWARDS_ENABLED: "true", USE_MEMORY_STORE: "true" }).seconds, 60);
});

test("Start.io awards bind to owner, provider, test mode and single-use session", async () => {
  let now = 100_000;
  const store = new MemoryStore(() => now);
  await store.register("owner", "key");
  await store.register("other", "key2");
  const session = await store.createAdSession("owner", "ad1", 60, { provider: "startio", testAd: true });
  const expected = { installationId: "owner", provider: "startio", testAd: true };
  await assert.rejects(store.grantAdReward(session.id, "tx", { ...expected, installationId: "other" }), { status: 404 });
  await assert.rejects(store.grantAdReward(session.id, "tx", { ...expected, provider: "admob" }), { status: 403 });
  await assert.rejects(store.grantAdReward(session.id, "tx", { ...expected, testAd: false }), { status: 403 });
  await store.grantAdReward(session.id, "tx", expected);
  assert.equal((await store.credits("owner")).availableSeconds, 60);
  assert.equal(session.rewardEvidence, "client-reported");
  now += 20 * 60_000;
  assert.equal((await store.grantAdReward(session.id, "tx", expected)).duplicate, true);
  assert.equal((await store.credits("owner")).availableSeconds, 60);
  const expired = await store.createAdSession("owner", "ad2", 60, { provider: "startio", testAd: true });
  now = expired.expiresAt;
  await assert.rejects(store.grantAdReward(expired.id, "tx2", expected), { status: 410 });
  now += 24 * 60 * 60_000;
  assert.equal((await store.credits("owner")).availableSeconds, 0);
});

test("rewarded transcription refuses insufficient credit and refunds definitive failure once", async () => {
  const store = new MemoryStore();
  await store.register("owner", "key");
  const upload = await store.createUpload("owner", "upload", { fileName: "a.wav", sizeBytes: 32, mediaType: "audio/wav" });
  await store.markUploadStored(upload.id, "owner");
  await store.completeUpload(upload.id, "owner");
  const job = await store.createJob("owner", "job", { uploadId: upload.id });
  await assert.rejects(store.reserveJob(job.id, 10), { status: 402 });
  const session = await store.createAdSession("owner", "ad", 60, { provider: "startio", testAd: true });
  await store.grantAdReward(session.id, "tx", { installationId: "owner", provider: "startio", testAd: true });
  assert.equal((await store.reserveJob(job.id, 10)).dispatchClaimed, true);
  assert.equal((await store.reserveJob(job.id, 10)).dispatchClaimed, false);
  await assert.rejects(store.reserveProviderCost(job.id, 0.01, 0), { status: 503 });
  await store.releaseReservation(job.id);
  await store.releaseReservation(job.id);
  assert.equal((await store.credits("owner")).availableSeconds, 60);
});
