package com.thunderpass

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

// ─────────────────────────────────────────────────────────────────────────────
// Required BLE + notification permissions for Android 13 (API 33)
// ─────────────────────────────────────────────────────────────────────────────
private val BLE_PERMISSIONS = arrayOf(
    Manifest.permission.BLUETOOTH_SCAN,
    Manifest.permission.BLUETOOTH_ADVERTISE,
    Manifest.permission.BLUETOOTH_CONNECT,
    Manifest.permission.POST_NOTIFICATIONS,
)

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ThunderPassHome()
                }
            }
        }
    }
}

@Composable
private fun ThunderPassHome() {
    val context = LocalContext.current

    // Track whether all BLE permissions are granted
    var allGranted by remember {
        mutableStateOf(
            BLE_PERMISSIONS.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
        )
    }

    // Track whether the foreground service is "running" (simplified state for MVP)
    var serviceRunning by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        allGranted = results.values.all { it }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "⚡ ThunderPass",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "StreetPass for Android",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(40.dp))

        if (!allGranted) {
            Text(
                text = "ThunderPass needs Bluetooth and notification permissions to discover nearby devices.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = { permissionLauncher.launch(BLE_PERMISSIONS) }) {
                Text("Grant Permissions")
            }
        } else {
            // Status card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (serviceRunning) "Status: Active 🟢" else "Status: Idle ⚪",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (serviceRunning)
                            "Scanning for nearby ThunderPass devices…"
                        else
                            "Tap Start to begin exchanging profiles.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = {
                    val intent = Intent(context, BleService::class.java).apply {
                        action = if (serviceRunning)
                            BleService.ACTION_STOP
                        else
                            BleService.ACTION_START
                    }
                    if (!serviceRunning) {
                        ContextCompat.startForegroundService(context, intent)
                    } else {
                        context.startService(intent)
                    }
                    serviceRunning = !serviceRunning
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (serviceRunning) "Stop ThunderPass" else "Start ThunderPass")
            }

            Spacer(Modifier.height(40.dp))

            // Placeholder: encounter list will go here
            Text(
                text = "Encounters",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "No encounters yet. Go outside! 🚶",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
