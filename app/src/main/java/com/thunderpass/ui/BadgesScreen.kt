package com.thunderpass.ui

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

// ─────────────────────────────────────────────────────────────────────────────
// Tier colors — the PRIMARY visual language of a badge
// ─────────────────────────────────────────────────────────────────────────────

private val TIER_LOCKED = Color(0xFF9E9E9E)          // gray
private val TIER_BRONZE = Color(0xFFCD7F32)          // bronze
private val TIER_SILVER = Color(0xFFC0C0C0)          // silver
private val TIER_GOLD   = Color(0xFFFFD700)          // gold

private fun tierColor(tier: Int) = when (tier) {
    1    -> TIER_BRONZE
    2    -> TIER_SILVER
    3    -> TIER_GOLD
    else -> TIER_LOCKED
}

private fun tierLabel(tier: Int) = when (tier) {
    1    -> "BRONZE"
    2    -> "SILVER"
    3    -> "GOLD"
    else -> "LOCKED"
}

// ─────────────────────────────────────────────────────────────────────────────
// Category — used only for grouping & small accent dot
// ─────────────────────────────────────────────────────────────────────────────

private data class BadgeDef(
    val label:       String,
    val category:    BadgeCategory,
    val description: String,
    val tier:        Int   = 0,   // 0=locked, 1=bronze, 2=silver, 3=gold
    val progress:    Float = 0f,  // 0..1 (only shown when tier==0 and progress>0)
)

private enum class BadgeCategory(val label: String, val accentColor: Color, val sortOrder: Int) {
    ENCOUNTERS("Encounters",          Color(0xFFFFD430), 0),
    CONSOLE(   "Consoles",            Color(0xFF9C27B0), 1),
    GEO(       "Geolocation",         Color(0xFF009688), 2),
    SOCIAL(    "Social",              Color(0xFF00BCD4), 3),
    GAMES(     "Games",               Color(0xFFFF5722), 4),
    FOUNDERS(  "Founders & Creators", Color(0xFFFFD700), 5),
}

// ─────────────────────────────────────────────────────────────────────────────
// Badge definitions — sorted by category, then by tier desc, then progress desc
// ─────────────────────────────────────────────────────────────────────────────

