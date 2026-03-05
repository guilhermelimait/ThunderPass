package com.thunderpass.ble

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.util.Log
import com.thunderpass.ble.BleConstants
import com.thunderpass.ble.BleConstants.CCCD_UUID
import com.thunderpass.ble.BleConstants.REQUEST_CHAR_UUID
import com.thunderpass.ble.BleConstants.RESPONSE_CHAR_UUID
import com.thunderpass.ble.BleConstants.THUNDERPASS_SERVICE_UUID
import com.thunderpass.data.db.dao.EncounterDao
import com.thunderpass.data.db.dao.MyProfileDao
import com.thunderpass.data.db.dao.PeerProfileSnapshotDao
import com.thunderpass.data.db.entity.PeerProfileSnapshot
import com.thunderpass.retro.RetroAuthManager
import com.thunderpass.retro.RetroRepository
import com.thunderpass.security.PayloadSigner
import com.thunderpass.supabase.PeerPublicKeyRecord
import com.thunderpass.supabase.SupabaseManager
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "ThunderPass/GattClient"

/**
 * GATT client that connects to a discovered ThunderPass device and performs
 * the profile exchange.
 *
 * ### Flow (SPEC.md § GATT Handshake)
 * 1. [connect] is called with the remote [BluetoothDevice].
 * 2. On connection, request MTU = [BleConstants.PREFERRED_MTU] (512).
 * 3. After `onMtuChanged`, discover services.
 * 4. Enable notifications on [RESPONSE_CHAR_UUID].
 * 5. Write to [REQUEST_CHAR_UUID] to request the peer's profile.
 * 6. Collect chunked notifications from [RESPONSE_CHAR_UUID] and reassemble.
 * 7. Parse the JSON, persist a [PeerProfileSnapshot], and update the encounter.
 * 8. Disconnect.
 *
 * ### Chunking
 * Each notification is prefixed with a 3-byte header:
 * `[CHUNK_MAGIC:1][totalChunks:1][chunkIndex:1][data…]`
 * The client accumulates chunks until all [totalChunks] are received, then
 * concatenates and parses the full JSON.
 */
