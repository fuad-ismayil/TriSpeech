import crypto from "node:crypto";
import { createRemoteJWKSet, jwtVerify } from "jose";

const EMPTY_HASH = crypto.createHash("sha256").update(Buffer.alloc(0)).digest("hex");
const fromBase64Url = (value) => Buffer.from(value, "base64url");
const hash = (body) => crypto.createHash("sha256").update(body ?? Buffer.alloc(0)).digest("hex");

export function authMiddleware(store, { requireAppCheck = false } = {}) {
  return async (req, res, next) => {
    try {
      const installationId = req.get("X-Installation-Id");
      const timestamp = req.get("X-Request-Timestamp");
      const nonce = req.get("X-Request-Nonce");
      const idempotencyKey = req.get("Idempotency-Key");
      const suppliedHash = req.get("X-Body-SHA256") ?? EMPTY_HASH;
      const signature = req.get("X-Request-Signature");
      if (!installationId || !timestamp || !nonce || !idempotencyKey || !signature) throw httpError("Missing request authentication.", 401);
      if (Math.abs(Date.now() - Number(timestamp)) > 5 * 60 * 1000) throw httpError("Expired request.", 401);
      const rawBody = req.rawBody ?? Buffer.alloc(0);
      if (hash(rawBody) !== suppliedHash) throw httpError("Request body hash mismatch.", 401);
      const installation = await store.installation(installationId);
      if (!installation) throw httpError("Unknown installation.", 401);
      verifySignature(installation.publicKey, signature, [req.method, req.path, timestamp, nonce, idempotencyKey, suppliedHash].join("\n"));
      if (!(await store.consumeNonce(installationId, nonce))) throw httpError("Replayed request.", 409);
      await store.rateLimit(installationId, "all", 120, 60_000);
      if (req.path === "/v1/credits") await store.rateLimit(installationId, "credits", 30, 60_000);
      if (req.path === "/v1/config") await store.rateLimit(installationId, "config", 10, 60_000);
      if (requireAppCheck) {
        const token = req.get("X-Firebase-AppCheck");
        if (!token) throw httpError("App Check is required.", 401);
        await verifyAppCheck(token);
      }
      req.installationId = installationId;
      next();
    } catch (error) { next(error); }
  };
}

export function verifyRegistrationRequest(req) {
  const { installationId, publicKey } = req.body ?? {};
  const timestamp = req.get("X-Request-Timestamp"); const nonce = req.get("X-Request-Nonce");
  const idempotencyKey = req.get("Idempotency-Key"); const suppliedHash = req.get("X-Body-SHA256"); const signature = req.get("X-Request-Signature");
  if (!installationId || !publicKey || !timestamp || !nonce || !idempotencyKey || !suppliedHash || !signature) throw httpError("Invalid installation registration.", 400);
  if (installationId !== req.get("X-Installation-Id")) throw httpError("Installation ID mismatch.", 400);
  if (Math.abs(Date.now() - Number(timestamp)) > 5 * 60 * 1000 || hash(req.rawBody) !== suppliedHash) throw httpError("Invalid registration request.", 401);
  verifySignature(publicKey, signature, [req.method, req.path, timestamp, nonce, idempotencyKey, suppliedHash].join("\n"));
  return { installationId, publicKey };
}

function verifySignature(publicKey, signature, canonical) {
  const key = crypto.createPublicKey({ key: fromBase64Url(publicKey), format: "der", type: "spki" });
  if (!crypto.verify("sha256", Buffer.from(canonical), key, fromBase64Url(signature))) throw httpError("Invalid request signature.", 401);
}

export function httpError(message, status = 400) { const error = new Error(message); error.status = status; return error; }

const appCheckKeys = createRemoteJWKSet(new URL("https://firebaseappcheck.googleapis.com/v1/jwks"));
export async function verifyAppCheck(token) {
  const projectNumber = process.env.FIREBASE_PROJECT_NUMBER;
  if (!projectNumber) throw httpError("App Check verifier is not configured.", 503);
  await jwtVerify(token, appCheckKeys, {
    issuer: `https://firebaseappcheck.googleapis.com/${projectNumber}`,
    audience: `projects/${projectNumber}`,
    algorithms: ["RS256"]
  });
}
