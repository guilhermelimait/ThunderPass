package com.thunderpass.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    firstRun: Boolean = false,
    onComplete: (() -> Unit)? = null,
    vm: ProfileViewModel = viewModel(),
) {
    val profile by vm.profile.collectAsState()

    // Local draft state — only flushed to DB on Save
    var draftName     by remember(profile.displayName) { mutableStateOf(profile.displayName) }
    var draftGreeting by remember(profile.greeting)     { mutableStateOf(profile.greeting) }
    var saved by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (firstRun) "Set Up Profile" else "My Profile") },
                navigationIcon = {
                    if (!firstRun) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(16.dp))

            // — Welcome banner (first run only) ─────────────────────────────
            if (firstRun) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text      = "Welcome to ThunderPass! 👋\nSet up your profile to get started.",
                        style     = MaterialTheme.typography.bodyMedium,
                        color     = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier  = Modifier.padding(16.dp),
                    )
                }
            }

            // — Auto-generated DiceBear avatar ─────────────────────────────
            DiceBearAvatar(
                seed = profile.installationId.ifEmpty { "default" },
                size = 96.dp,
            )

            Text(
                text  = "Your avatar is auto-generated and unique to you.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // — Display name ──────────────────────────────────────────────────
            OutlinedTextField(
                value = draftName,
                onValueChange = {
                    draftName = it
                    saved = false
                },
                label         = { Text("Display name") },
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth(),
                supportingText = { Text("Shown to nearby ThunderPass users") },
            )

            // — Greeting ──────────────────────────────────────────────────────
            OutlinedTextField(
                value = draftGreeting,
                onValueChange = {
                    draftGreeting = it
                    saved = false
                },
                label    = { Text("Greeting message") },
                singleLine = false,
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth(),
                supportingText = { Text("Exchanged when you meet someone") },
            )

            // — Save button ───────────────────────────────────────────────────
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick  = {
                    vm.save(draftName, draftGreeting)
                    saved = true
                    if (firstRun) onComplete?.invoke()
                },
            ) {
                Text(if (saved && !firstRun) "✓ Saved" else if (firstRun) "Start exploring!" else "Save Profile")
            }

            // — Installation ID (read-only, for debugging) ────────────────────
            Spacer(Modifier.height(4.dp))
            Text(
                text  = "Installation ID (never shared directly)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
            Text(
                text  = profile.installationId.ifEmpty { "—" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outlineVariant,
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}
