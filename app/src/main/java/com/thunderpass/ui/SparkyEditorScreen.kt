package com.thunderpass.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.thunderpass.ui.theme.SpaceCyan
import com.thunderpass.ui.theme.VividPurple
import kotlin.math.roundToInt

// ─────────────────────────────────────────────────────────────────────────────
// Sparky Editor Screen
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Full-featured avatar customisation screen ("Edit Sparky").
 * In portrait: scrollable single column with big avatar preview + sliders.
 * In landscape: avatar on the left (non-scrolling), sliders on the right (scrollable).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SparkyEditorScreen(
    onBack:  () -> Unit           = {},
    vm:      ProfileViewModel     = viewModel(),
) {
    val profile by vm.profile.collectAsState()

    // ── Slider state ────────────────────────────────────────────────────────
    // Initialised to SparkyOptions defaults; synced to the real profile below.
    // We intentionally do NOT read profile.avatarSeed here directly, because the
    // StateFlow may still hold its blank default (MyProfile(installationId=…)) on
    // the first composition frame — the actual DB row arrives slightly later.
    var hairIdx        by remember { mutableStateOf(SparkyOptions().hair) }
    var hairColorIdx   by remember { mutableStateOf(SparkyOptions().hairColor) }
    var eyesIdx        by remember { mutableStateOf(SparkyOptions().eyes) }
    var mouthIdx       by remember { mutableStateOf(SparkyOptions().mouth) }
    var skinIdx        by remember { mutableStateOf(SparkyOptions().skin) }
    var bgIdx          by remember { mutableStateOf(SparkyOptions().bg) }
    var accessoryIdx   by remember { mutableStateOf(SparkyOptions().accessory) }

    // True once the user actually moves any slider (stays false during initial sync).
    var hasModified by remember { mutableStateOf(false) }

    // Sync sliders from the profile seed whenever it changes.
    // Using vm.profile.collect (instead of LaunchedEffect keyed on the seed string)
    // guarantees the sync runs immediately on first subscription: StateFlow always
    // emits its latest cached value synchronously, so sliders are populated even
    // when the seed is already loaded by the time the screen opens.
    // The !hasModified guard prevents mid-edit Room saves from clobbering the
    // current slider positions.
    LaunchedEffect(Unit) {
        vm.profile.collect { p ->
            if (!hasModified) {
                when {
                    p.avatarSeed.startsWith("sparky|") -> {
                        // Saved sparky seed — decode and sync all sliders to match exactly.
                        val opts = parseSparkyOptions(p.avatarSeed)
                        hairIdx      = opts.hair
                        hairColorIdx = opts.hairColor
                        eyesIdx      = opts.eyes
                        mouthIdx     = opts.mouth
                        skinIdx      = opts.skin
                        bgIdx        = opts.bg
                        accessoryIdx = opts.accessory
                    }
                    p.avatarSeed.isNotEmpty() -> {
                        // Legacy UUID seed: derive slider positions deterministically,
                        // then immediately replace with a sparky seed so it's consistent
                        // from this point forward.
                        val opts = sparkyOptionsFromSeed(p.avatarSeed)
                        hairIdx      = opts.hair
                        hairColorIdx = opts.hairColor
                        eyesIdx      = opts.eyes
                        mouthIdx     = opts.mouth
                        skinIdx      = opts.skin
                        bgIdx        = opts.bg
                        accessoryIdx = opts.accessory
                        vm.saveAvatarSeed(buildSparkySeed(opts))
                    }
                    else -> {
                        // Empty seed (very first open before ViewModel init fires):
                        // generate a fresh random sparky seed and save it.
                        val seed = randomSparkySeed()
                        val opts = parseSparkyOptions(seed)
                        hairIdx      = opts.hair
                        hairColorIdx = opts.hairColor
                        eyesIdx      = opts.eyes
                        mouthIdx     = opts.mouth
                        skinIdx      = opts.skin
                        bgIdx        = opts.bg
                        accessoryIdx = opts.accessory
                        vm.saveAvatarSeed(seed)
                    }
                }
            }
        }
    }

    // Randomize all sliders to a fresh random sparky
    val onRandomize: () -> Unit = {
        val seed = randomSparkySeed()
        val opts = parseSparkyOptions(seed)
        hairIdx      = opts.hair
        hairColorIdx = opts.hairColor
        eyesIdx      = opts.eyes
        mouthIdx     = opts.mouth
        skinIdx      = opts.skin
        bgIdx        = opts.bg
        accessoryIdx = opts.accessory
        hasModified  = true
    }

    // Live preview seed rebuilt from current selections
    val previewSeed by remember {
        derivedStateOf {
            buildSparkySeed(SparkyOptions(hairIdx, hairColorIdx, eyesIdx, mouthIdx, skinIdx, bgIdx, accessoryIdx))
        }
    }

    // Preview shows the saved sparky avatar when the user hasn't modified anything yet.
    // If the stored seed is not a sparky seed (e.g. a plain UUID from first install),
    // we show previewSeed instead so the card always matches the sliders.
    val displaySeed = if (hasModified || !profile.avatarSeed.startsWith("sparky|")) previewSeed
                      else profile.avatarSeed

    // Auto-save: persist any change 400 ms after the user stops moving a slider
    LaunchedEffect(previewSeed) {
        if (hasModified) {
            kotlinx.coroutines.delay(400)
            vm.saveAvatarSeed(previewSeed)
        }
    }

    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Edit Sparky", fontWeight = FontWeight.Bold)
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
        if (isLandscape) {
            // ── Landscape: avatar left, sliders right ──────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                // LEFT — fixed avatar panel (no scroll)
                Box(
                    modifier          = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(16.dp),
                    contentAlignment  = Alignment.Center,
                ) {
                    SparkyAvatarCard(seed = displaySeed, onRandomize = onRandomize, modifier = Modifier.fillMaxSize())
                }

                // Amber gradient vertical divider — same style as HomeScreen landscape split
                Box(
                    modifier = Modifier
                        .fillMaxHeight(0.85f)
                        .width(3.dp)
                        .align(Alignment.CenterVertically)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFFFFB300).copy(alpha = 0.2f),
                                    Color(0xFFFFB300),
                                    Color(0xFFFF6F00),
                                    Color(0xFFFFB300).copy(alpha = 0.2f),
                                )
                            )
                        )
                )

                // RIGHT — scrollable attribute sliders
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    SparkyAttributeSliders(
                        hairIdx      = hairIdx,
                        hairColorIdx = hairColorIdx,
                        eyesIdx      = eyesIdx,
                        mouthIdx     = mouthIdx,
                        skinIdx      = skinIdx,
                        bgIdx        = bgIdx,
                        accessoryIdx = accessoryIdx,
                        onHair       = { hairIdx = it;       hasModified = true },
                        onHairColor  = { hairColorIdx = it;  hasModified = true },
                        onEyes       = { eyesIdx = it;       hasModified = true },
                        onMouth      = { mouthIdx = it;      hasModified = true },
                        onSkin       = { skinIdx = it;       hasModified = true },
                        onBg         = { bgIdx = it;         hasModified = true },
                        onAccessory  = { accessoryIdx = it;  hasModified = true },
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        } else {
            // ── Portrait: scrollable column ────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Spacer(Modifier.height(4.dp))

                // Avatar preview card
                Box(
                    modifier         = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    SparkyAvatarCard(seed = displaySeed, onRandomize = onRandomize, modifier = Modifier.fillMaxSize())
                }

                // Horizontal amber gradient separator — mirrors the landscape divider
                Box(
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFFFFB300).copy(alpha = 0.2f),
                                    Color(0xFFFFB300),
                                    Color(0xFFFF6F00),
                                    Color(0xFFFFB300).copy(alpha = 0.2f),
                                )
                            )
                        )
                )

                SparkyAttributeSliders(
                    hairIdx      = hairIdx,
                    hairColorIdx = hairColorIdx,
                    eyesIdx      = eyesIdx,
                    mouthIdx     = mouthIdx,
                    skinIdx      = skinIdx,
                    bgIdx        = bgIdx,
                    accessoryIdx = accessoryIdx,
                    onHair       = { hairIdx = it;       hasModified = true },
                    onHairColor  = { hairColorIdx = it;  hasModified = true },
                    onEyes       = { eyesIdx = it;       hasModified = true },
                    onMouth      = { mouthIdx = it;      hasModified = true },
                    onSkin       = { skinIdx = it;       hasModified = true },
                    onBg         = { bgIdx = it;         hasModified = true },
                    onAccessory  = { accessoryIdx = it;  hasModified = true },
                )

                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Avatar preview card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SparkyAvatarCard(seed: String, onRandomize: () -> Unit, modifier: Modifier = Modifier) {
    // Same geometric gradient banner used in the Badges header and Home user panel.
    // Decorated with semi-transparent rotated squares for depth.
    Box(
        modifier = modifier
            .shadow(8.dp, RoundedCornerShape(24.dp))
            .clip(RoundedCornerShape(24.dp))
            .drawBehind {
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(VividPurple, SpaceCyan),
                        start  = Offset(0f, 0f),
                        end    = Offset(size.width, size.height),
                    ),
                )
                val base = size.width * 0.32f
                val positions = listOf(
                    Triple(size.width * 0.92f,  size.width * 0.18f,   35f to base * 2.0f),
                    Triple(size.width * 1.10f,  size.width * 0.68f,   20f to base * 1.55f),
                    Triple(size.width * 0.50f,  size.width * 1.40f,   45f to base * 1.80f),
                    Triple(size.width * -0.05f, size.width * 0.52f,  -15f to base * 1.20f),
                )
                for ((cx, cy, rotAndSize) in positions) {
                    val (deg, sqSz) = rotAndSize
                    rotate(deg, Offset(cx, cy)) {
                        drawRect(
                            color   = Color.White.copy(alpha = 0.09f),
                            topLeft = Offset(cx - sqSz / 2, cy - sqSz / 2),
                            size    = Size(sqSz, sqSz),
                        )
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        // Avatar circle: clipped but no visible border — transparent against the gradient.
        DiceBearAvatar(
            seed     = seed,
            size     = 160.dp,
            modifier = Modifier.clip(CircleShape),
        )

        // Random button — top-right corner, matching ProfileScreen
        IconButton(
            onClick  = onRandomize,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp),
        ) {
            Icon(
                imageVector        = Icons.Filled.Casino,
                contentDescription = "Randomize Sparky",
                tint               = Color.White,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Attribute slider group
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SparkyAttributeSliders(
    hairIdx:      Int,
    hairColorIdx: Int,
    eyesIdx:      Int,
    mouthIdx:     Int,
    skinIdx:      Int,
    bgIdx:        Int,
    accessoryIdx: Int,
    onHair:       (Int) -> Unit,
    onHairColor:  (Int) -> Unit,
    onEyes:       (Int) -> Unit,
    onMouth:      (Int) -> Unit,
    onSkin:       (Int) -> Unit,
    onBg:         (Int) -> Unit,
    onAccessory:  (Int) -> Unit,
) {
    SparkySliderSection(
        title   = "💇 Hair Style",
        labels  = SPARKY_HAIR_LABELS,
        current = hairIdx,
        onChange = onHair,
    )
    SparkySliderSection(
        title   = "🎨 Hair Color",
        labels  = SPARKY_HAIR_COLOR_LABELS,
        current = hairColorIdx,
        onChange = onHairColor,
    )
    SparkySliderSection(
        title   = "👁️ Eyes",
        labels  = SPARKY_EYE_LABELS,
        current = eyesIdx,
        onChange = onEyes,
    )
    SparkySliderSection(
        title   = "😀 Mouth",
        labels  = SPARKY_MOUTH_LABELS,
        current = mouthIdx,
        onChange = onMouth,
    )
    SparkySliderSection(
        title   = "🧑 Skin Tone",
        labels  = SPARKY_SKIN_LABELS,
        current = skinIdx,
        onChange = onSkin,
    )
    SparkySliderSection(
        title   = "🌈 Background",
        labels  = SPARKY_BG_LABELS,
        current = bgIdx,
        onChange = onBg,
    )
    SparkySliderSection(
        title   = "🎭 Accessories",
        labels  = SPARKY_ACCESSORY_LABELS,
        current = accessoryIdx,
        onChange = onAccessory,
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Single attribute slider section
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SparkySliderSection(
    title:    String,
    labels:   List<String>,
    current:  Int,
    onChange: (Int) -> Unit,
) {
    val maxIdx = labels.size - 1
    ElevatedCard(
        modifier  = Modifier
            .fillMaxWidth()
            .shadow(6.dp, RoundedCornerShape(12.dp)),
        shape     = RoundedCornerShape(12.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Title + current selection
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Text(
                    text       = title,
                    style      = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color      = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text      = labels.getOrElse(current) { "" },
                    style     = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color     = MaterialTheme.colorScheme.primary,
                    fontFamily = FontFamily.Monospace,
                )
            }

            // Step counter beneath title
            Text(
                text  = "${current + 1} / ${labels.size}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )

            // Discrete slider — amber/golden palette matching the landscape divider
            Slider(
                value         = current.toFloat(),
                onValueChange = { onChange(it.roundToInt().coerceIn(0, maxIdx)) },
                valueRange    = 0f..maxIdx.toFloat(),
                steps         = if (maxIdx > 1) maxIdx - 1 else 0,
                colors        = SliderDefaults.colors(
                    thumbColor         = Color(0xFFFFB300),
                    activeTrackColor   = Color(0xFFFF6F00),
                    inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}


