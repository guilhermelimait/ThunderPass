package com.thunderpass.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.thunderpass.data.db.entity.PeerProfileSnapshot

@Composable
fun RetroSparkCard(snapshot: PeerProfileSnapshot) {
    if (snapshot.retroUsername == null) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier            = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // ── Header row ──────────────────────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🎮", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        text       = "RetroAchievements",
                        style      = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color      = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text       = snapshot.retroUsername,
                        style      = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            // ── Stats or loading placeholder ─────────────────────────────────
            if (snapshot.retroTotalPoints != null) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    modifier            = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    RetroStat(
                        label = "Points",
                        value = "%,d".format(snapshot.retroTotalPoints),
                    )
                    RetroStat(
                        label = "Recent Games",
                        value = snapshot.retroRecentlyPlayedCount?.toString() ?: "—",
                    )
                    when {
                        snapshot.retroTotalPoints > 20_000L ->
                            RetroStat(label = "Rank", value = "\uD83C\uDFC6 Elite")
                        snapshot.retroTotalPoints > 5_000L  ->
                            RetroStat(label = "Rank", value = "\u2B50 Veteran")
                        else ->
                            RetroStat(label = "Rank", value = "\uD83C\uDFAE Player")
                    }
                }

                // ── Mastery icons — one chip per recently played game ────────
                val gameCount = snapshot.retroRecentlyPlayedCount ?: 0
                if (gameCount > 0) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text(
                        text  = "Recently Played",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val icons = listOf("\uD83C\uDFAE", "\uD83D\uDD79\uFE0F", "\uD83D\uDC7E", "\uD83C\uDFC6", "\u2694\uFE0F", "\uD83C\uDF1F", "\uD83D\uDD25", "\uD83D\uDC8E")
                    Row(
                        modifier              = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        repeat(gameCount.coerceAtMost(8)) { i ->
                            SuggestionChip(
                                onClick = {},
                                label   = { Text(icons[i % icons.size]) },
                            )
                        }
                    }
                }
            } else if (!snapshot.retroFetchAttempted) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    text  = "Fetching RA stats\u2026",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text  = "RA stats unavailable — set up your RetroAchievements account to see stats.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RetroStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text       = value,
            style      = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text  = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
