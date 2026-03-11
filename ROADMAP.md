# ThunderPass — Product Roadmap

ThunderPass is a modern StreetPass-inspired Android app built on Bluetooth Low Energy. This roadmap reflects the product's evolution from a working MVP to a polished, cloud-connected experience.

## Status Summary

| Milestone | Description | Status |
|---|---|---|
| 0 | Foundation | ✅ Complete |
| 1 | StreetPass Core | ✅ Complete |
| 2 | Energy & Quality of Life | ✅ Complete |
| 3 | Fun Layer & Retro | ✅ Complete |
| 4 | Accounts & Cloud Sync | ❌ Removed |
| 5 | UX Overhaul | ✅ Complete |
| 6 | Platform Growth | ✅ Complete |
| 7 | UI Polish & Feature Completion | 🔄 In Progress |
| 8 | Security Hardening & Release Readiness | 🔄 In Progress |

**Current release:** v0.8.0 (versionCode 15) — 2×2 widget, adaptive icon, security hardening (FLAG_SECURE, data extraction rules), Obtainium support.

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
- [x] Rotating identity via HMAC-SHA256 (60-minute window) for privacy
- [x] Encounter deduplication (24-hour cooldown per identity)
- [x] GATT server and client for profile card exchange
- [x] Core screens: Home, Passes, Profile, Navigation
- [x] Brand theme and unit tests
- [ ] Local data export / import *(deferred)*

---

## Milestone 2 — Energy and Quality of Life
**Goal:** Introduce the Volts energy system and improve day-to-day reliability.

- [x] Encounter widget showing new-encounter count on the home screen
- [x] Volts engine — earns 100 V per unique Spark; recalculated on launch
- [x] Haptic feedback: double-pulse vibration on GATT exchange (toggle in Settings → General)
- [x] Safe Zones manual toggle (Settings → Advanced)
- [x] Battery scan modes: Battery Saver / Balanced / Always On (Settings → General)
- [x] Improved UX copy and first-launch onboarding

---

## Milestone 3 — Fun Layer and Retro Integration
**Goal:** Add gamification depth and RetroAchievements connectivity.

- [x] Ghost payload type — high score and time exchange over BLE
- [x] Sticker book foundation
- [x] Encounter statistics: total counts, streaks, Volts displayed on Home
- [x] RetroAchievements integration — `RetroProfile`, GATT sharing, `RetroAuthManager`, `RetroRepository`
- [x] Retro Spark Card with mastery icon row and reference achievements
- [x] RA achievement OS notifications removed — triggers still detected and logged; no user-visible notification fired for peer milestones
- [x] Visual Shop: CRT Scanlines, Pixelated Aura, Thunder Trail effects

---

## Milestone 4 — Accounts and Cloud Sync *(Removed)*
**Goal:** ~~Introduce optional cloud identity so users’ data is safe and accessible across reinstalls.~~

**Status**: All Supabase code has been removed. ThunderPass is now fully offline — no cloud servers, no accounts, no internet required. The features below were implemented but have been deliberately stripped in favor of the offline-first architecture.

- [x] ~~Supabase Email OTP sign-in (no password required)~~ — **removed**
- [x] ~~Anonymous sign-in~~ — **removed**
- [x] ~~Auto-sync profile to Supabase~~ — **removed**
- [x] Web dashboard (GitHub Pages) — static page remains at `docs/index.html`
- [x] Profile sync triggered after Spark encounters — dedup path now refreshes friend card with latest data instead of dropping it (local-only)
- [ ] ~~Optional encounter backup and restore~~ — deferred
- [ ] ~~Remote blocklist and privacy controls~~ — deferred
- [ ] ~~GitHub-managed deployment automation~~ — deferred

---

## Milestone 5 — UX Overhaul and Feature Completion
**Goal:** Polish every screen, sharpen identity, and complete the core feature set.

### 5.1 Identity and Device Fingerprinting
- [x] Stable installation ID derived from `ANDROID_ID` (SHA-256, UUID v5 format) — survives reinstalls without requiring any permission
- [x] Profile synced from Supabase on startup — local data wins offline; server data applied when it is newer
- [x] Display name pre-filled from Android account or device model (Thor, Odin, Retroid Pocket, etc.)
    - [x] Privacy mode: hides stable identity (userId, instId, retroUsername, sig, deviceType) from all BLE payloads; name, avatar, greeting, Volts, Passes, Badges, and streak are still shared so friend cards remain meaningful
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
- [x] Personal phrase (greeting) field — shared with peers during encounters; max 60 characters, trailing spaces stripped
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
- [x] In-development banner shown at top of Badges screen

