package com.thunderpass.retro

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Maps to the RetroAchievements getUserSummary API response.
 * Only the fields relevant to the ThunderPass Spark Card are included;
 * the rest of the large response payload is ignored during deserialization.
 *
 * API endpoint:
 *   GET https://retroachievements.org/API/API_GetUserSummary.php
 *   ?z={apiUser}&y={apiKey}&u={targetUser}&g=0&a=0
 */
@JsonClass(generateAdapter = true)
data class RetroProfile(
    /** The peer's RetroAchievements username (may differ from display name). */
    @Json(name = "User") val user: String,

    /** Lifetime achievement points earned on RetroAchievements. */
    @Json(name = "TotalPoints") val totalPoints: Long,

    /** Number of distinct games the peer has played recently. */
    @Json(name = "RecentlyPlayedCount") val recentlyPlayedCount: Int,
)
