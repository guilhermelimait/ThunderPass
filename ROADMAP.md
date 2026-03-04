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
[x] Widget: "new encounters" indicator (Glance widget — total + today counts)
[x] Energy Engine: Implement "Joules" counter logic in Room (100 J per unique Spark)
[x] Haptic Feedback: Double-pulse vibration ("The Spark") on successful GATT exchange
[x] "Safe Zones" manual toggle (Wi‑Fi SSID + GPS Geofencing deferred)
[x] Battery modes: aggressive (foreground), balanced (timed), off
[x] Better UX copy + onboarding (The "Grid" introduction)

Milestone 3 — Fun Layer + Game Sync
[x] Ghost payload type (score/time)
[x] Sticker book (basic, non-location-based first)
[x] Encounter stats (counts, streaks, energy/Joules shown on HomeScreen)
[x] RetroAchievements Integration (Phase 1):
[x] RetroProfile + RetroRetrofitClient (Retrofit + Moshi) — fetchRetroMetadata(username)
[x] Peer RA username shared via GATT BLE profile card
[x] Auto-fetch peer RA stats after exchange, shown on Spark Card (EncounterDetailScreen)
[x] Create RetroAuthManager to securely store the user's RA API Key.
[x] Build RetroRepository (OOD) to fetch and cache peer data (Points, Rank, Recent Mastery).
[ ] Dynamic Profile Card:
[x] Implement RetroSparkCard (Compose) to show RA Rank and Points alongside the ThunderBolt.
[x] Add "Mastery Icons" (scrolling row) to the encounter detail view.
[ ] Famous Reference Achievements:
[x] Platinum Pulse: Trigger if peer has TotalPoints > 20,000 (High Energy).
[x] Legendary Encounter: Trigger if both users have the same game in their "Recently Played" list.
[x] Retro Circuit: Trigger if peer is an "Active Master" (Mastered a game in the last 30 days).
[x] Visual Shop: Unlock "CRT Scanlines", "Pixelated Aura", and "Thunder Trail" profile effects using Joules.

Milestone 4 — Accounts + Cloud Sync
[x] Supabase Email OTP sign-in (6-digit code, no password, 30-day session)
[x] Auto-sync profile to Supabase `profiles` table after every save
[x] Web dashboard (GitHub Pages — docs/index.html) — look up any user by Installation ID via Supabase REST API
[x] Profile sync triggered automatically after Spark encounters
[ ] Optional encounter backup/restore
[ ] Remote blocklist / privacy controls
[ ] GitHub-managed deployment automation (infra-as-code)

Milestone 5 — Platform (future)
[ ] Public “payload provider” API / SDK for other apps
[ ] Versioned protocol extensions
[ ] Power Surge Events: Location-based 2x Energy multipliers



a few things to include/change :
1- use the usersname on the android to name the user on the app instead of traveler, if no name is found, call him by the android device name, Thor, ODin and so on
2- instead of scanning and idle , make it on/off ThunderPass as a toggle, and make it as a service, so if the app is closed, the service keeps the value of the app
3- remove the safezone, no need for that, same for battery mode, move that part to the settings of the app under the user profile on a cog on the bottom bar
4- on the bottom app bar, when on landscape, make it smaller, it is too tall at the moment and the app is not beautiful, modern clean as asked, make the following buttons on it, home, passes, profile, badges, shop and settings
5- when on landscape mode, the app is cutting thte cards where total today and shreak is in, but i dont get why we have those cards, better to remove them and keep that kind of information on the profile area
5- if a new user is recognized around, create a notification that a thunder passer was identified
6- remove the sticker book, it doesnt make any connectoin to the app since we have the badge area,
7- make the bottom bar fixed, so wehn i move between areas of the app, it is always the same and visible
8- profile, make a profile maker, or random button so the user can select the profile avatar 
9- lets make this area of the profile much more interesting, check image paste 1 and 2 and lets mix the ideas with a clean layout
10- i was thinking to have the badges area something like the image 3 that could be progressing in each area, and the colors would be associated to consoles, player numbers, geolocation, games shared, integratoins (with retroarchive) and auto created by the app, not something based on the web, and instead of a star, it should have a thunder on it
11- on display name, ra key , ra name and so on, keep only those that are needed, short and clean, once saved, it saves locally and use that as ghost informatoin between the ble updates between users (not sure if it goes online somehow)
12- on home, explain the joules idea, how to collect points (based on badges and interactions,passes,retroarchive and so on)


