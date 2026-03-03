package com.thunderpass.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.thunderpass.ble.ScanMode
import java.util.Calendar

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
    onNavigateToDetail: (Long) -> Unit = {},
    vm: HomeViewModel = viewModel(),
) {
    val context        = LocalContext.current
    val serviceRunning by vm.serviceRunning.collectAsState()
    val encounterCount by vm.encounterCount.collectAsState()
    val encounterStreak by vm.encounterStreak.collectAsState()
    val installationId by vm.installationId.collectAsState()
    val displayName    by vm.displayName.collectAsState()
    val encounters     by vm.encounters.collectAsState()
    val scanMode       by vm.scanMode.collectAsState()

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

    // Compute start-of-today for the "Today" stat (seenAt is epoch millis)
    val todayStart = remember {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    val todayCount = remember(encounters) {
        encounters.count { it.encounter.seenAt >= todayStart }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = true,
                    onClick  = { /* already on Home */ },
                    icon     = { Icon(Icons.Filled.Home, contentDescription = "Home") },
                    label    = { Text("Home") },
                )
                NavigationBarItem(
                    selected = false,
                    onClick  = onNavigateToEncounters,
                    icon     = { Icon(Icons.Filled.List, contentDescription = "Encounters") },
                    label    = { Text("Encounters") },
                )
                NavigationBarItem(
                    selected = false,
                    onClick  = onNavigateToProfile,
                    icon     = { Icon(Icons.Filled.Person, contentDescription = "Profile") },
                    label    = { Text("Profile") },
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .windowInsetsPadding(WindowInsets.statusBars)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(20.dp))

            if (!allGranted) {
                PermissionPrompt { permLauncher.launch(BLE_PERMISSIONS) }
            } else {
                // ── Greeting + avatar row ─────────────────────────────────────
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
                        Text(
                            text  = if (serviceRunning) "Scanning nearby…" else "Tap to start scanning",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    DiceBearAvatar(
                        seed     = installationId.ifEmpty { "default" },
                        size     = 52.dp,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { onNavigateToProfile() },
                    )
                }

                Spacer(Modifier.height(28.dp))

                // ── Large pill scanning toggle ─────────────────────────────────
                PillToggle(
                    active       = serviceRunning,
                    onActivate   = { vm.startService() },
                    onDeactivate = { vm.stopService() },
                )

                Spacer(Modifier.height(28.dp))

                // ── Recent Encounters ──────────────────────────────────────────
                Text(
                    text       = "Recent Encounters",
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color      = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(12.dp))

                if (encounters.isEmpty()) {
                    Box(
                        modifier         = Modifier
                            .fillMaxWidth()
                            .height(96.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text      = "None yet — go out and encounter someone!",
                            style     = MaterialTheme.typography.bodySmall,
                            color     = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                } else {
                    Row(
                        modifier              = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        encounters.take(20).forEach { ewp ->
                            val seed = ewp.snapshot?.rotatingId ?: ewp.encounter.rotatingId
                            val name = ewp.snapshot?.displayName ?: "Unknown"
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier            = Modifier
                                    .width(64.dp)
                                    .clickable { onNavigateToDetail(ewp.encounter.id) },
                            ) {
                                DiceBearAvatar(seed = seed, size = 52.dp)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text      = name,
                                    style     = MaterialTheme.typography.labelSmall,
                                    maxLines  = 1,
                                    overflow  = TextOverflow.Ellipsis,
                                    color     = MaterialTheme.colorScheme.onBackground,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(28.dp))

                // ── Stats row ──────────────────────────────────────────────────
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    StatCard(
                        modifier = Modifier.weight(1f),
                        label    = "Total",
                        value    = encounterCount.toString(),
                    )
                    StatCard(
                        modifier = Modifier.weight(1f),
                        label    = "Today",
                        value    = todayCount.toString(),
                    )
                    StatCard(
                        modifier = Modifier.weight(1f),
                        label    = "Streak 🔥",
                        value    = if (encounterStreak > 0) "${encounterStreak}d" else "—",
                    )
                }

                Spacer(Modifier.height(24.dp))

                // ── Scan intensity segmented control ───────────────────────────
                Text(
                    text  = "Scan intensity",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        shape    = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        selected = scanMode == ScanMode.BALANCED,
                        onClick  = { vm.setScanMode(ScanMode.BALANCED) },
                    ) { Text("Balanced ⚡") }
                    SegmentedButton(
                        shape    = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        selected = scanMode == ScanMode.AGGRESSIVE,
                        onClick  = { vm.setScanMode(ScanMode.AGGRESSIVE) },
                    ) { Text("Aggressive 🔥") }
                }

                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Pill toggle (Scanning / Idle)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PillToggle(
    active: Boolean,
    onActivate: () -> Unit,
    onDeactivate: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        // "Scanning" segment
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(50))
                .background(
                    if (active) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                )
                .clickable { if (!active) onActivate() },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text       = "⚡ Scanning",
                fontWeight = FontWeight.SemiBold,
                color      = if (active) MaterialTheme.colorScheme.onPrimaryContainer
                             else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // "Idle" segment
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(50))
                .background(
                    if (!active) MaterialTheme.colorScheme.secondaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                )
                .clickable { if (active) onDeactivate() },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text       = "Idle",
                fontWeight = FontWeight.SemiBold,
                color      = if (!active) MaterialTheme.colorScheme.onSecondaryContainer
                             else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Stat card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StatCard(modifier: Modifier = Modifier, label: String, value: String) {
    Card(
        modifier = modifier,
        colors   = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier            = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text       = value,
                style      = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text      = label,
                style     = MaterialTheme.typography.labelMedium,
                color     = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
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
