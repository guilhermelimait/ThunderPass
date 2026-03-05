package com.thunderpass.retro

import android.util.Log
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

private const val TAG = "ThunderPass/RetroClient"
private const val BASE_URL = "https://retroachievements.org/API/"

/**
 * Singleton Retrofit client for the RetroAchievements API.
 *
 * Credentials are resolved at call-time from [RetroAuthManager], which checks
 * EncryptedSharedPreferences first and falls back to BuildConfig compile-time values.
 */
object RetroRetrofitClient {

    private val moshi: Moshi by lazy {
        Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
    }

    private val service: RetroApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(RetroApiService::class.java)
    }

    /**
     * Fetches the RetroAchievements global stats for [username].
     * Credentials are resolved from [auth] at call time.
     */
    suspend fun fetchRetroMetadata(
        username: String,
        auth: RetroAuthManager,
    ): Result<RetroProfile> {
        val apiKey  = auth.getApiKey()
        val apiUser = auth.getApiUser()

        if (apiUser.isBlank()) {
            Log.d(TAG, "RA username not configured — skipping fetch for '$username'")
            return Result.failure(IllegalStateException("RA username not set"))
        }

        return runCatching {
            service.getUserSummary(apiUser = apiUser, apiKey = apiKey, user = username)
        }.onSuccess {
            Log.d(TAG, "Fetched RA profile for '$username': ${it.totalPoints} pts")
        }.onFailure {
            Log.w(TAG, "RA fetch failed for '$username': ${it.message}")
        }
    }

    /**
     * Fetches the total number of softcore achievements earned by [username].
     * Makes a single call requesting up to 500 games (covers the vast majority of players).
     * Returns 0 on error or if credentials are missing.
     *
     * The [UserCompletionProgress.results] entries each have [CompletionEntry.numAwarded]
     * which counts achievements earned in softcore mode (includes hardcore-earned ones,
     * matching the count displayed on the RA website).
     */
    suspend fun fetchSoftcoreAchievementCount(
        username: String,
        auth: RetroAuthManager,
    ): Int {
        val apiKey  = auth.getApiKey()
        val apiUser = auth.getApiUser()
        if (apiUser.isBlank()) return 0
        return runCatching {
            service.getUserCompletionProgress(apiUser = apiUser, apiKey = apiKey, user = username)
        }.getOrNull()?.results?.sumOf { it.numAwarded } ?: 0
    }
}
