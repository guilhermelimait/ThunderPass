ThunderPass Roadmap
Milestone 0 — Repository + Android App Scaffold ✅
[x] Android project scaffold (Kotlin + Jetpack Compose)
[x] Foreground service (BleService) + persistent notification
[x] Runtime permission flow for Android 13 BLE + POST_NOTIFICATIONS
[x] Local storage with Room (MyProfile, Encounter, PeerProfileSnapshot)
[x] CI workflow (GitHub Actions) — assembles debug APK on every push/PR

Milestone 1 — StreetPass MVP ✅
[x] BLE advertise + scan (concurrent, low-power)
[x] Rotating ID via HMAC-SHA256 (30-min window, never broadcasts raw install ID)
[x] Encounter dedup (10-min cooldown per rotating ID)
[x] GATT server + client (profile card exchange over REQUEST/RESPONSE characteristics)
[x] Presence packet binary format [version:1][flags:1][rotatingId:16]
[x] UI: Home (status + start/stop), Encounters list, Profile edit, Navigation
[x] Brand theme (ThunderYellow + ThunderGray, dark/light, adaptive icon)
[x] Unit tests: RotatingIdUtils, EncounterDedup (JVM, no Android runtime)
[ ] Export / import local data (optional, deferred to M2)

Milestone 2 — Trust, Quality + Energy Base
[ ] “Safe Zones” (manual toggle + Wi‑Fi SSID + GPS Geofencing)
[ ] Battery modes: aggressive (foreground), balanced (timed), off
[ ] Energy Engine: Implement "Joules" counter logic in Room (100 J per unique Spark)
[ ] Haptic Feedback: Double-pulse vibration ("The Spark") on successful GATT exchange
[ ] Widget: “new encounters” indicator
[ ] Better UX copy + onboarding (The "Grid" introduction)

Milestone 3 — Fun Layer + Game Sync
[ ] Ghost payload type (score/time)
[ ] Sticker book (basic, non-location-based first)
[ ] Encounter stats (counts, streaks)
[ ]RetroAchievements Integration:
[ ] Create RetroAuthManager to securely store the user's RA API Key.
[ ] Build RetroRepository (OOD) to fetch and cache peer data (Points, Rank, Recent Mastery).
[ ] Dynamic Profile Card:
[ ] Implement RetroSparkCard (Compose) to show RA Rank and Points alongside the ThunderBolt.
[ ] Add "Mastery Icons" (scrolling row) to the encounter detail view.
[ ] Famous Reference Achievements:
[ ] Platinum Pulse: Trigger if peer has TotalPoints > 20,000 (High Energy).
[ ] Legendary Encounter: Trigger if both users have the same game in their "Recently Played" list.
[ ] Retro Circuit: Trigger if peer is an "Active Master" (Mastered a game in the last 30 days).
[ ] Visual Shop: - [ ] Unlock "CRT Scanlines" or "Pixelated Aura" profile effects using Joules.

Milestone 4 — Accounts + Cloud Sync
[ ] Passwordless Auth: Firebase Magic Link sign-in (Sync Bio-ID)
[ ] Deep Link Integration: Handle thunderpass.page.link to auto-verify Gmail links
[ ] Opt-in sign-in + Sync My Card across devices
[ ] Optional encounter backup/restore
[ ] GitHub-managed deployment automation (infra-as-code)
[ ] Remote blocklist / privacy controls

Milestone 5 — Platform (future)
[ ] Public “payload provider” API / SDK for other apps
[ ] Versioned protocol extensions
[ ] Power Surge Events: Location-based 2x Energy multipliers