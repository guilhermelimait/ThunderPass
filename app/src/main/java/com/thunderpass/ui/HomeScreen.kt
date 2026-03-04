package com.thunderpass.ui

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import android.content.res.Configuration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.tooling.preview.Preview

private val BLE_PERMISSIONS = arrayOf(
    Manifest.permission.BLUETOOTH_SCAN,
    Manifest.permission.BLUETOOTH_ADVERTISE,
    Manifest.permission.BLUETOOTH_CONNECT,
    Manifest.permission.POST_NOTIFICATIONS,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToDetail: (Long) -> Unit = {},
    onNavigate: (String) -> Unit = {},
    vm: HomeViewModel = viewModel(),
) {
    val context        = LocalContext.current
    val serviceRunning by vm.serviceRunning.collectAsState()
    val joulesTotal     by vm.joulesTotal.collectAsState()
    val installationId by vm.installationId.collectAsState()
    val displayName    by vm.displayName.collectAsState()
    val encounters     by vm.encounters.collectAsState()

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

    // BT enable launcher: when system dialog confirms BT on, start the service
    val btEnableLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) vm.startService()
    }

    HomeScreenContent(
        allGranted = allGranted,
        displayName = displayName,
        serviceRunning = serviceRunning,
        installationId = installationId,
        encounters = encounters,
        joulesTotal = joulesTotal,
        onToggleService = {
            if (serviceRunning) {
                vm.stopService()
            } else {
                val btAdapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
                if (btAdapter?.isEnabled == true) {
                    vm.startService()
                } else {
                    @Suppress("DEPRECATION")
                    btEnableLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                }
            }
        },
        onNavigateToDetail = onNavigateToDetail,
        onNavigate = onNavigate,
        onGrantPermissions = { permLauncher.launch(BLE_PERMISSIONS) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreenContent(
    allGranted: Boolean,
    displayName: String,
    serviceRunning: Boolean,
    installationId: String,
    encounters: List<EncounterWithProfile>,
    joulesTotal: Long,
    onToggleService: () -> Unit,
    onNavigateToDetail: (Long) -> Unit,
    onNavigate: (String) -> Unit = {},
    onGrantPermissions: () -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

        if (!allGranted) {
            Box(
                modifier         = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.Center,
            ) {
                PermissionPrompt { onGrantPermissions() }
            }
        } else if (isLandscape) {
            // ── Landscape: two-panel layout ──────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Left panel — identity + controls
                Column(
                    modifier = Modifier
                        .weight(0.42f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // Greeting row
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier              = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text       = "Hi, $displayName 👋",
                                style      = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color      = MaterialTheme.colorScheme.onBackground,
                            )
                            Text(
                                text  = if (serviceRunning) "Scanning nearby…" else "Tap to start scanning",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        DiceBearAvatar(
                            seed     = installationId.ifEmpty { "default" },
                            size     = 40.dp,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { onNavigate("profile") },
                        )
                    }
                    // ThunderPass ON/OFF toggle
                    ThunderPassToggleCard(
                        active   = serviceRunning,
                        onToggle = onToggleService,
                    )
                }

                VerticalDivider(
                    modifier  = Modifier
                        .fillMaxHeight()
                        .padding(vertical = 12.dp),
                    thickness = 1.dp,
                    color     = MaterialTheme.colorScheme.outlineVariant,
                )

                // Right panel — walking animation + recent bypassers
                Column(
                    modifier = Modifier
                        .weight(0.58f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    WalkingSceneCard(
                        avatarSeed     = installationId.ifEmpty { "default" },
                        serviceRunning = serviceRunning,
                    )
                    val lastEnc = encounters.firstOrNull()
                    if (lastEnc != null) {
                        LastPassedByCard(
                            encounter = lastEnc,
                            onClick   = { onNavigateToDetail(lastEnc.encounter.id) },
                        )
                    }
                }
            }
        } else {
            // ── Portrait layout ───────────────────────────────────────────────
            // No verticalScroll — animation expands to fill all available height.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 20.dp),
            ) {
                Spacer(Modifier.height(16.dp))

                // ── Greeting + avatar row ──────────────────────────────────────
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text       = "Hi, $displayName 👋",
                            style      = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color      = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                    DiceBearAvatar(
                        seed     = installationId.ifEmpty { "default" },
                        size     = 52.dp,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { onNavigate("profile") },
                    )
                }

                Spacer(Modifier.height(12.dp))

                // ── Animation + toggle overlay — fills all remaining height ────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    // Animated scene fills the box
                    WalkingSceneCard(
                        avatarSeed     = installationId.ifEmpty { "default" },
                        serviceRunning = serviceRunning,
                        fillHeight     = true,
                    )
                    // Toggle floats at the bottom of the animation, transparent bg
                    ThunderPassToggleCard(
                        active      = serviceRunning,
                        onToggle    = onToggleService,
                        transparent = true,
                        modifier    = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                    )
                }

                Spacer(Modifier.height(12.dp))

                // ── Last Passed By ─────────────────────────────────────────────
                val lastEnc = encounters.firstOrNull()
                if (lastEnc != null) {
                    LastPassedByCard(
                        encounter = lastEnc,
                        onClick   = { onNavigateToDetail(lastEnc.encounter.id) },
                    )
                }

                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ThunderPass ON/OFF toggle card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ThunderPassToggleCard(
    active:      Boolean,
    onToggle:    () -> Unit,
    transparent: Boolean  = false,
    modifier:    Modifier = Modifier,
) {
    val containerColor = when {
        transparent && active -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.88f)
        transparent           -> MaterialTheme.colorScheme.surface.copy(alpha = 0.82f)
        active                -> MaterialTheme.colorScheme.primaryContainer
        else                  -> MaterialTheme.colorScheme.surfaceVariant
    }
    Card(
        modifier  = modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (transparent) 0.dp else 2.dp,
        ),
    ) {
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    text       = "⚡ ThunderPass",
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color      = if (active)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text  = if (active) "Active — watching for Travelers" else "Tap to activate",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (active)
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                )
            }
            Switch(checked = active, onCheckedChange = { onToggle() })
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Joules info card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun JoulesInfoCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        ),
    ) {
        Column(
            modifier            = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text       = "HOW TO EARN JOULES",
                style      = MaterialTheme.typography.labelSmall,
                color      = MaterialTheme.colorScheme.primary,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            )
            Text(
                text  = "⚡ Meet a new Traveler via BLE — 100 J\n" +
                         "⚡ Unlock a Badge — 50–200 J\n" +
                         "⚡ RetroAchievements activity — up to 500 J\n" +
                         "⚡ Streak bonuses for daily Sparks",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Last Passed By strip
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun LastPassedByCard(encounter: EncounterWithProfile, onClick: () -> Unit) {
    val name = encounter.snapshot?.displayName
        ?.takeIf { it.isNotBlank() } ?: "Unknown Traveler"
    val seed = encounter.snapshot?.rotatingId ?: encounter.encounter.rotatingId
    val ago  = relativeTimeString(encounter.encounter.seenAt)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(
            modifier          = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DiceBearAvatar(
                seed     = seed,
                size     = 40.dp,
                modifier = Modifier.clip(CircleShape),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text       = "Last Passed By",
                    style      = MaterialTheme.typography.labelSmall,
                    color      = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text       = name,
                    style      = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis,
                )
            }
            Text(
                text  = ago,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Returns a compact human-readable relative time string (e.g. "2 h ago", "Just now"). */
internal fun relativeTimeString(epochMillis: Long): String {
    val diff = System.currentTimeMillis() - epochMillis
    return when {
        diff < 60_000L               -> "Just now"
        diff < 3_600_000L            -> "${diff / 60_000L} min ago"
        diff < 86_400_000L           -> "${diff / 3_600_000L} h ago"
        diff < 7 * 86_400_000L       -> "${diff / 86_400_000L} d ago"
        else                         -> "${diff / (7 * 86_400_000L)} wk ago"
    }
}


// ─────────────────────────────────────────────────────────────────────────────
// Permission prompt
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PermissionPrompt(onGrant: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier            = Modifier.padding(top = 64.dp),
    ) {
        Text(
            text       = "Permissions needed",
            style      = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text  = "ThunderPass needs Bluetooth Scan, Advertise, Connect, and " +
                    "Notification permissions to discover and exchange profiles with nearby devices.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onGrant) { Text("Grant Permissions") }
    }
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    MaterialTheme {
        HomeScreenContent(
            allGranted = true,
            displayName = "Gui",
            serviceRunning = true,
            installationId = "test-id",
            encounters = emptyList(),
            joulesTotal = 2500,
            onToggleService = {},
            onNavigateToDetail = {},
            onGrantPermissions = {}
        )
    }
}
