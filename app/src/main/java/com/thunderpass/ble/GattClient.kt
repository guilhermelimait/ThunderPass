package com.thunderpass.ble

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.util.Log
import com.thunderpass.ble.BleConstants.CCCD_UUID
import com.thunderpass.ble.BleConstants.REQUEST_CHAR_UUID
import com.thunderpass.ble.BleConstants.RESPONSE_CHAR_UUID
import com.thunderpass.ble.BleConstants.THUNDERPASS_SERVICE_UUID
import com.thunderpass.data.db.dao.EncounterDao
import com.thunderpass.data.db.dao.PeerProfileSnapshotDao
import com.thunderpass.data.db.entity.PeerProfileSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val TAG = "ThunderPass/GattClient"

/**
 * GATT client that connects to a discovered ThunderPass device and performs
 * the profile exchange.
 *
 * ### Flow (SPEC.md § GATT Handshake)
 * 1. [connect] is called with the remote [BluetoothDevice].
 * 2. On service discovery, enable notifications on [RESPONSE_CHAR_UUID].
 * 3. Write to [REQUEST_CHAR_UUID] to request the peer's profile.
 * 4. Collect the notification payload from [RESPONSE_CHAR_UUID].
 * 5. Parse the JSON, persist a [PeerProfileSnapshot], and update the encounter.
 * 6. Disconnect.
 */
class GattClient(
    private val context: Context,
    private val encounterDao: EncounterDao,
    private val snapshotDao: PeerProfileSnapshotDao,
    private val scope: CoroutineScope,
    /** Called on the IO dispatcher after a profile exchange succeeds. */
    private val onProfileReceived: ((encounterId: Long, displayName: String) -> Unit)? = null,
) {

    // Track active connections to avoid duplicate GATTs to the same device
    private val activeConnections = mutableSetOf<String>()

    /**
     * Initiates a GATT connection to [device].
     *
     * @param encounterId  The encounter row id to update after a successful exchange.
     */
    fun connect(device: BluetoothDevice, encounterId: Long) {
        val address = device.address
        if (activeConnections.contains(address)) {
            Log.d(TAG, "Already connecting/connected to $address — skip.")
            return
        }
        activeConnections += address
        Log.i(TAG, "Connecting to $address (encounterId=$encounterId)")
        device.connectGatt(context, /* autoConnect= */ false, buildCallback(encounterId))
    }

    // ── GATT Callback ─────────────────────────────────────────────────────────

    private fun buildCallback(encounterId: Long) = object : BluetoothGattCallback() {

        private var gatt: BluetoothGatt? = null

        override fun onConnectionStateChange(
            gatt: BluetoothGatt?, status: Int, newState: Int
        ) {
            this.gatt = gatt
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.d(TAG, "GATT connected to ${gatt?.device?.address}; discovering services…")
                    gatt?.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d(TAG, "GATT disconnected from ${gatt?.device?.address}")
                    activeConnections -= gatt?.device?.address.orEmpty()
                    gatt?.close()
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.w(TAG, "onServicesDiscovered failed: status=$status")
                gatt?.disconnect()
                return
            }

            val service = gatt?.getService(THUNDERPASS_SERVICE_UUID)
            if (service == null) {
                Log.w(TAG, "ThunderPass service not found on ${gatt?.device?.address}")
                gatt?.disconnect()
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
                gatt?.disconnect()
                return
            }

            // Notifications enabled — now write the request
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
            val raw = value.toString(Charsets.UTF_8)
            Log.d(TAG, "RESPONSE received (${raw.length} chars) from ${gatt.device?.address}")

            scope.launch(Dispatchers.IO) {
                parseAndPersist(raw, encounterId)
            }

            // Exchange complete — disconnect
            gatt.disconnect()
        }
    }

    // ── Payload parser ────────────────────────────────────────────────────────

    /**
     * Parses the JSON payload per SPEC.md and stores a [PeerProfileSnapshot],
     * then links it to the [encounter] row.
     */
    private suspend fun parseAndPersist(raw: String, encounterId: Long) {
        try {
            val json     = org.json.JSONObject(raw)
            val version  = json.optInt("v", 0)
            val rotatingId = json.optString("rotatingId", "")
            val ts       = json.optLong("ts", System.currentTimeMillis() / 1000)
            val data     = json.optJSONObject("data") ?: org.json.JSONObject()

            val displayName = data.optString("displayName", "Unknown")
            val greeting    = data.optString("greeting", "")
            val avatar      = data.optJSONObject("avatar") ?: org.json.JSONObject()
            val avatarKind  = avatar.optString("kind", "defaultBolt")
            val avatarColor = avatar.optString("color", "#FFFFFF")

            val snapshotId = snapshotDao.insert(
                PeerProfileSnapshot(
                    rotatingId     = rotatingId,
                    displayName    = displayName,
                    greeting       = greeting,
                    avatarKind     = avatarKind,
                    avatarColor    = avatarColor,
                    protocolVersion = version,
                    receivedAt     = ts * 1000,
                    rawJson        = raw,
                )
            )

            encounterDao.linkSnapshot(encounterId, snapshotId)
            Log.i(TAG, "Profile from $rotatingId persisted (snapshot=$snapshotId, encounter=$encounterId)")
            onProfileReceived?.invoke(encounterId, displayName)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse/persist GATT payload: ${e.message}")
        }
    }
}
