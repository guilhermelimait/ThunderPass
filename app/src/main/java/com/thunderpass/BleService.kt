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
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.IBinder
import android.os.VibrationEffect
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.thunderpass.ble.BleConstants
import com.thunderpass.ble.BleStats
import com.thunderpass.ble.EncounterDedup
import com.thunderpass.ble.GattClient
import com.thunderpass.ble.GattServer
import com.thunderpass.ble.RotatingIdManager
import com.thunderpass.ble.ScanMode
import com.thunderpass.data.db.ThunderPassDatabase
import com.thunderpass.steps.StepVoltManager
import com.thunderpass.widget.ThunderPassWidget
import com.thunderpass.widget.ThunderPassWidget2x2
import com.thunderpass.ui.randomSparkySeed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
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
        const val ACTION_SET_AUTO_WALK = "com.thunderpass.SET_AUTO_WALK"
        const val EXTRA_AUTO_WALK      = "EXTRA_AUTO_WALK"

        /** SharedPreferences file and key used by both Service and ViewModel. */
        const val PREFS_NAME          = "thunderpass_prefs"
        const val PREF_SAFE_ZONE      = "safe_zone_active"
        const val PREF_SCAN_MODE      = "scan_mode"
        const val PREF_SERVICE_ACTIVE = "service_active"
        const val PREF_BLE_ENABLED    = "ble_enabled"
        const val PREF_AUTO_WALK      = "auto_walk_enabled"

        /** Compute encounter streak in consecutive calendar days (local timezone). */
        fun computeEncounterStreak(encounters: List<com.thunderpass.data.db.entity.Encounter>): Int {
            if (encounters.isEmpty()) return 0
            val cal = java.util.Calendar.getInstance()
            fun dayStart(ms: Long): Long {
                cal.timeInMillis = ms
                cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                cal.set(java.util.Calendar.MINUTE, 0)
                cal.set(java.util.Calendar.SECOND, 0)
                cal.set(java.util.Calendar.MILLISECOND, 0)
                return cal.timeInMillis
            }
            val seenDays  = encounters.map { dayStart(it.seenAt) }.toSet()
            var checkDay  = dayStart(System.currentTimeMillis())
            if (checkDay !in seenDays) checkDay -= 86_400_000L
            var streak = 0
            while (checkDay in seenDays) { streak++; checkDay -= 86_400_000L }
            return streak
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // ── Scan mode ─────────────────────────────────────────────────────────────
    private var _scanMode: ScanMode = ScanMode.BALANCED
    private var _isActive: Boolean  = false
    // ── Safe Zone ───────────────────────────────────────────────────
    private var _safeZoneActive: Boolean = false
    // ── Auto-Walk mode ────────────────────────────────────────────────────────
    // When enabled: BLE scanning/advertising is paused while idle; resumes the
    // moment the step detector fires, and pauses again after STILL_TIMEOUT_MS
    // of inactivity.  TYPE_STEP_DETECTOR is hardware-backed and essentially free.
    @Volatile private var _autoWalkEnabled: Boolean = false
    @Volatile private var _autoWalkPaused:  Boolean = false  // true = BLE paused awaiting motion
    @Volatile private var lastStepDetectedMs: Long  = 0L
    private var stillCheckJob: kotlinx.coroutines.Job? = null
    private var walkSensorManager: SensorManager? = null
    private val autoWalkListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (event.sensor.type != Sensor.TYPE_STEP_DETECTOR) return
            lastStepDetectedMs = System.currentTimeMillis()
            Log.d(TAG, "Auto-Walk: step detected (paused=$_autoWalkPaused active=$_isActive safe=$_safeZoneActive)")
            if (_autoWalkPaused && _isActive && !_safeZoneActive) {
                _autoWalkPaused = false
                startAdvertising()
                startScanning()
                Log.i(TAG, "Auto-Walk: motion detected — BLE resumed.")
                refreshNotification()
            }
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }
    // ── BLE handles ───────────────────────────────────────────────────────────
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bleScanner: BluetoothLeScanner? = null

    // ── ThunderPass components ────────────────────────────────────────────────
    private lateinit var rotatingIdManager: RotatingIdManager
    private lateinit var gattServer: GattServer
    private lateinit var gattClient: GattClient
    private lateinit var encounterDedup: EncounterDedup

    // ── Profile cache for GattServer (saves ~10–50ms DB read per encounter) ────
    // Cache ProfileData only; BlePayloadProto outer layer (ts, sig, encryption) is built fresh per exchange.
    @Volatile private var cachedProfile: com.thunderpass.data.db.entity.MyProfile? = null
    @Volatile private var cachedStats: BleStats? = null
    @Volatile private var profileCacheTimestampMs: Long = 0L
    private val PROFILE_CACHE_TTL_MS = 2_000L

    // ── Bluetooth state receiver ──────────────────────────────────────────────
    // Restarts advertising + scanning automatically when the user turns BT back on.
    private val btStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: android.content.Context, intent: Intent) {
            if (intent.action != BluetoothAdapter.ACTION_STATE_CHANGED) return
            when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
                BluetoothAdapter.STATE_OFF -> {
                    Log.i(TAG, "Bluetooth turned off \u2014 pausing BLE operations.")
                    stopScanning()
                    stopAdvertising()
                }
                BluetoothAdapter.STATE_ON -> {
                    if (_isActive && !_safeZoneActive && !_autoWalkPaused) {
                        Log.i(TAG, "Bluetooth turned on \u2014 restarting BLE operations.")
                        startAdvertising()
                        startScanning()
                    }
                }
            }
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()

        val btManager = getSystemService(BluetoothManager::class.java)
        bluetoothAdapter = btManager?.adapter

        // Restore persisted Safe Zone + scan mode state
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        _safeZoneActive = prefs.getBoolean(PREF_SAFE_ZONE, false)
        _scanMode = runCatching {
            ScanMode.valueOf(prefs.getString(PREF_SCAN_MODE, ScanMode.BALANCED.name)!!)
        }.getOrDefault(ScanMode.BALANCED)
        _autoWalkEnabled = prefs.getBoolean(PREF_AUTO_WALK, false)

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

        // Ensure default profile row exists and always has an avatarSeed so peers
        // receive a stable Sparky avatar from the very first BLE exchange.
        serviceScope.launch {
            val profileDao = db.myProfileDao()
            val existing = profileDao.get()
            if (existing == null) {
                profileDao.upsert(
                    com.thunderpass.data.db.entity.MyProfile(
                        installationId = rotatingIdManager.installationId,
                        avatarSeed     = randomSparkySeed(),
                    )
                )
            } else if (existing.avatarSeed.isBlank()) {
                profileDao.upsert(existing.copy(avatarSeed = randomSparkySeed()))
            }
        }

        createNotificationChannel()
        registerReceiver(
            btStateReceiver,
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
        )

        // Step counter — awards up to 100 Volts/day from walking (no-ops on devices without sensor)
        StepVoltManager.start(this)

        // Auto-Walk — register step detector now; BLE is only started on demand in onStartCommand
        if (_autoWalkEnabled) startAutoWalkWatch()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                if (_isActive) {
                    Log.i(TAG, "Already active — ignoring duplicate START.")
                    return START_STICKY
                }
                _isActive = true
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                    .putBoolean(PREF_SERVICE_ACTIVE, true).apply()
                serviceScope.launch { ThunderPassWidget.refreshAll(this@BleService, true) }
                serviceScope.launch { ThunderPassWidget2x2.refreshAll(this@BleService, true) }
                startForeground(BleConstants.NOTIF_ID, buildNotification())
                // Warm profile cache so first encounter avoids DB read
                refreshProfileCache()
                gattServer.start(
                    profileProvider = { getProfileForExchange() },
                    statsProvider = { getStatsForExchange() },
                )
                if (!_safeZoneActive) {
                    if (_autoWalkEnabled) {
                        // Start paused — auto-walk will resume BLE when motion is detected
                        _autoWalkPaused = true
                        refreshNotification()
                        Log.i(TAG, "Auto-Walk enabled — waiting for motion before starting BLE.")
                    } else {
                        Log.i(TAG, "Starting BLE: safeZone=$_safeZoneActive autoWalk=$_autoWalkEnabled scanMode=$_scanMode")
                        startAdvertising()
                        startScanning()
                    }
                } else {
                    Log.i(TAG, "Service started in Safe Zone — BLE paused until zone deactivated.")
                }
                Log.i(TAG, "ThunderPass BLE service started. active=$_isActive safeZone=$_safeZoneActive autoWalk=$_autoWalkEnabled autoWalkPaused=$_autoWalkPaused scanMode=$_scanMode")
            }
            ACTION_STOP -> {
                _isActive = false
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                    .putBoolean(PREF_SERVICE_ACTIVE, false).apply()
                serviceScope.launch { ThunderPassWidget.refreshAll(this@BleService, false) }
                serviceScope.launch { ThunderPassWidget2x2.refreshAll(this@BleService, false) }
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
                    getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                        .putString(PREF_SCAN_MODE, newMode.name).apply()
                    if (_isActive && !_safeZoneActive && !_autoWalkPaused) {
                        stopScanning()
                        if (newMode != ScanMode.OFF) startScanning()
                    }
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
                        // Only resume if auto-walk isn't also holding BLE paused
                        if (!_autoWalkPaused) {
                            startAdvertising()
                            startScanning()
                        }
                        Log.i(TAG, "Safe Zone deactivated — BLE resumed.")
                    }
                }
                refreshNotification()
            }
            ACTION_SET_AUTO_WALK -> {
                val enabled = intent.getBooleanExtra(EXTRA_AUTO_WALK, false)
                _autoWalkEnabled = enabled
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                    .putBoolean(PREF_AUTO_WALK, enabled).apply()
                if (enabled) {
                    startAutoWalkWatch()
                    // If service is already active and BLE is running, set paused state so the
                    // still-check can take over — don't immediately cut BLE, give it one timeout.
                    lastStepDetectedMs = System.currentTimeMillis()
                } else {
                    stopAutoWalkWatch()
                    // If BLE was paused by auto-walk, resume it now
                    if (_autoWalkPaused && _isActive && !_safeZoneActive) {
                        _autoWalkPaused = false
                        startAdvertising()
                        startScanning()
                    }
                }
                refreshNotification()
                Log.i(TAG, "Auto-Walk mode ${if (enabled) "enabled" else "disabled"}.")
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        StepVoltManager.stop(this)
        stopAutoWalkWatch()
        stopScanning()
        stopAdvertising()
        gattServer.stop()
        runCatching { unregisterReceiver(btStateReceiver) }
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

        // NOTE: Only include the service UUID — NOT service data.
        // A 128-bit UUID AD record is 18 bytes; adding 18 bytes of service data would
        // exceed the 31-byte BLE advertisement payload limit and cause
        // ADVERTISE_FAILED_DATA_TOO_LARGE.
        // Identity dedup is done post-GATT via peerInstId (installationId), not via scan payload.
        val data = AdvertiseData.Builder()
            .addServiceUuid(BleConstants.THUNDERPASS_SERVICE_PARCEL)
            .setIncludeDeviceName(false)    // omit device name for privacy
            .build()

        val cb = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                Log.i(TAG, "Advertising started.")
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

    // ── Scanning ──────────────────────────────────────────────────────────────

    private var scanCallback: ScanCallback? = null

    private fun startScanning() {
        val adapter = bluetoothAdapter ?: return
        if (!adapter.isEnabled) { Log.w(TAG, "Bluetooth off; skip scan."); return }

        bleScanner = adapter.bluetoothLeScanner

        val filter = ScanFilter.Builder()
            .setServiceUuid(BleConstants.THUNDERPASS_SERVICE_PARCEL)
            .build()

        if (_scanMode == ScanMode.OFF) {
            Log.i(TAG, "Battery mode OFF — scan skipped.")
            return
        }

        val settings = when (_scanMode) {
            ScanMode.AGGRESSIVE -> ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)  // ~100% duty cycle
                .build()
            ScanMode.BALANCED   -> ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_BALANCED)     // ~50% duty cycle
                .build()
            ScanMode.OFF        -> return  // unreachable — handled above
        }

        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                result ?: return
                val device     = result.device
                val rssi       = result.rssi
                val serviceData = result.scanRecord
                    ?.getServiceData(BleConstants.THUNDERPASS_SERVICE_PARCEL)

                // ThunderPass advertisements carry only the service UUID — adding 18 bytes
                // of service data would exceed the 31-byte BLE payload limit and trigger
                // ADVERTISE_FAILED_DATA_TOO_LARGE (see startAdvertising).
                //
                // Use device.address as a session-scoped dedup key: it's stable within
                // an advertising session so it prevents redundant GATT connections to the
                // same hardware in one scan pass.  True 24-hour identity dedup is handled
                // post-GATT via the peer's installationId (see GattClient).
                val rotatingId = device.address

                Log.d(TAG, "Seen: ${device.address} rotId=${rotatingId.take(8)}… RSSI=$rssi")

                // Apply dedup + potentially initiate GATT exchange
                serviceScope.launch {
                    val encounterId = encounterDedup.onDeviceSeen(rotatingId, rssi)
                    if (encounterId != null) {
                        Log.i(TAG, "New encounter #$encounterId — initiating GATT exchange with ${device.address}")
                        showEncounterNotification(encounterId)
                        com.thunderpass.widget.EncounterWidget.refresh(applicationContext)
                        gattClient.connect(device, encounterId)
                    } else if (gattClient.shouldReconnectPaired(device.address)) {
                        // Known paired device — bypass encounter dedup for delta sync.
                        // encounterId=0 signals GattClient this is a paired-only reconnection.
                        Log.i(TAG, "Paired device reconnect for ${device.address} — bypassing encounter dedup")
                        gattClient.connect(device, 0L)
                    } else {
                        Log.d(TAG, "Dedup suppressed GATT for ${device.address} (rotId=${rotatingId.take(8)}…)")
                    }
                }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "Scan failed: errorCode=$errorCode")
                // Codes 1 (ALREADY_STARTED) and 4 (FEATURE_UNSUPPORTED) are not recoverable.
                // Codes 2 (APPLICATION_REGISTRATION_FAILED) and 3 (INTERNAL_ERROR) usually are.
                val recoverable = errorCode == android.bluetooth.le.ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED ||
                                  errorCode == android.bluetooth.le.ScanCallback.SCAN_FAILED_INTERNAL_ERROR
                if (recoverable && _isActive && !_safeZoneActive) {
                    serviceScope.launch {
                        kotlinx.coroutines.delay(5_000L)
                        if (_isActive && !_safeZoneActive) {
                            Log.i(TAG, "Restarting scanner after failure (code=$errorCode)\u2026")
                            stopScanning()
                            startScanning()
                        }
                    }
                }
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

    // ── Profile cache for GattServer ───────────────────────────────────────────

    /** Fetches profile + stats from DB and updates the cache. Call at service start to warm cache. */
    private fun refreshProfileCache() {
        runBlocking(Dispatchers.IO) {
            val db = ThunderPassDatabase.getInstance(this@BleService)
            cachedProfile = db.myProfileDao().get()
            val encounterDao = db.encounterDao()
            val passes = encounterDao.countAll()
            val encounters = encounterDao.getAll()
            cachedStats = BleStats(
                passesCount = passes,
                badgesCount = com.thunderpass.ui.ALL_BADGES.count { it.tier > 0 },
                streakCount = computeEncounterStreak(encounters),
            )
            profileCacheTimestampMs = System.currentTimeMillis()
        }
    }

    /** Returns cached profile if warm; else fetches and updates cache. BlePayloadProto outer layer built fresh per exchange. */
    private fun getProfileForExchange(): com.thunderpass.data.db.entity.MyProfile? {
        val now = System.currentTimeMillis()
        if (cachedProfile != null && (now - profileCacheTimestampMs) < PROFILE_CACHE_TTL_MS) {
            return cachedProfile
        }
        return runBlocking(Dispatchers.IO) {
            val db = ThunderPassDatabase.getInstance(this@BleService)
            val profile = db.myProfileDao().get()
            val encounterDao = db.encounterDao()
            cachedProfile = profile
            cachedStats = BleStats(
                passesCount = encounterDao.countAll(),
                badgesCount = com.thunderpass.ui.ALL_BADGES.count { it.tier > 0 },
                streakCount = computeEncounterStreak(encounterDao.getAll()),
            )
            profileCacheTimestampMs = System.currentTimeMillis()
            profile
        }
    }

    /** Returns cached stats if warm; else fetches (and updates profile cache). */
    private fun getStatsForExchange(): BleStats? {
        val now = System.currentTimeMillis()
        if (cachedStats != null && (now - profileCacheTimestampMs) < PROFILE_CACHE_TTL_MS) {
            return cachedStats
        }
        runBlocking(Dispatchers.IO) {
            val db = ThunderPassDatabase.getInstance(this@BleService)
            cachedProfile = db.myProfileDao().get()
            val encounterDao = db.encounterDao()
            cachedStats = BleStats(
                passesCount = encounterDao.countAll(),
                badgesCount = com.thunderpass.ui.ALL_BADGES.count { it.tier > 0 },
                streakCount = computeEncounterStreak(encounterDao.getAll()),
            )
            profileCacheTimestampMs = System.currentTimeMillis()
        }
        return cachedStats
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
        // Encounter alert channel (heads-up) — v2: yellow LED blink
        nm.createNotificationChannel(
            NotificationChannel(
                BleConstants.ENCOUNTER_CHANNEL_ID,
                getString(R.string.encounter_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Alerts when a new traveler is discovered nearby"
                setShowBadge(true)
                enableLights(true)
                lightColor = android.graphics.Color.YELLOW
            }
        )
        // Paired-device sync channel
        nm.createNotificationChannel(
            NotificationChannel(
                BleConstants.PAIRED_SYNC_CHANNEL_ID,
                "Device Sync",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Alerts when a paired device is nearby and syncing data"
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
            .setLights(android.graphics.Color.YELLOW, 300, 200)
            .build()
        getSystemService(NotificationManager::class.java)
            .notify(BleConstants.NOTIF_ID + 1, notif)
    }

    /**
     * Flashes the AYN Thor joystick LEDs yellow 3× then restores them to white.
     * SN3112 LED driver sysfs interface (AYN Thor — left and right joystick):
     *   /sys/class/sn3112l/led/brightness   ← left  joystick LED
     *   /sys/class/sn3112r/led/brightness   ← right joystick LED
     *
     * Command format (per LED, per index):  "{index}-{R}:{G}:{B}:{brightness}"
     *   index 1 = top LED, index 2 = bottom LED (each joystick has two LEDs)
     *   e.g. "1-255:212:0:255" = top LED, yellow (#FFD400), full brightness
     *
     * Commands are sent via PServerBinder (a privileged AYN system service) using
     * reflection on android.os.ServiceManager — same mechanism as Bifrost.
     * Falls back to direct FileOutputStream if the binder is unavailable.
     *
     * Silently no-ops on non-AYN devices or if the sysfs node is inaccessible.
     */
    private fun flashThorLeds() {
        val prefs = getSharedPreferences("tp_settings", android.content.Context.MODE_PRIVATE)
        if (!prefs.getBoolean("led_flash_enabled", true)) return

        val isAyn = android.os.Build.MANUFACTURER.equals("AYN", ignoreCase = true) ||
                    android.os.Build.BRAND.equals("AYN", ignoreCase = true)
        if (!isAyn) return

        serviceScope.launch(Dispatchers.IO) {
            // SN3112 format: "{index}-{R}:{G}:{B}:{brightness}"
            // yellow = #FFD400 (R:255, G:212, B:0)
            val yellowOn = listOf("1-255:212:0:255", "2-255:212:0:255")
            val off      = listOf("1-0:0:0:0",       "2-0:0:0:0")
            val restore  = listOf("1-255:255:255:255","2-255:255:255:255")

            // Obtain PServerBinder via reflection (no special Android permission needed).
            val pServerBinder: android.os.IBinder? = runCatching {
                val sm = Class.forName("android.os.ServiceManager")
                val getService = sm.getDeclaredMethod("getService", String::class.java)
                getService.invoke(null, "PServerBinder") as? android.os.IBinder
            }.getOrNull()

            fun sendCommand(value: String, path: String) {
                val cmd = "echo $value > $path"
                if (pServerBinder != null) {
                    runCatching {
                        val data  = android.os.Parcel.obtain()
                        val reply = android.os.Parcel.obtain()
                        try {
                            data.writeStringArray(arrayOf(cmd, "1"))
                            pServerBinder.transact(0, data, reply, android.os.IBinder.FLAG_ONEWAY)
                        } finally {
                            data.recycle()
                            reply.recycle()
                        }
                    }
                } else {
                    // Fallback: direct write (file is world-writable on AYN Thor)
                    runCatching {
                        java.io.FileOutputStream(path).use { it.write(value.toByteArray()) }
                    }
                }
            }

            fun writeLeds(values: List<String>) {
                for (path in listOf("/sys/class/sn3112l/led/brightness",
                                    "/sys/class/sn3112r/led/brightness")) {
                    for (v in values) sendCommand(v, path)
                }
            }

            try {
                repeat(3) {
                    writeLeds(yellowOn)
                    delay(300)
                    writeLeds(off)
                    delay(200)
                }
            } finally {
                runCatching { writeLeds(restore) }
            }
        }
    }

    /** Update the encounter notification once we know the peer's display name. */
    private fun updateEncounterNotification(encounterId: Long, displayName: String) {
        // ⚡ "The Spark" — double-pulse haptic feedback on successful profile exchange
        // Null-guard: VibratorManager can be null on custom ROMs (OdinOS)
        val settingsPrefs = getSharedPreferences("tp_settings", android.content.Context.MODE_PRIVATE)
        if (settingsPrefs.getBoolean("vibration_enabled", true)) {
            val vibrator = getSystemService(VibratorManager::class.java)?.defaultVibrator
            // Pattern: off 0ms → buzz 80ms → pause 120ms → buzz 250ms
            vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 80, 120, 250), -1))
        }
        flashThorLeds()

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
            .setLights(android.graphics.Color.YELLOW, 300, 200)
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
        val text = when {
            _safeZoneActive  -> "\uD83D\uDEE1\uFE0F Safe Zone active \u2014 BLE paused"
            _autoWalkPaused  -> "\uD83D\uDEB6 Waiting for motion\u2026"
            else             -> getString(R.string.notification_text)
        }
        return NotificationCompat.Builder(this, BleConstants.NOTIF_CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(tapIntent)
            .setOngoing(true)
            .build()
    }

    // ── Auto-Walk helpers ─────────────────────────────────────────────────────

    private fun startAutoWalkWatch() {
        val sm = getSystemService(SensorManager::class.java) ?: return
        walkSensorManager = sm
        val sensor = sm.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
        if (sensor == null) {
            Log.w(TAG, "Auto-Walk: no step detector — feature unavailable on this device.")
            return
        }
        sm.registerListener(autoWalkListener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        // Coroutine that auto-pauses BLE if no steps for STILL_TIMEOUT_MS
        stillCheckJob?.cancel()
        stillCheckJob = serviceScope.launch {
            val STILL_TIMEOUT_MS = 10 * 60 * 1_000L   // 10 minutes
            val CHECK_INTERVAL   =      60 * 1_000L   //  1 minute
            while (true) {
                delay(CHECK_INTERVAL)
                if (!_autoWalkEnabled) break
                val idleMs = System.currentTimeMillis() - lastStepDetectedMs
                if (idleMs >= STILL_TIMEOUT_MS && !_autoWalkPaused && _isActive && !_safeZoneActive) {
                    _autoWalkPaused = true
                    stopScanning()
                    stopAdvertising()
                    Log.i(TAG, "Auto-Walk: idle for ${idleMs / 60_000} min — BLE paused.")
                    refreshNotification()
                }
            }
        }
        Log.i(TAG, "Auto-Walk: step detector registered.")
    }

    private fun stopAutoWalkWatch() {
        stillCheckJob?.cancel()
        stillCheckJob = null
        walkSensorManager?.unregisterListener(autoWalkListener)
        walkSensorManager = null
        _autoWalkPaused = false
    }

    private fun refreshNotification() {
        getSystemService(android.app.NotificationManager::class.java)
            .notify(BleConstants.NOTIF_ID, buildNotification())
    }
}
