<p align="center">
  <img src="docs/logo.png" alt="ThunderPass Logo" width="180"/>
</p>

<h1 align="center">ThunderPass ⚡ALPHA VERSION </h1>

<p align="center">
  <em>StreetPass. For Android. Finally.</em>
</p>

<p align="center">
  <img alt="Android" src="https://img.shields.io/badge/Android-13%2B-brightgreen?logo=android"/>
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-Jetpack%20Compose-blueviolet?logo=kotlin"/>
  <img alt="Version" src="https://img.shields.io/badge/version-0.8.0-yellow"/>
  <img alt="License" src="https://img.shields.io/github/license/guilhermelimait/ThunderPass"/>
  <a href="https://discord.gg/jVxQnp8Fy"><img alt="Discord" src="https://img.shields.io/badge/Discord-Join%20us-5865F2?logo=discord&logoColor=white"/></a>
</p>

---

<img src="docs/screenshots.png" alt="ThunderPass" width="720"/>

Remember StreetPass? You would carry your 3DS in your bag to a coffee shop, a convention, a train station -- and later pull it out to find it had quietly swapped little profile cards with strangers while it sat in your pocket. That tiny blue light was one of the most quietly brilliant things Nintendo ever shipped. Then it disappeared.

ThunderPass brings it back. Walk around with your Android device in your pocket. Whenever another ThunderPasser is nearby, your phones silently find each other over Bluetooth, shake hands with end-to-end encryption, swap profile cards, and go back to sleep. No tapping. No accounts. No internet. Just the same low-key magic -- rebuilt from scratch with modern cryptography and a healthy respect for your privacy.

This version fixes all issues that were shared during the super initial phases of the open alpha and discussed from reddit, discord or in private chat. From now on I will focus on usability, badges, character customization, UX and UI. Please stay tuned!

---

## Screenshots - Alpha (Proof of concept)

<table>
  <tr>
    <td align="center"><img src="docs/1%20Start%20Screen.jpg" alt="Start Screen" width="100%"/><br/><b>Start Screen</b></td>
    <td align="center"><img src="docs/2%20My%20Profile.jpg" alt="My Profile" width="100%"/><br/><b>My Profile</b></td>
  </tr>
  <tr>
    <td align="center"><img src="docs/3%20My%20Sparky.jpg" alt="My Sparky" width="100%"/><br/><b>My Sparky</b></td>
    <td align="center"><img src="docs/4%20Friend%20Exchanged%20with%20Privacy%20Mode.jpg" alt="Friend Exchanged with Privacy Mode" width="100%"/><br/><b>Friend Exchanged with Privacy Mode</b></td>
  </tr>
</table>

---

## Was this app AI Developed?

Yes. And I will never pretend otherwise.

I work full-time. ThunderPass gets whatever hours are left after that — nights, weekends, the occasional lunch break. I do not have the luxury of spending three months polishing a single feature from scratch. So I use AI to write code faster. That is the honest truth.

But here is what AI did not do: AI did not decide that this app should exist. I've tailored each part of it to bring StreetPass back for Android. The name, the yellow lightning bolt, make the LED flash yellow on encounter, or decide that privacy mode should still show your name and avatar to strangers but hide the parts that could be used to track you, every encription method to keep your safety, and of course 100 steps should equal 1 Volt as a nod to the 3DS Play Coin system.

Every one of those choices was mine.

Every screen, every button, every permission, every colour, every behaviour — I thought about it, argued with myself about it, tested it on my own devices, and made a call. The AI wrote the code that implemented those calls. I reviewed every line, caught the bugs, pushed back when something felt wrong, and put my name on the result.

This is not "AI made an app and I uploaded it." This is me building something I genuinely wanted to exist, using every tool available to me — including AI — because time is finite and I refused to let that stop me.

If that bothers you, I understand. If it does not, welcome. I hope you enjoy it as much as I enjoyed making it.

---

## Now let's talk about what really matters, what happens when you Spark?

Two phones cross paths. Here is what happens in the background, in order:

1. Both devices notice each other over Bluetooth -- quietly, with no interaction from you.
2. Each phone generates a one-time secret key just for this exchange. 
3. Using that key, they swap profile cards -- your name, avatar, origin, greeting, and stats -- locked inside an encryption layer that only the other device can open.
4. Both sides confirm the other is who they claim to be, so nobody can pretend to be someone else or replay an old exchange.
5. You feel a double-pulse vibration, if your device has leds, it will flash them!
6. The encounter lands in your Passes list.
7. You earn 100 Volts.

