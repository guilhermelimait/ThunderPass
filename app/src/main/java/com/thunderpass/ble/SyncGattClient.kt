package com.thunderpass.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.util.Log
import com.thunderpass.data.db.dao.MyProfileDao
import com.thunderpass.data.db.entity.MyProfile
import com.thunderpass.retro.RetroAuthManager
import com.thunderpass.security.DeviceGroupManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.security.KeyPair
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "ThunderPass/SyncGattCli"

/**
 * GATT client (secondary device) that scans for, connects to, and receives the
 * encrypted profile from a primary device running [SyncGattServer].
 *
 * ## Protocol (v2 — SAS numeric comparison)
 * 1. Scan for [BleConstants.SYNC_SERVICE_UUID], connect to primary.
 * 2. ECDH key exchange → both derive 6-digit confirmation code 1.
 * 3. User confirms code 1 match → client writes [0x43] to server.
 * 4. Receive encrypted profile chunks, decrypt.
 * 5. Both derive 6-digit confirmation code 2 (includes payload hash).
 * 6. User confirms code 2 match → client writes [0x44] + installationId, saves profile + DGK.
 */
class SyncGattClient(
    private val context: Context,
    private val profileDao: MyProfileDao,
) {
    private val scope   = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val done    = AtomicBoolean(false)
    private val found   = AtomicBoolean(false)

    private var ephemeralKey: KeyPair?       = null
    private var scanCallback: ScanCallback?  = null
    private var gatt: BluetoothGatt?         = null
    private var timeoutJob: Job?             = null

    // ECDH state
    @Volatile private var ecdhRaw: ByteArray? = null

    // Chunk reassembly
    private var chunkBuffer: Array<ByteArray?> = emptyArray()

    // Decrypted payload held until user confirms code 2
    @Volatile private var decryptedPayload: ByteArray? = null
    @Volatile private var syncJsonObj: org.json.JSONObject? = null

    /** Called by the UI when a new confirmation code should be shown. */
    var onShowConfirmCode: ((code: String, step: Int) -> Unit)? = null
    /** Called on the IO thread when profile import succeeds. */
    var onSyncComplete: ((primaryInstId: String) -> Unit)? = null
    /** Called on any error or timeout. */
    var onSyncFailed: ((reason: String) -> Unit)? = null

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Begins scanning for the sync service UUID. Automatically stops after
     * [BleConstants.SYNC_ADVERTISE_TIMEOUT_MS] if no primary device is found.
     */
    fun start() {
        ephemeralKey = BleEncryption.generateEphemeralKeyPair()
        startScan()
        timeoutJob = scope.launch {
            delay(BleConstants.SYNC_ADVERTISE_TIMEOUT_MS)
            if (!done.get()) {
                Log.i(TAG, "Scan timeout — no primary device found.")
                stop()
                onSyncFailed?.invoke("No primary device found — ensure export mode is active on the primary.")
            }
        }
        Log.i(TAG, "Sync client started (scanning for primary device).")
    }

    /** Stops scanning and closes any open GATT connection. Idempotent. */
    fun stop() {
        stopScan()
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        timeoutJob?.cancel()
        Log.i(TAG, "Sync client stopped.")
    }

    /**
     * Called by the UI when the user confirms a 6-digit code.
     * - Step 1: writes [0x43] to the server characteristic.
     * - Step 2: writes [0x44][UTF-8 installationId], then saves profile + DGK.
     */
    @SuppressLint("MissingPermission")
    fun confirmStep(step: Int) {
        val g = gatt ?: return
        val writeChar = g.getService(BleConstants.SYNC_SERVICE_UUID)
            ?.getCharacteristic(BleConstants.SYNC_WRITE_CHAR_UUID) ?: return

        scope.launch {
            when (step) {
                1 -> {
                    val frame = byteArrayOf(BleConstants.CONFIRM_STEP1_MAGIC)
                    g.writeCharacteristic(writeChar, frame, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
                    Log.i(TAG, "Confirm step 1 sent to server.")
                }
                2 -> {
                    val instId = profileDao.get()?.installationId ?: ""
                    // Encrypt the installationId before writing it over the unencrypted BLE transport.
                    // Both sides share ecdhRaw at this point (key exchange completed in step 1).
                    val raw = ecdhRaw
                    val encInstId = if (raw != null && instId.isNotBlank()) {
                        val sessionKey = DeviceGroupManager.deriveSyncSessionKeyDirect(raw)
                        BleEncryption.encrypt(instId.toByteArray(Charsets.UTF_8), sessionKey)
                    } else {
                        instId.toByteArray(Charsets.UTF_8)
                    }
                    val frame  = byteArrayOf(BleConstants.CONFIRM_STEP2_MAGIC) + encInstId
                    g.writeCharacteristic(writeChar, frame, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
                    Log.i(TAG, "Confirm step 2 sent to server (instId encrypted, ${instId.take(8)}…)")
                    saveProfileAndDgk()
                }
            }
        }
    }

    // ── BLE scan ──────────────────────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    private fun startScan() {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        if (adapter == null || !adapter.isEnabled) {
            onSyncFailed?.invoke("Bluetooth is not enabled.")
            return
        }

        val filter = ScanFilter.Builder()
            .setServiceUuid(BleConstants.SYNC_SERVICE_PARCEL)
            .build()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                val device = result?.device ?: return
                if (done.get() || !found.compareAndSet(false, true)) return
                Log.i(TAG, "Found primary device: ${device.address}")
                scope.launch {
                    stopScan()
                    connectToDevice(device)
                }
            }
            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "Scan failed: errorCode=$errorCode")
                onSyncFailed?.invoke("BLE scan failed ($errorCode).")
            }
        }
        scanCallback = cb
        adapter.bluetoothLeScanner?.startScan(listOf(filter), settings, cb)
    }

    @SuppressLint("MissingPermission")
    private fun stopScan() {
        val cb = scanCallback ?: return
        try {
            context.getSystemService(BluetoothManager::class.java)
                ?.adapter?.bluetoothLeScanner?.stopScan(cb)
        } catch (_: Exception) {}
        scanCallback = null
    }

    // ── GATT connection ───────────────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    private fun connectToDevice(device: BluetoothDevice) {
        gatt = device.connectGatt(context, false, buildCallback(), BluetoothDevice.TRANSPORT_LE)
    }

    @SuppressLint("MissingPermission")
    private fun buildCallback() = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            when {
                newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS -> {
                    Log.d(TAG, "Connected to ${gatt?.device?.address}; requesting MTU…")
                    gatt?.requestMtu(BleConstants.PREFERRED_MTU)
                }
                newState == BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d(TAG, "Disconnected from ${gatt?.device?.address}")
                    if (!done.get()) {
                        // Unexpected disconnect
                        gatt?.close()
                        scope.launch { onSyncFailed?.invoke("Connection lost before sync completed.") }
                    }
                }
                else -> {
                    Log.w(TAG, "Unexpected GATT state: newState=$newState status=$status")
                    gatt?.close()
                }
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            Log.d(TAG, "MTU = $mtu")
            gatt.discoverServices()
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.w(TAG, "Service discovery failed: status=$status")
                gatt?.disconnect()
                return
            }
            val svc = gatt?.getService(BleConstants.SYNC_SERVICE_UUID)
            if (svc == null) {
                Log.w(TAG, "Sync service not found on ${gatt?.device?.address}")
                gatt?.disconnect()
                onSyncFailed?.invoke("Sync service not found — ensure the primary device is in export mode.")
                return
            }
            // Enable notifications on SYNC_NOTIFY_CHAR
            val notifyChar = svc.getCharacteristic(BleConstants.SYNC_NOTIFY_CHAR_UUID)
            gatt.setCharacteristicNotification(notifyChar, true)
            val cccd = notifyChar.getDescriptor(BleConstants.CCCD_UUID)
            gatt.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt?,
            descriptor: BluetoothGattDescriptor?,
            status: Int,
        ) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.w(TAG, "CCCD write failed: status=$status")
                gatt?.disconnect()
                return
            }
            // Notifications enabled — send sync REQUEST frame
            val writeChar = gatt?.getService(BleConstants.SYNC_SERVICE_UUID)
                ?.getCharacteristic(BleConstants.SYNC_WRITE_CHAR_UUID) ?: return

            val clientPub = BleEncryption.encodePublicKey(ephemeralKey!!.public)
            val frame     = byteArrayOf(BleConstants.SYNC_REQUEST_MAGIC) + clientPub

            gatt.writeCharacteristic(
                writeChar,
                frame,
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT,
            )
            Log.d(TAG, "Sync REQUEST written to ${gatt.device?.address} (${frame.size}B)")
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            if (characteristic.uuid != BleConstants.SYNC_NOTIFY_CHAR_UUID) return
            if (value.isEmpty()) return

            val address = gatt.device?.address.orEmpty()

            when (value[0]) {
                // ── Server pubkey (key exchange response) ─────────────────
                BleConstants.SYNC_RESPONSE_MAGIC -> {
                    Log.d(TAG, "Received server pubkey from $address (${value.size}B)")
                    scope.launch { handleKeyExchange(value, gatt) }
                }

                // ── Encrypted profile chunks ─────────────────────────────
                BleConstants.CHUNK_MAGIC -> {
                    if (value.size < 4) {
                        Log.w(TAG, "Malformed chunk from $address (size=${value.size})")
                        return
                    }
                    val totalChunks = value[1].toInt() and 0xFF
                    val chunkIndex  = value[2].toInt() and 0xFF
                    val data        = value.copyOfRange(3, value.size)

                    if (chunkBuffer.size != totalChunks) {
                        chunkBuffer = arrayOfNulls(totalChunks)
                    }
                    if (chunkIndex < chunkBuffer.size) {
                        chunkBuffer[chunkIndex] = data
                    }
                    Log.d(TAG, "Chunk $chunkIndex/$totalChunks from $address (${data.size}B)")

                    if (chunkBuffer.all { it != null }) {
                        val full = chunkBuffer.filterNotNull().fold(byteArrayOf()) { acc, b -> acc + b }
                        chunkBuffer = emptyArray()
                        scope.launch { processEncryptedPayload(full, gatt) }
                    }
                }

                // ── Server confirmed step 2 (sender typed the code) ────
                BleConstants.STEP2_ACK_MAGIC -> {
                    Log.i(TAG, "Server ACK for step 2 received — confirming step 2")
                    confirmStep(2)
                }

                else -> {
                    Log.w(TAG, "Unknown notification magic 0x${String.format("%02X", value[0])} from $address")
                }
            }
        }
    }

    // ── Key Exchange (step 1) ────────────────────────────────────────────────

    /**
     * Handles the server's [0x52][91-byte pubkey] notification.
     * Computes ECDH, derives confirmation code 1, and shows it to the user.
     */
    @SuppressLint("MissingPermission")
    private fun handleKeyExchange(value: ByteArray, gatt: BluetoothGatt) {
        if (value.size < 1 + BleEncryption.PUBLIC_KEY_BYTES) {
            Log.w(TAG, "Server pubkey notification too short (${value.size}B)")
            onSyncFailed?.invoke("Bad key exchange from primary device.")
            gatt.disconnect()
            return
        }

        val serverPubKey = try {
            BleEncryption.decodePublicKey(value.copyOfRange(1, 1 + BleEncryption.PUBLIC_KEY_BYTES))
        } catch (e: Exception) {
            Log.w(TAG, "Bad server pubkey: ${e.message}")
            onSyncFailed?.invoke("Bad key from primary device.")
            gatt.disconnect()
            return
        }

        val raw = try {
            val ka = javax.crypto.KeyAgreement.getInstance("ECDH")
            ka.init(ephemeralKey!!.private)
            ka.doPhase(serverPubKey, true)
            ka.generateSecret()
        } catch (e: Exception) {
            Log.w(TAG, "ECDH failed: ${e.message}")
            onSyncFailed?.invoke("Key agreement failed.")
            gatt.disconnect()
            return
        }

        ecdhRaw = raw
        val code1 = DeviceGroupManager.deriveConfirmCode(raw, step = 1)
        Log.i(TAG, "Code 1 derived — showing to user for confirmation.")
        onShowConfirmCode?.invoke(code1, 1)
    }

    // ── Encrypted Payload Processing (step 2) ────────────────────────────────

    /**
     * Decrypts the reassembled payload using the ECDH session key (no DGK),
     * derives confirmation code 2 (with payload hash), and shows it to the user.
     */
    @SuppressLint("MissingPermission")
    private fun processEncryptedPayload(encrypted: ByteArray, gatt: BluetoothGatt) {
        val raw = ecdhRaw
        if (raw == null) {
            Log.w(TAG, "Received payload before key exchange — dropping.")
            onSyncFailed?.invoke("Protocol error: payload before key exchange.")
            gatt.disconnect()
            return
        }

        val sessionKey = DeviceGroupManager.deriveSyncSessionKeyDirect(raw)
        val plaintext = BleEncryption.decrypt(encrypted, sessionKey)
        if (plaintext == null) {
            Log.w(TAG, "Decryption failed — wrong key or tampered data.")
            onSyncFailed?.invoke("Decryption failed.")
            gatt.disconnect()
            return
        }

        decryptedPayload = plaintext

        val jsonStr = plaintext.toString(Charsets.UTF_8)
        val json = try { org.json.JSONObject(jsonStr) } catch (e: Exception) {
            Log.w(TAG, "Invalid sync JSON: ${e.message}")
            onSyncFailed?.invoke("Malformed sync data from primary device.")
            gatt.disconnect()
            return
        }

        if (json.optString("type") != "sync") {
            Log.w(TAG, "Unexpected payload type: ${json.optString("type")}")
            onSyncFailed?.invoke("Unexpected sync data format.")
            gatt.disconnect()
            return
        }

        syncJsonObj = json

        // Code 2 includes a hash of the payload for integrity binding
        val payloadHash = MessageDigest.getInstance("SHA-256").digest(plaintext)
        val code2 = DeviceGroupManager.deriveConfirmCode(raw, step = 2, payloadHash = payloadHash)
        Log.i(TAG, "Code 2 derived — showing to user for confirmation.")
        onShowConfirmCode?.invoke(code2, 2)
    }

    // ── Profile Import (after user confirms code 2) ──────────────────────────

    /**
     * Imports the decrypted profile, registers paired devices, and derives + stores
     * the Device Group Key from the ECDH secret.
     */
    @SuppressLint("MissingPermission")
    private suspend fun saveProfileAndDgk() {
        val json = syncJsonObj
        val raw  = ecdhRaw
        if (json == null || raw == null) {
            onSyncFailed?.invoke("Protocol error: no data to save.")
            return
        }

        val primaryInstId = json.optString("serverInstId", "")
        val profileJson   = json.optJSONObject("profile")
        val pairedIds     = json.optJSONArray("pairedInstIds")

        if (profileJson == null) {
            onSyncFailed?.invoke("Incomplete sync data from primary device.")
            return
        }

        // Import the profile — keep OUR OWN installationId
        val existing = profileDao.get()
        val imported = MyProfile(
            id            = 1,
            installationId= existing?.installationId ?: "",
            displayName   = profileJson.optString("displayName", existing?.displayName ?: ""),
            greeting      = profileJson.optString("greeting",    existing?.greeting    ?: ""),
            avatarKind    = profileJson.optString("avatarKind",  existing?.avatarKind  ?: "defaultBolt"),
            avatarColor   = profileJson.optString("avatarColor", existing?.avatarColor ?: "#FFFFFF"),
            avatarSeed    = profileJson.optString("avatarSeed",  existing?.avatarSeed  ?: ""),
            voltsTotal    = maxOf(
                profileJson.optLong("voltsTotal", 0L),
                existing?.voltsTotal ?: 0L,
            ),
            retroUsername = profileJson.optString("retroUsername", existing?.retroUsername ?: ""),
            raApiKey      = profileJson.optString("raApiKey",      existing?.raApiKey      ?: ""),
            ghostGame     = profileJson.optString("ghostGame",     existing?.ghostGame     ?: ""),
            ghostScore    = profileJson.optLong("ghostScore",      existing?.ghostScore    ?: 0L),
            stickersJson  = unionCsv(
                profileJson.optString("stickersJson", ""),
                existing?.stickersJson ?: "",
            ),
            badgesJson    = unionCsv(
                profileJson.optString("badgesJson", ""),
                existing?.badgesJson ?: "",
            ),
            privacyMode   = profileJson.optBoolean("privacyMode", existing?.privacyMode ?: false),
            deviceType    = existing?.deviceType ?: profileJson.optString("deviceType", ""),
            country       = profileJson.optString("country",  existing?.country ?: ""),
            city          = profileJson.optString("city",     existing?.city    ?: ""),
            payloadPublicKey = existing?.payloadPublicKey ?: "",
            updatedAt     = profileJson.optLong("updatedAt", System.currentTimeMillis() / 1000),
        )
        profileDao.upsert(imported)
        Log.i(TAG, "Profile imported from primary device ($primaryInstId).")

        // Mirror RetroAchievements credentials to EncryptedSharedPreferences
        // so RetroAuthManager.hasCredentials() returns true for future RA API calls.
        val syncedRaUser = imported.retroUsername
        val syncedRaKey  = imported.raApiKey
        if (syncedRaUser.isNotBlank() || syncedRaKey.isNotBlank()) {
            val auth = RetroAuthManager.getInstance(context)
            auth.saveCredentials(apiUser = syncedRaUser, apiKey = syncedRaKey)
            Log.i(TAG, "RA credentials mirrored to RetroAuthManager.")
        }

        // Register peers
        if (primaryInstId.isNotBlank()) {
            DeviceGroupManager.addPairedInstallationId(context, primaryInstId)
        }
        if (pairedIds != null) {
            for (i in 0 until pairedIds.length()) {
                val id = pairedIds.optString(i, "")
                if (id.isNotBlank()) DeviceGroupManager.addPairedInstallationId(context, id)
            }
        }

        // Derive and store Device Group Key from ECDH
        DeviceGroupManager.deriveAndStoreGroupKeyFromSync(context, raw)
        Log.i(TAG, "DGK derived and stored from sync ECDH.")

        if (done.compareAndSet(false, true)) {
            gatt?.disconnect()
            gatt?.close()
            stopScan()
            timeoutJob?.cancel()
            onSyncComplete?.invoke(primaryInstId)
        }
    }

    /** Union-merge two comma-separated value strings, deduplicating keys. */
    private fun unionCsv(a: String, b: String): String {
        val setA = a.split(",").filter { it.isNotBlank() }.toSet()
        val setB = b.split(",").filter { it.isNotBlank() }.toSet()
        return (setA + setB).joinToString(",")
    }
}
