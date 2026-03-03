package com.thunderpass

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Intent
import android.os.IBinder
import android.os.VibrationEffect
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.thunderpass.ble.BleConstants
import com.thunderpass.ble.EncounterDedup
import com.thunderpass.ble.ScanMode
import com.thunderpass.ble.GattClient
import com.thunderpass.ble.GattServer
import com.thunderpass.ble.RotatingIdManager
import com.thunderpass.data.db.ThunderPassDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private const val TAG = "ThunderPass/BleService"

/**
 * Foreground service (type = connectedDevice) that concurrently:
 *  - **Advertises** a ThunderPass presence packet (Broadcaster role).
 *  - **Scans** for ThunderPass presence packets (Discoverer role).
 *  - Runs a **GATT server** to accept incoming profile exchanges.
 *  - Triggers a **GATT client** exchange when a new device is discovered.
 *
 * Start via [ACTION_START], stop via [ACTION_STOP].
 */
class BleService : Service() {

    companion object {
        const val ACTION_START         = "com.thunderpass.ble.START"
        const val ACTION_STOP          = "com.thunderpass.ble.STOP"
        const val ACTION_SET_SCAN_MODE = "com.thunderpass.SET_SCAN_MODE"
        const val EXTRA_SCAN_MODE      = "EXTRA_SCAN_MODE"
        const val ACTION_SET_SAFE_ZONE = "com.thunderpass.SET_SAFE_ZONE"
        const val EXTRA_SAFE_ZONE      = "EXTRA_SAFE_ZONE"

        /** SharedPreferences file and key used by both Service and ViewModel. */
        const val PREFS_NAME         = "thunderpass_prefs"
        const val PREF_SAFE_ZONE     = "safe_zone_active"
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // ── Scan mode ─────────────────────────────────────────────────────────────
    private var _scanMode: ScanMode = ScanMode.BALANCED
    private var _isActive: Boolean  = false
    // ── Safe Zone ───────────────────────────────────────────────────
    private var _safeZoneActive: Boolean = false
    // ── BLE handles ───────────────────────────────────────────────────────────
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bleScanner: BluetoothLeScanner? = null

    // ── ThunderPass components ────────────────────────────────────────────────
    private lateinit var rotatingIdManager: RotatingIdManager
    private lateinit var gattServer: GattServer
    private lateinit var gattClient: GattClient
    private lateinit var encounterDedup: EncounterDedup

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()

        val btManager = getSystemService(BluetoothManager::class.java)
        bluetoothAdapter = btManager?.adapter

        // Restore persisted Safe Zone state
        _safeZoneActive = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .getBoolean(PREF_SAFE_ZONE, false)

        // Wire up components
        rotatingIdManager = RotatingIdManager(this)

        val db = ThunderPassDatabase.getInstance(this)
        encounterDedup = EncounterDedup(db.encounterDao())
        gattClient = GattClient(
            context     = this,
            encounterDao = db.encounterDao(),
            snapshotDao  = db.peerProfileSnapshotDao(),
            profileDao   = db.myProfileDao(),
            scope        = serviceScope,
            onProfileReceived = { encId, name -> updateEncounterNotification(encId, name) },
        )
        gattServer = GattServer(
            context            = this,
            rotatingIdManager  = rotatingIdManager,
        )

        // Ensure default profile row exists
        serviceScope.launch {
            val profileDao = db.myProfileDao()
            if (profileDao.get() == null) {
                profileDao.upsert(
                    com.thunderpass.data.db.entity.MyProfile(
                        installationId = rotatingIdManager.installationId
                    )
                )
            }
        }

        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                _isActive = true
                startForeground(BleConstants.NOTIF_ID, buildNotification())
                gattServer.start {
                    // Provide the current profile synchronously (ok: DB on IO thread)
                    runBlocking(Dispatchers.IO) {
                        ThunderPassDatabase.getInstance(this@BleService).myProfileDao().get()
                    }
                }
                if (!_safeZoneActive) {
                    startAdvertising()
                    startScanning()
                } else {
                    Log.i(TAG, "Service started in Safe Zone — BLE paused until zone deactivated.")
                }
                Log.i(TAG, "ThunderPass BLE service started.")
            }
            ACTION_STOP -> {
                _isActive = false
                stopScanning()
                stopAdvertising()
                gattServer.stop()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                Log.i(TAG, "ThunderPass BLE service stopped.")
            }
            ACTION_SET_SCAN_MODE -> {
                val modeName = intent.getStringExtra(EXTRA_SCAN_MODE) ?: return START_STICKY
                val newMode  = runCatching { ScanMode.valueOf(modeName) }.getOrNull() ?: return START_STICKY
                if (newMode != _scanMode) {
                    _scanMode = newMode
                    if (_isActive && !_safeZoneActive) { stopScanning(); startScanning() }
                    Log.i(TAG, "Scan mode changed to $_scanMode")
                }
            }
            ACTION_SET_SAFE_ZONE -> {
                val active = intent.getBooleanExtra(EXTRA_SAFE_ZONE, false)
                _safeZoneActive = active
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                    .putBoolean(PREF_SAFE_ZONE, active).apply()
                if (_isActive) {
                    if (active) {
                        stopScanning()
                        stopAdvertising()
                        Log.i(TAG, "Safe Zone activated — BLE paused.")
                    } else {
                        startAdvertising()
                        startScanning()
                        Log.i(TAG, "Safe Zone deactivated — BLE resumed.")
                    }
                }
                // Refresh the persistent notification to reflect Safe Zone state
                getSystemService(android.app.NotificationManager::class.java)
                    .notify(BleConstants.NOTIF_ID, buildNotification())
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopScanning()
        stopAdvertising()
        gattServer.stop()
        serviceScope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── Advertising ───────────────────────────────────────────────────────────

    private var advertiseCallback: AdvertiseCallback? = null

    private fun startAdvertising() {
        val adapter = bluetoothAdapter ?: return
        if (!adapter.isEnabled)                   { Log.w(TAG, "Bluetooth off; skip advertise."); return }
        if (!adapter.isMultipleAdvertisementSupported) { Log.w(TAG, "Advertising not supported."); return }

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER)
            .setConnectable(true)           // accept GATT connections
            .setTimeout(0)                  // run indefinitely
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_LOW)
            .build()

