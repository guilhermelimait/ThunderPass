package com.thunderpass.retro

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
import com.thunderpass.data.db.dao.PeerProfileSnapshotDao
import java.text.NumberFormat

private const val TAG     = "ThunderPass/RetroRepo"
private const val CHANNEL = "retro_achievements"

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
            return emptyList()
        }

        // Persist in Room
        snapshotDao.updateRetroStats(
            id          = snapshotId,
            points      = profile.totalPoints,
            recentCount = profile.recentlyPlayedCount,
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

        // Fire notifications for all triggered achievements
        triggers.forEach { fireAchievementNotification(context, it) }

        return triggers
    }

    private fun fireAchievementNotification(context: Context, trigger: AchievementTrigger) {
        val nm = context.getSystemService(NotificationManager::class.java)

        // Ensure channel exists
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL,
                    "RetroAchievements",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply { description = "ThunderPass RetroAchievements encounter milestones" }
            )
        }

        val fmt = NumberFormat.getNumberInstance()

        val (title, body) = when (trigger) {
            is AchievementTrigger.PlatinumPulse ->
                "🏆 Platinum Pulse!" to
                "${trigger.peerUsername} has ${fmt.format(trigger.totalPoints)} RA points — a true elite!"

            is AchievementTrigger.LegendaryEncounter ->
                "⚡ Legendary Encounter!" to
                "You and ${trigger.peerUsername} both played ${trigger.sharedGame} recently!"

            is AchievementTrigger.RetroCircuit ->
                "🔌 Retro Circuit!" to
                "${trigger.peerUsername} is an active retro player — ${trigger.recentlyPlayedCount} games this session!"
        }

        val notifId = System.currentTimeMillis().toInt()
        val notif = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.star_on)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .build()

        nm.notify(notifId, notif)
    }
}
