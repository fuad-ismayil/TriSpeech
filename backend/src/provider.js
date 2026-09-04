import fs from "node:fs";

export class AmbiguousProviderError extends Error {}

export class ProviderPool {
  constructor({ keys, endpoint, timeoutMs = 120000, transport = fetch, now = () => Date.now() }) {
    this.keys = keys.filter((key) => key.value).sort((a, b) => a.priority - b.priority);
    this.endpoint = endpoint;
    this.timeoutMs = timeoutMs;
    this.transport = transport;
    this.now = now;
    this.health = new Map(this.keys.map((key) => [key.alias, { enabled: true, failures: 0, cooldownUntil: 0 }]));
  }

  async transcribe({ filePath, fileName, mediaType, language, logicalJobId }) {
    const candidates = this.keys.filter((key) => {
      const health = this.health.get(key.alias);
      return health.enabled && health.cooldownUntil <= this.now();
    });
    if (!candidates.length) throw new Error("Online transcription is temporarily unavailable.");

    let lastError;
    for (let index = 0; index < candidates.length; index += 1) {
      const key = candidates[index];
      try {
        const result = await this.#call(key, { filePath, fileName, mediaType, language, logicalJobId });
        this.health.set(key.alias, { enabled: true, failures: 0, cooldownUntil: 0, lastSuccess: this.now() });
        return { ...result, keyAlias: key.alias };
      } catch (error) {
        lastError = error;
        if (error instanceof AmbiguousProviderError) throw error;
        if (![401, 403, 429].includes(error.status)) throw error;
        if (error.status === 429 && !error.keySpecific) throw error;
        const health = this.health.get(key.alias);
        if (error.status === 401 || error.status === 403) health.enabled = false;
        if (error.status === 429) health.cooldownUntil = this.now() + 15 * 60 * 1000;
        health.failures += 1;
      }
    }
    throw lastError ?? new Error("All provider credentials are unavailable.");
  }

  async #call(key, { filePath, fileName, mediaType, language, logicalJobId }) {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), this.timeoutMs);
    try {
      const form = new FormData();
      form.append("file", new Blob([await fs.promises.readFile(filePath)], { type: mediaType }), fileName);
      form.append("model", "gpt-4o-mini-transcribe");
      form.append("response_format", "verbose_json");
      if (language && language !== "auto") form.append("language", language);
      const response = await this.transport(this.endpoint, {
        method: "POST",
        headers: { Authorization: `Bearer ${key.value}`, "X-TriSpeech-Job": logicalJobId },
        body: form,
        signal: controller.signal
      });
      const text = await response.text();
      if (!response.ok) {
        const error = new Error(`Provider rejected request (${response.status}).`);
        error.status = response.status;
        if (response.status === 429) {
          const scope = response.headers?.get?.("x-ratelimit-scope")?.toLowerCase();
          const payload = parseJson(text);
          const code = String(payload?.error?.code ?? payload?.code ?? "").toLowerCase();
          error.keySpecific = scope === "key" || ["key_quota_exceeded", "api_key_rate_limit"].includes(code);
        }
        throw error;
      }
      const json = JSON.parse(text);
      return {
        text: json.text ?? "",
        language: json.language ?? language ?? "auto",
        segments: (json.segments ?? []).map((segment) => ({
          startMs: Math.round((segment.start ?? 0) * 1000),
          endMs: Math.round((segment.end ?? 0) * 1000),
          text: String(segment.text ?? "").trim()
        }))
      };
    } catch (error) {
      if (error.name === "AbortError" || error instanceof TypeError) {
        throw new AmbiguousProviderError("Provider outcome is ambiguous; no automatic retry was made.");
      }
      throw error;
    } finally {
      clearTimeout(timeout);
    }
  }
}

function parseJson(value) { try { return JSON.parse(value); } catch { return null; } }
