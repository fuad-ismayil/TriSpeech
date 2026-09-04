# TriSpeech: Autonomous Execution Master Specification & Prompt

> **Execution Directive**: You are operating as an elite Senior Android Systems Engineer, Audio DSP Specialist, and UI/UX Architect. You are executing this specification autonomously under the **Zero-Interruption Autonomous Loop Protocol**. Follow all tasks sequentially, verify each task with automated tests, self-heal any failures, and do not halt or request user input until the entire project is completed and verified.

---

## 1. Autonomous Loop Execution Protocol (Strict Rules)

1. **Zero-Interruption Mandate**: Do not stop to ask questions, solicit opinions, or ask for confirmation midway. Everything needed is defined in this document.
2. **Deterministic Task Loop**:
   ```
   [Select Task N] 
         │
         ▼
   [Implement Code & Unit Tests]
         │
         ▼
   [Execute Verification Gate Command]
         │
         ├──► [FAIL] ──► [Read Stacktrace ──► Patch Code ──► Re-run Gate] (Loop until PASS)
         │
         └──► [PASS] ──► [Log Task Completion] ──► [Proceed to Task N+1]
   ```
3. **Hard Quality Gates**: Never skip a test or comment out failing assertions. Every task has a specific build or test command that must exit with code `0`.
4. **Self-Healing Principle**: If a Gradle build, NDK compile, or test fails, automatically inspect compiler error outputs, fix missing dependencies, adjust ProGuard/R8 rules, fix syntax or logic bugs, and re-run immediately.
5. **No Placeholders**: Never write `// TODO: Implement later` or stub functions that return dummy data without proper handling. Build complete, working implementations.

---

## 2. Product Identity & Design Soul
- **Name**: TriSpeech
- **Core Mantra**: *"Speak naturally. Get perfect text."*
- **Design Language**: Strictly monochromatic, high-contrast, professional, typography-first document reader.
  - Palette: True Black (`#000000`), Pure White (`#FFFFFF`), Slate/Neutral Grays (`#121212`, `#1E1E1E`, `#2A2A2A`, `#8E8E93`).
  - Zero rainbow gradients, zero neon colors, zero flashy AI cliches.
  - Subtle Liquid Glass: Frosted modal sheets, translucent floating pill selectors, restrained haptic feedback.
  - Performance: Zero perceived lag, instant UI transitions, 60/120 FPS render targets. Heavy audio and inference tasks must run in isolated background dispatchers.

---

## 3. Platform & Target Specifications
- **Operating System**: Android
- **Target SDK**: `compileSdk = 35`, `targetSdk = 35`, `minSdk = 29` (Android 10+)
- **Language**: 100% Kotlin 2.0+ (Coroutines, StateFlow, Flow)
- **UI Framework**: Jetpack Compose with Material 3 custom monochromatic theme
- **NDK / Native**: C++20 via CMake for `whisper.cpp` (`arm64-v8a`, `x86_64`)
- **App Localization (UI Languages)**:
  - English (`en` - Default)
  - Azerbaijani (`az`)
  - Russian (`ru`)
  - All UI strings, alerts, dialogs, and error messages must be localized in `res/values/strings.xml`, `res/values-az/strings.xml`, and `res/values-ru/strings.xml`.
- **Transcription Target Languages**:
  - Full Whisper language catalogue (~99 languages: English, Azerbaijani, Russian, Turkish, Spanish, German, French, Chinese, etc.).
  - Prominent **"Auto Detect" (`auto`)** option at the top of the picker to leverage Whisper's automatic language identification (`whisper_full_lang_id()`).

---

## 4. The 3-Engine Transcription Matrix

### Engine 1: Online Whisper (Cloud & Zero-Cost Local Dev Mode)
- **Dual-Mode Backend Architecture**:
  - **Dev/Local Mode (Default)**: Points to `http://10.0.2.2:8000/v1/audio/transcriptions` (Android Emulator) or user's PC LAN IP. Allows $0 full-pipeline testing without paying for OpenAI API credits.
  - **Production Mode**: Connects to `https://api.openai.com/v1/audio/transcriptions` when an API key is provided in settings.
- **Monetization & Free Ad Economy**:
  - Initial user balance: **0 minutes**.
  - Users earn minutes by watching rewarded video ads via Google Mobile Ads SDK (AdMob test ID: `ca-app-pub-3940256099942544/5224354917`).
  - **Rules**:
    - Each ad watched grants **+10 minutes**.
    - Strict cap: **Max 6 ads per rolling 7 days** (maximum 60 free minutes per rolling week).
    - DataStore ledger records timestamp of each ad watch.
    - UI shows: *"X ads remaining this week. Next refill in Y days"*.
  - Pro Subscription: Unlimited minutes bypass via Google Play Billing.

