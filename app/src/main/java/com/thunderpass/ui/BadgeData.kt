package com.thunderpass.ui

import androidx.compose.ui.graphics.Color

// ─────────────────────────────────────────────────────────────────────────────
// Legacy tier helpers (kept for any remaining references)
// ─────────────────────────────────────────────────────────────────────────────

val TIER_LOCKED = Color(0xFF9E9E9E)
val TIER_BRONZE = Color(0xFFCD7F32)
val TIER_SILVER = Color(0xFFC0C0C0)
val TIER_GOLD   = Color(0xFFFFD700)

fun tierColor(tier: Int) = when (tier) {
    1    -> TIER_BRONZE
    2    -> TIER_SILVER
    3    -> TIER_GOLD
    else -> TIER_LOCKED
}

fun tierLabel(tier: Int) = when (tier) {
    1    -> "BRONZE"
    2    -> "SILVER"
    3    -> "GOLD"
    else -> "LOCKED"
}

// ─────────────────────────────────────────────────────────────────────────────
// Rarity system — badge color based on its position in the category list
//   index 0-1  → Common     → Blue
//   index 2-3  → Uncommon   → Purple
//   index 4-5  → Rare       → Orange
//   index 6+   → Legendary  → Gold
//   not achieved (tier==0)  → Dark Grey
// ─────────────────────────────────────────────────────────────────────────────

val RARITY_NOT_ACHIEVED = Color(0xFF4A4A4A)
val RARITY_COMMON       = Color(0xFF2196F3)  // Blue
val RARITY_UNCOMMON     = Color(0xFF9C27B0)  // Purple
val RARITY_RARE         = Color(0xFFFF9800)  // Orange
val RARITY_LEGENDARY    = Color(0xFFFFD700)  // Gold

fun rarityColor(index: Int): Color = when {
    index <= 1 -> RARITY_COMMON
    index <= 3 -> RARITY_UNCOMMON
    index <= 5 -> RARITY_RARE
    else       -> RARITY_LEGENDARY
}

fun rarityLabel(index: Int): String = when {
    index <= 1 -> "COMMON"
    index <= 3 -> "UNCOMMON"
    index <= 5 -> "RARE"
    else       -> "LEGENDARY"
}

// Card background colors — medium-light, vibrant (like reference image)
fun rarityCardColor(index: Int): Color = when {
    index <= 1 -> Color(0xFF42A5F5)  // light blue
    index <= 3 -> Color(0xFFAB47BC)  // medium purple
    index <= 5 -> Color(0xFFFF9800)  // orange
    else       -> Color(0xFFFFCA28)  // golden
}
val RARITY_LOCKED_CARD = Color(0xFF757575)  // medium grey

// Dark bg tinted to each rarity (kept for shield interior)
fun rarityDarkBg(index: Int): Color = when {
    index <= 1 -> Color(0xFF001428)  // dark blue
    index <= 3 -> Color(0xFF16001E)  // dark purple
    index <= 5 -> Color(0xFF1E0A00)  // dark orange
    else       -> Color(0xFF1E1600)  // dark gold
}

val RARITY_LOCKED_BG = Color(0xFF111111)

