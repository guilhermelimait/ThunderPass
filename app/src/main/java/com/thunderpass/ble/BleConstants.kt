package com.thunderpass.ble

import android.os.ParcelUuid
import java.util.UUID

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
     * Required to enable notifications on RESPONSE_CHAR.
     */
    val CCCD_UUID: UUID =
        UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    // ── Protocol constants ────────────────────────────────────────────────────

    /** Current protocol version written into every payload. */
    const val PROTOCOL_VERSION = 1

    /**
     * Rotating ID window length in milliseconds.
     * IDs rotate every 30 minutes per SPEC.md.
     */
    const val ROTATING_ID_WINDOW_MS = 30L * 60 * 1000

    /**
     * Encounter dedup cooldown in milliseconds.
     * A re-encounter with the same rotating ID is ignored for 10 minutes.
     */
    const val DEDUP_COOLDOWN_MS = 10L * 60 * 1000

    /** Maximum GATT MTU payload size (safe conservative value). */
    const val GATT_MAX_MTU = 512

    /** Notification channel ID for the foreground service. */
    const val NOTIF_CHANNEL_ID = "thunderpass_ble"

    /** Foreground service notification ID. */
    const val NOTIF_ID = 1001
}
