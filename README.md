<p align="center">
  <img src="logo.png" alt="ThunderPass Logo" width="180"/>
</p>

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
5. You earn **Joules** — the energy currency of ThunderPass.

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

### 🔋 Joules — Energy System
Every unique Spark earns you **100 Joules**. Your balance is recalculated automatically on app launch so it's always accurate. Spend Joules in the Shop to unlock cool profile effects:
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
Spend your hard-earned Joules on profile effects and cosmetics. The more you spark, the more you unlock.

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