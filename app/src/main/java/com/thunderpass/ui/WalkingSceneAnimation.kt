package com.thunderpass.ui

import android.net.Uri
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.*
import kotlinx.coroutines.delay

// ─────────────────────────────────────────────────────────────────────────────
// Walking Scene Card — parallax running animation
//   • 4-scene world: City → Park → Volcano → Space  (seamless loop)
//   • Far silhouette: sine-wave based → zero seam at any speed ratio
//   • Near objects: tiled at 1× speed (4 tiles per 28 s = seamless)
//   • Canvas walking figure: articulated 2-segment limbs, fixed Z-order
//   • DiceBear SVG head bobs with body
//   • Freezes at current pos when serviceRunning == false
// ─────────────────────────────────────────────────────────────────────────────

// ── Pastel palettes per scene ─────────────────────────────────────────────────

private data class ScenePalette(
    val sky1: Color, val sky2: Color,
    val groundA: Color, val groundB: Color,
    val silhouette: Color,
    val near1: Color, val near2: Color,
)

private val PAL_CITY    = ScenePalette(
    sky1 = Color(0xFFB4D4EE), sky2 = Color(0xFFCEE5F5),
    groundA = Color(0xFFA2BCC8), groundB = Color(0xFF7E9EAE),
    silhouette = Color(0xFF90AEC8),
    near1 = Color(0xFF6E9AB2), near2 = Color(0xFF5882A0),
)
private val PAL_PARK    = ScenePalette(
    sky1 = Color(0xFFA4DEB8), sky2 = Color(0xFFC4EDD0),
    groundA = Color(0xFF72C48A), groundB = Color(0xFF52A66C),
    silhouette = Color(0xFF5DAA76),
    near1 = Color(0xFF3E8C58), near2 = Color(0xFF68B480),
)
private val PAL_VOLCANO = ScenePalette(
    sky1 = Color(0xFFFFB894), sky2 = Color(0xFFFFD4B0),
    groundA = Color(0xFFBE7E6E), groundB = Color(0xFF9E6254),
    silhouette = Color(0xFFD08070),
    near1 = Color(0xFFE06060), near2 = Color(0xFFFF8870),
)
private val PAL_SPACE   = ScenePalette(
    sky1 = Color(0xFF68789E), sky2 = Color(0xFF8898BE),
    groundA = Color(0xFF505878), groundB = Color(0xFF383E5E),
    silhouette = Color(0xFFB0A0D8),
    near1 = Color(0xFF6878A8), near2 = Color(0xFF8898C0),
)

private val PALETTES = listOf(PAL_CITY, PAL_PARK, PAL_VOLCANO, PAL_SPACE)

// ── Layout fractions (height-relative) ───────────────────────────────────────

private const val GROUND_FRAC   = 0.80f
private const val LEG_FRAC      = 0.22f   // reduced from 0.28 — shorter legs
private const val TORSO_FRAC    = 0.22f
private const val HEAD_R_FRAC   = 0.10f   // head radius as fraction of card height
private const val PERSON_X_FRAC = 0.25f

// Derived (all fractions of height)
// hipFracY      = 0.80 - 0.22 = 0.58
// shoulderFracY = 0.58 - 0.22 = 0.36
// headCenterY   = 0.36 - 0.10 = 0.26
// headTopY      = 0.26 - 0.10 = 0.16
private const val HEAD_TOP_FRAC = 0.16f

// Skin tone is determined by skinToneForSeed() in AvatarFace.kt so the walking
// figure's body colour always matches the DiceBear head loaded with the same seed.

// ── Main composable ───────────────────────────────────────────────────────────

