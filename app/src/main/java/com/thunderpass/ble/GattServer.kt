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

    /** Call once from [BleService.onCreate] or before advertising starts. */
    fun start(profileProvider: () -> MyProfile?) {
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
                Log.d(TAG, "Device ${device?.address} → $state")
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

                // Build and send our profile as a notification
                device?.let { sendProfileNotification(it, profileProvider()) }
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

    private fun sendProfileNotification(device: BluetoothDevice, profile: MyProfile?) {
        if (profile == null) {
            Log.w(TAG, "No local profile; skipping GATT notification.")
            return
        }

        val payload = buildPayloadJson(profile)
        val bytes = payload.toByteArray(Charsets.UTF_8)

        val responseChar = gattServer
            ?.getService(THUNDERPASS_SERVICE_UUID)
            ?.getCharacteristic(RESPONSE_CHAR_UUID) ?: return

        // TODO: chunk if bytes.size > negotiated MTU
        val notified = gattServer?.notifyCharacteristicChanged(device, responseChar, false, bytes)
        Log.d(TAG, "Notified ${device.address} with ${bytes.size} bytes (success=$notified)")
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
            })
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
