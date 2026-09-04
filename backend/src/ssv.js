import crypto from "node:crypto";

let keyCache = { expiresAt: 0, keys: new Map() };

export async function verifyAdMobSsv(originalUrl, now = Date.now()) {
  const query = originalUrl.split("?")[1] ?? "";
  const marker = "&signature=";
  const signatureAt = query.indexOf(marker);
  if (signatureAt < 0) throw invalid("Missing SSV signature.");
  const signedData = query.slice(0, signatureAt);
  const params = new URLSearchParams(query);
  const signature = params.get("signature");
  const keyId = params.get("key_id");
  const customData = params.get("custom_data");
  const transactionId = params.get("transaction_id");
  if (!signature || !keyId || !customData || !transactionId) throw invalid("Incomplete SSV callback.");
  const keys = await verifierKeys(now);
  const pem = keys.get(String(keyId));
  if (!pem) throw invalid("Unknown SSV verifier key.");
  const valid = crypto.verify("sha256", Buffer.from(signedData), pem, Buffer.from(signature, "base64url"));
  if (!valid) throw invalid("Invalid SSV signature.");
  return { sessionId: customData, transactionId };
}

async function verifierKeys(now) {
  if (keyCache.expiresAt > now) return keyCache.keys;
  const response = await fetch("https://www.gstatic.com/admob/reward/verifier-keys.json", { signal: AbortSignal.timeout(10_000) });
  if (!response.ok) throw new Error("Unable to refresh AdMob verifier keys.");
  const data = await response.json();
  const keys = new Map((data.keys ?? []).map((item) => [String(item.keyId ?? item.key_id), item.pem]));
  keyCache = { keys, expiresAt: now + 12 * 60 * 60 * 1000 };
  return keys;
}

function invalid(message) { const error = new Error(message); error.status = 400; return error; }
