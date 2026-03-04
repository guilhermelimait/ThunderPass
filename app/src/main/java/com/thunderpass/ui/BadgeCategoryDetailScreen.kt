package com.thunderpass.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
                itemsIndexed(badges) { index, badge ->
                    BadgeListCard(badge = badge, index = index)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Single badge card — horizontal layout: shield on left, info on right
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun BadgeListCard(badge: BadgeDef, index: Int) {
    val locked   = badge.tier == 0
    val rarColor = if (locked) RARITY_NOT_ACHIEVED else rarityColor(index)
    val darkBg   = if (locked) RARITY_LOCKED_BG else rarityDarkBg(index)

    // Card background: dark rarity-tinted
    val cardBg = darkBg

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
                        color = rarColor.copy(alpha = if (locked) 0.35f else 0.85f),
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
                    categoryColor = rarColor,
                    darkBg        = darkBg,
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
                            color = rarColor.copy(alpha = if (locked) 0.5f else 0.9f),
                        )

                        // Progress bar
                        LinearProgressIndicator(
                            progress   = { badge.progress.coerceIn(0f, 1f) },
                            modifier   = Modifier
                                .weight(1f)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color      = rarColor.copy(alpha = if (locked) 0.35f else 0.9f),
                            trackColor = Color.White.copy(alpha = 0.08f),
                        )

                        // Tier dots: 3 always shown — rarity color if achieved, gray if not
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            (1..3).forEach { t ->
                                val achieved = badge.tier >= t
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(
                                            color = if (achieved)
                                                rarColor.copy(alpha = 0.9f)
                                            else
                                                Color(0xFF6B6B6B).copy(alpha = 0.55f),
                                        ),
                                )
                            }
                        }

                        // Rarity label — always shown
                        Text(
                            text  = if (locked) "NOT ACHIEVED" else rarityLabel(index),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize   = 9.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                            color = rarColor.copy(alpha = if (locked) 0.6f else 1f),
                        )
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
    categoryColor: Color,
    darkBg:        Color,
    size:          Dp = 80.dp,
) {
    // Canvas is square — bolt lives INSIDE the shield
    Canvas(modifier = Modifier.size(size)) {
        val w       = this.size.width
        val h       = this.size.height
        val pad     = w * 0.04f
        val shieldTop = pad
        val shieldH   = h - pad
        val stroke    = w * 0.055f

        val catC = if (tier == 0) Color(0xFF5D5D5D).copy(alpha = 0.5f) else categoryColor

        // ── 1. Shield path ────────────────────────────────────────────────────
        val shieldPath = buildShieldPath(w, shieldTop, shieldH)

        // ── 2. Dark interior fill ─────────────────────────────────────────────
        drawPath(path = shieldPath, color = darkBg)

        // ── 3. Radial glow on earned badges ──────────────────────────────────
        if (tier > 0) {
            drawPath(
                path  = shieldPath,
                brush = Brush.radialGradient(
                    colors = listOf(
                        categoryColor.copy(alpha = 0.20f),
                        Color.Transparent,
                    ),
                    center = Offset(w * 0.5f, shieldTop + shieldH * 0.45f),
                    radius = shieldH * 0.55f,
                ),
            )
        }

        // ── 4. Chevrons — all 3 always visible, achieved=category, else gray ────────
        val chevStart   = shieldTop + shieldH * 0.53f
        val chevSpacing = shieldH * 0.15f
        val chevW       = stroke * 0.9f
        val grayC       = Color(0xFF5D5D5D).copy(alpha = 0.4f)
        for (i in 0..2) {
            val cy       = chevStart + i * chevSpacing
            val achieved = tier > i   // tier 1 = first chevron lit, tier 3 = all lit
            val chevPath = Path().apply {
                moveTo(w * 0.22f, cy - shieldH * 0.05f)
                lineTo(w * 0.50f, cy + shieldH * 0.05f)
                lineTo(w * 0.78f, cy - shieldH * 0.05f)
            }
            drawPath(
                path  = chevPath,
                color = if (achieved) catC else grayC,
                style = Stroke(width = chevW, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }

        // ── 5. Shield border ──────────────────────────────────────────────────
        drawPath(
            path  = shieldPath,
            color = catC,
            style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )

        // ── 6. Horizontal bar — always shown (gray if locked, colored if earned) ───────
        val barY = shieldTop + shieldH * 0.36f
        drawPath(
            path  = Path().apply {
                moveTo(w * 0.28f, barY)
                lineTo(w * 0.72f, barY)
            },
            color = if (tier > 0) catC.copy(alpha = 0.7f) else Color(0xFF5D5D5D).copy(alpha = 0.3f),
            style = Stroke(width = stroke * 0.5f, cap = StrokeCap.Round),
        )

        // ── 7. ⚡ Bolt centered INSIDE the shield (upper zone) ────────────────
        val boltColor = if (tier == 0) Color(0xFF9E9E9E).copy(alpha = 0.45f) else categoryColor
        drawBoltInShield(w = w, shieldTop = shieldTop, shieldH = shieldH, color = boltColor)
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

// ── Helper: ⚡ bolt drawn INSIDE the shield, upper-center zone ───────────────
// Occupies roughly the top 35% of the shield interior

private fun DrawScope.drawBoltInShield(
    w: Float,
    shieldTop: Float,
    shieldH: Float,
    color: Color,
) {
    val boltTop    = shieldTop + shieldH * 0.04f
    val boltBottom = shieldTop + shieldH * 0.36f
    val midY       = (boltTop + boltBottom) * 0.5f

    // Asymmetric lightning bolt centered horizontally
    val boltPath = Path().apply {
        moveTo(w * 0.560f, boltTop)      // top — slightly right of center
        lineTo(w * 0.640f, midY)         // right slope to mid
        lineTo(w * 0.560f, midY)         // step inward
        lineTo(w * 0.640f, boltBottom)   // bottom-right tip
        lineTo(w * 0.440f, midY * 0.98f + boltBottom * 0.02f) // sweep left
        lineTo(w * 0.520f, midY)         // step inward (left notch)
        lineTo(w * 0.400f, boltTop)      // top-left
        close()
    }

    drawPath(boltPath, color = color)
    drawPath(
        path  = boltPath,
        color = Color.Black.copy(alpha = 0.25f),
        style = Stroke(width = w * 0.022f, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}
