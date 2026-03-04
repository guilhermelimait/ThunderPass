package com.thunderpass.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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

@Composable
fun SettingsScreen(
    darkMode: Boolean,
    onDarkModeToggle: (Boolean) -> Unit,
    vm: HomeViewModel = viewModel(),
) {
    val context = LocalContext.current

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Spacer(Modifier.height(16.dp))

        Text(
            text       = "Settings",
            style      = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color      = MaterialTheme.colorScheme.onBackground,
        )

        // ── Appearance ────────────────────────────────────────────────────────
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
                subtitle = "Alert you when a new Traveler is nearby",
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

            // Always-on scanning (battery optimisation)
            SettingActionRow(
                label    = "Always-on Scanning",
                subtitle = "Disable battery optimisation so ThunderPass keeps the BLE service alive when the app is closed",
                buttonLabel = "Open Settings",
                onClick  = {
                    runCatching {
                        context.startActivity(
                            Intent(AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                        )
                    }.onFailure {
                        // Fallback: open general battery settings
                        context.startActivity(Intent(AndroidSettings.ACTION_BATTERY_SAVER_SETTINGS))
                    }
                },
            )
        }

        // ── About ─────────────────────────────────────────────────────────────
        SettingsSection("About") {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "ThunderPass",
                    style      = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "v0.7.2",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text  = "Offline-first StreetPass for Android.\nData stays on your device. BLE only.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            SettingActionRow(
                label       = "Check for Updates",
                subtitle    = "View the latest releases on GitHub",
                buttonLabel = "Open",
                onClick     = {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://github.com/guilhermelimait/ThunderPass/releases"))
                    )
                },
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            SettingActionRow(
                label       = "Support the Project",
                subtitle    = "Buy me a coffee on Ko-fi ☕",
                buttonLabel = "Ko-fi",
                onClick     = {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://ko-fi.com/guilhermelimait/posts"))
                    )
                },
            )
        }

        Spacer(Modifier.height(24.dp))
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
