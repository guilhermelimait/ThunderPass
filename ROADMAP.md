# ThunderPass — Product Roadmap

ThunderPass is a modern StreetPass-inspired Android app built on Bluetooth Low Energy. This roadmap reflects the product's evolution from a working MVP to a polished, cloud-connected experience.

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
| 7 | UI Polish & Feature Completion | 🔄 In Progress |

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

## Milestone 4 — Accounts and Cloud Sync
**Goal:** Introduce optional cloud identity so users' data is safe and accessible across reinstalls.

- [x] Supabase Email OTP sign-in (no password required)
- [x] **Anonymous sign-in** — every device automatically gets a Supabase session on first launch, no account creation required. Used for encounter verification and 24-hour identity deduplication.
- [x] Auto-sync profile to Supabase `profiles` table (display name, greeting, avatar seed, ghost score, stickers)
- [x] Web dashboard (GitHub Pages)
- [x] Profile sync triggered after Spark encounters — dedup path now refreshes friend card with latest data instead of dropping it
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
- [x] OTA update check: polls GitHub Releases API on launch; amber banner with Download button if a newer version is found
- [x] Background music toggle (default on; plays `thunderpass-bg.mp3` on open)
- [x] Privacy Mode toggle
- [x] App management section with permission shortcuts and service controls

### 5.9 About Screen
- [x] Ko-fi / GitHub / Discord social links as icon circles (white icon on dark circle / dark icon on light circle)
- [x] Developer avatar pulled from Ko-fi profile
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
**Goal:** Expand the platform with server-side intelligence, location events, and a versioned BLE protocol.

- [x] Profile synced from Supabase on startup — server data applied only when newer than local state
- [ ] Versioned BLE protocol extensions (backward-compatible)
- [x] Stats over BLE: Volts, badge count, streaks, and pass count included in GATT payload — shared even in privacy mode (only stable identity fields are withheld)
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
- [ ] Header area: gradient card with decorative squares matching Badges/About card style
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
- [ ] Always show softcore points for the peer (on friend card and own profile when RA is connected)
- [ ] Remove Web API key field — RA public profile data accessible with username only
- [ ] Retro card text rendered in white for readability

### 7.8 Unknown User Validation
- [ ] Do not create a "Last Passed By" entry for an unconfirmed traveler (no data confirmed from server); prefer showing nothing over phantom or duplicate users farming Volts
- [ ] Validate uniqueness before crediting any Volts

### 7.9 Identity & Supabase
- [ ] Use device-derived installation ID + anonymous Supabase session to confirm a user is unique without requiring sign-in
- [ ] Display total unique-user count (server-side) on the web dashboard
- [ ] Roadmap note added: users who delete the app or factory-reset their device will receive a new anonymous identity; named account linking is planned for a future version

### 7.10 Known Bugs
- [ ] **LED flash blinking not working** — investigate `flashThorLeds()` in `BleService`, verify `WRITE_SECURE_SETTINGS` path on AYN Thor 2
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

### How the Server Recognises a User
No account or registration is required. On first launch ThunderPass automatically signs in **anonymously** via Supabase. This creates a server-side UUID tied to the session — invisible to the user, requiring no email or password.

---

### What Happens if You Delete the App or Factory Reset the Device?

ThunderPass stores two kinds of state:

| What | Stored where | Survives uninstall? | Survives factory reset? |
|---|---|---|---|
| Display name, avatar, greeting, Volts, stickers | Supabase `profiles` table (cloud) | ✅ Yes — restored on first launch after reinstall | ✅ Yes — if you sign in with the same Google / email account |
| Pass history (Sparks list) | Local Room database | ❌ No — lost on uninstall | ❌ No |
| Anonymous session UUID | Android SharedPreferences | ❌ No — new UUID issued on reinstall | ❌ No |
| Installation ID | SharedPreferences (derived from `ANDROID_ID`) | ✅ Yes — same device produces same ID via SHA-256 | ❌ No — `ANDROID_ID` changes after factory reset |

**In plain terms:**
- If you **uninstall and reinstall** without signing in: you get a fresh anonymous UUID and your Spark history is gone. Your cloud profile remains on the server but cannot be automatically linked back until you sign in.
- If you **factory reset**: same as above, plus your `ANDROID_ID` changes.
- If you **sign in with Google or email** before losing the app, your profile is fully restored after reinstall.

> 📌 **Planned for a future version:** optional cloud backup of the Spark history, and the ability to link an anonymous session to a named account after the fact.

