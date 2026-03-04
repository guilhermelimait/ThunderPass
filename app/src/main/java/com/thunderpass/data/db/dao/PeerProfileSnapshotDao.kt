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

    /** Update RetroAchievements stats + game lists after a background fetch. */
    @Query("""
        UPDATE peer_profile_snapshot
        SET retroTotalPoints          = :points,
            retroRecentlyPlayedCount  = :recentCount,
            retroGameTitles           = :gameTitles,
            retroGameConsoles         = :gameConsoles
        WHERE id = :id
    """)
    suspend fun updateRetroStatsWithGames(
        id:           Long,
        points:       Long,
        recentCount:  Int,
        gameTitles:   String,
        gameConsoles: String,
    )

    /**
     * Mark that a RA fetch has been attempted for this snapshot.
     * Called after every fetch attempt — success or failure — so the UI can
     * distinguish "still in-flight" from "fetch failed / credentials missing".
     */
    @Query("UPDATE peer_profile_snapshot SET retroFetchAttempted = 1 WHERE id = :id")
    suspend fun markRetroFetchAttempted(id: Long)

    /**
     * Count snapshots recorded from a specific peer Supabase userId since [sinceMs].
     * Used for 24-hour identity dedup: if > 0, we’ve already sparked this user today.
     */
    @Query("SELECT COUNT(*) FROM peer_profile_snapshot WHERE peerUserId = :userId AND receivedAt >= :sinceMs")
    suspend fun countByUserIdSince(userId: String, sinceMs: Long): Int
}
