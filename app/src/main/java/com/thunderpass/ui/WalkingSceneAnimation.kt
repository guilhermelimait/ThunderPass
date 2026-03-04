package com.thunderpass.ui

import android.net.Uri
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.dp
import kotlin.math.*

// ─────────────────────────────────────────────────────────────────────────────
// Walking Scene Card — parallax runner animation for the Home screen
//   • 4-scene looping world: City → Park → Volcano → Space
//   • Pastel palette per scene, sky gradient blends between scenes
//   • Person body drawn via Canvas, DiceBear avatar head overlaid
// ─────────────────────────────────────────────────────────────────────────────

/** DiceBear URL without background — transparent head for the walking figure. */
fun diceBearTransparentUrl(seed: String): String =
    "https://api.dicebear.com/9.x/big-smile/svg" +
    "?seed=${Uri.encode(seed)}" +
    "&radius=50" +
    "&size=128"

// ── Pastel palettes per scene ─────────────────────────────────────────────────

private data class ScenePalette(
    val sky1: Color,       // sky gradient top
    val sky2: Color,       // sky gradient bottom
    val groundA: Color,    // ground top strip
    val groundB: Color,    // ground base
    val far1: Color,       // far element fill
    val far2: Color,       // far element accent
    val near1: Color,      // near element fill
    val near2: Color,      // near element accent
)

private val PAL_CITY = ScenePalette(
    sky1   = Color(0xFFB4D4EE), sky2   = Color(0xFFCEE5F5),
    groundA = Color(0xFF9EB8C4), groundB = Color(0xFF7E9EAE),
    far1   = Color(0xFF90AEC8), far2   = Color(0xFFB4CCE0),
    near1  = Color(0xFF6E9AB2), near2  = Color(0xFF5882A0),
)
private val PAL_PARK = ScenePalette(
    sky1   = Color(0xFFA4DEB8), sky2   = Color(0xFFC4EDD0),
    groundA = Color(0xFF72C48A), groundB = Color(0xFF52A66C),
    far1   = Color(0xFF5DAA76), far2   = Color(0xFF90C8A0),
    near1  = Color(0xFF3E8C58), near2  = Color(0xFF68B480),
)
private val PAL_VOLCANO = ScenePalette(
    sky1   = Color(0xFFFFB894), sky2   = Color(0xFFFFD4B0),
    groundA = Color(0xFFBE7E6E), groundB = Color(0xFF9E6254),
    far1   = Color(0xFFD08070), far2   = Color(0xFFE8A098),
    near1  = Color(0xFFE06060), near2  = Color(0xFFFF8870),
)
private val PAL_SPACE = ScenePalette(
    sky1   = Color(0xFF68789E), sky2   = Color(0xFF8898BE),
    groundA = Color(0xFF505878), groundB = Color(0xFF383E5E),
    far1   = Color(0xFFB0A0D8), far2   = Color(0xFFD4C8F0),
    near1  = Color(0xFF6878A8), near2  = Color(0xFF8898C0),
)

private val PALETTES = listOf(PAL_CITY, PAL_PARK, PAL_VOLCANO, PAL_SPACE)

// ── Main composable ───────────────────────────────────────────────────────────

@Composable
fun WalkingSceneCard(avatarSeed: String) {
    val inf = rememberInfiniteTransition(label = "walk_scene")

    // Scene scroll: 0→1 in 28 s — drives the parallax world
    val scrollFrac by inf.animateFloat(
        initialValue  = 0f,
        targetValue   = 1f,
        animationSpec = infiniteRepeatable(tween(28_000, easing = LinearEasing)),
        label         = "scroll",
    )
    // Walk cycle: 0→2π in 540 ms
    val walkPhase by inf.animateFloat(
        initialValue  = 0f,
        targetValue   = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(540, easing = LinearEasing)),
        label         = "cycle",
    )

    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
        ) {
            val sceneW   = maxWidth           // Dp
            val sceneH   = 200.dp

            // Fractional layout constants (must match drawWalkingBody)
            val personFracX = 0.28f
            val groundFracY = 0.80f
            // head-top from canvas top (in fractions of height):
            //  groundY - legLen(0.28) - torsoH(0.22) - headR(0.12) = 0.80-0.62 = 0.18
            //  headTop = headCenter - headR = 0.18 - 0.12 = 0.06
            val headFracTopY = 0.06f
            val headFracR    = 0.12f   // head radius  as fraction of height

            val headSizeDp  = sceneH * (headFracR * 2f)   // diameter
            val headHalfDp  = headSizeDp / 2f

            // 1 — Parallax background
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawParallaxBg(scrollFrac)
            }

            // 2 — Walking body (no head)
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawWalkingBody(
                    personFracX = personFracX,
                    groundFracY = groundFracY,
                    walkPhase   = walkPhase,
                )
            }

            // 3 — DiceBear avatar head (transparent background)
            DiceBearAvatar(
                seed     = avatarSeed,
                size     = headSizeDp,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(
                        x = sceneW * personFracX - headHalfDp,
                        y = sceneH * headFracTopY,
                    )
                    .clip(CircleShape),
            )
        }
    }
}

