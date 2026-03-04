package com.thunderpass

import android.annotation.SuppressLint
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.thunderpass.BleService
import com.thunderpass.supabase.SupabaseManager
import com.thunderpass.ui.ThunderPassNavGraph
import io.github.jan.supabase.auth.handleDeeplinks

class MainActivity : ComponentActivity() {

    private var mediaPlayer: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleSupabaseDeepLink(intent)
        requestIgnoreBatteryOptimizationsIfNeeded()
        setContent {
            ThunderPassNavGraph(onMusicChange = { enabled ->
                if (enabled) startMusicIfEnabled() else { mediaPlayer?.pause() }
            })
        }
    }

    override fun onResume() {
        super.onResume()
        applyScreenOnFlag()
        startMusicIfEnabled()
    }

    override fun onPause() {
        super.onPause()
        mediaPlayer?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    // Handle deep link when app is already running (singleTop)
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleSupabaseDeepLink(intent)
    }

    // ── Background music ──────────────────────────────────────────────────────

    private fun startMusicIfEnabled() {
        val prefs = getSharedPreferences("tp_settings", MODE_PRIVATE)
        if (!prefs.getBoolean("music_enabled", true)) return

        if (mediaPlayer == null) {
            runCatching {
                mediaPlayer = MediaPlayer().apply {
                    val afd = assets.openFd("thunderpass-bg.mp3")
                    setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                    afd.close()
                    isLooping = true
                    setVolume(0.5f, 0.5f)
                    prepare()
                    start()
                }
            }
        } else if (!mediaPlayer!!.isPlaying) {
            mediaPlayer!!.start()
        }
    }

    // ── Screen-on flag ────────────────────────────────────────────────────────

    private fun applyScreenOnFlag() {
        val prefs         = getSharedPreferences("tp_settings", MODE_PRIVATE)
        val blePrefs      = getSharedPreferences(BleService.PREFS_NAME, MODE_PRIVATE)
        val screenOnPref  = prefs.getBoolean("screen_on_active", true)
        val serviceActive = blePrefs.getBoolean(BleService.PREF_SERVICE_ACTIVE, false)

        if (screenOnPref && serviceActive) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // ── Modify system settings (AYN Thor joystick LEDs) ─────────────────────

    /**
     * On first launch, opens the "Modify system settings" special-access page
     * so the user can grant WRITE_SETTINGS. Required to flash AYN Thor joystick
     * LEDs via Settings.System on encounter. Silently no-ops on other devices.
     */
    private fun requestWriteSettingsIfNeeded() {
        runCatching {
            if (Settings.System.canWrite(this)) return
            val prefs = getSharedPreferences("tp_settings", MODE_PRIVATE)
            if (prefs.getBoolean("write_settings_prompt_shown", false)) return
            prefs.edit().putBoolean("write_settings_prompt_shown", true).apply()
            startActivity(
                Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                    data = Uri.parse("package:$packageName")
                }
            )
        }
    }

    // ── Battery optimization whitelist ──────────────────────────────────────

    /**
     * On first launch, if the app is not already exempt from battery restrictions,
     * open the system dialog so the user can add it to the Doze whitelist.
     * Without this, BLE scanning is throttled or killed in background.
     */
    @SuppressLint("BatteryLife")
    private fun requestIgnoreBatteryOptimizationsIfNeeded() {
        runCatching {
            val prefs = getSharedPreferences("tp_settings", MODE_PRIVATE)
            if (prefs.getBoolean("doze_prompt_shown", false)) return
            val pm = getSystemService(PowerManager::class.java) ?: return
            if (pm.isIgnoringBatteryOptimizations(packageName)) return
            prefs.edit().putBoolean("doze_prompt_shown", true).apply()
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
            )
        }
    }

    // ── Supabase deep-link ────────────────────────────────────────────────────

    private fun handleSupabaseDeepLink(intent: Intent?) {
        val uri: Uri = intent?.data ?: return
        if (uri.scheme == "thunderpass" && uri.host == "callback") {
            SupabaseManager.client.handleDeeplinks(intent)
        }
    }
}

