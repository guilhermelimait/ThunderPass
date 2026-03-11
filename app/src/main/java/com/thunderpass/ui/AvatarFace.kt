package com.thunderpass.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage

// ─────────────────────────────────────────────────────────────────────────────
// DiceBear "big-smile" avatar loader
// ─────────────────────────────────────────────────────────────────────────────

/** Background colours offered to DiceBear — picked deterministically by seed. */
private const val BG_COLORS =
    "b6e3f4,c0aede,d1d4f9,ffd5dc,ffdfbf,b1f0c8,ffeaa7,fab1a0"

// ── Skin-tone palette (hex values accepted by DiceBear big-smile skinColor param) ──
// Kept in sync with the body/arm colour drawn in WalkingSceneAnimation so the
// walking figure's skin always matches the DiceBear head.
val SKIN_TONE_HEXES = listOf(
    "FDDEB5", // light peach
    "ECB882", // warm tan
    "D48B5A", // medium
    "A0684A", // brown
    "FFC8A0", // peachy
)
val SPARKY_SKIN_LABELS = listOf("Fair", "Warm Tan", "Medium", "Brown", "Peachy")

// ─────────────────────────────────────────────────────────────────────────────
// Sparky attribute lists — explicit DiceBear big-smile options
// ─────────────────────────────────────────────────────────────────────────────

// v9.x DiceBear big-smile hair styles (API param: ?hair=<name>)
val SPARKY_HAIR_STYLES = listOf(
    "bangs", "bowlCutHair", "braids", "bunHair", "curlyBob",
    "curlyShortHair", "froBun", "halfShavedHead", "mohawk", "shavedHead",
    "shortHair", "straightHair", "wavyBob",
)
val SPARKY_HAIR_LABELS = listOf(
    "Bangs", "Bowl Cut", "Braids", "Bun", "Curly Bob",
    "Curly Short", "Fro Bun", "Half Shaved", "Mohawk", "Shaved",
    "Short Hair", "Straight", "Wavy Bob",
)

val SPARKY_HAIR_COLORS_HEX = listOf(
    "0e0e0e", "3d2314", "a0522d", "c4a35a", "f4d03f",
    "e74c3c", "2980b9", "8e44ad", "ff69b4", "7f8c8d",
    "27ae60", "f5f5f5",
)
val SPARKY_HAIR_COLOR_LABELS = listOf(
    "Black", "Dark Brown", "Brown", "Light Brown", "Blonde",
    "Red", "Blue", "Purple", "Pink", "Silver", "Green", "White",
)

// v9.x DiceBear big-smile eye styles (API param: ?eyes=<name>)
val SPARKY_EYE_STYLES = listOf(
    "angry", "cheery", "confused", "normal",
    "sad", "sleepy", "starstruck", "winking",
)
val SPARKY_EYE_LABELS = listOf(
    "Angry", "Cheery", "Confused", "Normal",
    "Sad", "Sleepy", "Starstruck", "Winking",
)

// v9.x DiceBear big-smile mouth styles (API param: ?mouth=<name>)
val SPARKY_MOUTH_STYLES = listOf(
    "awkwardSmile", "braces", "gapSmile", "kawaii",
    "openedSmile", "openSad", "teethSmile", "unimpressed",
)
val SPARKY_MOUTH_LABELS = listOf(
    "Awkward Smile", "Braces", "Gap Smile", "Kawaii",
    "Opened Smile", "Open Sad", "Teeth Smile", "Unimpressed",
)

val SPARKY_BG_COLORS = listOf(
    "b6e3f4", "c0aede", "d1d4f9", "ffd5dc",
    "ffdfbf", "b1f0c8", "ffeaa7", "fab1a0",
)
val SPARKY_BG_LABELS = listOf(
    "Sky Blue", "Lavender", "Periwinkle", "Pink",
    "Peach", "Mint", "Yellow", "Salmon",
)

// v9.x DiceBear big-smile accessories (API param: accessories=<name>&accessoriesProbability=100)
// Index 0 = "none" = no accessory (accessoriesProbability=0)
// Valid names sourced from https://api.dicebear.com/9.x/big-smile/schema.json
val SPARKY_ACCESSORY_STYLES = listOf(
    "none", "catEars", "clownNose", "faceMask", "glasses",
    "mustache", "sailormoonCrown", "sleepMask", "sunglasses",
)
val SPARKY_ACCESSORY_LABELS = listOf(
    "None", "Cat Ears", "Clown Nose", "Face Mask", "Glasses",
    "Mustache", "Sailor Moon Crown", "Sleep Mask", "Sunglasses",
)

// ─────────────────────────────────────────────────────────────────────────────
// Sparky seed encoding / decoding
// Seed format: "sparky|h=bangs|hc=0e0e0e|e=cheery|m=awkwardSmile|s=FDDEB5|b=b6e3f4|ac=none"
// ─────────────────────────────────────────────────────────────────────────────

