package com.thunderpass.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EncounterListScreen(
    onNavigateToDetail: (Long) -> Unit = {},
    onBack: () -> Unit = {},
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
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                )
            )
        },
    ) { padding ->
        if (encounters.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                EmptyEncountersState()
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
                    EncounterCard(
                        item = item,
                        onClick = { onNavigateToDetail(item.encounter.id) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyEncountersState() {
    val infiniteTransition = rememberInfiniteTransition(label = "bolt-pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.82f,
        targetValue  = 1.18f,
        animationSpec = infiniteRepeatable(
            animation  = tween(950, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "scale",
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue  = 1f,
        animationSpec = infiniteRepeatable(
            animation  = tween(950, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "alpha",
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(32.dp),
    ) {
        Text(
            text     = "\u26A1",
            fontSize = 72.sp,
            modifier = Modifier.graphicsLayer {
                scaleX     = scale
                scaleY     = scale
                this.alpha = alpha
            },
        )
        Text(
            text       = "No Sparks yet",
            style      = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color      = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text      = "Head outside and let ThunderPass\ndetect other Travelers nearby.",
            style     = MaterialTheme.typography.bodyMedium,
            color     = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun EncounterCard(
    item: EncounterWithProfile,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val enc      = item.encounter
    val snapshot = item.snapshot
    val timeStr  = remember(enc.seenAt) { relativeTimeString(enc.seenAt) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Auto-generated peer avatar (seed = rotatingId seen at encounter time)
            DiceBearAvatar(
                seed = snapshot?.rotatingId ?: enc.rotatingId,
                size = 52.dp,
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
                    text = "$timeStr  \u00B7  ${enc.rssi} dBm",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
                if (snapshot == null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Profile exchange pending\u2026",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }
    }

}