### 5.7 Shop
- [x] Portrait layout: Volts balance card on the left, earn-points explanation on the right
- [x] Volts balance recalculated from encounter count on boot
- [x] Unlockable effects: CRT Scanlines, Pixelated Aura, Thunder Trail
- [x] In-development banner shown at top of Shop screen

### 5.8 Settings
- [x] **Scanning Mode** radio (Battery Saver / Balanced / Always On) merged into General section
- [x] **Vibration** toggle in General — enable/disable haptic feedback on encounters
- [x] LED Flash on Encounter moved to Permissions section; requested at install time
- [x] Safe Zone toggle in Advanced
- [x] Keep-screen-on while ThunderPass service is active
- [x] ~~OTA update check: polls GitHub Releases API on launch~~ — **removed** (`OtaChecker.kt` deleted with Supabase code; `HomeViewModel.availableUpdate` is dead code, always null)
- [x] Background music toggle (default on; plays `thunderpass-bg.mp3` on open)
- [x] Privacy Mode toggle
- [x] App management section with permission shortcuts and service controls

### 5.9 About Screen
// [x] GitHub / Discord social links as icon circles (white icon on dark circle / dark icon on light circle)
// [x] Developer avatar (no Ko-fi)
- [x] Gradient card wrapper with decorative squares (same style as Home/Badges/Profile cards)
- [x] All content contained inside card — card never touches screen edges
- [x] Back button / TopAppBar matching all other screens
- [x] App version and build number

### 5.10 Platform and Reliability
- [x] `FEATURE_BLUETOOTH_LE` capability check on start — blocking error if unsupported
- [x] `isMultipleAdvertisementSupported` check — user warning if background sync may be degraded
- [x] Bluetooth enable request via `ACTION_REQUEST_ENABLE` when toggled on while BT is off
- [x] First-launch Doze-mode whitelist prompt
- [x] LED notification: three yellow blinks on supported devices when a new encounter is detected
- [x] Near-peer deduplication: three-layer guard (60-min scan / 60-min rotating-ID / 24h identity) — at most one Spark and one Volt reward per person per day; privacy-mode peers deduplicated by MAC address within the same 24-hour window

---

## Milestone 6 — Platform Growth *(In Progress)*
**Goal:** Expand the platform with a versioned BLE protocol, connection resilience, and future event systems.

- [x] ~~Profile synced from Supabase on startup~~ — **removed** (Supabase deleted; app is fully offline)
- [x] Versioned BLE protocol extensions — GATT payload versioned, backward-compatible
- [x] Stats over BLE: Volts, badge count, streaks, and pass count included in GATT payload — shared even in privacy mode (only stable identity fields are withheld)
- [x] GattClient connection retry: up to 2 retries (3 total attempts) with 2.5 s delay on pre-connection failures (e.g. BLE race condition status=62). Fresh ephemeral ECDH keys per retry.
- [x] **2×2 home screen widget** — profile card (avatar + name + status + Volts) + Passes / Badges / Streak stats row; registered alongside the existing 2×1 toggle widget
- [x] EncounterWidget unregistered from manifest — only the two ThunderPass-branded widgets exposed in the picker
- [x] Widget sync fixed — all 6 call sites (BleService, HomeViewModel, ToggleAction) now refresh both the 2×1 and 2×2 widgets together, preventing stale state
- [ ] Power Surge Events: location-based 2× Volt multipliers
- [ ] Optional encounter cloud backup and restore
- [ ] Remote privacy controls and blocklist

---

## Milestone 7 — UI Polish & Feature Completion *(In Progress)*
**Goal:** Finish all deferred visual and feature items, sharpen every screen.

### 7.1 Passes & Traveler View
- [ ] Landscape layout: divider line between left/right areas shown in a yellow/amber accent colour
- [ ] Passes friend card: rounded rectangular background behind avatar + username + phrase; encounter info shown as compact inline chips on the right
- [ ] RetroAchievements data on friend card: show points (softcore), last played games, and consoles; no web API key required for viewing a friend profile
- [ ] Friend card consoles: console images visible with same styling as Home screen
- [ ] Shared-profile dedup: if same profile passes again, update the last-seen timestamp only — do not create a new card when the encounter is within 4 hours

