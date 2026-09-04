# TriSpeech V1: Free-first release

This document is the implementation contract for the first public release.

## Product

- Native Android STT, Offline Whisper, model downloads, local history, playback, import, and export are free.
- GPT-4o Mini is the only production online model and requires credit from a server-verified rewarded ad.
- There are no accounts, payments, subscriptions, paid entitlements, cloud history, or cross-device restore.
- The interface ships in English, Azerbaijani, Turkish, and Russian. The transcription-language catalogue remains independent.
- Developer support is voluntary and links to <https://kofe.al/@fuadismayil>.

## Online access

The Android app never contains provider credentials and never grants usable credit from a client ad callback. A minimal Cloud Run backend registers an anonymous installation public key, creates an opaque ad session, verifies AdMob SSV, stores credit in seconds, validates uploaded audio, reserves credit, and proxies the request to TokenMix.

There is no daily product cap. An installation may create at most six ad sessions in a rolling hour, with one active session. Market rewards are configured between 30 and 120 seconds, expire after 24 hours, and are calculated from conservative finalized eCPM and all-in transcription cost.

If ads are blocked or unavailable, online transcription is unavailable. Offline Whisper, Native STT, history, and export continue to work.

## Operations and release gates

- TokenMix keys are injected into the backend as numbered `TOKENMIX_KEY_N` secrets; they never enter Android source, resources, BuildConfig, logs, or artifacts. Lower numbers have higher priority and the pool has no application-level key-count limit.
- Keys previously pasted into chat or used in APK-producing builds must be replaced before production.
- Release cleartext traffic is disabled. Emulator HTTP is debug-only.
- Room migrations must be explicit; local history may not be destructively recreated.
- API/provider budgets, request idempotency, upload limits, job concurrency, and an online kill switch prevent runaway charges.
- Legal release artifacts include Privacy Policy, Terms, Data Safety, ads/UMP disclosures, cloud-processing retention, and open-source notices.

## Deferred

The future account, billing, subscriptions, purchase restoration, cloud backup, signed paid entitlements, multi-device account policy, support dashboard, and advanced fraud controls are intentionally deferred to the V2 roadmap.

## Locked technical defaults

- API: installation registration/config/credits, ad-session creation, temporary upload/content/complete, transcription job create/status/cancel, and AdMob SSV callback.
- Every client mutation is bound to the anonymous installation ID, Android Keystore P-256 signature, timestamp, nonce, body hash, idempotency key, and—when production is enabled—App Check/Play Integrity.
- Only `gpt-4o-mini-transcribe` is accepted. TokenMix uses the OpenAI-compatible `/v1/audio/transcriptions` endpoint; `/v1/audio/speech` is text-to-speech and is not used for STT.
- Online media is limited to 25 MiB and 10 minutes. Each installation has at most one processing job, two outstanding jobs/uploads, and five job/upload creations per ten minutes. Provider concurrency is capped at 20 or the contracted lower limit.
- A maximum of six ad-session creations per rolling hour guards loops/fraud. There is no daily product cap or daily refill counter.
- Ad rewards are 30–120 seconds, rounded down to 15-second steps from conservative eCPM, all-in minute cost, and a 25% target contribution margin. Issued rewards last 24 hours and never change retroactively.
- AdMob SSV transaction IDs and sessions are single-use. A client reward callback only enters “verifying”; it never changes credit.
- Provider fallback occurs once only after definitive key-specific 401/403/429 results. Ambiguous outcomes are held for reconciliation and never blindly retried.
- Source audio is retained locally when an online operation fails. Server uploads are removed after terminal processing, with a 24-hour bucket lifecycle as a backstop.
- File and Whisper transcription runs through persistent WorkManager jobs. The WorkManager UUID is reused as the backend operation identity, server upload/job idempotency scope, and unique local Room operation ID, so a retry or process restart cannot create a second paid provider job or duplicate history row.
- Room schema v2 adds the nullable unique operation ID through an explicit v1-to-v2 migration. Existing transcripts remain unchanged and receive a null operation ID.
- Abandoned server upload reservations expire after 24 hours. Upload ownership, completion, expiry, and one-upload-to-one-job binding are enforced in the persistence layer as well as the HTTP route.
- Release Android traffic is HTTPS-only, local backup/device transfer is disabled, Room destructive migration is prohibited, and API 36 is the release target.

## Implementation locations

- Android anonymous identity and signed backend client: `app/src/main/java/com/trispeech/app/network/`
- Rewarded ad SSV client state: `app/src/main/java/com/trispeech/app/ads/`
- Free-first selectors, disclosures, support prompts, and four-locale UI: `app/src/main/java/com/trispeech/app/ui/` and `app/src/main/res/values*/`
- Persistent transcription worker and Room migration: `app/src/main/java/com/trispeech/app/transcription/TranscriptionWorker.kt`, `app/src/main/java/com/trispeech/app/data/`, and `app/schemas/`
- Cloud Run API/worker, Firestore store, GCS uploads, Cloud Tasks dispatch, provider pool, SSV verification, and tests: `backend/`
- Deployment blockers and test matrix: `docs/release-checklist-v1.md`
- Single-owner incident procedure: `docs/operations-v1.md`
