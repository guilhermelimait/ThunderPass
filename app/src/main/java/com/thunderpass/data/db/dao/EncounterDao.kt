package com.thunderpass.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.thunderpass.data.db.entity.Encounter
import kotlinx.coroutines.flow.Flow

@Dao
interface EncounterDao {

    /** All encounters, newest first. */
    @Query("SELECT * FROM encounter ORDER BY seenAt DESC")
    fun observeAll(): Flow<List<Encounter>>

    /**
     * Confirmed encounters only — those where a GATT profile exchange succeeded
     * (peerSnapshotId IS NOT NULL). Phantoms/unknowns are excluded.
     */
    @Query("SELECT * FROM encounter WHERE peerSnapshotId IS NOT NULL ORDER BY seenAt DESC")
    fun observeConfirmed(): Flow<List<Encounter>>

    /** Count of confirmed encounters (GATT-verified), used for home screen badge. */
    @Query("SELECT COUNT(*) FROM encounter WHERE peerSnapshotId IS NOT NULL")
    fun observeConfirmedCount(): Flow<Int>

    /** Insert a raw encounter (before GATT exchange). Returns new row id. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(encounter: Encounter): Long

    /**
     * After a successful GATT exchange, link the peer snapshot to the encounter.
     */
    @Query("UPDATE encounter SET peerSnapshotId = :snapshotId WHERE id = :encounterId")
    suspend fun linkSnapshot(encounterId: Long, snapshotId: Long)

    /**
     * Returns the most recent [seenAt] timestamp for [rotatingId],
     * used by the dedup engine to enforce the cooldown period.
     */
    @Query(
        "SELECT seenAt FROM encounter WHERE rotatingId = :rotatingId ORDER BY seenAt DESC LIMIT 1"
    )
    suspend fun lastSeenAt(rotatingId: String): Long?

    /** Count of all encounters (displayed on the home screen). */
    @Query("SELECT COUNT(*) FROM encounter")
    fun observeCount(): Flow<Int>

    /** Synchronous count — used by home screen widget (off main thread). */
    @Query("SELECT COUNT(*) FROM encounter")
    suspend fun countAll(): Int

    /** Returns all encounters — used for streak computation in BLE payload. */
    @Query("SELECT * FROM encounter ORDER BY seenAt DESC")
    suspend fun getAll(): List<Encounter>

    /** Number of encounters since [sinceMs] epoch ms — for "today" widget cell. */
    @Query("SELECT COUNT(*) FROM encounter WHERE seenAt >= :sinceMs")
    suspend fun countSince(sinceMs: Long): Int

    // ── Friends ────────────────────────────────────────────────────────────────

    /** Toggle the friend flag for a given encounter row. */
    @Query("UPDATE encounter SET isFriend = :isFriend WHERE id = :id")
    suspend fun setFriend(id: Long, isFriend: Boolean)

    /** Observe all encounters the user has marked as friends, newest first. */
    @Query("SELECT * FROM encounter WHERE isFriend = 1 ORDER BY seenAt DESC")
    fun observeFriends(): Flow<List<Encounter>>

    /** Total friend count. */
    @Query("SELECT COUNT(*) FROM encounter WHERE isFriend = 1")
    suspend fun countFriends(): Int

    /**
     * Delete a provisional encounter row.
     * Called when post-GATT dedup determines this device was already encountered
     * within the 24-hour window.
     */
    @Query("DELETE FROM encounter WHERE id = :id")
    suspend fun delete(id: Long)

    /**
     * Find the encounter that is linked to a specific peer snapshot.
     * Used to resolve friend-invite deep links.
     */
    @Query("SELECT id FROM encounter WHERE peerSnapshotId = :snapshotId LIMIT 1")
    suspend fun getIdBySnapshotId(snapshotId: Long): Long?

    /**
     * Refresh the [seenAt] timestamp of an existing encounter row.
     * Called when the same user passes by again within [BleConstants.USER_DEDUP_WINDOW_MS]
     * so the gallery shows the most recent meeting time without creating a duplicate row.
     */
    @Query("UPDATE encounter SET seenAt = :seenAt WHERE id = :id")
    suspend fun updateSeenAt(id: Long, seenAt: Long)

    /**
     * Find the most recent encounter whose linked peer snapshot belongs to [peerInstId].
     * Used together with [updateSeenAt] to refresh an existing pass instead of
     * inserting a duplicate when the same device is re-encountered within the dedup window.
     */
    @Query("""
        SELECT e.id FROM encounter e
        INNER JOIN peer_profile_snapshot p ON e.peerSnapshotId = p.id
        WHERE p.peerInstId = :peerInstId
        ORDER BY e.seenAt DESC
        LIMIT 1
    """)
    suspend fun getMostRecentEncounterIdForPeer(peerInstId: String): Long?

    /**
     * Set the friend flag on ALL encounter rows linked to a peer with [peerInstId].
     * Called on friend-toggle so every historical row for the same identity stays
     * consistent — prevents the same person appearing in both Sparks and Friends.
     */
    @Query("""
        UPDATE encounter SET isFriend = :isFriend
        WHERE peerSnapshotId IN (
            SELECT id FROM peer_profile_snapshot WHERE peerInstId = :peerInstId
        )
    """)
    suspend fun setFriendByInstId(peerInstId: String, isFriend: Boolean)

    /**
     * Count encounters whose [rotatingId] matches [mac] (i.e. same hardware address),
     * that have a successfully linked peer snapshot, and whose [seenAt] falls within
     * the [sinceMs] window.
     *
     * Used as a fallback identity-dedup mechanism for privacy-mode peers where no
     * stable [effectiveId] is available: we use the BLE MAC address recorded in
     * the encounter row to prevent the same anonymous device from farming Volts
     * once per hour for the whole day.
     */
    @Query("SELECT COUNT(*) FROM encounter WHERE rotatingId = :mac AND peerSnapshotId IS NOT NULL AND seenAt >= :sinceMs")
    suspend fun countLinkedByMacSince(mac: String, sinceMs: Long): Int
}
