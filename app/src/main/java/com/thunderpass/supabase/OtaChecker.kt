package com.thunderpass.supabase

import android.content.Context
import android.util.Log
import com.thunderpass.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private const val TAG = "ThunderPass/OtaChecker"
private const val RELEASES_URL =
    "https://api.github.com/repos/guilhermelimait/ThunderPass/releases/latest"

/**
 * Lightweight OTA update checker.
 *
 * Fetches the latest GitHub release tag and compares it against the running
 * [BuildConfig.VERSION_NAME]. Returns the new tag string when a newer version
 * is available, or null when the app is up-to-date (or the check fails).
 *
 * Version strings are expected to follow semver with an optional "v" prefix
 * (e.g. "v0.2.0" or "0.2.0"). The comparison is lexicographic after stripping
 * the prefix and splitting on "."; this is sufficient for the current scheme
 * where versions only increase.
 */
object OtaChecker {

    /**
     * Checks GitHub for a newer release.
     *
     * @return The new version tag (e.g. "v0.2.0") if newer than the running
     *         build, or null if up-to-date or the check cannot be completed.
     */
    suspend fun checkForUpdate(context: Context): String? = withContext(Dispatchers.IO) {
        try {
            val url  = URL(RELEASES_URL)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod  = "GET"
                connectTimeout = 8_000
                readTimeout    = 8_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "ThunderPass/${BuildConfig.VERSION_NAME}")
            }
            if (conn.responseCode != 200) {
                Log.d(TAG, "GitHub API returned ${conn.responseCode} — skipping OTA check")
                return@withContext null
            }
            val body    = conn.inputStream.bufferedReader().readText()
            val tagName = JSONObject(body).optString("tag_name", "") .takeIf { it.isNotBlank() }
                ?: return@withContext null

            val current = parseVersion(BuildConfig.VERSION_NAME)
            val latest  = parseVersion(tagName)

            Log.d(TAG, "Current: $current  Latest: $latest")
            if (isNewer(latest, current)) tagName else null
        } catch (e: Exception) {
            Log.d(TAG, "OTA check failed: ${e.message}")
            null
        }
    }

    /** Returns true if [a] is strictly newer than [b]. */
    private fun isNewer(a: Triple<Int,Int,Int>, b: Triple<Int,Int,Int>): Boolean {
        if (a.first != b.first) return a.first > b.first
        if (a.second != b.second) return a.second > b.second
        return a.third > b.third
    }

    /**
     * Parses a semver string like "v1.2.3" or "1.2.3" into a
     * [Triple] of (major, minor, patch).
     */
    private fun parseVersion(raw: String): Triple<Int, Int, Int> {
        val parts = raw.trimStart('v').split(".")
        return Triple(
            parts.getOrNull(0)?.toIntOrNull() ?: 0,
            parts.getOrNull(1)?.toIntOrNull() ?: 0,
            parts.getOrNull(2)?.toIntOrNull() ?: 0,
        )
    }
}
