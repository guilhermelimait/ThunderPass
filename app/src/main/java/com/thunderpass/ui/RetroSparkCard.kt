package com.thunderpass.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thunderpass.data.db.entity.PeerProfileSnapshot

private const val SEP = "|||"

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
                Text("🏆", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        text       = "RetroAchievements",
                        style      = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color      = Color(0xFFFFB300),
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
                        label = "Games",
                        value = snapshot.retroRecentlyPlayedCount?.toString() ?: "—",
                    )
                    when {
                        snapshot.retroTotalPoints > 20_000L ->
                            RetroStat(label = "Rank", value = "🏆 Elite")
                        snapshot.retroTotalPoints > 5_000L  ->
                            RetroStat(label = "Rank", value = "⭐ Veteran")
                        else ->
                            RetroStat(label = "Rank", value = "🎮 Player")
                    }
                }

                // ── Actual recent games (if available) ───────────────────────
                val titles   = snapshot.retroGameTitles
                    ?.split(SEP)?.filter { it.isNotBlank() } ?: emptyList()
                val consoles = snapshot.retroGameConsoles
                    ?.split(SEP)?.filter { it.isNotBlank() } ?: emptyList()
                val games = titles.zip(consoles)

                if (games.isNotEmpty()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text(
                        text  = "Recent Games",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier              = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        games.forEach { (title, console) ->
                            PeerGameChip(title = title, console = console)
                        }
                    }
                } else {
                    // Fallback: show count with emoji placeholders (pre-fetch data)
                    val gameCount = snapshot.retroRecentlyPlayedCount ?: 0
                    if (gameCount > 0) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Text(
                            text  = "Recently Played — $gameCount game${if (gameCount != 1) "s" else ""}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else if (!snapshot.retroFetchAttempted) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    text  = "Fetching RA stats…",
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
private fun PeerGameChip(title: String, console: String) {
    Card(
        shape     = RoundedCornerShape(10.dp),
        colors    = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        elevation = CardDefaults.cardElevation(2.dp),
        modifier  = Modifier.width(120.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(
                text       = title,
                fontSize   = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color      = MaterialTheme.colorScheme.onSurface,
                maxLines   = 2,
                overflow   = TextOverflow.Ellipsis,
                lineHeight = 14.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text      = console,
                fontSize  = 10.sp,
                color     = Color(0xFFFFB300),
                fontFamily = FontFamily.Monospace,
                maxLines  = 1,
                overflow  = TextOverflow.Ellipsis,
            )
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
