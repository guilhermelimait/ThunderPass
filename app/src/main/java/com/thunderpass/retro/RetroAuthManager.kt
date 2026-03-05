package com.thunderpass.retro

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.thunderpass.BuildConfig

private const val TAG  = "ThunderPass/RetroAuth"
private const val PREF = "ra_credentials"
private const val KEY_API_KEY  = "ra_api_key"
private const val KEY_API_USER = "ra_api_user"

/**
 * Secure storage for RetroAchievements API credentials.
 *
 * Credentials are persisted in [EncryptedSharedPreferences] (AES-256-GCM).
 * If the user has not yet set credentials at runtime, the manager falls back
 * to the build-time values in [BuildConfig] (injected from local.properties).
 *
 * Usage:
 * ```kotlin
 * val auth = RetroAuthManager.getInstance(context)
 * auth.saveCredentials(apiKey = "...", apiUser = "YourRAName")
 * auth.getApiKey()   // → encrypted runtime value or BuildConfig fallback
 * ```
 */
class RetroAuthManager private constructor(context: Context) {

    private val prefs by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                PREF,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } catch (e: Exception) {
            Log.e(TAG, "EncryptedSharedPreferences unavailable, falling back to plaintext", e)
            context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        }
    }

    /**
     * Returns the API key — checks EncryptedSharedPrefs first, then falls back
     * to the build-time [BuildConfig.RA_API_KEY] value.
     */
    fun getApiKey(): String =
        prefs.getString(KEY_API_KEY, null)?.takeIf { it.isNotBlank() }
            ?: BuildConfig.RA_API_KEY

    /** Returns the stored API username, falling back to the BuildConfig compile-time value. */
    fun getApiUser(): String =
        prefs.getString(KEY_API_USER, null)?.takeIf { it.isNotBlank() }
            ?: BuildConfig.RA_API_USER

    /** Returns true if a usable RA username and API key are available (allows fetching). */
    fun hasCredentials(): Boolean = getApiUser().isNotBlank() && getApiKey().isNotBlank()

    /**
     * Saves the user's RA credentials securely.
     * [apiKey] is optional — omit or pass empty string to leave the existing key unchanged.
     */
    fun saveCredentials(apiUser: String, apiKey: String = "") {
        prefs.edit()
            .putString(KEY_API_USER, apiUser.trim())
            .also { if (apiKey.isNotBlank()) it.putString(KEY_API_KEY, apiKey.trim()) }
            .apply()
        Log.i(TAG, "RA credentials updated (apiUser=${apiUser.trim().take(4)}…, hasKey=${apiKey.isNotBlank()})")
    }

    companion object {
        @Volatile private var INSTANCE: RetroAuthManager? = null

        fun getInstance(context: Context): RetroAuthManager =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: RetroAuthManager(context.applicationContext).also { INSTANCE = it }
            }
    }
}
