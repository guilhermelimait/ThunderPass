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
private val SKIN_TONE_HEXES = listOf(
    "FDDEB5", // light peach
    "ECB882", // warm tan
    "D48B5A", // medium
    "A0684A", // brown
    "FFC8A0", // peachy
)

/**
 * Returns the skin-tone hex string (no #) deterministically chosen for [seed].
 * Used both in the DiceBear URL (skinColor=) and to paint the walking figure's body.
 */
fun skinToneHexForSeed(seed: String): String {
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

/**
 * Builds the DiceBear big-smile URL for [seed].
 * radius=50   → perfectly circular avatar inside the SVG itself.
 * skinColor   → locked to the same tone used to paint the walking body.
 * transparent → omits backgroundColor so the SVG has no fill behind the face.
 */
fun diceBearUrl(seed: String, transparent: Boolean = false): String {
    val safeSeed = seed.ifBlank { "thunderpass-default" }
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