@Composable
fun WalkingSceneCard(
    avatarSeed:     String,
    serviceRunning: Boolean = true,
    cardHeight:     Dp      = 200.dp,
    fillHeight:     Boolean = false,
) {
    val inf = rememberInfiniteTransition(label = "walk_scene")
    val textMeasurer = rememberTextMeasurer()

    // Infinite animations always tick — never paused
    val scrollFracLive by inf.animateFloat(
        initialValue  = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(28_000, easing = LinearEasing)),
        label = "scroll",
    )
    val walkPhaseLive by inf.animateFloat(
        initialValue  = 0f, targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(560, easing = LinearEasing)),
        label = "cycle",
    )

    // ── Scroll/walk display state (frozen in place when stopped) ──────────────
    val serviceRunningState = rememberUpdatedState(serviceRunning)
    var scrollOffset by remember { mutableStateOf(0f) }   // keeps parallax continuous on resume
    var scrollFrac   by remember { mutableStateOf(0f) }
    var walkPhase    by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        snapshotFlow { Triple(serviceRunningState.value, scrollFracLive, walkPhaseLive) }
            .collect { (running: Boolean, s: Float, p: Float) ->
                if (running) {
                    scrollFrac = ((s + scrollOffset) % 1f + 1f) % 1f
                    walkPhase  = p
                }
            }
    }

    // ── Wind-down (stop) and wind-up (start) animations ───────────────────────
    val windDownAnim = remember { Animatable(0f) }
    val windUpAnim   = remember { Animatable(0f) }
    var windingDown  by remember { mutableStateOf(false) }
    var windingUp    by remember { mutableStateOf(false) }
    var showBubble   by remember { mutableStateOf(false) }

    LaunchedEffect(serviceRunning) {
        if (!serviceRunning) {
            // ── Stopping: wind limbs to rest, then show dream bubble after 1 s
            showBubble  = false
            windingDown = true
            windDownAnim.snapTo(walkPhase)
            windDownAnim.animateTo(0f, tween(700, easing = FastOutSlowInEasing))
            windingDown = false
            delay(1_000L)
            showBubble  = true
        } else {
            // ── Resuming: dismiss bubble, adjust scroll offset to resume seamlessly,
            //    then play one wind-up cycle before handing off to live animation.
            showBubble   = false
            scrollOffset = scrollFrac - scrollFracLive   // freeze-point continuity
            windingUp    = true
            windUpAnim.snapTo(0f)
            windUpAnim.animateTo(
                targetValue   = (2f * PI).toFloat(),
                animationSpec = tween(560, easing = LinearEasing),
            )
            windingUp = false
        }
    }

    // ── Effective animation values ────────────────────────────────────────────
    val effectiveWalkPhase: Float = when {
        serviceRunning && windingUp -> windUpAnim.value
        serviceRunning              -> walkPhase
        windingDown                 -> windDownAnim.value
        else                        -> 0f
    }
    val showZzz = showBubble

    Card(
        modifier  = Modifier
            .fillMaxWidth()
            .then(if (fillHeight) Modifier.fillMaxHeight() else Modifier.wrapContentHeight()),
        shape     = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (fillHeight) Modifier.fillMaxHeight() else Modifier.height(cardHeight)),
        ) {
            val cardW      = maxWidth
            val cardSizeH  = if (fillHeight) maxHeight else cardHeight
            val headDiamDp = cardSizeH * (HEAD_R_FRAC * 2f)
            val headHalfDp = headDiamDp / 2f
            val personXDp  = cardW * PERSON_X_FRAC
            val armColor   = remember(avatarSeed) { skinToneForSeed(avatarSeed) }

            // No bob — head is fixed at body top (byOffDp = 0)

            // 1 ── Parallax background
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawScene(scrollFrac)
            }

            // 2 ── Walking body (no head)
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawWalker(effectiveWalkPhase, serviceRunning, armColor, showZzz, textMeasurer)
            }

            // 3 ── DiceBear head — bobs with body, transparent background
            DiceBearAvatar(
                seed                  = avatarSeed,
                size                  = headDiamDp,
                showLoadingBackground = false,
                transparent           = true,
                modifier              = Modifier
                    .align(Alignment.TopStart)
                    .offset(
                        x = personXDp - headHalfDp,
                        // Push the DiceBear image down so the visible face
                        // sits flush against the neck (avatars have top padding)
                        y = cardSizeH * HEAD_TOP_FRAC + headDiamDp * 0.12f,
                    ),
            )
        }
    }
}

// ── Colour helpers ────────────────────────────────────────────────────────────

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t.coerceIn(0f, 1f)
private fun lerpColor(a: Color, b: Color, t: Float) = Color(
    red   = lerp(a.red,   b.red,   t),
    green = lerp(a.green, b.green, t),
    blue  = lerp(a.blue,  b.blue,  t),
)

