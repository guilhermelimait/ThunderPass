package com.thunderpass.ble

import android.content.Context
import android.provider.Settings
import java.security.MessageDigest
import java.util.UUID

/**
 * Manages the local installation ID and derives privacy-preserving
 * rotating IDs from it.
 *
 * ### Design (from SPEC.md)
 * - A stable **installation UUID** is generated once and stored in
 *   SharedPreferences (never broadcast directly).
 * - For new installs the UUID is derived from ANDROID_ID (SHA-256, UUID v5
 *   format) so it is deterministically tied to the physical Android device
 *   and survives app reinstalls. Existing installs keep their stored UUID.
 * - A **rotating ID** is derived via [RotatingIdUtils.deriveRotatingId]:
 *     `HMAC-SHA256(installationId, floor(currentTimeMs / windowMs))`
 *   truncated to 16 bytes and Base64url-encoded (no padding).
 * - The ID rotates every [BleConstants.ROTATING_ID_WINDOW_MS] (30 min default).
 * - Purpose: reduce passive long-term tracking.
 */
class RotatingIdManager(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext
        .getSharedPreferences("thunderpass_prefs", Context.MODE_PRIVATE)

    // ── Installation ID ──────────────────────────────────────────────────

    /**
     * Stable installation UUID. Generated once, never leaves the device in
     * plaintext (only its HMAC-derived rotating IDs are broadcast).
     *
     * For new installs this is deterministically derived from [Settings.Secure.ANDROID_ID]
     * (SHA-256, UUID v5 byte layout) so the same device always produces the
     * same ID even after a reinstall. Falls back to a random UUID on emulators
     * or devices that report a bogus ANDROID_ID.
     */
    val installationId: String by lazy {
        prefs.getString(KEY_INSTALLATION_ID, null) ?: run {
            val id = androidIdBasedUuid()
            prefs.edit().putString(KEY_INSTALLATION_ID, id).apply()
            id
        }
    }

    /**
     * Derives a UUID v5-style identifier from the device's [Settings.Secure.ANDROID_ID].
     * The raw ANDROID_ID is never stored or transmitted — only the SHA-256 digest
     * (formatted as a UUID) is used, preserving privacy while ensuring uniqueness.
     */
    private fun androidIdBasedUuid(): String {
        val androidId = Settings.Secure.getString(
            appContext.contentResolver,
            Settings.Secure.ANDROID_ID,
        )
        // Emulators and some ROMs report null, blank, or the infamous placeholder value.
        if (androidId.isNullOrBlank() || androidId == "9774d56d682e549c") {
            return UUID.randomUUID().toString()
        }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("thunderpass:$androidId".toByteArray(Charsets.UTF_8))
        // Apply UUID version 5 and RFC 4122 variant bits
        digest[6] = ((digest[6].toInt() and 0x0f) or 0x50).toByte()
        digest[8] = ((digest[8].toInt() and 0x3f) or 0x80).toByte()
        return buildString {
            for (i in 0..3)   append("%02x".format(digest[i])); append('-')
            for (i in 4..5)   append("%02x".format(digest[i])); append('-')
            for (i in 6..7)   append("%02x".format(digest[i])); append('-')
            for (i in 8..9)   append("%02x".format(digest[i])); append('-')
            for (i in 10..15) append("%02x".format(digest[i]))
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
