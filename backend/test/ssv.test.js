import test from "node:test";
import assert from "node:assert/strict";
import crypto from "node:crypto";
import { createAdMobSsvVerifier } from "../src/ssv.js";
import { MemoryStore } from "../src/store.js";

const EXPECTED = { adUnitId: "9876543210", rewardAmount: "1", rewardItem: "online_credit" };

function keyPair() {
  return crypto.generateKeyPairSync("ec", { namedCurve: "prime256v1" });
}

function callback(privateKey, { keyId = "123", sessionId = "a".repeat(64), transactionId = "b".repeat(32),
  adUnitId = EXPECTED.adUnitId, rewardAmount = EXPECTED.rewardAmount, rewardItem = EXPECTED.rewardItem,
  timestamp = "1700000000000", extra = [] } = {}) {
  const params = new URLSearchParams([
    ["ad_network", "5450213213286189855"], ["ad_unit", adUnitId], ["custom_data", sessionId],
    ["reward_amount", rewardAmount], ["reward_item", rewardItem], ["timestamp", timestamp],
    ["transaction_id", transactionId], ...extra
  ]);
  const signedData = params.toString();
  const signature = crypto.sign("sha256", Buffer.from(signedData), privateKey).toString("base64url");
  return `/callbacks/admob/ssv?${signedData}&signature=${signature}&key_id=${keyId}`;
}

test("valid AdMob SSV callback verifies every authoritative field", async () => {
  const keys = keyPair();
  const verify = createAdMobSsvVerifier({ loadKeys: async () => new Map([["123", keys.publicKey]]) });
  const verified = await verify(callback(keys.privateKey), EXPECTED);
  assert.deepEqual(verified, {
    sessionId: "a".repeat(64), transactionId: "b".repeat(32), adUnitId: EXPECTED.adUnitId,
    rewardAmount: EXPECTED.rewardAmount, rewardItem: EXPECTED.rewardItem, timestamp: 1700000000000,
    consoleVerification: false
  });
});

test("Google-signed AdMob console probes are accepted but marked as non-crediting", async () => {
  const keys = keyPair();
  const verify = createAdMobSsvVerifier({ loadKeys: async () => new Map([["123", keys.publicKey]]) });
  const verified = await verify(callback(keys.privateKey, {
    adUnitId: "1234567890",
    rewardAmount: "999",
    rewardItem: "console_test",
    transactionId: "123456789"
  }), EXPECTED);
  assert.equal(verified.consoleVerification, true);
  assert.equal(verified.adUnitId, "1234567890");
});

test("AdMob dashboard test transaction IDs are accepted", async () => {
  const keys = keyPair();
  const verify = createAdMobSsvVerifier({ loadKeys: async () => new Map([["123", keys.publicKey]]) });
  const verified = await verify(callback(keys.privateKey, { transactionId: "123456789" }), EXPECTED);
  assert.equal(verified.transactionId, "123456789");
  await assert.rejects(verify(callback(keys.privateKey, { transactionId: "not-hex" }), EXPECTED), {
    status: 400,
    message: "Invalid SSV transaction_id."
  });
});

test("SSV rejects invalid signatures, malformed payloads, and unexpected rewards", async () => {
  const trusted = keyPair();
  const attacker = keyPair();
  const verify = createAdMobSsvVerifier({ loadKeys: async () => new Map([["123", trusted.publicKey]]) });
  await assert.rejects(verify(callback(attacker.privateKey), EXPECTED), { status: 400, message: "Invalid SSV signature." });
  await assert.rejects(verify(callback(trusted.privateKey, { adUnitId: "1111111111" }), EXPECTED), { status: 403 });
  await assert.rejects(verify(callback(trusted.privateKey, { rewardAmount: "2" }), EXPECTED), { status: 403 });
  await assert.rejects(verify(callback(trusted.privateKey, { rewardItem: "coins" }), EXPECTED), { status: 403 });
  await assert.rejects(verify(callback(trusted.privateKey, { extra: [["transaction_id", "c".repeat(32)]] }), EXPECTED), { status: 400 });
  await assert.rejects(verify(`${callback(trusted.privateKey)}&unsigned=value`, EXPECTED), { status: 400 });
});

test("unknown verifier key triggers one controlled refresh for key rotation", async () => {
  const oldKeys = keyPair();
  const rotatedKeys = keyPair();
  let loads = 0;
  const verify = createAdMobSsvVerifier({ loadKeys: async () => {
    loads += 1;
    return loads === 1 ? new Map([["123", oldKeys.publicKey]]) : new Map([["456", rotatedKeys.publicKey]]);
  } });
  await verify(callback(oldKeys.privateKey), EXPECTED);
  await verify(callback(rotatedKeys.privateKey, { keyId: "456" }), EXPECTED);
  assert.equal(loads, 2);
});

test("verifier keys are cached for twelve hours and refresh failures fail closed", async () => {
  let now = 0;
  let loads = 0;
  let failRefresh = false;
  const keys = keyPair();
  const verify = createAdMobSsvVerifier({ now: () => now, loadKeys: async () => {
    loads += 1;
    if (failRefresh) throw new Error("offline");
    return new Map([["123", keys.publicKey]]);
  } });
  await verify(callback(keys.privateKey), EXPECTED);
  await verify(callback(keys.privateKey, { transactionId: "c".repeat(32) }), EXPECTED);
  assert.equal(loads, 1);
  now = 12 * 60 * 60 * 1000;
  failRefresh = true;
  await assert.rejects(verify(callback(keys.privateKey, { transactionId: "d".repeat(32) }), EXPECTED), { status: 503 });
  assert.equal(loads, 2);
});

test("verified callbacks grant once and reject expired sessions", async () => {
  let now = 1_000_000;
  const keys = keyPair();
  const verify = createAdMobSsvVerifier({ loadKeys: async () => new Map([["123", keys.publicKey]]), now: () => now });
  const store = new MemoryStore(() => now);
  await store.register("owner", "public-key");

  const session = await store.createAdSession("owner", "first", 60);
  const first = await verify(callback(keys.privateKey, { sessionId: session.id, transactionId: "1".repeat(32) }), EXPECTED);
  assert.equal((await store.grantAdReward(first.sessionId, first.transactionId, { provider: "admob" })).duplicate, false);
  assert.equal((await store.grantAdReward(first.sessionId, first.transactionId, { provider: "admob" })).duplicate, true);
  const replay = await verify(callback(keys.privateKey, { sessionId: session.id, transactionId: "2".repeat(32) }), EXPECTED);
  assert.equal((await store.grantAdReward(replay.sessionId, replay.transactionId, { provider: "admob" })).duplicate, true);
  assert.equal((await store.credits("owner")).availableSeconds, 60);

  const expired = await store.createAdSession("owner", "expired", 60);
  now = expired.expiresAt;
  const late = await verify(callback(keys.privateKey, { sessionId: expired.id, transactionId: "3".repeat(32) }), EXPECTED);
  await assert.rejects(store.grantAdReward(late.sessionId, late.transactionId, { provider: "admob" }), { status: 410 });
  assert.equal((await store.credits("owner")).availableSeconds, 60);
});
