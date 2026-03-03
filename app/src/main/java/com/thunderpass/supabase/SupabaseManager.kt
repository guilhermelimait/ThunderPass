package com.thunderpass.supabase

import com.thunderpass.BuildConfig
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest

/**
 * Singleton Supabase client.
 *
 * The anon key is safe to include in the app — all security is enforced
 * server-side via Row Level Security (RLS) policies.
 * Credentials are injected via BuildConfig from local.properties at build time.
 *
 * Session is automatically persisted to SharedPreferences and refreshed;
 * users stay logged in for 30 days without doing anything.
 */
object SupabaseManager {

    val client = createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_ANON_KEY,
    ) {
        install(Auth) {
            alwaysAutoRefresh = true   // silently refreshes token before it expires
            autoLoadFromStorage = true // restores session from SharedPreferences on cold start
        }
        install(Postgrest)
    }
}
