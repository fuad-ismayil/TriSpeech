# TriSpeech V1 release checklist

The code can produce an AAB, but a public release is blocked until every item below is complete.

## Secrets and services

- Revoke both TokenMix credentials that were pasted into chat. Create fresh restricted production credentials and inject them into Secret Manager only.
- Supply `TRISPEECH_BACKEND_URL`, `TRISPEECH_ADMOB_APP_ID`, and `TRISPEECH_REWARDED_AD_UNIT_ID` through protected release Gradle properties/CI secrets.
- Add an uncommitted `app/google-services.json`, register the Play Integrity App Check provider, and set `REQUIRE_APP_CHECK=true` only after a closed-track build is verified.
- Configure AdMob SSV to the HTTPS `/callbacks/admob/ssv` endpoint. Test signed, duplicate, replayed, delayed, and invalid callbacks.
- Configure Firestore, private Cloud Storage, the 24-hour lifecycle backstop, Cloud Tasks rate ceilings, Cloud Run maximum instances, IAM/IAP, monitoring, logging exclusions, and budget alerts.
- Replace default market economics with finalized 30-day AdMob revenue and TokenMix invoice data. Pause markets that cannot sustainably fund 30 seconds.

## Product and policy

- Publish reviewed Privacy Policy and Terms at stable public URLs. The in-app summaries are not legal review.
- Complete Play Data Safety, ads declaration, UMP messages, target audience/content rating, provider/cloud-processing disclosures, retention/deletion disclosures, and open-source/model notices.
- Replace the placeholder AdMob application ID and test rewarded unit. Confirm production ads are never used in internal automated tests.
- Confirm support prompts fire after 5 successes, then each 20 additional successes, and at most once per 30 days.
- Human-review EN/AZ/TR/RU strings and test both themes, rotation, screen readers, and 200% font scale.

## Quality gates

- Run `gradlew testDebugUnitTest lintDebug compileDebugAndroidTestKotlin bundleRelease`.
- Run `npm test`, `npm run check`, and `npm audit --omit=dev` in `backend`.
- Scan source, Git history, APK/AAB contents, mapping files, logs, and CI artifacts for provider credentials.
- Verify Room migration tests against every released schema. Never add destructive fallback.
- Run the v1-to-v2 Room migration test on a device/emulator and verify persistent WorkManager transcription through process death, restart, cancellation, network loss, and duplicate enqueue attempts.
- Test recording/import/playback/export for WAV, MP3, M4A, OGG, FLAC, and WebM on representative Android 10–16 devices.
- Exercise no-network, blocked-ad, no-consent, no-inventory, delayed SSV, provider 401/403/429, ambiguous timeout, all-key failure, budget-stop, and backend-outage paths.
- Roll out 1%, 5%, 20%, 50%, then 100%, halting on crash/ANR, transcription-failure, SSV-loss, cost, or fraud thresholds.

## Current implementation checkpoint

- Android unit tests, debug lint, debug Android-test compilation, and release AAB packaging pass on API 36.
- Backend tests cover signing, reward economics, ad/SSV replay, rolling-hour limits, upload ownership/expiry, credit/job idempotency, credential fallback, and ambiguous-provider outcomes.
- EN/AZ/TR/RU resource keys have exact parity. Human language and accessibility review remains a release gate.
- The generated AAB is a verification artifact, not a Play-upload candidate: it intentionally contains placeholder backend and AdMob identifiers until production services and fresh secrets are ready.
