package com.thunderpass.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EncounterListScreen(
    onBack: () -> Unit,
    vm: HomeViewModel = viewModel(),
) {
    val encounters by vm.encounters.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Encounters") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (encounters.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "No encounters yet.\nGo outside! 🚶",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(encounters, key = { it.encounter.id }) { item ->
                    EncounterCard(item)
                }
            }
        }
    }
}

@Composable
private fun EncounterCard(item: EncounterWithProfile) {
    val enc      = item.encounter
    val snapshot = item.snapshot
    val dateStr  = remember(enc.seenAt) {
        SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(enc.seenAt))
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Auto-generated peer avatar (seed = rotatingId seen at encounter time)
            DiceBearAvatar(
                seed = snapshot?.rotatingId ?: enc.rotatingId,
                size = 48.dp,
            )

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = snapshot?.displayName ?: "Unknown traveler",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                if (!snapshot?.greeting.isNullOrBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "\"${snapshot!!.greeting}\"",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "$dateStr  ·  ${enc.rssi} dBm",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
                if (snapshot == null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Profile exchange pending…",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }
    }
}


