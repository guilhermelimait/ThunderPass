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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.pow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EncounterDetailScreen(
    encounterId: Long,
    onBack: () -> Unit,
    vm: HomeViewModel = viewModel(),
) {
    val allEncounters by vm.encounters.collectAsState()
    val item = remember(allEncounters, encounterId) {
        allEncounters.find { it.encounter.id == encounterId }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(item?.snapshot?.displayName ?: "Traveler") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        if (item == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            val enc      = item.encounter
            val snapshot = item.snapshot

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(32.dp))

                // ── Avatar ────────────────────────────────────────────────────
                DiceBearAvatar(
                    seed = snapshot?.rotatingId ?: enc.rotatingId,
                    size = 120.dp,
                )

                Spacer(Modifier.height(20.dp))

                // ── Name ──────────────────────────────────────────────────────
                Text(
                    text = snapshot?.displayName ?: "Unknown Traveler",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp),
                )

                // ── Greeting ──────────────────────────────────────────────────
                if (!snapshot?.greeting.isNullOrBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "\u201C${snapshot!!.greeting}\u201D",
                        style = MaterialTheme.typography.bodyLarge,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 40.dp),
                    )
                }

                if (snapshot == null) {
                    Spacer(Modifier.height(10.dp))
                    SuggestionChip(
                        onClick = {},
                        label = { Text("Profile exchange pending\u2026") },
                    )
                }

                Spacer(Modifier.height(32.dp))
                HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp))
                Spacer(Modifier.height(24.dp))

                // ── Metadata cards ────────────────────────────────────────────
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val dateStr = remember(enc.seenAt) {
                        SimpleDateFormat(
                            "EEEE, MMM d \u00B7 HH:mm",
                            Locale.getDefault(),
                        ).format(Date(enc.seenAt))
                    }
                    MetaCard(label = "Encountered on", value = dateStr)

                    val (proximity, rssiDesc) = rssiToProximity(enc.rssi)
                    MetaCard(label = "Signal strength", value = "$rssiDesc  (${enc.rssi}\u00A0dBm)")
                    MetaCard(label = "Estimated distance", value = proximity)

                    if (snapshot != null) {
                        MetaCard(label = "Protocol version", value = "v${snapshot.protocolVersion}")
                    }
                    // ── Ghost Score ─────────────────────────────────────────────────────
                    if (snapshot?.ghostGame != null) {
                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(8.dp))
                        GhostScoreCard(snapshot)
                    }
                    // ── RetroAchievements ─────────────────────────────────────
                    if (snapshot?.retroUsername != null) {
                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(8.dp))
                        RetroSparkCard(snapshot)
                    }
                }

                Spacer(Modifier.height(40.dp))
            }
        }
    }
}

@Composable
private fun MetaCard(label: String, value: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun GhostScoreCard(snapshot: com.thunderpass.data.db.entity.PeerProfileSnapshot) {
    val game  = snapshot.ghostGame  ?: return
    val score = snapshot.ghostScore
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Row(
            modifier            = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment   = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("👻", style = MaterialTheme.typography.headlineMedium)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text  = game,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                if (score != null && score > 0L) {
                    Text(
                        text  = "Score: $score",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
            Text(
                text  = "beat it!",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}

/**
 * Rough RSSI → human-readable proximity.
 * Uses the Log-distance path loss model: distance = 10^((TxPower - RSSI) / (10 * n))
 * TxPower ≈ -59 dBm at 1 m, n ≈ 2.5 (indoor).
 */
private fun rssiToProximity(rssi: Int): Pair<String, String> {
    val txPower = -59.0
    val n = 2.5
    val distance = 10.0.pow((txPower - rssi) / (10.0 * n))
    val proximity = when {
        distance < 0.5 -> "Very close  (<\u00A00.5\u00A0m)"
        distance < 2.0 -> "Nearby  (~${distance.toInt()}\u00A0m)"
        distance < 8.0 -> "Moderate  (~${distance.toInt()}\u00A0m)"
        else           -> "Far  (~${distance.toInt()}\u00A0m)"
    }
    val desc = when {
        rssi >= -60  -> "Excellent"
        rssi >= -75  -> "Good"
        rssi >= -90  -> "Fair"
        else         -> "Weak"
    }
    return proximity to desc
}