class GattClient(
    private val context: Context,
    private val encounterDao: EncounterDao,
    private val snapshotDao: PeerProfileSnapshotDao,
    private val profileDao: MyProfileDao,
    private val scope: CoroutineScope,
    private val retroAuth: RetroAuthManager,
    /** Called on the IO dispatcher after a profile exchange succeeds. */
    private val onProfileReceived: ((encounterId: Long, displayName: String) -> Unit)? = null,
) {

    // Tracks addresses that have an active GATT connection attempt in flight.
    private val activeConnections = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    // Per-address timeout jobs — cancelled when connection resolves cleanly.
    // Guards against stuck addresses in activeConnections when a device disappears.
    private val connectionTimeouts = ConcurrentHashMap<String, Job>()

    // Chunk reassembly buffers keyed by "$deviceAddress:$encounterId".
    // ConcurrentHashMap so concurrent callbacks from multiple simultaneous
    // GATT connections do not race on the same backing map.
    private val chunkBuffers = ConcurrentHashMap<String, Array<ByteArray?>>()

    private fun bufferKey(address: String, encounterId: Long) = "$address:$encounterId"

    /**
     * Initiates a GATT connection to [device].
     *
     * @param encounterId  The encounter row id to update after a successful exchange.
     */
    @android.annotation.SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice, encounterId: Long) {
        val address = device.address
        if (activeConnections.contains(address)) {
            Log.d(TAG, "Already connecting/connected to $address — skip.")
            return
        }
        activeConnections += address
        Log.i(TAG, "Connecting to $address (encounterId=$encounterId)")
        // TRANSPORT_LE forces BLE — avoids accidental BR/EDR connection attempts.
        device.connectGatt(
            context,
            /* autoConnect= */ false,
            buildCallback(encounterId),
            BluetoothDevice.TRANSPORT_LE,
        )

        // Safety net: if onConnectionStateChange never fires (device disappeared
        // between scan and connect), release the slot after 60 s so the next
        // scan cycle can attempt again.
        connectionTimeouts[address] = scope.launch {
            delay(60_000L)
            if (activeConnections.remove(address)) {
                connectionTimeouts.remove(address)
                chunkBuffers.remove(bufferKey(address, encounterId))
                Log.w(TAG, "GATT connection to $address timed out \u2014 slot released.")
            }
        }
    }

    // ── GATT Callback ─────────────────────────────────────────────────────────

    @android.annotation.SuppressLint("MissingPermission")
    private fun buildCallback(encounterId: Long) = object : BluetoothGattCallback() {

        private var gatt: BluetoothGatt? = null

        private fun cleanup(gatt: BluetoothGatt?) {
            val address = gatt?.device?.address.orEmpty()
            activeConnections -= address
            connectionTimeouts.remove(address)?.cancel()  // timeout no longer needed
            chunkBuffers.remove(bufferKey(address, encounterId))
            gatt?.close()
        }

        override fun onConnectionStateChange(
            gatt: BluetoothGatt?, status: Int, newState: Int
        ) {
            this.gatt = gatt
            val address = gatt?.device?.address.orEmpty()

            when {
                newState == BluetoothProfile.STATE_CONNECTED &&
                status == BluetoothGatt.GATT_SUCCESS -> {
                    Log.d(TAG, "GATT connected to $address; requesting MTU ${BleConstants.PREFERRED_MTU}…")
                    // Negotiate a larger MTU before service discovery so that
                    // the full profile JSON fits in a single notification.
                    gatt?.requestMtu(BleConstants.PREFERRED_MTU)
                }

                newState == BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d(TAG, "GATT disconnected from $address (status=$status)")
                    cleanup(gatt)
                }

                else -> {
                    // STATUS_CONNECTED with error, or other unexpected state.
                    Log.w(TAG, "GATT unexpected state: address=$address newState=$newState status=$status")
                    cleanup(gatt)
                }
            }
        }

        /**
         * Called when MTU negotiation completes.
         * Only start service discovery after MTU is agreed so the server
         * knows how large each notification packet can be.
         */
        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            val address = gatt.device?.address.orEmpty()
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "MTU negotiated: $mtu for $address — discovering services…")
            } else {
                Log.w(TAG, "MTU negotiation failed (status=$status) for $address — discovering services anyway…")
            }
            // Proceed regardless; the server will use the agreed (or default) MTU.
            gatt.discoverServices()
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.w(TAG, "onServicesDiscovered failed: status=$status")
                cleanup(gatt)
                return
            }

            val service = gatt?.getService(THUNDERPASS_SERVICE_UUID)
            if (service == null) {
                Log.w(TAG, "ThunderPass service not found on ${gatt?.device?.address}")
                cleanup(gatt)
                return
            }

            // Enable notifications on RESPONSE_CHAR
            val responseChar = service.getCharacteristic(RESPONSE_CHAR_UUID)
            gatt?.setCharacteristicNotification(responseChar, true)

            val cccd = responseChar.getDescriptor(CCCD_UUID)
            gatt?.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt?, descriptor: BluetoothGattDescriptor?, status: Int
        ) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.w(TAG, "CCCD write failed: status=$status")
                cleanup(gatt)
                return
            }

            // Notifications enabled — now send the REQUEST
            val requestChar = gatt
                ?.getService(THUNDERPASS_SERVICE_UUID)
                ?.getCharacteristic(REQUEST_CHAR_UUID) ?: return

            gatt.writeCharacteristic(
                requestChar,
                byteArrayOf(0x01), // request type 0x01 = profile
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            )
            Log.d(TAG, "REQUEST written to ${gatt.device?.address}")
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            if (characteristic.uuid != RESPONSE_CHAR_UUID) return

            val address = gatt.device?.address.orEmpty()

            // ── Chunk handling ────────────────────────────────────────────────
            if (value.isNotEmpty() && value[0] == BleConstants.CHUNK_MAGIC) {
                if (value.size < 4) {
                    Log.w(TAG, "Malformed chunk (too short: ${value.size}) from $address")
                    return
                }
                val totalChunks = value[1].toInt() and 0xFF
                val chunkIndex  = value[2].toInt() and 0xFF
                val data        = value.copyOfRange(3, value.size)

                val key = bufferKey(address, encounterId)
                val buffer = chunkBuffers.getOrPut(key) { arrayOfNulls(totalChunks) }

                if (chunkIndex < buffer.size) {
                    buffer[chunkIndex] = data
                    Log.d(TAG, "Chunk $chunkIndex/$totalChunks from $address (${data.size} bytes)")
                }

                // Check if all chunks have arrived
                if (buffer.all { it != null }) {
                    val full = buffer.filterNotNull()
                        .fold(byteArrayOf()) { acc, bytes -> acc + bytes }
                    chunkBuffers.remove(key)
                    Log.d(TAG, "All $totalChunks chunks received from $address (${full.size} bytes total)")
                    scope.launch(Dispatchers.IO) { parseAndPersist(full.toString(Charsets.UTF_8), encounterId, address) }
                    gatt.disconnect()
                }
            } else {
                // Legacy / non-chunked single notification (backward compat)
                val raw = value.toString(Charsets.UTF_8)
                Log.d(TAG, "Single-packet RESPONSE (${raw.length} chars) from $address")
                scope.launch(Dispatchers.IO) { parseAndPersist(raw, encounterId, address) }
                gatt.disconnect()
            }
        }
    }

    // ── Payload parser ────────────────────────────────────────────────────────

    /**
     * Parses the JSON payload per SPEC.md and stores a [PeerProfileSnapshot],
     * then links it to the [encounter] row.
     *
     * @param address  The BLE MAC address of the remote device (stable within an advertising
     *                 session). Used as a fallback dedup key when the peer is in privacy mode
     *                 and no stable [effectiveId] is available.
     */
    private suspend fun parseAndPersist(raw: String, encounterId: Long, address: String) {
        try {
            val json       = org.json.JSONObject(raw)
            val version    = json.optInt("v", 0)
            val rotatingId = json.optString("rotatingId", "")
            val peerUserId = json.optString("userId", "").takeIf { it.isNotBlank() }
            val ts         = json.optLong("ts", System.currentTimeMillis() / 1000)
            val sig        = json.optString("sig", "").takeIf { it.isNotBlank() }
            val data       = json.optJSONObject("data") ?: org.json.JSONObject()

            val displayName   = data.optString("displayName", "Unknown")
            val greeting      = data.optString("greeting", "")
            val avatar        = data.optJSONObject("avatar") ?: org.json.JSONObject()
            val avatarKind    = avatar.optString("kind", "defaultBolt")
            val avatarColor   = avatar.optString("color", "#FFFFFF")
            val avatarSeed    = avatar.optString("seed", "").takeIf { it.isNotBlank() }
            val retroUsername = data.optString("retroUsername", "").takeIf { it.isNotBlank() }
            val ghostGame     = data.optString("ghostGame", "").takeIf { it.isNotBlank() }
            val ghostScore    = data.optLong("ghostScore", 0L).takeIf { it > 0L }

            // Peer stats — null when peer is in privacy mode or running an older build
            val peerVolts   = data.optLong("volts",   -1L).takeIf { it >= 0L }
            val peerPasses  = data.optInt("passes",   -1).takeIf  { it >= 0 }
            val peerBadges  = data.optInt("badges",   -1).takeIf  { it >= 0 }
            val peerStreak  = data.optInt("streak",   -1).takeIf  { it >= 0 }

            // Stable peer identifier for identity dedup.
            // Prefer Supabase userId when available; fall back to installationId so that
            // dedup works even when Supabase is offline or the peer has no session.
            val peerInstId  = data.optString("instId", "").takeIf { it.isNotBlank() }
            val effectiveId = peerUserId ?: peerInstId

            // ── 24-hour identity dedup (local) ─────────────────────────────────────
            // Rotating IDs change every 60 min, so scan-level dedup alone can't
            // prevent the same user earning multiple Sparks within the hour.
            // Use the stable effectiveId (Supabase userId OR installationId) to check
            // whether we already sparked this person within the dedup window.
            if (effectiveId != null) {
                val cutoffMs = System.currentTimeMillis() - BleConstants.USER_DEDUP_WINDOW_MS
                if (snapshotDao.countByUserIdSince(effectiveId, cutoffMs) > 0) {
                    Log.i(TAG, "User $effectiveId already encountered within 24h window — refreshing profile data only")
                    // Refresh the existing snapshot with the latest profile data so the
                    // friend card always shows the peer's current name, avatar, and stats.
                    // Volts are NOT re-awarded and the encounter's timestamp is NOT moved.
                    val existingId = snapshotDao.latestIdByUserIdSince(effectiveId, cutoffMs)
                    if (existingId != null) {
                        snapshotDao.updateProfileData(
                            id             = existingId,
                            displayName    = displayName,
                            greeting       = greeting,
                            avatarKind     = avatarKind,
                            avatarColor    = avatarColor,
                            avatarSeed     = avatarSeed,
                            retroUsername  = retroUsername,
                            ghostGame      = ghostGame,
                            ghostScore     = ghostScore,
                            peerVoltsTotal = peerVolts,
                            peerPassesCount= peerPasses,
                            peerBadgesCount= peerBadges,
                            peerStreakCount = peerStreak,
                            rawJson        = raw,
                        )
                        // If retro username changed or fetch hasn't been done yet,
                        // queue a background RA fetch on the refreshed snapshot.
                        if (retroUsername != null) {
                            scope.launch(Dispatchers.IO) {
                                val ownUsername = profileDao.get()?.retroUsername?.takeIf { it.isNotBlank() }
                                RetroRepository.fetchAndCache(
                                    context      = context,
                                    peerUsername = retroUsername,
                                    snapshotId   = existingId,
                                    snapshotDao  = snapshotDao,
                                    auth         = retroAuth,
                                    ownUsername  = ownUsername,
                                )
                            }
                        }
                        Log.i(TAG, "Profile refreshed for $effectiveId (snapshot=$existingId)")
                    }
                    encounterDao.delete(encounterId)
                    return
                }

                // ── Online Supabase identity verify + ownership proof ──────────────
                // Only run when the peer explicitly claimed a Supabase userId.
                // Peers identified only by installationId are accepted locally;
                // installationId is not in Supabase so we can't verify it there.
                if (peerUserId != null) {
                    val verifyResult = runCatching {
                        SupabaseManager.client.from("profiles")
                            .select { filter { eq("id", peerUserId) } }
                            .decodeList<PeerPublicKeyRecord>()
                    }
                    if (verifyResult.isSuccess) {
                        val peerRecord = verifyResult.getOrNull()!!.firstOrNull()
                        if (peerRecord == null) {
                            Log.w(TAG, "Peer userId=$peerUserId not in Supabase — rejecting encounter #$encounterId")
                            encounterDao.delete(encounterId)
                            return
                        }
                        Log.d(TAG, "Supabase userId verified: $peerUserId")

                        // ── Payload signature verification ──────────────────────────
                        val pubKey = peerRecord.publicKey
                        if (sig != null && pubKey != null) {
                            val msg = PayloadSigner.signedPayload(peerUserId, rotatingId, ts)
                            if (!PayloadSigner.verify(msg, sig, pubKey)) {
                                Log.w(TAG, "Payload signature mismatch for $peerUserId — rejecting encounter #$encounterId")
                                encounterDao.delete(encounterId)
                                return
                            }
                            Log.d(TAG, "Payload signature verified for $peerUserId ✓")
                        } else if (sig != null) {
                            Log.d(TAG, "No public key in Supabase for $peerUserId — skipping sig check")
                        } else {
                            Log.d(TAG, "No sig in payload for $peerUserId — legacy device, accepting")
                        }
                    } else {
                        Log.d(TAG, "Supabase verify skipped (offline): ${verifyResult.exceptionOrNull()?.message}")
                    }
                } else {
                    Log.d(TAG, "Identity dedup by installationId=${peerInstId} — Supabase verify skipped")
                }
            } else {
                // No stable identity available (peer is in privacy mode or running an old build).
                // Fall back to MAC-address dedup so an anonymous device can't farm Volts
                // once per rotating-ID window for the entire day.
                val cutoffMs = System.currentTimeMillis() - BleConstants.USER_DEDUP_WINDOW_MS
                if (encounterDao.countLinkedByMacSince(address, cutoffMs) > 0) {
                    Log.i(TAG, "Anonymous device $address already linked within 24h window — skipping duplicate")
                    encounterDao.delete(encounterId)
                    return
                }
            }

            val snapshotId = snapshotDao.insert(
                PeerProfileSnapshot(
                    rotatingId      = rotatingId,
                    displayName     = displayName,
                    greeting        = greeting,
                    avatarKind      = avatarKind,
                    avatarColor     = avatarColor,
                    avatarSeed      = avatarSeed,
                    protocolVersion = version,
                    receivedAt      = ts * 1000,
                    rawJson         = raw,
                    retroUsername   = retroUsername,
                    ghostGame       = ghostGame,
                    ghostScore      = ghostScore,
                    // Store effectiveId so future dedup checks work regardless of
                    // whether Supabase issued a session by then.
                    peerUserId      = effectiveId,
                    peerVoltsTotal  = peerVolts,
                    peerPassesCount = peerPasses,
                    peerBadgesCount = peerBadges,
                    peerStreakCount  = peerStreak,
                )
            )

            encounterDao.linkSnapshot(encounterId, snapshotId)

            // Award 100 Volts for each successful Spark
            profileDao.addVolts(100)

            // Award stickers based on this encounter
            val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
            val totalEncounters = encounterDao.countAll()
            val todayStart = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }.timeInMillis
            val stickerKeys = mutableListOf("first_spark")
            if (retroUsername != null)                       stickerKeys += "player_2"
            if (hour >= 21)                                  stickerKeys += "dusk_patrol"
            if (hour < 8)                                    stickerKeys += "early_bird"
            if (totalEncounters >= 100)                      stickerKeys += "thunder_god"
            else if (totalEncounters >= 50)                  stickerKeys += "marathon"
            if (encounterDao.countSince(todayStart) >= 3)    stickerKeys += "on_fire"
            com.thunderpass.data.StickerManager.award(context, *stickerKeys.toTypedArray())

            Log.i(TAG, "Profile from $rotatingId persisted (snapshot=$snapshotId, encounter=$encounterId, +100J)")
            onProfileReceived?.invoke(encounterId, displayName)

            // Background RetroAchievements fetch + achievement detection
            if (retroUsername != null) {
                scope.launch(Dispatchers.IO) {
                    val ownUsername = profileDao.get()?.retroUsername?.takeIf { it.isNotBlank() }
                    RetroRepository.fetchAndCache(
                        context      = context,
                        peerUsername = retroUsername,
                        snapshotId   = snapshotId,
                        snapshotDao  = snapshotDao,
                        auth         = retroAuth,
                        ownUsername  = ownUsername,
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse/persist GATT payload: ${e.message}")
        }
    }
}
