import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { Storage } from "@google-cloud/storage";

export class UploadStorage {
  constructor(bucketName = process.env.UPLOAD_BUCKET) {
    this.bucketName = bucketName;
    this.storage = bucketName ? new Storage() : null;
    this.localRoot = path.join(os.tmpdir(), "trispeech-uploads");
  }

  objectName(uploadId) { return `temporary-audio/${uploadId}`; }

  async write(uploadId, buffer) {
    if (this.storage) {
      await this.storage.bucket(this.bucketName).file(this.objectName(uploadId)).save(buffer, { resumable: false, validation: "crc32c" });
      return;
    }
    await fs.promises.mkdir(this.localRoot, { recursive: true });
    await fs.promises.writeFile(path.join(this.localRoot, uploadId), buffer);
  }

  async localFile(uploadId) {
    if (!this.storage) return path.join(this.localRoot, uploadId);
    const target = path.join(os.tmpdir(), `trispeech-${uploadId}`);
    await this.storage.bucket(this.bucketName).file(this.objectName(uploadId)).download({ destination: target });
    return target;
  }

  async remove(uploadId, localFile) {
    if (this.storage) await this.storage.bucket(this.bucketName).file(this.objectName(uploadId)).delete({ ignoreNotFound: true });
    await fs.promises.rm(localFile ?? path.join(this.localRoot, uploadId), { force: true });
  }
}
