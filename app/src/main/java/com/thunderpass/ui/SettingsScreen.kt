package com.thunderpass.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.thunderpass.ble.ScanMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    darkMode: Boolean,
    onDarkModeToggle: (Boolean) -> Unit,
    vm: HomeViewModel = viewModel(),
) {
    val scanMode       by vm.scanMode.collectAsState()
    val safeZoneActive by vm.safeZoneActive.collectAsState()

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

        // Appearance
        SettingsSection("Appearance") {
            SettingToggleRow(
                label    = "Dark Mode",
                subtitle = "Switch to dark theme",
                checked  = darkMode,
                onCheckedChange = onDarkModeToggle,
            )
        }

        // Scanning
        SettingsSection("Scanning") {
            SettingToggleRow(
                label    = "Safe Zone",
                subtitle = "Pause BLE while at a trusted location",
                checked  = safeZoneActive,
                onCheckedChange = { vm.setSafeZone(it) },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text       = "Battery Mode",
                    style      = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text  = "Controls BLE scan aggressiveness and battery usage",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        shape    = SegmentedButtonDefaults.itemShape(0, 3),
                        selected = scanMode == ScanMode.OFF,
                        onClick  = { vm.setScanMode(ScanMode.OFF) },
                    ) { Text("Off") }
                    SegmentedButton(
                        shape    = SegmentedButtonDefaults.itemShape(1, 3),
                        selected = scanMode == ScanMode.BALANCED,
                        onClick  = { vm.setScanMode(ScanMode.BALANCED) },
                    ) { Text("Balanced") }
                    SegmentedButton(
                        shape    = SegmentedButtonDefaults.itemShape(2, 3),
                        selected = scanMode == ScanMode.AGGRESSIVE,
                        onClick  = { vm.setScanMode(ScanMode.AGGRESSIVE) },
                    ) { Text("Aggressive") }
                }
            }
        }

        // About
        SettingsSection("About") {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("ThunderPass", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text("v0.7.0", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                text  = "Offline-first StreetPass for Android.\nData stays on your device. BLE only.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}

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
