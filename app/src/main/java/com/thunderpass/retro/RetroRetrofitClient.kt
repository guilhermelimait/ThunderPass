package com.thunderpass.retro

import android.util.Log
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.thunderpass.BuildConfig
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

private const val TAG = "ThunderPass/RetroClient"
private const val BASE_URL = "https://retroachievements.org/API/"

/**
 * Singleton Retrofit client for the RetroAchievements API.
 *
 * Configure credentials in `local.properties`:
 * ```
 * ra.apiKey=YOUR_WEB_API_KEY
 * ra.apiUser=YOUR_RA_USERNAME
 * ```
 * Keys are injected at build time via BuildConfig.
 * If no key is set, [fetchRetroMetadata] returns [Result.failure] gracefully.
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
     *
     * @return [Result.success] with a [RetroProfile], or [Result.failure] if:
     *   - No API credentials are configured (BuildConfig.RA_API_KEY is empty), or
     *   - The network call fails for any reason.
     */
    suspend fun fetchRetroMetadata(username: String): Result<RetroProfile> {
        val apiKey  = BuildConfig.RA_API_KEY
        val apiUser = BuildConfig.RA_API_USER

        if (apiKey.isBlank() || apiUser.isBlank()) {
            Log.d(TAG, "RA credentials not configured — skipping fetch for '$username'")
            return Result.failure(IllegalStateException("RA_API_KEY / RA_API_USER not set in local.properties"))
        }

        return runCatching {
            service.getUserSummary(
                apiUser = apiUser,
                apiKey  = apiKey,
                user    = username,
            )
        }.onSuccess {
            Log.d(TAG, "Fetched RA profile for '$username': ${it.totalPoints} pts")
        }.onFailure {
            Log.w(TAG, "RA fetch failed for '$username': ${it.message}")
        }
    }
}
