package com.thunderpass.supabase

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

    // Anon key is safe to commit — all access control is enforced by RLS policies in Supabase.
    private const val URL     = "https://wrunnzrxuapqpxoxvzpa.supabase.co"
    private const val ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9" +
        ".eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6IndydW5uenJ4dWFwcXB4b3h2enBhIiwicm9s" +
        "ZSI6ImFub24iLCJpYXQiOjE3NzI1NTgyMjcsImV4cCI6MjA4ODEzNDIyN30" +
        ".IwgF-1KZg1wzaFvny0RidcU_Yw0qronKt_HSEW7-3K0"

    val client = createSupabaseClient(
        supabaseUrl = URL,
        supabaseKey = ANON_KEY,
    ) {
        install(Auth) {
            alwaysAutoRefresh = true   // silently refreshes token before it expires
            autoLoadFromStorage = true // restores session from SharedPreferences on cold start
        }
        install(Postgrest)
    }
}
