package com.thunderpass.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * An encounter event — one row per detected ThunderPass advertisement.
 * Encounters are append-only; rows are never updated.
 *
 * The [peerSnapshotId] foreign key points to the peer's profile snapshot
 * as it was received during this encounter (nullable: populated only
 * after a successful GATT exchange).
 */
@Entity(
    tableName = "encounter",
    foreignKeys = [
        ForeignKey(
            entity = PeerProfileSnapshot::class,
            parentColumns = ["id"],
            childColumns = ["peerSnapshotId"],
            onDelete = ForeignKey.SET_NULL,
        )
    ],
    indices = [
        Index("rotatingId"),
        Index("peerSnapshotId"),
    ]
)
data class Encounter(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    /**
     * The rotating ID broadcast by the peer at the time of the encounter.
     * Used for dedup (see EncounterDedup).
     */
    val rotatingId: String,

    /** Unix epoch millis when the advertisement was first seen. */
    val seenAt: Long,

    /** BLE signal strength at discovery time. */
    val rssi: Int,

    /**
     * Foreign key to [PeerProfileSnapshot.id].
     * Null until a GATT exchange completes.
     */
    val peerSnapshotId: Long? = null,

    /**
     * True when the user has manually marked this encounter as a friend.
     * Used to build the Friends list in the Passes screen.
     */
    val isFriend: Boolean = false,
)
