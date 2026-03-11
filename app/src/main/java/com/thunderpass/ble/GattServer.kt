package com.thunderpass.ble

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import java.security.KeyPair
import javax.crypto.SecretKey
import com.thunderpass.ble.BleConstants.CCCD_UUID
import com.thunderpass.ble.BleConstants.REQUEST_CHAR_UUID
import com.thunderpass.ble.BleConstants.RESPONSE_CHAR_UUID
import com.thunderpass.ble.BleConstants.THUNDERPASS_SERVICE_UUID
import com.thunderpass.data.db.entity.MyProfile
import com.thunderpass.security.DeviceGroupManager
import com.thunderpass.security.PayloadSigner

private const val TAG = "ThunderPass/GattServer"

/**
 * Supplementary stats included in the BLE payload when the user is not in privacy mode.
 * Sourced at exchange time from the local DB by [BleService].
 */
data class BleStats(
    val passesCount: Int,
    val badgesCount: Int,
    val streakCount: Int,
)

/**
 * GATT server that accepts incoming profile-exchange requests.
 *
 * ### Flow (SPEC.md § GATT Handshake)
 * 1. Remote client connects and enables notifications on [RESPONSE_CHAR_UUID].
 * 2. Client writes to [REQUEST_CHAR_UUID] to signal it wants a profile.
 * 3. Server notifies [RESPONSE_CHAR_UUID] with the JSON payload.
 *    (MVP: single notification; chunking is a TODO for payloads > MTU.)
 */
