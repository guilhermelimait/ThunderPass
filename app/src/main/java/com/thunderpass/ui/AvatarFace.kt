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

val SPARKY_HAIR_STYLES = listOf(
    "bunLarge", "bunSmall", "curly", "extraLong", "high",
    "long", "lowReceding", "plain", "shortComb", "shortCombed",
    "shortFromBottom", "shortSober", "wavy", "twists",
)
val SPARKY_HAIR_LABELS = listOf(
    "Bun Large", "Bun Small", "Curly", "Extra Long", "High",
    "Long", "Low Receding", "Plain", "Short Comb", "Short Combed",
    "Short Fringe", "Short Sober", "Wavy", "Twists",
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

val SPARKY_EYE_STYLES = listOf(
    "cheery", "normal", "happy", "sleepy", "starstruck",
    "winking", "winkingAlt", "sad", "plain",
)
val SPARKY_EYE_LABELS = listOf(
    "Cheery", "Normal", "Happy", "Sleepy", "Starstruck",
    "Winking", "Winking Alt", "Sad", "Plain",
)

val SPARKY_MOUTH_STYLES = listOf(
    "bigSmile", "braces", "cheekPop", "grin", "laughing",
    "lilSmile", "plain", "smirk", "teethSmile", "unimpressed",
)
val SPARKY_MOUTH_LABELS = listOf(
    "Big Smile", "Braces", "Cheek Pop", "Grin", "Laughing",
    "Lil Smile", "Plain", "Smirk", "Teeth Smile", "Unimpressed",
)

val SPARKY_BG_COLORS = listOf(
    "b6e3f4", "c0aede", "d1d4f9", "ffd5dc",
    "ffdfbf", "b1f0c8", "ffeaa7", "fab1a0",
)
val SPARKY_BG_LABELS = listOf(
    "Sky Blue", "Lavender", "Periwinkle", "Pink",
    "Peach", "Mint", "Yellow", "Salmon",
)

// ─────────────────────────────────────────────────────────────────────────────
// Sparky seed encoding / decoding
// Seed format: "sparky|h=0|hc=3|e=2|m=0|s=0|b=0"
// ─────────────────────────────────────────────────────────────────────────────

data class SparkyOptions(
    val hair:      Int = 0,
    val hairColor: Int = 0,
    val eyes:      Int = 2,  // happy
    val mouth:     Int = 0,  // bigSmile
    val skin:      Int = 0,
    val bg:        Int = 0,
)

fun parseSparkyOptions(seed: String): SparkyOptions {
    if (!seed.startsWith("sparky|")) return SparkyOptions()
    val params = seed.split("|").drop(1).mapNotNull { part ->
        val idx = part.indexOf('=')
        if (idx < 0) null else part.substring(0, idx) to (part.substring(idx + 1).toIntOrNull() ?: 0)
    }.toMap()
    return SparkyOptions(
        hair      = params["h"]  ?: 0,
        hairColor = params["hc"] ?: 0,
        eyes      = params["e"]  ?: 2,
        mouth     = params["m"]  ?: 0,
        skin      = params["s"]  ?: 0,
        bg        = params["b"]  ?: 0,
    )
}

fun buildSparkySeed(o: SparkyOptions): String =
    "sparky|h=${o.hair}|hc=${o.hairColor}|e=${o.eyes}|m=${o.mouth}|s=${o.skin}|b=${o.bg}"

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
        val hair      = SPARKY_HAIR_STYLES.getOrElse(opts.hair)      { SPARKY_HAIR_STYLES[0] }
        val hairColor = SPARKY_HAIR_COLORS_HEX.getOrElse(opts.hairColor) { SPARKY_HAIR_COLORS_HEX[0] }
        val eyes      = SPARKY_EYE_STYLES.getOrElse(opts.eyes)       { SPARKY_EYE_STYLES[2] }
        val mouth     = SPARKY_MOUTH_STYLES.getOrElse(opts.mouth)    { SPARKY_MOUTH_STYLES[0] }
        val skin      = SKIN_TONE_HEXES.getOrElse(opts.skin)         { SKIN_TONE_HEXES[0] }
        val bg        = SPARKY_BG_COLORS.getOrElse(opts.bg)          { SPARKY_BG_COLORS[0] }
        return "https://api.dicebear.com/9.x/big-smile/svg" +
            "?seed=sparky-fixed" +
            "&radius=50" +
            "&size=128" +
            "&hair[]=$hair" +
            "&hairColor[]=$hairColor" +
            "&eyes[]=$eyes" +
            "&mouth[]=$mouth" +
            "&skinColor=$skin" +
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
 * Loads a unique, deterministic DiceBear "big-smile" avatar for [seed].
 * Shows a muted circle placeholder while loading / on error.
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

