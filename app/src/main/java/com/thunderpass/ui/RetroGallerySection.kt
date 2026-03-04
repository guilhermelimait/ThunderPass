package com.thunderpass.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thunderpass.retro.RetroProfileCache
import com.thunderpass.retro.RetroProfileCacheData

// ─────────────────────────────────────────────────────────────────────────────
// RetroGallerySection — shows cached RA data as separate per-kind galleries.
//
// Reads from SharedPrefs cache (written by RetroProfileCache.save) so it is
// always instant and works fully offline. A cache refresh is triggered from
// ProfileScreen whenever the user saves their RA credentials.
//
// Galleries shown:
//   1. RA Stats summary (username, total points, recently played count)
//   2. Recent Games — horizontal scroll of per-game chips
//   3. Top Consoles — derived from recentGames, horizontal console chips
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun RetroGallerySection(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var data by remember { mutableStateOf<RetroProfileCacheData?>(null) }

    // Load from SharedPrefs on first composition (lightweight, no network call)
    LaunchedEffect(Unit) {
        data = RetroProfileCache.load(context)
    }

    val raData = data ?: return   // nothing to show — user hasn't set RA credentials yet

    Column(
        modifier            = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // ── 1. Stats summary card ───────────────────────────────────────────
        RetroStatsCard(raData)

        // ── 2. Recent Games gallery ─────────────────────────────────────────
        if (raData.recentGames.isNotEmpty()) {
            RetroGalleryCard(
                title   = "🎮  RECENT GAMES",
            ) {
                Row(
                    modifier              = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    raData.recentGames.forEach { (title, console) ->
                        RecentGameChip(title = title, console = console)
                    }
                }
            }
        }

        // ── 3. Top Consoles gallery ─────────────────────────────────────────
        val topConsoles = raData.recentGames
            .groupBy { it.second }
            .mapValues { it.value.size }
            .entries
            .sortedByDescending { it.value }
            .take(6)
        if (topConsoles.isNotEmpty()) {
            RetroGalleryCard(title = "🕹️  TOP CONSOLES") {
                Row(
                    modifier              = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    topConsoles.forEach { (console, count) ->
                        ConsoleChip(console = console, gameCount = count)
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Stats summary (username + points + recently played count)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun RetroStatsCard(raData: RetroProfileCacheData) {
    Card(
        shape     = RoundedCornerShape(12.dp),
        colors    = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    text          = "🏆  RETROACHIEVEMENTS",
                    fontFamily    = FontFamily.Monospace,
                    fontWeight    = FontWeight.Bold,
                    fontSize      = 11.sp,
                    color         = Color(0xFFFFB300),
                    letterSpacing = 0.8.sp,
                )
                Text(
                    text       = raData.username,
                    fontSize   = 12.sp,
                    color      = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text  = "${raData.recentlyPlayedCount} games played",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text       = "%,d".format(raData.totalPoints),
                    fontSize   = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color      = Color(0xFFFFB300),
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    text     = "POINTS",
                    fontSize = 9.sp,
                    color    = Color(0xFFFFB300).copy(alpha = 0.75f),
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Generic gallery wrapper card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun RetroGalleryCard(
    title:   String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        shape     = RoundedCornerShape(12.dp),
        colors    = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text          = title,
                fontFamily    = FontFamily.Monospace,
                fontWeight    = FontWeight.Bold,
                fontSize      = 10.sp,
                color         = Color(0xFFFFB300),
                letterSpacing = 0.8.sp,
            )
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Per-game card chip
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun RecentGameChip(title: String, console: String) {
    Card(
        shape   = RoundedCornerShape(10.dp),
        colors  = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        elevation = CardDefaults.cardElevation(2.dp),
        modifier  = Modifier.width(118.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(
                text       = title,
                fontSize   = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color      = MaterialTheme.colorScheme.onSurface,
                maxLines   = 2,
                overflow   = TextOverflow.Ellipsis,
                lineHeight  = 14.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text       = console,
                fontSize   = 10.sp,
                color      = Color(0xFFFFB300),
                fontFamily = FontFamily.Monospace,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Console chip (top consoles gallery)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ConsoleChip(console: String, gameCount: Int) {
    ElevatedCard(
        shape  = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(
            modifier            = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text       = console,
                fontSize   = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color      = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis,
            )
            Text(
                text  = "$gameCount game${if (gameCount != 1) "s" else ""}",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
            )
        }
    }
}