### Engine 2: Offline Local Whisper (`whisper.cpp` via NDK/JNI)
- **Engine**: Native C++ `whisper.cpp` compiled with Android NDK and JNI bindings. 100% private, works in Airplane Mode.
- **Monetization & Legal Transparency**:
  - Locked by default. Unlocked via a single **one-time in-app purchase**.
  - **Mandatory Open-Source Disclaimer Dialog**:
    > *"All AI transcription models are open-source and free to download. Your one-time payment unlocks the offline on-device processing engine for this device."*
- **Device RAM Profiling & Model Recommendations**:
  - Inspect device RAM dynamically via `ActivityManager.MemoryInfo`:
    - `< 4 GB RAM`: Maximum Speed tier (`ggml-tiny.bin`, ~75 MB). If user tries a higher model, display a performance/OOM warning dialog.
    - `4 GB - 6 GB RAM`: Recommended tier is **Best Balance** (`ggml-base.bin`, ~142 MB).
    - `> 6 GB RAM`: Unlocks **Maximum Quality** (`ggml-small.bin`, ~466 MB or `medium`).
  - Tier UI Display:
    - ⚡ **Maximum Speed**: Fastest transcription, lowest memory footprint.
    - ⚖️ **Best Balance** *(Recommended)*: Optimal speed and accuracy balance.
    - 🏆 **Maximum Quality**: Best possible accuracy, requires high compute.
  - Built-in Model Downloader: Resumable HTTP downloads with SHA-256 verification and storage check.

### Engine 3: Offline Native STT (Non-AI Platform Engine)
- Uses Android's native `android.speech.SpeechRecognizer` with offline language packs.
- **100% Free Forever**: No IAP, no ads, no cloud, no model downloads required. Instant fallback for low-resource environments.

---

## 5. UI Architecture & Navigation (Reference Screenshot Match)

### Primary Screens
1. **Screen 1: Recording Studio**
   - **Header**: Hamburger menu (`≡`), Segmented tab (`Record` | `Files`), Crown icon (Subscription/Unlock status).
   - **Center**: Monospaced elapsed timer (`00:02:35`), "Recording..." status text, live dynamic monochrome Canvas waveform.
   - **Controls**: Bookmark button, large circular tactile Stop button, Pause/Resume button.
   - **Floating Bottom Pills**:
     - Language picker (`Auto Detect`, `English`, `Azerbaijani`, `Russian`, +95 others).
     - Engine/Quality picker (`Best Balance`, `Maximum Quality`, `Native STT`, `Online Whisper`).
   - **Bottom Navigation**: `Transcribe` (Mic), `History` (Clock), `Models` (Cube).

2. **Screen 2: Background Processing Screen**
   - Centered dotted circular progress ring with percentage (`64% Transcribing...`).
   - File Info Card: Waveform icon, filename (`Interview_with_CEO.wav`), duration, file size.
   - Metadata rows: Engine (`Offline (Local)`), Model (`Best Balance`), Language (`English`).
   - Reassurance footer: *"You can close the app. We'll notify you when it's done."*
   - Active Android Foreground Service with live progress notification.

3. **Screen 3: Document-Grade Transcript Viewer**
   - Header: Back arrow, Search icon, Edit title, Three-dot overflow menu.
   - Title & Metadata: `Interview with CEO`, `May 18, 2025 • 09:41 • 05:41`.
   - Segmented Tabs: `Transcript` | `Summary`.
   - Body: Clean document typography with smooth scrolling and search highlight.
   - Bottom Audio Controller: Play/Pause, timestamp, scrubbable waveform bar, speed pill (`1x`, `1.5x`, `2x`).
   - Floating Action Dock (Liquid Glass): `Copy`, `Share`, `Edit`, `Export` (.txt, .srt, .pdf), `More`.

4. **Screen 4: Offline Models Management**
   - Hero Card: *"Recommended for your device"* with title (`Best Balance`), description, and Download/Active action button.
   - Isometric 3D layer illustration.
   - Tier list items with status checkmarks and download buttons:
     - 🏆 Maximum Quality (~1.6 GB)
     - ⚖️ Best Balance (Recommended, ~1.0 GB)
     - ⚡ Maximum Speed (~650 MB)
   - Expandable *"Technical Details"* section (quantization, RAM usage, real-time factor).

5. **Screen 5: History & Search**
   - Searchable and filterable list of all previous recordings and transcriptions.

### Side Navigation Drawer (`≡` Hamburger Menu)
- Accessed via hamburger icon at top-left of Recording Studio.
- **Drawer Contents**:
  - Header: App logo, "TriSpeech", version string.
  - **App Language Selector**: Radio/dropdown to toggle between **English**, **Azerbaijani (Azərbaycan)**, and **Russian (Русский)** (instantly updates app locale via `AppCompatDelegate.setApplicationLocales`).
  - Storage & Cache cleaner (shows audio cache size and model storage).
  - Privacy Policy & Open Source Licenses.
  - **Bottom Pinned Item**:
    - **"Support Developer" button**: Tactile button with coffee cup icon that launches an Intent to the developer's donation/coffee page (`https://buymeacoffee.com/...`).

---

