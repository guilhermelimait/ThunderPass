package com.thunderpass.data

import android.content.Context
import com.thunderpass.data.db.ThunderPassDatabase

/**
 * Manages dynamically-awarded badges for the current user.
 *
 * Badge grants are stored as a comma-separated list of keys in [MyProfile.badgesJson].
 * Each badge is identified by the stable [BadgeDef.key] string defined in BadgeData.kt.
 */
object BadgeManager {

    /**
     * Award one or more badge keys to the profile, persisting them in the DB.
     * No-ops for keys already owned.
     * Returns the set of newly awarded keys (empty if nothing new).
     */
    suspend fun award(context: Context, vararg keys: String): Set<String> {
        val db  = ThunderPassDatabase.getInstance(context)
        val dao = db.myProfileDao()
        val current = dao.get() ?: return emptySet()

        val owned = current.badgesJson.split(",").filter { it.isNotBlank() }.toMutableSet()
        val newlyEarned = mutableSetOf<String>()

        for (key in keys) {
            if (key !in owned) {
                owned += key
                newlyEarned += key
            }
        }

        if (newlyEarned.isNotEmpty()) {
            dao.upsert(current.copy(badgesJson = owned.joinToString(",")))
        }
        return newlyEarned
    }

    /**
     * Returns the set of badge keys currently owned by the user.
     */
    suspend fun owned(context: Context): Set<String> {
        val dao = ThunderPassDatabase.getInstance(context).myProfileDao()
        val raw = dao.get()?.badgesJson ?: return emptySet()
        return raw.split(",").filter { it.isNotBlank() }.toSet()
    }
}
