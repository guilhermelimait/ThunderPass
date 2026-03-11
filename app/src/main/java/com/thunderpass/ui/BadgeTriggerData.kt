package com.thunderpass.ui

/**
 * BadgeTriggerData — badge unlock conditions keyed by RA Console/Genre IDs.
 *
 * This file is intentionally separate from the UI so BleService, HomeViewModel,
 * or any future encounter-processing layer can import it without pulling in
 * Compose dependencies.
 *
 * Usage example (in encounter processing):
 *   val badgeLabel = CONSOLE_ID_TO_BADGE[peerConsoleId]
 *   if (badgeLabel != null) triggerBadge(badgeLabel)
 */

// ─────────────────────────────────────────────────────────────────────────────
// Console badges — keyed by RetroAchievements consoleId
// Source: https://api.retroachievements.org/API/API_GetConsoleIDs.php
// ─────────────────────────────────────────────────────────────────────────────

/** Maps every known RA Console ID to the ThunderPass badge it unlocks. */
val CONSOLE_ID_TO_BADGE: Map<Int, String> = mapOf(
    // ── 80's Kid ──────────────────────────────────────────────────────────────
    1  to "80's Kid",    // NES / Famicom
    15 to "80's Kid",    // Master System
    // Note: Atari 2600=25, Atari 7800=51, Atari Lynx=13, Atari Jaguar=17
    25 to "80's Kid",    // Atari 2600
    51 to "80's Kid",    // Atari 7800

    // ── 16-Bit Power ──────────────────────────────────────────────────────────
    3  to "16-Bit Power", // SNES / Super Famicom
    2  to "16-Bit Power", // Genesis / Mega Drive
    15 to "16-Bit Power", // Master System (also 80's Kid — first match wins in logic)

    // ── Handheld Hero ─────────────────────────────────────────────────────────
    4  to "Handheld Hero", // Game Boy (DMG)
    6  to "Handheld Hero", // Game Boy Advance
    5  to "Handheld Hero", // Game Boy Color

    // ── The Disc Age ──────────────────────────────────────────────────────────
    7  to "The Disc Age", // PlayStation (PS1)
    39 to "The Disc Age", // Sega Saturn
    23 to "The Disc Age", // Dreamcast

    // ── 64-Bit Sync ───────────────────────────────────────────────────────────
    9  to "64-Bit Sync", // Nintendo 64
    17 to "64-Bit Sync", // Atari Jaguar (optional stretch — 17 is Jaguar on RA)

    // ── Arcade Ally ───────────────────────────────────────────────────────────
    30 to "Arcade Ally", // Arcade (MAME)
    74 to "Arcade Ally", // FinalBurn Neo / Arcade (alternate RA ID)

    // ── The Underdog ──────────────────────────────────────────────────────────
    53 to "The Underdog", // WonderSwan / WonderSwan Color
    14 to "The Underdog", // Neo Geo Pocket (Color)
    13 to "The Underdog", // Atari Lynx
)

/**
 * Multi-badge console IDs (one peer console ID qualifies for more than one badge).
 * If you want to award ALL matching badges instead of first-match, use this set.
 * Currently: Master System (ID 15) qualifies for both "80's Kid" and "16-Bit Power".
 */
val CONSOLE_ID_MULTI_BADGE: Map<Int, List<String>> = mapOf(
    15 to listOf("80's Kid", "16-Bit Power"),
)

// ─────────────────────────────────────────────────────────────────────────────
// Games badges — keyed by RA genre strings / game-level conditions
// Source: RetroAchievements API "Genre" field on game metadata
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Maps RA genre strings (lowercase) to the Games badge they unlock.
 * Used by: "Genre Specialist" (10+ games in same genre).
 */
val GENRE_TO_BADGE: Map<String, String> = mapOf(
    // ── Master of Monsters ────────────────────────────────────────────────────
    // Triggered by game title keyword or specific game IDs — see POKEMON_GAME_IDS below.
    // Genre alone is not enough; peer must have "Mastered" a Pokémon game.

    // ── RPG Grinder ───────────────────────────────────────────────────────────
    "role-playing game"  to "RPG Grinder",
    "rpg"                to "RPG Grinder",
    "action rpg"         to "RPG Grinder",
    "jrpg"               to "RPG Grinder",

    // ── Genre Specialist ──────────────────────────────────────────────────────
    // Any genre reaching 10+ games by peer qualifies
    // "action"          -> generic, check count in VM
    // "shmup" / "shoot 'em up" are the typical RA values:
    "shooter"            to "Genre Specialist",
    "shoot 'em up"       to "Genre Specialist",
    "shmup"              to "Genre Specialist",
    "platformer"         to "Genre Specialist",
    "fighting"           to "Genre Specialist",
    "strategy"           to "Genre Specialist",
    "simulation"         to "Genre Specialist",
    "puzzle"             to "Genre Specialist",
    "sports"             to "Genre Specialist",
)

/**
 * Known Pokémon game IDs on RetroAchievements.
 * Peer must have a "Mastery" on any of these for "Master of Monsters".
 */
val POKEMON_GAME_IDS: Set<Int> = setOf(
    515,   // Pokémon Red Version (GB)
    516,   // Pokémon Blue Version (GB)
    4423,  // Pokémon Yellow (GB)
    517,   // Pokémon Gold (GBC)
    518,   // Pokémon Silver (GBC)
    519,   // Pokémon Crystal (GBC)
    1278,  // Pokémon Ruby (GBA)
    1279,  // Pokémon Sapphire (GBA)
    1281,  // Pokémon FireRed (GBA)
    1282,  // Pokémon LeafGreen (GBA)
    1280,  // Pokémon Emerald (GBA)
    // Add more as RA expands coverage
)

/**
 * Horror / Survival-Horror genre strings for the "Survival Horror" badge.
 * Badge condition: peer playing a horror-genre game AND current time is after 22:00.
 */
val HORROR_GENRES: Set<String> = setOf(
    "survival horror",
    "horror",
    "psychological horror",
)

// ─────────────────────────────────────────────────────────────────────────────
// Encounter-count thresholds (Encounters category)
// ─────────────────────────────────────────────────────────────────────────────

object EncounterThresholds {
    const val CENTURY_CLUB     = 100   // total unique encounters
    const val HIGH_FREQUENCY   = 10    // encounters in 24 h window
    const val SURGE_LIMIT      = 50    // unique encounters in 7-day window
    const val DOUBLE_TAP_DAYS  = 2     // same peer on 2+ different calendar days
    const val GHOST_TOWN_HOURS = 4     // hours open without any encounter
}

// ─────────────────────────────────────────────────────────────────────────────
// RA point thresholds (Social / Platinum Pulse)
// ─────────────────────────────────────────────────────────────────────────────

object RaThresholds {
    const val PLATINUM_PULSE_POINTS  = 20_000  // peer TotalPoints for "The Rival" context
    const val COMPLETIONIST_MASTERIES = 50     // peer Mastery count for "The Completionist"
    const val HIDDEN_GEM_MAX_PLAYERS  = 100    // max players on RA for "Hidden Gem"
    const val RPG_GRINDER_HOURS       = 40     // estimated playtime hours for "RPG Grinder"
    const val GENRE_SPECIALIST_COUNT  = 10     // games in same genre for "Genre Specialist"
}
