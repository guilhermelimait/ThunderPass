package com.thunderpass.ble

import com.thunderpass.data.db.dao.EncounterDao
import com.thunderpass.data.db.entity.Encounter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * JVM unit tests for [EncounterDedup].
 *
 * Uses a simple in-memory fake [EncounterDao] — no Room, no Android runtime.
 */
class EncounterDedupTest {

    private lateinit var fakeDao: FakeEncounterDao
    private lateinit var dedup: EncounterDedup

    @Before
    fun setup() {
        fakeDao = FakeEncounterDao()
        dedup   = EncounterDedup(fakeDao)
    }

    // ── First encounter is always recorded ───────────────────────────────────

    @Test
    fun `first encounter with any rotating ID is recorded`() = runTest {
        val encounterId = dedup.onDeviceSeen("rot-id-001", rssi = -70)
        assertNotNull("First encounter should be inserted", encounterId)
    }

    // ── Cooldown suppresses duplicates ────────────────────────────────────────

    @Test
    fun `second encounter within cooldown is suppressed`() = runTest {
        val now = System.currentTimeMillis()
        dedup.onDeviceSeen("rot-id-002", rssi = -70, nowMs = now)

        val secondId = dedup.onDeviceSeen(
            "rot-id-002",
            rssi  = -68,
            nowMs = now + BleConstants.DEDUP_COOLDOWN_MS - 1L, // 1 ms before cooldown expires
        )
        assertNull("Encounter within cooldown should be suppressed", secondId)
    }

    @Test
    fun `second encounter after cooldown expires is recorded`() = runTest {
        val now = System.currentTimeMillis()
        val firstId = dedup.onDeviceSeen("rot-id-003", rssi = -70, nowMs = now)
        val secondId = dedup.onDeviceSeen(
            "rot-id-003",
            rssi  = -68,
            nowMs = now + BleConstants.DEDUP_COOLDOWN_MS, // exactly at cooldown boundary
        )

        assertNotNull("First encounter should be inserted", firstId)
        assertNotNull("Encounter after cooldown should be inserted", secondId)
    }

    // ── Different rotating IDs are independent ────────────────────────────────

    @Test
    fun `different rotating IDs do not interfere with each other`() = runTest {
        val now = System.currentTimeMillis()
        val id1 = dedup.onDeviceSeen("rot-id-A", rssi = -70, nowMs = now)
        val id2 = dedup.onDeviceSeen("rot-id-B", rssi = -72, nowMs = now)

        assertNotNull("rot-id-A encounter should be inserted", id1)
        assertNotNull("rot-id-B encounter should be inserted independently", id2)
    }

    // ── Inserted rows have correct fields ─────────────────────────────────────

    @Test
    fun `inserted encounter row has correct rotatingId and rssi`() = runTest {
        val now = 1_000_000L
        dedup.onDeviceSeen("rot-check", rssi = -55, nowMs = now)

        val row = fakeDao.rows.first { it.rotatingId == "rot-check" }
        assertEquals(-55, row.rssi)
        assertEquals(now, row.seenAt)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// In-memory fake EncounterDao — no Room or Android runtime required.
// ─────────────────────────────────────────────────────────────────────────────
class FakeEncounterDao : EncounterDao {

    val rows = mutableListOf<Encounter>()
    private var nextId = 1L

    override fun observeAll(): Flow<List<Encounter>> = MutableStateFlow(rows.toList())

    override suspend fun insert(encounter: Encounter): Long {
        val id = nextId++
        rows.add(encounter.copy(id = id))
        return id
    }

    override suspend fun linkSnapshot(encounterId: Long, snapshotId: Long) {
        val idx = rows.indexOfFirst { it.id == encounterId }
        if (idx >= 0) rows[idx] = rows[idx].copy(peerSnapshotId = snapshotId)
    }

    override suspend fun lastSeenAt(rotatingId: String): Long? =
        rows.filter { it.rotatingId == rotatingId }.maxOfOrNull { it.seenAt }

    override fun observeCount(): Flow<Int> = MutableStateFlow(rows.size)

    override suspend fun countAll(): Int = rows.size

    override suspend fun countSince(sinceMs: Long): Int = rows.count { it.seenAt >= sinceMs }

    override suspend fun setFriend(id: Long, isFriend: Boolean) {
        val idx = rows.indexOfFirst { it.id == id }
        if (idx >= 0) rows[idx] = rows[idx].copy(isFriend = isFriend)
    }

    override fun observeFriends(): Flow<List<Encounter>> =
        MutableStateFlow(rows.filter { it.isFriend })

    override suspend fun countFriends(): Int = rows.count { it.isFriend }
}
