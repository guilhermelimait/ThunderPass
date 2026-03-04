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
import androidx.lifecycle.viewmodel.compose.viewModel

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
                    tierColor     = tierColor(topTier),
                    darkBg        = Color(0xFF2D1E00),
                    categoryColor = Color(0xFFFFD700),
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

            // Category cards
            BadgeCategory.entries.forEach { category ->
                BadgeCategoryCard(
                    category = category,
                    onClick  = { onNavigateToCategory(category.name) },
                )
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
) {
    val badges  = badgesForCategory(category)
    val earned  = badges.count { it.tier > 0 }
    val total   = badges.size
    val topTier = badges.maxOfOrNull { it.tier } ?: 0

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(110.dp)
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
                if (earned > 0 && topTier > 0) {
                    Spacer(Modifier.height(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(tierColor(topTier)),
                        )
                        Text(
                            text  = "Best: ${tierLabel(topTier)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.75f),
                        )
                    }
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text  = category.emoji,
                    style = MaterialTheme.typography.displaySmall,
                )
                if (earned > 0) {
                    Spacer(Modifier.height(2.dp))
                    ThunderShield(
                        tier          = topTier,
                        tierColor     = tierColor(topTier),
                        darkBg        = categoryDarkBg(category, topTier),
                        categoryColor = category.accentColor,
                        size          = 28.dp,
                    )
                }
            }
        }
    }
}
