# ThunderPass Roadmap

## Milestone 0 — Repository + Android App Scaffold ✅
- [x] Android project scaffold (Kotlin + Jetpack Compose)
- [x] Foreground service (`BleService`) + persistent notification
- [x] Runtime permission flow for Android 13 BLE + POST_NOTIFICATIONS
- [x] Local storage with Room (MyProfile, Encounter, PeerProfileSnapshot)
- [x] CI workflow (GitHub Actions) — assembles debug APK on every push/PR

## Milestone 1 — StreetPass MVP ✅
- [x] BLE advertise + scan (concurrent, low-power)
- [x] Rotating ID via HMAC-SHA256 (30-min window, never broadcasts raw install ID)
- [x] Encounter dedup (10-min cooldown per rotating ID)
- [x] GATT server + client (profile card exchange over REQUEST/RESPONSE characteristics)
- [x] Presence packet binary format `[version:1][flags:1][rotatingId:16]`
- [x] UI: Home (status + start/stop), Encounters list, Profile edit, Navigation
- [x] Brand theme (ThunderYellow + ThunderGray, dark/light, adaptive icon)
- [x] Unit tests: RotatingIdUtils, EncounterDedup (JVM, no Android runtime)
- [ ] Export / import local data (optional, deferred to M2)

## Milestone 2 — Trust + Quality
- “Safe Zones” (manual toggle + Wi‑Fi SSID based)
- Battery modes: aggressive (foreground), balanced (timed), off
- Widget: “new encounters” indicator
- Better UX copy + onboarding

## Milestone 3 — Fun Layer
- Ghost payload type (score/time)
- Sticker book (basic, non-location-based first)
- Encounter stats (counts, streaks)

## Milestone 4 — Accounts + Cloud Sync (optional)
- Opt-in sign-in
- Sync My Card across devices
- Optional encounter backup/restore
- GitHub-managed deployment automation (infra-as-code)
- Remote blocklist / privacy controls

## Milestone 5 — Platform (future)
- Public “payload provider” API / SDK for other apps
- Versioned protocol extensions