package com.thunderpass.ble

import android.content.Context
import android.util.Base64
import com.thunderpass.ble.BleConstants.ROTATING_ID_WINDOW_MS
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Manages the local installation ID and derives privacy-preserving
 * rotating IDs from it.
 *
 * ### Design (from SPEC.md)
 * - A stable **installation UUID** is generated once and stored in
 *   SharedPreferences (never broadcast directly).
 * - A **rotating ID** is derived as:
 *     `HMAC-SHA256(installationId, floor(currentTimeMs / windowMs))`
 *   truncated to 16 bytes and Base64url-encoded (no padding).
 * - The ID rotates every [ROTATING_ID_WINDOW_MS] (30 min default).
 * - Purpose: reduce passive long-term tracking.
 */
class RotatingIdManager(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("thunderpass_prefs", Context.MODE_PRIVATE)

    // ── Installation ID ───────────────────────────────────────────────────────

    /**
     * Stable installation UUID.  Generated once, never leaves the device
     * in plaintext.
     */
    val installationId: String by lazy {
        prefs.getString(KEY_INSTALLATION_ID, null) ?: run {
            val id = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_INSTALLATION_ID, id).apply()
            id
        }
    }

    // ── Rotating ID ───────────────────────────────────────────────────────────

    /**
     * Returns the rotating ID valid for the current 30-minute window.
     * This is what we put in the BLE advertising payload.
     */
    fun currentRotatingId(): String = rotatingIdForTime(System.currentTimeMillis())

    /**
     * Derives the rotating ID for an arbitrary point in time.
     * Useful for unit-testing or pre-computing the next window's ID.
     */
    fun rotatingIdForTime(timeMs: Long): String {
        val window = timeMs / ROTATING_ID_WINDOW_MS
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(installationId.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val digest = mac.doFinal(window.toString().toByteArray(Charsets.UTF_8))
        // Truncate to 16 bytes for a compact, hard-to-correlate ID
        val truncated = digest.copyOf(16)
        return Base64.encodeToString(truncated, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    companion object {
        private const val KEY_INSTALLATION_ID = "installation_id"
    }
}
