package com.thunderpass.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.thunderpass.R

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
            // Earned summary pill
            val earned = ALL_BADGES.count { it.tier > 0 }
            val total  = ALL_BADGES.size
            val topTier = ALL_BADGES.maxOf { it.tier }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text  = "Your Collection",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    )
                    Text(
                        text       = "$earned / $total badges earned",
                        style      = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color      = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                ThunderShield(
                    tier          = topTier,
                    categoryColor = Color(0xFFFFB300),
                    darkBg        = Color(0xFF2D1E00),
                    size          = 44.dp,
                )
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
                                category = cat,
                                onClick  = { onNavigateToCategory(cat.name) },
                                modifier = Modifier.weight(1f),
                                compact  = true,
                            )
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            } else {
                BadgeCategory.entries.forEach { category ->
                    BadgeCategoryCard(
                        category = category,
                        onClick  = { onNavigateToCategory(category.name) },
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
    category: BadgeCategory,
    onClick:  () -> Unit,
    modifier: Modifier = Modifier,
    compact:  Boolean  = false,
) {
    val badges  = badgesForCategory(category)
    val earned  = badges.count { it.tier > 0 }
    val total   = badges.size
    val topTier = badges.maxOfOrNull { it.tier } ?: 0

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(if (compact) 84.dp else 110.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .drawBehind {
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(category.accentColor, category.gradientEnd),
                    ),
                )
                val cx = size.width * 0.82f
                val cy = size.height * 0.5f
                for (r in listOf(90f, 130f, 170f)) {
                    drawCircle(
                        color  = Color.White.copy(alpha = 0.08f),
                        radius = r,
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
                    text       = if (earned > 0) "$earned of $total earned" else "$total badges",
                    style      = MaterialTheme.typography.bodyMedium,
                    color      = Color.White.copy(alpha = 0.80f),
                    fontWeight = FontWeight.Medium,
                )
            }

            // Right side: for ENCOUNTERS show just the app logo; otherwise always show shield
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                if (category == BadgeCategory.ENCOUNTERS) {
                    // Encounters: logo only — no emoji, no shield
                    androidx.compose.foundation.Image(
                        painter            = painterResource(R.drawable.logo),
                        contentDescription = null,
                        modifier           = Modifier.size(if (compact) 40.dp else 52.dp),
                    )
                } else {
                    Text(
                        text  = category.emoji,
                        style = MaterialTheme.typography.displaySmall,
                    )
                    Spacer(Modifier.height(2.dp))
                    // Always show shield — grey when locked, colored when earned
                    ThunderShield(
                        tier          = topTier,
                        categoryColor = category.accentColor,
                        darkBg        = categoryDarkBg(category, topTier),
                        size          = 28.dp,
                    )
                }
            }
        }
    }
}
