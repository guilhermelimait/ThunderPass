package com.thunderpass.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * JVM unit tests for [RotatingIdUtils].
 * No Android runtime required — pure HMAC derivation only.
 */
class RotatingIdUtilsTest {

    private val installationId = "test-installation-id-1234"

    // ── Determinism ───────────────────────────────────────────────────────────

    @Test
    fun `same installationId and window index produce identical rotating ID`() {
        val id1 = RotatingIdUtils.deriveRotatingId(installationId, timeWindowIndex = 100L)
        val id2 = RotatingIdUtils.deriveRotatingId(installationId, timeWindowIndex = 100L)
        assertEquals(id1, id2)
    }

    // ── Uniqueness across windows ─────────────────────────────────────────────

    @Test
    fun `different window indices produce different rotating IDs`() {
        val id1 = RotatingIdUtils.deriveRotatingId(installationId, timeWindowIndex = 100L)
        val id2 = RotatingIdUtils.deriveRotatingId(installationId, timeWindowIndex = 101L)
        assertNotEquals(id1, id2)
    }

    // ── Privacy: different installation IDs ──────────────────────────────────

    @Test
    fun `different installationIds produce different rotating IDs in same window`() {
        val id1 = RotatingIdUtils.deriveRotatingId("device-A", timeWindowIndex = 50L)
        val id2 = RotatingIdUtils.deriveRotatingId("device-B", timeWindowIndex = 50L)
        assertNotEquals(id1, id2)
    }

    // ── Format checks ─────────────────────────────────────────────────────────

    @Test
    fun `rotating ID is non-empty`() {
        val id = RotatingIdUtils.deriveRotatingId(installationId, timeWindowIndex = 0L)
        assert(id.isNotEmpty()) { "Expected non-empty rotating ID" }
    }

    @Test
    fun `rotating ID does not contain Base64 padding`() {
        // URL-safe Base64 with NO_PADDING should never have '='
        val id = RotatingIdUtils.deriveRotatingId(installationId, timeWindowIndex = 42L)
        assert('=' !in id) { "Expected no padding character in '$id'" }
    }

    @Test
    fun `rotating ID does not contain whitespace or newlines`() {
        val id = RotatingIdUtils.deriveRotatingId(installationId, timeWindowIndex = 42L)
        assert(id.none { it.isWhitespace() }) { "Expected no whitespace in '$id'" }
    }

    // ── Window boundary test ──────────────────────────────────────────────────

    @Test
    fun `IDs within same window are identical, IDs in adjacent windows differ`() {
        val windowMs = BleConstants.ROTATING_ID_WINDOW_MS
        val baseTime = windowMs * 10L         // start of window 10
        val midTime  = baseTime + windowMs / 2 // still in window 10
        val nextTime = baseTime + windowMs     // start of window 11

        val idBase = RotatingIdUtils.deriveRotatingIdForTime(installationId, baseTime, windowMs)
        val idMid  = RotatingIdUtils.deriveRotatingIdForTime(installationId, midTime,  windowMs)
        val idNext = RotatingIdUtils.deriveRotatingIdForTime(installationId, nextTime, windowMs)

        assertEquals("Same window should produce same ID", idBase, idMid)
        assertNotEquals("Adjacent windows should produce different IDs", idBase, idNext)
    }
}
