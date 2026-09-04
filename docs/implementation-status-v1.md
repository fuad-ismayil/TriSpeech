# TriSpeech V1 implementation status

## Implemented

- Free-only V1 product surface with no billing, subscriptions, sign-in, cloud history, paywalls, or paid model selector.
- Free Native STT and local Whisper, local audio import including Android-decodable MP3/M4A/FLAC/OGG/WebM/WAV, unlocked model downloads, history/playback/export, and separately confirmed local-data/model cleanup.
- EN/AZ/TR/RU app locale selector, light/dark theme control, voluntary support entry/prompts, corrected support URL, online disclosure, privacy choices, and in-app policy summaries.
- Anonymous Keystore-backed installation identity, signed/replay-protected requests, optional production App Check/Play Integrity, server-authoritative credit, AdMob SSV, dynamic market rewards, and no daily ad cap.
- Backend-only numbered TokenMix credentials, ordered health handling across all configured keys for definitive key-specific failures, Mini-only provider requests, 25 MiB/10-minute limits, upload/job ownership and idempotency, credit/provider-cost reservation, queue/concurrency limits, and kill-switch configuration. Ambiguous outcomes are never retried automatically.
- WorkManager-backed file transcription that survives process loss, uses one operation ID end to end, and saves idempotently through Room schema v2. Existing Room v1 history migrates explicitly without destructive fallback.
- API 36 release configuration, release cleartext disabled, Android cloud backup/device transfer disabled, R8 release build, backend tests, source/artifact secret scanning, and release runbooks/checklists.

## External release blockers

- Revoke and replace every TokenMix key pasted into chat. Configure fresh keys only in Secret Manager.
- Deploy and secure GCP services/IAM, Firestore, Storage lifecycle, Cloud Tasks, App Check, budgets, monitoring, and trusted market injection.
- Replace the release backend URL and AdMob IDs, add the uncommitted Firebase configuration, configure and validate AdMob SSV/UMP, and create the Play upload signing setup.
- Publish legally reviewed Privacy Policy and Terms; finish Data Safety, ads, content rating, retention/provider disclosures, and open-source/model notices.
- Human-review all four translations and complete physical-device/emulator testing, including the Room migration instrumentation test and the process-death/ad/provider failure matrix.
- Measure real finalized eCPM and provider/cloud cost before enabling each market; the example economics are not production facts.