private fun blendPalette(scrollFrac: Float): ScenePalette {
    val world = (scrollFrac * 4f).coerceIn(0f, 4f - 1e-6f)
    val idx   = world.toInt() % 4
    val frac  = world - floor(world)
    val p0    = PALETTES[idx]
    val p1    = PALETTES[(idx + 1) % 4]
    return ScenePalette(
        sky1       = lerpColor(p0.sky1,       p1.sky1,       frac),
        sky2       = lerpColor(p0.sky2,       p1.sky2,       frac),
        groundA    = lerpColor(p0.groundA,    p1.groundA,    frac),
        groundB    = lerpColor(p0.groundB,    p1.groundB,    frac),
        silhouette = lerpColor(p0.silhouette, p1.silhouette, frac),
        near1      = lerpColor(p0.near1,      p1.near1,      frac),
        near2      = lerpColor(p0.near2,      p1.near2,      frac),
    )
}

// ── Background scene ─────────────────────────────────────────────────────────

private fun DrawScope.drawScene(scrollFrac: Float) {
    val w       = size.width
    val h       = size.height
    val groundY = h * GROUND_FRAC
    val pal     = blendPalette(scrollFrac)

    // Sky
    drawRect(
        brush = Brush.verticalGradient(listOf(pal.sky1, pal.sky2), startY = 0f, endY = groundY),
        size  = Size(w, groundY),
    )
    // Ground strips
    drawRect(pal.groundA, topLeft = Offset(0f, groundY),             size = Size(w, h * 0.07f))
    drawRect(pal.groundB, topLeft = Offset(0f, groundY + h * 0.07f), size = Size(w, h * 0.20f))

    // Far silhouette — sine-wave, seamlessly tileable, scrolls at 0.5× speed
    drawSilhouetteWave(scrollFrac, w, h, groundY, pal.silhouette)

    // Near tiled objects (streetlamps, trees, volcano, craters) scroll at 1×
    val nearScroll = scrollFrac * w * 4f
    clipRect(0f, 0f, w, h) {
        val baseTile = floor(nearScroll / w).toInt()
        for (off in -1..5) {
            val tile     = baseTile + off
            val tileType = ((tile % 4) + 4) % 4
            val tileLeft = tile * w - nearScroll
            if (tileLeft > w * 1.1f || tileLeft + w < -w * 0.1f) continue
            drawNearTile(tileType, tileLeft, w, h, groundY, pal)
        }
    }

    // Space extras (stars, planet) — blend in when world ≈ scene 3 (Space)
    val world      = scrollFrac * 4f
    val spaceFrac  = when {
        world < 2.5f -> 0f
        world < 3.0f -> (world - 2.5f) / 0.5f
        world < 3.5f -> 1f
        world < 4.0f -> (4f - world) / 0.5f
        else         -> 0f
    }
    if (spaceFrac > 0.01f) {
        for (i in 0..22) {
            val sx = (i * 137.508f) % w
            val sy = h * ((i * 0.053f) % 0.58f)
            drawCircle(Color.White.copy(alpha = spaceFrac * (0.4f + (i % 3) * 0.2f)),
                radius = w * 0.005f + (i % 2) * w * 0.003f, center = Offset(sx, sy))
        }
        val px = w * 0.74f; val py = h * 0.22f; val pr = h * 0.09f
        drawCircle(pal.near1.copy(alpha = spaceFrac), radius = pr, center = Offset(px, py))
        drawOval(pal.near2.copy(alpha = spaceFrac * 0.55f),
            topLeft = Offset(px - pr * 1.8f, py - pr * 0.25f),
            size    = Size(pr * 3.6f, pr * 0.5f),
            style   = Stroke(width = w * 0.012f))
    }
}

