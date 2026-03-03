package com.thunderpass

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.thunderpass.ui.ThunderPassNavGraph
import com.thunderpass.ui.theme.ThunderPassTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Render behind system bars — status bar becomes transparent on dark bg
        enableEdgeToEdge()
        setContent {
            ThunderPassTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color    = androidx.compose.material3.MaterialTheme.colorScheme.background,
                ) {
                    ThunderPassNavGraph()
                }
            }
        }
    }
}
