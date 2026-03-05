<p align="center">
  <img src="logo.png" alt="ThunderPass Logo" width="180"/>
</p>

<h1 align="center">ThunderPass ⚡</h1>

<p align="center">
  <em>The modern StreetPass experience — for Android.</em>
</p>

<p align="center">
  <a href="https://github.com/guilhermelimait/ThunderPass/actions"><img alt="CI" src="https://github.com/guilhermelimait/ThunderPass/actions/workflows/android.yml/badge.svg"/></a>
  <img alt="Android" src="https://img.shields.io/badge/Android-12%2B-brightgreen?logo=android"/>
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-Jetpack%20Compose-blueviolet?logo=kotlin"/>
  <img alt="License" src="https://img.shields.io/github/license/guilhermelimait/ThunderPass"/>
  <a href="https://ko-fi.com/guilhermelimait"><img alt="Ko-fi" src="https://img.shields.io/badge/Support-Ko--fi-FF5E5B?logo=kofi&logoColor=white"/></a>
  <a href="https://discord.gg/jVxQnp8Fy"><img alt="Discord" src="https://img.shields.io/badge/Discord-Join%20us-5865F2?logo=discord&logoColor=white"/></a>
</p>

---

> **Remember StreetPass?** You'd wander around with your Nintendo 3DS and later find it had silently swapped little profile cards with everyone you'd walked past. ✨
>
> **ThunderPass brings that magic to Android.** Walk around — whenever another ThunderPasser is nearby your phones quietly spark, swapping profile cards and building your collection. No accounts, no fuss, no internet required.

---

## Screenshots

> 📸 *Images coming soon — contributions welcome!*

| Home Screen | Passes | Badges |
|:-----------:|:------:|:------:|
| ![Home](docs/screenshots/home.png) | ![Passes](docs/screenshots/passes.png) | ![Badges](docs/screenshots/badges.png) |

| Profile | Settings | About |
|:-------:|:--------:|:-----:|
| ![Profile](docs/screenshots/profile.png) | ![Settings](docs/screenshots/settings.png) | ![About](docs/screenshots/about.png) |

*Drop your screenshots into `docs/screenshots/` and open a PR — we'd love to show the app in action!*

---

## What happens when you Spark? ⚡

When two ThunderPassers cross paths:

1. Your phones notice each other over Bluetooth — completely in the background.
2. They silently swap **profile cards** — your name, avatar, and personal greeting.
3. A double-pulse vibration says *The Spark happened*.
4. The encounter is saved in your **Passes** list.
5. You earn **100 Volts** — the energy currency of ThunderPass.

That's it. No tapping required. Just live your life and let ThunderPass do its thing.

---

## Features

### ⚡ Sparks & Encounters
- Automatic **Bluetooth BLE** detection — runs quietly in the background as a foreground service.
- **Privacy first**: your real ID is never broadcast. A rotating anonymous ID changes every 60 minutes.
- Smart dedup — the same person won't flood your list (24-hour cooldown per identity, profile card always updated with the latest data).
- Instant **push notification** the moment a new ThunderPasser is detected nearby.
- Full **encounter history** with timestamps, signal strength, and the peer's profile card.

### 🧑‍🎨 Your Profile (Sparky)
- Pick a display name, write a personal greeting (up to 60 characters), and customize your **Sparky** avatar with per-feature sliders.
- Your profile is what others see when you Spark — make it yours.
- **Privacy mode**: flip the toggle and nearby devices see only *"Private User"* — no name, avatar, or greeting shared.
- Connect your **RetroAchievements** username to show your gaming stats on your Spark card.

### 🏆 Badges
Badges are earned by living your ThunderPass life — sparking new people, playing retro games, hitting streaks, and more. Five tiers:

| Tier | Colour | Vibe |
|---|---|---|
| Not Achieved | Dark grey | Keep going! |
| Common | Blue | Nice start ⚡ |
| Uncommon | Purple | Getting rare! |
| Rare | Orange | Wow, impressive |
| Legendary | Gold | You're a legend 🌩️ |

Each badge features a **thunder bolt** — because of course it does.

### 🔋 Volts — Energy System
Every unique Spark earns you **100 Volts**. Spend Volts in the Shop to unlock profile effects:
- **CRT Scanlines** — retro TV vibes on your profile card.
- **Pixelated Aura** — 8-bit glow around your avatar.
- **Thunder Trail** — leave a lightning trail wherever you go.

