package com.thunderpass.retro

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * A single recently-played game entry returned by the RA "GetUserSummary" API.
 */
@JsonClass(generateAdapter = true)
data class RecentGame(
    @Json(name = "GameID")      val gameId: Long,
    @Json(name = "Title")       val title: String,
    @Json(name = "ConsoleName") val consoleName: String,
    @Json(name = "LastPlayed")  val lastPlayed: String? = null,
    /** Image icon path suffix, e.g. "/Images/001234.png". Prepend https://media.retroachievements.org. */
    @Json(name = "ImageIcon")   val imageIcon: String? = null,
)

/**
 * One game entry in the GetUserCompletionProgress response.
 * Used to derive the total softcore achievement count.
 */
@JsonClass(generateAdapter = true)
data class CompletionEntry(
    /** Total achievements unlocked in softcore mode (includes hardcore-earned ones). */
    @Json(name = "NumAwarded")         val numAwarded: Int = 0,
    /** Achievements earned specifically in hardcore mode. */
    @Json(name = "NumAwardedHardcore") val numAwardedHardcore: Int = 0,
)

/**
 * Top-level wrapper returned by API_GetUserCompletionProgress.
 */
@JsonClass(generateAdapter = true)
data class UserCompletionProgress(
    @Json(name = "Count")   val count: Int = 0,
    @Json(name = "Total")   val total: Int = 0,
    @Json(name = "Results") val results: List<CompletionEntry> = emptyList(),
)

/**
 * Maps to the RetroAchievements getUserSummary API response.
 * Only the fields relevant to the ThunderPass Spark Card are included;
 * the rest of the large response payload is ignored during deserialization.
 *
 * API endpoint:
 *   GET https://retroachievements.org/API/API_GetUserSummary.php
 *   ?z={apiUser}&y={apiKey}&u={targetUser}&g=5&a=0
 */
@JsonClass(generateAdapter = true)
data class RetroProfile(
    /** The peer's RetroAchievements username (may differ from display name). */
    @Json(name = "User") val user: String,

    /** Lifetime achievement points earned on RetroAchievements. */
    @Json(name = "TotalPoints") val totalPoints: Long,

    /** Softcore (non-hardcore) points earned on RetroAchievements. */
    @Json(name = "TotalSoftcorePoints") val totalSoftcorePoints: Long = 0L,

    /** Number of distinct games the peer has played recently. */
    @Json(name = "RecentlyPlayedCount") val recentlyPlayedCount: Int,

    /** List of recent games (populated when g>0 in the API request). */
    @Json(name = "RecentlyPlayed") val recentlyPlayed: List<RecentGame>? = null,
)
