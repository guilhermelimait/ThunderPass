package com.thunderpass.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

// ─────────────────────────────────────────────────────────────────────────────
// Badge data
// ─────────────────────────────────────────────────────────────────────────────

private data class BadgeDef(
    val label:       String,
    val icon:        String,   // emoji / unicode
    val category:   BadgeCategory,
    val description: String,
    val tier:        Int = 0,   // 0=locked, 1=bronze, 2=silver, 3=gold
    val progress:    Float = 0f, // 0..1
)

private enum class BadgeCategory(val label: String, val color: Color) {
    ENCOUNTERS("Encounters",        Color(0xFFFFD430)),
    RETRO(     "RetroAchievements", Color(0xFFCC3333)),
    CONSOLE(   "Console",          Color(0xFF9C27B0)),
    GEO(       "Geolocation",      Color(0xFF009688)),
    SOCIAL(    "Social",           Color(0xFF00BCD4)),
    AUTO(      "App Awards",       Color(0xFF03A9F4)),
}

private val ALL_BADGES = listOf(
    // Encounters
    BadgeDef("First Spark",    "⚡", BadgeCategory.ENCOUNTERS, "Meet your first Traveler",          tier = 1, progress = 1f),
    BadgeDef("Spark Collector","⚡", BadgeCategory.ENCOUNTERS, "Meet 10 Travelers",                 tier = 0, progress = 0.4f),
    BadgeDef("Thunder Storm",  "⚡", BadgeCategory.ENCOUNTERS, "Meet 50 Travelers",                 tier = 0, progress = 0.1f),
    BadgeDef("Daily Spark",    "⚡", BadgeCategory.ENCOUNTERS, "Keep a 7-day streak",               tier = 0, progress = 0.2f),
    // Retro
    BadgeDef("Retro Fan",      "🎮", BadgeCategory.RETRO, "Link your RA account",                   tier = 0, progress = 0f),
    BadgeDef("Platinum Hunter","🏆", BadgeCategory.RETRO, "Meet a user with 20k+ RA points",        tier = 0, progress = 0f),
    BadgeDef("Retro Circuit",  "🎮", BadgeCategory.RETRO, "Meet an active game master",             tier = 0, progress = 0f),
    // Console
    BadgeDef("Handheld",       "🕹️", BadgeCategory.CONSOLE, "Meet a portable gamer",               tier = 0, progress = 0f),
    BadgeDef("Console Clash",  "🕹️", BadgeCategory.CONSOLE, "Meet someone on a rival platform",    tier = 0, progress = 0f),
    // Geo
    BadgeDef("Explorer",       "🌍", BadgeCategory.GEO, "Spark in 3 different cities",              tier = 0, progress = 0f),
    BadgeDef("Globetrotter",   "🌍", BadgeCategory.GEO, "Spark in 5 different cities",              tier = 0, progress = 0f),
    // Social
    BadgeDef("Shared Taste",   "🤝", BadgeCategory.SOCIAL, "Match a game with a Traveler",          tier = 0, progress = 0f),
    BadgeDef("Legend Met",     "🤝", BadgeCategory.SOCIAL, "Meet the same Traveler 3+ times",       tier = 0, progress = 0f),
    // Auto
    BadgeDef("Early Adopter",  "⚡", BadgeCategory.AUTO, "Joined during the beta era",              tier = 1, progress = 1f),
    BadgeDef("Grid Initiate",  "⚡", BadgeCategory.AUTO, "Complete onboarding",                     tier = 1, progress = 1f),
)

// ─────────────────────────────────────────────────────────────────────────────
// Screen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun BadgesScreen(vm: HomeViewModel = viewModel()) {
    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
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
                text  = "Earn badges by exploring, connecting, and playing.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Category legend
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BadgeCategory.entries.forEach { cat ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier          = Modifier.weight(1f),
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(cat.color),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text     = cat.label,
                        style    = MaterialTheme.typography.labelSmall,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        fontSize = 9.sp,
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        LazyVerticalGrid(
            columns             = GridCells.Fixed(3),
            modifier            = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement   = Arrangement.spacedBy(12.dp),
            contentPadding        = PaddingValues(bottom = 24.dp),
        ) {
            items(ALL_BADGES, key = { it.label }) { badge ->
                BadgeItem(badge)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Single badge tile
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun BadgeItem(badge: BadgeDef) {
    val locked = badge.tier == 0
    val tierLabel = when (badge.tier) {
        1    -> "Bronze"
        2    -> "Silver"
        3    -> "Gold"
        else -> null
    }
    val catColor = badge.category.color
    val baseAlpha = if (locked) 0.35f else 1f

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(
            containerColor = if (locked)
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            else
                MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier            = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Thunder shield icon area
            Box(
                modifier         = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(catColor.copy(alpha = if (locked) 0.15f else 0.20f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text     = if (locked) "⚡" else badge.icon,
                    fontSize = 24.sp,
                    color    = catColor.copy(alpha = baseAlpha),
                )
            }

            // Category color indicator
            Box(
                modifier = Modifier
                    .width(24.dp)
                    .height(3.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(50))
                    .background(catColor.copy(alpha = baseAlpha)),
            )

            Text(
                text      = badge.label,
                style     = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color     = MaterialTheme.colorScheme.onSurface.copy(alpha = baseAlpha),
                textAlign = TextAlign.Center,
                maxLines  = 2,
            )

            if (tierLabel != null) {
                Text(
                    text  = tierLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = catColor,
                    fontFamily = FontFamily.Monospace,
                )
            } else if (badge.progress > 0f) {
                LinearProgressIndicator(
                    progress = { badge.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(50)),
                    color            = catColor,
                    trackColor       = catColor.copy(alpha = 0.2f),
                    strokeCap        = androidx.compose.ui.graphics.StrokeCap.Round,
                )
            }
        }
    }
}
