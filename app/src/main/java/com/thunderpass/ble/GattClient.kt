package com.thunderpass.ble

import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import android.util.Base64
import android.util.Log
import androidx.core.app.NotificationCompat
import com.thunderpass.MainActivity
import com.thunderpass.R
import com.thunderpass.ble.BleConstants
import com.thunderpass.ble.BleConstants.CCCD_UUID
import com.thunderpass.ble.BleConstants.REQUEST_CHAR_UUID
import com.thunderpass.ble.BleConstants.RESPONSE_CHAR_UUID
import com.thunderpass.ble.BleConstants.THUNDERPASS_SERVICE_UUID
import com.thunderpass.data.db.dao.EncounterDao
import com.thunderpass.data.db.dao.MyProfileDao
import com.thunderpass.data.db.dao.PeerProfileSnapshotDao
import com.thunderpass.data.db.entity.PeerProfileSnapshot
import com.thunderpass.ble.BleEncryption
import com.thunderpass.ble.proto.BlePayloadProto
import com.thunderpass.security.DeviceGroupManager
import com.thunderpass.security.PairedSyncManager
import com.thunderpass.security.PayloadSigner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.security.KeyPair
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
 * 7. Parse the payload (JSON or Protobuf), persist a [PeerProfileSnapshot], and update the encounter.
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
    /** Called on the IO dispatcher after a profile exchange succeeds. */
    private val onProfileReceived: ((encounterId: Long, displayName: String) -> Unit)? = null,
) {

    // Tracks addresses that have an active GATT connection attempt in flight.
    private val activeConnections = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    // Per-address timeout jobs — cancelled when connection resolves cleanly.
    // Guards against stuck addresses in activeConnections when a device disappears.
    private val connectionTimeouts = ConcurrentHashMap<String, Job>()

    // Ephemeral ECDH key pairs generated per connect() call; discarded after exchange.
    // One pair per in-flight BLE address — never stored persistently.
    // Deferred allows key gen to run in parallel with BLE connection; resolved KeyPair
    // is stored in ephemeralKeys after await in onDescriptorWrite for use in decryptResponse.
    private val ephemeralKeyDeferreds = ConcurrentHashMap<String, kotlinx.coroutines.Deferred<KeyPair>>()
    private val ephemeralKeys = ConcurrentHashMap<String, KeyPair>()

    // Chunk reassembly buffers keyed by "$deviceAddress:$encounterId".
    // ConcurrentHashMap so concurrent callbacks from multiple simultaneous
    // GATT connections do not race on the same backing map.
    private val chunkBuffers = ConcurrentHashMap<String, Array<ByteArray?>>()

    // BLE addresses that were identified as paired devices via groupTag.
    // Maps address → timestamp of last successful GATT connection to that address.
    // Used by BleService to bypass encounter dedup for paired device reconnections.
    private val knownPairedAddresses = ConcurrentHashMap<String, Long>()

    /** Per-address retry counter for connection failures (e.g. status=62 race). */
    private val retryCount = ConcurrentHashMap<String, Int>()

    // Per-address timeout jobs — cancelled when all chunks have arrived (successful exchange).
    // Guards against the GattClient hanging forever waiting for chunks that never come
    // (e.g. GattServer returns null profile and silently sends nothing).
    private val exchangeTimeouts = ConcurrentHashMap<String, Job>()

    companion object {
        /** Minimum interval between paired-device GATT reconnections (5 min). */
        const val PAIRED_RECONNECT_MS = 5L * 60 * 1000
        /** Max automatic retries when GATT connection establishment fails. */
        private const val MAX_CONNECT_RETRIES = 2
        /** Delay before retrying a failed GATT connection (ms). */
        private const val RETRY_DELAY_MS = 2_500L
    }

    /**
     * Returns true if [address] belongs to a known paired device AND enough time
     * has elapsed since the last GATT connection to warrant a reconnection.
     */
    fun shouldReconnectPaired(address: String): Boolean {
        val lastMs = knownPairedAddresses[address] ?: return false
        return System.currentTimeMillis() - lastMs >= PAIRED_RECONNECT_MS
    }

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
        retryCount[address] = 0
        // Generate ephemeral P-256 key pair in parallel with BLE connection
        // (saves ~20–50ms). Resolved in onDescriptorWrite before building REQUEST.
        ephemeralKeyDeferreds[address] = scope.async(Dispatchers.Default) {
            BleEncryption.generateEphemeralKeyPair()
        }
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
    private fun buildCallback(encounterId: Long): BluetoothGattCallback = object : BluetoothGattCallback() {

        private var gatt: BluetoothGatt? = null
        /** Set to true once we reach STATE_CONNECTED; prevents retrying normal disconnects. */
        private var wasConnected = false

        private fun cleanup(gatt: BluetoothGatt?) {
            val address = gatt?.device?.address.orEmpty()
            activeConnections -= address
            ephemeralKeyDeferreds.remove(address)
            ephemeralKeys.remove(address)
            retryCount.remove(address)
            connectionTimeouts.remove(address)?.cancel()  // timeout no longer needed
            exchangeTimeouts.remove(address)?.cancel()    // exchange timeout no longer needed
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
                    wasConnected = true
                    // Cancel the pre-connection timeout — we're connected; the exchange
                    // timeout below will take over once the REQUEST write completes.
                    connectionTimeouts.remove(address)?.cancel()

                    // 1. Request high connection priority (shorter connection interval).
                    gatt?.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)

                    // 2. Request 2M PHY for faster data transfer (Android 8+). May be
                    // ignored or fail on devices that don't support 2M — safe to call.
                    gatt?.setPreferredPhy(
                        BluetoothDevice.PHY_LE_2M_MASK,
                        BluetoothDevice.PHY_LE_2M_MASK,
                        BluetoothDevice.PHY_OPTION_NO_PREFERRED,
                    )

                    Log.d(TAG, "GATT connected to $address; requesting MTU ${BleConstants.PREFERRED_MTU}, discovering services…")
                    // Pipeline MTU negotiation and service discovery — overlap saves ~15ms on stacks that allow it.
                    gatt?.requestMtu(BleConstants.PREFERRED_MTU)
                    gatt?.discoverServices()
                }

                newState == BluetoothProfile.STATE_DISCONNECTED -> {
                    // If the connection was never established (e.g. status=62
                    // GATT_CONN_FAIL_ESTABLISH from a simultaneous-connect race),
                    // retry automatically after a short delay.
                    if (!wasConnected && status != BluetoothGatt.GATT_SUCCESS) {
                        val device = gatt?.device
                        val attempt = (retryCount[address] ?: 0) + 1
                        if (attempt <= MAX_CONNECT_RETRIES && device != null) {
                            Log.i(TAG, "Connection to $address failed (status=$status), " +
                                "retrying in ${RETRY_DELAY_MS}ms " +
                                "(attempt ${attempt + 1}/${MAX_CONNECT_RETRIES + 1})")
                            retryCount[address] = attempt
                            gatt?.close()
                            // Fresh ephemeral key for the retry session
                            ephemeralKeyDeferreds[address] = scope.async(Dispatchers.Default) {
                                BleEncryption.generateEphemeralKeyPair()
                            }
                            chunkBuffers.remove(bufferKey(address, encounterId))
                            // activeConnections entry stays — we're about to reconnect
                            scope.launch {
                                delay(RETRY_DELAY_MS)
                                Log.d(TAG, "Retrying GATT to $address (attempt ${attempt + 1})")
                                device.connectGatt(
                                    context, false,
                                    buildCallback(encounterId),
                                    BluetoothDevice.TRANSPORT_LE,
                                )
                            }
                            return  // Don't cleanup — retry in progress
                        }
                    }
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
         * Service discovery is already initiated in onConnectionStateChange (pipelined).
         * If the stack serialized and discovery hasn't run yet, it will use the new MTU.
         */
        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            val address = gatt.device?.address.orEmpty()
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "MTU negotiated: $mtu for $address")
            } else {
                Log.w(TAG, "MTU negotiation failed (status=$status) for $address")
            }
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

            // Notifications enabled — await ephemeral key (generated in parallel with connect)
            // then send the encrypted REQUEST. Use scope.launch so we don't block the Binder thread.
            val address = gatt?.device?.address.orEmpty()
            val requestChar = gatt
                ?.getService(THUNDERPASS_SERVICE_UUID)
                ?.getCharacteristic(REQUEST_CHAR_UUID) ?: return

            scope.launch {
                val clientKey = try {
                    ephemeralKeyDeferreds[address]?.await()
                } catch (e: Exception) {
                    Log.w(TAG, "Ephemeral key gen failed for $address: ${e.message}")
                    null
                }
                if (clientKey == null) {
                    Log.w(TAG, "No ephemeral key for $address — aborting")
                    cleanup(gatt)
                    return@launch
                }
                ephemeralKeys[address] = clientKey
                val requestFrame = byteArrayOf(BleEncryption.REQUEST_TYPE_ENCRYPTED) +
                    BleEncryption.encodePublicKey(clientKey.public)
                // WRITE_TYPE_NO_RESPONSE saves ~7–15ms (one ATT round-trip); 92-byte payload fits in one packet.
                gatt?.writeCharacteristic(
                    requestChar,
                    requestFrame,
                    BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                )
                Log.d(TAG, "Encrypted REQUEST sent to $address (${requestFrame.size} bytes, No Response)")
                // Start exchange timeout immediately — with NO_RESPONSE we don't await ATT ack.
                exchangeTimeouts[address] = scope.launch {
                    delay(20_000L)
                    if (activeConnections.contains(address)) {
                        Log.w(TAG, "No response chunks from $address within 20s — disconnecting")
                        gatt?.disconnect()
                    }
                }
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            val address = gatt?.device?.address.orEmpty()
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.w(TAG, "REQUEST write ATT failure for $address (status=$status) — aborting")
                cleanup(gatt)
                return
            }
            // With WRITE_TYPE_NO_RESPONSE the timeout is started in onDescriptorWrite's scope.launch.
            // This callback may still fire (Android signals write queued); log for debugging.
            Log.d(TAG, "REQUEST write confirmed for $address — awaiting chunked response…")
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
                    // Decrypt and authenticate the payload; reject any tampering or wrong key
                    val rawBytes = decryptResponse(full, address) ?: run {
                        Log.w(TAG, "Decryption failed for $address — dropping response")
                        gatt.disconnect()
                        return
                    }
                    scope.launch(Dispatchers.IO) { parseAndPersist(rawBytes, encounterId, address) }
                    gatt.disconnect()
                }
            } else {
                // Unencrypted / non-chunked responses are rejected — no plaintext accepted
                Log.w(TAG, "Non-chunked or unrecognised response from $address — rejected (no plaintext accepted)")
                gatt.disconnect()
            }
        }
    }

    // ── Decryption helper ─────────────────────────────────────────────────────

    /**
     * Decrypts and authenticates an encrypted GATT RESPONSE payload.
     *
     * Expected format:
     *   `[0xE5][server ephemeral pubkey: 91 bytes][AES-GCM IV: 12 bytes][ciphertext + 16-byte tag]`
     *
     * @param full    Reassembled raw bytes from all GATT chunks.
     * @param address Remote device MAC address (used to look up our ephemeral private key).
     * @return Decrypted plaintext bytes, or `null` if verification fails (tampered, wrong key, etc.).
     */
    private fun decryptResponse(full: ByteArray, address: String): ByteArray? {
        if (full.isEmpty() || full[0] != BleEncryption.ENCRYPTED_RESPONSE_MAGIC) {
            Log.w(TAG, "Response from $address missing encrypted magic byte — rejected")
            return null
        }
        if (full.size < BleEncryption.MIN_ENCRYPTED_RESPONSE_SIZE) {
            Log.w(TAG, "Response from $address too short (${full.size} bytes) for encrypted format")
            return null
        }
        val serverPubKey = try {
            BleEncryption.decodePublicKey(full.copyOfRange(1, 1 + BleEncryption.PUBLIC_KEY_BYTES))
        } catch (e: Exception) {
            Log.w(TAG, "Bad server ephemeral pubkey from $address: ${e.message}")
            return null
        }
        val ephemeral = ephemeralKeys[address] ?: run {
            Log.w(TAG, "No ephemeral key for $address — cannot decrypt")
            return null
        }
        val sharedKey = try {
            BleEncryption.deriveSharedKey(ephemeral.private, serverPubKey)
        } catch (e: Exception) {
            Log.w(TAG, "ECDH failed for $address: ${e.message}")
            return null
        }
        val ivAndCt = full.copyOfRange(1 + BleEncryption.PUBLIC_KEY_BYTES, full.size)
        return BleEncryption.decrypt(ivAndCt, sharedKey)
    }

    // ── Payload parser ────────────────────────────────────────────────────────

    /**
     * Dual-format routing: if the decrypted payload starts with `{` (0x7B), route to the
     * legacy JSON parser. Otherwise, parse as Protobuf and store raw bytes as Base64 for
     * future-proofing.
     */
    private suspend fun parseAndPersist(rawBytes: ByteArray, encounterId: Long, address: String) {
        if (rawBytes.isEmpty()) return
        if (rawBytes[0] == 0x7B.toByte()) {
            parseAndPersistJson(String(rawBytes, Charsets.UTF_8), encounterId, address)
        } else {
            parseAndPersistProtobuf(rawBytes, encounterId, address)
        }
    }

    private suspend fun parseAndPersistProtobuf(rawBytes: ByteArray, encounterId: Long, address: String) {
        try {
            val payload = BlePayloadProto.parseFrom(rawBytes)
            val version = payload.v
            val rotatingId = payload.rotatingId
            val ts = payload.ts
            val data = payload.data

            val peerPubKey = payload.pubKey
            val peerSig = payload.sig

            when {
                peerPubKey.isBlank() ->
                    Log.d(TAG, "No pubKey from $address — accepting as legacy (unverified)")
                peerSig.isBlank() ->
                    Log.w(TAG, "Empty sig from $address despite pubKey present — accepting unverified (possible Keystore error on sender)")
                else -> {
                    val authMessage = PayloadSigner.signedPayload(rotatingId, ts)
                    if (!PayloadSigner.verify(authMessage, peerSig, peerPubKey)) {
                        Log.w(TAG, "Signature verification FAILED for $address — dropping payload (possible spoofing)")
                        return
                    }
                    Log.d(TAG, "Signature verified for $address")
                }
            }

            val peerGroupTag = if (data.hasGroupTag()) data.groupTag else ""
            val peerInstIdGT = if (data.hasInstId()) data.instId else ""

            if (peerGroupTag.isNotBlank() && peerInstIdGT.isNotBlank() &&
                DeviceGroupManager.isOwnDevice(context, peerInstIdGT, peerGroupTag)) {

                val peerName = data.displayName.ifBlank { "Paired Device" }
                val peerDevType = if (data.hasDeviceType()) data.deviceType else ""
                val willSync = PairedSyncManager.shouldSync(context, peerInstIdGT)

                Log.i(TAG, "Own paired device detected via groupTag ($peerInstIdGT) — willSync=$willSync")

                knownPairedAddresses[address] = System.currentTimeMillis()

                NearbyDeviceState.onPairedDeviceSeen(
                    displayName = peerName,
                    deviceType = peerDevType,
                    instId = peerInstIdGT,
                    synced = willSync,
                )

                PairedSyncManager.recordPairedDevice(context, peerInstIdGT, peerName, peerDevType)
                showPairedDeviceNotification(peerName, willSync)

                if (willSync) {
                    // Bridge format: map ProfileDataProto to JSONObject until PairedSyncManager
                    // is refactored to accept ProfileDataProto directly.
                    val jsonForSync = org.json.JSONObject().apply {
                        put("displayName", data.displayName)
                        put("greeting", data.greeting)
                        put("avatar", org.json.JSONObject().apply {
                            put("kind", data.avatar.kind)
                            put("color", data.avatar.color)
                            if (data.avatar.hasSeed()) put("seed", data.avatar.seed)
                        })
                        if (data.hasUpdatedAt()) put("updatedAt", data.updatedAt)
                        put("volts", data.volts)
                        if (data.hasRetroUsername()) put("retroUsername", data.retroUsername)
                        if (data.hasGhostGame()) put("ghostGame", data.ghostGame)
                        if (data.hasGhostScore()) put("ghostScore", data.ghostScore)
                        if (data.hasCountry()) put("country", data.country)
                        if (data.hasCity()) put("city", data.city)
                        if (data.hasSyncBadges()) put("syncBadges", data.syncBadges)
                        if (data.hasSyncStickers()) put("syncStickers", data.syncStickers)
                        if (data.hasSyncPrivacyMode()) put("syncPrivacyMode", data.syncPrivacyMode)
                    }
                    scope.launch { PairedSyncManager.mergeIncomingProfile(context, profileDao, peerInstIdGT, jsonForSync) }
                }

                val ownDeviceGreeting = peerDevType.ifBlank { "Your paired device" }
                val rawBase64 = Base64.encodeToString(rawBytes, Base64.NO_WRAP)
                val ownSnapshot = PeerProfileSnapshot(
                    rotatingId = rotatingId,
                    displayName = peerName.ifBlank { "Paired Device" },
                    greeting = ownDeviceGreeting,
                    avatarKind = "own_device",
                    avatarColor = "#FFFFFF",
                    avatarSeed = peerInstIdGT,
                    protocolVersion = version,
                    receivedAt = System.currentTimeMillis(),
                    rawJson = rawBase64,
                    peerInstId = peerInstIdGT,
                )
                val snapshotId = snapshotDao.insert(ownSnapshot)
                encounterDao.linkSnapshot(encounterId, snapshotId)
                encounterDao.setFriend(encounterId, true)
                Log.i(TAG, "Own device added as friend (encounterId=$encounterId snapshotId=$snapshotId)")
                return
            }

            val displayName = data.displayName.ifBlank { "Unknown" }
            val greeting = data.greeting
            val avatarKind = data.avatar.kind.ifBlank { "defaultBolt" }
            val avatarColor = data.avatar.color.ifBlank { "#FFFFFF" }
            val avatarSeed = if (data.avatar.hasSeed()) data.avatar.seed else null
            val retroUsername = if (data.hasRetroUsername()) data.retroUsername else null
            val ghostGame = if (data.hasGhostGame()) data.ghostGame else null
            val ghostScore = if (data.hasGhostScore()) data.ghostScore else null

            val peerVolts = if (data.volts >= 0L) data.volts else null
            val peerPasses = if (data.hasPasses() && data.passes >= 0) data.passes else null
            val peerBadges = if (data.hasBadges() && data.badges >= 0) data.badges else null
            val peerStreak = if (data.hasStreak() && data.streak >= 0) data.streak else null
            val peerCountry = if (data.hasCountry() && data.country.isNotBlank()) data.country else null
            val peerCity = if (data.hasCity() && data.city.isNotBlank()) data.city else null

            val peerInstId = if (data.hasInstId() && data.instId.isNotBlank()) data.instId else null
            val effectiveId = peerInstId

            val rawBase64 = Base64.encodeToString(rawBytes, Base64.NO_WRAP)

            if (effectiveId != null) {
                val cutoffMs = System.currentTimeMillis() - BleConstants.USER_DEDUP_WINDOW_MS
                if (snapshotDao.countByInstIdSince(effectiveId, cutoffMs) > 0) {
                    Log.i(TAG, "User $effectiveId already encountered within 24h window — refreshing profile data only")
                    val existingId = snapshotDao.latestIdByInstIdSince(effectiveId, cutoffMs)
                    if (existingId != null) {
                        snapshotDao.updateProfileData(
                            id = existingId,
                            displayName = displayName,
                            greeting = greeting,
                            avatarKind = avatarKind,
                            avatarColor = avatarColor,
                            avatarSeed = avatarSeed,
                            retroUsername = retroUsername,
                            ghostGame = ghostGame,
                            ghostScore = ghostScore,
                            peerVoltsTotal = peerVolts,
                            peerPassesCount = peerPasses,
                            peerBadgesCount = peerBadges,
                            peerStreakCount = peerStreak,
                            peerCountry = peerCountry,
                            peerCity = peerCity,
                            rawJson = rawBase64,
                        )
                        Log.i(TAG, "Profile refreshed for $effectiveId (snapshot=$existingId)")
                    }
                    return
                }
            } else {
                val cutoffMs = System.currentTimeMillis() - BleConstants.USER_DEDUP_WINDOW_MS
                if (encounterDao.countLinkedByMacSince(address, cutoffMs) > 0) {
                    Log.i(TAG, "Anonymous device $address already linked within 24h window — skipping duplicate")
                    return
                }
            }

            val snapshotId = snapshotDao.insert(
                PeerProfileSnapshot(
                    rotatingId = rotatingId,
                    displayName = displayName,
                    greeting = greeting,
                    avatarKind = avatarKind,
                    avatarColor = avatarColor,
                    avatarSeed = avatarSeed,
                    protocolVersion = version,
                    receivedAt = ts * 1000,
                    rawJson = rawBase64,
                    retroUsername = retroUsername,
                    ghostGame = ghostGame,
                    ghostScore = ghostScore,
                    peerInstId = effectiveId,
                    peerVoltsTotal = peerVolts,
                    peerPassesCount = peerPasses,
                    peerBadgesCount = peerBadges,
                    peerStreakCount = peerStreak,
                    peerCountry = peerCountry,
                    peerCity = peerCity,
                )
            )

            encounterDao.linkSnapshot(encounterId, snapshotId)
            profileDao.addVolts(100)

            val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
            val totalEncounters = encounterDao.countAll()
            val todayStart = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }.timeInMillis

            val stickerKeys = mutableListOf("first_spark")
            if (retroUsername != null) stickerKeys += "player_2"
            if (hour >= 21) stickerKeys += "dusk_patrol"
            if (hour < 8) stickerKeys += "early_bird"
            if (totalEncounters >= 100) stickerKeys += "thunder_god"
            else if (totalEncounters >= 50) stickerKeys += "marathon"
            if (encounterDao.countSince(todayStart) >= 3) stickerKeys += "on_fire"
            com.thunderpass.data.StickerManager.award(context, *stickerKeys.toTypedArray())

            Log.i(TAG, "Profile from $rotatingId persisted (snapshot=$snapshotId, encounter=$encounterId, +100J)")
            onProfileReceived?.invoke(encounterId, displayName)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse/persist Protobuf payload: ${e.message}")
        }
    }

    /**
     * Parses the legacy JSON payload per SPEC.md and stores a [PeerProfileSnapshot],
     * then links it to the [encounter] row.
     *
     * @param address The BLE MAC address of the remote device (stable within an advertising
     *                session). Used as a fallback dedup key when the peer is in privacy mode
     *                and no stable [effectiveId] is available.
     */
    private suspend fun parseAndPersistJson(raw: String, encounterId: Long, address: String) {
        try {
            val json       = org.json.JSONObject(raw)
            val version    = json.optInt("v", 0)
            val rotatingId = json.optString("rotatingId", "")
            val ts         = json.optLong("ts", System.currentTimeMillis() / 1000)
            val data       = json.optJSONObject("data") ?: org.json.JSONObject()

            // ── Mutual authentication (best-effort per AGENTS.md) ──────────────────
            // Devices with a Keystore key sign their payload; verify when possible.
            // Legacy or older-build devices may have no key at all — accepted unverified.
            // If signing failed on the sender (Keystore error) sig may be blank — warn
            // but accept so a Keystore hiccup doesn't silently kill all exchanges.
            val peerPubKey = json.optString("pubKey", "")
            val peerSig    = json.optString("sig", "")
            when {
                peerPubKey.isBlank() ->
                    // No key at all — legacy device or old build; accept unverified.
                    Log.d(TAG, "No pubKey from $address — accepting as legacy (unverified)")
                peerSig.isBlank() ->
                    // Has a key but signing failed on the sender (Keystore error?).
                    Log.w(TAG, "Empty sig from $address despite pubKey present — accepting unverified (possible Keystore error on sender)")
                else -> {
                    val authMessage = PayloadSigner.signedPayload(rotatingId, ts)
                    if (!PayloadSigner.verify(authMessage, peerSig, peerPubKey)) {
                        Log.w(TAG, "Signature verification FAILED for $address — dropping payload (possible spoofing)")
                        return
                    }
                    Log.d(TAG, "Signature verified for $address")
                }
            }

            // ── Own device detection (Device Group) ───────────────────────────
            // If we share a DGK with this peer and the groupTag authenticates,
            // it's one of our own paired devices — skip encounter creation.
            val peerGroupTag = data.optString("groupTag", "")
            val peerInstIdGT = data.optString("instId", "")
            if (peerGroupTag.isNotBlank() && peerInstIdGT.isNotBlank() &&
                DeviceGroupManager.isOwnDevice(context, peerInstIdGT, peerGroupTag)) {
                val peerName      = data.optString("displayName", "Paired Device")
                val peerDevType   = data.optString("deviceType", "")
                val willSync      = PairedSyncManager.shouldSync(context, peerInstIdGT)

                Log.i(TAG, "Own paired device detected via groupTag ($peerInstIdGT) — willSync=$willSync")

                // Remember this BLE address as belonging to a paired device
                // so BleService can bypass encounter dedup for reconnections.
                knownPairedAddresses[address] = System.currentTimeMillis()

                // Emit state for DeviceSyncScreen
                NearbyDeviceState.onPairedDeviceSeen(
                    displayName = peerName,
                    deviceType  = peerDevType,
                    instId      = peerInstIdGT,
                    synced      = willSync,
                )

                // Persist paired device metadata for the device list UI
                PairedSyncManager.recordPairedDevice(context, peerInstIdGT, peerName, peerDevType)

                // Show system notification
                showPairedDeviceNotification(peerName, willSync)

                if (willSync) {
                    scope.launch { PairedSyncManager.mergeIncomingProfile(context, profileDao, peerInstIdGT, data) }
                }

                // ── Auto-add own device as a friend ────────────────────────────────
                // Create / refresh a PeerProfileSnapshot tagged as "own_device" so
                // the Friends list shows this device with a game-controller icon.
                val ownDeviceGreeting = if (peerDevType.isNotBlank()) peerDevType else "Your paired device"
                val ownSnapshot = PeerProfileSnapshot(
                    rotatingId       = rotatingId,
                    displayName      = peerName.ifBlank { "Paired Device" },
                    greeting         = ownDeviceGreeting,
                    avatarKind       = "own_device",   // sentinel — renders controller icon in UI
                    avatarColor      = "#FFFFFF",
                    avatarSeed       = peerInstIdGT,
                    protocolVersion  = version,
                    receivedAt       = System.currentTimeMillis(),
                    rawJson          = raw,
                    peerInstId       = peerInstIdGT,
                )
                val snapshotId = snapshotDao.insert(ownSnapshot)
                encounterDao.linkSnapshot(encounterId, snapshotId)
                encounterDao.setFriend(encounterId, true)
                Log.i(TAG, "Own device added as friend (encounterId=$encounterId snapshotId=$snapshotId)")
                return
            }

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
            val peerCountry = data.optString("country", "").takeIf { it.isNotBlank() }
            val peerCity    = data.optString("city",    "").takeIf { it.isNotBlank() }

            // Stable peer identifier for 24-hour identity dedup (local only).
            val peerInstId  = data.optString("instId", "").takeIf { it.isNotBlank() }
            val effectiveId = peerInstId

            // ── 24-hour identity dedup (local) ─────────────────────────────────────
            // Rotating IDs change every 60 min, so scan-level dedup alone can't
            // prevent the same user earning multiple Sparks within the hour.
            // Use installationId as the stable dedup key — never sent to any server.
            if (effectiveId != null) {
                val cutoffMs = System.currentTimeMillis() - BleConstants.USER_DEDUP_WINDOW_MS
                if (snapshotDao.countByInstIdSince(effectiveId, cutoffMs) > 0) {
                    Log.i(TAG, "User $effectiveId already encountered within 24h window — refreshing profile data only")
                    // Refresh the existing snapshot with the latest profile data so the
                    // friend card always shows the peer's current name, avatar, and stats.
                    // Volts are NOT re-awarded and the encounter's timestamp is NOT moved.
                    val existingId = snapshotDao.latestIdByInstIdSince(effectiveId, cutoffMs)
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
                            peerCountry    = peerCountry,
                            peerCity       = peerCity,
                            rawJson        = raw,
                        )
                        // If retro username changed or fetch hasn't been done yet,
                        // profile refresh is still persisted; no internet fetch needed.
                        Log.i(TAG, "Profile refreshed for $effectiveId (snapshot=$existingId)")
                    }
                    // Keep the encounter row intact — its seenAt (= now) acts as a
                    // dedup sentinel so EncounterDedup.lastSeenAt() returns a fresh
                    // timestamp and blocks new GATT connections for the next 60 min.
                    // Deleting it would reset lastSeenAt to the original encounter,
                    // causing a rapid-fire GATT reconnect loop that can bypass the
                    // 24h check and re-trigger vibration.
                    return
                }
            } else {
                // No stable identity available (peer is in privacy mode or running an old build).
                // Fall back to MAC-address dedup so an anonymous device can't farm Volts
                // once per rotating-ID window for the entire day.
                val cutoffMs = System.currentTimeMillis() - BleConstants.USER_DEDUP_WINDOW_MS
                if (encounterDao.countLinkedByMacSince(address, cutoffMs) > 0) {
                    Log.i(TAG, "Anonymous device $address already linked within 24h window — skipping duplicate")
                    // Keep the encounter row for the same reason as the identity dedup
                    // above: its seenAt timestamp gates the 60-min EncounterDedup window.
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
                    peerInstId      = effectiveId,
                    peerVoltsTotal  = peerVolts,
                    peerPassesCount = peerPasses,
                    peerBadgesCount = peerBadges,
                    peerStreakCount  = peerStreak,
                    peerCountry      = peerCountry,
                    peerCity         = peerCity,
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
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse/persist GATT payload: ${e.message}")
        }
    }

    private fun showPairedDeviceNotification(peerName: String, syncing: Boolean) {
        val title = "🔗 $peerName nearby"
        val text = if (syncing) "Syncing your data…" else "Devices are in sync"
        val tapIntent = PendingIntent.getActivity(
            context, BleConstants.PAIRED_SYNC_NOTIF_ID,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notif = NotificationCompat.Builder(context, BleConstants.PAIRED_SYNC_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(tapIntent)
            .setAutoCancel(true)
            .build()
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)
            ?.notify(BleConstants.PAIRED_SYNC_NOTIF_ID, notif)
    }
}
