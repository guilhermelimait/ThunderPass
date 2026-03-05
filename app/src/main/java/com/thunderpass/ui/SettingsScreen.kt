package com.thunderpass.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.thunderpass.ble.ScanMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    darkMode: Boolean,
    onDarkModeToggle: (Boolean) -> Unit,
    onMusicChange: (Boolean) -> Unit = {},
    onBack: () -> Unit = {},
    vm: HomeViewModel = viewModel(),
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("tp_settings", android.content.Context.MODE_PRIVATE) }
    var musicEnabled by remember { mutableStateOf(prefs.getBoolean("music_enabled", true)) }
    var screenOnActive by remember { mutableStateOf(prefs.getBoolean("screen_on_active", true)) }

    val safeZoneActive  by vm.safeZoneActive.collectAsState()
    val scanMode        by vm.scanMode.collectAsState()
    val privacyMode     by vm.privacyMode.collectAsState()
    val availableUpdate by vm.availableUpdate.collectAsState()
    var advancedExpanded by remember { mutableStateOf(false) }
    var vibrationEnabled by remember { mutableStateOf(prefs.getBoolean("vibration_enabled", true)) }
    // ── Hardware (AYN Thor LED flash) ─────────────────────────────────────────
    var ledFlashEnabled  by remember { mutableStateOf(prefs.getBoolean("led_flash_enabled", true)) }
    // WRITE_SECURE_SETTINGS is granted via ADB (not via the system settings UI):
    //   adb shell pm grant com.thunderpass android.permission.WRITE_SECURE_SETTINGS
    var canWriteSettings by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.WRITE_SECURE_SETTINGS
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    // ── Permission state — re-checked on every recomposition ──────────────────
    var notifGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED
        )
    }
    val blePermissions = arrayOf(
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_ADVERTISE,
        Manifest.permission.BLUETOOTH_CONNECT,
    )
    var bleGranted by remember {
        mutableStateOf(
            blePermissions.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
        )
    }

    // ── Permission launchers ───────────────────────────────────────────────────
    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> notifGranted = granted }

    val bleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results -> bleGranted = results.values.all { it } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(innerPadding)
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Spacer(Modifier.height(8.dp))

        // ── OTA update banner ────────────────────────────────────────────
        if (availableUpdate != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors   = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                ),
            ) {
                Row(
                    modifier              = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text       = "⚡ Update available: $availableUpdate",
                            style      = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color      = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                        Text(
                            text  = "Download the latest release from GitHub.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f),
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            context.startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://github.com/guilhermelimait/ThunderPass/releases/tag/$availableUpdate"),
                                )
                            )
                        },
                    ) { Text("Download") }
                }
            }
        }

        // ── General ─────────────────────────────────────────────────────
        SettingsSection("General") {
            SettingToggleRow(
                label           = "Background Music",
                subtitle        = "Play thunderpass-bg.mp3 when the app opens",
                checked         = musicEnabled,
                onCheckedChange = { enabled ->
                    musicEnabled = enabled
                    prefs.edit().putBoolean("music_enabled", enabled).apply()
                    onMusicChange(enabled)
                },
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            SettingToggleRow(
                label           = "Keep Screen On",
                subtitle        = "Prevent the screen from turning off while ThunderPass is active",
                checked         = screenOnActive,
                onCheckedChange = { enabled ->
                    screenOnActive = enabled
                    prefs.edit().putBoolean("screen_on_active", enabled).apply()
                },
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            SettingToggleRow(
                label           = "Privacy Mode",
                subtitle        = "Appear as \"Private User\" to nearby devices — no name, avatar, or greeting is shared",
                checked         = privacyMode,
                onCheckedChange = { vm.setPrivacyMode(it) },
            )
        }

        // ── Appearance ───────────────────────────────────────────────────
        SettingsSection("Appearance") {
            SettingToggleRow(
                label           = "Dark Mode",
                subtitle        = "Switch to dark theme",
                checked         = darkMode,
                onCheckedChange = onDarkModeToggle,
            )
        }

        // ── Permissions ───────────────────────────────────────────────────────
        SettingsSection("Permissions") {
            // Notifications
            PermissionRow(
                label    = "Notifications",
                subtitle = "Alert you when a new SparkyUser is nearby",
                granted  = notifGranted,
                onRequest = {
                    if (notifGranted) {
                        // Already granted — open app settings so user can revoke if desired
                        context.startActivity(
                            Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                        )
                    } else {
                        notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Bluetooth
            PermissionRow(
                label    = "Bluetooth",
                subtitle = "Scan & advertise BLE presence (SCAN, ADVERTISE, CONNECT)",
                granted  = bleGranted,
                onRequest = {
                    if (bleGranted) {
                        context.startActivity(
                            Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                        )
                    } else {
                        bleLauncher.launch(blePermissions)
                    }
                },
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Flash LEDs on Encounter (AYN Thor hardware)
            SettingToggleRow(
                label           = "Flash LEDs on Encounter",
                subtitle        = "Blink the joystick LEDs yellow 3× when a SparkyUser is found nearby (AYN Thor)",
                checked         = ledFlashEnabled,
                onCheckedChange = { enabled ->
                    ledFlashEnabled = enabled
                    prefs.edit().putBoolean("led_flash_enabled", enabled).apply()
                    if (enabled) canWriteSettings = ContextCompat.checkSelfPermission(
                        context, Manifest.permission.WRITE_SECURE_SETTINGS
                    ) == PackageManager.PERMISSION_GRANTED
                },
            )
            if (ledFlashEnabled && !canWriteSettings) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingActionRow(
                    label       = "Permission required",
                    subtitle    = "Run once via ADB: adb shell pm grant com.thunderpass android.permission.WRITE_SECURE_SETTINGS",
                    buttonLabel = "App Info",
                    onClick     = {
                        runCatching {
                            context.startActivity(
                                Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                            )
                        }
                    },
                )
            }
        }

        // ── App Management ────────────────────────────────────────────────────
        SettingsSection("App Management") {
            // Usage Access — required for the daily play-time tracker
            SettingActionRow(
                label       = "Usage Access",
                subtitle    = "Required for the daily gaming play-time tracker (🎮 Daily Play Time)",
                buttonLabel = "Open",
                onClick     = {
                    context.startActivity(
                        Intent(AndroidSettings.ACTION_USAGE_ACCESS_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                },
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // App Info — full system settings page for ThunderPass
            SettingActionRow(
                label       = "App Info",
                subtitle    = "View storage, permissions, and other system-level app settings",
                buttonLabel = "Open",
                onClick     = {
                    context.startActivity(
                        Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                    )
                },
            )
        }

        // ── Advanced (collapsible) ────────────────────────────────────────────
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier              = Modifier
                    .fillMaxWidth()
                    .clickable { advancedExpanded = !advancedExpanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Text(
                    text       = "ADVANCED",
                    style      = MaterialTheme.typography.labelSmall,
                    color      = MaterialTheme.colorScheme.primary,
                    fontFamily = FontFamily.Monospace,
                )
                Icon(
                    imageVector        = if (advancedExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint               = MaterialTheme.colorScheme.primary,
                )
            }
            AnimatedVisibility(visible = advancedExpanded) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors   = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    ),
                ) {
                    Column(
                        modifier            = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // Safe Zone — completely pauses all BLE activity
                        SettingToggleRow(
                            label           = "Safe Zone",
                            subtitle        = "Fully pause all BLE — no scanning or advertising (e.g. at home or work). This overrides the Scanning Mode setting above.",
                            checked         = safeZoneActive,
                            onCheckedChange = { vm.setSafeZone(it) },
                        )
                    }
                }
            }
        }

        // ── Updates ───────────────────────────────────────────────────────────
        SettingsSection("Updates") {
            SettingActionRow(
                label       = "Check for Updates",
                subtitle    = if (availableUpdate != null)
                                  "⚡ $availableUpdate is available! ThunderPass checks automatically when connected to the internet."
                              else
                                  "ThunderPass checks for updates automatically when connected to the internet. New versions appear here and in the Android notification area.",
                buttonLabel = "Releases",
                onClick     = {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://github.com/guilhermelimait/ThunderPass/releases"))
                    )
                },
            )
        }

        Spacer(Modifier.height(24.dp))
    }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Section wrapper
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text       = title.uppercase(),
            style      = MaterialTheme.typography.labelSmall,
            color      = MaterialTheme.colorScheme.primary,
            fontFamily = FontFamily.Monospace,
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors   = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            ),
        ) {
            Column(
                modifier            = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content             = content,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Toggle row (dark mode, etc.)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SettingToggleRow(
    label: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Permission row — shows grant state + request / manage button
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PermissionRow(
    label: String,
    subtitle: String,
    granted: Boolean,
    onRequest: () -> Unit,
) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text  = if (granted) "✓ Granted" else "Not granted",
                style = MaterialTheme.typography.labelSmall,
                color = if (granted) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error,
            )
        }
        OutlinedButton(onClick = onRequest) {
            Text(if (granted) "Manage" else "Grant")
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Action row — opens a system settings screen (no runtime permission needed)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SettingActionRow(
    label: String,
    subtitle: String,
    buttonLabel: String,
    onClick: () -> Unit,
) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedButton(onClick = onClick) {
            Text(buttonLabel)
        }
    }
}
