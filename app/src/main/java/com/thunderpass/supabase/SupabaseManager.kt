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
 * Credentials are loaded from BuildConfig, which reads them from local.properties
 * (gitignored) or CI environment secrets — never hardcoded in source.
 * All server-side security is enforced via Row Level Security (RLS) policies.
 */
object SupabaseManager {

    val client = createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_ANON_KEY,
    ) {
        install(Auth) {
            alwaysAutoRefresh = true   // silently refreshes token before it expires
            autoLoadFromStorage = true // restores session from SharedPreferences on cold start
            // Magic links will redirect to thunderpass://callback, intercepted by MainActivity
            // and processed by handleDeeplinks() — no browser detour.
            defaultRedirectUrl = "thunderpass://callback"
        }
        install(Postgrest)
        install(ComposeAuth) {
            googleNativeLogin(serverClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID)
        }
    }
}
