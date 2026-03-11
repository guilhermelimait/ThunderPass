package com.thunderpass.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.SportsEsports
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
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.viewmodel.compose.viewModel
import com.thunderpass.ui.theme.SpaceCyan
import com.thunderpass.ui.theme.VividPurple

// ─────────────────────────────────────────────────────────────────────────────
// Badges screen — category grid (tap a card to see its badges)
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BadgesScreen(
    onNavigateToCategory: (String) -> Unit = {},
    onBack: () -> Unit = {},
    vm: HomeViewModel = viewModel(),
) {
    val earnedBadgeKeys by vm.earnedBadgeKeys.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Badges", fontWeight = FontWeight.Bold)
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
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // ── In-development notice ─────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors   = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                ),
                shape = RoundedCornerShape(12.dp),
            ) {
                Row(
                    modifier              = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    Text("🚧", fontSize = 20.sp)
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text       = "In Development",
                            fontWeight = FontWeight.Bold,
                            style      = MaterialTheme.typography.bodyMedium,
                            color      = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        Text(
                            text  = "This section is coming soon. Stay tuned!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f),
                        )
                    }
                }
            }

            // Collection banner — gradient card with decorative rotated squares
            // earned/total use computeBadges so dynamically awarded badges are reflected.
            val earned  = computeBadges(earnedBadgeKeys).count { it.tier > 0 }
            val total   = ALL_BADGES.size

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(8.dp, RoundedCornerShape(16.dp))
                    .clip(RoundedCornerShape(16.dp))
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
                            Triple(size.width * 0.92f,  size.width * 0.18f,  35f to base * 2.0f),
                            Triple(size.width * 1.10f,  size.width * 0.68f,  20f to base * 1.55f),
                            Triple(size.width * 0.50f,  size.width * 1.40f,  45f to base * 1.80f),
                            Triple(size.width * -0.05f, size.width * 0.52f, -15f to base * 1.20f),
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
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text  = "YOUR COLLECTION",
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp),
                            color = Color.White.copy(alpha = 0.75f),
                        )
                        Text(
                            text       = "$earned / $total badges earned",
                            style      = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color      = Color.White,
                        )
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress   = { if (total > 0) earned.toFloat() / total else 0f },
                            modifier   = Modifier
                                .width(180.dp)
                                .height(5.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color      = Color.White,
                            trackColor = Color.White.copy(alpha = 0.25f),
                        )
                    }
                    Box(
                        modifier         = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector        = Icons.Filled.EmojiEvents,
                            contentDescription = null,
                            tint               = Color.White,
                            modifier           = Modifier.size(40.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            Text(
                text  = "CATEGORIES",
                style = MaterialTheme.typography.labelSmall.copy(
                    letterSpacing = 2.sp,
                    fontWeight    = FontWeight.SemiBold,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Category cards — 2-column grid in landscape, full-width in portrait
            val configuration = LocalConfiguration.current
            val isLandscape   = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            if (isLandscape) {
                BadgeCategory.entries.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { cat ->
                            BadgeCategoryCard(
                                category  = cat,
                                onClick   = { onNavigateToCategory(cat.name) },
                                modifier  = Modifier.weight(1f),
                                compact   = true,
                                earnedKeys = earnedBadgeKeys,
                            )
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            } else {
                BadgeCategory.entries.forEach { category ->
                    BadgeCategoryCard(
                        category   = category,
                        onClick    = { onNavigateToCategory(category.name) },
                        earnedKeys = earnedBadgeKeys,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Single category card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun BadgeCategoryCard(
    category:   BadgeCategory,
    onClick:    () -> Unit,
    modifier:   Modifier    = Modifier,
    compact:    Boolean     = false,
    earnedKeys: Set<String> = emptySet(),
) {
    val badges  = badgesForCategory(category, earnedKeys)
    val earned  = badges.count { it.tier > 0 }
    val total   = badges.size
    val topTier = badges.maxOfOrNull { it.tier } ?: 0

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(if (compact) 84.dp else 110.dp)
            .shadow(6.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .drawBehind {
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(category.accentColor, category.gradientEnd),
                    ),
                )
                // Align circle centre to the actual icon position:
                // row end-padding (24.dp) + half of icon box width (32.dp / 24.dp compact)
                val endPadPx   = 24.dp.toPx()
                val iconHalfPx = (if (compact) 24.dp else 32.dp).toPx()
                val cx = size.width - endPadPx - iconHalfPx
                val cy = size.height * 0.5f
                // Radii are proportional to card height so they look identical
                // on every screen size / density.
                for (ratio in listOf(0.65f, 0.90f, 1.15f)) {
                    drawCircle(
                        color  = Color.White.copy(alpha = 0.08f),
                        radius = size.height * ratio,
                        center = Offset(cx, cy),
                    )
                }
            },
    ) {
        Row(
            modifier          = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text          = category.label.uppercase(),
                    style         = MaterialTheme.typography.headlineSmall,
                    fontWeight    = FontWeight.ExtraBold,
                    color         = Color.White,
                    letterSpacing = 1.sp,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text       = "$earned of $total earned",
                    style      = MaterialTheme.typography.bodyMedium,
                    color      = Color.White.copy(alpha = 0.80f),
                    fontWeight = FontWeight.Medium,
                )
            }

            // Right side: white category icon centered in the circle area
            Box(
                contentAlignment = Alignment.Center,
                modifier         = Modifier.size(if (compact) 48.dp else 64.dp),
            ) {
                Icon(
                    imageVector        = categoryIcon(category),
                    contentDescription = category.label,
                    tint               = Color.White,
                    modifier           = Modifier.size(if (compact) 28.dp else 36.dp),
                )
            }
        }
    }
}

private fun categoryIcon(category: BadgeCategory): ImageVector = when (category) {
    BadgeCategory.ENCOUNTERS -> Icons.Filled.ElectricBolt
    BadgeCategory.CONSOLE    -> Icons.Filled.SportsEsports
    BadgeCategory.GEO        -> Icons.Filled.Map
    BadgeCategory.SOCIAL     -> Icons.Filled.Group
    BadgeCategory.GAMES      -> Icons.Filled.Gamepad
    BadgeCategory.FOUNDERS   -> Icons.Filled.EmojiEvents
}
