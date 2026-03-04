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
- [x] Energy Engine: Volts counter (100 V per unique Spark)
- [x] Haptic Feedback: Double-pulse vibration on GATT exchange
- [x] Safe Zones manual toggle
- [x] Battery modes: aggressive, balanced, off
- [x] Better UX copy + onboarding

---

## Milestone 3 — Fun Layer + Game Sync
- [x] Ghost payload type (score/time)
- [x] Sticker book (basic)
- [x] Encounter stats (counts, streaks, Volts on HomeScreen)
- [x] RetroAchievements Integration (RetroProfile, GATT share, RetroAuthManager, RetroRepository)
- [x] RetroSparkCard, Mastery Icons row, reference achievements
- [x] Visual Shop: CRT Scanlines, Pixelated Aura, Thunder Trail

---

## Milestone 4 — Accounts + Cloud Sync (partial)
- [x] Supabase Email OTP sign-in
- [x] Auto-sync profile to Supabase profiles table (display name, greeting, avatar seed, ghost score, stickers)
- [x] Web dashboard (GitHub Pages)
- [x] Profile sync after Spark encounters
- [ ] Optional encounter backup/restore
- [ ] Remote blocklist / privacy controls
- [ ] GitHub-managed deployment automation

---

## Milestone 5 — UX Overhaul & Feature Completion (Next MVP)

### 5.1 Identity & Naming
- [x] Read Android account display name at startup; fall back to device model name (Thor, Odin, etc.) — replace hardcoded "Traveler" if user not informed on the profile page.
- [x] Replace Installation ID with a human-readable name-based share ID. Show as "Private User" when privacy toggle is on.
- [x] Profile fields trimmed to essentials: Display Name, RA Username, RA API Key. Saved locally + synced to Supabase; included in BLE ghost payload.
- - [x] once it the friend profile is open, we will be able to see last games played, their achievements, consoles most used, and everything that can be used to make the badges works

### 5.2 ThunderPass Toggle & Foreground Service
- [x] Replace Scan / Idle states with a single ON / OFF toggle. Foreground service persists state when app is closed.
- [x] Remove Safe Zones and Battery Mode from Home — move to Settings (5.10).

### 5.3 Navigation & Bottom Bar
- [x] Fixed bottom bar always visible across all screens (no teardown on navigate).
- [x] Seven tabs: Home · Passes · Profile · Badges · Shop · Settings · About
- [x] Tab icons: Home=walking figure, Passes=thunder bolt, Profile=live avatar head, Badges=thunder badge, Shop=cart, Settings=cog, About=coffee mug
- [x] Landscape: bar height ~48 dp, icons only (no labels).

### 5.4 Home Screen
- [x] Remove Today/Streak stat cards; move data to Profile.
- [x] "Last Passed By" strip below walking animation: peer avatar + name + relative time. Tap opens encounter detail.
- [x] Tapping the user avatar in the walking scene navigates to Profile.

### 5.5 Bluetooth Reliability & Permissions
- [x] Check PackageManager.FEATURE_BLUETOOTH_LE on start; show blocking error if unsupported.
- [x] Check BluetoothAdapter.isMultipleAdvertisementSupported(); warn if background sync is limited.
- [x] If BT is off when toggling ON, request enable via BluetoothAdapter.ACTION_REQUEST_ENABLE.
- [x] First-launch prompt: disable battery optimisation (Doze-mode whitelist).
- [x] Audit BLE concurrency; verify both test devices (RP4 Pro + AYN Thor) discover each other reliably.

### 5.6 Passes (Encounters)
- [x] On new encounter: status-bar notification "ThunderPass! [DisplayName] is nearby." Omit name if peer is private.
- [x] Double-pulse vibration if haptics are enabled.
- [x] Passes screen: live list with peer avatar, name, RSSI, relative time.
- [x] Friends list UI: accessible from Passes and Profile. Tap a friend to open their badge dashboard.
  - [x] Data layer: `Encounter.isFriend` flag, DAO methods (`setFriend`, `observeFriends`, `countFriends`), `HomeViewModel.friends` StateFlow + `toggleFriend()`.

### 5.7 Profile Screen
- [x] Avatar maker: DiceBear seed selector + Randomise button. Syncs to walking animation and bottom-bar icon immediately; saved to Supabase.
- [x] Privacy toggle: when on, BLE exchange shows "Private User" to peers. Toggle in Settings > General.
- [x] Badge gallery: horizontal scrollable row, exotic/legendary first, achieved only. Remove legend row and duplicate Volt Badge.
- [x] Share ID uses display name, not installation ID.
- [x] All edits saved to Supabase; Room is source-of-truth offline. (`avatarSeed` now synced.)

### 5.8 Badges
- [x] Replace star icon with a thunder bolt on every badge, make the thunder with a large edge on the same color as the background
- [x] Tier colours: Dark grey=Not achieved, Blue=Common (1-2), Purple=Uncommon (3-4), Orange=Rare (5-6), Gold=Legendary (7).
- [x] Category grid: small squares in landscape, large cards in portrait.
- [x] Remove "Best" highlight from categories overview.
- [x] Create badges/badges.csv — columns: id, category, name, description, how_to_achieve, tier, colour.
- [x] levels works this way: if level 1 (blue common) first line under the thunder is colored, rest is not, if level 2, first and secon lines below the thunder, if level 3 and 4, two lines under the thunder, if 5 and 6 , three lines, if 7 all lines colored. thunder should be bigger and over the lines showing a large besel.

