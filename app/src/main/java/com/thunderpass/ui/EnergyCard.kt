package com.thunderpass.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview

// ─────────────────────────────────────────────────────────────────────────────
// Volt level definitions
// ─────────────────────────────────────────────────────────────────────────────

data class VoltLevel(
    val number:  Int,
    val name:    String,
    val tagline: String,
    val color:   Color,
    val minJ:    Long,
    val maxJ:    Long?,   // null = no ceiling
)

val VOLT_LEVELS = listOf(
    VoltLevel(0, "DORMANT",     "Start encountering to ignite your spark",    Color(0xFF616161), 0L,       100L),
    VoltLevel(1, "FLICKERING",  "The spark has ignited — keep going",          Color(0xFFFF8800), 100L,     500L),
    VoltLevel(2, "CHARGED",     "Getting charged up — full power soon",        Color(0xFFFFA000), 500L,     1_500L),
    VoltLevel(3, "SURGING",     "Energy surging through the grid",             Color(0xFFFFD400), 1_500L,   5_000L),
    VoltLevel(4, "THUNDERING",  "Full power flowing through every wire",       Color(0xFFB8FF00), 5_000L,   10_000L),
    VoltLevel(5, "OVERCHARGED", "Maximum voltage — you are a legend",          Color(0xFF00FFD0), 10_000L,  null),
)

fun voltLevelFor(voltsTotal: Long): VoltLevel =
    VOLT_LEVELS.last { voltsTotal >= it.minJ }

// ─────────────────────────────────────────────────────────────────────────────
// EnergyCard — full-width hero card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun EnergyCard(
    voltsTotal: Long,
    modifier:    Modifier = Modifier,
) {
    val level    = voltLevelFor(voltsTotal)
    val accent   = level.color
    val cardBg   = MaterialTheme.colorScheme.surfaceVariant

    // Progress fraction within current level
    val progressFraction: Float = when {
        level.maxJ == null -> 1f
        else -> ((voltsTotal - level.minJ).toFloat() /
                 (level.maxJ    - level.minJ).toFloat()).coerceIn(0f, 1f)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(cardBg)
            .padding(20.dp),
    ) {
        // ── Decorative background lightning bolt ─────────────────────────────
        Text(
            text     = "⚡",
            fontSize = 180.sp,
            color    = accent.copy(alpha = 0.10f),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(x = 32.dp, y = (-8).dp),
        )

        Column(
            modifier            = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {

            // ── Top row: level label ─────────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text       = "VOLT LEVEL ${level.number}",
                    color      = accent.copy(alpha = 0.75f),
                    fontFamily = FontFamily.Monospace,
                    fontSize   = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                )
                Text(
                    text       = level.name,
                    color      = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Black,
                    fontSize   = 26.sp,
                    letterSpacing = 0.5.sp,
                )
            }

            // ── Volts value ───────────────────────────────────────────────────────────────────
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text       = "%,d".format(voltsTotal),
                    color      = accent,
                    fontWeight = FontWeight.Black,
                    fontSize   = 48.sp,
                    lineHeight = 48.sp,
                )
                Text(
                    text       = "V",
                    color      = accent.copy(alpha = 0.6f),
                    fontWeight = FontWeight.Bold,
                    fontSize   = 28.sp,
                    modifier   = Modifier.padding(bottom = 4.dp),
                )
            }

            // ── Segmented bar ────────────────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    val totalSegments = VOLT_LEVELS.size   // 6 segments (0-5)
                    (0 until totalSegments).forEach { i ->
                        val filled    = i < level.number
                        val isCurrent = i == level.number && level.maxJ != null
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(7.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    when {
                                        filled    -> accent
                                        isCurrent -> accent.copy(alpha = progressFraction.coerceAtLeast(0.25f))
                                        else      -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                                    }
                                ),
                        )
                    }
                }

                // Next level threshold hint
                val nextJ = level.maxJ
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    Text(
                        text  = level.tagline,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        modifier = Modifier.weight(1f),
                    )
                    if (nextJ != null) {
                        val remaining = nextJ - voltsTotal
                        Text(
                            text  = "+${"%,d".format(remaining)}V to next",
                            color = accent.copy(alpha = 0.55f),
                            fontFamily = FontFamily.Monospace,
                            fontSize   = 10.sp,
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun EnergyCardPreview() {
    MaterialTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            EnergyCard(voltsTotal = 2500)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// CompactVoltBadge — small pill for ProfileScreen / stat areas
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun CompactVoltBadge(
    voltsTotal: Long,
    modifier:    Modifier = Modifier,
    height:      Dp       = 36.dp,
) {
    val level  = voltLevelFor(voltsTotal)
    val accent = level.color

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("⚡", fontSize = 16.sp)
        Column {
            Text(
                text       = "VOLT LV.${level.number} · ${level.name}",
                color      = accent,
                fontFamily = FontFamily.Monospace,
                fontSize   = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
            )
            Text(
                text  = "%,d Volts accumulated".format(voltsTotal),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize  = 10.sp,
            )
        }
    }
}
