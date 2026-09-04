# TriSpeech development preview

This is a lighter testing milestone than the preserved free-first V1 plan in `release-v1-free.md`. It does not replace the V1 or V2 roadmaps.

## Included

- Native Android STT and Offline Whisper remain local and free.
- Online transcription uses only `gpt-4o-mini-transcribe` through the TriSpeech backend.
- TokenMix credentials exist only as backend environment secrets and never in the Android project, Gradle properties, resources, BuildConfig, or APK.
- Ads, Google Play billing, subscriptions, accounts, and cloud history are not included in this preview.
- The sidebar contains Models, Privacy Policy, Terms of Use, licenses, language/theme controls, and voluntary developer support.
- English, Azerbaijani, Turkish, and Russian are the only interface languages.

## Backend test deployment

The repository root contains `render.yaml`. Deploy it as a Render Blueprint, then add `TOKENMIX_KEY_1`, `TOKENMIX_KEY_2`, and any additional numbered keys in Render's secret environment-variable UI. Never place the values in this repository.

Build the Android test APK against the HTTPS service URL:

```text
./gradlew :app:assembleDebug -PTRISPEECH_BACKEND_URL=https://YOUR-SERVICE.onrender.com
```

For local emulator testing, run the backend on port 8080 and use the default `http://10.0.2.2:8080`. Cleartext is permitted only by the debug manifest; release builds continue to require HTTPS.

The correct TokenMix speech-to-text endpoint is `/v1/audio/transcriptions`. `/v1/audio/speech` is text-to-speech and must not be used for transcription.

## Preview limitations

- Render's free service can sleep, so the first online request may be slow or time out while it wakes.
- The development configuration uses volatile memory and temporary local storage. A server restart loses backend job state.
- Keep the URL within a small tester group and configure a hard provider-side spending/quota limit. The backend's in-memory daily cost counter resets on server restart.
- This mode demonstrates that the provider key is not in the APK; it is not the final public anti-abuse design.
- Before any public distribution, replace every provider key previously pasted into chat, enable stronger attestation/abuse controls, and complete production legal review.

## Acceptance check

1. Install an APK built with the HTTPS backend URL.
2. Select GPT-4o Mini Online and accept the cloud-processing disclosure.
3. Record or import a short non-confidential sample.
4. Confirm the backend creates one logical job and returns a transcript.
5. Disable the first backend key and repeat; the next healthy numbered key should be selected without exposing the key or changing the app.
6. Scan source and the built artifact for provider-key prefixes before sharing the APK.