/** Sine-wave far silhouette. All periods divide 4w evenly → zero seam. */
private fun DrawScope.drawSilhouetteWave(
    scrollFrac: Float, w: Float, h: Float, groundY: Float, color: Color,
) {
    // Far layer scrolls at 0.5× speed = 2w per 28 s; periods 2w, 1w, 0.5w all divide 2w
    val offset = scrollFrac * w * 2f
    val path   = Path()
    val steps  = 80
    path.moveTo(0f, groundY)
    for (i in 0..steps) {
        val sx  = i / steps.toFloat() * w
        val wx  = sx + offset
        val dY  = h * (
            0.13f * sin(2f * PI.toFloat() * wx / (w * 2f)) +
            0.07f * sin(2f * PI.toFloat() * wx / (w * 1f)) +
            0.035f* sin(2f * PI.toFloat() * wx / (w * 0.5f))
        ) * 0.45f + h * 0.10f
        if (i == 0) path.moveTo(sx, groundY - dY) else path.lineTo(sx, groundY - dY)
    }
    path.lineTo(w, groundY)
    path.close()
    drawPath(path, color.copy(alpha = 0.50f))
}

// ── Near tile dispatcher ──────────────────────────────────────────────────────

private fun DrawScope.drawNearTile(
    type: Int, left: Float, w: Float, h: Float, gY: Float, pal: ScenePalette,
) = when (type) {
    0 -> drawCityNear(left, w, h, gY, pal)
    1 -> drawParkNear(left, w, h, gY, pal)
    2 -> drawVolcanoNear(left, w, h, gY, pal)
    else -> drawSpaceNear(left, w, h, gY, pal)
}

// ── Near: City ────────────────────────────────────────────────────────────────

private fun DrawScope.drawCityNear(l: Float, w: Float, h: Float, gY: Float, pal: ScenePalette) {
    listOf(0.12f, 0.44f, 0.76f).forEach { rx ->
        val lx = l + rx * w
        if (lx < -w * 0.1f || lx > size.width + w * 0.1f) return@forEach
        drawLine(pal.near1, Offset(lx, gY - h * 0.22f), Offset(lx, gY), strokeWidth = w * 0.011f)
        drawLine(pal.near1, Offset(lx, gY - h * 0.22f), Offset(lx + w * 0.04f, gY - h * 0.22f),
            strokeWidth = w * 0.009f)
        drawCircle(Color(0xFFFFF0A0).copy(alpha = 0.75f), radius = w * 0.016f,
            center = Offset(lx + w * 0.04f, gY - h * 0.22f))
    }
    for (d in 0..6) {
        val dx = l + w * d / 7f + w * 0.02f
        if (dx < 0f || dx > size.width) continue
        drawRect(Color.White.copy(alpha = 0.20f),
            topLeft = Offset(dx, gY + h * 0.030f), size = Size(w * 0.055f, h * 0.012f))
    }
}

// ── Near: Park ────────────────────────────────────────────────────────────────

private fun DrawScope.drawParkNear(l: Float, w: Float, h: Float, gY: Float, pal: ScenePalette) {
    listOf(0.10f, 0.44f, 0.76f).forEach { rx ->
        val tx = l + rx * w
        if (tx < -w * 0.1f || tx > size.width + w * 0.1f) return@forEach
        drawRect(pal.near1.copy(alpha = 0.85f),
            topLeft = Offset(tx - w * 0.010f, gY - h * 0.22f), size = Size(w * 0.020f, h * 0.22f))
        drawCircle(pal.near2, radius = h * 0.095f, center = Offset(tx, gY - h * 0.25f))
        drawCircle(pal.near1, radius = h * 0.065f, center = Offset(tx - w * 0.018f, gY - h * 0.28f))
    }
    val fc = listOf(Color(0xFFFFB0C0), Color(0xFFFFF0A0), Color(0xFFD0C0FF))
    for (i in 0..9) {
        val fx = l + w * (i * 0.10f + 0.03f)
        if (fx < 0f || fx > size.width) continue
        drawCircle(fc[i % 3], radius = h * 0.013f, center = Offset(fx, gY + h * 0.016f))
    }
}

// ── Near: Volcano ─────────────────────────────────────────────────────────────

