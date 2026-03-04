package com.thunderpass.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ─────────────────────────────────────────────────────────────────────────────
// Badge Category Detail Screen — full single-column list, all tiers visible
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BadgeCategoryDetailScreen(
    categoryName: String,
    onBack: () -> Unit,
) {
    val category = BadgeCategory.entries.find { it.name == categoryName } ?: return
    val badges   = badgesForCategory(category)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text       = "${category.emoji}  ${category.label}",
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // ── Tier legend row ───────────────────────────────────────────────
            Row(
                modifier              = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                listOf(0 to "Locked", 1 to "Bronze", 2 to "Silver", 3 to "Gold").forEach { (t, name) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(tierColor(t)),
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text  = name,
                            style = MaterialTheme.typography.labelMedium,
                            color = tierColor(t).copy(alpha = if (t == 0) 0.6f else 0.9f),
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

            // ── All badge cards ───────────────────────────────────────────────
            LazyColumn(
                contentPadding    = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier          = Modifier.fillMaxSize(),
            ) {
                items(badges) { badge ->
                    BadgeListCard(badge = badge, category = category)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Single badge card — horizontal layout: shield on left, info on right
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun BadgeListCard(badge: BadgeDef, category: BadgeCategory) {
    val locked  = badge.tier == 0
    val tColor  = tierColor(badge.tier)
    val darkBg  = categoryDarkBg(category, badge.tier)

    // Card background: dark category-tinted, slightly lighter for earned badges
    val cardBg = if (locked) {
        darkBg.copy(alpha = 0.85f)
    } else {
        darkBg
    }

    Surface(
        shape  = RoundedCornerShape(16.dp),
        color  = Color.Transparent,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            cardBg,
                            cardBg.copy(alpha = 0.7f),
                        ),
                    ),
                    shape = RoundedCornerShape(16.dp),
                )
                .clip(RoundedCornerShape(16.dp)),
        ) {
            // Subtle accent stripe on left edge
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(
                        color = if (locked) Color(0xFF5D5D5D).copy(alpha = 0.4f) else tColor.copy(alpha = 0.8f),
                    )
                    .align(Alignment.CenterStart),
            )

            Row(
                modifier          = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // ── Shield ────────────────────────────────────────────────────
                ThunderShield(
                    tier          = badge.tier,
                    tierColor     = tColor,
                    darkBg        = darkBg,
                    categoryColor = category.accentColor,
                    size          = 80.dp,
                )

                Spacer(Modifier.width(14.dp))

                // ── Text info ─────────────────────────────────────────────────
                Column(modifier = Modifier.weight(1f)) {

                    // Badge name
                    Text(
                        text       = badge.label,
                        style      = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color      = if (locked)
                            Color.White.copy(alpha = 0.45f)
                        else
                            Color.White,
                        maxLines   = 1,
                    )

                    Spacer(Modifier.height(3.dp))

                    // Description
                    Text(
                        text  = badge.description,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                        color = if (locked)
                            Color.White.copy(alpha = 0.28f)
                        else
                            Color.White.copy(alpha = 0.72f),
                        maxLines   = 2,
                    )

                    Spacer(Modifier.height(8.dp))

                    // Progress row: X/Y bar + tier dots
                    Row(
                        verticalAlignment      = Alignment.CenterVertically,
                        horizontalArrangement  = Arrangement.spacedBy(10.dp),
                    ) {
                        // Progress X/Y label
                        Text(
                            text  = "${badge.progressCurrent} / ${badge.progressMax}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize   = 10.sp,
                            ),
                            color = if (locked)
                                Color.White.copy(alpha = 0.3f)
                            else
                                tColor.copy(alpha = 0.9f),
                        )

                        // Progress bar
                        LinearProgressIndicator(
                            progress   = { badge.progress.coerceIn(0f, 1f) },
                            modifier   = Modifier
                                .weight(1f)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color      = if (locked)
                                Color(0xFF5D5D5D).copy(alpha = 0.5f)
                            else
                                tColor.copy(alpha = 0.9f),
                            trackColor = Color.White.copy(alpha = 0.08f),
                        )

                        // Tier dots: 3 dots — filled = achieved, hollow = not yet
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            (1..3).forEach { t ->
                                val achieved = badge.tier >= t
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(
                                            color = if (achieved)
                                                tierColor(t).copy(alpha = 0.9f)
                                            else
                                                Color.White.copy(alpha = 0.12f),
                                        ),
                                )
                            }
                        }

                        // Tier label for earned badges
                        if (!locked) {
                            Text(
                                text  = tierLabel(badge.tier),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize   = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                ),
                                color = tColor,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ThunderShield — military shield with:
//   • ⚡ bolt protruding ABOVE the shield (never touching chevrons)
//   • Dark category-tinted interior
//   • Tier-colored chevrons in the lower 55% of the shield
//   • Bold border in tier color (gray when locked)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ThunderShield(
    tier:          Int,
    tierColor:     Color,
    darkBg:        Color,
    categoryColor: Color,
    size:          Dp = 80.dp,
) {
    val density    = LocalDensity.current
    // Canvas is taller than shield: 28% extra at top for the bolt
    val boltFrac   = 0.28f
    val canvasW    = size
    val canvasH    = size * (1f + boltFrac)

    Canvas(modifier = Modifier.size(width = canvasW, height = canvasH)) {
        val w       = this.size.width
        val h       = this.size.height
        val shieldTop = h * boltFrac          // shield body starts here
        val shieldH   = h * (1f - boltFrac)  // shield body height
        val stroke    = w * 0.055f

        val tC = if (tier == 0) Color(0xFF5D5D5D).copy(alpha = 0.5f) else tierColor

        // ── 1. Shield path (body starts at shieldTop) ─────────────────────────
        val shieldPath = buildShieldPath(w, shieldTop, shieldH)

        // ── 2. Dark interior fill ─────────────────────────────────────────────
        drawPath(
            path  = shieldPath,
            color = darkBg,
        )

        // ── 3. Very subtle inner glow on non-locked badges ───────────────────
        if (tier > 0) {
            drawPath(
                path  = shieldPath,
                brush = Brush.radialGradient(
                    colors = listOf(
                        tierColor.copy(alpha = 0.18f),
                        Color.Transparent,
                    ),
                    center = androidx.compose.ui.geometry.Offset(w * 0.5f, shieldTop + shieldH * 0.45f),
                    radius = shieldH * 0.55f,
                ),
            )
        }

        // ── 4. Chevrons — strictly in lower 55% of shield ────────────────────
        if (tier > 0) {
            val chevStart  = shieldTop + shieldH * 0.44f
            val chevSpacing = shieldH * 0.155f
            val chevW      = stroke * 0.9f

            repeat(tier) { i ->
                val cy = chevStart + i * chevSpacing
                val chevPath = Path().apply {
                    moveTo(w * 0.20f, cy - shieldH * 0.055f)
                    lineTo(w * 0.50f, cy + shieldH * 0.055f)
                    lineTo(w * 0.80f, cy - shieldH * 0.055f)
                }
                drawPath(
                    path  = chevPath,
                    color = tierColor,
                    style = Stroke(
                        width = chevW,
                        cap   = StrokeCap.Round,
                        join  = StrokeJoin.Round,
                    ),
                )
            }
        }

        // ── 5. Shield border ──────────────────────────────────────────────────
        drawPath(
            path  = shieldPath,
            color = tC,
            style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )

        // ── 6. Top bar (Bronze+) inside shield, below bolt entry ──────────────
        if (tier > 0) {
            val barY = shieldTop + shieldH * 0.175f
            val barPath = Path().apply {
                moveTo(w * 0.28f, barY)
                lineTo(w * 0.72f, barY)
            }
            drawPath(
                path  = barPath,
                color = tierColor,
                style = Stroke(width = stroke * 0.6f, cap = StrokeCap.Round),
            )
        }

        // ── 7. Lightning bolt ABOVE the shield ────────────────────────────────
        // Bolt tip at y=0, base at shieldTop (sits on shield rim)
        val boltColor = if (tier == 0) Color(0xFF9E9E9E).copy(alpha = 0.5f) else tierColor
        drawBoltAboveShield(w = w, boltBottom = shieldTop, color = boltColor)
    }
}

// ── Helper: shield path with flat top entry for bolt ──────────────────────────

private fun buildShieldPath(w: Float, topY: Float, shieldH: Float): Path = Path().apply {
    // Start at top-center (where bolt meets shield)
    moveTo(w * 0.50f, topY)
    // Top-right
    lineTo(w * 0.91f, topY + shieldH * 0.13f)
    // Right side down
    lineTo(w * 0.91f, topY + shieldH * 0.50f)
    // Right curve to bottom point
    cubicTo(
        w * 0.91f, topY + shieldH * 0.78f,
        w * 0.50f, topY + shieldH * 0.97f,
        w * 0.50f, topY + shieldH * 0.97f,
    )
    // Left curve back up
    cubicTo(
        w * 0.50f, topY + shieldH * 0.97f,
        w * 0.09f, topY + shieldH * 0.78f,
        w * 0.09f, topY + shieldH * 0.50f,
    )
    // Top-left
    lineTo(w * 0.09f, topY + shieldH * 0.13f)
    close()
}

// ── Helper: ⚡ lightning bolt rising ABOVE the shield ─────────────────────────
// boltBottom = y coordinate where bolt base meets the shield top-center

private fun DrawScope.drawBoltAboveShield(w: Float, boltBottom: Float, color: Color) {
    if (boltBottom <= 0f) return

    val midH = boltBottom * 0.48f

    // Classic asymmetric lightning bolt shape, centered on w*0.50
    val boltPath = Path().apply {
        moveTo(w * 0.555f, 0f)           // tip — slightly right of center
        lineTo(w * 0.630f, midH)         // right slope down to mid
        lineTo(w * 0.560f, midH)         // step inward (creates notch)
        lineTo(w * 0.650f, boltBottom)   // bottom-right of bolt (rests on shield)
        lineTo(w * 0.445f, midH * 1.05f) // sweep back left across center
        lineTo(w * 0.520f, midH * 1.05f) // step inward (left notch)
        lineTo(w * 0.400f, 0f)           // tip — back to upper left
        close()
    }

    // Fill
    drawPath(boltPath, color = color)

    // Thin crisp outline to define edges against dark bg
    drawPath(
        path  = boltPath,
        color = color.copy(alpha = (color.alpha * 0.35f).coerceIn(0f, 1f)),
        style = Stroke(width = w * 0.025f, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}
