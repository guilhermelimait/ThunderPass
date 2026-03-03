package com.thunderpass.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

private val AVATAR_COLORS = listOf(
    "#FFD400", "#FF6B35", "#E63946", "#06D6A0",
    "#118AB2", "#7B2D8B", "#F4A261", "#2EC4B6",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    vm: ProfileViewModel = viewModel(),
) {
    val profile by vm.profile.collectAsState()

    // Local draft state — only flushed to DB on Save
    var draftName     by remember(profile.displayName) { mutableStateOf(profile.displayName) }
    var draftGreeting by remember(profile.greeting)     { mutableStateOf(profile.greeting) }
    var draftColor    by remember(profile.avatarColor)  { mutableStateOf(profile.avatarColor) }

    var saved by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Profile") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
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
        ) {
            Spacer(Modifier.height(8.dp))

            // — Avatar preview ────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(
                        color = parseColor(draftColor),
                        shape = CircleShape,
                    )
                    .align(Alignment.CenterHorizontally),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = draftName.trim().ifEmpty { "?" }.take(1).uppercase(),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.headlineMedium,
                )
            }

            // — Color picker ──────────────────────────────────────────────────
            Text(
                text = "Avatar color",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(AVATAR_COLORS) { hex ->
                    val selected = hex == draftColor
                    Box(
                        modifier = Modifier
                            .size(if (selected) 44.dp else 36.dp)
                            .background(parseColor(hex), CircleShape)
                            .clickable { draftColor = hex },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) {
                            Text("✓", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // — Display name ──────────────────────────────────────────────────
            OutlinedTextField(
                value = draftName,
                onValueChange = {
                    draftName = it
                    saved = false
                },
                label = { Text("Display name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                supportingText = { Text("Shown to nearby ThunderPass users") },
            )

            // — Greeting ──────────────────────────────────────────────────────
            OutlinedTextField(
                value = draftGreeting,
                onValueChange = {
                    draftGreeting = it
                    saved = false
                },
                label = { Text("Greeting message") },
                singleLine = false,
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth(),
                supportingText = { Text("Exchanged when you meet someone") },
            )

            // — Installation ID (read-only, for debugging) ────────────────────
            Text(
                text = "Installation ID (never shared directly)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
            Text(
                text = profile.installationId.ifEmpty { "—" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outlineVariant,
            )

            // — Save button ───────────────────────────────────────────────────
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    vm.save(draftName, draftGreeting, draftColor)
                    saved = true
                },
            ) {
                Text(if (saved) "✓ Saved" else "Save Profile")
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

private fun parseColor(hex: String): Color = try {
    Color(android.graphics.Color.parseColor(hex))
} catch (e: Exception) {
    Color(0xFFFFD400)
}
