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
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingCart
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
    onNavigateToShop: () -> Unit = {},
    onNavigateToStickerBook: () -> Unit = {},
    vm: HomeViewModel = viewModel(),
) {
    val context        = LocalContext.current
    val serviceRunning by vm.serviceRunning.collectAsState()
    val encounterCount by vm.encounterCount.collectAsState()
    val encounterStreak by vm.encounterStreak.collectAsState()
    val joulesTotal     by vm.joulesTotal.collectAsState()
    val ownedStickers   by vm.ownedStickers.collectAsState()
    val installationId by vm.installationId.collectAsState()
    val displayName    by vm.displayName.collectAsState()
    val encounters     by vm.encounters.collectAsState()
    val scanMode       by vm.scanMode.collectAsState()
    val safeZoneActive by vm.safeZoneActive.collectAsState()

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
                    icon     = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Encounters") },
                    label    = { Text("Encounters") },
                )
                NavigationBarItem(
                    selected = false,
                    onClick  = onNavigateToProfile,
                    icon     = { Icon(Icons.Filled.Person, contentDescription = "Profile") },
                    label    = { Text("Profile") },
                )
                NavigationBarItem(
                    selected = false,
                    onClick  = onNavigateToShop,
                    icon     = { Icon(Icons.Filled.ShoppingCart, contentDescription = "Shop") },
                    label    = { Text("Shop") },
                )
            }
        }
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
                PermissionPrompt { permLauncher.launch(BLE_PERMISSIONS) }
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
                                .clickable { onNavigateToProfile() },
                        )
                    }
                    // Pill toggle
                    PillToggle(
                        active       = serviceRunning,
                        onActivate   = { vm.startService() },
                        onDeactivate = { vm.stopService() },
                    )
                    // Safe Zone
                    FilterChip(
                        selected = safeZoneActive,
                        onClick  = { vm.setSafeZone(!safeZoneActive) },
                        label    = {
                            Text(
                                if (safeZoneActive) "\uD83D\uDEE1\uFE0F Safe Zone — BLE paused"
                                else "\uD83D\uDEE1\uFE0F Safe Zone"
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    // Battery mode
                    Text(
                        text  = "Battery Mode",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            shape    = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                            selected = scanMode == ScanMode.OFF,
                            onClick  = { vm.setScanMode(ScanMode.OFF) },
                        ) { Text("Off 🌙") }
                        SegmentedButton(
                            shape    = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                            selected = scanMode == ScanMode.BALANCED,
                            onClick  = { vm.setScanMode(ScanMode.BALANCED) },
                        ) { Text("Balanced ⚡") }
                        SegmentedButton(
                            shape    = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                            selected = scanMode == ScanMode.AGGRESSIVE,
                            onClick  = { vm.setScanMode(ScanMode.AGGRESSIVE) },
                        ) { Text("Aggressive 🔥") }
                    }
                }

                VerticalDivider(
                    modifier  = Modifier
                        .fillMaxHeight()
                        .padding(vertical = 12.dp),
                    thickness = 1.dp,
                    color     = MaterialTheme.colorScheme.outlineVariant,
                )

                // Right panel — stats + encounters
                Column(
                    modifier = Modifier
                        .weight(0.58f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // Stats
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        StatCard(modifier = Modifier.weight(1f), label = "Total",     value = encounterCount.toString())
                        StatCard(modifier = Modifier.weight(1f), label = "Today",     value = todayCount.toString())
                        StatCard(modifier = Modifier.weight(1f), label = "Streak 🔥", value = if (encounterStreak > 0) "${encounterStreak}d" else "—")
                        StatCard(modifier = Modifier.weight(1f), label = "⚡ Energy",  value = "${joulesTotal}J")
                    }
                    OutlinedButton(
                        onClick  = onNavigateToStickerBook,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("🃴 Sticker Book · ${ownedStickers.size} collected") }
                    // Encounters
                    Text(
                        text       = "Recent Encounters",
                        style      = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color      = MaterialTheme.colorScheme.onBackground,
                    )
                    if (encounters.isEmpty()) {
                        Box(
                            modifier         = Modifier.fillMaxWidth().height(72.dp),
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
                                        .width(56.dp)
                                        .clickable { onNavigateToDetail(ewp.encounter.id) },
                                ) {
                                    DiceBearAvatar(seed = seed, size = 44.dp)
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
                }
            }
        } else {
            // ── Portrait layout (original) ───────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
            ) {
                Spacer(Modifier.height(20.dp))

                // ── Greeting + avatar row
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

                PillToggle(
                    active       = serviceRunning,
                    onActivate   = { vm.startService() },
                    onDeactivate = { vm.stopService() },
                )

                Spacer(Modifier.height(12.dp))

                FilterChip(
                    selected = safeZoneActive,
                    onClick  = { vm.setSafeZone(!safeZoneActive) },
                    label    = {
                        Text(
                            if (safeZoneActive) "\uD83D\uDEE1\uFE0F Safe Zone — BLE paused"
                            else "\uD83D\uDEE1\uFE0F Safe Zone"
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(16.dp))

                Text(
                    text       = "Recent Encounters",
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color      = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(12.dp))

                if (encounters.isEmpty()) {
                    Box(
                        modifier         = Modifier.fillMaxWidth().height(96.dp),
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

                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    StatCard(modifier = Modifier.weight(1f), label = "Total",     value = encounterCount.toString())
                    StatCard(modifier = Modifier.weight(1f), label = "Today",     value = todayCount.toString())
                    StatCard(modifier = Modifier.weight(1f), label = "Streak 🔥", value = if (encounterStreak > 0) "${encounterStreak}d" else "—")
                    StatCard(modifier = Modifier.weight(1f), label = "⚡ Energy",  value = "${joulesTotal}J")
                }
                OutlinedButton(
                    onClick  = onNavigateToStickerBook,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("🃴 Sticker Book · ${ownedStickers.size} collected") }

                Spacer(Modifier.height(24.dp))

                Text(
                    text  = "Battery Mode",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        shape    = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                        selected = scanMode == ScanMode.OFF,
                        onClick  = { vm.setScanMode(ScanMode.OFF) },
                    ) { Text("Off 🌙") }
                    SegmentedButton(
                        shape    = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                        selected = scanMode == ScanMode.BALANCED,
                        onClick  = { vm.setScanMode(ScanMode.BALANCED) },
                    ) { Text("Balanced ⚡") }
                    SegmentedButton(
                        shape    = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
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
