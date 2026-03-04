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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import android.content.res.Configuration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.tooling.preview.Preview
import com.thunderpass.R

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
    val voltsTotal      by vm.voltsTotal.collectAsState()
    val installationId by vm.installationId.collectAsState()
    val avatarSeed     by vm.avatarSeed.collectAsState()
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
        avatarSeed = avatarSeed.ifEmpty { installationId },
        encounters = encounters,
        voltsTotal = voltsTotal,
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
    avatarSeed: String,
    encounters: List<EncounterWithProfile>,
    voltsTotal: Long,
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
            // Landscape: left=info, right=animation 50% width x 50% HEIGHT, 12dp gap
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                // Match portrait card aspect ratio: portrait_card = full_portrait_w × (full_portrait_h / 3)
                // In landscape: maxHeight = portrait_w, maxWidth = portrait_h
                // portrait aspect (w:h) = maxHeight / (maxWidth / 3) = 3*maxHeight/maxWidth
                // landscape card_width = maxWidth/2 (right panel via weight)
                // card_height = card_width / aspect = (maxWidth/2) / (3*maxHeight/maxWidth) = maxWidth²/(6·maxHeight)
                val animLandscapeH = maxWidth * (maxWidth / maxHeight) / 6f
                Row(modifier = Modifier.fillMaxSize()) {
                    // Left panel
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier              = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier              = Modifier.weight(1f),
                                verticalAlignment     = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                androidx.compose.foundation.Image(
                                    painter            = painterResource(R.drawable.logo),
                                    contentDescription = "ThunderPass",
                                    modifier           = Modifier.height(44.dp),
                                )
                                Column {
                                    Text(
                                        text       = displayName,
                                        style      = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color      = MaterialTheme.colorScheme.onBackground,
                                    )
                                    Text(
                                        text  = if (serviceRunning) "Scanning nearby\u2026" else "Tap to start scanning",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            DiceBearAvatar(
                                seed     = avatarSeed.ifEmpty { "default" },
                                size     = 40.dp,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable { onNavigate("profile") },
                            )
                        }
                        NavShortcuts(onNavigate = onNavigate)

                        RetroGallerySection(modifier = Modifier.fillMaxWidth())
                    }

                    // Right panel: toggle → animation → LastPassedBy
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // Toggle above the animation (not overlaid on it)
                        ThunderPassToggleCard(
                            active      = serviceRunning,
                            onToggle    = onToggleService,
                            transparent = false,
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(animLandscapeH),
                        ) {
                            WalkingSceneCard(
                                avatarSeed     = avatarSeed.ifEmpty { "default" },
                                serviceRunning = serviceRunning,
                                fillHeight     = true,
                            )
                        }
                        val lastEnc = encounters.firstOrNull()
                        if (lastEnc != null) {
                            LastPassedByCard(
                                encounter = lastEnc,
                                onClick   = { onNavigateToDetail(lastEnc.encounter.id) },
                            )
                        }
                    }
                }
            }
        } else {
            // ── Portrait layout ───────────────────────────────────────────────
            // Order: greeting → toggle → animation (1/3 height) → last-passed-by → nav buttons
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .windowInsetsPadding(WindowInsets.statusBars),
            ) {
                val animHeight = maxHeight / 3

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp),
                ) {
                    Spacer(Modifier.height(16.dp))

                    // ── 1. Greeting + avatar row ───────────────────────────────
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            modifier              = Modifier.weight(1f),
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            androidx.compose.foundation.Image(
                                painter            = painterResource(R.drawable.logo),
                                contentDescription = "ThunderPass",
                                modifier           = Modifier.height(56.dp),
                            )
                            Text(
                                text       = displayName,
                                style      = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color      = MaterialTheme.colorScheme.onBackground,
                            )
                        }
                        DiceBearAvatar(
                            seed     = avatarSeed.ifEmpty { "default" },
                            size     = 52.dp,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { onNavigate("profile") },
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    // ── 2. ThunderPass radio toggle (solid card, above animation) ─
                    ThunderPassToggleCard(
                        active      = serviceRunning,
                        onToggle    = onToggleService,
                        transparent = false,
                    )

                    Spacer(Modifier.height(10.dp))

                    // ── 3. Animation — exactly 1/3 of available screen height ──
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(animHeight)
                            .padding(horizontal = 0.dp, vertical = 4.dp),
                    ) {
                        WalkingSceneCard(
                            avatarSeed     = avatarSeed.ifEmpty { "default" },
                            serviceRunning = serviceRunning,
                            fillHeight     = true,
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    // ── 4. Last Passed By ──────────────────────────────────────
                    val lastEnc = encounters.firstOrNull()
                    if (lastEnc != null) {
                        LastPassedByCard(
                            encounter = lastEnc,
                            onClick   = { onNavigateToDetail(lastEnc.encounter.id) },
                        )
                        Spacer(Modifier.height(10.dp))
                    }

                    // ── 5. RetroAchievements galleries (above nav buttons) ─────
                    RetroGallerySection(modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))

                    // ── 6. Navigation buttons grid ─────────────────────────────
                    NavShortcuts(onNavigate = onNavigate)
                    Spacer(Modifier.height(16.dp))
                }
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
    val containerColor = if (transparent) Color.Transparent else when {
        active -> MaterialTheme.colorScheme.primaryContainer
        else   -> MaterialTheme.colorScheme.surfaceVariant
    }
    Card(
        modifier  = modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (transparent) 0.dp else 2.dp),
    ) {
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                androidx.compose.foundation.Image(
                    painter            = painterResource(R.drawable.logo),
                    contentDescription = "ThunderPass logo",
                    modifier           = Modifier.size(28.dp),
                )
                Text(
                    text       = "ThunderPass",
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color      = MaterialTheme.colorScheme.onSurface,
                )
            }
            Switch(
                checked         = active,
                onCheckedChange = { onToggle() },
                colors          = SwitchDefaults.colors(
                    checkedTrackColor   = Color.Black,
                    uncheckedTrackColor = Color(0xFF222222),
                    checkedThumbColor   = Color.White,
                    uncheckedThumbColor = Color(0xFFAAAAAA),
                ),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Volts info card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun VoltsInfoCard() {
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
                text       = "HOW TO EARN VOLTS",
                style      = MaterialTheme.typography.labelSmall,
                color      = MaterialTheme.colorScheme.primary,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            )
            Text(
                text  = "⚡ Meet a new Traveler via BLE — 100 V\n" +
                         "⚡ Unlock a Badge — 50–200 V\n" +
                         "⚡ RetroAchievements activity — up to 500 V\n" +
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
                seed     = encounter.snapshot?.avatarSeed?.takeIf { it.isNotBlank() } ?: seed,
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

// ─────────────────────────────────────────────────────────────────────────────
// Navigation shortcut buttons — always-visible 2×3 grid, badge-style square tiles
// ─────────────────────────────────────────────────────────────────────────────

private data class NavEntry(
    val label:       String,
    val icon:        ImageVector,
    val route:       String,
    val accentColor: Color,
    val gradientEnd: Color,
)

private val NAV_ENTRIES = listOf(
    NavEntry("Passes",   Icons.Filled.ElectricBolt,     "encounters", Color(0xFFFFB300), Color(0xFFFF6F00)),
    NavEntry("Profile",  Icons.Filled.Person,           "profile",    Color(0xFF2196F3), Color(0xFF0D47A1)),
    NavEntry("Badges",   Icons.Filled.WorkspacePremium, "badges",     Color(0xFF7B1FA2), Color(0xFFAD1457)),
    NavEntry("Shop",     Icons.Filled.ShoppingCart,     "shop",       Color(0xFFE64A19), Color(0xFFF57F17)),
    NavEntry("Settings", Icons.Filled.Settings,         "settings",   Color(0xFF37474F), Color(0xFF546E7A)),
    NavEntry("About",    Icons.Filled.LocalCafe,        "about",      Color(0xFF00796B), Color(0xFF00ACC1)),
)

@Composable
internal fun NavShortcuts(onNavigate: (String) -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier            = Modifier.fillMaxWidth(),
    ) {
        NAV_ENTRIES.chunked(3).forEach { rowEntries ->
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowEntries.forEach { entry ->
                    NavSquareButton(
                        entry    = entry,
                        onClick  = { onNavigate(entry.route) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun NavSquareButton(
    entry:    NavEntry,
    onClick:  () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .drawBehind {
                // Badge-style gradient background
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(entry.accentColor, entry.gradientEnd),
                        start  = Offset(0f, 0f),
                        end    = Offset(size.width, size.height),
                    ),
                )
                // Decorative radial glow circles (badge card style)
                val cx = size.width * 0.88f
                val cy = size.height * 0.12f
                for (r in listOf(28f, 50f, 72f)) {
                    drawCircle(
                        color  = Color.White.copy(alpha = 0.09f),
                        radius = r,
                        center = Offset(cx, cy),
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector        = entry.icon,
                contentDescription = entry.label,
                tint               = Color.White,
                modifier           = Modifier.size(22.dp),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text       = entry.label,
                style      = MaterialTheme.typography.labelSmall,
                color      = Color.White,
                fontWeight = FontWeight.SemiBold,
            )
        }
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
            avatarSeed = "test-id",
            encounters = emptyList(),
            voltsTotal = 2500,
            onToggleService = {},
            onNavigateToDetail = {},
            onGrantPermissions = {}
        )
    }
}
