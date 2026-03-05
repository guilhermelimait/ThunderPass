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

    /** Reactive observation of a single snapshot — emits whenever the row is updated. */
    @Query("SELECT * FROM peer_profile_snapshot WHERE id = :id")
    fun observeById(id: Long): Flow<PeerProfileSnapshot?>

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
            retroGameConsoles         = :gameConsoles,
            retroGameImages           = :gameImages
        WHERE id = :id
    """)
    suspend fun updateRetroStatsWithGames(
        id:           Long,
        points:       Long,
        recentCount:  Int,
        gameTitles:   String,
        gameConsoles: String,
        gameImages:   String,
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
    /**
     * Returns the most-recent snapshot id from [userId] that falls within the dedup window.
     * Used to refresh profile data when dedup fires.
     */
    @Query("SELECT id FROM peer_profile_snapshot WHERE peerUserId = :userId AND receivedAt >= :sinceMs ORDER BY receivedAt DESC LIMIT 1")
    suspend fun latestIdByUserIdSince(userId: String, sinceMs: Long): Long?

    /**
     * Refreshes the mutable profile fields on an existing snapshot without changing
     * RA stats or the receivedAt timestamp (Volts are NOT re-awarded).
     * Called when the same user is seen again within the 24h dedup window so the
     * friend card always shows up-to-date name / avatar / stats.
     */
    @Query("""
        UPDATE peer_profile_snapshot
        SET displayName     = :displayName,
            greeting        = :greeting,
            avatarKind      = :avatarKind,
            avatarColor     = :avatarColor,
            avatarSeed      = :avatarSeed,
            retroUsername   = :retroUsername,
            ghostGame       = :ghostGame,
            ghostScore      = :ghostScore,
            peerVoltsTotal  = :peerVoltsTotal,
            peerPassesCount = :peerPassesCount,
            peerBadgesCount = :peerBadgesCount,
            peerStreakCount  = :peerStreakCount,
            rawJson         = :rawJson
        WHERE id = :id
    """)
    suspend fun updateProfileData(
        id:             Long,
        displayName:    String,
        greeting:       String,
        avatarKind:     String,
        avatarColor:    String,
        avatarSeed:     String?,
        retroUsername:  String?,
        ghostGame:      String?,
        ghostScore:     Long?,
        peerVoltsTotal: Long?,
        peerPassesCount:Int?,
        peerBadgesCount:Int?,
        peerStreakCount: Int?,
        rawJson:        String,
    )    /**
     * Find the most recent snapshot from a specific peer Supabase userId.
     * Used to resolve friend-invite deep links to an existing encounter.
     */
    @Query("SELECT id FROM peer_profile_snapshot WHERE peerUserId = :userId ORDER BY receivedAt DESC LIMIT 1")
    suspend fun getSnapshotIdByUserId(userId: String): Long?

    /**
     * Find snapshots that have a retroUsername but whose RA data hasn't been
     * fetched yet. Used by the internet-sync callback to backfill offline encounters.
     */
    @Query("""
        SELECT * FROM peer_profile_snapshot
        WHERE retroUsername IS NOT NULL AND retroUsername != '' AND retroFetchAttempted = 0
        ORDER BY receivedAt DESC
    """)
    suspend fun findSnapshotsNeedingRetroFetch(): List<PeerProfileSnapshot>
}
