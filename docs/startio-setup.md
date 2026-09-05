# Start.io rewarded online transcription

TriSpeech 1.2.0 (version code 6) includes Start.io Android SDK 5.3.1 and a rewarded-access dialog for GPT-4o Mini. Native STT and Offline Whisper remain available without advertising.

## Current behavior

Select GPT-4o Mini and open **Online access**. The dialog reads the backend allowance and available credit, offers ad privacy choices, and loads an optional rewarded video. The default experimental allowance is 60 seconds, configurable on the backend from 30 to 120 seconds. Credit expires 24 hours after the most recent award. No claim of profitability is implied.

When a recording or import needs more credit, its audio is retained in private app storage. Watch enough ads and press Continue. Audio pending credit survives app restarts; discarding it requires confirmation. The existing upload/job/TokenMix path handles transcription. Server-measured duration remains authoritative. The session limit is six new sessions per installation per rolling hour, so large files may require credit accumulated over time; the file limit remains 10 minutes/25 MiB.

Splash, return, and automatic interstitial ads are disabled. The SDK initializes only when the user has made an ad privacy choice and explicitly requests a rewarded ad.

## Reward evidence

No authenticated Start.io server-to-server reward API has been verified. The implemented fallback uses **client-reported completion**, as authorized for the limited experiment. Signed requests establish installation identity, not genuine viewing. Modified clients can fabricate this signal and create additional installations. Single-use sessions, ownership checks, rate limits, and a shared backend cost ceiling limit exposure; they do not eliminate this limitation.

The stored legacy `verified` flag means a session has been consumed. The separate `rewardEvidence` field identifies Start.io awards as `client-reported`. AdMob callbacks cannot grant Start.io sessions. Test-mode matching prevents accidental mixing of ordinary test and live builds; it is not an anti-tampering guarantee.

## Local test configuration

Create a Start.io app entry for `com.trispeech.app` and obtain its actual App ID. No example publisher ID is embedded in the app.

In the backend's untracked `.env`, keep existing TokenMix credentials and set:

```dotenv
AD_PROVIDER=startio
DEVELOPMENT_ACCESS_MODE=false
STARTIO_CLIENT_REWARDS_ENABLED=true
STARTIO_TEST_REWARDS=true
STARTIO_REWARD_SECONDS=60
USE_MEMORY_STORE=true
REQUIRE_APP_CHECK=false
PROVIDER_MAX_CONCURRENCY=2
PROVIDER_DAILY_BUDGET_USD=1
```

Start the backend with `npm run dev`. Build from the repository root:

```powershell
.\gradlew.bat assembleDebug -PSTARTIO_APP_ID=YOUR_NUMERIC_APP_ID -PTRISPEECH_BACKEND_URL=https://YOUR_TEST_BACKEND
```

For an emulator and a local backend, omit the backend property to use `http://10.0.2.2:8080`. On a USB device, `adb reverse tcp:8080 tcp:8080` can be used with a debug-only `http://127.0.0.1:8080` backend URL. Debug builds always request test ads. Test ads produce no revenue. Test-awarded credit may incur actual TokenMix cost if real provider credentials are used; configure a provider-side hard spending limit too.

The existing Render blueprint remains a private preview with `DEVELOPMENT_ACCESS_MODE=true`: it preserves online access without requiring ads. Deploying new code alone does not switch that service to rewarded access. A Start.io experiment configuration must use `DEVELOPMENT_ACCESS_MODE=false`.

## Before live activation

1. Supply the Start.io App ID and a working HTTPS backend URL.
2. Use separate test and live backend data. Set `STARTIO_TEST_REWARDS=false`, explicitly enable `STARTIO_CLIENT_REWARDS_ENABLED`, and turn off development access.
3. Configure Firestore (`FIREBASE_PROJECT_ID`), durable audio storage (`UPLOAD_BUCKET`), and Cloud Tasks (`CLOUD_TASKS_QUEUE`, `CLOUD_TASKS_LOCATION`, `WORKER_URL`). Configure service credentials/IAM and `INTERNAL_TASK_TOKEN` through the host secret manager. Never embed service credentials in the APK.
4. Set a backend daily estimated-cost ceiling greater than zero and no greater than $1 for this experiment, plus a hard TokenMix-side spending/quota limit. Firestore preserves the shared backend budget across service restarts. Backend estimates are not a substitute for a provider-enforced billing ceiling.
5. Configure the bucket's 24-hour audio lifecycle, Firestore cleanup, and restricted access to internal routes. Use a separate persistent environment rather than repurposing private-preview memory storage. Start.io live mode refuses startup without the persistence/queue prerequisites.
6. Test actual Uptodown/sideload installations. The Android client does not yet supply Firebase App Check tokens: do not turn on `REQUIRE_APP_CHECK` and assume this client will work. This experiment uses signed anonymous installation requests and the bounded fallback described above.
7. On an actual device, verify completion, early close, no-fill, airplane mode, background/rotation, credit retry after network loss, pending-audio restoration, and a short real TokenMix transcription. Verify consent behavior and inspect SDK disclosures before public distribution.
8. Build with `-PSTARTIO_LIVE_ADS=true -PSTARTIO_APP_ID=YOUR_NUMERIC_APP_ID -PTRISPEECH_BACKEND_URL=https://YOUR_LIVE_BACKEND`. Only release builds can enable live ads. Release validation rejects a missing App ID or backend URL.
9. Sign the release APK with the owner's persistent release key. The generated `app-release-unsigned.apk` is not ready for installation or store submission. Keep the signing key/password outside version control and preserve the signing identity for updates.

