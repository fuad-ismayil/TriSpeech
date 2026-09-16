import crypto from "node:crypto";

const VERIFIER_KEYS_URL = "https://www.gstatic.com/admob/reward/verifier-keys.json";
const KEY_CACHE_TTL_MS = 12 * 60 * 60 * 1000;
const UNKNOWN_KEY_REFRESH_COOLDOWN_MS = 5 * 60 * 1000;
const REQUIRED_SIGNED_PARAMETERS = [
  "ad_network", "ad_unit", "custom_data", "reward_amount", "reward_item", "timestamp", "transaction_id"
];

export function createAdMobSsvVerifier({ loadKeys = fetchVerifierKeys, now = () => Date.now() } = {}) {
  let cache = { expiresAt: 0, keys: new Map() };
  let refreshPromise;
  let lastUnknownKeyRefreshAt = -Infinity;

  async function keys(force = false) {
    const currentTime = now();
    if (!force && cache.expiresAt > currentTime) return cache.keys;
    if (!refreshPromise) {
      refreshPromise = Promise.resolve(loadKeys()).then((loaded) => {
        const verified = validateVerifierKeys(loaded);
        cache = { keys: verified, expiresAt: now() + KEY_CACHE_TTL_MS };
        return verified;
      }).finally(() => { refreshPromise = undefined; });
    }
    return refreshPromise;
  }

  return async function verifyAdMobSsv(originalUrl, expected) {
    const callback = parseCallback(originalUrl);
    let verifierKeys;
    try {
      verifierKeys = await keys();
      if (!verifierKeys.has(callback.keyId) && now() - lastUnknownKeyRefreshAt >= UNKNOWN_KEY_REFRESH_COOLDOWN_MS) {
        lastUnknownKeyRefreshAt = now();
        verifierKeys = await keys(true);
      }
    } catch (error) {
      throw unavailable("Unable to refresh AdMob verifier keys.", error);
    }

    const publicKey = verifierKeys.get(callback.keyId);
    if (!publicKey) throw invalid("Unknown SSV verifier key.");
    let valid = false;
    try {
      valid = crypto.verify("sha256", Buffer.from(callback.signedData, "utf8"), publicKey, Buffer.from(callback.signature, "base64url"));
    } catch {
      // Malformed signatures fail closed just like a cryptographic mismatch.
    }
    if (!valid) throw invalid("Invalid SSV signature.");
    validateExpectedReward(callback, expected);
    return {
      sessionId: callback.customData,
      transactionId: callback.transactionId,
      adUnitId: callback.adUnitId,
      rewardAmount: callback.rewardAmount,
      rewardItem: callback.rewardItem,
      timestamp: callback.timestamp
    };
  };
}

export const verifyAdMobSsv = createAdMobSsvVerifier();

function parseCallback(originalUrl) {
  const query = String(originalUrl).split("?", 2)[1] ?? "";
  const marker = "&signature=";
  const signatureAt = query.indexOf(marker);
  if (signatureAt < 0) throw invalid("Missing SSV signature.");
  const signedData = query.slice(0, signatureAt);
  const authentication = query.slice(signatureAt + 1);
  const authenticationMatch = /^signature=([A-Za-z0-9_-]+)&key_id=(\d+)$/.exec(authentication);
  if (!authenticationMatch) throw invalid("Malformed SSV authentication parameters.");

  const params = new URLSearchParams(signedData);
  for (const name of REQUIRED_SIGNED_PARAMETERS) {
    if (params.getAll(name).length !== 1 || !params.get(name)) throw invalid(`Invalid SSV ${name}.`);
  }
  const allNames = [...params.keys()];
  if (new Set(allNames).size !== allNames.length) throw invalid("Duplicate SSV parameter.");

  const customData = params.get("custom_data");
  const transactionId = params.get("transaction_id");
  const rewardAmount = params.get("reward_amount");
  const timestamp = params.get("timestamp");
  if (!/^[a-f0-9]{64}$/.test(customData)) throw invalid("Invalid SSV custom_data.");
  if (!/^[a-fA-F0-9]{16,128}$/.test(transactionId)) throw invalid("Invalid SSV transaction_id.");
  if (!/^\d+$/.test(params.get("ad_unit"))) throw invalid("Invalid SSV ad_unit.");
  if (!/^\d+$/.test(rewardAmount) || BigInt(rewardAmount) <= 0n) throw invalid("Invalid SSV reward_amount.");
  if (!/^\d+$/.test(timestamp) || !Number.isSafeInteger(Number(timestamp))) throw invalid("Invalid SSV timestamp.");
  if (!/^\d+$/.test(params.get("ad_network"))) throw invalid("Invalid SSV ad_network.");

  return {
    signedData,
    signature: authenticationMatch[1],
    keyId: authenticationMatch[2],
    customData,
    transactionId,
    adUnitId: params.get("ad_unit"),
    rewardAmount,
    rewardItem: params.get("reward_item"),
    timestamp: Number(timestamp)
  };
}

function validateExpectedReward(callback, expected) {
  if (!expected?.adUnitId || !expected?.rewardAmount || !expected?.rewardItem) {
    throw unavailable("AdMob SSV reward validation is not configured.");
  }
  if (callback.adUnitId !== expected.adUnitId) throw forbidden("Unexpected AdMob ad unit.");
  if (callback.rewardAmount !== expected.rewardAmount || callback.rewardItem !== expected.rewardItem) {
    throw forbidden("Unexpected AdMob reward.");
  }
}

async function fetchVerifierKeys() {
  const response = await fetch(VERIFIER_KEYS_URL, { signal: AbortSignal.timeout(10_000) });
  if (!response.ok) throw new Error(`AdMob verifier key request failed (${response.status}).`);
  const data = await response.json();
  return new Map((data.keys ?? []).map((item) => [String(item.keyId ?? item.key_id), item.pem]));
}

function validateVerifierKeys(keys) {
  if (!(keys instanceof Map) || keys.size === 0) throw new Error("No AdMob verifier keys were returned.");
  const verified = new Map();
  for (const [keyId, value] of keys) {
    if (!/^\d+$/.test(String(keyId))) throw new Error("AdMob returned an invalid verifier key ID.");
    const key = value?.type === "public" ? value : crypto.createPublicKey(value);
    if (key.asymmetricKeyType !== "ec" || key.asymmetricKeyDetails?.namedCurve !== "prime256v1") {
      throw new Error("AdMob returned an unsupported verifier key.");
    }
    verified.set(String(keyId), key);
  }
  return verified;
}

function error(message, status, cause) { const result = new Error(message, cause ? { cause } : undefined); result.status = status; return result; }
function invalid(message) { return error(message, 400); }
function forbidden(message) { return error(message, 403); }
function unavailable(message, cause) { return error(message, 503, cause); }
