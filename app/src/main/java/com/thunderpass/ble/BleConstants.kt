package com.thunderpass.ble

import android.os.ParcelUuid
import java.util.UUID

/** BLE scan intensity mode. */
enum class ScanMode { OFF, BALANCED, AGGRESSIVE }

/**
 * All ThunderPass BLE/GATT UUIDs and protocol constants.
 * Keep UUIDs stable — changing them breaks interoperability with older builds.
 */
object BleConstants {

    // ── Service UUIDs ─────────────────────────────────────────────────────────

    /** Primary service UUID broadcast in the advertising packet. */
    val THUNDERPASS_SERVICE_UUID: UUID =
        UUID.fromString("12345678-1234-1234-1234-1234567890ab")

    val THUNDERPASS_SERVICE_PARCEL: ParcelUuid =
        ParcelUuid(THUNDERPASS_SERVICE_UUID)

    // ── GATT Characteristic UUIDs ─────────────────────────────────────────────

    /**
     * REQUEST characteristic (Write/Write-No-Response).
     * The GATT client writes a small request frame here to indicate it
     * wants a profile exchange.
     */
    val REQUEST_CHAR_UUID: UUID =
        UUID.fromString("12345678-1234-1234-1234-1234567890cd")

    /**
     * RESPONSE characteristic (Notify + Read).
     * The GATT server chunks the JSON payload and notifies the client.
     */
    val RESPONSE_CHAR_UUID: UUID =
        UUID.fromString("12345678-1234-1234-1234-1234567890ef")

    /**
     * Standard Client Characteristic Configuration Descriptor (CCCD).
     * Required to enable notifications on RESPONSE_CHAR and SYNC_NOTIFY_CHAR.
     */
    val CCCD_UUID: UUID =
        UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    // ── Device Sync Service UUIDs (separate from the main ThunderPass service) ─

    /**
     * Sync service UUID — only advertised while the owning device is in active export mode.
     * Uses a distinct base to guarantee no conflict with the main ThunderPass service.
     */
    val SYNC_SERVICE_UUID: UUID =
        UUID.fromString("12345678-1234-1234-1234-123456789001")

    val SYNC_SERVICE_PARCEL: android.os.ParcelUuid =
        android.os.ParcelUuid(SYNC_SERVICE_UUID)

    /**
     * Sync WRITE characteristic — the GATT client (secondary device) writes the
     * ephemeral key exchange frame here:
     * `[SYNC_REQUEST_MAGIC: 1 byte][client ephemeral P-256 pubkey: 91 bytes]` = 92 bytes.
     */
    val SYNC_WRITE_CHAR_UUID: UUID =
        UUID.fromString("12345678-1234-1234-1234-123456789002")

    /**
     * Sync NOTIFY characteristic — the GATT server (primary device) sends the
     * encrypted, chunked sync payload here. NOTIFY-only; no READ permission.
     */
    val SYNC_NOTIFY_CHAR_UUID: UUID =
        UUID.fromString("12345678-1234-1234-1234-123456789003")

    /** Magic byte that opens a sync WRITE frame from the secondary device. */
    const val SYNC_REQUEST_MAGIC: Byte = 0x53  // 'S'

    /** Magic byte that opens every encrypted sync RESPONSE chunk from the primary. */
    const val SYNC_RESPONSE_MAGIC: Byte = 0x52.toByte()  // 'R'

    /** Written by the client to confirm step 1 (pairing code match). */
    const val CONFIRM_STEP1_MAGIC: Byte = 0x43  // 'C'

    /** Written by the client to confirm step 2 (profile verification). */
    const val CONFIRM_STEP2_MAGIC: Byte = 0x44  // 'D'

    /** Sent by the server to the client when the sender has confirmed step 2 locally. */
    const val STEP2_ACK_MAGIC: Byte = 0x45  // 'E'

    /** How long (ms) the sync GATT server stays open awaiting a connection. */
    const val SYNC_ADVERTISE_TIMEOUT_MS = 5L * 60 * 1000  // 5 minutes

    // ── Protocol constants ────────────────────────────────────────────────────

    /** Current protocol version written into every payload. */
    const val PROTOCOL_VERSION = 1

    /**
     * Rotating ID window length in milliseconds.
     * IDs rotate every 60 minutes (1 hour).
     */
    const val ROTATING_ID_WINDOW_MS = 60L * 60 * 1000

    /**
     * Encounter dedup cooldown in milliseconds (scan-level, per rotating-ID / device.address).
     * Must be >= [ROTATING_ID_WINDOW_MS]: once the rotating ID changes the same physical device
     * looks new to the scanner, so we gate on the full rotation window to prevent the same pair
     * of devices from exchanging more than once per hour.
     */
    const val DEDUP_COOLDOWN_MS = ROTATING_ID_WINDOW_MS

    /**
     * Identity dedup window in milliseconds (post-GATT, per installationId).
     * A given device earns at most one new Spark per [USER_DEDUP_WINDOW_MS];
     * within this window the existing pass's timestamp is refreshed instead
     * of creating a duplicate row.
     * Default: 24 hours — one Spark per device per day maximum.
     */
    const val USER_DEDUP_WINDOW_MS = 24L * 60 * 60 * 1000

    /** Preferred MTU requested by the GATT client (Android max = 517 but 512 is safe). */
    const val PREFERRED_MTU = 512

    /** Default ATT MTU when no negotiation has happened. */
    const val DEFAULT_MTU = 23

    /**
     * Magic byte that marks a chunked notification.
     * 0xCA never appears as the first byte of a valid UTF-8/JSON payload (which starts with '{').
     * Chunk format: [CHUNK_MAGIC:1][totalChunks:1][chunkIndex:1][data…]
     */
    const val CHUNK_MAGIC: Byte = 0xCA.toByte()

    /** Maximum GATT MTU payload size (safe conservative value). */
    const val GATT_MAX_MTU = 512

    /** Notification channel ID for the foreground service. */
    const val NOTIF_CHANNEL_ID = "thunderpass_ble"

    /** Foreground service notification ID. */
    const val NOTIF_ID = 1001

    /** Notification channel ID for encounter alerts. v2 = added LED light settings. */
    const val ENCOUNTER_CHANNEL_ID = "thunderpass_encounter_v2"

    /** Notification channel ID for paired-device sync alerts. */
    const val PAIRED_SYNC_CHANNEL_ID = "thunderpass_paired_sync"

    /** Notification ID for paired-device nearby alert. */
    const val PAIRED_SYNC_NOTIF_ID = 1003
}