private fun DrawScope.drawVolcanoNear(l: Float, w: Float, h: Float, gY: Float, pal: ScenePalette) {
    val topX = l + w * 0.50f; val topY = gY * 0.28f; val bW = w * 0.46f
    val cone = Path().apply { moveTo(topX, topY); lineTo(topX + bW, gY); lineTo(topX - bW, gY); close() }
    drawPath(cone, pal.near1.copy(alpha = 0.85f))
    drawCircle(Color(0xFFFF6040).copy(alpha = 0.65f), radius = w * 0.038f, center = Offset(topX, topY))
    for (i in 0..2) drawCircle(Color.White.copy(alpha = 0.20f - i * 0.05f),
        radius = w * (0.04f + i * 0.035f),
        center = Offset(topX + i * w * 0.02f, topY - h * (0.06f + i * 0.06f)))
    listOf(0.08f to 0.9f, 0.55f to 1.0f, 0.82f to 0.75f).forEach { (rx, sc) ->
        val cx = l + rx * w; if (cx < 0f || cx > size.width + w) return@forEach
        val rr = w * 0.030f * sc
        drawCircle(pal.near2, radius = rr, center = Offset(cx, gY + rr * 0.4f))
    }
}

// ── Near: Space ───────────────────────────────────────────────────────────────

private fun DrawScope.drawSpaceNear(l: Float, w: Float, h: Float, gY: Float, pal: ScenePalette) {
    listOf(0.10f, 0.34f, 0.60f, 0.85f).forEach { rx ->
        val cx = l + rx * w; if (cx < 0f || cx > size.width + w) return@forEach
        val cr = w * 0.040f
        drawCircle(pal.near1.copy(alpha = 0.7f), radius = cr, center = Offset(cx, gY + cr * 0.25f))
        drawCircle(pal.near2.copy(alpha = 0.5f), radius = cr * 0.50f, center = Offset(cx + cr * 0.1f, gY))
    }
    val ax = l + w * 0.28f
    if (ax > 0f && ax < size.width + w * 0.1f) {
        drawLine(pal.near2, Offset(ax, gY), Offset(ax, gY - h * 0.22f), strokeWidth = w * 0.008f)
        drawLine(pal.near2, Offset(ax - w * 0.05f, gY - h * 0.14f), Offset(ax + w * 0.05f, gY - h * 0.14f),
            strokeWidth = w * 0.008f)
        drawCircle(Color(0xFF80FF80).copy(alpha = 0.8f), radius = w * 0.012f,
            center = Offset(ax, gY - h * 0.22f))
    }
}

// ── Walking figure ────────────────────────────────────────────────────────────
//
// Z-ORDER:
//   1. Back leg
//   2. Back arm  (screen-right, slightly darker skin)
//   3. Bag       (flush against body left side)
//   4. Body      (gradient pill: shirt top → pants bottom)
//   5. Front leg (drawn OVER body — appears in front)
//   6. Neck      (skin-tone bridge, covers head/body join)
//   7. Front arm (always topmost)
// Head is a Compose layer above Canvas.

