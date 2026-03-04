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
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import androidx.compose.ui.tooling.preview.Preview
import com.thunderpass.data.db.entity.Encounter

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

    HomeScreenContent(
        allGranted = allGranted,
        displayName = displayName,
        serviceRunning = serviceRunning,
        installationId = installationId,
        encounters = encounters,
        joulesTotal = joulesTotal,
        onToggleService = { if (serviceRunning) vm.stopService() else vm.startService() },
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
                            modifier = Modifier.clip(CircleShape),
                        )
                    }
                    // ThunderPass ON/OFF toggle
                    ThunderPassToggleCard(
                        active   = serviceRunning,
                        onToggle = onToggleService,
                    )
                    HomeNavGrid(onNavigate = onNavigate)
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
                    if (encounters.isNotEmpty()) {
                        Text(
                            text  = "Recent bypassers",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        encounters.take(5).forEach { enc ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onNavigateToDetail(enc.encounter.id) },
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                ),
                            ) {
                                Row(
                                    modifier          = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    DiceBearAvatar(
                                        seed = enc.snapshot?.rotatingId ?: enc.encounter.rotatingId,
                                        size = 36.dp,
                                        modifier = Modifier.clip(CircleShape),
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text       = enc.snapshot?.displayName ?: "Unknown traveler",
                                            style      = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines   = 1,
                                            overflow   = TextOverflow.Ellipsis,
                                        )
                                    }
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
                        modifier = Modifier.clip(CircleShape),
                    )
                }

                Spacer(Modifier.height(20.dp))

                ThunderPassToggleCard(
                    active   = serviceRunning,
                    onToggle = onToggleService,
                )

                Spacer(Modifier.height(12.dp))
                HomeNavGrid(onNavigate = onNavigate)
                Spacer(Modifier.height(16.dp))

                // ── Walking scene (replaces dormant area) ─────────────────────
                WalkingSceneCard(
                    avatarSeed     = installationId.ifEmpty { "default" },
                    serviceRunning = serviceRunning,
                )

                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Navigation grid — 5 destination tiles
// ─────────────────────────────────────────────────────────────────────────────

private data class NavTile(
    val label: String,
    val route: String,
    val icon: ImageVector,
    val gradientStart: Color,
    val gradientEnd: Color,
)

private val NAV_TILES = listOf(
    NavTile("Passes",   "encounters", Icons.AutoMirrored.Filled.List,  Color(0xFF1565C0), Color(0xFF0D47A1)),
    NavTile("Profile",  "profile",    Icons.Filled.Person,             Color(0xFF6A1B9A), Color(0xFF4A148C)),
    NavTile("Badges",   "badges",     Icons.Filled.Star,               Color(0xFFF57F17), Color(0xFFE65100)),
    NavTile("Shop",     "shop",       Icons.Filled.ShoppingCart,       Color(0xFF00695C), Color(0xFF004D40)),
    NavTile("Settings", "settings",   Icons.Filled.Settings,           Color(0xFF37474F), Color(0xFF263238)),
)

@Composable
private fun HomeNavGrid(onNavigate: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // First row: 3 tiles
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            NAV_TILES.take(3).forEach { tile ->
                NavTileCard(tile = tile, modifier = Modifier.weight(1f), onClick = { onNavigate(tile.route) })
            }
        }
        // Second row: 2 tiles (centred via weight)
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Spacer(Modifier.weight(0.5f))
            NAV_TILES.drop(3).forEach { tile ->
                NavTileCard(tile = tile, modifier = Modifier.weight(1f), onClick = { onNavigate(tile.route) })
            }
            Spacer(Modifier.weight(0.5f))
        }
    }
}

@Composable
private fun NavTileCard(tile: NavTile, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(72.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.horizontalGradient(listOf(tile.gradientStart, tile.gradientEnd)))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector        = tile.icon,
                contentDescription = tile.label,
                tint               = Color.White,
                modifier           = Modifier.size(24.dp),
            )
            Text(
                text       = tile.label,
                style      = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color      = Color.White,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ThunderPass ON/OFF toggle card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ThunderPassToggleCard(active: Boolean, onToggle: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(
            containerColor = if (active)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant,
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
                text  = "★ Meet a new Traveler via BLE — 100 J\n" +
                         "★ Unlock a Badge — 50–200 J\n" +
                         "★ RetroAchievements activity — up to 500 J\n" +
                         "★ Streak bonuses for daily Sparks",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
