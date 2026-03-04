# ThunderPass Roadmap

## Milestone 0 — Repository + Android App Scaffold
- [x] Android project scaffold (Kotlin + Jetpack Compose)
- [x] Foreground service (BleService) + persistent notification
- [x] Runtime permission flow for Android 13 BLE + POST_NOTIFICATIONS
- [x] Local storage with Room (MyProfile, Encounter, PeerProfileSnapshot)
- [x] CI workflow (GitHub Actions) — assembles debug APK on every push/PR

---

## Milestone 1 — StreetPass MVP
- [x] BLE advertise + scan (concurrent, low-power)
- [x] Rotating ID via HMAC-SHA256 (30-min window)
- [x] Encounter dedup (10-min cooldown per rotating ID)
- [x] GATT server + client (profile card exchange)
- [x] UI: Home, Encounters list, Profile edit, Navigation
- [x] Brand theme, unit tests
- [ ] Export / import local data (deferred)

---

## Milestone 2 — Trust, Quality + Energy Base
- [x] Widget: new encounters indicator
- [x] Energy Engine: Joules counter (100 J per unique Spark)
- [x] Haptic Feedback: Double-pulse vibration on GATT exchange
- [x] Safe Zones manual toggle
- [x] Battery modes: aggressive, balanced, off
- [x] Better UX copy + onboarding

---

## Milestone 3 — Fun Layer + Game Sync
- [x] Ghost payload type (score/time)
- [x] Sticker book (basic)
- [x] Encounter stats (counts, streaks, Joules on HomeScreen)
- [x] RetroAchievements Integration (RetroProfile, GATT share, RetroAuthManager, RetroRepository)
- [x] RetroSparkCard, Mastery Icons row, reference achievements
- [x] Visual Shop: CRT Scanlines, Pixelated Aura, Thunder Trail

---

## Milestone 4 — Accounts + Cloud Sync (partial)
- [x] Supabase Email OTP sign-in
- [x] Auto-sync profile to Supabase profiles table
- [x] Web dashboard (GitHub Pages)
- [x] Profile sync after Spark encounters
- [ ] Optional encounter backup/restore
- [ ] Remote blocklist / privacy controls
- [ ] GitHub-managed deployment automation

---

## Milestone 5 — UX Overhaul & Feature Completion (Next MVP)

### 5.1 Identity & Naming
- [ ] Read Android account display name at startup; fall back to device model name (Thor, Odin, etc.) — replace hardcoded "Traveler".
- [ ] Replace Installation ID with a human-readable name-based share ID. Show as "Private User" when privacy toggle is on.
- [ ] Profile fields trimmed to essentials: Display Name, RA Username, RA API Key. Saved locally + synced to Supabase; included in BLE ghost payload.

### 5.2 ThunderPass Toggle & Foreground Service
- [ ] Replace Scan / Idle states with a single ON / OFF toggle. Foreground service persists state when app is closed.
- [ ] Remove Safe Zones and Battery Mode from Home — move to Settings (5.10).

### 5.3 Navigation & Bottom Bar
- [ ] Fixed bottom bar always visible across all screens (no teardown on navigate).
- [ ] Seven tabs: Home · Passes · Profile · Badges · Shop · Settings · About
- [ ] Tab icons: Home=walking figure, Passes=thunder bolt, Profile=live avatar head, Badges=thunder badge, Shop=cart, Settings=cog, About=coffee mug
- [ ] Landscape: bar height ~48 dp, icons only (no labels).

### 5.4 Home Screen
- [ ] Remove Today/Streak stat cards; move data to Profile.
- [ ] "Last Passed By" strip below walking animation: peer avatar + name + relative time. Tap opens encounter detail.
- [ ] Joules explanation panel: what they are and how to earn them.
- [ ] Tapping the user avatar in the walking scene navigates to Profile.

### 5.5 Bluetooth Reliability & Permissions
- [ ] Check PackageManager.FEATURE_BLUETOOTH_LE on start; show blocking error if unsupported.
- [ ] Check BluetoothAdapter.isMultipleAdvertisementSupported(); warn if background sync is limited.
- [ ] If BT is off when toggling ON, request enable via BluetoothAdapter.ACTION_REQUEST_ENABLE.
- [ ] First-launch prompt: disable battery optimisation (Doze-mode whitelist).
- [ ] Audit BLE concurrency; verify both test devices (RP4 Pro + AYN Thor) discover each other reliably.

### 5.6 Passes (Encounters)
- [ ] On new encounter: status-bar notification "ThunderPass! [DisplayName] is nearby." Omit name if peer is private.
- [ ] Double-pulse vibration if haptics are enabled.
- [ ] Passes screen: live list with peer avatar, name, RSSI, relative time.
- [ ] Friends list accessible from Passes and Profile. Tap a friend to open their badge dashboard.

### 5.7 Profile Screen
- [ ] Avatar maker: DiceBear seed selector + Randomise button. Syncs to walking animation and bottom-bar icon immediately; saved to Supabase.
- [ ] Privacy toggle: when on, BLE exchange shows "Private User" to peers.
- [ ] Badge gallery: horizontal scrollable row, exotic/legendary first, achieved only. Remove legend row and duplicate Volt Badge.
- [ ] Share ID uses display name, not installation ID.
- [ ] All edits saved to Supabase; Room is source-of-truth offline.

### 5.8 Badges
- [ ] Replace star icon with a thunder bolt on every badge.
- [ ] Six categories: Consoles · Player Numbers · Geolocation · Shared Games · RetroAchievements · Auto.
- [ ] Tier colours: Dark grey=Not achieved, Blue=Common (1-2), Purple=Uncommon (3-4), Orange=Rare (5-6), Gold=Legendary (7).
- [ ] Category grid: small squares in landscape, large cards in portrait.
- [ ] Remove "Best" highlight from categories overview.
- [ ] Create badges/badges.csv — columns: id, category, name, description, how_to_achieve, tier, colour.

### 5.9 Shop
- [ ] Portrait layout: Joules balance card on the left, earn-points explanation on the right.
- [ ] On load: recalculate Joules from achieved badges and award missing points.
- [ ] Keep existing unlockable effects (CRT Scanlines, Pixelated Aura, Thunder Trail).

### 5.10 Settings
- [ ] Move Safe Zones and Battery Mode here (under Advanced, collapsed by default).
- [ ] Keep screen on (FLAG_KEEP_SCREEN_ON) while ThunderPass service is active.
- [ ] OTA updates: poll GitHub Releases API; show banner + download link if newer version exists.
- [ ] Background music toggle — default ON. Play assets/thunderpass-bg.mp3 on app open; skip if user disabled it.

### 5.11 About Screen
- [ ] Ko-fi link: https://ko-fi.com/guilhermelimait/ — styled as a support/donation button.
- [ ] Developer avatar + short bio.
- [ ] "Report an Issue" button — opens GitHub Issues URL in browser.
- [ ] App version + build number.

### 5.12 Removals
- [ ] Remove Sticker Book screen and all related code.
- [ ] Remove Safe Zone toggle from Home.
- [ ] Remove Battery Mode selector from Home.
- [ ] Remove duplicate Volt Badge widget from Profile Joules area.
- [ ] Remove badge legend row from Profile.

---

## Milestone 6 — Platform (future)
- [ ] Public "payload provider" API / SDK for other apps
- [ ] Versioned protocol extensions
- [ ] Power Surge Events: Location-based 2x Energy multipliers
