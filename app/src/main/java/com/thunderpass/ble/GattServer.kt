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
import com.thunderpass.ble.BleConstants.CCCD_UUID
import com.thunderpass.ble.BleConstants.REQUEST_CHAR_UUID
import com.thunderpass.ble.BleConstants.RESPONSE_CHAR_UUID
import com.thunderpass.ble.BleConstants.THUNDERPASS_SERVICE_UUID
import com.thunderpass.data.db.entity.MyProfile

private const val TAG = "ThunderPass/GattServer"

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

    /** Negotiated MTU per remote device address (set in [onMtuChanged]). */
    private val deviceMtu = mutableMapOf<String, Int>()

    /** Call once from [BleService.onCreate] or before advertising starts. */
    fun start(profileProvider: () -> MyProfile?) {
        if (gattServer != null) {
            Log.w(TAG, "GATT server already running — ignoring duplicate start().")
            return
        }
        val btManager = context.getSystemService(BluetoothManager::class.java) ?: return

        gattServer = btManager.openGattServer(context, buildCallback(profileProvider))
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

        // RESPONSE characteristic: Notify + Read
        val responseChar = BluetoothGattCharacteristic(
            RESPONSE_CHAR_UUID,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY or
                    BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
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

    private fun buildCallback(profileProvider: () -> MyProfile?) =
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
                Log.d(TAG, "REQUEST_CHAR written by ${device?.address}")

                // Acknowledge the write
                if (responseNeeded) {
                    gattServer?.sendResponse(
                        device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null
                    )
                }

                // Build and send our profile as (possibly chunked) notifications
                device?.let { dev ->
                    val mtu = deviceMtu[dev.address] ?: BleConstants.DEFAULT_MTU
                    sendProfileNotification(dev, profileProvider(), mtu)
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
     * Sends the local profile to [device] as one or more chunked GATT notifications.
     *
     * Chunk format: [CHUNK_MAGIC:1][totalChunks:1][chunkIndex:1][data…]
     * The magic byte (0xCA) never appears as the first byte of valid JSON, so the
     * client can distinguish chunked vs. legacy single-notification payloads.
     *
     * @param mtu  The negotiated ATT MTU for this device. Each notification payload must
     *             be ≤ (mtu - 3) bytes (3 bytes for the ATT op-code + handle overhead).
     */
    private fun sendProfileNotification(device: BluetoothDevice, profile: MyProfile?, mtu: Int) {
        if (profile == null) {
            Log.w(TAG, "No local profile; skipping GATT notification.")
            return
        }

        val responseChar = gattServer
            ?.getService(THUNDERPASS_SERVICE_UUID)
            ?.getCharacteristic(RESPONSE_CHAR_UUID) ?: return

        val payload = buildPayloadJson(profile).toByteArray(Charsets.UTF_8)

        // ATT notification overhead: 3 bytes (opcode 1 + handle 2).
        // Chunk header: 3 bytes (magic 1 + totalChunks 1 + chunkIndex 1).
        val maxDataPerChunk = maxOf(1, mtu - 3 - 3)

        val chunks = payload.toList().chunked(maxDataPerChunk) { it.toByteArray() }
        val totalChunks = chunks.size.coerceAtMost(255)  // uint8 cap — profiles won't exceed this

        Log.d(TAG, "Sending ${payload.size} bytes to ${device.address} in $totalChunks chunk(s) (mtu=$mtu)")

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
    private fun buildPayloadJson(profile: MyProfile): String {
        val data = org.json.JSONObject().apply {
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
        }
        return org.json.JSONObject().apply {
            put("v", BleConstants.PROTOCOL_VERSION)
            put("type", "profile")
            put("rotatingId", rotatingIdManager.currentRotatingId())
            put("ts", System.currentTimeMillis() / 1000)
            put("data", data)
        }.toString()
    }
}
