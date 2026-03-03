package com.thunderpass.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel

private val BLE_PERMISSIONS = arrayOf(
    Manifest.permission.BLUETOOTH_SCAN,
    Manifest.permission.BLUETOOTH_ADVERTISE,
    Manifest.permission.BLUETOOTH_CONNECT,
    Manifest.permission.POST_NOTIFICATIONS,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToEncounters: () -> Unit,
    onNavigateToProfile: () -> Unit,
    vm: HomeViewModel = viewModel(),
) {
    val context          = LocalContext.current
    val serviceRunning   by vm.serviceRunning.collectAsState()
    val encounterCount   by vm.encounterCount.collectAsState()
    val installationId   by vm.installationId.collectAsState()

    var allGranted by remember {
        mutableStateOf(
            BLE_PERMISSIONS.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
        )
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results -> allGranted = results.values.all { it } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("⚡ ThunderPass") },
                actions = {
                    IconButton(onClick = onNavigateToEncounters) {
                        Icon(Icons.Default.List, contentDescription = "Encounters")
                    }
                    // Tappable avatar leads to the Profile screen
                    DiceBearAvatar(
                        seed = installationId.ifEmpty { "default" },
                        size = 36.dp,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { onNavigateToProfile() }
                    )
                    Spacer(Modifier.width(8.dp))
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (!allGranted) {
                PermissionPrompt { permLauncher.launch(BLE_PERMISSIONS) }
            } else {
                StatusCard(serviceRunning, encounterCount)

                Spacer(Modifier.height(24.dp))

                Button(
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (serviceRunning)
                            MaterialTheme.colorScheme.error
                        else
                            MaterialTheme.colorScheme.primary
                    ),
                    onClick = {
                        if (serviceRunning) vm.stopService() else vm.startService()
                    }
                ) {
                    Text(if (serviceRunning) "Stop ThunderPass" else "Start ThunderPass")
                }

                Spacer(Modifier.height(32.dp))

                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onNavigateToEncounters,
                ) {
                    Text("View Encounters ($encounterCount)")
                }
            }
        }
    }
}

@Composable
private fun StatusCard(running: Boolean, count: Int) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (running) "🟢" else "⚪",
                    fontSize = 20.sp,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (running) "Active" else "Idle",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (running)
                    "Scanning and advertising to nearby ThunderPass devices…"
                else
                    "Tap Start to begin exchanging profiles.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (count > 0) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "$count encounter${if (count == 1) "" else "s"} recorded",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun PermissionPrompt(onGrant: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "Permissions needed",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "ThunderPass needs Bluetooth Scan, Advertise, Connect, and " +
                    "Notification permissions to discover and exchange profiles with nearby devices.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onGrant) {
            Text("Grant Permissions")
        }
    }
}