private val ALL_BADGES: List<BadgeDef> = listOf(
    // ─── Encounters: The Frequency ───────────────────────────────────────────
    BadgeDef("First Contact",    BadgeCategory.ENCOUNTERS, "Pass your very first user",                           tier = 1, progress = 1f),
    BadgeDef("Double Tap",       BadgeCategory.ENCOUNTERS, "Pass the same user on two different days",            tier = 0, progress = 0.3f),
    BadgeDef("High-Frequency",   BadgeCategory.ENCOUNTERS, "Pass 10 people in a single 24-hour window",          tier = 0, progress = 0f),
    BadgeDef("Century Club",     BadgeCategory.ENCOUNTERS, "Reach 100 total unique encounters",                   tier = 0, progress = 0.05f),
    BadgeDef("Ghost Town",       BadgeCategory.ENCOUNTERS, "App open 4 hours without meeting anyone",            tier = 0, progress = 0f),
    BadgeDef("Surge Limit",      BadgeCategory.ENCOUNTERS, "Encounter 50 unique users in a single week",         tier = 0, progress = 0f),
    BadgeDef("Eternal Pulse",    BadgeCategory.ENCOUNTERS, "Pass a user from ThunderPass Launch Week (v1.0)",     tier = 0, progress = 0f),
    // ─── Consoles: The Hardware ──────────────────────────────────────────────
    BadgeDef("80's Kid",         BadgeCategory.CONSOLE, "Match with an NES, Master System or Atari player",      tier = 0, progress = 0f),
    BadgeDef("16-Bit Power",     BadgeCategory.CONSOLE, "Match with an SNES or Genesis player",                  tier = 0, progress = 0f),
    BadgeDef("Handheld Hero",    BadgeCategory.CONSOLE, "Match with a Game Boy (DMG/Color/Advance) player",      tier = 0, progress = 0f),
    BadgeDef("The Disc Age",     BadgeCategory.CONSOLE, "Match with a PS1, Saturn or Dreamcast player",          tier = 0, progress = 0f),
    BadgeDef("Arcade Ally",      BadgeCategory.CONSOLE, "Match with a MAME or FinalBurn Neo player",             tier = 0, progress = 0f),
    BadgeDef("The Underdog",     BadgeCategory.CONSOLE, "Match with a WonderSwan, Neo Geo Pocket or Lynx player",tier = 0, progress = 0f),
    BadgeDef("64-Bit Sync",      BadgeCategory.CONSOLE, "Match with a Nintendo 64 or Jaguar player",             tier = 0, progress = 0f),
    // ─── GeoLocation: The Grid ───────────────────────────────────────────────
    BadgeDef("Local Circuit",    BadgeCategory.GEO, "Pass 5 people in your current city",                        tier = 0, progress = 0.2f),
    BadgeDef("Long Distance",    BadgeCategory.GEO, "Pass someone whose Home City is 500+ miles away",           tier = 0, progress = 0f),
    BadgeDef("Border Crosser",   BadgeCategory.GEO, "Pass a user from a different country",                      tier = 0, progress = 0f),
    BadgeDef("Travel Log",       BadgeCategory.GEO, "Spark in three different cities in one week",               tier = 0, progress = 0f),
    BadgeDef("Grid Navigator",   BadgeCategory.GEO, "Spark at a major airport or train station",                 tier = 0, progress = 0f),
    BadgeDef("Urban Legend",     BadgeCategory.GEO, "Encounter someone in a city with 5M+ population",           tier = 0, progress = 0f),
    BadgeDef("Off-Grid",         BadgeCategory.GEO, "Record an encounter above 2,000 metres elevation",          tier = 0, progress = 0f),
    // ─── Social: The Connection ──────────────────────────────────────────────
    BadgeDef("Shared Quest",     BadgeCategory.SOCIAL, "Both you and the peer are playing the same game",         tier = 0, progress = 0f),
    BadgeDef("Hardcore Sync",    BadgeCategory.SOCIAL, "Both users have Hardcore Mode enabled on RA",             tier = 0, progress = 0f),
    BadgeDef("Friendly Fire",    BadgeCategory.SOCIAL, "Send a High Five to a user you've passed 5+ times",       tier = 0, progress = 0f),
    BadgeDef("The Rival",        BadgeCategory.SOCIAL, "Pass someone with a higher Global Rank than you",         tier = 0, progress = 0f),
    BadgeDef("Influence Surge",  BadgeCategory.SOCIAL, "10 different people viewed your profile today",           tier = 0, progress = 0f),
    BadgeDef("Badge Collector",  BadgeCategory.SOCIAL, "Pass someone who has more ThunderPass badges than you",   tier = 0, progress = 0f),
    BadgeDef("Profile Ghost",    BadgeCategory.SOCIAL, "Pass someone with their Bio set to private",              tier = 0, progress = 0f),
    // ─── Games: The Software ─────────────────────────────────────────────────
    BadgeDef("Master of Monsters",  BadgeCategory.GAMES, "Pass someone who Mastered a Pokémon game on RA",       tier = 0, progress = 0f),
    BadgeDef("Speedrunner's Spark", BadgeCategory.GAMES, "Pass someone with a Fastest Completion record on RA",  tier = 0, progress = 0f),
    BadgeDef("RPG Grinder",         BadgeCategory.GAMES, "Pass a user playing a game with 40+ hours playtime",   tier = 0, progress = 0f),
    BadgeDef("Survival Horror",     BadgeCategory.GAMES, "Match with someone playing a horror game after 10 PM",  tier = 0, progress = 0f),
    BadgeDef("The Completionist",   BadgeCategory.GAMES, "Match a user with 50+ Mastery (100%) badges on RA",    tier = 0, progress = 0f),
    BadgeDef("Genre Specialist",    BadgeCategory.GAMES, "Pass someone who played 10+ games in the same genre",  tier = 0, progress = 0f),
    BadgeDef("Hidden Gem",          BadgeCategory.GAMES, "Match a user playing a game with <100 RA players",     tier = 0, progress = 0f),
    // ─── Founders & Creators: The Architects ─────────────────────────────────
    BadgeDef("Core Architect",   BadgeCategory.FOUNDERS, "Awarded to the ThunderPass Development Team",          tier = 3, progress = 1f),
    BadgeDef("Kinetic Beta",     BadgeCategory.FOUNDERS, "Joined during the Closed Beta phase",                  tier = 1, progress = 1f),
    BadgeDef("Glitch Hunter",    BadgeCategory.FOUNDERS, "Submitted a verified bug report via the app",          tier = 0, progress = 0f),
    BadgeDef("Node Zero",        BadgeCategory.FOUNDERS, "Among the first 100 users to register a Bio-ID",       tier = 0, progress = 0f),
    BadgeDef("Patch Survivor",   BadgeCategory.FOUNDERS, "Pass someone running a different protocol version",    tier = 0, progress = 0f),
    BadgeDef("Open Circuit",     BadgeCategory.FOUNDERS, "Contributed to the GitHub repository or docs",         tier = 0, progress = 0f),
    BadgeDef("Overclocked",      BadgeCategory.FOUNDERS, "Rare: participated in a 500+ Sparks Stress Test event",tier = 0, progress = 0f),
).sortedWith(
    compareBy(
        { it.category.sortOrder },
        { -it.tier },
        { -it.progress },
    )
)

