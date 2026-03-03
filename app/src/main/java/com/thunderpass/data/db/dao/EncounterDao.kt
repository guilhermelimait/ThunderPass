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
    @Insert(onConflictStrategy = OnConflictStrategy.ABORT)
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
}
