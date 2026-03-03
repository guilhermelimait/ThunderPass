# ThunderPass SPEC (MVP → v1)

## Goals
- Recreate a **StreetPass-like** experience on **Android 13** (AYN Thor first).
- **Offline-first**: no account required to function.
- Later: optional **accounts + cloud sync**, deployed/managed via GitHub workflows (“infra-as-code”).

## Non-Goals (MVP)
- No always-on background scanning without a foreground service (Android 13 limits).
- No cloud dependency.
- No “plugins for other apps” yet.

---

## System Roles
Each device can act as:
- **Broadcaster (Advertiser):** periodically advertises a small “presence” payload.
- **Discoverer (Scanner):** scans for presence payloads from nearby devices.
- **Exchanger (GATT client/server):** performs a short connection to exchange a larger payload once a nearby device is found.

In practice on Android, we will likely run **scan + advertise concurrently** while the feature is enabled.

---

## Android 13 Permissions / Requirements
### Runtime permissions (Android 12+)
- `android.permission.BLUETOOTH_SCAN`
- `android.permission.BLUETOOTH_ADVERTISE`
- `android.permission.BLUETOOTH_CONNECT`

### Foreground service
For reliable operation, use a Foreground Service with a persistent notification.

> Note: Android’s BLE scanning may still be affected by OEM power management; AYN Thor is the primary target initially.

---

## Discovery Layer (BLE Advertising)
### Advertising payload (MVP)
We need a small payload that fits advertising constraints. Use:
- **Service UUID**: identifies ThunderPass presence.
- **Service Data**: small binary structure.

#### Presence packet (binary, little-endian)
- `version` (1 byte) — protocol version
- `flags` (1 byte) — capabilities bits (supports_gatt_exchange, etc.)
- `rotating_id` (8–16 bytes) — privacy-preserving ID that changes periodically
- `profile_hint_hash` (optional, 4 bytes) — quick hash to detect updates (optional)

### Rotating IDs (privacy)
- Device generates a stable **local installation ID** (never broadcast directly).
- Broadcast uses a **rotating ID** derived from local installation ID + time window.
- Rotation interval (MVP): **30 minutes**.
- Purpose: reduce passive tracking.

---

## Exchange Layer (GATT Handshake)
When a scanner sees a ThunderPass advertiser:
1. Apply **dedup rules** (don’t reconnect repeatedly).
2. Connect via GATT (short timeout).
3. Exchange “cards” and optional payload extensions.

### GATT service
- Primary Service UUID: `THUNDERPASS_SERVICE_UUID`
- Characteristics:
  - `REQUEST_CHAR` (Write): client writes request (what it wants)
  - `RESPONSE_CHAR` (Notify/Read): server responds with payload chunk(s)

### Payload format (logical, over GATT)
Use CBOR or JSON (MVP can be JSON for speed; CBOR later for size).
Envelope:
```json
{
  "v": 1,
  "type": "profile",
  "rotatingId": "base64...",
  "ts": 1710000000,
  "data": {
    "displayName": "Gui",
    "greeting": "Hey!",
    "avatar": { "kind": "defaultBolt", "color": "#FFD400" }
  }
}
```

### Size + chunking
- Keep the “profile” small (target < 4–8KB).
- If larger, implement chunking (MVP: simple single-packet, fail otherwise).

---

## Encounter Rules (Dedup)
Store an encounter when:
- A new rotating ID is seen, OR
- Same rotating ID seen after cooldown.

Cooldown (MVP): **10 minutes** per rotating ID.

Store:
- timestamp
- RSSI
- received profile snapshot

---

## Local Storage (Room) – Conceptual Schema
Tables:
- `my_profile`
- `encounter`
- `peer_profile_snapshot`

Key points:
- Encounters are append-only.
- Peer profile snapshots are stored as “what you saw at that time”.

---

## Security Plan (Future)
MVP is “best effort privacy” without accounts:
- Rotating IDs to reduce tracking.

Future improvements:
- Signed payloads (integrity)
- Optional encryption of exchanged payload once accounts exist
- Blocklist / ignore list
- Safe zones (auto-disable by Wi‑Fi SSID or geofence)

---

## Cloud Sync (Later, GitHub-managed)
- Offline-first remains primary.
- When user opts in and signs in:
  - sync `my_profile`
  - optionally sync encounter history
- Keep remote sync behind an interface so backend can be swapped.