// Dark interior fill for each category × tier combination
// Gets richer (less dark) as tier increases, always category-tinted
fun categoryDarkBg(category: BadgeCategory, tier: Int): Color = when (category) {
    BadgeCategory.ENCOUNTERS -> when (tier) {
        0    -> Color(0xFF1A1200)
        1    -> Color(0xFF2D1E00)
        2    -> Color(0xFF3D2800)
        3    -> Color(0xFF4D3300)
        else -> Color(0xFF1A1200)
    }
    BadgeCategory.CONSOLE -> when (tier) {
        0    -> Color(0xFF12001A)
        1    -> Color(0xFF2D0044)
        2    -> Color(0xFF3D0060)
        3    -> Color(0xFF4D0080)
        else -> Color(0xFF12001A)
    }
    BadgeCategory.GEO -> when (tier) {
        0    -> Color(0xFF001A18)
        1    -> Color(0xFF002E2A)
        2    -> Color(0xFF003E38)
        3    -> Color(0xFF004E48)
        else -> Color(0xFF001A18)
    }
    BadgeCategory.SOCIAL -> when (tier) {
        0    -> Color(0xFF001220)
        1    -> Color(0xFF001D33)
        2    -> Color(0xFF002644)
        3    -> Color(0xFF003055)
        else -> Color(0xFF001220)
    }
    BadgeCategory.GAMES -> when (tier) {
        0    -> Color(0xFF1A0800)
        1    -> Color(0xFF2D1000)
        2    -> Color(0xFF3D1600)
        3    -> Color(0xFF4D1C00)
        else -> Color(0xFF1A0800)
    }
    BadgeCategory.FOUNDERS -> when (tier) {
        0    -> Color(0xFF0D1215)
        1    -> Color(0xFF1A2228)
        2    -> Color(0xFF263038)
        3    -> Color(0xFF2E3C44)
        else -> Color(0xFF0D1215)
    }
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
// progressCurrent / progressMax shown as "X / Y" on the card
// tier: 0 = locked, 1 = bronze, 2 = silver, 3 = gold
// ─────────────────────────────────────────────────────────────────────────────

data class BadgeDef(
    val label:           String,
    val category:        BadgeCategory,
    val description:     String,
    val tier:            Int    = 0,
    val progress:        Float  = 0f,   // 0..1 fraction for bar
    val progressCurrent: Int    = 0,
    val progressMax:     Int    = 1,
    val key:             String = "",   // stable identifier for dynamic badge grants
    val tierWhenEarned:  Int    = 1,    // tier unlocked to when badge is dynamically awarded
)

// ─────────────────────────────────────────────────────────────────────────────
// All 42 badges
// ─────────────────────────────────────────────────────────────────────────────

val ALL_BADGES: List<BadgeDef> = listOf(
    // ── Encounters ────────────────────────────────────────────────────────────
    BadgeDef("First Contact",    BadgeCategory.ENCOUNTERS, "Pass your very first ThunderPasser",                   tier = 1, progress = 1f,    progressCurrent = 1,  progressMax = 1),
    BadgeDef("Double Tap",       BadgeCategory.ENCOUNTERS, "Pass the same user on two different days",             tier = 0, progress = 0.5f,  progressCurrent = 1,  progressMax = 2),
    BadgeDef("High-Frequency",   BadgeCategory.ENCOUNTERS, "Pass 10 people in a single 24-hour window",           tier = 0, progress = 0f,    progressCurrent = 0,  progressMax = 10),
    BadgeDef("Century Club",     BadgeCategory.ENCOUNTERS, "Reach 100 total unique encounters",                    tier = 0, progress = 0.05f, progressCurrent = 5,  progressMax = 100),
    BadgeDef("Ghost Town",       BadgeCategory.ENCOUNTERS, "App open 4 hours without meeting anyone",             tier = 0, progress = 0f,    progressCurrent = 0,  progressMax = 1),
    BadgeDef("Surge Limit",      BadgeCategory.ENCOUNTERS, "Encounter 50 unique users in a single week",          tier = 0, progress = 0f,    progressCurrent = 0,  progressMax = 50),
    BadgeDef("Eternal Pulse",    BadgeCategory.ENCOUNTERS, "Pass a user from ThunderPass Launch Week (v1.0)",      tier = 0, progress = 0f,    progressCurrent = 0,  progressMax = 1),
    // ── Consoles ──────────────────────────────────────────────────────────────
    BadgeDef("80's Kid",         BadgeCategory.CONSOLE, "Match with an NES, Master System or Atari player",        tier = 0, progress = 0f,   progressCurrent = 0, progressMax = 1),
    BadgeDef("16-Bit Power",     BadgeCategory.CONSOLE, "Match with an SNES or Genesis player",                    tier = 0, progress = 0f,   progressCurrent = 0, progressMax = 1),
    BadgeDef("Handheld Hero",    BadgeCategory.CONSOLE, "Match with a Game Boy (DMG/Color/Advance) player",        tier = 0, progress = 0f,   progressCurrent = 0, progressMax = 1),
    BadgeDef("The Disc Age",     BadgeCategory.CONSOLE, "Match with a PS1, Saturn or Dreamcast player",            tier = 0, progress = 0f,   progressCurrent = 0, progressMax = 1),
    BadgeDef("Arcade Ally",      BadgeCategory.CONSOLE, "Match with a MAME or FinalBurn Neo player",               tier = 0, progress = 0f,   progressCurrent = 0, progressMax = 1),
    BadgeDef("The Underdog",     BadgeCategory.CONSOLE, "Match with a WonderSwan, Neo Geo Pocket or Lynx player",  tier = 0, progress = 0f,   progressCurrent = 0, progressMax = 1),
    BadgeDef("64-Bit Sync",      BadgeCategory.CONSOLE, "Match with a Nintendo 64 or Jaguar player",               tier = 0, progress = 0f,   progressCurrent = 0, progressMax = 1),
    // ── GeoLocation ───────────────────────────────────────────────────────────
    BadgeDef("Local Circuit",    BadgeCategory.GEO, "Pass 5 people in your current city",                          tier = 0, progress = 0.2f, progressCurrent = 1, progressMax = 5),
    BadgeDef("Long Distance",    BadgeCategory.GEO, "Pass someone whose Home City is 500+ miles away",             tier = 0, progress = 0f,   progressCurrent = 0, progressMax = 1),
    BadgeDef("Border Crosser",   BadgeCategory.GEO, "Pass a user from a different country",                        tier = 0, progress = 0f,   progressCurrent = 0, progressMax = 1),
    BadgeDef("Travel Log",       BadgeCategory.GEO, "Spark in three different cities in one week",                 tier = 0, progress = 0f,   progressCurrent = 0, progressMax = 3),
    BadgeDef("Grid Navigator",   BadgeCategory.GEO, "Spark at a major airport or train station",                   tier = 0, progress = 0f,   progressCurrent = 0, progressMax = 1),
    BadgeDef("Urban Legend",     BadgeCategory.GEO, "Encounter someone in a city with 5M+ population",             tier = 0, progress = 0f,   progressCurrent = 0, progressMax = 1),
    BadgeDef("Off-Grid",         BadgeCategory.GEO, "Record an encounter above 2,000 metres elevation",            tier = 0, progress = 0f,   progressCurrent = 0, progressMax = 1),
    // ── Social ────────────────────────────────────────────────────────────────
    BadgeDef("Shared Quest",  BadgeCategory.SOCIAL,   "Linked your RetroAchievements account and downloaded your game data.", key = "shared_quest"),
    BadgeDef("Hardcore Sync",    BadgeCategory.SOCIAL, "Both users have Hardcore Mode enabled on RA",               tier = 0, progress = 0f,   progressCurrent = 0, progressMax = 1),
    BadgeDef("Friendly Fire",    BadgeCategory.SOCIAL, "Send a High Five to a user you've passed 5+ times",         tier = 0, progress = 0f,   progressCurrent = 0, progressMax = 5),
    BadgeDef("The Rival",        BadgeCategory.SOCIAL, "Pass someone with a higher Global Rank than you",           tier = 0, progress = 0f,   progressCurrent = 0, progressMax = 1),
    BadgeDef("Influence Surge",  BadgeCategory.SOCIAL, "10 different people viewed your profile today",             tier = 0, progress = 0f,   progressCurrent = 0, progressMax = 10),
    BadgeDef("Badge Collector",  BadgeCategory.SOCIAL, "Pass someone who has more ThunderPass badges than you",     tier = 0, progress = 0f,   progressCurrent = 0, progressMax = 1),
    BadgeDef("Profile Ghost",    BadgeCategory.SOCIAL, "Pass someone with their Bio set to private",                tier = 0, progress = 0f,   progressCurrent = 0, progressMax = 1),
    // ── Games ─────────────────────────────────────────────────────────────────
    BadgeDef("Master of Monsters",  BadgeCategory.GAMES, "Pass someone who Mastered a Pokémon game on RA",         tier = 0, progress = 0f,   progressCurrent = 0, progressMax = 1),
    BadgeDef("Speedrunner's Spark", BadgeCategory.GAMES, "Pass someone with a Fastest Completion record on RA",    tier = 0, progress = 0f,   progressCurrent = 0, progressMax = 1),
    BadgeDef("RPG Grinder",         BadgeCategory.GAMES, "Pass a user playing a game with 40+ hours playtime",    tier = 0, progress = 0f,   progressCurrent = 0, progressMax = 1),
    BadgeDef("Survival Horror",     BadgeCategory.GAMES, "Match with someone playing a horror game after 10 PM",   tier = 0, progress = 0f,   progressCurrent = 0, progressMax = 1),
    BadgeDef("The Completionist",   BadgeCategory.GAMES, "Match a user with 50+ Mastery (100%) badges on RA",     tier = 0, progress = 0f,   progressCurrent = 0, progressMax = 50),
    BadgeDef("Genre Specialist",    BadgeCategory.GAMES, "Pass someone who played 10+ games in the same genre",   tier = 0, progress = 0f,   progressCurrent = 0, progressMax = 10),
    BadgeDef("Hidden Gem",          BadgeCategory.GAMES, "Match a user playing a game with <100 RA players",      tier = 0, progress = 0f,   progressCurrent = 0, progressMax = 1),
    // ── Fitness (step-Volts) ───────────────────────────────────────────────────
    BadgeDef("First Step",   BadgeCategory.FOUNDERS, "Earned your first Volt from walking.",                                         key = "first_step"),
    BadgeDef("Daily Walker", BadgeCategory.FOUNDERS, "Hit the full 100-Volt step quota in a single day.",                            key = "daily_walker"),
    BadgeDef("Marathon",     BadgeCategory.FOUNDERS, "Accumulated 10,000 Volts from walking across all time.",                       key = "marathon"),
    // ── Founders ──────────────────────────────────────────────────────────────
    BadgeDef("Alfa Tester",  BadgeCategory.FOUNDERS, "Installed ThunderPass before version 0.7 was officially launched.",            key = "alfa_tester"),
    BadgeDef("Beta Tester",  BadgeCategory.FOUNDERS, "Installed ThunderPass during the Beta phase (before v0.8 launched).",          key = "beta_tester"),
    BadgeDef("Glitch Hunter",    BadgeCategory.FOUNDERS, "Submitted a verified bug report via the app",            tier = 0, progress = 0f,   progressCurrent = 0, progressMax = 1),
    BadgeDef("Node Zero",    BadgeCategory.FOUNDERS, "Among the first 100 users to create and confirm their account on ThunderPass servers.", key = "node_zero"),
    BadgeDef("Patch Survivor",   BadgeCategory.FOUNDERS, "Pass someone running a different protocol version",      tier = 0, progress = 0f,   progressCurrent = 0, progressMax = 1),
    BadgeDef("Open Circuit",     BadgeCategory.FOUNDERS, "Contributed to the GitHub repository or docs",           tier = 0, progress = 0f,   progressCurrent = 0, progressMax = 1),
    BadgeDef("Overclocked",      BadgeCategory.FOUNDERS, "Rare: participated in a 500+ Sparks Stress Test event",  tier = 0, progress = 0f,   progressCurrent = 0, progressMax = 1),
)

/**
 * Returns ALL_BADGES with dynamic tier applied for any key found in [earnedKeys].
 * Badges without a key are returned unchanged.
 */
fun computeBadges(earnedKeys: Set<String>): List<BadgeDef> =
    ALL_BADGES.map { badge ->
        if (badge.key.isNotBlank() && badge.key in earnedKeys && badge.tier == 0)
            badge.copy(tier = badge.tierWhenEarned, progress = 1f, progressCurrent = badge.progressMax)
        else badge
    }

fun badgesForCategory(category: BadgeCategory, earnedKeys: Set<String> = emptySet()) =
    computeBadges(earnedKeys)
        .filter { it.category == category }
        .sortedWith(compareBy({ -it.tier }, { -it.progress }))