// ── Parallax background ───────────────────────────────────────────────────────

private fun DrawScope.drawParallaxBg(scrollFrac: Float) {
    val w        = size.width
    val h        = size.height
    val groundY  = h * 0.80f

    // Total world = 4 scenes × w pixels, scroll through in 28 s
    val fullScroll = scrollFrac * w * 4f

    // Current scene blend for sky/ground color
    val sWorld = (scrollFrac * 4f).coerceAtLeast(0f)
    val sIdx   = (sWorld.toInt() % 4).coerceIn(0, 3)
    val sFrac  = sWorld - floor(sWorld)
    val p0     = PALETTES[sIdx]
    val p1     = PALETTES[(sIdx + 1) % 4]

    fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
    fun lc(a: Color, b: Color, t: Float)   = Color(
        lerp(a.red,   b.red,   t),
        lerp(a.green, b.green, t),
        lerp(a.blue,  b.blue,  t),
    )

    val skyTop   = lc(p0.sky1,    p1.sky1,    sFrac.toFloat())
    val skyBot   = lc(p0.sky2,    p1.sky2,    sFrac.toFloat())
    val gndA     = lc(p0.groundA, p1.groundA, sFrac.toFloat())
    val gndB     = lc(p0.groundB, p1.groundB, sFrac.toFloat())

    // Sky
    drawRect(
        brush = Brush.verticalGradient(listOf(skyTop, skyBot), endY = groundY),
        size  = Size(w, groundY),
    )
    // Ground strips
    drawRect(gndA, topLeft = Offset(0f, groundY),             size = Size(w, h * 0.07f))
    drawRect(gndB, topLeft = Offset(0f, groundY + h * 0.07f), size = Size(w, h * 0.20f))

    // Far elements (scroll at 38 % speed)
    val farScroll  = fullScroll * 0.38f
    val nearScroll = fullScroll

    clipRect(0f, 0f, w, h) {
        // Draw 6 tile slots to cover any wrapping
        for (layer in 0..1) {
            val layerScroll = if (layer == 0) farScroll else nearScroll
            val baseTile    = floor(layerScroll / w).toInt()
            for (off in 0..5) {
                val tile      = baseTile + off
                val tileType  = ((tile % 4) + 4) % 4
                val tileLeft  = tile * w - layerScroll
                if (tileLeft >= w + w * 0.2f || tileLeft + w <= -w * 0.2f) continue
                val pal = PALETTES[tileType]
                if (layer == 0) drawFarTile(tileType, tileLeft, w, h, groundY, pal)
                else            drawNearTile(tileType, tileLeft, w, h, groundY, pal)
            }
        }
    }
}

// ── Far layer tiles (slower, smaller, upper area) ────────────────────────────

private fun DrawScope.drawFarTile(
    type: Int, left: Float, w: Float, h: Float, gY: Float, pal: ScenePalette,
) = when (type) {
    0 -> drawCityFar(left, w, h, gY, pal)
    1 -> drawParkFar(left, w, h, gY, pal)
    2 -> drawVolcanoFar(left, w, h, gY, pal)
    else -> drawSpaceFar(left, w, h, gY, pal)
}

private fun DrawScope.drawNearTile(
    type: Int, left: Float, w: Float, h: Float, gY: Float, pal: ScenePalette,
) = when (type) {
    0 -> drawCityNear(left, w, h, gY, pal)
    1 -> drawParkNear(left, w, h, gY, pal)
    2 -> drawVolcanoNear(left, w, h, gY, pal)
    else -> drawSpaceNear(left, w, h, gY, pal)
}

// ── CITY ─────────────────────────────────────────────────────────────────────

