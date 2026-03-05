package com.thunderpass.retro

import android.content.Context
import android.util.Log
import com.thunderpass.data.db.dao.PeerProfileSnapshotDao

private const val TAG       = "ThunderPass/RetroRepo"
private const val RETRO_SEP = "|||"  // matches RetroProfileCache separator

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

        val result = RetroRetrofitClient.fetchRetroMetadata(peerUsername, auth)
        val profile = result.getOrElse {
            Log.w(TAG, "Could not fetch RA for $peerUsername: ${it.message}")
            snapshotDao.markRetroFetchAttempted(snapshotId)
            return emptyList()
        }

        // Persist in Room — store title + console lists so EncounterDetailScreen can show them
        val recentGames  = profile.recentlyPlayed ?: emptyList()
        val gameTitles   = recentGames.joinToString(RETRO_SEP) { it.title }
        val gameConsoles = recentGames.joinToString(RETRO_SEP) { it.consoleName }
        val gameImages   = recentGames.joinToString(RETRO_SEP) { it.imageIcon ?: "" }
        snapshotDao.updateRetroStatsWithGames(
            id           = snapshotId,
            points       = profile.totalPoints,
            recentCount  = profile.recentlyPlayedCount,
            gameTitles   = gameTitles,
            gameConsoles = gameConsoles,
            gameImages   = gameImages,
        )

        Log.i(TAG, "Cached RA stats for $peerUsername: ${profile.totalPoints} pts, ${profile.recentlyPlayedCount} recent games")

        // ── Achievement detection ──────────────────────────────────────────────
        val triggers = mutableListOf<AchievementTrigger>()

        // Platinum Pulse — peer has > 20,000 points
        if (profile.totalPoints > 20_000L) {
            triggers += AchievementTrigger.PlatinumPulse(peerUsername, profile.totalPoints)
            Log.i(TAG, "🏆 Platinum Pulse triggered! $peerUsername has ${profile.totalPoints} pts")
            com.thunderpass.data.StickerManager.award(context, "high_roller")
        }

        // Legendary Encounter — both players share a recently played game
        if (!ownUsername.isNullOrBlank() && profile.recentlyPlayed != null) {
            val peerGameIds = profile.recentlyPlayed.map { it.gameId }.toSet()
            val ownResult = RetroRetrofitClient.fetchRetroMetadata(ownUsername, auth)
            ownResult.getOrNull()?.recentlyPlayed
                ?.firstOrNull { it.gameId in peerGameIds }
                ?.let { shared ->
                    triggers += AchievementTrigger.LegendaryEncounter(peerUsername, shared.title)
                    Log.i(TAG, "⚡ Legendary Encounter! Shared game: ${shared.title}")
                    com.thunderpass.data.StickerManager.award(context, "legendary")
                }
        }

        // Retro Circuit — peer is an active dedicated player
        if (profile.totalPoints > 100L && profile.recentlyPlayedCount >= 3) {
            triggers += AchievementTrigger.RetroCircuit(peerUsername, profile.recentlyPlayedCount)
            Log.i(TAG, "🔌 Retro Circuit triggered! $peerUsername played ${profile.recentlyPlayedCount} games recently")
        }

        snapshotDao.markRetroFetchAttempted(snapshotId)
        return triggers
    }
}
