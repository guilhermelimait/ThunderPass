package com.thunderpass.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * The user's own profile — there is exactly one row (id = 1 always).
 * Editing the profile in the Settings screen updates this row.
 */
@Entity(tableName = "my_profile")
data class MyProfile(
    @PrimaryKey val id: Int = 1,

    /** Human-readable display name shown to peers. */
    val displayName: String = "Traveler",

    /** Short greeting message exchanged during a pass. */
    val greeting: String = "Hey, greetings from ThunderPass!",

    /** Avatar kind key — matches a drawable key in the UI. */
    val avatarKind: String = "defaultBolt",

    /** Avatar accent color as an ARGB hex string, e.g. "#FFD400". */
    val avatarColor: String = "#FFD400",

    /** Stable installation ID (UUID). Never broadcast directly. */
    val installationId: String = "",

    /** Unix epoch seconds when the profile was last edited. */
    val updatedAt: Long = 0L,

    /**
     * Accumulated energy in Joules. Each successful Spark (GATT profile exchange)
     * earns 100 J. Displayed on the Home screen as a game mechanic.
     */
    val joulesTotal: Long = 0L,

    /**
     * User's RetroAchievements username (optional). When set, it is included
     * in the BLE profile exchange so peers can see your RA stats on your Spark Card.
     */
    val retroUsername: String = "",
)
