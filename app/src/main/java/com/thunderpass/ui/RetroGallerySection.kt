package com.thunderpass.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage
import com.thunderpass.retro.RetroAuthManager
import com.thunderpass.retro.RetroProfileCache
import com.thunderpass.retro.RetroProfileCacheData
import com.thunderpass.retro.RetroRetrofitClient
import kotlinx.coroutines.async
import com.thunderpass.ui.theme.BurntOrange
import com.thunderpass.ui.theme.SpaceCyan
import com.thunderpass.ui.theme.VividPurple

private val Gold        = Color(0xFFFFB300)
private val Amber       = Color(0xFFFFB300)
private val AmberLight  = Color(0xFFFFF59D)
private val OrangeDark  = Color(0xFFE65100)
private const val RA_IMAGE_BASE = "https://media.retroachievements.org"

// ─────────────────────────────────────────────────────────────────────────────
// RetroGallerySection — shows cached RA data as separate per-kind galleries.
//
// On composition: reads from SharedPrefs cache instantly (offline-safe), then
// triggers a background network refresh. When the refresh completes the UI
// recomposes to show the latest stats without requiring the user to open the
// Profile screen and press Save.
//
// Galleries shown:
//   1. RA Stats summary (username, softcore points, achievements unlocked)
//   2. Recent Games — horizontal scroll of per-game chips with cover art
//   3. Top Consoles — derived from recentGames, horizontal console chips
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun RetroGallerySection(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var data   by remember { mutableStateOf<RetroProfileCacheData?>(null) }
    var loaded by remember { mutableStateOf(false) }

    // Re-reads SharedPrefs whenever the cache is updated — by a background fetch
    // here OR by ProfileViewModel after a profile save. This makes every gallery
    // instance (HomeScreen, ProfileScreen) reactive without any extra plumbing.
    val cacheVersion by RetroProfileCache.cacheVersion.collectAsState()
    LaunchedEffect(cacheVersion) {
        data   = RetroProfileCache.load(context)
        loaded = true
    }

    // One-time background network refresh. On completion save() bumps cacheVersion
    // which triggers the LaunchedEffect above to re-read and recompose.
    LaunchedEffect(Unit) {
        val auth      = RetroAuthManager.getInstance(context)
        val dbProfile = com.thunderpass.data.db.ThunderPassDatabase.getInstance(context)
            .myProfileDao().get()
        val username  = (data?.username?.takeIf { it.isNotBlank() }
            ?: dbProfile?.retroUsername?.trim()?.takeIf { it.isNotBlank() })
            ?: return@LaunchedEffect
        val summaryJob    = async { RetroRetrofitClient.fetchRetroMetadata(username, auth) }
        val countJob      = async { RetroRetrofitClient.fetchSoftcoreAchievementCount(username, auth) }
        val result        = summaryJob.await()
        val softcoreCount = countJob.await()
        result.getOrNull()?.let { raProfile ->
            RetroProfileCache.save(
                context                    = context,
                username                   = username,
                points                     = raProfile.totalPoints,
                softcorePoints             = raProfile.totalSoftcorePoints,
                softcoreAchievementsEarned = softcoreCount,
                games                      = raProfile.recentlyPlayed ?: emptyList(),
                recentlyPlayedCount        = raProfile.recentlyPlayedCount,
            )
            // save() bumps cacheVersion → LaunchedEffect(cacheVersion) re-reads automatically
        }
    }

    if (!loaded) return   // avoid brief empty-state flash while prefs are read

    Column(
        modifier            = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val raData = data

        if (raData == null) {
            // ── Username not configured ─────────────────────────────────────
            RetroNotConfiguredCard()
            return@Column
        }

        // ── 1. Stats summary card ───────────────────────────────────────────
        RetroStatsCard(raData)

        // ── 2. Recent Games gallery ─────────────────────────────────────────
        if (raData.recentGames.isNotEmpty()) {
            RetroGalleryCard(title = "RECENT GAMES") {
                Row(
                    modifier              = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    raData.recentGames.forEach { (title, console, imageIcon) ->
                        RecentGameChip(
                            title     = title,
                            console   = console,
                            imageUrl  = imageIcon?.let { "$RA_IMAGE_BASE$it" },
                        )
                    }
                }
            }
        }

        // ── 3. Top Consoles gallery ─────────────────────────────────────────
        val topConsoles = raData.recentGames
            .groupBy { it.second }
            .entries
            .sortedByDescending { it.value.size }
            .take(6)
            .map { (console, games) ->
                Triple(console, games.size, consoleNameToImageUrl(console))
            }
        if (topConsoles.isNotEmpty()) {
            RetroGalleryCard(title = "TOP CONSOLES") {
                Row(
                    modifier              = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    topConsoles.forEach { (console, count, iconUrl) ->
                        ConsoleChip(
                            console   = console,
                            gameCount = count,
                            imageUrl  = iconUrl,
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Empty state — username not configured yet; same gradient banner, no stats
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun RetroNotConfiguredCard() {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(8.dp, shape)
            .drawBehind {
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(VividPurple, SpaceCyan),
                        start  = Offset(0f, 0f),
                        end    = Offset(size.width, size.height),
                    )
                )
                val overlayColors = listOf(
                    Color.White.copy(alpha = 0.07f),
                    Color.White.copy(alpha = 0.05f),
                    Color.White.copy(alpha = 0.04f),
                    Color.White.copy(alpha = 0.06f),
                )
                val offsets = listOf(
                    Offset(size.width * 0.15f, size.height * 0.1f),
                    Offset(size.width * 0.65f, size.height * 0.05f),
                    Offset(size.width * 0.5f,  size.height * 0.7f),
                    Offset(size.width * 0.85f, size.height * 0.6f),
                )
                val squareSize = size.minDimension * 1.2f
                offsets.forEachIndexed { i, offset ->
                    rotate(degrees = 25f + i * 15f, pivot = offset) {
                        drawRect(
                            color   = overlayColors[i],
                            topLeft = Offset(offset.x - squareSize / 2, offset.y - squareSize / 2),
                            size    = Size(squareSize, squareSize),
                        )
                    }
                }
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text          = "RETROACHIEVEMENTS",
                style         = MaterialTheme.typography.labelSmall,
                fontWeight    = FontWeight.Bold,
                color         = Color.White,
                letterSpacing = 0.8.sp,
            )
            Text(
                text  = "Username not shared",
                style = MaterialTheme.typography.titleSmall,
                color = Color.White.copy(alpha = 0.7f),
            )
            Text(
                text  = "Add your RetroAchievements username in Profile settings to show your stats here.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.5f),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Stats summary — gradient banner, softcore + hardcore points, black text
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun RetroStatsCard(raData: RetroProfileCacheData) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(8.dp, shape)
            .drawBehind {
                // Gradient background (VividPurple → SpaceCyan)
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(VividPurple, SpaceCyan),
                        start  = Offset(0f, 0f),
                        end    = Offset(size.width, size.height),
                    )
                )
                // Decorative semi-transparent rotated squares overlay
                val overlayColors = listOf(
                    Color.White.copy(alpha = 0.07f),
                    Color.White.copy(alpha = 0.05f),
                    Color.White.copy(alpha = 0.04f),
                    Color.White.copy(alpha = 0.06f),
                )
                val offsets = listOf(
                    Offset(size.width * 0.15f, size.height * 0.1f),
                    Offset(size.width * 0.65f, size.height * 0.05f),
                    Offset(size.width * 0.5f,  size.height * 0.7f),
                    Offset(size.width * 0.85f, size.height * 0.6f),
                )
                val squareSize = size.minDimension * 1.2f
                offsets.forEachIndexed { i, offset ->
                    rotate(degrees = 25f + i * 15f, pivot = offset) {
                        drawRect(
                            color   = overlayColors[i],
                            topLeft = Offset(offset.x - squareSize / 2, offset.y - squareSize / 2),
                            size    = Size(squareSize, squareSize),
                        )
                    }
                }
            }
    ) {
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text          = "RETROACHIEVEMENTS",
                    style         = MaterialTheme.typography.labelSmall,
                    fontWeight    = FontWeight.Bold,
                    color         = Color.White,
                    letterSpacing = 0.8.sp,
                )
                Text(
                    text       = raData.username,
                    style      = MaterialTheme.typography.titleSmall,
                    color      = Color.White,
                    fontWeight = FontWeight.Bold,
                )
                val achievementLabel = if (raData.softcoreAchievementsEarned > 0)
                    "${raData.softcoreAchievementsEarned} achievements (softcore)"
                else
                    "${raData.recentlyPlayedCount} games played"
                Text(
                    text  = achievementLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text       = "%,d".format(raData.softcorePoints),
                    fontSize   = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color      = Gold,
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    text          = "SOFTCORE",
                    style         = MaterialTheme.typography.labelSmall,
                    color         = Color.White.copy(alpha = 0.7f),
                    letterSpacing = 1.sp,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Generic gallery wrapper card — neutral surface, gold label
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun RetroGalleryCard(
    title:   String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        shape     = RoundedCornerShape(12.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier  = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text          = title,
                style         = MaterialTheme.typography.labelSmall,
                fontWeight    = FontWeight.Bold,
                color         = MaterialTheme.colorScheme.onSurface,
                letterSpacing = 0.8.sp,
            )
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Per-game card chip — cover art on left, neutral surface, uniform height
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun RecentGameChip(title: String, console: String, imageUrl: String?) {
    Card(
        shape     = RoundedCornerShape(10.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier  = Modifier
            .width(140.dp)
            .height(76.dp),
    ) {
        Row(
            modifier          = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (imageUrl != null) {
                SubcomposeAsyncImage(
                    model              = imageUrl,
                    contentDescription = title,
                    contentScale       = ContentScale.Crop,
                    modifier           = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(6.dp)),
                )
                Spacer(Modifier.width(8.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text       = title,
                    style      = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color      = MaterialTheme.colorScheme.onSurface,
                    maxLines   = 2,
                    overflow   = TextOverflow.Ellipsis,
                    lineHeight = 13.sp,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text     = console,
                    style    = MaterialTheme.typography.labelSmall,
                    color    = Gold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Console chip — neutral pill, uniform height
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ConsoleChip(console: String, gameCount: Int, imageUrl: String? = null) {
    Card(
        shape     = RoundedCornerShape(10.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier  = Modifier
            .width(140.dp)
            .height(76.dp),
    ) {
        Row(
            modifier          = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (imageUrl != null) {
                SubcomposeAsyncImage(
                    model              = imageUrl,
                    contentDescription = console,
                    contentScale       = ContentScale.Crop,
                    modifier           = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(6.dp)),
                )
                Spacer(Modifier.width(8.dp))
            }
            Column(
                modifier            = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text       = console,
                    style      = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color      = MaterialTheme.colorScheme.onSurface,
                    maxLines   = 2,
                    overflow   = TextOverflow.Ellipsis,
                    lineHeight = 13.sp,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text  = "$gameCount game${if (gameCount != 1) "s" else ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }
        }
    }
}

private fun consoleNameToImageUrl(name: String): String? {
    val base = "https://raw.githubusercontent.com/RetroAchievements/RAWeb/master/public/assets/images/system"
    val slug = when {
        name.contains("DSi", ignoreCase = true)                                                -> "dsi"
        name.contains("Nintendo DS", ignoreCase = true)                                        -> "ds"
        name.contains("3DS", ignoreCase = true)                                                -> "3ds"
        name.contains("Game Boy Advance", ignoreCase = true)                                  -> "gba"
        name.contains("Game Boy Color", ignoreCase = true)                                    -> "gbc"
        name.contains("Game Boy", ignoreCase = true)                                          -> "gb"
        name.contains("Wii U", ignoreCase = true)                                             -> "wiiu"
        name.contains("Wii", ignoreCase = true)                                               -> "wii"
        name.contains("GameCube", ignoreCase = true)                                          -> "gc"
        name.contains("Nintendo 64", ignoreCase = true)                                       -> "n64"
        name.contains("SNES", ignoreCase = true) || name.contains("Super Nintendo", ignoreCase = true) || name.contains("Super Famicom", ignoreCase = true) -> "snes"
        name.contains("Famicom Disk", ignoreCase = true)                                      -> "fds"
        name.contains("NES", ignoreCase = true) || name.contains("Famicom", ignoreCase = true) -> "nes"
        name.contains("PlayStation 2", ignoreCase = true)                                     -> "ps2"
        name.contains("PlayStation 3", ignoreCase = true)                                     -> "ps3"
        name.contains("PSP", ignoreCase = true)                                               -> "psp"
        name.contains("PlayStation", ignoreCase = true)                                       -> "ps1"
        name.contains("32X", ignoreCase = true)                                               -> "32x"
        name.contains("Sega CD", ignoreCase = true)                                           -> "scd"
        name.contains("Mega Drive", ignoreCase = true) || name.contains("Genesis", ignoreCase = true) -> "md"
        name.contains("Saturn", ignoreCase = true)                                            -> "sat"
        name.contains("Dreamcast", ignoreCase = true)                                         -> "dc"
        name.contains("Game Gear", ignoreCase = true)                                         -> "gg"
        name.contains("Master System", ignoreCase = true)                                     -> "sms"
        name.contains("SG-1000", ignoreCase = true)                                           -> "sg1k"
        name.contains("PC Engine CD", ignoreCase = true)                                      -> "pccd"
        name.contains("PC Engine", ignoreCase = true) || name.contains("TurboGrafx", ignoreCase = true) -> "pce"
        name.contains("Atari 2600", ignoreCase = true)                                        -> "2600"
        name.contains("Atari 5200", ignoreCase = true)                                        -> "5200"
        name.contains("Atari 7800", ignoreCase = true)                                        -> "7800"
        name.contains("Atari Lynx", ignoreCase = true)                                        -> "lynx"
        name.contains("Jaguar CD", ignoreCase = true)                                         -> "jag"
        name.contains("Jaguar", ignoreCase = true)                                            -> "jag"
        name.contains("Neo Geo Pocket", ignoreCase = true)                                    -> "ngp"
        name.contains("WonderSwan Color", ignoreCase = true)                                  -> "wsv"
        name.contains("WonderSwan", ignoreCase = true)                                        -> "ws"
        name.contains("Virtual Boy", ignoreCase = true)                                       -> "vb"
        name.contains("3DO", ignoreCase = true)                                               -> "3do"
        name.contains("Arcade", ignoreCase = true)                                            -> "arc"
        name.contains("MSX", ignoreCase = true)                                               -> "msx"
        name.contains("Xbox", ignoreCase = true)                                              -> "xbox"
        else                                                                                    -> null
    } ?: return null
    return "$base/$slug.png"
}

