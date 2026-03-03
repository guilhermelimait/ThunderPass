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
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Intent
import android.os.IBinder
import android.os.ParcelUuid
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.util.UUID

// ─────────────────────────────────────────────────────────────────────────────
// ThunderPass BLE constants (see SPEC.md)
// ─────────────────────────────────────────────────────────────────────────────
private const val TAG = "ThunderPass/BleService"

/**
 * Primary Service UUID that identifies ThunderPass presence packets.
 * Replace with a stable UUID before 1.0 release.
 */
val THUNDERPASS_SERVICE_UUID: UUID =
    UUID.fromString("12345678-1234-1234-1234-1234567890ab")

private val SERVICE_PARCEL_UUID = ParcelUuid(THUNDERPASS_SERVICE_UUID)

private const val NOTIF_CHANNEL_ID = "thunderpass_ble"
private const val NOTIF_ID = 1001

// ─────────────────────────────────────────────────────────────────────────────
// BleService
// A foreground service (type = connectedDevice) that concurrently:
//   • Advertises a ThunderPass presence packet (Broadcaster role).
//   • Scans for nearby ThunderPass presence packets (Discoverer role).
// GATT exchange (Exchanger role) will be added in a follow-up.
// ─────────────────────────────────────────────────────────────────────────────
class BleService : Service() {

    companion object {
        const val ACTION_START = "com.thunderpass.ble.START"
        const val ACTION_STOP  = "com.thunderpass.ble.STOP"
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var advertiser: BluetoothLeAdvertiser? = null
    private var scanner: BluetoothLeScanner? = null

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        val btManager = getSystemService(BluetoothManager::class.java)
        bluetoothAdapter = btManager?.adapter
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startForeground(NOTIF_ID, buildNotification())
                startAdvertising()
                startScanning()
            }
            ACTION_STOP -> {
                stopAdvertising()
                stopScanning()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopAdvertising()
        stopScanning()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── Advertising ───────────────────────────────────────────────────────────

    private fun startAdvertising() {
        val adapter = bluetoothAdapter ?: return
        if (!adapter.isEnabled) { Log.w(TAG, "Bluetooth disabled; cannot advertise."); return }
        if (!adapter.isMultipleAdvertisementSupported) {
            Log.w(TAG, "Device does not support BLE advertising.")
            return
        }

        advertiser = adapter.bluetoothLeAdvertiser

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER)
            .setConnectable(true)   // true = GATT exchange supported later
            .setTimeout(0)          // advertise indefinitely
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_LOW)
            .build()

        val data = AdvertiseData.Builder()
            .addServiceUuid(SERVICE_PARCEL_UUID)
            // TODO: add service data with presence packet (version, flags, rotatingId)
            .setIncludeDeviceName(false) // omit device name for privacy
            .build()

        advertiser?.startAdvertising(settings, data, advertiseCallback)
        Log.i(TAG, "BLE advertising started.")
    }

    private fun stopAdvertising() {
        try {
            advertiser?.stopAdvertising(advertiseCallback)
        } catch (e: Exception) {
            Log.w(TAG, "stopAdvertising error: ${e.message}")
        }
        advertiser = null
        Log.i(TAG, "BLE advertising stopped.")
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            Log.i(TAG, "Advertising onStartSuccess")
        }

        override fun onStartFailure(errorCode: Int) {
            Log.e(TAG, "Advertising onStartFailure: errorCode=$errorCode")
        }
    }

    // ── Scanning ──────────────────────────────────────────────────────────────

    private fun startScanning() {
        val adapter = bluetoothAdapter ?: return
        if (!adapter.isEnabled) { Log.w(TAG, "Bluetooth disabled; cannot scan."); return }

        scanner = adapter.bluetoothLeScanner

        val filter = ScanFilter.Builder()
            .setServiceUuid(SERVICE_PARCEL_UUID)
            .build()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
            .build()

        scanner?.startScan(listOf(filter), settings, scanCallback)
        Log.i(TAG, "BLE scanning started.")
    }

    private fun stopScanning() {
        try {
            scanner?.stopScan(scanCallback)
        } catch (e: Exception) {
            Log.w(TAG, "stopScan error: ${e.message}")
        }
        scanner = null
        Log.i(TAG, "BLE scanning stopped.")
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result ?: return
            val device = result.device
            val rssi   = result.rssi
            Log.d(TAG, "Found ThunderPass device: ${device.address}  RSSI=$rssi")
            // TODO: apply dedup rules (see SPEC.md § Encounter Rules)
            // TODO: initiate GATT exchange if not recently seen
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "Scan failed: errorCode=$errorCode")
        }
    }

    // ── Notification ──────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        val name = getString(R.string.notification_channel_name)
        val channel = NotificationChannel(
            NOTIF_CHANNEL_ID,
            name,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "ThunderPass BLE foreground service"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val tapIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, tapIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, NOTIF_CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(android.R.drawable.ic_dialog_info) // replace with custom icon
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
}
