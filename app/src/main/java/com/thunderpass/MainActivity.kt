package com.thunderpass

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.thunderpass.supabase.SupabaseManager
import com.thunderpass.ui.ThunderPassNavGraph
import io.github.jan.supabase.auth.handleDeeplinks

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleSupabaseDeepLink(intent)
        // Theme is managed inside ThunderPassNavGraph (supports dark-mode toggle)
        setContent { ThunderPassNavGraph() }
    }

    // Handle deep link when app is already running (singleTop)
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleSupabaseDeepLink(intent)
    }

    private fun handleSupabaseDeepLink(intent: Intent?) {
        val uri: Uri = intent?.data ?: return
        if (uri.scheme == "thunderpass" && uri.host == "callback") {
            SupabaseManager.client.handleDeeplinks(intent)
        }
    }
}
