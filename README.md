# MEMORA — Private Hybrid-AI Second Brain

> **Capture anything. Understand locally. Connect automatically. Never forget.**
> Built for **#BuildHybridAI 2026** — running across **Android (Kotlin + Jetpack Compose)**, **Chrome Web (React + TypeScript on Firebase Hosting)**, and a **Manifest V3 Chrome Extension**, all powered by **Firebase AI Logic Hybrid Inference**.

- **Live Web App:** https://memora-hybrid-ai-2026.web.app
- **Android APK (`v1.2.3` Direct Download):** https://github.com/tobidesignedit/Memora/releases/latest/download/MEMORA-Android.apk
- **Demo Video (2:45):** _[Add your YouTube / Vimeo link here once uploaded]_
- **License:** [Apache 2.0](./LICENSE)
- **Core Cycle:** `CAPTURE → UNDERSTAND → CONNECT → REMEMBER → ACT`

### Quick Judge Test (Zero Setup)
1. **Chrome Web:** Open [memora-hybrid-ai-2026.web.app](https://memora-hybrid-ai-2026.web.app) and click **Continue as Guest** (1-click Firebase Anonymous Auth) or sign in with Google.
2. **Capture & Privacy Routing:** Drop any screenshot, flyer, receipt, or PDF — toggle between **Smart Hybrid (`PREFER_ON_DEVICE`)** and **Local Vault (`ONLY_ON_DEVICE`)** to see real-time extraction, Visual Intelligence Reminders, and latency telemetry.
3. **Ask & Spoken Recap:** Tap **Ask** to query your vault in natural language with clickable source citations and audio recaps.
4. **Android Native (`v1.2.3`):** Download [MEMORA-Android.apk](https://github.com/tobidesignedit/Memora/releases/latest/download/MEMORA-Android.apk) to test Jetpack Compose UI, Android Share Sheet capture, local offline notifications, and real-time Phone ↔ PC Firestore sync.

---

## Why Hybrid AI Matters for MEMORA

Your screenshots, receipts, exam timetables, payment notices, and voice memos are the most personal artifacts on your phone. Pure-cloud AI apps fail in two ways: they force you to upload private life documents to remote servers, and they break completely when you have zero signal or try to offer an affordable free tier.

**MEMORA solves both using Firebase AI Logic Hybrid Inference:**

| Scenario | Execution Path | Firebase AI Logic Mode | Why |
|---|---|---|---|
| Daily screenshots, flyers, voice notes, receipts | **On-Device Gemini Nano 4** (Android AICore / Chrome Built-in AI) | `PREFER_ON_DEVICE` | Fast, private, offline-capable, \$0 marginal inference cost |
| Sensitive bills, personal journals, private IDs | **Strictly Local Vault** (never leaves device or syncs to Firestore) | `ONLY_ON_DEVICE` | Hard privacy guarantee + enforced in `firestore.rules` |
| Everyday Android phones without AICore (e.g. Samsung S10 / A06) | **Seamless Cloud Fallback** (`gemini-3.5-flash-lite`) | `PREFER_ON_DEVICE` → `IN_CLOUD` | Zero crashes, zero lock-out for billions of everyday devices |
| Deep multi-memory Thread synthesis, heavy PDFs, Spoken Audio Recaps | **Cloud Gemini** (`gemini-3.8-flash` & `gemini-3.1-flash-tts-preview`) | `PREFER_IN_CLOUD` | High-value reasoning & natural voice recaps |
| Zero connectivity + no local Nano | **Deterministic Local Fallback Engine** | Offline parser + local index | Captures, date extraction, local notifications & search keep working |

---

## Five Signature Experiences (Release 1)

1. **Universal Capture & Permission-Gated Auto-Indexing**
   - **Android:** Camera capture, Gallery/Screenshot picker, Voice speech capture, Note/URL capture, **Native Android Share Sheet (`ACTION_SEND` for images, text, URLs, audio)**, **Home-Screen Quick Capture Widget**, AND **Permission-Gated Automatic Screenshots & Photos Indexing** (`READ_MEDIA_IMAGES` on API 33+ / `READ_EXTERNAL_STORAGE` maxSdkVersion 32, live `MediaStore` `ContentObserver` + resume sweep, `Screenshots Only` vs. `All Photos` filter, `Local Vault Only` privacy toggle, and stable deduplication).
   - **Chrome Web:** Global drag-and-drop for images/PDFs/audio, live microphone voice recorder with transcript, URL/clipboard capture, privacy routing selector, AND **"Connect Local Screenshots Folder"** via Chrome's **File System Access API (`showDirectoryPicker`)** with explicit read permission, folder scope filter, deduplication, and foreground auto-watch.
   - **Chrome Extension (`extension/`):** Manifest V3 popup (`Alt+M`) and right-click context menus (*Save page / highlighted text / link to MEMORA*) with offline queue.
2. **Visual Intelligence Reminders** *(Signature Feature)*
   - Automatically detects upcoming dates, times, venues, and deadlines from event flyers, exam timetables, payment notices, tickets, and voice memos (distinguishing historical dates from upcoming commitments).
   - Flags schedule conflicts, links every reminder directly back to the original captured image, and schedules **real offline Android local notifications** (`AlarmManager` + `BOOT_COMPLETED` reboot recovery).
3. **Memory Threads**
   - Automatically clusters related screenshots, voice notes, flyers, and links into connected topic threads with 1-tap AI synthesis (summary, key connections, next actions).
4. **AI Natural Language Search & Voice Intelligence**
   - Ask conversational questions across your entire vault (*"Where is the screenshot with the payment deadline?"*, *"When is my next exam?"*).
   - Returns **source-linked answers citing exact memories** plus **Spoken Audio Recaps** via `gemini-3.1-flash-tts-preview` (with offline device TTS fallback).
5. **Offline & Hybrid Intelligence Telemetry**
   - Local-first storage (app-private files on Android, IndexedDB on Web) + Firestore persistent offline cache + live **Measured Latency Benchmarks** (`On-Device ms` vs. `Cloud ms`).

---

## Firebase Stack Used at Runtime

- **Firebase AI Logic:**
  - Android: `com.google.firebase:firebase-ai` + `com.google.firebase:firebase-ai-ondevice` (`android/app/src/main/kotlin/com/memora/app/data/HybridAiEngine.kt`)
  - Web: `firebase/ai` (`web/src/services/hybridAi.ts`) with `InferenceMode.PREFER_ON_DEVICE`, `ONLY_ON_DEVICE`, `PREFER_IN_CLOUD`, `initializeDeviceModel()`, `response.inferenceSource`, and `ResponseModality.AUDIO` TTS
- **Firebase App Check:** `DebugAppCheckProviderFactory` / `PlayIntegrityAppCheckProviderFactory` on Android (`MemoraApplication.kt`) and Web App Check (`web/src/services/firebase.ts`)
- **Cloud Firestore:** Real-time Android ↔ Web sync with persistent offline cache and hardened owner-only security rules (`firestore.rules`) that explicitly reject `only_on_device` private memories from cloud storage
- **Firebase Authentication:** Anonymous 1-click Guest Sync, Email/Password, and Google Sign-In
- **Firebase Remote Config:** Dynamic cloud model names (`cloud_model_fast`, `cloud_model_pro`, `tts_model`) and routing defaults (`remote_config.json`)
- **Firebase Hosting:** Live public deployment at https://memora-hybrid-ai-2026.web.app

---

## Repository Structure

```text
memora/
├── android/          # Native Android app (Kotlin + Jetpack Compose + Material 3)
├── web/              # Chrome Web app (React 19 + TypeScript + Vite + Tailwind CSS)
├── extension/        # Chrome Manifest V3 Quick-Save Extension
├── firestore.rules   # Hardened owner-isolated Firestore Security Rules
├── remote_config.json# Firebase Remote Config model & hybrid routing template
├── firebase.json     # Hosting, Firestore, Auth & Remote Config deployment config
├── DESIGN.md         # DESIGNmd-derived visual system (Ember Studio + TouchFlow)
└── LICENSE           # Apache License 2.0
```

---

## Quick Start

### 1. Web App (`web/`)
```bash
cd web
cp .env.example .env   # Fill with your Firebase Web SDK config
npm install
npm run dev
```

### 2. Android App (`android/`)
1. Download `google-services.json` from your Firebase project into `android/app/google-services.json`.
2. Build & install:
```bash
cd android
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
*(In debug builds, copy the App Check debug UUID from Logcat and register it under **Firebase Console → App Check → Apps → Manage debug tokens**.)*

### 3. Chrome Extension (`extension/`)
1. Open `chrome://extensions` and enable **Developer mode**.
2. Click **Load unpacked** and select the `extension/` folder.
3. Press `Alt+M` or right-click any webpage/highlight to save straight into MEMORA.
