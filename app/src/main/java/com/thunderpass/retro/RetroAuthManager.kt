package com.thunderpass.retro

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private const val TAG  = "ThunderPass/RetroAuth"
private const val PREF = "ra_credentials"
private const val KEY_API_KEY  = "ra_api_key"
private const val KEY_API_USER = "ra_api_user"

/**
 * Secure storage for RetroAchievements API credentials.
 *
 * Credentials are persisted in [EncryptedSharedPreferences] (AES-256-GCM) backed by the
 * Android Keystore. Credentials must be entered at runtime via [saveCredentials]; there is
 * no compile-time fallback so no personal API keys can be baked into the APK.
 *
 * Usage:
 * ```kotlin
 * val auth = RetroAuthManager.getInstance(context)
 * auth.saveCredentials(apiKey = "...", apiUser = "YourRAName")
 * auth.getApiKey()   // → encrypted runtime value, or blank if not set
 * ```
 */
class RetroAuthManager private constructor(context: Context) {

    private val _credentialsVersion = MutableStateFlow(0L)
    /** Bumped every time [saveCredentials] is called so observers can re-fetch. */
    val credentialsVersion: StateFlow<Long> get() = _credentialsVersion

    private val prefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        // No plaintext fallback — credentials must always be stored encrypted.
        // If the Keystore is unavailable an exception propagates intentionally.
        EncryptedSharedPreferences.create(
            context,
            PREF,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    /**
     * Returns the stored API key, or blank string if not yet set.
     * Credentials must be entered at runtime via [saveCredentials].
     */
    fun getApiKey(): String =
        prefs.getString(KEY_API_KEY, null)?.takeIf { it.isNotBlank() } ?: ""

    /** Returns the stored API username, or blank string if not yet set. */
    fun getApiUser(): String =
        prefs.getString(KEY_API_USER, null)?.takeIf { it.isNotBlank() } ?: ""

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
        _credentialsVersion.value = System.currentTimeMillis()
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
