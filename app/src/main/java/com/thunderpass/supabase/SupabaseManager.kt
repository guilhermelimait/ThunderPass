package com.thunderpass.supabase

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.compose.auth.ComposeAuth
import io.github.jan.supabase.compose.auth.googleNativeLogin
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest

/**
 * Singleton Supabase client.
 *
 * The anon key and Google Web Client ID are safe to include in the app —
 * all security is enforced server-side via Row Level Security (RLS) policies.
 * The Google Web Client ID is a public identifier (not a secret).
 *
 * Session is automatically persisted to SharedPreferences and refreshed;
 * users stay logged in for 30 days without doing anything.
 */
object SupabaseManager {

    private const val URL      = "https://wrunnzrxuapqpxoxvzpa.supabase.co"
    private const val ANON_KEY = "REDACTED_ANON_KEY_PART1" +
        "REDACTED_ANON_KEY_PART2" +
        "REDACTED_ANON_KEY_PART3" +
        "REDACTED_ANON_KEY_SIG"

    // Google OAuth Web Client ID — public identifier, safe to commit.
    // Created in Google Cloud Console → APIs & Services → Credentials → Web application.
    private const val GOOGLE_WEB_CLIENT_ID =
        "1099212379806-0i50usun50p8vemfq4b01ccufe1gnig1.apps.googleusercontent.com"

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
            googleNativeLogin(serverClientId = GOOGLE_WEB_CLIENT_ID)
        }
    }
}
