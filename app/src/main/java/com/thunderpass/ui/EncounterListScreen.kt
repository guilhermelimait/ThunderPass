package com.thunderpass.ui

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EncounterListScreen(
    onNavigateToDetail: (Long) -> Unit = {},
    onBack: () -> Unit = {},
    vm: HomeViewModel = viewModel(),
) {
    val encounters by vm.encounters.collectAsState()
    val friends    by vm.friends.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("All Sparks", "Friends")

    val configuration = LocalConfiguration.current
    val isLandscape   = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Passes") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                )
            )
        },
    ) { padding ->
        if (isLandscape) {
            // ── Landscape: side-by-side panels ─────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                // Left — Sparks
                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    Text(
                        text       = "ALL SPARKS",
                        style      = MaterialTheme.typography.labelMedium.copy(
                            letterSpacing = 1.5.sp,
                            fontWeight    = FontWeight.Bold,
                        ),
                        color    = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                    HorizontalDivider()
                    if (encounters.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            EmptyEncountersState()
                        }
                    } else {
                        LazyColumn(
                            modifier        = Modifier.fillMaxSize(),
                            contentPadding  = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(encounters, key = { it.encounter.id }) { item ->
                                EncounterCard(
                                    item    = item,
                                    onClick = { onNavigateToDetail(item.encounter.id) },
                                    modifier = Modifier.animateItem(),
                                )
                            }
                        }
                    }
                }

                VerticalDivider()

                // Right — Friends
                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    Row(
                        modifier          = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector        = Icons.Filled.Star,
                            contentDescription = null,
                            tint               = MaterialTheme.colorScheme.primary,
                            modifier           = Modifier.size(14.dp),
                        )
                        Text(
                            text  = "FRIENDS",
                            style = MaterialTheme.typography.labelMedium.copy(
                                letterSpacing = 1.5.sp,
                                fontWeight    = FontWeight.Bold,
                            ),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    HorizontalDivider()
                    if (friends.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            EmptyFriendsState()
                        }
                    } else {
                        LazyColumn(
                            modifier        = Modifier.fillMaxSize(),
                            contentPadding  = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(friends, key = { it.encounter.id }) { item ->
                                EncounterCard(
                                    item    = item,
                                    onClick = { onNavigateToDetail(item.encounter.id) },
                                    modifier = Modifier.animateItem(),
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // ── Portrait: tabs ──────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor   = MaterialTheme.colorScheme.primary,
                    contentColor     = Color.White,
                ) {
                    tabs.forEachIndexed { index, label ->
                        Tab(
                            selected      = selectedTab == index,
                            onClick       = { selectedTab = index },
                            selectedContentColor   = Color.White,
                            unselectedContentColor = Color.White.copy(alpha = 0.65f),
                            text = { Text(label, fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal) },
                            icon = if (index == 1) ({
                                Icon(
                                    imageVector        = Icons.Filled.Star,
                                    contentDescription = null,
                                    modifier           = Modifier.size(16.dp),
                                )
                            }) else null,
                        )
                    }
                }

                val list = if (selectedTab == 0) encounters else friends

                if (list.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selectedTab == 0) EmptyEncountersState() else EmptyFriendsState()
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(list, key = { it.encounter.id }) { item ->
                            EncounterCard(
                                item    = item,
                                onClick = { onNavigateToDetail(item.encounter.id) },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyFriendsState() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(32.dp),
    ) {
        Text(
            text     = "\u2605",
            fontSize = 72.sp,
        )
        Text(
            text       = "No friends yet",
            style      = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color      = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text      = "Open an encounter and tap\n\"Add Friend\" to star it here.",
            style     = MaterialTheme.typography.bodyMedium,
            color     = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun EmptyEncountersState() {
    val infiniteTransition = rememberInfiniteTransition(label = "bolt-pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.82f,
        targetValue  = 1.18f,
        animationSpec = infiniteRepeatable(
            animation  = tween(950, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "scale",
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue  = 1f,
        animationSpec = infiniteRepeatable(
            animation  = tween(950, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "alpha",
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(32.dp),
    ) {
        Text(
            text     = "\u26A1",
            fontSize = 72.sp,
            modifier = Modifier.graphicsLayer {
                scaleX     = scale
                scaleY     = scale
                this.alpha = alpha
            },
        )
        Text(
            text       = "No Sparks yet",
            style      = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color      = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text      = "Head outside and let ThunderPass\ndetect other Travelers nearby.",
            style     = MaterialTheme.typography.bodyMedium,
            color     = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun EncounterCard(
    item: EncounterWithProfile,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val enc      = item.encounter
    val snapshot = item.snapshot
    val timeStr  = remember(enc.seenAt) { relativeTimeString(enc.seenAt) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Peer avatar — prefer their own avatarSeed so it matches what they see on
            // their device; fall back to rotatingId for pre-fix snapshots.
            DiceBearAvatar(
                seed = snapshot?.avatarSeed?.takeIf { it.isNotBlank() }
                    ?: snapshot?.rotatingId
                    ?: enc.rotatingId,
                size = 52.dp,
            )

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = snapshot?.displayName ?: "Unknown traveler",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                if (!snapshot?.greeting.isNullOrBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "\"${snapshot!!.greeting}\"",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "$timeStr  \u00B7  ${enc.rssi} dBm",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
                if (snapshot == null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Profile exchange pending\u2026",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }

            // Friend star indicator
            if (enc.isFriend) {
                Spacer(Modifier.width(8.dp))
                Icon(
                    imageVector        = Icons.Filled.Star,
                    contentDescription = "Friend",
                    tint               = MaterialTheme.colorScheme.primary,
                    modifier           = Modifier.size(20.dp),
                )
            }
        }
    }

}


