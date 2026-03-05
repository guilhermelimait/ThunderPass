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

    /** Total friend count — used for Supabase sync. */
    @Query("SELECT COUNT(*) FROM encounter WHERE isFriend = 1")
    suspend fun countFriends(): Int

    /**
     * Delete a provisional encounter row.
     * Called when post-GATT dedup determines this user was already encountered
     * within the 24-hour window, or when Supabase identity verification fails.
     */
    @Query("DELETE FROM encounter WHERE id = :id")
    suspend fun delete(id: Long)

    /**
     * Find the encounter that is linked to a specific peer snapshot.
     * Used to resolve friend-invite deep links.
     */
    @Query("SELECT id FROM encounter WHERE peerSnapshotId = :snapshotId LIMIT 1")
    suspend fun getIdBySnapshotId(snapshotId: Long): Long?
}