private fun DrawScope.drawWalker(
    walkPhase: Float,
    running: Boolean,
    armColor: Color,
    showZzz: Boolean = false,
    textMeasurer: TextMeasurer? = null,
) {
    val w = size.width
    val h = size.height

    val pX        = w * PERSON_X_FRAC
    val groundY   = h * GROUND_FRAC
    val hipY      = h * (GROUND_FRAC - LEG_FRAC)
    val shoulderY = h * (GROUND_FRAC - LEG_FRAC - TORSO_FRAC)

    val torsoW    = w * 0.042f
    val segStroke = w * 0.038f
    val thighLen  = h * 0.085f
    val uArmLen   = h * 0.075f
    val fArmLen   = h * 0.068f

    // No vertical bob — pure walk swing only
    val swingA = sin(walkPhase).toFloat()
    val swingB = -swingA

    // Body: top at shoulderY = head bottom → seamlessly connected
    val bodyTopY   = shoulderY
    val bodyHeight = h * TORSO_FRAC * 1.20f

    // Colors
    val shirtColor = Color(0xFFB0C8E8)   // upper body
    val legColorFront = Color(0xFF7878B0)   // front (left) leg — normal
    val legColorBack  = Color(0xFF4B4B80)   // back (right) leg — clearly darker
    // Back arm: same skin tone, ~25% dimmer
    val armBackC   = Color(
        red   = armColor.red   * 0.78f,
        green = armColor.green * 0.78f,
        blue  = armColor.blue  * 0.78f,
    )

    // Arms attach at body-top edge, close to body (not floating wide)
    val lHipX = pX - torsoW * 0.55f;  val rHipX = pX + torsoW * 0.55f
    val lShX  = pX - torsoW * 0.92f   // front arm, just inside left body edge
    val rShX  = pX + torsoW * 0.92f   // back arm, just inside right body edge

    // Leg endpoints (no byOff)
    val lKneeX = lHipX + swingA * h * 0.038f; val lKneeY = hipY + thighLen
    val lFootX = lHipX + swingA * h * 0.075f; val lFootY = groundY
    val rKneeX = rHipX + swingB * h * 0.038f; val rKneeY = hipY + thighLen
    val rFootX = rHipX + swingB * h * 0.075f; val rFootY = groundY

    // Neck bottom — arms attach visibly below the neck rect
    val neckTopY    = shoulderY - h * 0.042f
    val neckBottomY = neckTopY + h * 0.048f + h * 0.055f    // lowered arm origin

    // Arm endpoints — origin at neck bottom
    val lElbowX = lShX + swingB * h * 0.028f; val lElbowY = neckBottomY + uArmLen
    val lHandX  = lShX + swingB * h * 0.055f; val lHandY  = lElbowY + fArmLen
    val rElbowX = rShX + swingA * h * 0.028f; val rElbowY = neckBottomY + uArmLen
    val rHandX  = rShX + swingA * h * 0.055f; val rHandY  = rElbowY + fArmLen

    // 2-segment limb helper
    fun DrawScope.seg(x0: Float, y0: Float, x1: Float, y1: Float,
                      x2: Float, y2: Float, color: Color, sw: Float) {
        val p = Path().apply { moveTo(x0, y0); lineTo(x1, y1); lineTo(x2, y2) }
        drawPath(p, color, style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(color, radius = sw * 0.50f, center = Offset(x1, y1))
    }

    // 1. Right leg — always behind, darker shade
    seg(rHipX, hipY, rKneeX, rKneeY, rFootX, rFootY, legColorBack, segStroke)

    // 2. Back arm — anchored at neck bottom, screen-right, always behind body
    seg(rShX, neckBottomY, rElbowX, rElbowY, rHandX, rHandY, armBackC, segStroke * 0.82f)

    // 3. Bag — flush against left side of body, near the top
    val bagW = torsoW * 1.20f
    drawRoundRect(
        color        = Color(0xFF7888A0),
        topLeft      = Offset(pX - torsoW - bagW, bodyTopY + bodyHeight * 0.02f),
        size         = Size(bagW, bodyHeight * 0.55f),
        cornerRadius = CornerRadius(w * 0.010f),
    )

    // 4. Body — hard split: top half shirt colour, bottom half pants colour (no blend)
    //    Top corners rounded, bottom corners square (shirt-to-pants join at waist)
    val bodyBrush = Brush.verticalGradient(
        colorStops = arrayOf(
            0.0f to shirtColor,
            0.5f to shirtColor,
            0.5f to legColorFront,
            1.0f to legColorFront,
        ),
        startY = bodyTopY,
        endY   = bodyTopY + bodyHeight,
    )
    val bodyLeft  = pX - torsoW
    val bodyRight = pX + torsoW
    val bodyBot   = bodyTopY + bodyHeight
    val cr        = torsoW              // top corner radius
    val crBot     = torsoW * 0.38f     // slight bottom corner radius
    val bodyPath  = Path().apply {
        moveTo(bodyLeft + cr, bodyTopY)
        lineTo(bodyRight - cr, bodyTopY)
        quadraticBezierTo(bodyRight, bodyTopY, bodyRight, bodyTopY + cr)
        lineTo(bodyRight, bodyBot - crBot)                           // bottom-right: slight curve
        quadraticBezierTo(bodyRight, bodyBot, bodyRight - crBot, bodyBot)
        lineTo(bodyLeft + crBot, bodyBot)
        quadraticBezierTo(bodyLeft, bodyBot, bodyLeft, bodyBot - crBot)
        lineTo(bodyLeft, bodyTopY + cr)                              // bottom-left: slight curve
        quadraticBezierTo(bodyLeft, bodyTopY, bodyLeft + cr, bodyTopY)
        close()
    }
    drawPath(bodyPath, brush = bodyBrush)

    // 5. Left leg — always in front, drawn over body
    seg(lHipX, hipY, lKneeX, lKneeY, lFootX, lFootY, legColorFront, segStroke)

    // 6. Neck — tall enough to cover any gap between head image and body top
    val neckW = torsoW * 0.65f
    drawRoundRect(
        color        = armColor,
        topLeft      = Offset(pX - neckW / 2f, neckTopY),
        size         = Size(neckW, h * 0.048f),
        cornerRadius = CornerRadius(neckW / 2f),
    )

    // 8. Dream cloud bubble — shown 1 s after fully stopped (no border, white cloud)
    //    Positioned clearly to the RIGHT of the character, well above and away from the head.
    if (showZzz && textMeasurer != null) {
        val zStyle   = TextStyle(fontSize = 12.sp, color = Color(0xFF222222))
        val measured = textMeasurer.measure("ZzZ", zStyle)
        val tw = measured.size.width.toFloat()
        val th = measured.size.height.toFloat()

        // Cloud anchor: clearly to the right of the character (right half of the card)
        val cloudCX = pX + w * 0.26f          // x ≈ 51 % of card width — well right of head
        val cloudCY = shoulderY - h * 0.10f   // above the shoulders
        val cr1     = tw * 0.52f              // main cloud blob radius

        val cc = Color.White
        // Main body blobs
        drawCircle(cc, radius = cr1,          center = Offset(cloudCX,              cloudCY))
        drawCircle(cc, radius = cr1 * 0.82f,  center = Offset(cloudCX + cr1 * 1.12f, cloudCY + cr1 * 0.12f))
        drawCircle(cc, radius = cr1 * 0.76f,  center = Offset(cloudCX - cr1 * 1.08f, cloudCY + cr1 * 0.18f))
        drawCircle(cc, radius = cr1 * 0.62f,  center = Offset(cloudCX + cr1 * 0.52f, cloudCY - cr1 * 0.62f))
        drawCircle(cc, radius = cr1 * 0.56f,  center = Offset(cloudCX - cr1 * 0.42f, cloudCY - cr1 * 0.58f))
        // Flat base fill
        drawRect(cc,
            topLeft = Offset(cloudCX - cr1 * 1.80f, cloudCY),
            size    = Size(cr1 * 4.0f, cr1 * 1.08f))
        // Dream trail: three circles arcing RIGHT from the head toward the cloud
        val t1R = cr1 * 0.28f
        val t2R = cr1 * 0.19f
        val t3R = cr1 * 0.12f
        drawCircle(cc, radius = t1R, center = Offset(pX + torsoW * 2.0f, neckTopY - h * 0.008f))
        drawCircle(cc, radius = t2R, center = Offset(pX + torsoW * 3.8f, neckTopY - h * 0.030f))
        drawCircle(cc, radius = t3R, center = Offset(pX + w * 0.18f,     neckTopY - h * 0.058f))
        // ZzZ text centred in cloud
        drawText(textMeasurer, "ZzZ",
            topLeft = Offset(cloudCX - tw / 2f, cloudCY - th / 2f + cr1 * 0.08f),
            style   = zStyle)
    }

    // 7. Front arm — anchored at neck bottom, always topmost
    seg(lShX, neckBottomY, lElbowX, lElbowY, lHandX, lHandY, armColor, segStroke * 0.82f)

    // 9. Handheld console — small landscape device in left hand, lighter gray
    val devW  = segStroke * 2.4f
    val devH  = segStroke * 1.1f
    val devTL = Offset(lHandX - devW * 0.5f, lHandY - devH * 0.5f)
    drawRoundRect(
        Color(0xFF686868),
        topLeft      = devTL,
        size         = Size(devW, devH),
        cornerRadius = CornerRadius(devH * 0.42f),
    )
}
