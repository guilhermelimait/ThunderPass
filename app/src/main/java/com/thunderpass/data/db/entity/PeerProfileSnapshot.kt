package com.thunderpass.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A snapshot of a peer's profile as received during a GATT exchange.
 * Profiles are stored as "what you saw at that time" — they are
 * immutable after insertion (per SPEC.md).
 */
@Entity(tableName = "peer_profile_snapshot")
data class PeerProfileSnapshot(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    /** The peer's rotating ID at the time of the exchange. */
    val rotatingId: String,

    /** Display name as provided by the peer. */
    val displayName: String,

    /** Greeting message from the peer. */
    val greeting: String,

    /** Avatar kind key (matches a drawable key in the UI). */
    val avatarKind: String,

    /** Avatar accent color hex string. */
    val avatarColor: String,

    /** Protocol version reported by the peer. */
    val protocolVersion: Int,

    /** Unix epoch millis when the snapshot was stored. */
    val receivedAt: Long,

    /**
     * Raw JSON payload from the GATT exchange, kept for forward
     * compatibility so future fields aren't lost.
     */
    val rawJson: String,

    // ── RetroAchievements ────────────────────────────────────────────────────

    /** Peer's RetroAchievements username, if shared in their profile card. */
    val retroUsername: String? = null,

    /** Peer's total RA points, fetched after the GATT exchange. */
    val retroTotalPoints: Long? = null,

    /** Number of games the peer has recently played on RA. */
    val retroRecentlyPlayedCount: Int? = null,
)
