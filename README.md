# TriSpeech

Start.io rewarded GPT-4o Mini integration: see [setup and verification](docs/startio-setup.md). Live activation requires publisher configuration; debug ads are test-only. The TokenMix backend remains in use.

TriSpeech is a free-first monochrome Android transcription studio with ad-supported GPT-4o Mini, on-device `whisper.cpp`, and Android native speech recognition.

## Build

```powershell
.\gradlew.bat testDebugUnitTest compileDebugAndroidTestKotlin lintDebug assembleDebug
```

The project targets Android API 36 and requires JDK 17 or newer. Android debug builds call the V1 backend at `http://10.0.2.2:8080`; release builds require an HTTPS `TRISPEECH_BACKEND_URL`.

Provider credentials never belong in the Android build. See [backend/README.md](backend/README.md) for the anonymous installation, rewarded-ad SSV, credit, upload, job, provider-fallback, and Cloud Run setup.

## Legacy local Faster-Whisper server

```powershell
cd tools/faster_whisper_server
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
.\start-server.ps1
```

This developer tool preloads `large-v3-turbo` with CPU INT8 compute and exposes the
OpenAI-compatible endpoint at `http://10.0.2.2:8000/v1/audio/transcriptions`
for the Android emulator. To opt into GPU execution after installing matching
CUDA 12 and cuDNN 9 runtime libraries, set `TRISPEECH_WHISPER_DEVICE=cuda`
before starting the server. It is not reachable from production V1 and contains no ad-credit path.

## Offline Whisper

The official `whisper.cpp` source is vendored at `app/src/main/cpp/whisper.cpp`; CMake links it and GGML into the JNI bridge for `arm64-v8a` and `x86_64`. Model downloads are resumable and verified against a supplied SHA-256 digest before activation.

The active V1 contract is [docs/release-v1-free.md](docs/release-v1-free.md). The paid/account/cloud design remains deferred in [docs/roadmap/monetization-security-v2.md](docs/roadmap/monetization-security-v2.md).
