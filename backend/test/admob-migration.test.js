import test from "node:test";
import assert from "node:assert/strict";
import { MemoryStore } from "../src/store.js";
import { rewardConfig } from "../src/config.js";

test("AdMob client completion is isolated to explicit test environments", () => {
  assert.deepEqual(rewardConfig({}), {
    provider: "admob", clientRewards: false, testRewards: false,
    ssv: { adUnitId: "", rewardAmount: "", rewardItem: "" }
  });
  assert.throws(() => rewardConfig({ ADMOB_CLIENT_REWARDS_ENABLED: "true" }), /only for test ads/);
  assert.throws(() => rewardConfig({ ADMOB_CLIENT_REWARDS_ENABLED: "true", ADMOB_TEST_REWARDS: "true", DEVELOPMENT_ACCESS_MODE: "true" }), /cannot be combined/);
  assert.equal(rewardConfig({ ADMOB_CLIENT_REWARDS_ENABLED: "true", ADMOB_TEST_REWARDS: "true" }).clientRewards, true);
  assert.throws(() => rewardConfig({ NODE_ENV: "production", DEVELOPMENT_ACCESS_MODE: "false" }), /Production AdMob rewards require/);
  assert.deepEqual(rewardConfig({
    NODE_ENV: "production", DEVELOPMENT_ACCESS_MODE: "false",
    ADMOB_REWARDED_AD_UNIT_ID: "ca-app-pub-1234567890123456/9876543210",
    ADMOB_SSV_REWARD_AMOUNT: "1", ADMOB_SSV_REWARD_ITEM: "online_credit"
  }).ssv, { adUnitId: "9876543210", rewardAmount: "1", rewardItem: "online_credit" });
});

test("AdMob awards bind to owner, provider, test mode and single-use session", async () => {
  let now = 100_000;
  const store = new MemoryStore(() => now);
  await store.register("owner", "key");
  await store.register("other", "key2");
  const session = await store.createAdSession("owner", "ad1", 60, { provider: "admob", testAd: true });
  const expected = { installationId: "owner", provider: "admob", testAd: true, rewardEvidence: "test-client-reported" };
  await assert.rejects(store.grantAdReward(session.id, "tx", { ...expected, installationId: "other" }), { status: 404 });
  await assert.rejects(store.grantAdReward(session.id, "tx", { ...expected, provider: "legacy" }), { status: 403 });
  await assert.rejects(store.grantAdReward(session.id, "tx", { ...expected, testAd: false }), { status: 403 });
  await store.grantAdReward(session.id, "tx", expected);
  assert.equal((await store.credits("owner")).availableSeconds, 60);
  assert.equal(session.rewardEvidence, "test-client-reported");
  now += 20 * 60_000;
  assert.equal((await store.grantAdReward(session.id, "tx", expected)).duplicate, true);
  assert.equal((await store.credits("owner")).availableSeconds, 60);
  const expired = await store.createAdSession("owner", "ad2", 60, { provider: "admob", testAd: true });
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
  const session = await store.createAdSession("owner", "ad", 60, { provider: "admob", testAd: true });
  await store.grantAdReward(session.id, "tx", { installationId: "owner", provider: "admob", testAd: true });
  assert.equal((await store.reserveJob(job.id, 10)).dispatchClaimed, true);
  assert.equal((await store.reserveJob(job.id, 10)).dispatchClaimed, false);
  await assert.rejects(store.reserveProviderCost(job.id, 0.01, 0), { status: 503 });
  await store.releaseReservation(job.id);
  await store.releaseReservation(job.id);
  assert.equal((await store.credits("owner")).availableSeconds, 60);
});
