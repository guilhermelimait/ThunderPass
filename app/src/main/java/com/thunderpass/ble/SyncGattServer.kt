package com.thunderpass.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.util.Log
import com.thunderpass.data.db.dao.MyProfileDao
import com.thunderpass.security.DeviceGroupManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "ThunderPass/SyncGattSrv"

/**
 * GATT server that exports the local profile to a secondary owned device
 * via a two-step SAS (Short Authentication String) verification flow.
 *
 * ## Protocol (v2 — SAS numeric comparison)
 * ```
 * Secondary (client)                    Primary (server — this class)
 * ──────────────────────────────────────────────────────────────────
 * [enable notifications]
 * WRITE: [0x53][91-byte client ECDH pubkey]
 *                                        ← compute ECDH raw secret
 *                                        ← derive confirmCode1
 *                                        NOTIFY: [0x52][91-byte server pubkey]
 *
 *   ── both display 6-digit code 1, user verifies ──
 *
 * WRITE: [0x43]                          ← client confirmed code 1
 *                                        ← encrypt profile, send chunks
 *                                        NOTIFY: [0xCA][total][idx][data…]
 *
 *   ── both display 6-digit code 2, user verifies ──
 *
 * WRITE: [0x44][installationId UTF-8]    ← client confirmed code 2
 *                                        ← store DGK, register peer, done
 * ```
 */
