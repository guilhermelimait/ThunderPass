package com.thunderpass.retro

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Retrofit service interface for the RetroAchievements public API.
 * Docs: https://api-docs.retroachievements.org/
 */
interface RetroApiService {

    /**
     * Fetch a user's global summary stats.
     *
     * @param apiUser  Your own RA username (used for authentication).
     * @param apiKey   Your RA API key (found at retroachievements.org/controlpanel.php).
     * @param user     The target user whose stats you want to retrieve.
     * @param recentGames  Number of recent games to include (0 = none, saves bandwidth).
     * @param recentAchievements  Number of recent achievements to include (0 = none).
     */
    @GET("API_GetUserSummary.php")
    suspend fun getUserSummary(
        @Query("z") apiUser: String,
        @Query("y") apiKey: String,
        @Query("u") user: String,
        @Query("g") recentGames: Int = 5,
        @Query("a") recentAchievements: Int = 0,
    ): RetroProfile
}
