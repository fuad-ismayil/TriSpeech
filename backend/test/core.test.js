import test from "node:test";
import assert from "node:assert/strict";
import { marketReward, rewardSeconds } from "../src/economics.js";
import { loadTokenMixKeys } from "../src/config.js";
import { AmbiguousProviderError, ProviderPool } from "../src/provider.js";

test("reward uses 15-second steps and configured bounds", () => {
  assert.equal(rewardSeconds({ conservativeEcpm: 2.34, allInMinuteCost: 0.0035 }), 30);
  assert.equal(rewardSeconds({ conservativeEcpm: 50, allInMinuteCost: 0.0035 }), 120);
  assert.equal(marketReward("LOW", { LOW: { conservativeEcpm: 0.2, allInMinuteCost: 0.0035 } }, 0.25), 0);
});

test("numbered TokenMix secrets are ordered, unlimited, and deduplicated", () => {
  const keys = loadTokenMixKeys({
    TOKENMIX_KEY_12: "last",
    TOKENMIX_KEY_1: "first",
    TOKENMIX_KEY_3: "first",
    TOKENMIX_KEY_2: "second",
    TOKENMIX_KEY_0: "ignored",
    TOKENMIX_PRIMARY_KEY: "legacy-is-ignored-when-numbered-keys-exist"
  });
  assert.deepEqual(keys.map(({ alias, priority }) => ({ alias, priority })), [
    { alias: "tokenmix-key-1", priority: 1 },
    { alias: "tokenmix-key-2", priority: 2 },
    { alias: "tokenmix-key-12", priority: 12 }
  ]);
});

test("definitive credential failures continue through every configured key", async () => {
  let calls = 0;
  const pool = new ProviderPool({
    keys: [
      { alias: "key-1", value: "a", priority: 1 },
      { alias: "key-2", value: "b", priority: 2 },
      { alias: "key-3", value: "c", priority: 3 },
      { alias: "key-4", value: "d", priority: 4 }
    ],
    endpoint: "https://provider.invalid",
    transport: async () => {
      calls += 1;
      return calls < 4
        ? { ok: false, status: 401, text: async () => "bad key" }
        : { ok: true, status: 200, text: async () => JSON.stringify({ text: "ok" }) };
    }
  });
  const result = await pool.transcribe({ filePath: new URL(import.meta.url), fileName: "x.wav", mediaType: "audio/wav", logicalJobId: "job" });
  assert.equal(result.text, "ok");
  assert.equal(result.keyAlias, "key-4");
  assert.equal(calls, 4);
});

test("ambiguous outcome is never sent to fallback", async () => {
  let calls = 0;
  const pool = new ProviderPool({
    keys: [{ alias: "primary", value: "a", priority: 1 }, { alias: "secondary", value: "b", priority: 2 }],
    endpoint: "https://provider.invalid",
    transport: async () => { calls += 1; throw new TypeError("socket closed"); }
  });
  await assert.rejects(
    pool.transcribe({ filePath: new URL(import.meta.url), fileName: "x.wav", mediaType: "audio/wav", logicalJobId: "job" }),
    AmbiguousProviderError
  );
  assert.equal(calls, 1);
});

test("account-wide 429 does not rotate keys but a key-scoped 429 does", async () => {
  let calls = 0;
  const globalPool = new ProviderPool({
    keys: [{ alias: "primary", value: "a", priority: 1 }, { alias: "secondary", value: "b", priority: 2 }],
    endpoint: "https://provider.invalid",
    transport: async () => { calls += 1; return { ok: false, status: 429, headers: { get: () => "account" }, text: async () => "{}" }; }
  });
  await assert.rejects(globalPool.transcribe({ filePath: new URL(import.meta.url), fileName: "x.wav", mediaType: "audio/wav", logicalJobId: "global" }));
  assert.equal(calls, 1);

  calls = 0;
  const keyPool = new ProviderPool({
    keys: [{ alias: "primary", value: "a", priority: 1 }, { alias: "secondary", value: "b", priority: 2 }],
    endpoint: "https://provider.invalid",
    transport: async () => {
      calls += 1;
      return calls === 1
        ? { ok: false, status: 429, headers: { get: () => "key" }, text: async () => "{}" }
        : { ok: true, status: 200, headers: { get: () => null }, text: async () => '{"text":"ok","segments":[]}' };
    }
  });
  const result = await keyPool.transcribe({ filePath: new URL(import.meta.url), fileName: "x.wav", mediaType: "audio/wav", logicalJobId: "key" });
  assert.equal(result.text, "ok");
  assert.equal(calls, 2);
});
