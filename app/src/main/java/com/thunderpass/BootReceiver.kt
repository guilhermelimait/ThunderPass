package com.thunderpass

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/**
 * Restarts BleService after device reboot if the user had scanning enabled before power-off.
 *
 * Triggered by BOOT_COMPLETED (normal reboot) and QUICKBOOT_POWERON (MediaTek fast-boot
 * and some OEM warm-boot paths, e.g. AYN Thor).
 *
 * The receiver only starts the service if [BleService.PREF_SERVICE_ACTIVE] was true at
 * the time of shutdown — it honours the user's explicit on/off choice.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != "android.intent.action.QUICKBOOT_POWERON"
        ) return

        val prefs = context.getSharedPreferences(BleService.PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(BleService.PREF_BLE_ENABLED, true)) return
        if (!prefs.getBoolean(BleService.PREF_SERVICE_ACTIVE, false)) return

        ContextCompat.startForegroundService(
            context,
            Intent(context, BleService::class.java).apply { action = BleService.ACTION_START }
        )
    }
}
