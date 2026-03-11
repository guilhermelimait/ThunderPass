package com.thunderpass.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage
import com.thunderpass.data.db.entity.PeerProfileSnapshot
import com.thunderpass.ui.theme.SpaceCyan
import com.thunderpass.ui.theme.VividPurple

private const val SEP            = "|||"
private val Gold                 = Color(0xFFFFB300)
private val Amber                = Color(0xFFFFB300)
private val AmberLight           = Color(0xFFFFF59D)
private val OrangeDark           = Color(0xFFE65100)
private const val RA_IMAGE_BASE  = "https://media.retroachievements.org"

// ─────────────────────────────────────────────────────────────────────────────
// RetroSparkCard — peer's RA data shown in EncounterDetailScreen.
// All data comes from PeerProfileSnapshot (received via BLE / cached DB row).
// Visual style mirrors RetroGallerySection (own profile on HomeScreen).
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun RetroSparkCard(snapshot: PeerProfileSnapshot) {
    val username = snapshot.retroUsername

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {

        // ── Stats summary card ─────────────────────────────────────────────
        val statsShape = RoundedCornerShape(14.dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(8.dp, statsShape)
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
                    if (username != null) {
                        Text(
                            text       = username,
                            style      = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color      = Color.White,
                        )
                        Text(
                            text  = "${snapshot.retroRecentlyPlayedCount ?: "—"} games played",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f),
                        )
                    } else {
                        Text(
                            text  = "Not shared / private",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f),
                        )
                    }
                }

                // Points — right side (only when available)
                if (username != null && snapshot.retroTotalPoints != null) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text       = "%,d".format(snapshot.retroTotalPoints),
                            style      = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color      = Gold,
                            fontFamily = FontFamily.Monospace,
                        )
                        Text(
                            text          = "HARDCORE",
                            style         = MaterialTheme.typography.labelSmall,
                            color         = Color.White.copy(alpha = 0.7f),
                            letterSpacing = 1.sp,
                        )
                    }
                } else if (username != null && !snapshot.retroFetchAttempted) {
                    CircularProgressIndicator(
                        modifier    = Modifier.size(28.dp),
                        color       = Gold,
                        strokeWidth = 2.5.dp,
                    )
                }
            }
        }

        // ── Nothing else to show if no username ────────────────────────────
        if (username == null || snapshot.retroTotalPoints == null) return@Column

        // ── Parse games ────────────────────────────────────────────────────
        val titles   = snapshot.retroGameTitles
            ?.split(SEP)?.filter { it.isNotBlank() } ?: emptyList()
        val consoles = snapshot.retroGameConsoles
            ?.split(SEP)?.filter { it.isNotBlank() } ?: emptyList()
        val images   = snapshot.retroGameImages
            ?.split(SEP) ?: emptyList()
        val games    = titles.mapIndexed { i, title ->
            Triple(title, consoles.getOrElse(i) { "" }, images.getOrNull(i)?.takeIf { it.isNotBlank() })
        }

        // ── Recent Games gallery ───────────────────────────────────────────
        if (games.isNotEmpty()) {
            PeerGalleryCard(title = "RECENT GAMES") {
                Row(
                    modifier              = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    games.forEach { (title, console, imageIcon) ->
                        PeerGameChip(
                            title    = title,
                            console  = console,
                            imageUrl = imageIcon?.let { "$RA_IMAGE_BASE$it" },
                        )
                    }
                }
            }
        } else {
            val gameCount = snapshot.retroRecentlyPlayedCount ?: 0
            if (gameCount > 0) {
                PeerGalleryCard(title = "RECENT GAMES") {
                    Text(
                        text  = "$gameCount game${if (gameCount != 1) "s" else ""} played",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        // ── Top Consoles gallery ───────────────────────────────────────────
        val topConsoles = games
            .groupBy { it.second }
            .mapValues { it.value.size }
            .entries
            .sortedByDescending { it.value }
            .take(6)
        if (topConsoles.isNotEmpty()) {
            PeerGalleryCard(title = "TOP CONSOLES") {
                Row(
                    modifier              = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    topConsoles.forEach { (console, count) ->
                        PeerConsoleChip(console = console, gameCount = count)
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Gallery wrapper card — neutral surface, gold label
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PeerGalleryCard(
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
// Game chip — cover art on left, neutral surface, uniform height
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PeerGameChip(title: String, console: String, imageUrl: String?) {
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
// Console chip — neutral pill, image on left + name + game count
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PeerConsoleChip(console: String, gameCount: Int) {
    val imageUrl = consoleNameToImageUrl(console)
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

// ─────────────────────────────────────────────────────────────────────────────
// Console name → RAWeb image URL (mirrors RetroGallerySection mapping)
// ─────────────────────────────────────────────────────────────────────────────

private fun consoleNameToImageUrl(name: String): String? {
    val base = "https://raw.githubusercontent.com/RetroAchievements/RAWeb/master/public/assets/images/system"
    val slug = when {
        name.contains("DSi", ignoreCase = true)                                                -> "dsi"
        name.contains("Nintendo DS", ignoreCase = true)                                        -> "ds"
        name.contains("3DS", ignoreCase = true)                                                -> "3ds"
        name.contains("Game Boy Advance", ignoreCase = true)                                   -> "gba"
        name.contains("Game Boy Color", ignoreCase = true)                                     -> "gbc"
        name.contains("Game Boy", ignoreCase = true)                                           -> "gb"
        name.contains("Wii U", ignoreCase = true)                                              -> "wiiu"
        name.contains("Wii", ignoreCase = true)                                                -> "wii"
        name.contains("GameCube", ignoreCase = true)                                           -> "gc"
        name.contains("Nintendo 64", ignoreCase = true)                                        -> "n64"
        name.contains("SNES", ignoreCase = true) || name.contains("Super Nintendo", ignoreCase = true) || name.contains("Super Famicom", ignoreCase = true) -> "snes"
        name.contains("Famicom Disk", ignoreCase = true)                                       -> "fds"
        name.contains("NES", ignoreCase = true) || name.contains("Famicom", ignoreCase = true) -> "nes"
        name.contains("PlayStation 2", ignoreCase = true)                                      -> "ps2"
        name.contains("PlayStation 3", ignoreCase = true)                                      -> "ps3"
        name.contains("PSP", ignoreCase = true)                                                -> "psp"
        name.contains("PlayStation", ignoreCase = true)                                        -> "ps1"
        name.contains("32X", ignoreCase = true)                                                -> "32x"
        name.contains("Sega CD", ignoreCase = true)                                            -> "scd"
        name.contains("Mega Drive", ignoreCase = true) || name.contains("Genesis", ignoreCase = true) -> "md"
        name.contains("Saturn", ignoreCase = true)                                             -> "sat"
        name.contains("Dreamcast", ignoreCase = true)                                          -> "dc"
        name.contains("Game Gear", ignoreCase = true)                                          -> "gg"
        name.contains("Master System", ignoreCase = true)                                      -> "sms"
        name.contains("SG-1000", ignoreCase = true)                                            -> "sg1k"
        name.contains("PC Engine CD", ignoreCase = true)                                       -> "pccd"
        name.contains("PC Engine", ignoreCase = true) || name.contains("TurboGrafx", ignoreCase = true) -> "pce"
        name.contains("Atari 2600", ignoreCase = true)                                         -> "2600"
        name.contains("Atari 5200", ignoreCase = true)                                         -> "5200"
        name.contains("Atari 7800", ignoreCase = true)                                         -> "7800"
        name.contains("Atari Lynx", ignoreCase = true)                                         -> "lynx"
        name.contains("Jaguar CD", ignoreCase = true)                                          -> "jag"
        name.contains("Jaguar", ignoreCase = true)                                             -> "jag"
        name.contains("Neo Geo Pocket", ignoreCase = true)                                     -> "ngp"
        name.contains("WonderSwan Color", ignoreCase = true)                                   -> "wsv"
        name.contains("WonderSwan", ignoreCase = true)                                         -> "ws"
        name.contains("Virtual Boy", ignoreCase = true)                                        -> "vb"
        name.contains("3DO", ignoreCase = true)                                                -> "3do"
        name.contains("Arcade", ignoreCase = true)                                             -> "arc"
        name.contains("MSX", ignoreCase = true)                                                -> "msx"
        name.contains("Xbox", ignoreCase = true)                                               -> "xbox"
        else                                                                                    -> null
    } ?: return null
    return "$base/$slug.png"
}

