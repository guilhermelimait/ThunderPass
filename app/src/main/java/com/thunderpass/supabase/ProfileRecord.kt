package com.thunderpass.supabase

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Mirrors the `profiles` table in Supabase.
 * Column names use snake_case to match the SQL schema.
 */
@Serializable
data class ProfileRecord(
    /** UUID from auth.users — primary key. */
    val id: String,

    /** Stable device UUID — used as the public lookup key on the web dashboard. */
    @SerialName("installation_id") val installationId: String,

    @SerialName("display_name")    val displayName:    String,
    val greeting:                                     String,
    @SerialName("avatar_kind")     val avatarKind:     String,
    @SerialName("avatar_color")    val avatarColor:    String,
    @SerialName("joules_total")    val voltsTotal:    Long,
    @SerialName("retro_username")  val retroUsername:  String,
    @SerialName("ghost_game")      val ghostGame:      String,
    @SerialName("ghost_score")     val ghostScore:     Long,
    @SerialName("stickers_json")   val stickersJson:   String,
    @SerialName("encounter_count") val encounterCount: Int,
    @SerialName("updated_at")      val updatedAt:      Long,
    @SerialName("avatar_seed")     val avatarSeed:     String = "",
)