private fun DrawScope.drawCityFar(l: Float, w: Float, h: Float, gY: Float, pal: ScenePalette) {
    // Background buildings (shorter, lighter)
    val blds = listOf(0.06f to 0.60f, 0.20f to 0.45f, 0.35f to 0.55f,
                      0.50f to 0.50f, 0.65f to 0.62f, 0.80f to 0.42f, 0.92f to 0.52f)
    blds.forEach { (rx, topFrac) ->
        val bx = l + rx * w
        val bw = w * 0.09f
        val bt = gY * topFrac
        drawRect(pal.far1, topLeft = Offset(bx - bw / 2f, bt), size = Size(bw, gY - bt))
        // windows
        for (row in 0..2) for (col in 0..1) {
            drawRect(
                Color.White.copy(alpha = 0.35f),
                topLeft = Offset(bx - bw / 2f + bw * 0.15f + col * bw * 0.40f,
                                 bt + (gY - bt) * 0.12f + row * (gY - bt) * 0.22f),
                size    = Size(bw * 0.22f, (gY - bt) * 0.12f),
            )
        }
    }
}

private fun DrawScope.drawCityNear(l: Float, w: Float, h: Float, gY: Float, pal: ScenePalette) {
    // Streetlamps at the ground line
    val lampsX = listOf(0.10f, 0.40f, 0.72f)
    lampsX.forEach { rx ->
        val lx = l + rx * w
        // pole
        drawLine(pal.near1, Offset(lx, gY - h * 0.22f), Offset(lx, gY), strokeWidth = w * 0.012f)
        // arm
        drawLine(pal.near1, Offset(lx, gY - h * 0.22f), Offset(lx + w * 0.04f, gY - h * 0.22f),
                 strokeWidth = w * 0.010f)
        // bulb glow
        drawCircle(Color(0xFFFFF0A0).copy(alpha = 0.7f), radius = w * 0.018f,
                   center = Offset(lx + w * 0.04f, gY - h * 0.22f))
    }
    // Road marking (dashes)
    val dashCount = 6
    for (d in 0 until dashCount) {
        val dx = l + w * d / dashCount.toFloat() + w * 0.05f
        if (dx < 0f || dx > size.width) continue
        drawRect(Color.White.copy(alpha = 0.25f),
                 topLeft = Offset(dx, gY + h * 0.035f), size = Size(w * 0.06f, h * 0.015f))
    }
}

// ── PARK ─────────────────────────────────────────────────────────────────────

private fun DrawScope.drawParkFar(l: Float, w: Float, h: Float, gY: Float, pal: ScenePalette) {
    // Rolling hills
    val hillCentres = listOf(0.20f to 0.70f, 0.55f to 0.75f, 0.85f to 0.65f)
    hillCentres.forEach { (rx, topFrac) ->
        val cx    = l + rx * w
        val hillW = w * 0.42f
        val hillH = gY * (1f - topFrac)
        val hillT = gY * topFrac
        val path  = Path().apply {
            moveTo(cx - hillW / 2f, gY)
            cubicTo(cx - hillW / 2f, hillT, cx + hillW / 2f, hillT, cx + hillW / 2f, gY)
            close()
        }
        drawPath(path, pal.far1)
    }
    // Distant tree dots
    listOf(0.05f, 0.35f, 0.70f, 0.95f).forEach { rx ->
        val tx = l + rx * w
        drawCircle(pal.far2, radius = h * 0.08f, center = Offset(tx, gY - h * 0.16f))
        drawLine(pal.far1, Offset(tx, gY - h * 0.10f), Offset(tx, gY), strokeWidth = w * 0.012f)
    }
}

private fun DrawScope.drawParkNear(l: Float, w: Float, h: Float, gY: Float, pal: ScenePalette) {
    // Trees (bigger, near ground)
    listOf(0.12f, 0.48f, 0.82f).forEach { rx ->
        val tx = l + rx * w
        // trunk
        drawRect(pal.near1.copy(alpha = 0.8f),
                 topLeft = Offset(tx - w * 0.012f, gY - h * 0.20f), size = Size(w * 0.024f, h * 0.20f))
        // canopy
        drawCircle(pal.near2, radius = h * 0.11f, center = Offset(tx, gY - h * 0.232f))
        drawCircle(pal.near1, radius = h * 0.08f, center = Offset(tx - w * 0.02f, gY - h * 0.26f))
    }
    // Flowers (tiny dots on the ground)
    val flowerColors = listOf(Color(0xFFFFB0C0), Color(0xFFFFF0A0), Color(0xFFD0C0FF))
    for (i in 0..8) {
        val fx = l + w * (i * 0.11f + 0.03f)
        if (fx < 0f || fx > size.width) continue
        drawCircle(flowerColors[i % 3], radius = h * 0.016f, center = Offset(fx, gY + h * 0.018f))
    }
}

