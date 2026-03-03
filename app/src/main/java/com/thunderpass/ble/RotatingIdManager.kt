package com.thunderpass.ble

import android.content.Context
import java.util.UUID

/**
 * Manages the local installation ID and derives privacy-preserving
 * rotating IDs from it.
 *
 * ### Design (from SPEC.md)
 * - A stable **installation UUID** is generated once and stored in
 *   SharedPreferences (never broadcast directly).
 * - A **rotating ID** is derived via [RotatingIdUtils.deriveRotatingId]:
 *     `HMAC-SHA256(installationId, floor(currentTimeMs / windowMs))`
 *   truncated to 16 bytes and Base64url-encoded (no padding).
 * - The ID rotates every [BleConstants.ROTATING_ID_WINDOW_MS] (30 min default).
 * - Purpose: reduce passive long-term tracking.
 */
class RotatingIdManager(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("thunderpass_prefs", Context.MODE_PRIVATE)

    // ── Installation ID ──────────────────────────────────────────────────

    /**
     * Stable installation UUID. Generated once, never leaves the device in
     * plaintext (only its HMAC-derived rotating IDs are broadcast).
     */
    val installationId: String by lazy {
        prefs.getString(KEY_INSTALLATION_ID, null) ?: run {
            val id = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_INSTALLATION_ID, id).apply()
            id
        }
    }

    // ── Rotating ID ────────────────────────────────────────────────────

    /**
     * Returns the rotating ID valid for the current 30-minute window.
     * This is what gets embedded in the BLE advertising service-data field.
     */
    fun currentRotatingId(): String =
        RotatingIdUtils.deriveRotatingIdForTime(installationId, System.currentTimeMillis())

    /**
     * Derives the rotating ID for an arbitrary point in time.
     * Useful for pre-computing the next window's ID.
     */
    fun rotatingIdForTime(timeMs: Long): String =
        RotatingIdUtils.deriveRotatingIdForTime(installationId, timeMs)

    companion object {
        private const val KEY_INSTALLATION_ID = "installation_id"
    }
}
