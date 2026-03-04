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
// RetroGallerySection — shows cached RA data (total points + recent games).
//
// Reads from SharedPrefs cache (written by RetroProfileCache.save) so it is
// always instant and works fully offline. A cache refresh is triggered from
// ProfileScreen whenever the user saves their RA credentials.
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

    Card(
        modifier  = modifier,
        shape     = RoundedCornerShape(12.dp),
        colors    = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {

            // ── Header row ───────────────────────────────────────────────────
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier              = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text       = "🏆 RETROACHIEVEMENTS",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize   = 11.sp,
                    color      = Color(0xFFFFB300),
                    letterSpacing = 0.8.sp,
                )
                Text(
                    text      = "%,d pts".format(raData.totalPoints),
                    fontSize  = 11.sp,
                    color     = Color(0xFFFFB300),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                )
            }

            Text(
                text  = raData.username,
                fontSize = 11.sp,
                color = Color(0xFF777777),
                fontFamily = FontFamily.Monospace,
            )

            Spacer(Modifier.height(10.dp))

            // ── Recent games horizontal gallery ──────────────────────────────
            if (raData.recentGames.isEmpty()) {
                Text(
                    text     = "No recent games cached yet.",
                    fontSize = 12.sp,
                    color    = Color(0xFF555555),
                )
            } else {
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
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Per-game card chip
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun RecentGameChip(title: String, console: String) {
    Card(
        shape   = RoundedCornerShape(10.dp),
        colors  = CardDefaults.cardColors(containerColor = Color(0xFF24243A)),
        elevation = CardDefaults.cardElevation(2.dp),
        modifier  = Modifier.width(118.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(
                text       = title,
                fontSize   = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color      = Color(0xFFDDDDDD),
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
