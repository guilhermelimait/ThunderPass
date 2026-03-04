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

/**
 * Builds the DiceBear big-smile URL for [seed].
 * radius=50  → perfectly circular avatar inside the SVG itself.
 */
fun diceBearUrl(seed: String): String =
    "https://api.dicebear.com/9.x/big-smile/svg" +
    "?seed=${Uri.encode(seed)}" +
    "&radius=50" +
    "&size=128" +
    "&backgroundColor=$BG_COLORS"

/**
 * Loads a unique, deterministic DiceBear "big-smile" avatar for [seed].
 * Shows a muted circle placeholder while loading / on error.
 */
@Composable
fun DiceBearAvatar(
    seed:                String,
    size:                Dp       = 72.dp,
    modifier:            Modifier = Modifier,
    showLoadingBackground: Boolean = true,
) {
    SubcomposeAsyncImage(
        model              = diceBearUrl(seed),
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
    )
}
