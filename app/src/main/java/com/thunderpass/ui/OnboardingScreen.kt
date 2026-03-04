package com.thunderpass.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thunderpass.R

private val GridBlack  = Color(0xFF0D1117)
private val GridPurple = Color(0xFF7C3AED)
private val GridCyan   = Color(0xFF06B6D4)
private val GridLine   = Color(0xFF21262D)

@Composable
fun OnboardingScreen(onEnter: () -> Unit) {
    // Sweep animation — a bright scanline travels top→bottom forever
    val infiniteTransition = rememberInfiniteTransition(label = "grid_sweep")
    val sweepProgress by infiniteTransition.animateFloat(
        initialValue   = 0f,
        targetValue    = 1f,
        animationSpec  = infiniteRepeatable(
            animation  = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "sweep",
    )

    // Fade-in for text content
    val contentAlpha by animateFloatAsState(
        targetValue    = 1f,
        animationSpec  = tween(1200, delayMillis = 400),
        label          = "content_alpha",
    )

    Box(
        modifier           = Modifier
            .fillMaxSize()
            .background(GridBlack),
        contentAlignment   = Alignment.Center,
    ) {
        // ── Animated grid background ─────────────────────────────────────────
        Canvas(modifier = Modifier.fillMaxSize()) {
            val step = 48.dp.toPx()
            val sweepY = sweepProgress * size.height

            // Vertical lines
            var x = 0f
            while (x <= size.width) {
                drawLine(
                    color       = GridLine,
                    start       = Offset(x, 0f),
                    end         = Offset(x, size.height),
                    strokeWidth = 1f,
                )
                x += step
            }
            // Horizontal lines
            var y = 0f
            while (y <= size.height) {
                drawLine(
                    color       = GridLine,
                    start       = Offset(0f, y),
                    end         = Offset(size.width, y),
                    strokeWidth = 1f,
                )
                y += step
            }

            // Sweeping glow band
            val bandHeight = 120.dp.toPx()
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        GridPurple.copy(alpha = 0.12f),
                        GridCyan.copy(alpha = 0.18f),
                        GridPurple.copy(alpha = 0.12f),
                        Color.Transparent,
                    ),
                    startY = sweepY - bandHeight / 2,
                    endY   = sweepY + bandHeight / 2,
                ),
                size = size,
            )

            // Radial vignette — darken corners to focus the eye on center
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(Color.Transparent, GridBlack.copy(alpha = 0.75f)),
                    center = Offset(size.width / 2, size.height / 2),
                    radius = (size.width.coerceAtLeast(size.height)) * 0.72f,
                ),
                size = size,
            )
        }

        // ── Foreground content ───────────────────────────────────────────────
        Column(
            modifier            = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
                .padding(bottom = 64.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // Logo
            Image(
                painter            = painterResource(R.drawable.logo),
                contentDescription = "ThunderPass",
                modifier           = Modifier
                    .height(110.dp)
                    .padding(bottom = 4.dp),
                alpha              = contentAlpha,
            )

            Text(
                text      = "Level up your social game",
                style     = MaterialTheme.typography.titleMedium,
                color     = GridCyan.copy(alpha = contentAlpha),
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(8.dp))

            // Feature highlights
            listOf(
                "📡" to "Auto-detect nearby players via Bluetooth",
                "⚡" to "Earn Volts energy every time you Spark",
                "🎮" to "Sync your RetroAchievements Spark Card",
            ).forEach { (icon, text) ->
                FeatureRow(
                    icon    = icon,
                    text    = text,
                    alpha   = contentAlpha,
                )
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick  = onEnter,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape    = RoundedCornerShape(14.dp),
                colors   = ButtonDefaults.buttonColors(
                    containerColor = GridPurple,
                    contentColor   = Color.White,
                ),
            ) {
                Text(
                    text       = "Enter the Grid",
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun FeatureRow(icon: String, text: String, alpha: Float) {
    Row(
        modifier            = Modifier.fillMaxWidth(),
        verticalAlignment   = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            modifier = Modifier.size(36.dp),
            shape    = RoundedCornerShape(10.dp),
            color    = Color(0xFF21262D),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(icon, fontSize = 18.sp)
            }
        }
        Text(
            text  = text,
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFFF0F6FF).copy(alpha = alpha * 0.85f),
        )
    }
}