Do not use developer/tester ad interactions to manufacture revenue. Measure normal public usage in the Start.io dashboard. Neither an SDK callback nor the backend credit balance proves cash earnings.

## Uptodown and app-ads.txt

- Create the Uptodown app draft, upload the signed release APK, and provide the actual icon, screenshots, description, developer website, privacy policy and support details. Publication/account steps remain pending; no store account was created by this implementation.
- Download **all exact lines** from Start.io's App-ads.txt portal section. The publisher/seller ID is not the App ID.
- Host the downloaded file as plain text at the developer website's `/app-ads.txt`, with HTTP 200 and no login. Add that website to Start.io's account details and the Uptodown listing where available.
- After publication, add the Uptodown listing URL to Start.io. Verify URL recognition and seller-file discovery in the portal; saving a URL is not proof that advertisers recognize it. Do not invent seller lines or publish a placeholder as a verified file.
- Check Azerbaijan publisher/payment onboarding in the account. Withdrawal is a later milestone.

The supplied seller file has now been copied unchanged into `store/uptodown/app-ads.txt`: 157 validly formatted records, including `start.io, 199952545, DIRECT`. That seller ID is not the SDK App ID. Website deployment and portal discovery verification still require the website address. No website or store listing has been published, and no revenue is claimed. The [Uptodown submission pack](../store/uptodown/README.md) contains graphics, descriptions, metadata, and the remaining release loops.

## Verification and rollback

Verified on 2026-09-05: 8 Android unit tests and 15 backend tests passed; Android lint reported 0 errors (69 warnings, 1 hint); debug and minified unsigned release APKs built. After the phone was connected, 2 instrumentation tests passed on Xiaomi M2004J19C / Android 10, including database migration with history preservation. Store screenshots were captured from a separate `com.trispeech.app.storepreview` installation with sample content. Core screens and the online-access dialog opened; its emulator backend address is unreachable from this phone. Real ad display and actual TokenMix transcription remain pending. No real provider calls were used in the automated integration test.

Initial implementation artifacts (SHA-256, historical; debug output was subsequently rebuilt for store captures):

- `app/build/outputs/apk/debug/app-debug.apk`: `0AA786BFA7AF115D0D15B17587B60ABBB7A45AA187744B3EA88B93FDC53736B1`
- `app/build/outputs/apk/release/app-release-unsigned.apk`: `44DE4C7DF2D9375D4EB9A4CEABF6835D7AD742F5B6D189C339A308B0CA98FBA2`

These artifacts use test-ad mode with no Start.io App ID supplied. The debug artifact points to the local emulator backend; the unsigned release has the placeholder backend URL. Rebuild with actual configuration before use against a hosted backend or publication.

The screenshot build uses `-PTRISPEECH_SCREENSHOT_BUILD=true`, which adds `.storepreview` only to debug. Its sample-data fixture refuses to write to the production package. This APK is for captures and QA, not store submission. The original installed production package was not replaced. Current APK hashes should be obtained from the actual files after each configured rebuild.

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleRelease compileDebugAndroidTestKotlin
```

Run `npm test` and `npm run check` from `backend`. The automated HTTP test uses a local mock provider and a one-second WAV; it verifies reward mode, credit consumption, and a single provider request despite duplicate job creation. It does not call real TokenMix or Start.io.

For an incident, set `ONLINE_SERVICE_ENABLED=false` to stop new sessions/jobs and set `STARTIO_CLIENT_REWARDS_ENABLED=false` to stop new Start.io awards. Already running provider requests may finish. Never set `DEVELOPMENT_ACCESS_MODE=true` as a public rollback: that would bypass credits. Local transcription remains usable.

## Implementation progress

| Part | Status | Evidence / remaining work |
|---|---|---|
| 1. Baseline and access boundaries | Implemented | Existing TokenMix route retained; reward-mode rejection removed; fixed configurable allowance; explicitly enabled fallback. |
| 2. Publisher/website | Seller file and listing assets prepared; external setup pending | Seller file checked; Fuad Ismayil and support email confirmed. Actual App ID, hosted website and Uptodown account/listing still pending. |
| 3. SDK/privacy | Implemented; device validation pending | Pinned SDK, explicit initialization, privacy choices, test/live separation and disabled automatic ads compile. |
| 4. Reward/backend flow | Implemented; device validation pending | Signed HTTP test, replay checks, credit reservation/refund, pending audio and duplicate-dispatch protection. |
| 5. Release quality | Automated and basic phone checks pass; signing/live flow pending | Builds, unit tests/lint, backend tests, 2 phone instrumentation tests and four core screens checked. Real rewarded-ad flow remains untested. |
| 6. Publication/revenue | Pending | No live activation, store publication, seller-file verification, or observed earnings. |

Independent implementation continued while account setup was unavailable; externally dependent parts are not marked passed.
