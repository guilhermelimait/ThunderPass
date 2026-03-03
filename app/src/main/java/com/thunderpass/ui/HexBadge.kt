package com.thunderpass.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// ─────────────────────────────────────────────────────────────────────────────
// Rarity tier definitions — ordered lowest → highest
// ─────────────────────────────────────────────────────────────────────────────

enum class BadgeRarity(
    val label:     String,
    val color:     Color,
    val glowColor: Color,
    val textColor: Color,
) {
    COMMON(
        label     = "Common",
        color     = Color(0xFFBDBDBD),
        glowColor = Color(0x44BDBDBD),
        textColor = Color(0xFF212121),
    ),
    UNCOMMON(
        label     = "Uncommon",
        color     = Color(0xFF4CAF50),
        glowColor = Color(0x554CAF50),
        textColor = Color.White,
    ),
    RARE(
        label     = "Rare",
        color     = Color(0xFF2979FF),
        glowColor = Color(0x552979FF),
        textColor = Color.White,
    ),
    LEGENDARY(
        label     = "Legendary",
        color     = Color(0xFFAB47BC),
        glowColor = Color(0x55AB47BC),
        textColor = Color.White,
    ),
    EXOTIC(
        label     = "Exotic",
        color     = Color(0xFFFFB300),
        glowColor = Color(0x55FFB300),
        textColor = Color(0xFF212121),
    ),
}

// ─────────────────────────────────────────────────────────────────────────────
// Data model — placeholder badges (fill in real unlock logic later)
// ─────────────────────────────────────────────────────────────────────────────

data class Badge(
    val id:       String,
    val name:     String,
    val rarity:   BadgeRarity,
    val emoji:    String,
    val unlocked: Boolean = false,
)

/** Master badge catalogue — unlock conditions wired in a future milestone. */
val BADGE_CATALOGUE: List<Badge> = listOf(
    Badge("first_spark",    "First Spark",    BadgeRarity.COMMON,    "⚡", unlocked = false),
    Badge("ten_encounters", "Connector",      BadgeRarity.COMMON,    "🤝", unlocked = false),
    Badge("thunder_net",    "Thunder Net",    BadgeRarity.UNCOMMON,  "🔗", unlocked = false),
    Badge("night_owl",      "Night Owl",      BadgeRarity.UNCOMMON,  "🦉", unlocked = false),
    Badge("storm_rider",    "Storm Rider",    BadgeRarity.RARE,      "🌩", unlocked = false),
    Badge("volt_surge",     "Volt Surge",     BadgeRarity.RARE,      "🔋", unlocked = false),
    Badge("storm_caller",   "Storm Caller",   BadgeRarity.LEGENDARY, "👑", unlocked = false),
    Badge("overcharged",    "Overcharged",    BadgeRarity.EXOTIC,    "🌟", unlocked = false),
)

// ─────────────────────────────────────────────────────────────────────────────
// Flat-top hexagon path helper
// ─────────────────────────────────────────────────────────────────────────────

/** Builds a flat-top regular hexagon path centred at [centre] with the given [radius]. */
private fun hexPath(centre: Offset, radius: Float): Path {
    val path = Path()
    for (i in 0..5) {
        val angle = Math.toRadians((60.0 * i) - 30.0)   // pointy-top
        val x = centre.x + radius * cos(angle).toFloat()
        val y = centre.y + radius * sin(angle).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}

/** Draws a filled hex with a thin border. */
private fun DrawScope.drawHex(
    centre:    Offset,
    radius:    Float,
    fillColor: Color,
    rimColor:  Color,
    rimWidth:  Float = 2.5f,
) {
    val path = hexPath(centre, radius)
    drawPath(path, fillColor)
    drawPath(path, rimColor, style = Stroke(width = rimWidth))
}

// ─────────────────────────────────────────────────────────────────────────────
// Single HexBadge composable
// ─────────────────────────────────────────────────────────────────────────────

/**
 * A single hexagonal badge tile.
 *
 * @param badge     The badge to render.
 * @param size      The flat-to-flat hex diameter in dp.
 */
@Composable
fun HexBadge(
    badge:    Badge,
    size:     Dp     = 64.dp,
    modifier: Modifier = Modifier,
) {
    val rarity    = badge.rarity
    val fillColor = if (badge.unlocked) rarity.color else Color(0xFF1A1A1A)
    val rimColor  = if (badge.unlocked) rarity.color.copy(alpha = 0.9f) else Color(0xFF333333)
    val textColor = if (badge.unlocked) rarity.textColor else Color(0xFF555555)

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(size),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = size.toPx() / 2f * 0.92f
            val centre = Offset(size.toPx() / 2f, size.toPx() / 2f)

            // Glow ring for unlocked badges
            if (badge.unlocked) {
                drawHex(
                    centre    = centre,
                    radius    = radius + 4f,
                    fillColor = rarity.glowColor,
                    rimColor  = Color.Transparent,
                )
            }
            drawHex(
                centre    = centre,
                radius    = radius,
                fillColor = fillColor,
                rimColor  = rimColor,
                rimWidth  = if (badge.unlocked) 2.5f else 1.5f,
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text     = if (badge.unlocked) badge.emoji else "🔒",
                fontSize = (size.value * 0.30).sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Honeycomb grid shelf
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Renders [badges] in a compact honeycomb layout (rows of 4 / 3 alternating).
 * Locked badges are shown in dark grey so the grid always looks full.
 */
@Composable
fun BadgeShelf(
    badges:   List<Badge> = BADGE_CATALOGUE,
    hexSize:  Dp          = 60.dp,
    modifier: Modifier    = Modifier,
) {
    val cols        = 4
    val hexW        = hexSize
    val hexH        = hexSize * (sqrt(3f) / 2f)   // height of a pointy-top hex
    val gapH        = 4.dp
    val offsetX     = hexW / 2f                    // stagger for odd rows
    val rows        = (badges.size + cols - 1) / cols

    // Total canvas height
    val totalHeight = hexH * rows + gapH * (rows - 1)

    Box(modifier = modifier.fillMaxWidth().height(totalHeight)) {
        badges.forEachIndexed { index, badge ->
            val row = index / cols
            val col = index % cols
            val isOddRow = row % 2 == 1

            val x = hexW * col + if (isOddRow) offsetX else 0.dp
            val y = (hexH + gapH) * row

            HexBadge(
                badge    = badge,
                size     = hexSize,
                modifier = Modifier
                    .offset(x = x, y = y),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Rarity legend row
// ─────────────────────────────────────────────────────────────────────────────

/** A small horizontal legend showing each rarity tier's colour dot + label. */
@Composable
fun RarityLegend(modifier: Modifier = Modifier) {
    Row(
        modifier            = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
    ) {
        BadgeRarity.entries.forEach { rarity ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Canvas(modifier = Modifier.size(8.dp)) {
                    drawCircle(color = rarity.color)
                }
                Text(
                    text  = rarity.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = rarity.color,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