// ── VOLCANO ───────────────────────────────────────────────────────────────────

private fun DrawScope.drawVolcanoFar(l: Float, w: Float, h: Float, gY: Float, pal: ScenePalette) {
    val topX    = l + w * 0.50f
    val topY    = gY * 0.20f
    val baseHalfW = w * 0.48f
    val coneCol = pal.far1

    // Volcano cone
    val cone = Path().apply {
        moveTo(topX, topY)
        lineTo(topX + baseHalfW, gY)
        lineTo(topX - baseHalfW, gY)
        close()
    }
    drawPath(cone, coneCol)

    // Crater rim
    drawCircle(pal.far2.copy(alpha = 0.6f), radius = w * 0.08f, center = Offset(topX, topY + w * 0.04f))
    drawCircle(Color(0xFFFF6040).copy(alpha = 0.7f), radius = w * 0.05f, center = Offset(topX, topY + w * 0.05f))

    // Smoke puffs
    for (i in 0..2) {
        drawCircle(Color.White.copy(alpha = 0.25f - i * 0.06f),
                   radius = w * (0.05f + i * 0.04f),
                   center = Offset(topX + i * w * 0.03f, topY - h * (0.06f + i * 0.07f)))
    }
}

private fun DrawScope.drawVolcanoNear(l: Float, w: Float, h: Float, gY: Float, pal: ScenePalette) {
    // Rocks on the ground
    listOf(0.08f to 0.8f, 0.30f to 1.0f, 0.55f to 0.7f, 0.78f to 0.9f).forEach { (rx, sc) ->
        val rx2 = l + rx * w
        val rr  = w * 0.04f * sc
        drawCircle(pal.near1, radius = rr, center = Offset(rx2, gY + rr * 0.5f))
        drawCircle(pal.near2, radius = rr * 0.5f, center = Offset(rx2 - rr * 0.3f, gY - rr * 0.2f))
    }
    // Lava drip on the side
    val lavaPath = Path().apply {
        moveTo(l + w * 0.52f, gY - h * 0.05f)
        cubicTo(l + w * 0.54f, gY - h * 0.03f, l + w * 0.53f, gY, l + w * 0.55f, gY + h * 0.02f)
    }
    drawPath(lavaPath, Color(0xFFFF5030).copy(alpha = 0.75f), style = Stroke(width = w * 0.015f))
}

// ── SPACE ────────────────────────────────────────────────────────────────────

private fun DrawScope.drawSpaceFar(l: Float, w: Float, h: Float, gY: Float, pal: ScenePalette) {
    // Stars
    for (i in 0..18) {
        val sx = l + ((i * 137.508f) % w)   // golden-angle spread (deterministic)
        val sy = h * ((i * 0.053f) % 0.68f)
        if (sx < l || sx > l + w) continue
        drawCircle(Color.White.copy(alpha = 0.55f + (i % 3) * 0.15f),
                   radius = w * 0.006f + (i % 2) * w * 0.003f,
                   center = Offset(sx, sy))
    }
    // Main planet
    val px = l + w * 0.70f
    val py = gY * 0.32f
    val pr = h * 0.14f
    drawCircle(pal.far1, radius = pr, center = Offset(px, py))
    drawCircle(pal.far2.copy(alpha = 0.4f), radius = pr * 0.6f, center = Offset(px - pr * 0.15f, py - pr * 0.2f))
    // Ring ellipse
    drawOval(pal.far2.copy(alpha = 0.55f),
             topLeft = Offset(px - pr * 1.6f, py - pr * 0.22f),
             size    = Size(pr * 3.2f, pr * 0.44f),
             style   = Stroke(width = w * 0.014f))
    // Small moon
    drawCircle(Color(0xFFE0D8F0), radius = h * 0.04f, center = Offset(l + w * 0.20f, gY * 0.22f))
}

private fun DrawScope.drawSpaceNear(l: Float, w: Float, h: Float, gY: Float, pal: ScenePalette) {
    // Craters on the ground
    listOf(0.12f, 0.38f, 0.60f, 0.85f).forEach { rx ->
        val cx = l + rx * w
        val cr = w * 0.05f
        drawCircle(pal.near1.copy(alpha = 0.6f), radius = cr, center = Offset(cx, gY + cr * 0.3f))
        drawCircle(pal.near2.copy(alpha = 0.6f), radius = cr * 0.55f, center = Offset(cx + cr * 0.1f, gY + cr * 0.1f))
    }
    // Small asteroid
    val ax = l + w * 0.25f
    val ay = gY - h * 0.08f
    val ap = Path().apply {
        moveTo(ax,            ay - h * 0.03f)
        lineTo(ax + w * 0.04f, ay)
        lineTo(ax + w * 0.03f, ay + h * 0.03f)
        lineTo(ax - w * 0.02f, ay + h * 0.025f)
        lineTo(ax - w * 0.035f, ay - h * 0.01f)
        close()
    }
    drawPath(ap, pal.near1)
}