### 7.2 Home Screen
- [x] Header area: gradient card with decorative squares matching Badges/About card style
- [x] Header: Volts count on the right side (⚡ + voltsTotal), clickable → Shop
- [ ] Light shadows on action buttons and animation area
- [ ] ThunderPass enable toggle moved to above the animation area (not inside it)
- [ ] Animation area aligned with top of the left-hand button column
- [ ] Landscape divider between left/right panels shown in yellow/amber

### 7.3 Profile Screen
- [x] Split into two-column landscape layout
- [ ] Left column: gradient card with avatar, Volts, Passes, Badges achieved, streak, device name (AYN Thor / Retroid / etc.); dice button for random avatar; tap avatar → Sparky Editor
- [ ] Right column: RetroAchievements area — last games, softcore points, consoles with images
- [ ] Display name, phrase (≤60 chars, trailing spaces stripped, injection-safe), RA username (no API key field) — auto-saved on change; Save Profile button removed
- [ ] Rename "Traveler" label to "SparkyUser" throughout the app
- [ ] Device name used in default greeting: "Hey, greetings from [Device Name]!"
- [ ] Remove badge gallery from Profile screen (badges live in the Badges tab)
- [ ] Shadows on all cards matching the rest of the app

### 7.4 Sparky Editor
- [ ] Landscape divider between preview/sliders panels in app accent colour
- [ ] Hair/colour/option selector cards: simple grayish shadow style matching other cards; radio buttons follow gradient colours
- [ ] Gradient card background matching Badges/About/Home style; avatar circle transparent over the card
- [ ] When entering the editor, sliders pre-loaded from current avatar values (random or saved) — always reflects the current avatar

### 7.5 Badges Screen
- [ ] Category header shows achieved count / total (e.g. "3 / 10") even when count is 0
- [ ] Category banner area: gradient card with decorative squares matching Badges/Home card style

### 7.6 Badge Definitions Update
- [ ] **Alfa Tester** (was Core Architect): awarded to everyone who installs before v0.7 launches
- [ ] **Beta Tester** (was Kinetic Beta): awarded to everyone who installs before v0.8 launches
- [ ] **Node Zero**: awarded to the first 100 users confirmed via Supabase
- [ ] **Shared Quest**: awarded when user connects a valid RetroAchievements username and downloads profile data
- [ ] **FriendlyFire with Hardcore Sync**: awarded when adding the first friend to favourites; values and positions swapped with its previous definition

### 7.7 RetroAchievements
- [x] RetroRepository.fetchAndCache() fully implemented — HTTP fetch via OkHttp, Room persistence, 3 achievement triggers (PlatinumPulse, LegendaryEncounter, RetroCircuit)
- [x] RA username resolution priority: Room → EncryptedSharedPreferences → cache (fixes stale mid-typing cache poisoning)
- [ ] Always show softcore points for the peer (on friend card and own profile when RA is connected)
- [ ] Remove Web API key field — RA public profile data accessible with username only
- [ ] Retro card text rendered in white for readability

### 7.8 Unknown User Validation
- [ ] Do not create a "Last Passed By" entry for an unconfirmed traveler (no data confirmed from server); prefer showing nothing over phantom or duplicate users farming Volts
- [ ] Validate uniqueness before crediting any Volts

### 7.9 Identity *(Revised \u2014 Supabase Removed)*
- [x] Device-derived installation ID used for local encounter dedup and device sync (no server confirmation needed)
- [ ] ~~Display total unique-user count (server-side) on the web dashboard~~ \u2014 deferred (no server)
- [ ] ~~Roadmap note: users who delete the app or factory-reset lose data; named account linking planned~~ \u2014 documented above in \"What Happens if You Delete the App\"

### 7.10 Known Bugs
- [x] **LED flash blinking not working** — fixed: `getString(…) ?: return` bailed when key not yet present in Secure settings; now proceeds and only restores previous color if it existed
- [ ] **Avatar not pre-loaded in Sparky Editor** — when entering the editor, sliders must reflect the currently displayed avatar (random or saved), not defaults
- [ ] **Online profile cards not auto-refreshing** — every time the app connects to the internet it should re-fetch profile data for all saved pass cards and update them locally