### 5.9 Shop
- [x] Portrait layout: Volts balance card on the left, earn-points explanation on the right.
- [x] On boot: recalculate Volts from encounter count (100 V per unique Spark) and award any missing points.
- [x] Keep existing unlockable effects (CRT Scanlines, Pixelated Aura, Thunder Trail).
- [x] Volts explanation panel: what they are and how to earn them.

### 5.10 Settings
- [x] Move Safe Zones and Battery Mode here (under Advanced, collapsed by default).
- [x] Keep screen on (FLAG_KEEP_SCREEN_ON) while ThunderPass service is active.
- [x] OTA updates: poll GitHub Releases API on launch; show amber banner + Download button if newer version exists.
- [x] Background music toggle — default ON. Play assets/thunderpass-bg.mp3 on app open; skip if user disabled it.
- [x] Privacy Mode toggle — hides name, avatar, and greeting from BLE peers.

### 5.11 About Screen
- [x] Ko-fi link: https://ko-fi.com/guilhermelimait/ — styled as a support/donation button.
- [x] Developer avatar + short bio.
- [x] "Report an Issue" button — opens GitHub Issues URL in browser.
- [x] App version + build number.

### 5.12 Removals
- [x] Remove Sticker Book screen and all related code.
- [x] Remove Safe Zone toggle from Home.
- [x] Remove Battery Mode selector from Home.
- [x] Remove duplicate Volt Badge widget from Profile Volts area.
- [x] Remove badge legend row from Profile.

---

## Milestone 6 — Platform (future)
- [ ] Versioned protocol extensions
- [ ] Power Surge Events: Location-based 2x Energy multipliers
- [x] keep an unique user recognized if devices are nearby, to avoid creating volts to users that are together
= [x] change the name of joules to volts and replace it everywhere on the app, documentation and site
- [x] fix the issue to disable the music if the radio button is unmarked, and save it to the app loading phase.
- [x] app should not start on profile, should go directly to the app homepage always
- - [x] monitor local games played using the android information, so we can create cards about it like last played, or played total time if things are related to the gaming area and add that to a daily play time card on the profile screen. here is an example: https://cdn.dribbble.com/userupload/26934632/file/original-5a33b421dd96feb16154939e3f9827cc.png?resize=1504x1128&vertical=center
- [x] fix the part where we create a unique account using the supabase authentication method with google on the profile area
- [x] when the app loads the users head , show the color of the arms the same as the skin of the head, doesnt matter if it was when the app open or when the profile head was changed.
- - [x] on the frontscren, home of the app, show the logo before the hi username, same size as the first and second line on it, remove the hadn after it, make it bigger
- [x] if the app is on landscape mode, put the last passed by under the animation area
- [] if the user has inputed the inofrmatoin about his retroachievements, plese save it locally as well, and if i open the app again, it will always be there, use that information to collect username, last games played, badges and so on, and create a view of that in a gallery, one per each kind used on this app below the buttons on the left area of the home screen and on profile of the user.
- [x] if a user is recognized by the device and the device has leds, blink it on yellow collor three times.
- [x] add the app management on the settings area as well as any other that might be missing and is used by the app, all related to games of course
-[x] on my about area, please show my avatar from the kofi, not the one on the app
-[x] remove the heads avatars area on the profile, keep the random button but on place of the one on top right of the profile, if the user changes it, always save, even if the user doesnt go to the button to save on the bottom
-[ ] update the badges view, add the shield badge on it, and remove the edge large as it was created on the previous change, on the user profile there are some badges on it that were as it should be on this badge area format
-[ ] on users profile, show the badges that were achieved, and respect their colors
-[x] share id on profile, make it clear, respect the colors of the app if on darkmode or not, but add a button to share and user decide whatever it uses on the device to share, whatsapp, copy, email, whatever, is it possible to connect it as a friend if i share the link with a special code to recognize the user?
-[x] on badges, over encounters, remove the badge and the thunder and keep just the logo on it
-[x] respect dark and bright mode if the user uses a profile with RA or for daily play time graph area
-[x] ra api key is not being saved to the local profile, and if the installation is updated, it is losing the profile information, what to do if the profiel is exchanged, will the receiver user be able to search and see this receiver data about its games played, consoles used and so on? we need that to update the badges on the app.
-[ ]restore my personal phrase field from the profile area, it was a nice thing to share when the user passed by, also, add the device type of the user, what it is? an odin, a retroid pocket? an ayn? update it to the database as well so we can check and confirm if the user is not spamming points to nearby users so it doesnt count those points many times
-[ ] if i have my retroachievements information saved on my profile, show my last games, badges, consoles most used and meaninful information on the home screen over the buttons, show each on a different gallery, so it becomes visible
-[ ] create a widget on android to show the animation and a button to enable or disable the thunderpass
-[ ] as this is based on the streetpass idea from the nintendo 3ds, i would like to call it Sparky, from my profile area, can you create a button to edit Sparky and the user is direct to a new screen where they can define the face based on the head tool we are using? for each option of the head, show a slider with the option to see what it is, for example if there are 40 types of hair, add 40 size slider that every 1 of them is one of the slider hair types, do the same to all options for the head and background. Please when the user select it, save to the user profile and always use it everywhere on the app that uses the same head idea. During this edit head ares is open, if on landscape mode, divide the screen on two sides, the left one create a big card with the head on evidence, big enough to be confortable to select the options, and on the right a slider for each option of the head, users can go  up and down on this side, but not on the head area, save it at the end.
-[ ] on home screen, remove the thunerpass option to be part of the animation on portrait or landscape mode, put on top of the animation, respect the colors of the bright or dark mode applied.
-[ ]if privacy mode is enabled, show the users head on the recipient, but not share the name or retroachievement or device name when shared.