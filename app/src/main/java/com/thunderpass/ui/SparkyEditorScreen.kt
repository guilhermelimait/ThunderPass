package com.thunderpass.ui

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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

    // ── Parse initial options from stored seed ──────────────────────────────
    val initial = remember(profile.avatarSeed) {
        if (profile.avatarSeed.startsWith("sparky|"))
            parseSparkyOptions(profile.avatarSeed)
        else SparkyOptions()
    }

    var hairIdx      by remember(initial) { mutableStateOf(initial.hair) }
    var hairColorIdx by remember(initial) { mutableStateOf(initial.hairColor) }
    var eyesIdx      by remember(initial) { mutableStateOf(initial.eyes) }
    var mouthIdx     by remember(initial) { mutableStateOf(initial.mouth) }
    var skinIdx      by remember(initial) { mutableStateOf(initial.skin) }
    var bgIdx        by remember(initial) { mutableStateOf(initial.bg) }

    // True once the user actually moves any slider
    var hasModified by remember { mutableStateOf(false) }
    // Also reset hasModified when profile seed reloads (so re-entering shows correct avatar)
    LaunchedEffect(profile.avatarSeed) { hasModified = false }

    // Live preview seed rebuilt from current selections
    val previewSeed by remember {
        derivedStateOf {
            buildSparkySeed(SparkyOptions(hairIdx, hairColorIdx, eyesIdx, mouthIdx, skinIdx, bgIdx))
        }
    }

    // Show the user's current profile avatar until they touch a slider.
    // For sparky seeds the sliders are already synced so previewSeed == profile seed.
    // For legacy non-sparky seeds this lets the user see their actual current avatar first.
    val displaySeed = when {
        hasModified                            -> previewSeed
        profile.avatarSeed.startsWith("sparky|") -> previewSeed   // sliders already match
        profile.avatarSeed.isNotEmpty()        -> profile.avatarSeed
        else                                   -> previewSeed
    }

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
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("✨", style = MaterialTheme.typography.titleLarge)
                        Text("Edit Sparky", fontWeight = FontWeight.Bold)
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
                    SparkyAvatarCard(seed = displaySeed, modifier = Modifier.fillMaxSize())
                }

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
                        onHair       = { hairIdx = it;      hasModified = true },
                        onHairColor  = { hairColorIdx = it; hasModified = true },
                        onEyes       = { eyesIdx = it;      hasModified = true },
                        onMouth      = { mouthIdx = it;     hasModified = true },
                        onSkin       = { skinIdx = it;      hasModified = true },
                        onBg         = { bgIdx = it;        hasModified = true },
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
                    SparkyAvatarCard(seed = displaySeed, modifier = Modifier.fillMaxSize())
                }

                SparkyAttributeSliders(
                    hairIdx      = hairIdx,
                    hairColorIdx = hairColorIdx,
                    eyesIdx      = eyesIdx,
                    mouthIdx     = mouthIdx,
                    skinIdx      = skinIdx,
                    bgIdx        = bgIdx,
                    onHair       = { hairIdx = it;      hasModified = true },
                    onHairColor  = { hairColorIdx = it; hasModified = true },
                    onEyes       = { eyesIdx = it;      hasModified = true },
                    onMouth      = { mouthIdx = it;     hasModified = true },
                    onSkin       = { skinIdx = it;      hasModified = true },
                    onBg         = { bgIdx = it;        hasModified = true },
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
private fun SparkyAvatarCard(seed: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape    = RoundedCornerShape(24.dp),
        colors   = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Box(
            modifier         = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            // Soft gradient backdrop inside the card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .align(Alignment.TopCenter)
                    .background(Brush.verticalGradient(listOf(VividPurple.copy(alpha = 0.3f), SpaceCyan.copy(alpha = 0f)))),
            )
            DiceBearAvatar(
                seed     = seed,
                size     = 160.dp,
                modifier = Modifier
                    .clip(CircleShape)
                    .border(4.dp, MaterialTheme.colorScheme.primaryContainer, CircleShape),
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
    onHair:       (Int) -> Unit,
    onHairColor:  (Int) -> Unit,
    onEyes:       (Int) -> Unit,
    onMouth:      (Int) -> Unit,
    onSkin:       (Int) -> Unit,
    onBg:         (Int) -> Unit,
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(12.dp),
        colors   = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        ),
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

            // Discrete slider
            Slider(
                value         = current.toFloat(),
                onValueChange = { onChange(it.roundToInt().coerceIn(0, maxIdx)) },
                valueRange    = 0f..maxIdx.toFloat(),
                steps         = if (maxIdx > 1) maxIdx - 1 else 0,
                colors        = SliderDefaults.colors(
                    thumbColor        = MaterialTheme.colorScheme.primary,
                    activeTrackColor  = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}