data class SparkyOptions(
    val hair:      Int = 0,
    val hairColor: Int = 0,
    val eyes:      Int = 1,  // cheery
    val mouth:     Int = 0,  // awkwardSmile
    val skin:      Int = 0,
    val bg:        Int = 0,
    val accessory: Int = 0,  // 0 = none
)

/**
 * Parses a Sparky seed into [SparkyOptions] indices.
 * Supports both legacy numeric format ("sparky|h=3|e=2|...") and
 * name-based format ("sparky|h=bangs|hc=0e0e0e|e=cheery|...").
 */
fun parseSparkyOptions(seed: String): SparkyOptions {
    if (!seed.startsWith("sparky|")) return SparkyOptions()
    val params = seed.split("|").drop(1).mapNotNull { part ->
        val idx = part.indexOf('=')
        if (idx < 0) null else part.substring(0, idx) to part.substring(idx + 1)
    }.toMap()

    fun idx(list: List<String>, key: String, default: Int): Int {
        val v = params[key] ?: return default
        val numeric = v.toIntOrNull()
        if (numeric != null) return numeric.coerceIn(0, list.lastIndex)
        return list.indexOfFirst { it.equals(v, ignoreCase = true) }.takeIf { it >= 0 } ?: default
    }

    return SparkyOptions(
        hair      = idx(SPARKY_HAIR_STYLES,      "h",  0),
        hairColor = idx(SPARKY_HAIR_COLORS_HEX,  "hc", 0),
        eyes      = idx(SPARKY_EYE_STYLES,       "e",  1),
        mouth     = idx(SPARKY_MOUTH_STYLES,     "m",  0),
        skin      = idx(SKIN_TONE_HEXES,         "s",  0),
        bg        = idx(SPARKY_BG_COLORS,        "b",  0),
        accessory = idx(SPARKY_ACCESSORY_STYLES, "ac", 0),
    )
}

/**
 * Generates a fully random [SparkyOptions] and encodes it as a sparky seed string.
 * Used by the dice button and first-install initialization so the avatar is always
 * representable by sliders — no UUID-based DiceBear randomness that can't be decoded.
 */
fun randomSparkySeed(): String = buildSparkySeed(
    SparkyOptions(
        hair      = SPARKY_HAIR_STYLES.indices.random(),
        hairColor = SPARKY_HAIR_COLORS_HEX.indices.random(),
        eyes      = SPARKY_EYE_STYLES.indices.random(),
        mouth     = SPARKY_MOUTH_STYLES.indices.random(),
        skin      = SKIN_TONE_HEXES.indices.random(),
        bg        = SPARKY_BG_COLORS.indices.random(),
        accessory = SPARKY_ACCESSORY_STYLES.indices.random(),
    )
)

/**
 * Derives deterministic [SparkyOptions] from any arbitrary seed string (e.g. a UUID).
 * Each attribute dimension is picked by hashing the seed with a unique multiplier,
 * so the result is stable across re-opens and looks "random" without being the
 * default (all-zero) fallback.
 */
fun sparkyOptionsFromSeed(seed: String): SparkyOptions {
    val h = seed.fold(0L) { acc, c -> acc * 31L + c.code }
    fun pick(size: Int, mix: Long): Int =
        ((((h * mix) ushr 16) % size).toInt().let { if (it < 0) it + size else it })
    return SparkyOptions(
        hair      = pick(SPARKY_HAIR_STYLES.size,      1L),
        hairColor = pick(SPARKY_HAIR_COLORS_HEX.size,  3L),
        eyes      = pick(SPARKY_EYE_STYLES.size,       7L),
        mouth     = pick(SPARKY_MOUTH_STYLES.size,     13L),
        skin      = pick(SKIN_TONE_HEXES.size,         23L),
        bg        = pick(SPARKY_BG_COLORS.size,        41L),
        accessory = pick(SPARKY_ACCESSORY_STYLES.size, 53L),
    )
}

/** Encodes a [SparkyOptions] as a human-readable seed string using style names. */
fun buildSparkySeed(o: SparkyOptions): String {
    val h  = SPARKY_HAIR_STYLES.getOrElse(o.hair)           { SPARKY_HAIR_STYLES[0] }
    val hc = SPARKY_HAIR_COLORS_HEX.getOrElse(o.hairColor)  { SPARKY_HAIR_COLORS_HEX[0] }
    val e  = SPARKY_EYE_STYLES.getOrElse(o.eyes)            { SPARKY_EYE_STYLES[1] }
    val m  = SPARKY_MOUTH_STYLES.getOrElse(o.mouth)         { SPARKY_MOUTH_STYLES[0] }
    val s  = SKIN_TONE_HEXES.getOrElse(o.skin)              { SKIN_TONE_HEXES[0] }
    val b  = SPARKY_BG_COLORS.getOrElse(o.bg)               { SPARKY_BG_COLORS[0] }
    val ac = SPARKY_ACCESSORY_STYLES.getOrElse(o.accessory) { SPARKY_ACCESSORY_STYLES[0] }
    return "sparky|h=$h|hc=$hc|e=$e|m=$m|s=$s|b=$b|ac=$ac"
}