Colour	badge
Dark grey - 0 badges achieved - name: not achieved
Blue - first two levels achieved - name: common
Purple - badge 3 and 4 achieved - name uncommon
Orange - badge 5 and 6 achieved - name rare 
gold - badge 7 - name legendary


[x] Android project scaffold

[ ] Hardware Capability Check: - [ ] Add packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE) check.

[ ] Add BluetoothAdapter.isMultipleAdvertisementSupported() check (Critical for AYN/Retroid background syncing).

[ ] Battery Optimization Bypass: Add a UI prompt to whitelist ThunderPass from Android's "Doze" mode.


1- make another button on the first area, about is the name, on it, add the link to my kofi and make it clear that it was made with love, https://ko-fi.com/guilhermelimait/, add my avatar picture on it, show my bio on it, create an area for issues so users can go to github and assign a github issue.

2- make all buttons like :
passes: a thunder, since it is the idea of the project
profile: should be the head of the user that was generated
badges: should be a badge like the ones we created
shop: a market cart is fine
settings: fine as well
about: a coffee mug

3- under the walking animation, should be the information of who I have passed by last time, when and with a head of the user and the name of it, so i can keep a record of who i have seem, as well as if i click on it, i will see the users profile on the dashboard of badges and so on, create that area so i can see my friends. It will also be able to see my friends from my profile area.

4- i have two devices connected to the pc and both with bluetooth enabled but they did not encounter each other, please make sure that bluetooth is enabled at beggining and that we can manage it from the app, permissions requested if needed. Make also a Hardware Capability Check: - [ ] Add packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE) check. Add BluetoothAdapter.isMultipleAdvertisementSupported() check (Critical for AYN/Retroid background syncing). Battery Optimization Bypass: Add a UI prompt to whitelist ThunderPass from Android's "Doze" mode. if the thunderpass is working, please make me see the information of the user on the passes area and on the start screen. if i pass by someone, create a notification on the app and on the android includding the information of the username (respect if the user is private). vibrate if possible and enabled by the user.

5- make the categories area smaller, square shapes to see all of them if on landscape mode, and large if on portrait mode, same for the badges if im on each category seeing my badges. From categories view, remove the best idea, i didnt like it at all

6- shop, make the dormant card on the left if on portrait mode with the explanation of how to collect points on the right. Please check if i have badges and add the points to it

7- settings, keep the app always open if the thunderpass is active, if there is a new version on the app, show it on the updates area, so teh app can download and apply it OTA, add an option to disable the background music of the app. the song is on the root folder of the app and it is called thunderpass-bg.mp3, play it by default when the app opens, except if the user disabled it, then dont play during the app opening.

8- on profile, remove this badges as they are now, show only my badges achieved on an a gallery that goes from left to right as squares and showing the ones ive achieved, if i have more than the space, make a gallery that goes beyond the right edge of the screen so user can slide through then make the more exotic ones first, the simple ones as last, remove the volt badge under the joules area, as it is a duplicated information, it doesnt help in anything, remove the legenda of the badges as well, as they will be the same as the ones ive achieved, the colors will be already presnted on it. instead of installation id, cant i share my userid based on the name of the user? except if private, add that information so i can hide my info if i pass by someone, it should show as private user once i pass by it.

9- if i go to my profile and change the visual of my user, respect the same visual on the screen animation and on the first screen. save that information on the user information area please (databse online?). if i click over my head avatar on the first screen, go to the profile area please.