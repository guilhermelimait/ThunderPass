package com.thunderpass.retro

/**
 * A single recently-played game entry (kept for local display on the Encounter Detail screen).
 * Populated from BLE payload data only — no network fetch.
 */
data class RecentGame(
    val gameId: Long,
    val title: String,
    val consoleName: String,
    val lastPlayed: String? = null,
    val imageIcon: String? = null,
)

/** One game entry in the completion progress response (kept for local compatibility). */
data class CompletionEntry(
    val numAwarded: Int = 0,
    val numAwardedHardcore: Int = 0,
)

/** Top-level wrapper — kept for API compatibility with remaining callers. */
data class UserCompletionProgress(
    val count: Int = 0,
    val total: Int = 0,
    val results: List<CompletionEntry> = emptyList(),
)

/**
 * Peer’s RetroAchievements profile data stored locally after a BLE exchange.
 * No longer fetched over the network; all fields originate from peer BLE payloads.
 */
data class RetroProfile(
    val user: String,
    val totalPoints: Long,
    val totalSoftcorePoints: Long = 0L,
    val recentlyPlayedCount: Int,
    val recentlyPlayed: List<RecentGame>? = null,
)
