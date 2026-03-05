# ThunderPass — Product Roadmap

ThunderPass is a modern StreetPass-inspired Android app built on Bluetooth Low Energy. This roadmap reflects the product's evolution from a working MVP to a polished, cloud-connected experience.

---

## Milestone 0 — Foundation
**Goal:** Establish the Android project scaffold and CI pipeline.

- [x] Android project scaffold (Kotlin + Jetpack Compose)
- [x] Foreground service (`BleService`) with persistent notification
- [x] Runtime permission flow for Android 13 BLE and `POST_NOTIFICATIONS`
- [x] Local storage with Room (`MyProfile`, `Encounter`, `PeerProfileSnapshot`)
- [x] CI workflow (GitHub Actions) — builds and publishes debug APK on every push and PR

---

## Milestone 1 — StreetPass Core
**Goal:** Deliver the core BLE encounter experience end-to-end.

- [x] BLE advertise and scan (concurrent, low-power)
- [x] Rotating identity via HMAC-SHA256 (30-minute window) for privacy
- [x] Encounter deduplication (10-minute cooldown per rotating ID)
- [x] GATT server and client for profile card exchange
- [x] Core screens: Home, Passes, Profile, Navigation
- [x] Brand theme and unit tests
- [ ] Local data export / import *(deferred)*

---

## Milestone 2 — Energy and Quality of Life
**Goal:** Introduce the Volts energy system and improve day-to-day reliability.

- [x] Encounter widget showing new-encounter count on the home screen
- [x] Volts engine — earns 100 V per unique Spark; recalculated on launch
- [x] Haptic feedback: double-pulse vibration on GATT exchange
- [x] Safe Zones manual toggle (moved to Settings in Milestone 5)
- [x] Battery scan modes: aggressive, balanced, off
- [x] Improved UX copy and first-launch onboarding

---

## Milestone 3 — Fun Layer and Retro Integration
**Goal:** Add gamification depth and RetroAchievements connectivity.

- [x] Ghost payload type — high score and time exchange over BLE
- [x] Sticker book foundation
- [x] Encounter statistics: total counts, streaks, Volts displayed on Home
- [x] RetroAchievements integration — `RetroProfile`, GATT sharing, `RetroAuthManager`, `RetroRepository`
- [x] Retro Spark Card with mastery icon row and reference achievements
- [x] Visual Shop: CRT Scanlines, Pixelated Aura, Thunder Trail effects

---

## Milestone 4 — Accounts and Cloud Sync
**Goal:** Introduce optional cloud identity so users' data is safe and accessible across reinstalls.

- [x] Supabase Email OTP sign-in (no password required)
- [x] Auto-sync profile to Supabase `profiles` table (display name, greeting, avatar seed, ghost score, stickers)
- [x] Web dashboard (GitHub Pages)
- [x] Profile sync triggered after Spark encounters
- [ ] Optional encounter backup and restore
- [ ] Remote blocklist and privacy controls
- [ ] GitHub-managed deployment automation

---

## Milestone 5 — UX Overhaul and Feature Completion
**Goal:** Polish every screen, sharpen identity, and complete the core feature set.

### 5.1 Identity and Device Fingerprinting
- [x] Stable installation ID derived from `ANDROID_ID` (SHA-256, UUID v5 format) — survives reinstalls without requiring any permission
- [x] Profile synced from Supabase on startup — local data wins offline; server data applied when it is newer
- [x] Display name pre-filled from Android account or device model (Thor, Odin, Retroid Pocket, etc.)
- [x] Privacy mode: replaces name, avatar, and greeting with "Private User" in all BLE payloads
- [x] Share ID uses display name; shareable link includes a friend-recognition code

### 5.2 Home Screen
- [x] Header: user avatar (→ Profile) · display name + "Scanning nearby" · Volts count (→ Shop)
- [x] ThunderPass ON/OFF toggle positioned above the walking animation, respects light and dark mode
- [x] "Last Passed By" strip: peer avatar, name, relative time — tap to open encounter detail
- [x] Landscape mode: "Last Passed By" strip repositioned below the animation area
- [x] RetroAchievements gallery visible on Home when RA account is connected (last played, badges, consoles)
- [x] Daily playtime card derived from Android game usage data

### 5.3 Navigation and Bottom Bar
- [x] Fixed bottom bar always visible; no teardown on navigation
- [x] Seven tabs: Home · Passes · Profile · Badges · Shop · Settings · About
- [x] Landscape mode: bar height ~48 dp, icons only (no labels)

