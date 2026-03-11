package com.thunderpass.ble

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Singleton that holds the most recent paired-device sighting from BLE delta sync.
 * Observed by DeviceSyncScreen to show "your device is nearby" info.
 */
object NearbyDeviceState {

    data class NearbyDevice(
        val displayName: String,
        val deviceType: String,
        val instId: String,
        val lastSyncMs: Long,
        val synced: Boolean,
    )

    private val _device = MutableStateFlow<NearbyDevice?>(null)
    val device: StateFlow<NearbyDevice?> = _device

    fun onPairedDeviceSeen(
        displayName: String,
        deviceType: String,
        instId: String,
        synced: Boolean,
    ) {
        _device.value = NearbyDevice(
            displayName = displayName,
            deviceType  = deviceType,
            instId      = instId,
            lastSyncMs  = System.currentTimeMillis(),
            synced      = synced,
        )
    }

    fun clear() { _device.value = null }
}
