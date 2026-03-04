package com.thunderpass

import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
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
        setContent { ThunderPassNavGraph() }
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

    // ── Supabase deep-link ────────────────────────────────────────────────────

    private fun handleSupabaseDeepLink(intent: Intent?) {
        val uri: Uri = intent?.data ?: return
        if (uri.scheme == "thunderpass" && uri.host == "callback") {
            SupabaseManager.client.handleDeeplinks(intent)
        }
    }
}

