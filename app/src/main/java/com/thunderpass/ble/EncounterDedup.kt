package com.thunderpass.ble

import android.util.Log
import com.thunderpass.ble.BleConstants.DEDUP_COOLDOWN_MS
import com.thunderpass.data.db.dao.EncounterDao
import com.thunderpass.data.db.entity.Encounter
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Decides whether a discovered rotating ID should trigger a new encounter.
 *
 * ### Rules (SPEC.md § Encounter Rules)
 * Record a new encounter when:
 *  - The rotating ID has **never been seen**, OR
 *  - The rotating ID was last seen **more than [DEDUP_COOLDOWN_MS] ago**.
 *
 * A new [Encounter] row is inserted immediately (before GATT exchange), so
 * that even a failed GATT attempt leaves a "seen" record.  The GATT layer
 * updates [Encounter.peerSnapshotId] on success.
 */
class EncounterDedup(private val encounterDao: EncounterDao) {

    private companion object {
        const val TAG = "EncounterDedup"
    }

    /** Prevents concurrent coroutines from racing past the check-then-insert. */
    private val mutex = Mutex()

    /**
     * Call this when the BLE scanner sees a ThunderPass advertisement.
     *
     * @param rotatingId  The peer's rotating ID from the scan result.
     * @param rssi        RSSI at discovery time.
     * @param nowMs       Current time in epoch millis (injectable for testing).
     * @return The newly inserted [Encounter.id] if the dedup rule allowed
     *         a new record, or `null` if the encounter was suppressed.
     */
    suspend fun onDeviceSeen(
        rotatingId: String,
        rssi: Int,
        nowMs: Long = System.currentTimeMillis(),
    ): Long? = mutex.withLock {
        val lastSeen = encounterDao.lastSeenAt(rotatingId)

        val shouldRecord = lastSeen == null || (nowMs - lastSeen) >= DEDUP_COOLDOWN_MS
        if (!shouldRecord) {
            val ageMs = nowMs - (lastSeen ?: 0L)
            val remainingMin = ((DEDUP_COOLDOWN_MS - ageMs) / 60_000L)
            Log.d(TAG, "Dedup BLOCKED rotId=${rotatingId.take(8)}… " +
                    "age=${ageMs / 1000}s, cooldown=${DEDUP_COOLDOWN_MS / 60_000}min, " +
                    "remaining≈${remainingMin}min")
            return@withLock null
        }

        val encounterId = encounterDao.insert(
            Encounter(
                rotatingId = rotatingId,
                seenAt = nowMs,
                rssi = rssi,
            )
        )
        encounterId
    }
}
