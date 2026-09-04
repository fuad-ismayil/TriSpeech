import test from "node:test";
import assert from "node:assert/strict";
import crypto from "node:crypto";
import { verifyRegistrationRequest } from "../src/auth.js";

test("installation registration proves possession of its private key", () => {
  const { publicKey, privateKey } = crypto.generateKeyPairSync("ec", { namedCurve: "prime256v1" });
  const installationId = crypto.randomUUID();
  const body = JSON.stringify({ installationId, publicKey: publicKey.export({ format: "der", type: "spki" }).toString("base64url"), platform: "android" });
  const timestamp = String(Date.now()); const nonce = crypto.randomUUID(); const idempotency = installationId;
  const bodyHash = crypto.createHash("sha256").update(body).digest("hex");
  const canonical = ["POST", "/v1/installations/register", timestamp, nonce, idempotency, bodyHash].join("\n");
  const signature = crypto.sign("sha256", Buffer.from(canonical), privateKey).toString("base64url");
  const headers = new Map([
    ["x-installation-id", installationId], ["x-request-timestamp", timestamp], ["x-request-nonce", nonce],
    ["idempotency-key", idempotency], ["x-body-sha256", bodyHash], ["x-request-signature", signature]
  ]);
  const request = {
    method: "POST", path: "/v1/installations/register", body: JSON.parse(body), rawBody: Buffer.from(body),
    get: (name) => headers.get(name.toLowerCase())
  };
  assert.equal(verifyRegistrationRequest(request).installationId, installationId);
  request.rawBody = Buffer.from(`${body} `);
  assert.throws(() => verifyRegistrationRequest(request), /Invalid registration request/);
});
