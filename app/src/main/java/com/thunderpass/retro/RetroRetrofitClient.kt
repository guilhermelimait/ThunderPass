package com.thunderpass.retro

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private const val TAG = "ThunderPass/RetroAPI"
private const val BASE_URL = "https://retroachievements.org/API"

/**
 * Fetches RetroAchievements profile data over the RA v1 REST API.
 * Uses OkHttp (already on the classpath via Coil) + Android's built-in org.json.
 */
object RetroRetrofitClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * Fetch a user's RA profile summary including points and recently played games.
     */
    suspend fun fetchRetroMetadata(
        username: String,
        auth: RetroAuthManager,
    ): Result<RetroProfile> = withContext(Dispatchers.IO) {
        try {
            val apiKey = auth.getApiKey()
            if (apiKey.isBlank()) {
                return@withContext Result.failure<RetroProfile>(
                    IllegalStateException("No RA API key configured")
                )
            }
            val apiUser = auth.getApiUser().ifBlank { username }

            val url = "$BASE_URL/API_GetUserSummary.php".toHttpUrl().newBuilder()
                .addQueryParameter("z", apiUser)
                .addQueryParameter("y", apiKey)
                .addQueryParameter("u", username)
                .addQueryParameter("g", "10")
                .build()

            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                return@withContext Result.failure<RetroProfile>(
                    RuntimeException("RA API HTTP ${response.code}")
                )
            }

            val body = response.body?.string()
            if (body.isNullOrBlank()) {
                return@withContext Result.failure<RetroProfile>(
                    RuntimeException("Empty response from RA API")
                )
            }

            val json = JSONObject(body)

            val recentlyPlayed = json.optJSONArray("RecentlyPlayed")?.let { arr ->
                (0 until arr.length()).map { i ->
                    val g = arr.getJSONObject(i)
                    RecentGame(
                        gameId      = g.optLong("GameID"),
                        title       = g.optString("Title", ""),
                        consoleName = g.optString("ConsoleName", ""),
                        lastPlayed  = g.optString("LastPlayed").takeIf { it.isNotBlank() },
                        imageIcon   = g.optString("ImageIcon").takeIf { it.isNotBlank() },
                    )
                }
            }

            Result.success(
                RetroProfile(
                    user                = json.optString("User", username),
                    totalPoints         = json.optLong("TotalPoints", 0),
                    totalSoftcorePoints = json.optLong("TotalSoftcorePoints", 0),
                    recentlyPlayedCount = json.optInt("RecentlyPlayedCount", 0),
                    recentlyPlayed      = recentlyPlayed,
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "fetchRetroMetadata($username) failed", e)
            Result.failure(e)
        }
    }

    /**
     * Returns the total number of (softcore) achievements the user has earned.
     * Uses the same summary endpoint; falls back to 0 on any error.
     */
    suspend fun fetchSoftcoreAchievementCount(
        username: String,
        auth: RetroAuthManager,
    ): Int = withContext(Dispatchers.IO) {
        try {
            val apiKey = auth.getApiKey()
            if (apiKey.isBlank()) return@withContext 0
            val apiUser = auth.getApiUser().ifBlank { username }

            val url = "$BASE_URL/API_GetUserSummary.php".toHttpUrl().newBuilder()
                .addQueryParameter("z", apiUser)
                .addQueryParameter("y", apiKey)
                .addQueryParameter("u", username)
                .addQueryParameter("g", "0")
                .build()

            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext 0

            val body = response.body?.string() ?: return@withContext 0
            val json = JSONObject(body)
            // ContribCount represents the user's contribution/achievement count
            json.optInt("ContribCount", 0)
        } catch (e: Exception) {
            Log.e(TAG, "fetchSoftcoreAchievementCount($username) failed", e)
            0
        }
    }
}
