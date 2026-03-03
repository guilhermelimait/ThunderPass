package com.thunderpass.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.thunderpass.data.db.entity.PeerProfileSnapshot
import kotlinx.coroutines.flow.Flow

@Dao
interface PeerProfileSnapshotDao {

    /** All snapshots, newest first. */
    @Query("SELECT * FROM peer_profile_snapshot ORDER BY receivedAt DESC")
    fun observeAll(): Flow<List<PeerProfileSnapshot>>

    /** Insert a snapshot. Returns the new row id (used to link an encounter). */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(snapshot: PeerProfileSnapshot): Long

    /** Retrieve a single snapshot by its primary key. */
    @Query("SELECT * FROM peer_profile_snapshot WHERE id = :id")
    suspend fun getById(id: Long): PeerProfileSnapshot?

    /** Update RetroAchievements stats after a background fetch. */
    @Query("""
        UPDATE peer_profile_snapshot
        SET retroTotalPoints = :points, retroRecentlyPlayedCount = :recentCount
        WHERE id = :id
    """)
    suspend fun updateRetroStats(id: Long, points: Long, recentCount: Int)
}