---

## Security, Privacy and Data Model

### How BLE Works in ThunderPass
ThunderPass uses **Bluetooth Low Energy (BLE)** — a short-range, low-power radio standard built into every modern Android device. The service runs quietly in the background and does two things simultaneously:

1. **Advertising** — broadcasts a tiny anonymous packet so nearby ThunderPass devices can detect you.
2. **Scanning** — listens for the same packets from other devices.

When two devices recognize each other, a **GATT connection** is opened (typically under 2 seconds). The devices exchange profile cards — name, avatar, greeting, stats — and then immediately disconnect. No persistent pairing, no ongoing link.

---

### What Is Shared Over BLE
The BLE advertising packet contains **no personal information**. It holds only:
- The ThunderPass **service UUID** (identifies this as a ThunderPass device, nothing more).
- A **rotating anonymous ID** (see below).

Personal data (display name, greeting, avatar, Volts, Badges, Passes, streak) is only transferred during a GATT exchange and only to another verified ThunderPass device. If **Privacy Mode** is enabled, the GATT payload still includes name, avatar, greeting, and stats — but withholds all stable identity fields (`userId`, `instId`, `sig`, `retroUsername`, `deviceType`) so the peer receives a meaningful card without any information that could be used to identify or track the user across encounters.

---

### Rotating Identity — No Long-Term Tracking
The device never broadcasts its real ID. Instead:

```
rotatingId = Base64Url( HMAC-SHA256(installationId, floor(epochMs / 3 600 000)) [0..15] )
```

- The **installation ID** is generated once from `ANDROID_ID` (SHA-256, UUID v5 layout) and stored locally. It never leaves the device in plaintext.
- The **rotating ID** is a 16-byte HMAC truncation that changes every **60 minutes**.
- A passive observer tracking the rotating ID in the air loses the trail every hour — they cannot link two consecutive windows to the same device without the private installation ID.

---

### Encounter Deduplication — Three Layers

| Layer | Scope | Window | Effect |
|---|---|---|---|
| **Scan-level** | Per `device.address` | **60 minutes** | Suppresses redundant GATT connections to the same hardware for the full rotating-ID window |
| **Rotating-ID** | Per rotating ID | 60 minutes | The same rotating ID cannot open more than one GATT exchange per hour |
| **Identity** | Per Supabase `userId` or installation ID | **24 hours** | At most one Spark and one Volt reward per person per day; the existing pass card is updated with the latest profile data on each re-encounter |

---

### How the Device Identifies a User
No account, server, or registration is required. On first launch the device derives a stable installation ID from `ANDROID_ID` via SHA-256 (UUID v5 format). This ID is used locally for encounter deduplication and device sync — it never leaves the device in plaintext.

---

### What Happens if You Delete the App or Factory Reset the Device?

ThunderPass stores two kinds of state:

| What | Stored where | Survives uninstall? | Survives factory reset? |
|---|---|---|---|
| Display name, avatar, greeting, Volts, stickers | Local Room database | ❌ No — lost on uninstall | ❌ No |
| Pass history (Sparks list) | Local Room database | ❌ No — lost on uninstall | ❌ No |
| ~~Anonymous session UUID~~ | ~~Android SharedPreferences~~ | N/A — Supabase removed | N/A |
| Installation ID | SharedPreferences (derived from `ANDROID_ID`) | ✅ Yes — same device produces same ID via SHA-256 | ❌ No — `ANDROID_ID` changes after factory reset |

**In plain terms:**
- If you **uninstall and reinstall**: your profile, Spark history, badges, and Volts are all gone. The installation ID will be the same (derived from `ANDROID_ID`), but all other data must be rebuilt.
- If you **factory reset**: same as above, plus your `ANDROID_ID` changes so you get a new identity.
- **Device Sync** (two-device pairing) keeps your data on multiple physical devices. If one is lost, the other retains a full copy.

> 📌 **Planned for a future version:** optional local export/import of Spark history for backup purposes.

---

## Milestone 8 — Security Hardening & Release Readiness *(Planned)*
**Goal:** Resolve all findings from the internal security audit and prepare the codebase for a public v1.0 release.

