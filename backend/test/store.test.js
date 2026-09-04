import test from "node:test";
import assert from "node:assert/strict";
import { MemoryStore } from "../src/store.js";

test("six sessions per rolling hour is a velocity guard, not a daily cap", async () => {
  let now = 1_000_000;
  const store = new MemoryStore(() => now);
  await store.register("installation", "public-key");
  for (let index = 0; index < 6; index += 1) {
    const session = await store.createAdSession("installation", `ad-${index}`, 30);
    await store.grantAdReward(session.id, `transaction-${index}`);
    now += 1_000;
  }
  await assert.rejects(store.createAdSession("installation", "ad-7", 30), (error) => error.status === 429);
  now += 60 * 60 * 1000;
  const later = await store.createAdSession("installation", "ad-later", 30);
  assert.equal(later.rewardSeconds, 30);
});

test("SSV transaction and session replay cannot grant twice", async () => {
  const store = new MemoryStore(() => 1_000_000);
  await store.register("installation", "public-key");
  const session = await store.createAdSession("installation", "ad", 60);
  await store.grantAdReward(session.id, "transaction");
  await store.grantAdReward(session.id, "transaction");
  assert.equal((await store.credits("installation")).availableSeconds, 60);
});

test("job creation and credit reservation are idempotent", async () => {
  const store = new MemoryStore(() => 1_000_000);
  await store.register("installation", "public-key");
  const session = await store.createAdSession("installation", "ad", 90);
  await store.grantAdReward(session.id, "transaction");
  const upload = await store.createUpload("installation", "upload", { fileName: "a.wav", sizeBytes: 10, mediaType: "audio/wav" });
  await assert.rejects(store.completeUpload(upload.id, "installation"), (error) => error.status === 409);
  await store.markUploadStored(upload.id, "installation");
  await store.completeUpload(upload.id, "installation");
  const first = await store.createJob("installation", "job", { uploadId: upload.id, language: "auto" });
  const duplicate = await store.createJob("installation", "job", { uploadId: upload.id, language: "auto" });
  assert.equal(first.id, duplicate.id);
  await store.reserveJob(first.id, 30);
  await store.reserveJob(first.id, 30);
  assert.equal((await store.credits("installation")).availableSeconds, 60);
});

test("development preview can process a job without ad credit", async () => {
  const store = new MemoryStore(() => 1_000_000);
  await store.register("preview", "public-key");
  const upload = await store.createUpload("preview", "upload", { fileName: "a.wav", sizeBytes: 10, mediaType: "audio/wav" });
  await store.markUploadStored(upload.id, "preview");
  await store.completeUpload(upload.id, "preview");
  const job = await store.createJob("preview", "job", { uploadId: upload.id, language: "auto" });
  const started = await store.reserveJob(job.id, 45, false);
  assert.equal(started.status, "processing");
  assert.equal((await store.credits("preview")).availableSeconds, 0);
});

test("upload slots expire and completed uploads cannot cross installations or jobs", async () => {
  let now = 1_000_000;
  const store = new MemoryStore(() => now);
  await store.register("owner", "owner-key");
  await store.register("other", "other-key");
  const first = await store.createUpload("owner", "first", { fileName: "a.mp3", sizeBytes: 10, mediaType: "audio/mpeg" });
  await store.createUpload("owner", "second", { fileName: "b.mp3", sizeBytes: 10, mediaType: "audio/mpeg" });
  await assert.rejects(store.createUpload("owner", "third", { fileName: "c.mp3", sizeBytes: 10, mediaType: "audio/mpeg" }), (error) => error.status === 429);
  await store.markUploadStored(first.id, "owner");
  await store.completeUpload(first.id, "owner");
  await assert.rejects(store.createJob("other", "stolen", { uploadId: first.id, language: "auto" }), (error) => error.status === 404);
  await store.createJob("owner", "job-one", { uploadId: first.id, language: "auto" });
  await assert.rejects(store.createJob("owner", "job-two", { uploadId: first.id, language: "auto" }), (error) => error.status === 409);
  now += 24 * 60 * 60 * 1000 + 1;
  const replacement = await store.createUpload("owner", "replacement", { fileName: "d.mp3", sizeBytes: 10, mediaType: "audio/mpeg" });
  assert.equal(replacement.installationId, "owner");
});
