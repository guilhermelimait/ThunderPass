package com.thunderpass.ui

import androidx.compose.ui.graphics.Color

// ─────────────────────────────────────────────────────────────────────────────
// Tier helpers
// ─────────────────────────────────────────────────────────────────────────────

// ── 7-tier rarity system ──────────────────────────────────────────────────────
// Tier 0 → Locked (Gray)
// Tier 1 → Common I   (Green)
// Tier 2 → Common II  (Green, brighter)
// Tier 3 → Uncommon I (Blue)
// Tier 4 → Uncommon II(Blue, brighter)
// Tier 5 → Rare       (Purple)
// Tier 6 → Legendary  (Orange)
// Tier 7 → Exotic     (Yellow/Gold)
val TIER_LOCKED      = Color(0xFF616161)
val TIER_COMMON_1    = Color(0xFF388E3C)
val TIER_COMMON_2    = Color(0xFF66BB6A)
val TIER_UNCOMMON_1  = Color(0xFF1565C0)
val TIER_UNCOMMON_2  = Color(0xFF42A5F5)
val TIER_RARE        = Color(0xFF7B1FA2)
val TIER_LEGENDARY   = Color(0xFFE65100)
val TIER_EXOTIC      = Color(0xFFFFD600)

fun tierColor(tier: Int) = when (tier) {
    1    -> TIER_COMMON_1
    2    -> TIER_COMMON_2
    3    -> TIER_UNCOMMON_1
    4    -> TIER_UNCOMMON_2
    5    -> TIER_RARE
    6    -> TIER_LEGENDARY
    7    -> TIER_EXOTIC
    else -> TIER_LOCKED
}

fun tierLabel(tier: Int) = when (tier) {
    1    -> "COMMON"
    2    -> "COMMON II"
    3    -> "UNCOMMON"
    4    -> "UNCOMMON II"
    5    -> "RARE"
    6    -> "LEGENDARY"
    7    -> "EXOTIC"
    else -> "LOCKED"
}

/** Number of chevrons to draw on the shield per tier. */
fun tierChevrons(tier: Int) = when (tier) {
    1, 2 -> 1
    3, 4 -> 2
    5    -> 3
    6    -> 4
    7    -> 5
    else -> 0
}

// ─────────────────────────────────────────────────────────────────────────────
// Category
// ─────────────────────────────────────────────────────────────────────────────

enum class BadgeCategory(
    val label:      String,
    val accentColor: Color,
    val gradientEnd: Color,
    val sortOrder:  Int,
    val emoji:      String,
) {
    ENCOUNTERS(
        "Encounters",
        Color(0xFFFFB300),
        Color(0xFFFF6F00),
        sortOrder = 0,
        emoji     = "⚡",
    ),
    CONSOLE(
        "Consoles",
        Color(0xFF7B1FA2),
        Color(0xFFAD1457),
        sortOrder = 1,
        emoji     = "🕹️",
    ),
    GEO(
        "Geolocation",
        Color(0xFF00796B),
        Color(0xFF00ACC1),
        sortOrder = 2,
        emoji     = "🌍",
    ),
    SOCIAL(
        "Social",
        Color(0xFF0288D1),
        Color(0xFF0097A7),
        sortOrder = 3,
        emoji     = "👥",
    ),
    GAMES(
        "Games",
        Color(0xFFE64A19),
        Color(0xFFF57F17),
        sortOrder = 4,
        emoji     = "🎮",
    ),
    FOUNDERS(
        "Founders",
        Color(0xFF37474F),
        Color(0xFF78909C),
        sortOrder = 5,
        emoji     = "🏆",
    ),
}

// ─────────────────────────────────────────────────────────────────────────────
// Badge definition
// ─────────────────────────────────────────────────────────────────────────────

data class BadgeDef(
    val label:       String,
    val category:    BadgeCategory,
    val description: String,
    val tier:        Int   = 0,
    val progress:    Float = 0f,
)

// ─────────────────────────────────────────────────────────────────────────────
// All 42 badges
// ─────────────────────────────────────────────────────────────────────────────

