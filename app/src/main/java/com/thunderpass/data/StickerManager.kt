package com.thunderpass.data

import android.content.Context
import com.thunderpass.data.db.ThunderPassDatabase

/**
 * Manages the sticker collection for the current user.
 *
 * Stickers are stored as a comma-separated list of keys in [MyProfile.stickersJson].
 * Each sticker is identified by a stable string key.
 */
object StickerManager {

    // ── Sticker definitions ──────────────────────────────────────────────────

    data class Sticker(
        val key:         String,
        val icon:        String,
        val name:        String,
        val description: String,
        val secret:      Boolean = false,   // if true, name/description hidden until earned
    )

    val ALL_STICKERS = listOf(
        Sticker("first_spark",   "🌩️", "First Spark",     "Made your very first wireless connection."),
        Sticker("player_2",      "🎮", "Player 2",        "Met someone who also plays RetroAchievements."),
        Sticker("high_roller",   "🏆", "High Roller",     "Encountered an RA legend with over 20,000 points."),
        Sticker("sharp_signal",  "📡", "Sharp Signal",    "Met someone at extremely close range."),
        Sticker("dusk_patrol",   "⭐", "Dusk Patrol",     "Made a connection after 9 PM."),
        Sticker("early_bird",    "🌅", "Early Bird",      "Made a connection before 8 AM."),
        Sticker("on_fire",       "🔥", "On Fire",         "Had 3 or more encounters in a single day."),
        Sticker("crystal_clear", "💎", "Crystal Clear",   "Achieved a 5-day encounter streak.",  secret = true),
        Sticker("double_trouble","🎲", "Double Trouble",  "Crossed paths with the same traveler twice."),
        Sticker("legendary",     "👾", "Legendary",       "Triggered a Legendary Encounter — same game, same moment.", secret = true),
        Sticker("marathon",      "♾️", "Marathon Runner", "Reached 50 total encounters.",        secret = true),
        Sticker("thunder_god",   "⚡", "Thunder God",     "Reached 100 total encounters.",       secret = true),
    )

    private val stickerMap = ALL_STICKERS.associateBy { it.key }

    fun get(key: String): Sticker? = stickerMap[key]

    // ── Award logic ──────────────────────────────────────────────────────────

    /**
     * Award the given [keys] to the profile, persisting them in the DB.
     * No-ops for keys already owned.
     * Returns the newly awarded stickers (empty if nothing new).
     */
    suspend fun award(context: Context, vararg keys: String): List<Sticker> {
        val db  = ThunderPassDatabase.getInstance(context)
        val dao = db.myProfileDao()
        val current = dao.get() ?: return emptyList()

        val owned = current.stickersJson.split(",").filter { it.isNotBlank() }.toMutableSet()
        val newlyEarned = mutableListOf<Sticker>()

        for (key in keys) {
            if (key !in owned) {
                owned += key
                stickerMap[key]?.let { newlyEarned += it }
            }
        }

        if (newlyEarned.isNotEmpty()) {
            dao.upsert(current.copy(stickersJson = owned.joinToString(",")))
        }
        return newlyEarned
    }

    /**
     * Returns the set of sticker keys currently owned by the user.
     */
    suspend fun owned(context: Context): Set<String> {
        val dao = ThunderPassDatabase.getInstance(context).myProfileDao()
        val raw = dao.get()?.stickersJson ?: return emptySet()
        return raw.split(",").filter { it.isNotBlank() }.toSet()
    }
}
