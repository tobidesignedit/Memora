# MEMORA — Product & Hackathon Execution Plan

> **Tagline:** Capture anything. Understand locally. Connect automatically. Never forget.
> **Core Cycle:** `CAPTURE → UNDERSTAND → CONNECT → REMEMBER → ACT`
> **Target Event:** #BuildHybridAI 2026 (Deadline: Mon Nov 30, 2026 @ 2:00 PM PST)
> **Positioning:** Unified Multi-Platform **Android APK (Hero)** + **Chrome Web App (Firebase Hosting)** + **Chrome Quick-Save Extension (Bonus)** sharing one Firebase AI Logic Hybrid backend.

## Release 1 Scope — The 5 Signature Experiences

### 1. Universal Capture
- **Android APK:** In-app Quick Capture Sheet (Camera, Gallery/Screenshot import, Voice Recording, Text Note, URL/Link, PDF/File) + **Native Android Share Sheet (`ACTION_SEND` for images, text, URLs, audio)** + **Home-Screen Quick Capture Widget**.
- **Chrome Web:** Drag-and-drop image/PDF/audio capture, URL/note/voice recorder capture, live sync with Android.
- **Chrome Extension (Lightweight Companion):** 1-click "Save Current Page / Selected Text" directly into MEMORA Firestore vault.

### 2. AI Content Understanding & Offline-First Hybrid Intelligence
- **Firebase AI Logic Hybrid Inference:**
  - Android: `com.google.firebase:firebase-ai` + `com.google.firebase:firebase-ai-ondevice` (`InferenceMode.PREFER_ON_DEVICE`, `ONLY_ON_DEVICE` Local Vault mode, `PREFER_IN_CLOUD`).
  - Web: `firebase/ai` (`InferenceMode.PREFER_ON_DEVICE`, `ONLY_ON_DEVICE`, `PREFER_IN_CLOUD`, `inCloudParams`).
- **Auto-Extraction Pipeline (Structured JSON):** Auto-title, summary, category (`receipt`, `flyer`, `timetable`, `ticket`, `voice`, `link`, `note`, `document`, `screenshot`), tags, OCR text, entities (people, orgs, amounts, dates, venues), action items, and **detected future reminders**.
- **True Offline Intelligence:** Local Room DB + local media files on Android, IndexedDB + Firestore offline persistence on Web, connectivity-aware pending sync queue, deterministic offline fallback indexing, and explicit labeling of cloud-only superpowers.
- **Real Hardware Story:** Live `ON_DEVICE` Gemini Nano 4 on remote Pixel 9 & Chrome Desktop + seamless automatic `IN_CLOUD` fallback on physical Samsung Galaxy S10 & A06.

### 3. Visual Intelligence Reminders (Signature Feature)
- Detects upcoming events, deadlines, exam timetables, payment notices, appointments, and flight/travel tickets from photos, flyers, screenshots, PDFs, and voice notes.
- Distinguishes historical dates vs. upcoming dates, detects schedule conflicts, displays a 1-tap confirmation/edit card linked back to the original image/memory.
- **Android:** Real local notifications via `AlarmManager` / `WorkManager`, snooze/complete notification actions, and `BOOT_COMPLETED` receiver for restart recovery — works 100% offline.
- **Web:** Synced Reminder Dashboard + Upcoming Agenda timeline + browser notification support.

### 4. Memory Threads
- Automatically clusters related memories across screenshots, links, voice notes, and PDFs by shared entities, topics, projects, or organizations.
- Generates an AI-synthesized **Thread Summary** + cross-memory action checklist + visual connected timeline, with manual link/unlink support.

### 5. AI Natural Language Search, Assistant & Voice Intelligence
- Ask natural questions across your entire library (*"Where is that screenshot with the payment deadline?"*, *"Summarize my voice notes about the project"*).
- Returns **source-linked AI answers** citing exact saved memories + clickable jump-to-source cards.
- **Voice Intelligence:** Voice capture & voice search + **Spoken Recaps (`gemini-3.1-flash-tts-preview`)** online (with offline Android TTS / Web Speech fallback).

## Firebase Stack (Every Service Used Authentically)
1. **Firebase AI Logic:** Hybrid on-device Gemini Nano 4 + cloud `gemini-3.8-flash` / `gemini-3.5-flash-lite` + `gemini-3.1-flash-tts-preview` (TTS).
2. **Firebase App Check:** Required security attestation (Debug provider for dev/judges + Play Integrity / reCAPTCHA ready).
3. **Firebase Authentication:** Google Sign-In + Email/Password + **1-Click Guest / Demo Mode** (Anonymous Auth) so judges can test immediately with zero friction.
4. **Cloud Firestore:** Real-time cross-device sync (Android ↔ Web ↔ Extension) with offline persistence enabled + production-grade Security Rules (authored via `firestore-rules-author`).
5. **Firebase Remote Config:** Dynamic cloud model names, hybrid routing mode defaults, prompt templates, and feature flags updated without redeploying.
6. **Firebase Hosting:** Public live URL for the Chrome Web app.

## 6-Day Execution Roadmap (Nov 24 → Nov 30, 2:00 PM PST)
- **Day 1 (Today, Nov 24):** Environment & Firebase setup (`firebase login`, new project, Auth/Firestore/AI Logic/Remote Config/App Check/Hosting), `DESIGN.md` tokens, monorepo scaffold (`android/` + `web/` + `extension/`), shared Firestore schema & security rules.
- **Day 2 (Nov 25):** Build **MEMORA Web App** end-to-end (Universal Capture, Firebase AI Logic Hybrid Nano 4 + Cloud extraction, Visual Intelligence Reminders, Memory Threads, AI Search + TTS Spoken Recap, Hybrid Benchmark Telemetry) & deploy live preview to Firebase Hosting.
- **Day 3–4 (Nov 26–27):** Build **MEMORA Android Native App (Kotlin + Jetpack Compose)** with matching `DESIGN.md` warm editorial UI, `firebase-ai` + `firebase-ai-ondevice`, Room offline-first cache, Android Share Sheet (`ACTION_SEND`), Camera/Voice/File capture, real local Notification Reminders + Boot Receiver, Quick Capture Home Widget, and live Firestore sync.
- **Day 5 (Nov 28):** Add lightweight **Chrome Extension** quick-saver + run full real-device verification across **Samsung S10/A06** (seamless `IN_CLOUD` fallback), **remote Pixel 9** via `android device remote` (live Airplane Mode `ON_DEVICE` Gemini Nano 4), and **Chrome Desktop**, capturing real measured benchmarks.
- **Day 6 (Nov 29–30):** UI polish pass (`impeccable`), Apache 2.0 public GitHub repo with crisp architecture README + APK release, record & upload tight **≤2:50 English YouTube demo video**, and submit on Devpost before Nov 30 @ 2:00 PM PST.