val ALL_BADGES: List<BadgeDef> = listOf(
    // ── Encounters ────────────────────────────────────────────────────────────
    BadgeDef("First Contact",    BadgeCategory.ENCOUNTERS, "Pass your very first user",                           tier = 1, progress = 1f),
    BadgeDef("Double Tap",       BadgeCategory.ENCOUNTERS, "Pass the same user on two different days",            tier = 0, progress = 0.3f),
    BadgeDef("High-Frequency",   BadgeCategory.ENCOUNTERS, "Pass 10 people in a single 24-hour window",          tier = 0, progress = 0f),
    BadgeDef("Century Club",     BadgeCategory.ENCOUNTERS, "Reach 100 total unique encounters",                   tier = 0, progress = 0.05f),
    BadgeDef("Ghost Town",       BadgeCategory.ENCOUNTERS, "App open 4 hours without meeting anyone",            tier = 0, progress = 0f),
    BadgeDef("Surge Limit",      BadgeCategory.ENCOUNTERS, "Encounter 50 unique users in a single week",         tier = 0, progress = 0f),
    BadgeDef("Eternal Pulse",    BadgeCategory.ENCOUNTERS, "Pass a user from ThunderPass Launch Week (v1.0)",     tier = 0, progress = 0f),
    // ── Consoles ──────────────────────────────────────────────────────────────
    BadgeDef("80's Kid",         BadgeCategory.CONSOLE, "Match with an NES, Master System or Atari player",      tier = 0, progress = 0f),
    BadgeDef("16-Bit Power",     BadgeCategory.CONSOLE, "Match with an SNES or Genesis player",                  tier = 0, progress = 0f),
    BadgeDef("Handheld Hero",    BadgeCategory.CONSOLE, "Match with a Game Boy (DMG/Color/Advance) player",      tier = 0, progress = 0f),
    BadgeDef("The Disc Age",     BadgeCategory.CONSOLE, "Match with a PS1, Saturn or Dreamcast player",          tier = 0, progress = 0f),
    BadgeDef("Arcade Ally",      BadgeCategory.CONSOLE, "Match with a MAME or FinalBurn Neo player",             tier = 0, progress = 0f),
    BadgeDef("The Underdog",     BadgeCategory.CONSOLE, "Match with a WonderSwan, Neo Geo Pocket or Lynx player",tier = 0, progress = 0f),
    BadgeDef("64-Bit Sync",      BadgeCategory.CONSOLE, "Match with a Nintendo 64 or Jaguar player",             tier = 0, progress = 0f),
    // ── GeoLocation ───────────────────────────────────────────────────────────
    BadgeDef("Local Circuit",    BadgeCategory.GEO, "Pass 5 people in your current city",                        tier = 0, progress = 0.2f),
    BadgeDef("Long Distance",    BadgeCategory.GEO, "Pass someone whose Home City is 500+ miles away",           tier = 0, progress = 0f),
    BadgeDef("Border Crosser",   BadgeCategory.GEO, "Pass a user from a different country",                      tier = 0, progress = 0f),
    BadgeDef("Travel Log",       BadgeCategory.GEO, "Spark in three different cities in one week",               tier = 0, progress = 0f),
    BadgeDef("Grid Navigator",   BadgeCategory.GEO, "Spark at a major airport or train station",                 tier = 0, progress = 0f),
    BadgeDef("Urban Legend",     BadgeCategory.GEO, "Encounter someone in a city with 5M+ population",           tier = 0, progress = 0f),
    BadgeDef("Off-Grid",         BadgeCategory.GEO, "Record an encounter above 2,000 metres elevation",          tier = 0, progress = 0f),
    // ── Social ────────────────────────────────────────────────────────────────
    BadgeDef("Shared Quest",     BadgeCategory.SOCIAL, "Both you and the peer are playing the same game",         tier = 0, progress = 0f),
    BadgeDef("Hardcore Sync",    BadgeCategory.SOCIAL, "Both users have Hardcore Mode enabled on RA",             tier = 0, progress = 0f),
    BadgeDef("Friendly Fire",    BadgeCategory.SOCIAL, "Send a High Five to a user you've passed 5+ times",       tier = 0, progress = 0f),
    BadgeDef("The Rival",        BadgeCategory.SOCIAL, "Pass someone with a higher Global Rank than you",         tier = 0, progress = 0f),
    BadgeDef("Influence Surge",  BadgeCategory.SOCIAL, "10 different people viewed your profile today",           tier = 0, progress = 0f),
    BadgeDef("Badge Collector",  BadgeCategory.SOCIAL, "Pass someone who has more ThunderPass badges than you",   tier = 0, progress = 0f),
    BadgeDef("Profile Ghost",    BadgeCategory.SOCIAL, "Pass someone with their Bio set to private",              tier = 0, progress = 0f),
    // ── Games ─────────────────────────────────────────────────────────────────
    BadgeDef("Master of Monsters",  BadgeCategory.GAMES, "Pass someone who Mastered a Pokémon game on RA",       tier = 0, progress = 0f),
    BadgeDef("Speedrunner's Spark", BadgeCategory.GAMES, "Pass someone with a Fastest Completion record on RA",  tier = 0, progress = 0f),
    BadgeDef("RPG Grinder",         BadgeCategory.GAMES, "Pass a user playing a game with 40+ hours playtime",   tier = 0, progress = 0f),
    BadgeDef("Survival Horror",     BadgeCategory.GAMES, "Match with someone playing a horror game after 10 PM",  tier = 0, progress = 0f),
    BadgeDef("The Completionist",   BadgeCategory.GAMES, "Match a user with 50+ Mastery (100%) badges on RA",    tier = 0, progress = 0f),
    BadgeDef("Genre Specialist",    BadgeCategory.GAMES, "Pass someone who played 10+ games in the same genre",  tier = 0, progress = 0f),
    BadgeDef("Hidden Gem",          BadgeCategory.GAMES, "Match a user playing a game with <100 RA players",     tier = 0, progress = 0f),
    // ── Founders ──────────────────────────────────────────────────────────────
    BadgeDef("Core Architect",   BadgeCategory.FOUNDERS, "Awarded to the ThunderPass Development Team",          tier = 3, progress = 1f),
    BadgeDef("Kinetic Beta",     BadgeCategory.FOUNDERS, "Joined during the Closed Beta phase",                  tier = 1, progress = 1f),
    BadgeDef("Glitch Hunter",    BadgeCategory.FOUNDERS, "Submitted a verified bug report via the app",          tier = 0, progress = 0f),
    BadgeDef("Node Zero",        BadgeCategory.FOUNDERS, "Among the first 100 users to register a Bio-ID",       tier = 0, progress = 0f),
    BadgeDef("Patch Survivor",   BadgeCategory.FOUNDERS, "Pass someone running a different protocol version",    tier = 0, progress = 0f),
    BadgeDef("Open Circuit",     BadgeCategory.FOUNDERS, "Contributed to the GitHub repository or docs",         tier = 0, progress = 0f),
    BadgeDef("Overclocked",      BadgeCategory.FOUNDERS, "Rare: participated in a 500+ Sparks Stress Test event",tier = 0, progress = 0f),
)

fun badgesForCategory(category: BadgeCategory) =
    ALL_BADGES.filter { it.category == category }
        .sortedWith(compareBy({ -it.tier }, { -it.progress }))
