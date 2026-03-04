package com.thunderpass.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.min

// ─────────────────────────────────────────────────────────────────────────────
// Badge Category Detail Screen
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BadgeCategoryDetailScreen(
    categoryName: String,
    onBack: () -> Unit,
) {
    val category = BadgeCategory.entries.find { it.name == categoryName }
        ?: return
    val badges = badgesForCategory(category)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text       = "${category.emoji}  ${category.label}",
                            fontWeight = FontWeight.Bold,
                        )
                    }
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
            // ── Tier legend — two rows so all 8 entries fit neatly ───────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val legendItems = listOf(
                    0 to "Locked",
                    1 to "Common",
                    2 to "Common II",
                    3 to "Uncommon",
                    4 to "Uncommon II",
                    5 to "Rare",
                    6 to "Legendary",
                    7 to "Exotic",
                )
                // Split into two rows of 4
                legendItems.chunked(4).forEach { rowItems ->
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        rowItems.forEach { (t, name) ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier          = Modifier.weight(1f),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(tierColor(t)),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text     = name,
                                    style    = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            // ── Badge grid ────────────────────────────────────────────────────
            LazyVerticalGrid(
                columns             = GridCells.Fixed(3),
                contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier            = Modifier.fillMaxSize(),
            ) {
                items(badges) { badge ->
                    BadgeShieldCard(badge = badge, categoryColor = category.accentColor)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Individual badge card with military shield
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun BadgeShieldCard(badge: BadgeDef, categoryColor: Color) {
    val locked = badge.tier == 0
    val tColor = tierColor(badge.tier)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier            = Modifier.fillMaxWidth(),
    ) {
        // Military shield badge
        MilitaryShield(
            tier          = badge.tier,
            tierColor     = tColor,
            categoryColor = categoryColor,
            progress      = badge.progress,
            size          = 72.dp,
        )

        Spacer(Modifier.height(6.dp))

        Text(
            text       = badge.label,
            style      = MaterialTheme.typography.labelSmall,
            fontWeight = if (!locked) FontWeight.SemiBold else FontWeight.Normal,
            color      = if (!locked)
                MaterialTheme.colorScheme.onBackground
            else
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            textAlign  = TextAlign.Center,
            maxLines   = 2,
            overflow   = TextOverflow.Ellipsis,
        )

        if (badge.tier > 0) {
            Spacer(Modifier.height(2.dp))
            Text(
                text  = tierLabel(badge.tier),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize   = 8.sp,
                    fontFamily = FontFamily.Monospace,
                ),
                color = tColor,
            )
        } else if (badge.progress > 0f) {
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress       = { badge.progress },
                modifier       = Modifier
                    .fillMaxWidth(0.85f)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color          = categoryColor.copy(alpha = 0.7f),
                trackColor     = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Military Shield drawn with Canvas  — rank grows with tier
//   Tier 0 (Locked)       — gray outline, no fill, no stripes
//   Tier 1 (Common)       — green fill + 1 chevron
//   Tier 2 (Common II)    — bright green fill + 1 chevron
//   Tier 3 (Uncommon)     — blue fill + 2 chevrons
//   Tier 4 (Uncommon II)  — bright blue fill + 2 chevrons
//   Tier 5 (Rare)         — purple fill + 3 chevrons
//   Tier 6 (Legendary)    — orange fill + 4 chevrons
//   Tier 7 (Exotic)       — gold fill + 5 chevrons + ⚡ thunder mark at top
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun MilitaryShield(
    tier:          Int,
    tierColor:     Color,
    categoryColor: Color,
    progress:      Float = 0f,
    size:          Dp    = 72.dp,
) {
    val density = LocalDensity.current
    val sizePx  = with(density) { size.toPx() }

    Canvas(modifier = Modifier.size(size)) {
        val w = sizePx
        val h = sizePx
        val stroke = w * 0.065f

        // ── Build shield path ─────────────────────────────────────────────────
        val shieldPath = shieldPath(w, h)

        // ── Background fill ───────────────────────────────────────────────────
        if (tier > 0) {
            // Solid color fill with slight gradient effect
            drawPath(
                path  = shieldPath,
                brush = Brush.verticalGradient(
                    colors = listOf(tierColor.copy(alpha = 0.35f), tierColor.copy(alpha = 0.12f)),
                    startY = 0f,
                    endY   = h,
                ),
            )
        } else if (progress > 0f) {
            // Partial-progress fill clipped to shield shape
            drawPath(
                path  = shieldPath,
                brush = Brush.verticalGradient(
                    colors = listOf(categoryColor.copy(alpha = 0.2f), categoryColor.copy(alpha = 0.05f)),
                    startY = h * (1f - progress),
                    endY   = h,
                ),
            )
        }

        // ── Chevron stripes (count driven by tier rarity group) ──────────────
        val chevronCount = tierChevrons(tier)
        if (chevronCount > 0) {
            // Raise start-Y so 5 chevrons still fit inside the shield
            val chevronY0      = h * 0.36f
            val chevronSpacing = h * 0.11f

            repeat(chevronCount) { i ->
                val cy = chevronY0 + i * chevronSpacing
                val chevronPath = Path().apply {
                    moveTo(w * 0.22f, cy - h * 0.06f)
                    lineTo(w * 0.50f, cy + h * 0.06f)
                    lineTo(w * 0.78f, cy - h * 0.06f)
                }
                drawPath(
                    path  = chevronPath,
                    color = tierColor,
                    style = Stroke(
                        width = stroke * 0.85f,
                        cap   = StrokeCap.Round,
                        join  = StrokeJoin.Round,
                    ),
                )
            }
        }

        // ── Thunder bolt (⚡) for Exotic (tier 7) only ────────────────────────
        if (tier == 7) {
            drawLightningBolt(w = w, h = h, color = tierColor)
        }

        // ── Shield border ─────────────────────────────────────────────────────
        val borderColor = if (tier > 0) tierColor else Color(0xFF9E9E9E).copy(alpha = 0.45f)
        drawPath(
            path  = shieldPath,
            color = borderColor,
            style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )

        // ── Top crown bar (appears from Bronze+) ─────────────────────────────
        if (tier > 0) {
            val crownPath = Path().apply {
                moveTo(w * 0.22f, h * 0.18f)
                lineTo(w * 0.78f, h * 0.18f)
            }
            drawPath(
                path  = crownPath,
                color = tierColor,
                style = Stroke(width = stroke * 0.65f, cap = StrokeCap.Round),
            )
        }
    }
}

// ── Helper: shield path ────────────────────────────────────────────────────────

private fun shieldPath(w: Float, h: Float): Path = Path().apply {
    // Top flat edge with corner notch (classic military shield silhouette)
    moveTo(w * 0.5f, h * 0.04f)
    lineTo(w * 0.92f, h * 0.14f)
    lineTo(w * 0.92f, h * 0.52f)
    // Right curve down to point
    cubicTo(
        w * 0.92f, h * 0.76f,
        w * 0.50f, h * 0.97f,
        w * 0.50f, h * 0.97f,
    )
    // Left curve up
    cubicTo(
        w * 0.50f, h * 0.97f,
        w * 0.08f, h * 0.76f,
        w * 0.08f, h * 0.52f,
    )
    lineTo(w * 0.08f, h * 0.14f)
    close()
}

// ── Helper: lightning bolt for Gold tier ──────────────────────────────────────

private fun DrawScope.drawLightningBolt(w: Float, h: Float, color: Color) {
    val boltPath = Path().apply {
        moveTo(w * 0.57f, h * 0.22f)
        lineTo(w * 0.42f, h * 0.38f)
        lineTo(w * 0.53f, h * 0.38f)
        lineTo(w * 0.43f, h * 0.56f)
        lineTo(w * 0.60f, h * 0.37f)
        lineTo(w * 0.49f, h * 0.37f)
        close()
    }
    drawPath(boltPath, color = color)
}
