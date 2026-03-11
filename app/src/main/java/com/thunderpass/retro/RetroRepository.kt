package com.thunderpass.retro

import android.content.Context
import android.util.Log
import com.thunderpass.data.db.dao.PeerProfileSnapshotDao

private const val TAG = "ThunderPass/RetroRepo"
private const val SEP = "|||"

/**
 * Achievement trigger variants detected after a peer RA profile fetch.
 */
sealed class AchievementTrigger {
    /** Peer has more than 20,000 lifetime points — they're a high-energy player. */
    data class PlatinumPulse(val peerUsername: String, val totalPoints: Long) : AchievementTrigger()

    /** Both players have the same game in their recently-played list. */
    data class LegendaryEncounter(val peerUsername: String, val sharedGame: String) : AchievementTrigger()

    /** Peer is clearly an active/dedicated player (high points + active recently). */
    data class RetroCircuit(val peerUsername: String, val recentlyPlayedCount: Int) : AchievementTrigger()
}

/**
 * Coordinates RA data fetching, caching in Room, and achievement detection.
 *
 * Call [fetchAndCache] from GattClient after a successful BLE exchange.
 */
object RetroRepository {

    /**
     * Fetches the peer's RA profile, caches it in Room, runs achievement checks,
     * and fires notifications for any triggered achievements.
     *
     * @param context        Application context.
     * @param peerUsername   The peer's RA username (from BLE GATT payload).
     * @param snapshotId     The Room PeerProfileSnapshot ID to update.
     * @param snapshotDao    DAO for persisting the fetched RA data.
     * @param auth           [RetroAuthManager] providing own API credentials.
     * @param ownUsername    Own RA username for Legendary Encounter cross-check (optional).
     */
    suspend fun fetchAndCache(
        context: Context,
        peerUsername: String,
        snapshotId: Long,
        snapshotDao: PeerProfileSnapshotDao,
        auth: RetroAuthManager,
        ownUsername: String? = null,
    ): List<AchievementTrigger> {
        if (peerUsername.isBlank()) return emptyList()
        try {
            val result = RetroRetrofitClient.fetchRetroMetadata(peerUsername, auth)
            val profile = result.getOrNull()
            if (profile != null) {
                val games = profile.recentlyPlayed.orEmpty()
                snapshotDao.updateRetroStatsWithGames(
                    id          = snapshotId,
                    points      = profile.totalPoints,
                    recentCount = profile.recentlyPlayedCount,
                    gameTitles  = games.joinToString(SEP) { it.title },
                    gameConsoles = games.joinToString(SEP) { it.consoleName },
                    gameImages  = games.joinToString(SEP) { it.imageIcon.orEmpty() },
                )

                // ── Achievement trigger evaluation ──
                val triggers = mutableListOf<AchievementTrigger>()

                // PlatinumPulse: peer has > 20k lifetime points
                if (profile.totalPoints > 20_000) {
                    triggers += AchievementTrigger.PlatinumPulse(peerUsername, profile.totalPoints)
                    com.thunderpass.data.StickerManager.award(context, "high_roller")
                }

                // RetroCircuit: peer has > 100 points AND >= 3 recently played games
                if (profile.totalPoints > 100 && profile.recentlyPlayedCount >= 3) {
                    triggers += AchievementTrigger.RetroCircuit(peerUsername, profile.recentlyPlayedCount)
                }

                // LegendaryEncounter: local user and peer share a recently-played game
                if (!ownUsername.isNullOrBlank() && games.isNotEmpty()) {
                    val ownResult = RetroRetrofitClient.fetchRetroMetadata(ownUsername, auth)
                    val ownProfile = ownResult.getOrNull()
                    if (ownProfile != null) {
                        val ownGameIds = ownProfile.recentlyPlayed.orEmpty().map { it.gameId }.toSet()
                        val shared = games.firstOrNull { it.gameId in ownGameIds }
                        if (shared != null) {
                            triggers += AchievementTrigger.LegendaryEncounter(peerUsername, shared.title)
                            com.thunderpass.data.StickerManager.award(context, "legendary")
                        }
                    }
                }

                return triggers
            } else {
                Log.w(TAG, "fetchAndCache($peerUsername): API returned failure: ${result.exceptionOrNull()?.message}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchAndCache($peerUsername) failed", e)
        } finally {
            snapshotDao.markRetroFetchAttempted(snapshotId)
        }
        return emptyList()
    }
}