        // Include current rotating ID in the service data (first 4 bytes)
        val rotId = rotatingIdManager.currentRotatingId()
        val serviceData = buildPresencePayload(rotId)

        val data = AdvertiseData.Builder()
            .addServiceUuid(BleConstants.THUNDERPASS_SERVICE_PARCEL)
            .addServiceData(BleConstants.THUNDERPASS_SERVICE_PARCEL, serviceData)
            .setIncludeDeviceName(false)    // omit device name for privacy
            .build()

        val cb = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                Log.i(TAG, "Advertising started (rotatingId=${rotId.take(8)}…)")
            }
            override fun onStartFailure(errorCode: Int) {
                Log.e(TAG, "Advertising failed: errorCode=$errorCode")
            }
        }
        advertiseCallback = cb
        adapter.bluetoothLeAdvertiser?.startAdvertising(settings, data, cb)
    }

    private fun stopAdvertising() {
        val cb = advertiseCallback ?: return
        try { bluetoothAdapter?.bluetoothLeAdvertiser?.stopAdvertising(cb) }
        catch (e: Exception) { Log.w(TAG, "stopAdvertising: ${e.message}") }
        advertiseCallback = null
    }

    /**
     * Builds the binary presence payload placed in the BLE service data field.
     *
     * Layout (little-endian, per SPEC.md § Discovery Layer):
     * ```
     * [version:1][flags:1][rotatingId:16]
     * ```
     */
    private fun buildPresencePayload(rotatingIdB64: String): ByteArray {
        val rotBytes = android.util.Base64.decode(rotatingIdB64, android.util.Base64.URL_SAFE)
        return byteArrayOf(
            BleConstants.PROTOCOL_VERSION.toByte(),  // version
            0x01.toByte(),                           // flags: bit0 = supports GATT exchange
        ) + rotBytes.copyOf(16)
    }

    // ── Scanning ──────────────────────────────────────────────────────────────

    private var scanCallback: ScanCallback? = null

    private fun startScanning() {
        val adapter = bluetoothAdapter ?: return
        if (!adapter.isEnabled) { Log.w(TAG, "Bluetooth off; skip scan."); return }

        bleScanner = adapter.bluetoothLeScanner

        val filter = ScanFilter.Builder()
            .setServiceUuid(BleConstants.THUNDERPASS_SERVICE_PARCEL)
            .build()

        val settings = when (_scanMode) {
            ScanMode.AGGRESSIVE -> ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)  // ~100% duty cycle
                .build()
            ScanMode.BALANCED   -> ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_BALANCED)     // ~50% duty cycle
                .build()
        }

        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                result ?: return
                val device     = result.device
                val rssi       = result.rssi
                val serviceData = result.scanRecord
                    ?.getServiceData(BleConstants.THUNDERPASS_SERVICE_PARCEL)

                // Extract the rotating ID from the service data payload
                val rotatingId = if (serviceData != null && serviceData.size >= 18) {
                    // bytes 0=version, 1=flags, 2..17=rotatingId
                    val rotBytes = serviceData.copyOfRange(2, 18)
                    android.util.Base64.encodeToString(
                        rotBytes,
                        android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP
                    )
                } else {
                    // Fallback: use device address (less private but safe for MVP)
                    device.address
                }

                Log.d(TAG, "Seen: ${device.address} rotId=${rotatingId.take(8)}… RSSI=$rssi")

                // Apply dedup + potentially initiate GATT exchange
                serviceScope.launch {
                    val encounterId = encounterDedup.onDeviceSeen(rotatingId, rssi)
                    if (encounterId != null) {
                        Log.i(TAG, "New encounter #$encounterId — initiating GATT exchange with ${device.address}")
                        showEncounterNotification(encounterId)
                        com.thunderpass.widget.EncounterWidget.refresh(applicationContext)
                        gattClient.connect(device, encounterId)
                    }
                }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "Scan failed: errorCode=$errorCode")
            }
        }
        scanCallback = cb
        bleScanner?.startScan(listOf(filter), settings, cb)
        Log.i(TAG, "BLE scanning started.")
    }

    private fun stopScanning() {
        val cb = scanCallback ?: return
        try { bleScanner?.stopScan(cb) }
        catch (e: Exception) { Log.w(TAG, "stopScan: ${e.message}") }
        scanCallback = null
        bleScanner = null
    }

    // ── Notification ──────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        // Foreground service channel (silent)
        nm.createNotificationChannel(
            NotificationChannel(
                BleConstants.NOTIF_CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "ThunderPass BLE foreground service"
                setShowBadge(false)
            }
        )
        // Encounter alert channel (heads-up)
        nm.createNotificationChannel(
            NotificationChannel(
                BleConstants.ENCOUNTER_CHANNEL_ID,
                getString(R.string.encounter_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Alerts when a new traveler is discovered nearby"
                setShowBadge(true)
            }
        )
    }

    /** Show a dismissible notification when a new encounter is recorded. */
    private fun showEncounterNotification(encounterId: Long) {
        val tapIntent = PendingIntent.getActivity(
            this, encounterId.toInt(),
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notif = NotificationCompat.Builder(this, BleConstants.ENCOUNTER_CHANNEL_ID)
            .setContentTitle(getString(R.string.encounter_notif_title))
            .setContentText(getString(R.string.encounter_notif_text))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(tapIntent)
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java)
            .notify(BleConstants.NOTIF_ID + 1, notif)
    }

    /** Update the encounter notification once we know the peer's display name. */
    private fun updateEncounterNotification(encounterId: Long, displayName: String) {
        // ⚡ "The Spark" — double-pulse haptic feedback on successful profile exchange
        val vibrator = getSystemService(VibratorManager::class.java).defaultVibrator
        // Pattern: off 0ms → buzz 80ms → pause 120ms → buzz 250ms
        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 80, 120, 250), -1))

        val tapIntent = PendingIntent.getActivity(
            this, encounterId.toInt(),
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notif = NotificationCompat.Builder(this, BleConstants.ENCOUNTER_CHANNEL_ID)
            .setContentTitle("👋 $displayName is nearby!")
            .setContentText("Tap to see their profile")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(tapIntent)
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java)
            .notify(BleConstants.NOTIF_ID + 1, notif)
    }

    private fun buildNotification(): Notification {
        val tapIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val text = if (_safeZoneActive)
            "\uD83D\uDEE1\uFE0F Safe Zone active — BLE paused"
        else
            getString(R.string.notification_text)
        return NotificationCompat.Builder(this, BleConstants.NOTIF_CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(tapIntent)
            .setOngoing(true)
            .build()
    }
}