That is the whole thing. It takes about two seconds. All of this in transparent mode, you can play, walk around and will never be interrupted, but of course you will know as a notification on your android and if you have leds on your device, it will spark!!!

> Every single byte that crosses the air between two devices is encrypted. There is no mode, no fallback, no legacy path where data is sent in the clear. If the encryption fails, the exchange fails -- nothing gets through.

---

## No cloud. No accounts. No tracking.

There is no backend. No servers. No sign-in screen. No analytics SDK quietly phoning home. Everything ThunderPass knows about you lives on your device, encrypted. The only data that ever leaves your phone is the profile card you deliberately chose to share -- and it goes directly to the other person's device over an encrypted Bluetooth channel.

Your device, your data, full stop.

---

## Features

### Sparks and Encounters

ThunderPass runs as a foreground service that restarts itself after a reboot, so you never miss a Spark. Your real device ID is never broadcast -- the advertisement carries only a service UUID and a rotating anonymous ID that changes every 60 minutes, making passive tracking a dead end. The same person can only Spark you once per day -- their card gets updated if you cross paths again, but you will not see duplicates stacking up or earning Volts with the same person.

When a new encounter happens you get an instant push notification. The full encounter card -- timestamps, their profile, signal strength -- is saved in your history.

### Your Sparky (Mii like)

Your profile is what the other person sees when you Spark. Give yourself a name, write a greeting (up to 60 characters), and build your avatar in the Sparky Editor using per-feature sliders. Set your Origin to your home country or, if you want, claim a planet -- Mercury through Pluto are all valid options.

**Privacy mode** is a single toggle. When it is on, strangers still see your name, avatar, greeting, origin, and stats -- your card is still meaningful -- but nothing that could be used to track you across encounters is included (no account ID, no device fingerprint, no RetroAchievements username). Your paired devices still sync everything normally. Only strangers get the redacted view.

