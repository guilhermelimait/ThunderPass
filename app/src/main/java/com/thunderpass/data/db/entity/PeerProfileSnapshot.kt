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

    /**
     * The peer's DiceBear seed string (from their own profile settings).
     * Used to render the same avatar on this device as on the peer's device.
     * Falls back to [rotatingId] when null (old peers / pre-fix builds).
     */
    val avatarSeed: String? = null,

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

    /**
     * True once a RA fetch has been attempted (regardless of success/failure).
     * Lets the UI distinguish "still in-flight" from "permanently unavailable".
     */
    val retroFetchAttempted: Boolean = false,

    // ── Ghost Payload ──────────────────────────────────────────────────

    /** Game name from the peer's ghost payload (null if peer didn't share one). */
    val ghostGame: String? = null,

    /** Peer's ghost score or time in ms (null if not shared). */
    val ghostScore: Long? = null,

    /**
     * The peer's Supabase auth UUID, sent voluntarily in the GATT payload.
     * Null when the peer is in privacy mode or running an older build.
     * Used for 24-hour identity dedup: one Spark per userId per day.
     */
    val peerUserId: String? = null,

    // ── RetroAchievements — recent game titles (SEP-joined strings) ──────────

    /** SEP-joined list of title strings from the peer's recently-played games. */
    val retroGameTitles: String? = null,

    /** SEP-joined list of console name strings matching [retroGameTitles]. */
    val retroGameConsoles: String? = null,

    // ── Peer Stats (exchanged via BLE; null when peer is in privacy mode or older build) ─────

    /** Peer's total Volts (energy). Null if peer did not share. */
    val peerVoltsTotal: Long? = null,

    /** Peer's total encounter count (Passes). Null if peer did not share. */
    val peerPassesCount: Int? = null,

    /** Peer's earned badge (sticker) count. Null if peer did not share. */
    val peerBadgesCount: Int? = null,

    /** Peer's current encounter streak in days. Null if peer did not share. */
    val peerStreakCount: Int? = null,
)
