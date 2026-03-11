package com.thunderpass

import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.thunderpass.BleService
import com.thunderpass.ui.ThunderPassNavGraph

class MainActivity : ComponentActivity() {

    private var mediaPlayer: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Prevent screenshots, screen recording, and Recent Apps thumbnail
        // from capturing sensitive profile data (RA API key, etc.).
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE,
        )
        enableEdgeToEdge()
        handleFriendInviteDeepLink(intent)
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
        // Stop the BLE service when the app is fully closed (back-press / swipe from recents).
        // isFinishing distinguishes a real close from a configuration change (screen rotation).
        if (isFinishing) {
            startService(Intent(this, BleService::class.java).apply {
                action = BleService.ACTION_STOP
            })
        }
    }

    // Handle deep link when app is already running (singleTop)
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleFriendInviteDeepLink(intent)
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

    // ── Friend-invite deep-link ───────────────────────────────────────

    /**
     * Handles [thunderpass://add-friend/{userId}] URIs.
     * Saves the pending userId to SharedPrefs so HomeViewModel can resolve it
     * (look up the existing encounter and mark as friend, or prompt the user to
     * walk near the sender if no encounter exists yet).
     */
    private fun handleFriendInviteDeepLink(intent: Intent?) {
        val uri: Uri = intent?.data ?: return
        if (uri.scheme != "thunderpass" || uri.host != "add-friend") return
        // Bound the input: installation IDs are UUIDs (36 chars). Discard anything longer
        // to prevent accidentally storing attacker-controlled data of unbounded length.
        val peerInstId = uri.lastPathSegment
            ?.takeIf { it.isNotBlank() && it.length <= 64 } ?: return
        getSharedPreferences("tp_settings", MODE_PRIVATE)
            .edit()
            .putString("pending_friend_invite", peerInstId)
            .apply()
    }
}