Connect your [RetroAchievements](https://retroachievements.org) username to show your gaming stats on your Spark card. The API key field is encripted by default -- tap the eye icon to reveal it.

### RetroAchievements

Connect your [RetroAchievements](https://retroachievements.org) username to see a peer's rank, points, and recently mastered games right on their encounter card. RA-linked accounts also unlock a handful of exclusive badges you cannot get any other way.

### Badges (in development)

Badges are earned by actually using the app -- Sparking people, walking, hitting streaks, connecting RetroAchievements, and a few other things. There is no badge shop. You earn them or you do not.

### Volts 

Every unique Spark awards 100 Volts. Walking earns them too: the hardware step counter in your device (the same low-power coprocessor your health app uses, no GPS) credits 1 Volt per 100 steps, up to 100 Volts a day. It is a direct nod to the Nintendo 3DS Play Coin system. Walk to the bus stop and back -- it counts.

**Auto-Walk** handles the fine details. If you have been still for 10 minutes, scanning pauses. The moment a step is detected, it resumes. Combined with the three scan modes -- Battery Saver, Balanced, and Always On -- you control exactly how hard the radio works.

### Home Screen Widgets

Two widgets are available in the widget picker:

- **Toggle widget (2x1)** -- your avatar, name, scanning status, and Volt count. Tap to start or stop the service.
- **Stats widget (2x2)** -- same profile card on top, with a stats row below showing total Passes, earned Badges, and your current day Streak. Also tap-to-toggle.

---

## Device Sync, yes ThunderPass can support more than 2 devices!

If you carry more than one device -- a phone for daily life, a Retroid Pocket for weekends, an AYN Thor for travel -- ThunderPass keeps them all in sync. Pairing is manual and deliberate: a one-time short authentication code confirms it is actually you on both ends. After that, when both devices are in Bluetooth range, they sync your profile, Volt balance, badges, and Spark history every 15 minutes automatically.

Your raw scan data, Device Group Key, and RA API key never leave the device they belong to. They are explicitly excluded from background sync.

**What syncs when devices are in range:**
- Your profile -- name, avatar, greeting, origin, privacy settings
- Volt balance and full badge collection
- Spark history and encounter cards

**What stays local-only, always:**
- Raw Bluetooth scan data
- Your Device Group Key
- Your RA Web API key (synced only during the initial explicit pairing, never during background sync)

**Security model:**

| Layer | Mechanism |
|---|---|
| Key agreement | ECDH P-256, fresh ephemeral keys per session |
| Payload encryption | AES-256-GCM, unique nonce per message |
| Integrity | HMAC-SHA256 on every payload |
| Authentication | ECDSA P-256 signatures, Android Keystore-bound |
| Pairing code | Single-use, 5-minute TTL |
| SAS verification | Short Authentication String from key material |
| Local database | SQLCipher AES-256, passphrase in EncryptedSharedPreferences |
| BLE advertisement | Service UUID only -- no device name, rotating anonymous ID |
| Perfect forward secrecy | Compromising one session reveals nothing about any other |

> If you ever want a clean profile, go to Settings > Advanced > Reset All Data. It wipes everything -- profile, encounters, badges, Volts, paired devices, all settings. There is no undo.

---

## Getting Started

### Stay updated with Obtainium

[Obtainium](https://github.com/ImranR98/Obtainium) tracks GitHub releases and notifies you when a new version drops -- no Play Store required.

[![Add to Obtainium](https://img.shields.io/badge/Add%20to-Obtainium-blue?logo=github)](obtainium://app/https://github.com/guilhermelimait/ThunderPass)

Or open Obtainium, tap **Add App**, and paste:
```
https://github.com/guilhermelimait/ThunderPass
```

### Just grab the APK

Download the latest from the [GitHub Releases](https://github.com/guilhermelimait/ThunderPass/releases) page and sideload it. Any Android 13+ device with Bluetooth LE will work.

---

## Permissions

| Permission | Why it is needed |
|---|---|
| `BLUETOOTH_SCAN` | Find nearby ThunderPassers |
| `BLUETOOTH_ADVERTISE` | Let others find you |
| `BLUETOOTH_CONNECT` | Exchange profile cards over GATT |
| `POST_NOTIFICATIONS` | Tell you when a Spark happens |
| `VIBRATE` | The double-pulse on encounter |
| `ACTIVITY_RECOGNITION` | Read the hardware step counter |
| `RECEIVE_BOOT_COMPLETED` | Restart the service after a reboot |

No location permission. No camera. No contacts. No microphone.

---

## Devices

Tested on the AYN Thor, Retroid Pocket 4 Pro, and a handful of stock Android phones. If it has Bluetooth LE and runs Android 13 or later, it will work. AYN Thor owners get a bonus: the joystick LEDs flash yellow on a successful Spark (requires one ADB command to grant the permission -- details in Settings).

---

## Documentation

Internal references for developers and security reviewers live in `.internal/` -- these are gitignored and not part of public releases.

| Document | What is in it |
|---|---|
| `SECURITY_AUDIT.md` | Threat model, OWASP matrix, findings |
| `ARCHITECTURE.md` | Project structure, data flow, design decisions |
| `BLE_PROTOCOL.md` | Frame formats, handshake sequences, chunking |
| `CRYPTOGRAPHY.md` | Key derivation chains, algorithm choices |
| `DATA_MODEL.md` | Room schema, DAOs, SQLCipher config |
| `COMPONENTS.md` | Quick-reference for every source file |

---

## Roadmap

The roadmap is maintained privately. Updates and previews will be shared on discord only.

---

## Contributing

Bug reports, feature ideas, screenshots, pull requests -- all of it is welcome. The project is small enough that every contribution gets real attention.

1. Fork the repo.
2. Branch off: `git checkout -b feature/your-thing`
3. Open a pull request.

Found a bug? [Open an issue](https://github.com/guilhermelimait/ThunderPass/issues). I read every one.

---

Come hang out on **[Discord](https://discord.gg/jVxQnp8Fy)**. Share screenshots, report weirdness, or just say what device you are running it on. The community is small and I am active there.

---

## Thanks

To everyone who has tried ThunderPass, filed a bug, sent a screenshot, or just told a friend about it -- thank you. This exists because of that.

To the RetroAchievements community, for keeping retro gaming alive and accessible. To the AYN, Retroid, and Odin communities, for being exactly the audience this was made for. And to everyone who has bought a coffee -- it genuinely helps.