> Full audit details: [`.internal/SECURITY_AUDIT.md`](.internal/SECURITY_AUDIT.md)

### 8.1 Database Migrations (Medium Priority)
- [ ] Replace `fallbackToDestructiveMigration()` with explicit `Migration` objects for every schema version increment
- [ ] Write migration tests using Room's `MigrationTestHelper` to verify data is preserved across upgrades
- [ ] Document the migration path from current version 17 to v1.0 schema

### 8.2 HKDF Salt Versioning (Low Priority)
- [ ] Add a protocol version byte to the BLE handshake that selects the HKDF salt
- [ ] Current `"thunderpass-ble-v2"` salt remains the default — new salt introduced only when the protocol version is bumped
- [ ] Ensure backward compatibility: older clients continue to work until a minimum protocol version is enforced

### 8.3 Security-Crypto Library (Low Priority)
- [ ] Monitor `androidx.security:security-crypto` for a stable (non-alpha) release
- [ ] When available, upgrade from `1.1.0-alpha06` and validate EncryptedSharedPreferences migration
- [ ] If stable release is delayed beyond v1.0, document the alpha dependency and its risk profile

### 8.4 Rogue-App Detection Hardening (Low Priority)
- [ ] Evaluate whether signature verification failures should trigger a user-visible warning (currently only logged)
- [ ] Add a "suspicious encounter" counter visible in Settings → Advanced for transparency
- [ ] Consider a configurable policy: log-only (default) vs. silent-drop vs. user-alert

### 8.5 Auto-Walk & Battery Optimization Audit
- [ ] Verify Auto-Walk correctly pauses scanning on all tested devices (AYN Thor, Retroid Pocket, stock Android)
- [ ] Profile battery consumption across the three scan modes with real-world usage traces
- [ ] Document expected battery impact per mode in user-facing settings descriptions
- [ ] Ensure Doze-mode whitelist prompt is shown once and respected

### 8.6 Permissions Audit
- [ ] Review `WRITE_SETTINGS` usage — confirm it is guarded and only used on supported AYN devices
- [x] Review `PACKAGE_USAGE_STATS` — ensure it is optional and the app degrades gracefully when denied
- [x] Verify all permission-gating in SettingsScreen matches AndroidManifest declarations
- [x] Non-blocking permission flow: permissions auto-requested on launch, red header when denied, tap navigates to Settings with highlighted Permissions section
- [x] Activity Recognition permission added to Settings Permissions section

### 8.7 Release Checklist
- [ ] ProGuard rules validated — confirm all Log calls stripped and no sensitive strings survive in release APK
- [x] `network_security_config.xml` verified — cleartext blocked, user CAs blocked in release
- [x] `allowBackup="false"` confirmed in manifest
- [x] `dataExtractionRules` XML added — explicitly blocks cloud Auto Backup and D2D device transfer on Android 12+
- [x] `FLAG_SECURE` set in MainActivity — blocks screenshots, screen recording, Recent Apps thumbnail, and overlay spyware from capturing any app screen
- [ ] Full test pass on Android 12, 13, 14, and 15 devices
- [ ] Final security audit sign-off documented in `.internal/SECURITY_AUDIT.md`

### 8.8 Documentation
- [x] Internal security audit (`.internal/SECURITY_AUDIT.md`)
- [x] Architecture reference (`.internal/ARCHITECTURE.md`)
- [x] BLE protocol specification (`.internal/BLE_PROTOCOL.md`)
- [x] Cryptographic primitives reference (`.internal/CRYPTOGRAPHY.md`)
- [x] Data model reference (`.internal/DATA_MODEL.md`)
- [x] Component reference (`.internal/COMPONENTS.md`)
- [x] AI agent context files (`AGENTS.md` + per-package leaf files) — excluded from git, never public
- [x] `ROADMAP.md` excluded from git — internal planning document, never public
- [ ] Contributor onboarding guide (`CONTRIBUTING.md`)

### 8.9 App Icon Overhaul
- [x] Adaptive icon introduced — `mipmap-anydpi-v26/` XMLs created, takes priority over density PNGs on all supported devices (minSdk=33)
- [x] Background: solid black (`#000000`)
- [x] Foreground: `black.png` logo (500×500) on full transparency — no background fill in foreground layer

