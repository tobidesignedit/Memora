# MEMORA Design System — Crisp Gallery Minimal (v4.0)

> Authoritative solid-surface visual specification for **MEMORA** across **Web**, **Native Android (Jetpack Compose)**, and **Chrome Extension**. Zero frosted blur, zero decorative fog — pure gallery surfaces where captured screenshots, receipts, voice thoughts, and notes command the frame.

## 1. DESIGNmd Provenance

Synthesized from three top non-glassy design systems retrieved via **DESIGNmd**:

- **[Genesis](https://designmd.ai/chef/genesis)** (`chef/genesis`) — Editorial precision, bold geometric display typography (`DM Sans` / `General Sans`, `-0.03em` tracking), generous spacing on a `#FAFAFA` gallery canvas, pure solid `#FFFFFF` gallery-frame cards with `1px solid #E8E8EC` hairlines, zero static shadows (elevation reserved strictly for hover `-2px` lift), and refined `#4F46E5` / `#6366F1` indigo reserved exclusively for interactive controls.
- **[Block Canvas](https://designmd.ai/chef/block-canvas)** (`chef/block-canvas`) — Content-first calm knowledge workspace where whitespace and typography do the heavy lifting, `#F7F6F3` warm off-white inset surfaces, `#191919` near-black reading ink, and quiet hover-reveal controls instead of persistent chrome.
- **[Aime Design System](https://designmd.ai/cccRaim/aime-design-system)** (`cccRaim/aime-design-system`) — *"Information is the protagonist, UI is the background; emerald is the spark, neutral gray is the base."* Strict restraint: 95% crisp white + neutral slate + deep ink (`#111318`), reserving surgical emerald (`#059669` / `#10B981`) exclusively for On-Device Privacy, Private Vault locks, and confirmed completion states.

## 2. Surface & Elevation Architecture

- **Zero Frosted Glass / Zero Aurora Blobs:** Every card, modal, drawer, navigation bar, toast, and bottom dock is a crisp, high-legibility solid surface (`#FFFFFF` light / `#14161B` dark).
- **Flat at Rest, Elevated on Hover:** Static cards rely purely on contrast against the canvas (`#FAFAFA` vs `#FFFFFF` in Light; `#0B0C0F` vs `#14161B` in Dark) and a surgical `1px` border (`#E8E8EC` / `#23262F`). Hover lifts cards `-2px` with a soft neutral shadow (`0 8px 28px rgba(17, 19, 24, 0.08)` light / `0 12px 32px rgba(0, 0, 0, 0.45)` dark).
- **Focus States:** Crisp `3px` indigo focus ring (`0 0 0 3px rgba(79, 70, 229, 0.14)`) with zero dashed outline clutter.

## 3. Semantic Color Tokens

| Token | Crisp Light (Default) | Solid Dark (Obsidian) | Role |
|---|---|---|---|
| Canvas Base | `#FAFAFA` | `#0B0C0F` | Primary gallery background |
| Card / Panel Surface | `#FFFFFF` | `#14161B` | Solid gallery-frame cards, modals, drawers |
| Inset / Chip Surface | `#F4F4F6` | `#1C1F26` | Chips, subtle inputs, secondary controls |
| Stationery Note Tint | `#FCFBF8` | `#18191D` | Longform stationery & receipt ledger cards |
| Voice Card Tint | `#F6F7FB` | `#151822` | Acoustic waveform voice memo cards |
| Primary Ink | `#111318` | `#F4F5F7` | Headings, primary labels, body |
| Secondary Ink | `#525866` | `#A1A7B5` | Summaries, descriptions, prose |
| Muted Ink | `#868C98` | `#6E7485` | Timestamps, latency, captions |
| Hairline Border | `#E8E8EC` | `#23262F` | Static card & divider outlines |
| Strong Border | `#D4D6DE` | `#323644` | Hover outlines, inputs, active controls |
| Interactive Indigo | `#4F46E5` (`#FFFFFF` ink) | `#6366F1` (`#FFFFFF` ink) | Primary CTAs, active rings, links |
| Privacy Emerald | `#059669` | `#10B981` | On-Device Gemini Nano, Local Vault, confirmed states |
| Reminder Amber | `#D97706` | `#F59E0B` | Detected Visual Intelligence Reminders |
| Danger Red | `#DC2626` | `#EF4444` | Destructive actions & overdue alerts |

## 4. Typography & Radii

- **Display Headings:** `DM Sans` (Web) / Sans Bold (Android) with tight `-0.03em` tracking (`font-display`).
- **Editorial Notes & Voice Quotes:** `Newsreader` serif reserved exclusively for handwritten-style stationery cards and spoken pull-quotes (`font-editorial`).
- **UI & Reading Body:** `Inter` (`font-feature-settings: "calt", "kern", "liga", "ss03"`) at `15px` / `1.56` line-height.
- **Data & Timestamps:** `JetBrains Mono` (`tabular-nums`) for dates, amounts, keycaps, and inference latency (`48ms`).
- **Radii:** `16px` (gallery cards & sheets), `12px` (date tiles, inputs, popovers), `9999px` (Mind Bar, tabs, pills, CTAs).

## 5. Interactive Primitives — Lucian UX/UI Prompt Library

MEMORA Web retains 8 dependency-free **Lucian UX/UI Prompt** (`lucianlabs.dev/uxui-prompt`) micro-interactions cleanly themed onto our solid Crisp Gallery Minimal `--ux-*` tokens:

- `#039` **Sliding pill tabs** — crisp solid segmented navigation dock (`Vault · Ask · Threads · Reminders`).
- `#001` **Liquid glass button** — adapted to a crisp solid indigo magnetic CTA capsule with spinner shrink and emerald checkmark.
- `#228` **Cursor-lit glowing card borders** — subtle pointer-tracked indigo edge highlight across gallery cards on desktop hover.
- `#432` **File upload** — animated dropzone with extension-colored file drop, filling progress ring, and completion check.
- `#458` **AI voice orb** — active orbiting **MEMORA 3-node constellation** voice orb (`idle`, `listen`, `think`, `speak`) for voice recording & Spoken Recaps.
- `#224` **AI answer with traceable sources** — inline sentence citation badges (`[1] [2]`) with crisp floating source preview & synced cards.
- `#435` **Toast notifications** — solid stacking spring toast deck (up to 3) with self-drawing checkmarks and swipe-to-dismiss.
- `#290` **Ask-first notification permission** — crisp solid wiggling-bell permission priming card on Reminders.
