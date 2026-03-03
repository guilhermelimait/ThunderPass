package com.thunderpass.supabase

import com.thunderpass.BuildConfig
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.compose.auth.ComposeAuth
import io.github.jan.supabase.compose.auth.googleNativeLogin
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest

/**
 * Singleton Supabase client.
 *
 * The anon key is safe to include in the app — all security is enforced
 * server-side via Row Level Security (RLS) policies.
 *
 * Session is automatically persisted to SharedPreferences and refreshed;
 * users stay logged in for 30 days without doing anything.
 */
object SupabaseManager {

    // Anon key is safe to commit — all access control is enforced by RLS policies in Supabase.
    private const val URL     = "https://wrunnzrxuapqpxoxvzpa.supabase.co"
    private const val ANON_KEY = "REDACTED_ANON_KEY_PART1" +
        "REDACTED_ANON_KEY_PART2" +
        "REDACTED_ANON_KEY_PART3" +
        "REDACTED_ANON_KEY_SIG"

    val client = createSupabaseClient(
        supabaseUrl = URL,
        supabaseKey = ANON_KEY,
    ) {
        install(Auth) {
            alwaysAutoRefresh = true   // silently refreshes token before it expires
            autoLoadFromStorage = true // restores session from SharedPreferences on cold start
        }
        install(Postgrest)
        install(ComposeAuth) {
            // Google native One-Tap sign-in via Android Credential Manager.
            // Set google.webClientId in local.properties after creating OAuth credentials
            // in Google Cloud Console (type = Web application).
            googleNativeLogin(serverClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID)
        }
    }
}