// ─────────────────────────────────────────────────────────────────────────────
// Screen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun BadgesScreen(vm: HomeViewModel = viewModel()) {
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Text(
                text       = "Badges",
                style      = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text  = "Badge glow follows your tier — earn Bronze, Silver, or Gold.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Tier legend
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            listOf(0 to "Locked", 1 to "Bronze", 2 to "Silver", 3 to "Gold").forEach { (tier, name) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(tierColor(tier)),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text  = name,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // In landscape: 3-column grid with tall aspect ratio cards
        // In portrait: 3-column grid with near-square cards
        LazyVerticalGrid(
            columns               = GridCells.Fixed(3),
            modifier              = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement   = Arrangement.spacedBy(10.dp),
            contentPadding        = PaddingValues(bottom = 24.dp),
        ) {
            items(ALL_BADGES, key = { it.label }) { badge ->
                BadgeItem(badge = badge, landscape = isLandscape)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Single badge tile
// Color = tier level. Category shown only as a tiny dot accent.
// Landscape = taller aspect ratio (portrait orientation cards).
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun BadgeItem(badge: BadgeDef, landscape: Boolean) {
    val locked    = badge.tier == 0
    val tColor    = tierColor(badge.tier)
    val tLabel    = tierLabel(badge.tier)
    val catAccent = badge.category.accentColor
    // Taller in landscape so cards feel like portrait tiles
    val aspectRatio = if (landscape) 0.60f else 0.95f

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio),
        shape  = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(
                alpha = if (locked) 0.45f else 1f
            ),
        ),
    ) {
        Column(
            modifier            = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            // ── Thunder bolt circle — colored by tier ──────────────────────
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            if (locked)
                                listOf(tColor.copy(alpha = 0.10f), tColor.copy(alpha = 0.04f))
                            else
                                listOf(tColor.copy(alpha = 0.35f), tColor.copy(alpha = 0.12f))
                        )
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text     = "⚡",
                    fontSize = 24.sp,
                    color    = tColor.copy(alpha = if (locked) 0.40f else 1f),
                )
            }

            // ── Tier color bar ─────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .width(28.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(50))
                    .background(tColor.copy(alpha = if (locked) 0.35f else 1f)),
            )

            // ── Badge name ─────────────────────────────────────────────────
            Text(
                text       = badge.label,
                style      = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color      = MaterialTheme.colorScheme.onSurface.copy(
                    alpha  = if (locked) 0.45f else 1f
                ),
                textAlign  = TextAlign.Center,
                maxLines   = 2,
                lineHeight = 14.sp,
            )

            // ── Description (always shown — cards are tall enough) ─────────
            Text(
                text      = badge.description,
                style     = MaterialTheme.typography.labelSmall,
                fontSize  = 9.sp,
                color     = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                    alpha = if (locked) 0.40f else 0.80f
                ),
                textAlign = TextAlign.Center,
                maxLines  = 2,
                lineHeight = 12.sp,
            )

            Spacer(Modifier.weight(1f))

            // ── Bottom row: tier label + category dot ──────────────────────
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                // Category accent dot
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(catAccent.copy(alpha = if (locked) 0.40f else 1f)),
                )

                if (!locked) {
                    // Tier label
                    Text(
                        text       = tLabel,
                        style      = MaterialTheme.typography.labelSmall,
                        fontSize   = 8.sp,
                        fontFamily = FontFamily.Monospace,
                        color      = tColor,
                        fontWeight = FontWeight.Bold,
                    )
                } else if (badge.progress > 0f) {
                    // Progress toward unlock
                    LinearProgressIndicator(
                        progress   = { badge.progress },
                        modifier   = Modifier
                            .weight(1f)
                            .height(3.dp)
                            .padding(start = 6.dp)
                            .clip(RoundedCornerShape(50)),
                        color      = tColor.copy(alpha = 0.55f),
                        trackColor = tColor.copy(alpha = 0.12f),
                        strokeCap  = androidx.compose.ui.graphics.StrokeCap.Round,
                    )
                } else {
                    Text(
                        text       = "LOCKED",
                        style      = MaterialTheme.typography.labelSmall,
                        fontSize   = 8.sp,
                        fontFamily = FontFamily.Monospace,
                        color      = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.40f),
                    )
                }
            }

            Spacer(Modifier.height(2.dp))
        }
    }
}