// ── Walking body (no head — head is the DiceBear composable above) ─────────────────

private fun DrawScope.drawWalkingBody(
    personFracX: Float,
    groundFracY: Float,
    walkPhase:   Float,
) {
    val w = size.width
    val h = size.height

    val pX      = w * personFracX       // person center X
    val groundY = h * groundFracY       // ground line

    // Segment lengths (fractions of canvas height)
    val legLen    = h * 0.28f
    val thighLen  = legLen * 0.52f
    val shinLen   = legLen * 0.55f
    val torsoH    = h * 0.22f
    val torsoHalf = w * 0.065f          // half-width of torso
    val armLen    = torsoH * 0.6f

    val hipY      = groundY - legLen + thighLen    // top of leg / bottom of torso
    val shoulderY = hipY - torsoH

    // Walking phase values
    val swingL =  sin(walkPhase)        * (h * 0.10f)   // left leg / right arm
    val swingR = -sin(walkPhase)        * (h * 0.10f)   // right leg / left arm
    val bob    =  abs(sin(walkPhase * 2f)) * (h * 0.012f)   // vertical body bob

    val bodyOffsetY = -bob   // bob lifts body slightly

    // ── Backpack ─────────────────────────────────────────────────────────────
    drawRoundRect(
        color       = Color(0xFF7888A0),
        topLeft     = Offset(pX + torsoHalf * 0.25f, shoulderY + bodyOffsetY + torsoH * 0.08f),
        size        = Size(torsoHalf * 1.10f, torsoH * 0.75f),
        cornerRadius = CornerRadius(w * 0.018f),
    )

    // ── Legs ─────────────────────────────────────────────────────────────────
    // Each leg: hip → knee (thigh) → foot (shin)
    for (side in 0..1) {
        val swing = if (side == 0) swingL else swingR
        val legX  = pX + (if (side == 0) -torsoHalf * 0.35f else torsoHalf * 0.35f)

        val kneeX = legX + swing * 0.5f
        val kneeY = hipY + bodyOffsetY + thighLen

        val footX = legX + swing
        val footY = groundY

        val segColor = if (side == 0) Color(0xFF8890B0) else Color(0xFF7880A0)
        val segW     = w * 0.042f

        val legPath = Path().apply {
            moveTo(legX, hipY + bodyOffsetY)
            lineTo(kneeX, kneeY)
            lineTo(footX, footY)
        }
        drawPath(legPath, segColor,
                 style = Stroke(width = segW, cap = StrokeCap.Round, join = StrokeJoin.Round))

        // Shoe
        drawOval(
            color   = Color(0xFFE8D8B8),
            topLeft = Offset(footX - w * 0.038f, footY - h * 0.018f),
            size    = Size(w * 0.075f, h * 0.032f),
        )
    }

    // ── Torso ────────────────────────────────────────────────────────────────
    drawRoundRect(
        color       = Color(0xFFB0C8E8),
        topLeft     = Offset(pX - torsoHalf, shoulderY + bodyOffsetY),
        size        = Size(torsoHalf * 2f, torsoH),
        cornerRadius = CornerRadius(w * 0.025f),
    )
    // Small smiley / badge on shirt
    drawCircle(Color(0xFFFFD060).copy(alpha = 0.8f),
               radius = w * 0.022f,
               center = Offset(pX - torsoHalf * 0.1f, shoulderY + bodyOffsetY + torsoH * 0.40f))

    // ── Arms ──────────────────────────────────────────────────────────────────
    for (side in 0..1) {
        val armSwing = if (side == 0) swingR else swingL   // opposite to legs
        val armX     = pX + (if (side == 0) -torsoHalf else torsoHalf)
        val armEndX  = armX + armSwing * 0.55f
        val armEndY  = shoulderY + bodyOffsetY + torsoH * 0.5f + armLen

        drawLine(
            color       = Color(0xFFF5D5B0),
            start       = Offset(armX, shoulderY + bodyOffsetY + torsoH * 0.15f),
            end         = Offset(armEndX, armEndY),
            strokeWidth = w * 0.038f,
            cap         = StrokeCap.Round,
        )
    }
}
