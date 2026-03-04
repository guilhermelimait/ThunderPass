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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
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
            // ── Rarity legend row ─────────────────────────────────────────────
            Row(
                modifier              = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                listOf(
                    RARITY_NOT_ACHIEVED to "Not\nAchieved",
                    RARITY_COMMON       to "Common",
                    RARITY_UNCOMMON     to "Uncommon",
                    RARITY_RARE         to "Rare",
                    RARITY_LEGENDARY    to "Legendary",
                ).forEach { (color, name) ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(color),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text      = name,
                            style     = MaterialTheme.typography.labelSmall.copy(
                                fontSize   = 11.sp,
                                fontWeight = FontWeight.Bold,
                                lineHeight = 13.sp,
                            ),
                            color     = Color.White,
                            textAlign = TextAlign.Center,
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
// Single badge card — reference style: colored bg, ripple circles, emoji right
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun BadgeListCard(badge: BadgeDef, index: Int) {
    val locked   = badge.tier == 0
    val rarColor  = if (locked) RARITY_NOT_ACHIEVED else rarityColor(index)
    val cardColor = if (locked) RARITY_LOCKED_CARD else rarityCardColor(index)
    val textAlpha = if (locked) 0.55f else 1f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(cardColor, cardColor.copy(alpha = 0.75f)),
                ),
            )
            .height(155.dp),
    ) {
        // ── Ripple circles — center aligned to shield center ──────────────────
        // Shield: 115dp box, offset(-12dp) from right → center = 12+57.5 = 69.5dp from right
        // Canvas 170dp wide → cx fraction = (170-69.5)/170 ≈ 0.591
        Canvas(
            modifier = Modifier
                .fillMaxHeight()
                .width(170.dp)
                .align(Alignment.CenterEnd),
        ) {
            val cx = size.width * 0.591f
            val cy = size.height * 0.5f
            val baseAlpha = if (locked) 0.07f else 0.15f
            for (i in 4 downTo 1) {
                drawCircle(
                    color  = Color.White.copy(alpha = baseAlpha - i * 0.025f + 0.025f),
                    radius = size.height * (0.38f + i * 0.14f),
                    center = Offset(cx, cy),
                )
            }
            // Inner solid circle
            drawCircle(
                color  = Color.White.copy(alpha = if (locked) 0.12f else 0.22f),
                radius = size.height * 0.38f,
                center = Offset(cx, cy),
            )
        }

        // ── Shield badge in right circle (matches profile format) ────────────
        Box(
            modifier = Modifier
                .size(115.dp)
                .align(Alignment.CenterEnd)
                .offset(x = (-12).dp),
            contentAlignment = Alignment.Center,
        ) {
            ThunderShield(
                tier          = badge.tier,
                categoryColor = rarColor,
                darkBg        = categoryDarkBg(badge.category, badge.tier),
                size          = 88.dp,
            )
        }

        // ── Left text block ───────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.62f)
                .padding(start = 20.dp, top = 16.dp, bottom = 14.dp, end = 8.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                // Rarity pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.White.copy(alpha = if (locked) 0.15f else 0.25f))
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                ) {
                    Text(
                        text  = if (locked) "NOT ACHIEVED" else rarityLabel(index),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize   = 8.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = Color.White.copy(alpha = textAlpha),
                    )
                }

                Spacer(Modifier.height(4.dp))

                // Badge name — large bold
                Text(
                    text       = badge.label.uppercase(),
                    style      = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize   = 23.sp,
                        lineHeight = 25.sp,
                    ),
                    color      = Color.White.copy(alpha = textAlpha),
                    maxLines   = 2,
                )
            }

            Column {
                // Description
                Text(
                    text  = badge.description,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = Color.White.copy(alpha = if (locked) 0.4f else 0.80f),
                    maxLines = 2,
                )

                Spacer(Modifier.height(6.dp))

                // Progress bar + X/Y
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LinearProgressIndicator(
                        progress   = { badge.progress.coerceIn(0f, 1f) },
                        modifier   = Modifier
                            .weight(1f)
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color      = Color.White.copy(alpha = if (locked) 0.3f else 0.9f),
                        trackColor = Color.White.copy(alpha = 0.18f),
                    )
                    Text(
                        text  = "${badge.progressCurrent}/${badge.progressMax}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize   = 10.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = Color.White.copy(alpha = textAlpha),
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ThunderBadgeIcon — large ⚡ bolt above 3 tier bars
//   • Thick bezel stroke in card background color (bolt appears raised/beveled)
//   • 3 rounded horizontal bars below the bolt:
//       tier 0 → none lit   tier 1 → top bar   tier 2 → top+mid   tier 3 → all
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ThunderBadgeIcon(
    tier:        Int,
    accentColor: Color,
    cardColor:   Color,
    locked:      Boolean,
    size:        Dp    = 100.dp,
) {
    Canvas(modifier = Modifier.size(size)) {
        val w          = this.size.width
        val h          = this.size.height
        val fillColor  = if (locked) Color(0xFF7A7A7A) else accentColor
        val litColor   = if (locked) Color(0xFF5A5A5A) else accentColor
        val unlitColor = Color(0xFF262626)
        val barsLit    = tier.coerceIn(0, 3)

        // ── 3 rounded bars — bottom ~36% of icon ──────────────────────────────
        val barH      = h * 0.09f
        val barGap    = h * 0.046f
        val barW      = w * 0.80f
        val barLeft   = (w - barW) / 2f
        val bottomPad = h * 0.05f
        val barsBot   = h - bottomPad
        val barsTop   = barsBot - (barH * 3f + barGap * 2f)

        // i=0 → topmost bar (immediately under bolt), i=2 → bottom bar
        for (i in 0..2) {
            val isLit = i < barsLit
            val top   = barsTop + i * (barH + barGap)
            drawRoundRect(
                color        = if (isLit) litColor else unlitColor,
                topLeft      = Offset(barLeft, top),
                size         = Size(barW, barH),
                cornerRadius = CornerRadius(barH / 2f),
            )
        }

        // ── Lightning bolt — upper ~56% of icon ───────────────────────────────
        val boltTop    = h * 0.02f
        val boltBottom = barsTop - h * 0.06f
        val span       = boltBottom - boltTop
        val midY       = boltTop + span * 0.52f

        val boltPath = Path().apply {
            moveTo(w * 0.555f, boltTop)
            lineTo(w * 0.660f, midY)
            lineTo(w * 0.562f, midY)
            lineTo(w * 0.665f, boltBottom)
            lineTo(w * 0.418f, midY * 0.96f + boltBottom * 0.04f)
            lineTo(w * 0.519f, midY)
            lineTo(w * 0.395f, boltTop)
            close()
        }

        // Thick bezel in card color — bolt appears to have a large edge blending into card bg
        drawPath(
            path  = boltPath,
            color = cardColor,
            style = Stroke(width = w * 0.14f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        // Bolt fill
        drawPath(boltPath, color = fillColor)
        // Subtle dark outline for crispness
        drawPath(
            path  = boltPath,
            color = Color.Black.copy(alpha = 0.18f),
            style = Stroke(width = w * 0.028f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
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
        val stroke    = w * 0.030f

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

    // Clean filled bolt — no thick outline/bezel
    drawPath(boltPath, color = color)
}