class SyncGattServer(
    private val context: Context,
    private val profileDao: MyProfileDao,
) {
    private val scope   = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val done    = AtomicBoolean(false)

    private var gattServer: BluetoothGattServer? = null
    private val deviceMtu = ConcurrentHashMap<String, Int>()

    // Multi-step ECDH state
    @Volatile private var ecdhRaw: ByteArray? = null
    @Volatile private var profileJsonBytes: ByteArray? = null
    @Volatile private var secondaryInstallationId: String? = null
    @Volatile private var connectedDevice: BluetoothDevice? = null

    // Step confirmation gating
    private val localConfirmedStep1  = AtomicBoolean(false)
    private val remoteConfirmedStep1 = AtomicBoolean(false)
    private val step1Complete = CompletableDeferred<Unit>()

    private val localConfirmedStep2  = AtomicBoolean(false)
    private val remoteConfirmedStep2 = AtomicBoolean(false)
    private val step2Complete = CompletableDeferred<Unit>()

    /** Called by the UI when a new confirmation code should be shown. */
    var onShowConfirmCode: ((code: String, step: Int) -> Unit)? = null
    /** Invoked on the IO thread after a secondary device has successfully completed sync. */
    var onSyncComplete: ((secondaryInstId: String) -> Unit)? = null
    /** Invoked on any error or timeout. */
    var onSyncFailed: ((reason: String) -> Unit)? = null

    // ── Advertise ─────────────────────────────────────────────────────────────

    private var advertiseCallback: AdvertiseCallback? = null

    /** Opens the GATT server + starts BLE advertising for the sync service. */
    fun start() {
        if (done.get()) {
            Log.w(TAG, "SyncGattServer already done — create a new instance.")
            return
        }
        openGattServer()
        startAdvertising()

        // Auto-stop after timeout
        scope.launch {
            delay(BleConstants.SYNC_ADVERTISE_TIMEOUT_MS)
            if (!done.get()) {
                Log.i(TAG, "Sync window expired.")
                stop()
                onSyncFailed?.invoke("Sync window expired — no secondary device connected.")
            }
        }
        Log.i(TAG, "Sync GATT server started (timeout in ${BleConstants.SYNC_ADVERTISE_TIMEOUT_MS / 1000}s).")
    }

    /** Closes the GATT server and stops advertising. Idempotent. */
    fun stop() {
        stopAdvertising()
        gattServer?.close()
        gattServer = null
        deviceMtu.clear()
        Log.i(TAG, "Sync GATT server stopped.")
    }

    /** Called by the UI when the local user confirms a confirmation code. */
    @SuppressLint("MissingPermission")
    fun confirmStep(step: Int) {
        when (step) {
            1 -> {
                localConfirmedStep1.set(true)
                if (remoteConfirmedStep1.get()) step1Complete.complete(Unit)
            }
            2 -> {
                localConfirmedStep2.set(true)
                // Notify the client that the sender has confirmed step 2
                val dev = connectedDevice
                val notifyChar = gattServer
                    ?.getService(BleConstants.SYNC_SERVICE_UUID)
                    ?.getCharacteristic(BleConstants.SYNC_NOTIFY_CHAR_UUID)
                if (dev != null && notifyChar != null) {
                    gattServer?.notifyCharacteristicChanged(
                        dev, notifyChar, false, byteArrayOf(BleConstants.STEP2_ACK_MAGIC)
                    )
                    Log.i(TAG, "Step 2 ACK sent to client")
                }
                if (remoteConfirmedStep2.get()) step2Complete.complete(Unit)
            }
        }
    }

    // ── GATT service ──────────────────────────────────────────────────────────

    private fun openGattServer() {
        val btManager = context.getSystemService(BluetoothManager::class.java) ?: return
        gattServer = btManager.openGattServer(context, buildCallback())?.also { srv ->
            srv.addService(buildService())
        }
    }

    private fun buildService(): BluetoothGattService {
        val service = BluetoothGattService(
            BleConstants.SYNC_SERVICE_UUID,
            BluetoothGattService.SERVICE_TYPE_PRIMARY,
        )

        // WRITE characteristic — secondary writes the ECDH frame here
        val writeChar = BluetoothGattCharacteristic(
            BleConstants.SYNC_WRITE_CHAR_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE or
                    BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
            BluetoothGattCharacteristic.PERMISSION_WRITE,
        )

        // NOTIFY characteristic — primary sends the encrypted sync payload
        val notifyChar = BluetoothGattCharacteristic(
            BleConstants.SYNC_NOTIFY_CHAR_UUID,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            0,  // No ATT read permission
        ).also { char ->
            char.addDescriptor(
                BluetoothGattDescriptor(
                    BleConstants.CCCD_UUID,
                    BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE,
                )
            )
        }

        service.addCharacteristic(writeChar)
        service.addCharacteristic(notifyChar)
        return service
    }

    // ── GATT callback ─────────────────────────────────────────────────────────

    private fun buildCallback() = object : BluetoothGattServerCallback() {

        override fun onConnectionStateChange(device: BluetoothDevice?, status: Int, newState: Int) {
            val label = if (newState == BluetoothProfile.STATE_CONNECTED) "CONNECTED" else "DISCONNECTED"
            Log.d(TAG, "Sync peer ${device?.address} → $label")
            if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                deviceMtu.remove(device?.address)
            }
        }

        override fun onMtuChanged(device: BluetoothDevice?, mtu: Int) {
            device?.address?.let { deviceMtu[it] = mtu }
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice?,
            requestId: Int,
            descriptor: BluetoothGattDescriptor?,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?,
        ) {
            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
            }
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice?,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic?,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?,
        ) {
            if (characteristic?.uuid != BleConstants.SYNC_WRITE_CHAR_UUID) return

            if (value == null || value.isEmpty()) {
                if (responseNeeded) {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null)
                }
                return
            }

            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
            }

            when (value[0]) {
                BleConstants.SYNC_REQUEST_MAGIC -> {
                    // ECDH request: [0x53][91-byte P-256 pubkey]
                    if (value.size != BleEncryption.REQUEST_FRAME_SIZE) {
                        Log.w(TAG, "Malformed ECDH frame from ${device?.address} (size=${value.size})")
                        return
                    }
                    val clientPubKeyBytes = value.copyOfRange(1, 1 + BleEncryption.PUBLIC_KEY_BYTES)
                    val dev = device ?: return
                    scope.launch { handleSyncRequest(dev, clientPubKeyBytes) }
                }

                BleConstants.CONFIRM_STEP1_MAGIC -> {
                    Log.d(TAG, "Remote confirmed step 1")
                    remoteConfirmedStep1.set(true)
                    if (localConfirmedStep1.get()) step1Complete.complete(Unit)
                }

                BleConstants.CONFIRM_STEP2_MAGIC -> {
                    Log.d(TAG, "Remote confirmed step 2")
                    if (value.size > 1) {
                        // The client encrypts the installationId with the established session key.
                        // Decrypt it here; fall back to raw bytes only if ecdhRaw is missing.
                        val raw = ecdhRaw
                        val encInstId = value.copyOfRange(1, value.size)
                        secondaryInstallationId = if (raw != null) {
                            val sessionKey = DeviceGroupManager.deriveSyncSessionKeyDirect(raw)
                            BleEncryption.decrypt(encInstId, sessionKey)?.toString(Charsets.UTF_8)
                        } else {
                            String(encInstId, Charsets.UTF_8)
                        }
                    }
                    remoteConfirmedStep2.set(true)
                    if (localConfirmedStep2.get()) step2Complete.complete(Unit)
                }

                else -> Log.w(TAG, "Unknown sync write magic: 0x${"%02X".format(value[0])}")
            }
        }
    }

    // ── Multi-step sync ─────────────────────────────────────────────────────

    private suspend fun handleSyncRequest(device: BluetoothDevice, clientPubKeyBytes: ByteArray) {
        connectedDevice = device
        // ── Step 1: ECDH key exchange + confirmation code 1 ──
        val clientPubKey = try {
            BleEncryption.decodePublicKey(clientPubKeyBytes)
        } catch (e: Exception) {
            Log.w(TAG, "Invalid client pubkey: ${e.message}")
            onSyncFailed?.invoke("Invalid key from secondary device.")
            return
        }

        val serverEphemeral = BleEncryption.generateEphemeralKeyPair()
        val ka = javax.crypto.KeyAgreement.getInstance("ECDH")
        ka.init(serverEphemeral.private)
        ka.doPhase(clientPubKey, true)
        ecdhRaw = ka.generateSecret()

        // Send server pubkey via notification (separate from payload chunks)
        val notifyChar = gattServer
            ?.getService(BleConstants.SYNC_SERVICE_UUID)
            ?.getCharacteristic(BleConstants.SYNC_NOTIFY_CHAR_UUID) ?: return

        val serverPubBytes = BleEncryption.encodePublicKey(serverEphemeral.public)
        val keyFrame = byteArrayOf(BleConstants.SYNC_RESPONSE_MAGIC) + serverPubBytes
        gattServer?.notifyCharacteristicChanged(device, notifyChar, false, keyFrame)
        Log.d(TAG, "Server pubkey sent to ${device.address}")

        // Derive and show confirmation code 1
        val code1 = DeviceGroupManager.deriveConfirmCode(ecdhRaw!!, 1)
        Log.i(TAG, "Confirmation code 1 ready")
        onShowConfirmCode?.invoke(code1, 1)

        // Wait for both local user and remote client to confirm code 1
        step1Complete.await()
        Log.i(TAG, "Step 1 confirmed by both sides — sending profile")

        // ── Step 2: Encrypt and send profile ──
        val profile = profileDao.get()
        if (profile == null) {
            Log.e(TAG, "No local profile to sync.")
            onSyncFailed?.invoke("No profile available.")
            return
        }

        val syncJson = buildSyncPayload(profile)
        profileJsonBytes = syncJson.toByteArray(Charsets.UTF_8)

        val sessionKey = DeviceGroupManager.deriveSyncSessionKeyDirect(ecdhRaw!!)
        val encrypted  = BleEncryption.encrypt(profileJsonBytes!!, sessionKey)

        val mtu     = deviceMtu[device.address] ?: BleConstants.DEFAULT_MTU
        val maxData = maxOf(1, mtu - 3 - 3)  // MTU - ATT header - chunk header
        val chunks  = encrypted.toList().chunked(maxData) { it.toByteArray() }
        val totalChunks = chunks.size.coerceAtMost(255)

        Log.i(TAG, "Sending ${encrypted.size}B encrypted payload in $totalChunks chunk(s)")

        chunks.forEachIndexed { idx, data ->
            if (idx >= 255) return@forEachIndexed
            val packet = byteArrayOf(BleConstants.CHUNK_MAGIC, totalChunks.toByte(), idx.toByte()) + data
            gattServer?.notifyCharacteristicChanged(device, notifyChar, false, packet)
        }

        // Derive and show confirmation code 2 (includes SHA-256 of payload for verification)
        val payloadHash = MessageDigest.getInstance("SHA-256").digest(profileJsonBytes!!)
        val code2 = DeviceGroupManager.deriveConfirmCode(ecdhRaw!!, 2, payloadHash)
        Log.i(TAG, "Confirmation code 2 ready")
        onShowConfirmCode?.invoke(code2, 2)

        // Wait for both to confirm code 2
        step2Complete.await()
        Log.i(TAG, "Step 2 confirmed by both sides — finalizing")

        // ── Step 3: Store DGK, register peer, done ──
        DeviceGroupManager.deriveAndStoreGroupKeyFromSync(context, ecdhRaw!!)
        val secondaryId = secondaryInstallationId ?: "unknown-${device.address}"
        if (secondaryId.isNotBlank()) {
            DeviceGroupManager.addPairedInstallationId(context, secondaryId)
        }
        markDoneAndNotify(secondaryId)
    }

    private fun buildSyncPayload(profile: com.thunderpass.data.db.entity.MyProfile): String {
        val profileJson = org.json.JSONObject().apply {
            put("displayName",  profile.displayName)
            put("greeting",     profile.greeting)
            put("avatarKind",   profile.avatarKind)
            put("avatarColor",  profile.avatarColor)
            put("avatarSeed",   profile.avatarSeed)
            put("voltsTotal",   profile.voltsTotal)
            put("retroUsername",profile.retroUsername)
            put("raApiKey",     profile.raApiKey)
            put("ghostGame",    profile.ghostGame)
            put("ghostScore",   profile.ghostScore)
            put("stickersJson", profile.stickersJson)
            put("badgesJson",   profile.badgesJson)
            put("privacyMode",  profile.privacyMode)
            put("deviceType",   profile.deviceType)
            put("country",      profile.country)
            put("city",         profile.city)
            put("updatedAt",    profile.updatedAt)
        }
        val pairedIds = org.json.JSONArray().apply {
            DeviceGroupManager.pairedInstallationIds(context).forEach { put(it) }
        }
        return org.json.JSONObject().apply {
            put("type",          "sync")
            put("v",             1)
            put("serverInstId",  profile.installationId)
            put("profile",       profileJson)
            put("pairedInstIds", pairedIds)
        }.toString()
    }

    private fun markDoneAndNotify(secondaryInstId: String) {
        if (done.compareAndSet(false, true)) {
            stop()
            onSyncComplete?.invoke(secondaryInstId)
        }
    }

    // ── Advertising ───────────────────────────────────────────────────────────

    private fun startAdvertising() {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return
        if (!adapter.isEnabled) {
            onSyncFailed?.invoke("Bluetooth is not enabled.")
            return
        }
        if (!adapter.isMultipleAdvertisementSupported) {
            onSyncFailed?.invoke("BLE peripheral advertising is not supported on this device.")
            return
        }

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true)
            .setTimeout(0)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .build()

        val data = AdvertiseData.Builder()
            .addServiceUuid(BleConstants.SYNC_SERVICE_PARCEL)
            .setIncludeDeviceName(false)
            .build()

        val cb = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                Log.i(TAG, "Sync advertising started.")
            }
            override fun onStartFailure(errorCode: Int) {
                Log.e(TAG, "Sync advertising failed: errorCode=$errorCode")
                onSyncFailed?.invoke("BLE advertising failed ($errorCode).")
            }
        }
        advertiseCallback = cb
        adapter.bluetoothLeAdvertiser?.startAdvertising(settings, data, cb)
    }

    private fun stopAdvertising() {
        val cb = advertiseCallback ?: return
        try {
            context.getSystemService(BluetoothManager::class.java)
                ?.adapter?.bluetoothLeAdvertiser?.stopAdvertising(cb)
        } catch (_: Exception) {}
        advertiseCallback = null
    }

}
