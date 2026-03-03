# ThunderPass Roadmap

## Milestone 0 — Repository + Android App Scaffold (MVP foundation)
- Create Android Studio project (Kotlin)
- Jetpack Compose UI
- Foreground service skeleton + notification
- Permissions flows for Android 13 BLE
- Local storage (Room)
- CI workflow on GitHub to build debug APK

## Milestone 1 — StreetPass MVP (usable)
- BLE advertise + scan
- Dedup + encounter logging
- GATT exchange of “Profile Card”
- UI: My Card, Encounters, Status/Debug
- Export/import local data (optional)

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