// ─────────────────────────────────────────────────────────────────────────────
// Skin tone helpers
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Returns the skin-tone hex string (no #) deterministically chosen for [seed].
 * Used both in the DiceBear URL (skinColor=) and to paint the walking figure's body.
 */
fun skinToneHexForSeed(seed: String): String {
    if (seed.startsWith("sparky|")) {
        val opts = parseSparkyOptions(seed)
        return SKIN_TONE_HEXES.getOrElse(opts.skin) { SKIN_TONE_HEXES[0] }
    }
    val h = seed.fold(0) { acc, c -> acc * 31 + c.code }
    return SKIN_TONE_HEXES[((h % SKIN_TONE_HEXES.size) + SKIN_TONE_HEXES.size) % SKIN_TONE_HEXES.size]
}

/**
 * Returns the skin-tone as a [Color] for [seed].
 * Identical mapping as [skinToneHexForSeed], exposed for Canvas drawing.
 */
fun skinToneForSeed(seed: String): Color {
    val hex = skinToneHexForSeed(seed)
    return Color(
        red   = hex.substring(0, 2).toInt(16) / 255f,
        green = hex.substring(2, 4).toInt(16) / 255f,
        blue  = hex.substring(4, 6).toInt(16) / 255f,
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// DiceBear URL builder
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Builds the DiceBear big-smile URL for [seed].
 * If [seed] starts with "sparky|", explicit attributes are decoded and passed
 * as URL params, giving full user control over the avatar appearance.
 * Otherwise the seed drives random generation as before.
 */
fun diceBearUrl(seed: String, transparent: Boolean = false): String {
    val safeSeed = seed.ifBlank { "thunderpass-default" }

    if (safeSeed.startsWith("sparky|")) {
        val opts      = parseSparkyOptions(safeSeed)
        val hair      = SPARKY_HAIR_STYLES.getOrElse(opts.hair)           { SPARKY_HAIR_STYLES[0] }
        val hairColor = SPARKY_HAIR_COLORS_HEX.getOrElse(opts.hairColor)  { SPARKY_HAIR_COLORS_HEX[0] }
        val eyes      = SPARKY_EYE_STYLES.getOrElse(opts.eyes)            { SPARKY_EYE_STYLES[1] }
        val mouth     = SPARKY_MOUTH_STYLES.getOrElse(opts.mouth)         { SPARKY_MOUTH_STYLES[0] }
        val skin      = SKIN_TONE_HEXES.getOrElse(opts.skin)              { SKIN_TONE_HEXES[0] }
        val bg        = SPARKY_BG_COLORS.getOrElse(opts.bg)               { SPARKY_BG_COLORS[0] }
        val ac        = SPARKY_ACCESSORY_STYLES.getOrElse(opts.accessory)  { "none" }
        // If the user chose an accessory, force 100% probability; otherwise suppress to 0%.
        val accessoryParam = if (ac == "none") "&accessoriesProbability=0"
                             else "&accessories=$ac&accessoriesProbability=100"
        return "https://api.dicebear.com/9.x/big-smile/svg" +
            "?seed=sparky-fixed" +
            "&radius=50&size=128" +
            accessoryParam +
            "&hair=$hair" +
            "&hairColor=${hairColor.lowercase()}" +
            "&eyes=$eyes" +
            "&mouth=$mouth" +
            "&skinColor=${skin.lowercase()}" +
            if (transparent) "" else "&backgroundColor=$bg"
    }

    return "https://api.dicebear.com/9.x/big-smile/svg" +
        "?seed=${Uri.encode(safeSeed)}" +
        "&radius=50" +
        "&size=128" +
        "&skinColor=${skinToneHexForSeed(safeSeed)}" +
        if (transparent) "" else "&backgroundColor=$BG_COLORS"
}

/**
 * Loads a DiceBear "big-smile" avatar for [seed] from the API (single composited request).
 * The API handles all compositing server-side — using crossfade so the image transitions
 * smoothly when the seed changes (e.g. slider moves in the editor).
 */
@Composable
fun DiceBearAvatar(
    seed:                  String,
    size:                  Dp       = 72.dp,
    modifier:              Modifier = Modifier,
    showLoadingBackground: Boolean  = true,
    transparent:           Boolean  = false,
) {
    SubcomposeAsyncImage(
        model              = diceBearUrl(seed, transparent),
        contentDescription = "Avatar",
        contentScale       = ContentScale.Fit,
        modifier           = modifier
            .size(size)
            .clip(CircleShape),
        loading = {
            if (showLoadingBackground) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
            }
        },
        error = {
            if (showLoadingBackground) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
            }
        },
    )
}

