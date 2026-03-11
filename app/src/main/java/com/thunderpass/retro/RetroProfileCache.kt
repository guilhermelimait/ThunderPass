package com.thunderpass.retro

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// ─────────────────────────────────────────────────────────────────────────────
// RetroProfileCache — lightweight SharedPrefs cache for own RA profile data.
// Persists username, total points, and recent games between sessions so the
// UI can display the last known RA data instantly without an extra network call.
// ─────────────────────────────────────────────────────────────────────────────

private const val PREFS_NAME        = "ra_profile_cache"
private const val KEY_USERNAME      = "username"
private const val KEY_POINTS        = "points"
private const val KEY_SOFTCORE      = "softcore_points"
private const val KEY_GAME_TITLES   = "game_titles"
private const val KEY_GAME_CONSOLES = "game_consoles"
private const val KEY_GAME_IMAGES   = "game_images"
private const val KEY_CACHED_AT              = "cached_at"
private const val KEY_RECENT_COUNT           = "recent_count"
private const val KEY_SOFTCORE_ACHIEVEMENTS  = "softcore_achievements"
private const val SEP                        = "|||"

data class RetroProfileCacheData(
    val username:                   String,
    val totalPoints:                Long,
    val softcorePoints:             Long = 0L,
    /** Total achievements unlocked in softcore mode (inc. hardcore-earned). */
    val softcoreAchievementsEarned: Int  = 0,
    /** List of (title, consoleName, imageIconUrl?) triples for recent games. */
    val recentGames:                List<Triple<String, String, String?>>,
    val recentlyPlayedCount:        Int,
    val cachedAt:                   Long,
)

object RetroProfileCache {

    /**
     * Monotonically-increasing version counter. Bumped on every [save] so that
     * composables keyed on this value re-read the cache without polling.
     */
    private val _cacheVersion = MutableStateFlow(0L)
    val cacheVersion: StateFlow<Long> get() = _cacheVersion

    /**
     * Persist RA profile data to SharedPreferences.
     * Call after a successful [RetroRetrofitClient.fetchRetroMetadata] for the local user.
     */
    fun save(
        context:                    Context,
        username:                   String,
        points:                     Long,
        softcorePoints:             Long = 0L,
        softcoreAchievementsEarned: Int  = 0,
        games:                      List<RecentGame>,
        recentlyPlayedCount:        Int  = games.size,
    ) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putString(KEY_USERNAME,             username)
            putLong(KEY_POINTS,                 points)
            putLong(KEY_SOFTCORE,               softcorePoints)
            putInt(KEY_SOFTCORE_ACHIEVEMENTS,   softcoreAchievementsEarned)
            putString(KEY_GAME_TITLES,          games.joinToString(SEP) { it.title })
            putString(KEY_GAME_CONSOLES,        games.joinToString(SEP) { it.consoleName })
            putString(KEY_GAME_IMAGES,          games.joinToString(SEP) { it.imageIcon ?: "" })
            putInt(KEY_RECENT_COUNT,            recentlyPlayedCount)
            putLong(KEY_CACHED_AT,              System.currentTimeMillis())
        }
        _cacheVersion.value = System.currentTimeMillis()
    }

    /**
     * Load cached RA profile. Returns null if no data has been saved yet.
     */
    fun load(context: Context): RetroProfileCacheData? {
        val p = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val username = p.getString(KEY_USERNAME, null)?.takeIf { it.isNotBlank() } ?: return null
        val titles   = p.getString(KEY_GAME_TITLES, "")
            ?.split(SEP)?.filter { it.isNotBlank() } ?: emptyList()
        val consoles = p.getString(KEY_GAME_CONSOLES, "")
            ?.split(SEP)?.filter { it.isNotBlank() } ?: emptyList()
        val images   = p.getString(KEY_GAME_IMAGES, "")
            ?.split(SEP) ?: emptyList()
        return RetroProfileCacheData(
            username                   = username,
            totalPoints                = p.getLong(KEY_POINTS, 0L),
            softcorePoints             = p.getLong(KEY_SOFTCORE, 0L),
            softcoreAchievementsEarned = p.getInt(KEY_SOFTCORE_ACHIEVEMENTS, 0),
            recentGames                = titles.mapIndexed { i, title ->
                Triple(title, consoles.getOrElse(i) { "" }, images.getOrNull(i)?.takeIf { it.isNotBlank() })
            },
            recentlyPlayedCount        = p.getInt(KEY_RECENT_COUNT, titles.size),
            cachedAt                   = p.getLong(KEY_CACHED_AT, 0L),
        )
    }

    /** Remove all cached data (e.g. when the user clears their RA credentials). */
    fun clear(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit { clear() }
    }
}