### 5.4 Passes (Encounters)
- [x] Push notification on new encounter: "ThunderPass! [Name] is nearby" — name omitted for private peers
- [x] Landscape layout: Sparks list on the left, Friends list on the right
- [x] Portrait layout: two white-tab panels — Sparks and Friends
- [x] Encounter card: rounded user block (avatar + name + greeting), inline status chips, retro info row
- [x] Friend toggle per encounter; friends list accessible from Passes and Profile
- [x] Traveler view landscape: profile panel on the left, encounter metadata on the right

### 5.5 Profile Screen
- [x] Avatar maker: DiceBear seed selector with Randomise button; syncs everywhere immediately
- [x] Sparky editor: dedicated screen for customizing the avatar with per-option sliders; landscape splits into preview and sliders panels
- [x] Personal phrase (greeting) field — shared with peers during encounters
- [x] RA username and API key saved locally and synced to Supabase; persisted across app updates
- [x] Badge gallery on profile: achieved badges shown with correct tier colours
- [x] Share ID card with one-tap share sheet (copy, WhatsApp, email, and more)
- [x] Device type field populated automatically and synced to the server

### 5.6 Badges
- [x] Thunder bolt replaces star on every badge type
- [x] Tier colours: dark grey (not achieved) → blue (Common) → purple (Uncommon) → orange (Rare) → gold (Legendary)
- [x] Level indicator: coloured lines beneath the thunder bolt, scaling with tier
- [x] Category grid: small squares in landscape, large cards in portrait; category icons are white and centered
- [x] `badges/badges.csv` defines all badges: id, category, name, description, how_to_achieve, tier, colour
- [x] Peer badge unlock logic driven by RetroAchievements data

### 5.7 Shop
- [x] Portrait layout: Volts balance card on the left, earn-points explanation on the right
- [x] Volts balance recalculated from encounter count on boot
- [x] Unlockable effects: CRT Scanlines, Pixelated Aura, Thunder Trail

### 5.8 Settings
- [x] Safe Zones and Battery Mode moved here (under Advanced, collapsed by default)
- [x] Keep-screen-on while ThunderPass service is active
- [x] OTA update check: polls GitHub Releases API on launch; amber banner with Download button if a newer version is found
- [x] Background music toggle (default on; plays `thunderpass-bg.mp3` on open)
- [x] Privacy Mode toggle
- [x] App management section with permission shortcuts and service controls

### 5.9 About Screen
- [x] Ko-fi support link styled as a donation button
- [x] Developer avatar pulled from Ko-fi profile
- [x] "Report an Issue" button (opens GitHub Issues in browser)
- [x] App version and build number

### 5.10 Platform and Reliability
- [x] `FEATURE_BLUETOOTH_LE` capability check on start — blocking error if unsupported
- [x] `isMultipleAdvertisementSupported` check — user warning if background sync may be degraded
- [x] Bluetooth enable request via `ACTION_REQUEST_ENABLE` when toggled on while BT is off
- [x] First-launch Doze-mode whitelist prompt
- [x] LED notification: three yellow blinks on supported devices when a new encounter is detected
- [x] Near-peer deduplication: devices consistently adjacent do not repeatedly generate Volts

---

## Milestone 6 — Platform Growth *(In Progress)*
**Goal:** Expand the platform with server-side intelligence, location events, and a versioned BLE protocol.

- [x] Profile synced from Supabase on startup — server data applied only when newer than local state
- [ ] Versioned BLE protocol extensions (backward-compatible)
- [ ] Stats over BLE: Volts, badge count, streaks, and pass count included in GATT payload (omitted when privacy mode is on)
- [ ] Power Surge Events: location-based 2× Volt multipliers
- [ ] Optional encounter cloud backup and restore
- [ ] Remote privacy controls and blocklist

---

## Status Summary

| Milestone | Description | Status |
|---|---|---|
| 0 | Foundation | ✅ Complete |
| 1 | StreetPass Core | ✅ Complete |
| 2 | Energy & Quality of Life | ✅ Complete |
| 3 | Fun Layer & Retro | ✅ Complete |
| 4 | Accounts & Cloud Sync | ✅ Partial |
| 5 | UX Overhaul | ✅ Complete |
| 6 | Platform Growth | 🔄 In Progress |