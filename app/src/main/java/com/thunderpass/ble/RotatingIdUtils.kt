package com.thunderpass.ble

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Pure, context-free functions for rotating ID derivation.
 * Separated from [RotatingIdManager] so they can be unit-tested on the JVM
 * without an Android runtime.
 *
 * Uses [java.util.Base64] (not android.util.Base64) so this file is
 * testable in pure-JVM unit tests without mocking.
 */
object RotatingIdUtils {

    /**
     * Derives a rotating ID from a stable [installationId] and a [timeWindowIndex].
     *
     * Algorithm (per SPEC.md § Rotating IDs):
     * ```
     * rotatingId = Base64Url(HMAC-SHA256(installationId, timeWindowIndex)[0..15])
     * ```
     * where `timeWindowIndex = floor(epochMs / windowMs)`.
     *
     * @param installationId  Stable per-device UUID string (never broadcast directly).
     * @param timeWindowIndex Zero-based window index (`epochMs / windowMs`).
     * @return 16-byte HMAC digest encoded as URL-safe Base64 (no padding, no newlines).
     */
    fun deriveRotatingId(installationId: String, timeWindowIndex: Long): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(installationId.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val digest = mac.doFinal(timeWindowIndex.toString().toByteArray(Charsets.UTF_8))
        val truncated = digest.copyOf(16)
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(truncated)
    }

    /**
     * Convenience overload that takes an epoch millisecond timestamp and window
     * duration, computing the window index internally.
     */
    fun deriveRotatingIdForTime(
        installationId: String,
        timeMs: Long,
        windowMs: Long = BleConstants.ROTATING_ID_WINDOW_MS,
    ): String = deriveRotatingId(installationId, timeMs / windowMs)
}
