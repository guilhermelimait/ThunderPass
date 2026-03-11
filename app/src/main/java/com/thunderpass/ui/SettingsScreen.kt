package com.thunderpass.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.Manifest
import android.app.AppOpsManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.thunderpass.BuildConfig
import com.thunderpass.ble.ScanMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    darkMode: Boolean,
    onDarkModeToggle: (Boolean) -> Unit,
    onMusicChange: (Boolean) -> Unit = {},
    onBack: () -> Unit = {},
    onNavigateToDeviceSync: () -> Unit = {},
    vm: HomeViewModel = viewModel(),
    highlightSection: String = "",
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val prefs = remember { context.getSharedPreferences("tp_settings", android.content.Context.MODE_PRIVATE) }
    var musicEnabled by remember { mutableStateOf(prefs.getBoolean("music_enabled", true)) }
    val scanMode        by vm.scanMode.collectAsState()
    val privacyMode     by vm.privacyMode.collectAsState()
    val bleEnabled      by vm.bleEnabled.collectAsState()
    val autoWalkEnabled by vm.autoWalkEnabled.collectAsState()
    val availableUpdate by vm.availableUpdate.collectAsState()
    var advancedExpanded by remember { mutableStateOf(false) }
    var vibrationEnabled by remember { mutableStateOf(prefs.getBoolean("vibration_enabled", true)) }

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
    var usageAccessGranted by remember {
        mutableStateOf(hasUsageStatsPermission(context))
    }
    var batteryOptGranted by remember {
        mutableStateOf(
            (context.getSystemService(android.content.Context.POWER_SERVICE) as? PowerManager)
                ?.isIgnoringBatteryOptimizations(context.packageName) == true
        )
    }
    var activityRecognitionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION)
                == PackageManager.PERMISSION_GRANTED
        )
    }
    // Re-check permissions when returning from system settings
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                usageAccessGranted = hasUsageStatsPermission(context)
                batteryOptGranted = (context.getSystemService(android.content.Context.POWER_SERVICE) as? PowerManager)
                    ?.isIgnoringBatteryOptimizations(context.packageName) == true
                notifGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                bleGranted = blePermissions.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
                activityRecognitionGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // ── Permission launchers ───────────────────────────────────────────────────
    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> notifGranted = granted }

    val activityRecLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> activityRecognitionGranted = granted }

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
    val scrollState = rememberScrollState()
    // Auto-scroll to the permissions section when highlighted
    val permissionsSectionY = remember { mutableStateOf(0) }
    LaunchedEffect(highlightSection) {
        if (highlightSection == "permissions" && permissionsSectionY.value > 0) {
            scrollState.animateScrollTo(permissionsSectionY.value)
        }
    }
    // Retry scroll once the layout position is known
    LaunchedEffect(permissionsSectionY.value) {
        if (highlightSection == "permissions" && permissionsSectionY.value > 0) {
            scrollState.animateScrollTo(permissionsSectionY.value)
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
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
                label           = "BLE Service",
                subtitle        = "Allow ThunderPass to scan and exchange Sparks over Bluetooth. When off, the service will not run or restart on boot.",
                checked         = bleEnabled,
                onCheckedChange = { vm.setBleEnabled(it) },
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            SettingToggleRow(
                label           = "Privacy Mode",
                subtitle        = "Appear as \"Private User\" to nearby devices — no name, avatar, or greeting is shared",
                checked         = privacyMode,
                onCheckedChange = { vm.setPrivacyMode(it) },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            SettingToggleRow(
                label           = "Auto-Walk Mode",
                subtitle        = "BLE pauses automatically when you\u2019re still for 10 minutes and resumes when you start walking \u2014 saves battery at home",
                checked         = autoWalkEnabled,
                onCheckedChange = { vm.setAutoWalk(it) },
            )        }

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
        Column(
            modifier = Modifier
                .onGloballyPositioned { coords ->
                    permissionsSectionY.value = coords.positionInParent().y.toInt()
                },
        ) {
            val allPermsGranted = notifGranted && bleGranted && activityRecognitionGranted && usageAccessGranted && batteryOptGranted
            SettingsSection(
                title = "Permissions",
                highlightTitle = highlightSection == "permissions" && !allPermsGranted,
            ) {
            // Notifications
            PermissionRow(
                label    = "Notifications",
                subtitle = "Alert you when a new SparkyUser is nearby",
                granted  = notifGranted,
                onRequest = {
                    if (notifGranted) {
                        context.startActivity(
                            Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                        )
                    } else if (activity != null && !activity.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                        // Permanently denied or never asked — open app settings
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
                    } else if (activity != null && blePermissions.any { perm ->
                        !activity.shouldShowRequestPermissionRationale(perm)
                    }) {
                        // At least one BLE permission permanently denied — open app settings
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

            // Activity Recognition — step counter for Walking Volts
            PermissionRow(
                label    = "Activity Recognition",
                subtitle = "Step counter for Walking Volts (100 steps = 1 Volt)",
                granted  = activityRecognitionGranted,
                onRequest = {
                    if (activityRecognitionGranted) {
                        context.startActivity(
                            Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                        )
                    } else if (activity != null && !activity.shouldShowRequestPermissionRationale(Manifest.permission.ACTIVITY_RECOGNITION)) {
                        context.startActivity(
                            Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                        )
                    } else {
                        activityRecLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                    }
                },
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Battery Optimization — keep BLE scanning alive in background
            @SuppressLint("BatteryLife")
            PermissionRow(
                label    = "Run in Background",
                subtitle = "Keep BLE scanning alive while the app is in the background (Doze whitelist)",
                granted  = batteryOptGranted,
                onRequest = {
                    if (batteryOptGranted) {
                        context.startActivity(
                            Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                        )
                    } else {
                        runCatching {
                            context.startActivity(
                                Intent(AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                            )
                        }.onFailure {
                            // Fallback: open app battery settings page
                            context.startActivity(
                                Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                            )
                        }
                    }
                },
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Usage Access — required for the daily play-time tracker
            PermissionRow(
                label    = "Usage Access",
                subtitle = "Required for the daily gaming play-time tracker (Daily Play Time)",
                granted  = usageAccessGranted,
                onRequest = {
                    try {
                        // Try to open usage access settings directly for this app (Android 10+)
                        context.startActivity(
                            Intent(AndroidSettings.ACTION_USAGE_ACCESS_SETTINGS)
                                .setData(Uri.parse("package:${context.packageName}"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    } catch (_: Exception) {
                        // Fallback: open the general usage access list
                        context.startActivity(
                            Intent(AndroidSettings.ACTION_USAGE_ACCESS_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                },
            )
        }
        } // close permissions Column

        // ── Device Sync ───────────────────────────────────────────────────────
        SettingsSection("Device Sync") {
            SettingActionRow(
                label       = "Sync Profile Between Devices",
                subtitle    = "Transfer your profile to another device or keep two devices in sync using a secure pairing code",
                buttonLabel = "Open",
                onClick     = onNavigateToDeviceSync,
            )
        }

        // ── Advanced (collapsible) ────────────────────────────────────────────
        var showResetDialog by remember { mutableStateOf(false) }
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
                        SettingActionRow(
                            label       = "Reset All Data",
                            subtitle    = "⚠ Erase your profile, encounters, step history, and all local settings. This action cannot be undone — all local data will be permanently lost.",
                            buttonLabel = "Reset",
                            onClick     = { showResetDialog = true },
                        )
                    }
                }
            }
        }

        if (showResetDialog) {
            AlertDialog(
                onDismissRequest = { showResetDialog = false },
                title = { Text("Reset All Data?") },
                text  = {
                    Text(
                        "This will permanently delete your profile, all encounters, " +
                        "step history, badges, stickers, and every local setting.\n\n" +
                        "This cannot be undone."
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showResetDialog = false

                            // ── 1. Clear ALL SharedPreferences (plain + encrypted) ──
                            val prefsDir = java.io.File(context.applicationInfo.dataDir, "shared_prefs")
                            if (prefsDir.isDirectory) {
                                prefsDir.listFiles()?.forEach { it.delete() }
                            }

                            // ── 2. Delete Room database files ──
                            context.deleteDatabase("thunderpass.db")
                            context.deleteDatabase("thunderpass.db-wal")
                            context.deleteDatabase("thunderpass.db-shm")

                            // ── 3. Wipe caches (Coil disk cache, etc.) ──
                            context.cacheDir?.deleteRecursively()

                            // ── 4. Restart app ──
                            val pm = context.packageManager
                            val intent = pm.getLaunchIntentForPackage(context.packageName)
                            if (intent != null) {
                                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(intent)
                                Runtime.getRuntime().exit(0)
                            }
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text("Reset Everything") }
                },
                dismissButton = {
                    TextButton(onClick = { showResetDialog = false }) {
                        Text("Cancel")
                    }
                },
            )
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
            Text(
                text  = "Version ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
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
private fun SettingsSection(
    title: String,
    highlightTitle: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text       = title.uppercase(),
            style      = MaterialTheme.typography.labelSmall,
            color      = if (highlightTitle) Color.White else MaterialTheme.colorScheme.primary,
            fontFamily = FontFamily.Monospace,
            modifier   = if (highlightTitle) Modifier
                .background(Color(0xFFB71C1C), RoundedCornerShape(4.dp))
                .padding(horizontal = 8.dp, vertical = 2.dp)
            else Modifier,
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

private fun hasUsageStatsPermission(context: android.content.Context): Boolean {
    val appOps = context.getSystemService(android.content.Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        )
    } else {
        @Suppress("DEPRECATION")
        appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        )
    }
    return mode == AppOpsManager.MODE_ALLOWED
}