class GattServer(
    private val context: Context,
    private val rotatingIdManager: RotatingIdManager,
) {

    private var gattServer: BluetoothGattServer? = null

    /** Negotiated MTU per remote device address (set in [onMtuChanged]).
     *  ConcurrentHashMap because [onMtuChanged] and [onConnectionStateChange] can
     *  be invoked simultaneously for different devices on different Binder threads.
     */
    private val deviceMtu = ConcurrentHashMap<String, Int>()

    /** Call once from [BleService.onCreate] or before advertising starts. */
    fun start(profileProvider: () -> MyProfile?, statsProvider: () -> BleStats? = { null }) {
        if (gattServer != null) {
            Log.w(TAG, "GATT server already running — ignoring duplicate start().")
            return
        }
        val btManager = context.getSystemService(BluetoothManager::class.java) ?: return

        gattServer = btManager.openGattServer(context, buildCallback(profileProvider, statsProvider))
            ?.also { server ->
                server.addService(buildService())
                Log.i(TAG, "GATT server started.")
            }
    }

    /** Call from [BleService.onDestroy]. */
    fun stop() {
        gattServer?.close()
        gattServer = null
        deviceMtu.clear()
        Log.i(TAG, "GATT server stopped.")
    }

    // ── Service definition ────────────────────────────────────────────────────

    private fun buildService(): BluetoothGattService {
        val service = BluetoothGattService(
            THUNDERPASS_SERVICE_UUID,
            BluetoothGattService.SERVICE_TYPE_PRIMARY
        )

        // REQUEST characteristic: Write only
        val requestChar = BluetoothGattCharacteristic(
            REQUEST_CHAR_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE or
                    BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )

        // RESPONSE characteristic: Notify only — direct reads are not permitted.
        // Data flows exclusively as encrypted notifications; no ciphertext should be
        // retrievable by a passive BLE scanner via a plain ATT Read request.
        val responseChar = BluetoothGattCharacteristic(
            RESPONSE_CHAR_UUID,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            0 // No ATT read permission — notifications only
        ).also { char ->
            // CCCD descriptor — required for the client to enable notifications
            char.addDescriptor(
                BluetoothGattDescriptor(
                    CCCD_UUID,
                    BluetoothGattDescriptor.PERMISSION_READ or
                            BluetoothGattDescriptor.PERMISSION_WRITE
                )
            )
        }

        service.addCharacteristic(requestChar)
        service.addCharacteristic(responseChar)
        return service
    }

    // ── Callback ──────────────────────────────────────────────────────────────

    private fun buildCallback(profileProvider: () -> MyProfile?, statsProvider: () -> BleStats?) =
        object : BluetoothGattServerCallback() {

            override fun onConnectionStateChange(
                device: BluetoothDevice?, status: Int, newState: Int
            ) {
                val state = if (newState == BluetoothProfile.STATE_CONNECTED) "CONNECTED" else "DISCONNECTED"
                Log.d(TAG, "Device ${device?.address} → $state (status=$status)")
                if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    deviceMtu.remove(device?.address)
                }
            }

            /** Track the MTU that the client negotiated so we chunk correctly. */
            override fun onMtuChanged(device: BluetoothDevice?, mtu: Int) {
                device?.address?.let {
                    deviceMtu[it] = mtu
                    Log.d(TAG, "Server MTU updated for $it: $mtu")
                }
            }

            override fun onCharacteristicWriteRequest(
                device: BluetoothDevice?,
                requestId: Int,
                characteristic: BluetoothGattCharacteristic?,
                preparedWrite: Boolean,
                responseNeeded: Boolean,
                offset: Int,
                value: ByteArray?
            ) {
                if (characteristic?.uuid != REQUEST_CHAR_UUID) return

                // Require encrypted request frame: [0x02][91-byte X.509 P-256 client ephemeral pubkey]
                val frame = value
                if (frame == null ||
                    frame.size != BleEncryption.REQUEST_FRAME_SIZE ||
                    frame[0] != BleEncryption.REQUEST_TYPE_ENCRYPTED
                ) {
                    Log.w(TAG, "Unencrypted or malformed REQUEST from ${device?.address} " +
                        "(size=${frame?.size ?: 0}) — rejected (no plaintext accepted)")
                    if (responseNeeded) {
                        gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null)
                    }
                    return
                }

                // Acknowledge the write
                if (responseNeeded) {
                    gattServer?.sendResponse(
                        device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null
                    )
                }

                Log.d(TAG, "Encrypted REQUEST received from ${device?.address}")

                // Parse client ephemeral public key and perform ECDH key agreement
                val clientPubKey = try {
                    BleEncryption.decodePublicKey(frame.copyOfRange(1, 1 + BleEncryption.PUBLIC_KEY_BYTES))
                } catch (e: Exception) {
                    Log.w(TAG, "Malformed client pubkey from ${device?.address}: ${e.message}")
                    return
                }

                val serverEphemeral = BleEncryption.generateEphemeralKeyPair()
                val sharedKey       = BleEncryption.deriveSharedKey(serverEphemeral.private, clientPubKey)

                device?.let { dev ->
                    val mtu = deviceMtu[dev.address] ?: BleConstants.DEFAULT_MTU
                    sendEncryptedProfileNotification(dev, profileProvider(), statsProvider(), mtu, serverEphemeral, sharedKey)
                }
            }

            override fun onDescriptorWriteRequest(
                device: BluetoothDevice?,
                requestId: Int,
                descriptor: BluetoothGattDescriptor?,
                preparedWrite: Boolean,
                responseNeeded: Boolean,
                offset: Int,
                value: ByteArray?
            ) {
                if (responseNeeded) {
                    gattServer?.sendResponse(
                        device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null
                    )
                }
                Log.d(TAG, "CCCD written by ${device?.address} → notifications enabled")
            }
        }

    // ── Payload builder ───────────────────────────────────────────────────────

    /**
     * Encrypts the local profile payload with AES-256-GCM using the session [sharedKey] and
     * sends it to [device] as one or more chunked GATT notifications.
     *
     * Wire format (before chunking):
     *   `[0xE5][server ephemeral pubkey: 91 bytes][IV: 12 bytes][AES-GCM ciphertext + 16-byte tag]`
     *
     * Chunk format: `[CHUNK_MAGIC: 1][totalChunks: 1][chunkIndex: 1][data…]`
     *
     * @param mtu             Negotiated ATT MTU; each packet must be ≤ (mtu − 3) bytes.
     * @param serverEphemeral Server's ephemeral key pair for this session.
     * @param sharedKey       256-bit AES session key derived from ECDH.
     */
    private fun sendEncryptedProfileNotification(
        device: BluetoothDevice,
        profile: MyProfile?,
        stats: BleStats?,
        mtu: Int,
        serverEphemeral: KeyPair,
        sharedKey: SecretKey,
    ) {
        if (profile == null) {
            Log.w(TAG, "No local profile \u2014 disconnecting client ${device.address} so it doesn't hang.")
            gattServer?.cancelConnection(device)
            return
        }

        val responseChar = gattServer
            ?.getService(THUNDERPASS_SERVICE_UUID)
            ?.getCharacteristic(RESPONSE_CHAR_UUID) ?: return

        val payload = BleEncryption.buildEncryptedResponse(
            serverEphemeral = serverEphemeral,
            sharedKey       = sharedKey,
            json            = buildPayloadJson(profile, stats),
        )

        // ATT notification overhead: 3 bytes (opcode 1 + handle 2).
        // Chunk header: 3 bytes (magic 1 + totalChunks 1 + chunkIndex 1).
        val maxDataPerChunk = maxOf(1, mtu - 3 - 3)

        val chunks = payload.toList().chunked(maxDataPerChunk) { it.toByteArray() }
        val totalChunks = chunks.size.coerceAtMost(255)  // uint8 cap

        Log.d(TAG, "Sending ${payload.size} encrypted bytes to ${device.address} in $totalChunks chunk(s) (mtu=$mtu)")

        chunks.forEachIndexed { index, data ->
            if (index >= 255) return@forEachIndexed  // safety guard
            val packet = byteArrayOf(
                BleConstants.CHUNK_MAGIC,
                totalChunks.toByte(),
                index.toByte(),
            ) + data
            val ok = gattServer?.notifyCharacteristicChanged(device, responseChar, false, packet)
            Log.v(TAG, "  chunk $index/$totalChunks → ${packet.size} bytes sent=$ok")
        }
    }

    /**
     * Builds the JSON exchange payload per SPEC.md § Exchange Layer.
     *
     * ```json
     * {
     *   "v": 1,
     *   "type": "profile",
     *   "rotatingId": "base64...",
     *   "ts": 1710000000,
     *   "data": { "displayName": "Gui", "greeting": "Hey!", "avatar": {...} }
     * }
     * ```
     */
    private fun buildPayloadJson(profile: MyProfile, stats: BleStats?): String {
        // Privacy mode: share name, greeting, avatar, and stats — but hide identity
        // (retroUsername, userId, instId, deviceType, ghostGame, sig) so the peer
        // can display the card without being able to track or verify who they met.
        val data = if (profile.privacyMode) {
            org.json.JSONObject().apply {
                put("displayName", profile.displayName)
                put("greeting", profile.greeting)
                put("avatar", org.json.JSONObject().apply {
                    put("kind", profile.avatarKind)
                    put("color", profile.avatarColor)
                    if (profile.avatarSeed.isNotBlank()) put("seed", profile.avatarSeed)
                })
                put("private", true)
                // Volts + encounter stats — visible even in privacy mode
                put("volts", profile.voltsTotal)
                if (stats != null) {
                    put("passes", stats.passesCount)
                    put("badges", stats.badgesCount)
                    put("streak", stats.streakCount)
                }
                // retroUsername, ghostGame, userId, instId, deviceType, sig intentionally omitted
            }
        } else org.json.JSONObject().apply {
            put("displayName", profile.displayName)
            put("greeting", profile.greeting)
            put("avatar", org.json.JSONObject().apply {
                put("kind", profile.avatarKind)
                put("color", profile.avatarColor)
                // Include DiceBear seed so peers can render the same avatar
                if (profile.avatarSeed.isNotBlank()) put("seed", profile.avatarSeed)
            })
            // Include RetroAchievements username if the user has set one
            if (profile.retroUsername.isNotBlank()) {
                put("retroUsername", profile.retroUsername)
            }
            if (profile.ghostGame.isNotBlank()) {
                put("ghostGame", profile.ghostGame)
                if (profile.ghostScore > 0L) put("ghostScore", profile.ghostScore)
            }
            // Always include the stable installationId as the identity dedup key.
            // Peers use this to perform 24-hour dedup so nearby devices don't
            // accumulate duplicate Sparks on every rotating-ID window change.
            if (profile.installationId.isNotBlank()) {
                put("instId", profile.installationId)
            }
            // Include detected device type (e.g. "AYN Thor 2", "Retroid Pocket 4 Pro")
            if (profile.deviceType.isNotBlank()) {
                put("deviceType", profile.deviceType)
            }
            // Country + city — visible to all encounters when privacy is off
            if (profile.country.isNotBlank()) put("country", profile.country)
            if (profile.city.isNotBlank()) put("city", profile.city)
            // Include Volts + encounter stats so peers can display them
            put("volts", profile.voltsTotal)
            if (stats != null) {
                put("passes", stats.passesCount)
                put("badges", stats.badgesCount)
                put("streak", stats.streakCount)
            }
        }

        // ── Paired-device delta sync — always appended regardless of privacy mode ──
        // Paired devices authenticate via HMAC(DGK, instId); strangers without
        // the DGK cannot verify or use the groupTag.  Including instId here for
        // the HMAC check is a minimal privacy trade-off (SHA-256 derivative, inside
        // AES-256-GCM payload) that enables country, city, and all profile fields
        // to stay in sync across paired devices even when privacy mode is on.
        val groupTag = DeviceGroupManager.computeGroupTag(context, profile.installationId)
        if (groupTag.isNotBlank()) {
            data.put("instId", profile.installationId)
            data.put("groupTag", groupTag)
            // Extra fields for periodic paired-device delta sync (PairedSyncManager).
            // The receiving GattClient verifies the groupTag before consuming these.
            // NOTE: credentials (raApiKey) are intentionally excluded — they are only
            // transferred during the explicit SAS-verified SyncGattServer flow.
            data.put("updatedAt", profile.updatedAt)
            if (profile.badgesJson.isNotBlank())   data.put("syncBadges",   profile.badgesJson)
            if (profile.stickersJson.isNotBlank()) data.put("syncStickers", profile.stickersJson)
            data.put("syncPrivacyMode", profile.privacyMode)
            // Country + city — always synced to paired devices
            if (profile.country.isNotBlank()) data.put("country", profile.country)
            if (profile.city.isNotBlank())    data.put("city",    profile.city)
        }
        val rotatingId = rotatingIdManager.currentRotatingId()
        val ts         = System.currentTimeMillis() / 1000
        // Authenticate this payload: sign the rotatingId + time window with the
        // device-bound Keystore key so peers can verify origin without breaking privacy.
        val pubKey = PayloadSigner.ensureKeyPairAndGetPublicKey()
        val sig    = PayloadSigner.sign(PayloadSigner.signedPayload(rotatingId, ts)) ?: ""
        return org.json.JSONObject().apply {
            put("v", BleConstants.PROTOCOL_VERSION)
            put("type", "profile")
            put("rotatingId", rotatingId)
            put("ts", ts)
            put("pubKey", pubKey)
            put("sig", sig)
            put("data", data)
        }.toString()
    }
}