### 🕹️ RetroAchievements Integration
Connect your [RetroAchievements](https://retroachievements.org) username and unlock extra sparks:
- See a peer's **rank, points, and recently mastered games** on their encounter card.
- Earn special badges for legendary encounters.

### ⚙️ Settings
- **Scanning Mode**: choose Battery Saver, Balanced, or Always On — tailored BLE scan intensity to match your lifestyle and battery.
- **Vibration**: enable or disable haptic feedback on encounters.
- **LED Flash**: three yellow blinks on supported devices when a new Spark is detected (configurable in Permissions).
- **Safe Zones**: pause scanning automatically when you're home.
- **Privacy Mode**, Background Music, Keep-Screen-On, and OTA update checks.

### ☁️ Anonymous Cloud Identity
ThunderPass signs you in **silently and anonymously** on first launch — no email, no password, no account creation. This creates a cloud identity used only for encounter verification and deduplication. Your profile (name, avatar, greeting, Volts) is backed up and restored automatically.

> **What if I delete the app?** Your anonymous identity is tied to this install. Your cloud profile stays on the server, but it cannot be auto-linked after reinstall. Signing in with a named account (email OTP) lets you recover everything. Cloud backup of Spark history is planned for a future version.

---

## Getting Started

### Download

Grab the latest debug APK from the [GitHub Releases](https://github.com/guilhermelimait/ThunderPass/releases) page and sideload it onto your Android 12+ device.

### Build from source

1. **Clone** the repo and open it in **Android Studio Hedgehog** or later.
2. **Build & run** on any Android 12+ device with Bluetooth LE support (`Run › Run 'app'`).
3. Set up your profile, hit the **ThunderPass toggle**, and go outside! 🚶

> **Tip:** ThunderPass works best running in the background. Grant all permissions it asks for at first launch and add it to your battery optimisation whitelist when prompted.

---

## Permissions Used

ThunderPass asks only for what it needs:

| Permission | Why |
|---|---|
| `BLUETOOTH_SCAN` | Find nearby ThunderPassers |
| `BLUETOOTH_ADVERTISE` | Let others find you |
| `BLUETOOTH_CONNECT` | Exchange profile cards |
| `POST_NOTIFICATIONS` | Alert you when someone Sparks nearby |
| `VIBRATE` | Double-pulse feedback on encounters |

---

## Supported Devices

ThunderPass runs on any **Android 12+** phone or handheld with Bluetooth LE. It's been tested and designed with handheld gaming devices in mind:

- **AYN Thor** series
- **Retroid Pocket** series
- **Odin** series
- Any Android phone 📱

---

## Roadmap

See [`ROADMAP.md`](ROADMAP.md) for the full plan. Highlights coming next:

- 🎨 Full Profile screen overhaul — two-column layout, RetroAchievements gallery, auto-save
- 🏷️ Badge updates — Alfa Tester, Beta Tester, Node Zero badges
- 🌍 Power Surge Events — location-based 2× Volt multipliers
- 🔌 Public payload SDK — let other apps plug into ThunderPass

---

## Contributing

Contributions are very welcome! Whether it's a bug report, a feature suggestion, a screenshot for the docs, or a pull request:

1. **Fork** the repo.
2. Create a branch: `git checkout -b feature/your-feature`.
3. Commit your changes and open a **Pull Request**.

Found a bug? [Open an issue](https://github.com/guilhermelimait/ThunderPass/issues) — all feedback is welcome and read by the developer personally.

---

## Support the project ☕

ThunderPass is a labour of love, built entirely by one person in their spare time. If it puts a smile on your face, a coffee goes a long way:

<p>
  <a href="https://ko-fi.com/guilhermelimait" target="_blank">
    <img src="https://ko-fi.com/img/githubbutton_sm.svg" alt="Support on Ko-fi"/>
  </a>
</p>

Come hang out on **[Discord](https://discord.gg/jVxQnp8Fy)** — share screenshots, report issues, or just say hi. The community is small but friendly. ⚡

---

## Thanks

A huge thank you to everyone who has tried ThunderPass, reported bugs, sent feedback, and kept the spirit of StreetPass alive. You're the reason this exists.

Special thanks to:
- The **RetroAchievements** community for keeping retro gaming alive.
- The **AYN**, **Retroid**, and **Odin** communities for being the perfect audience for a project like this.
- Everyone who buys a coffee — it genuinely makes a difference.
- **You**, for reading this far. Go Spark someone! ⚡

---

## License

[MIT](LICENSE) — free to use, modify, and distribute. Attribution appreciated but not required.


<h1 align="center">ThunderPass ⚡</h1>

<p align="center">
  <em>The modern StreetPass experience — for Android.</em>
</p>

<p align="center">
  <a href="https://github.com/guilhermelimait/ThunderPass/actions"><img alt="CI" src="https://github.com/guilhermelimait/ThunderPass/actions/workflows/android.yml/badge.svg"/></a>
  <img alt="Platform" src="https://img.shields.io/badge/platform-Android-green"/>
  <img alt="License" src="https://img.shields.io/github/license/guilhermelimait/ThunderPass"/>
</p>

---

Remember **StreetPass**? You'd wander around with your Nintendo 3DS in your pocket, and the next time you checked, it had silently swapped little cards with everyone you walked past. ✨

**ThunderPass** brings that magic to Android. Walk around with your phone, and whenever another ThunderPasser is nearby, the app quietly sparks — swapping profile cards, racking up energy, and building your collection — no accounts, no fuss, no internet required.

---

## What happens when you spark? ⚡

When two ThunderPassers cross paths:

1. Your phones notice each other over Bluetooth (completely in the background).
2. They silently swap **profile cards** — your name, avatar, and greeting.
3. A double-pulse vibration tells you *The Spark happened*.
4. The encounter is saved in your **Passes** list.
5. You earn **Volts** — the energy currency of ThunderPass.

That's it. No tapping required. Just live your life and let ThunderPass do its thing.

---

## Features

### ⚡ Sparks & Encounters
- Automatic **Bluetooth BLE** detection — runs quietly in the background as a foreground service.
- **Privacy first**: your real ID is never broadcast. A rotating anonymous ID changes every 30 minutes.
- Smart dedup — the same person won't flood your list (10-minute cooldown per device).
- Instant **push notification** the moment a new ThunderPasser is detected nearby.
- Full **encounter history** with timestamps, RSSI signal strength, and the peer's profile card.

### 🧑‍🎨 Your Profile
- Pick a display name, write a greeting, and choose (or randomize!) your avatar.
- Your profile is what others see when you spark — make it yours.
- **Privacy mode**: flip the toggle in Settings and nearby devices see only *"Private User"* — no name, avatar, or greeting is shared.

### 🏆 Badges
Badges are earned by living your ThunderPass life — sparking new people, playing retro games, hitting streaks, and more. They come in five tiers:

| Tier | Color | Vibe |
|---|---|---|
| Not Achieved | Dark grey | Keep going! |
| Common | Blue | Nice start ⚡ |
| Uncommon | Purple | Getting rare! |
| Rare | Orange | Wow, impressive |
| Legendary | Gold | You're a legend 🌩️ |

Each badge shows a **thunder bolt** instead of a star — because of course it does.

### 🔋 Volts — Energy System
Every unique Spark earns you **100 Volts**. Your balance is recalculated automatically on app launch so it's always accurate. Spend Volts in the Shop to unlock cool profile effects:
- **CRT Scanlines** — retro TV vibes on your profile card.
- **Pixelated Aura** — 8-bit glow around your avatar.
- **Thunder Trail** — leave a lightning trail wherever you go.

### 🕹️ RetroAchievements Integration
Connect your [RetroAchievements](https://retroachievements.org) account and unlock extra sparks:
- See a peer's **rank, points, and recently mastered games** right on their encounter card.
- Earn special badges for legendary encounters:
  - **Platinum Pulse** — peer has 20,000+ total points.
  - **Legendary Encounter** — you both recently played the same game.
  - **Retro Circuit** — peer mastered a game in the last 30 days.

### 🛍️ Shop
Spend your hard-earned Volts on profile effects and cosmetics. The more you spark, the more you unlock.

### ⚙️ Settings
- **Privacy Mode** — hide your identity from BLE peers in one tap.
- **OTA Updates** — the app polls GitHub Releases on launch and shows an update banner if a newer version is available.
- Background music, keep-screen-on, safe zones, scan intensity, and permission shortcuts.

### 📱 Widget
A handy home screen widget shows your **total encounters** and how many happened **today** — at a glance, without opening the app.

### ☁️ Optional Cloud Sync
Sign in with just your email (no passwords — a 6-digit code is sent to you) to:
- Back up your profile to the cloud.
- Look up other ThunderPassers on the [web dashboard](https://guilhermelimait.github.io/ThunderPass/).

---

## Getting Started

1. **Clone** the repo and open it in **Android Studio**.
2. **Build & run** on any Android 12+ device with Bluetooth LE support.
3. Set up your profile, hit the **ThunderPass toggle**, and go outside! 🚶

> **Tip:** ThunderPass works best when you keep it running in the background. Grant it the permissions it asks for, and let it do the magic.

---

## Permissions Used

ThunderPass asks only for what it needs:

| Permission | Why |
|---|---|
| `BLUETOOTH_SCAN` | Find nearby ThunderPassers |
| `BLUETOOTH_ADVERTISE` | Let others find you |
| `BLUETOOTH_CONNECT` | Exchange profile cards |
| `POST_NOTIFICATIONS` | Alert you when someone sparks nearby |

---

## Roadmap

See [`ROADMAP.md`](ROADMAP.md) for the full plan. Big things coming in future milestones:

- 🌍 Location-based Power Surge Events (2× Joule multiplier)
- 🔌 Public payload SDK — let other apps plug into ThunderPass
- 🔒 Remote privacy controls and encounter backup

---

## Support the project ☕

ThunderPass is made with love by [@guilhermelimait](https://github.com/guilhermelimait).
If you enjoy it, consider buying a coffee:

<a href="https://ko-fi.com/guilhermelimait" target="_blank"><img src="https://ko-fi.com/img/githubbutton_sm.svg" alt="Support on Ko-fi"/></a>

Found a bug or have an idea? [Open an issue on GitHub](https://github.com/guilhermelimait/ThunderPass/issues) — all feedback is welcome!

---

## License

[MIT](LICENSE)