import crypto from "node:crypto";

const baseUrl = process.argv[2]?.replace(/\/$/, "");
if (!baseUrl?.startsWith("https://")) throw new Error("Usage: node scripts/probe-config.mjs https://backend.example");

const installationId = crypto.randomUUID();
const { publicKey, privateKey } = crypto.generateKeyPairSync("ec", { namedCurve: "prime256v1" });
const encodedPublicKey = publicKey.export({ type: "spki", format: "der" }).toString("base64url");
const emptyHash = crypto.createHash("sha256").update(Buffer.alloc(0)).digest("hex");

function signedHeaders(method, path, body, idempotencyKey = crypto.randomUUID()) {
  const timestamp = Date.now().toString();
  const nonce = crypto.randomUUID();
  const bodyHash = crypto.createHash("sha256").update(body ?? Buffer.alloc(0)).digest("hex");
  const canonical = [method, path, timestamp, nonce, idempotencyKey, bodyHash].join("\n");
  return {
    "Content-Type": "application/json",
    "X-Installation-Id": installationId,
    "X-Request-Timestamp": timestamp,
    "X-Request-Nonce": nonce,
    "Idempotency-Key": idempotencyKey,
    "X-Body-SHA256": bodyHash,
    "X-Request-Signature": crypto.sign("sha256", Buffer.from(canonical), privateKey).toString("base64url")
  };
}

const registrationPath = "/v1/installations/register";
const registrationBody = Buffer.from(JSON.stringify({ installationId, publicKey: encodedPublicKey, platform: "diagnostic" }));
const registration = await fetch(`${baseUrl}${registrationPath}`, {
  method: "POST",
  headers: signedHeaders("POST", registrationPath, registrationBody, installationId),
  body: registrationBody
});
if (!registration.ok) throw new Error(`Registration failed: ${registration.status} ${await registration.text()}`);

const configPath = "/v1/config";
const config = await fetch(`${baseUrl}${configPath}`, {
  headers: { ...signedHeaders("GET", configPath, null), "X-Body-SHA256": emptyHash }
});
if (!config.ok) throw new Error(`Config failed: ${config.status} ${await config.text()}`);
console.log(JSON.stringify(await config.json(), null, 2));