## 6. Zero-Cost Dev Test Server
- Location: `/tools/mock_whisper_server/server.py`
- Lightweight Python FastAPI/Flask server providing:
  - `GET /health`: Health check.
  - `POST /v1/audio/transcriptions`: Accepts multipart audio and returns realistic OpenAI-compatible JSON responses (`{"text": "...", "segments": [...]}`).
- Allows testing the entire cloud transcription flow, minute deduction, and AdMob reward cycle without spending money on API keys.

---

## 7. Phased Implementation Roadmap & Automated Verification Gates

Execute these tasks sequentially. Run the verification command after each task. Do not proceed until the command exits cleanly with code 0.

### Task 1: Project Scaffolding & Monochromatic Design System
- Initialize Android Gradle project with Compose BOM, Navigation, Room, Coroutines, and Encrypted DataStore.
- Implement monochromatic theme tokens: `Color.kt`, `Type.kt`, `Theme.kt`, `LiquidGlass.kt`.
- Setup localized string resources for English (`en`), Azerbaijani (`az`), and Russian (`ru`).
- Scaffold main activity with `ModalNavigationDrawer` and bottom navigation bar.
- **Verification Command**:
  ```bash
  ./gradlew assembleDebug
  ```

### Task 2: Audio Recording & Real-time Waveform Engine
- Build `AudioRecorderEngine` capturing 16kHz 16-bit Mono PCM and packaging standard RIFF/WAV files.
- Implement real-time RMS amplitude calculation with circular ring buffer for visualization.
- Implement Android `ForegroundService` with notification channel for background recording.
- **Verification Command**:
  ```bash
  ./gradlew testDebugUnitTest --tests "*.AudioRecorderTest"
  ```

### Task 3: Room Database & Engine 3 (Offline Native STT)
- Implement Room entities: `TranscriptEntity` and `AudioMetadataEntity`.
- Define `TranscriptionEngine` interface and `TranscriptionRepository`.
- Implement `NativeSpeechRecognizerEngine` wrapping Android's `SpeechRecognizer`.
- Build Whisper language dictionary with ~99 languages + `Auto Detect` (`auto`).
- **Verification Command**:
  ```bash
  ./gradlew testDebugUnitTest --tests "*.RoomAndNativeEngineTest"
  ```

### Task 4: NDK / CMake Setup & Engine 2 (Offline Whisper.cpp)
- Configure `CMakeLists.txt` and integrate `whisper.cpp` source files.
- Write JNI wrapper `WhisperJniBridge.kt` and `whisper_jni.cpp`.
- Implement `DeviceMemoryProfiler` to detect total device RAM and recommend tiers (`Tiny` for <4GB, `Base` for 4-6GB, `Small` for >6GB).
- Build `ModelDownloadManager` with SHA-256 verification and storage check.
- Add mandatory Open-Source Disclaimer Dialog before unlock.
- **Verification Command**:
  ```bash
  ./gradlew testDebugUnitTest --tests "*.MemoryProfilerTest"
  ```

### Task 5: Engine 1 (Online Whisper), AdMob Economy & Billing
- Build `OnlineWhisperEngine` with dual-mode endpoint (Dev Server vs. OpenAI).
- Create `/tools/mock_whisper_server/server.py` for $0 local verification.
- Implement `RewardedAdManager`: 6 ads/week rolling cap, +10 min per ad, stored in Encrypted DataStore.
- Implement `BillingRepository`: One-time unlock for Engine 2 and monthly subscription for Engine 1.
- **Verification Command**:
  ```bash
  ./gradlew testDebugUnitTest --tests "*.AdQuotaAndBillingTest"
  ```

### Task 6: UI Implementation (Exact Screen Mapping)
- Implement Screen 1: Recording Studio with dynamic Canvas waveform, controls, and language/quality pills.
- Implement Side Drawer: Language switcher (ENG, AZE, RU) and "Support Developer" donation button.
- Implement Screen 2: Background Transcribing Screen with dotted circular progress ring.
- Implement Screen 3: Document-Grade Transcript Viewer with tab bar, audio scrubber, and Liquid Glass dock.
- Implement Screen 4: Offline Models Screen with recommendation banner and tier cards.
- Implement Screen 5: History & Search list.
- **Verification Command**:
  ```bash
  ./gradlew compileDebugAndroidTestSources
  ```

### Task 7: End-to-End Integration & Final Build
- Connect all ViewModels and StateFlows across the 3 engines.
- Test app locale switching at runtime between English, Azerbaijani, and Russian.
- Verify "Support Developer" intent trigger.
- Build release APK.
- **Verification Command**:
  ```bash
  ./gradlew test && ./gradlew assembleDebug
  ```

---

## 8. Definition of Done
The project is complete when:
1. All 7 tasks have passed their verification commands with 0 errors.
2. The user can record voice, view live waveforms, select any of the 3 engines, switch UI between English/Azerbaijani/Russian, and export transcripts.
3. The APK compiles cleanly and is ready for installation.
