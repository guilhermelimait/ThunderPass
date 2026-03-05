package com.thunderpass.ui

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thunderpass.ui.theme.SpaceCyan
import com.thunderpass.ui.theme.VividPurple
import com.thunderpass.ui.theme.DarkOrange
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.pow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EncounterDetailScreen(
    encounterId: Long,
    onBack: () -> Unit,
    vm: HomeViewModel = viewModel(),
) {
    val allEncounters by vm.encounters.collectAsState()
    val item = remember(allEncounters, encounterId) {
        allEncounters.find { it.encounter.id == encounterId }
    }

    val configuration = LocalConfiguration.current
    val isLandscape   = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(item?.snapshot?.displayName ?: "SparkyUser", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        if (item == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            val enc      = item.encounter
            val snapshot = item.snapshot

            // Live reactive snapshot — re-emits when RA data is updated in Room.
            val peerSnapshotId = enc.peerSnapshotId ?: 0L
            val liveSnapshot by vm.observeSnapshotById(peerSnapshotId).collectAsState(initial = snapshot)

            // Auto-fetch peer RA data using the local user's API key when the peer
            // shared their retroUsername but data was never retrieved (e.g. they had
            // no key themselves during the BLE exchange).
            LaunchedEffect(peerSnapshotId) {
                val sn = snapshot
                if (sn != null && !sn.retroUsername.isNullOrBlank() && !sn.retroFetchAttempted) {
                    vm.refreshPeerRetro(peerSnapshotId, sn.retroUsername)
                }
            }

            if (isLandscape) {
                // ── Landscape: left column (user card + stats + RA) | right column (chips + friend) ──
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // ── Left column: profile card + info cards ───────────────
                    Column(
                        modifier            = Modifier
                            .weight(0.50f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        // Rounded card — strong gradient + 4 decorative rotated squares (+ stats)
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
                                        Triple(size.width * 0.92f,  size.width * 0.18f,  35f  to base * 2.0f),
                                        Triple(size.width * 1.10f,  size.width * 0.68f,  20f  to base * 1.55f),
                                        Triple(size.width * 0.50f,  size.width * 1.40f,  45f  to base * 1.80f),
                                        Triple(size.width * -0.05f, size.width * 0.52f, -15f  to base * 1.20f),
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
                            Column(
                                modifier            = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Row(
                                    verticalAlignment     = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    DiceBearAvatar(
                                        seed = snapshot?.avatarSeed?.takeIf { it.isNotBlank() }
                                            ?: snapshot?.rotatingId
                                            ?: enc.rotatingId,
                                        size = 72.dp,
                                    )
                                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Text(
                                            text       = snapshot?.displayName ?: "Unknown SparkyUser",
                                            style      = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color      = Color.White,
                                            maxLines   = 1,
                                            overflow   = TextOverflow.Ellipsis,
                                        )
                                        if (!snapshot?.greeting.isNullOrBlank()) {
                                            Text(
                                                text      = "\u201C${snapshot!!.greeting}\u201D",
                                                style     = MaterialTheme.typography.bodySmall,
                                                fontStyle = FontStyle.Italic,
                                                color     = Color.White.copy(alpha = 0.80f),
                                                maxLines  = 2,
                                                overflow  = TextOverflow.Ellipsis,
                                            )
                                        }
                                        if (snapshot == null) {
                                            Text(
                                                text  = "Profile exchange pending\u2026",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.White.copy(alpha = 0.60f),
                                            )
                                        }
                                    }
                                }
                                HorizontalDivider(color = Color.White.copy(alpha = 0.25f))
                                Row(
                                    modifier              = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                ) {
                                    TravelerStat("Volts",  snapshot?.peerVoltsTotal?.let { "%,d".format(it) } ?: "—", Color.White, Color.White.copy(alpha = 0.70f))
                                    TravelerStat("Badges", snapshot?.peerBadgesCount?.toString() ?: "—", Color.White, Color.White.copy(alpha = 0.70f))
                                    TravelerStat("Passes", snapshot?.peerPassesCount?.toString() ?: "—", Color.White, Color.White.copy(alpha = 0.70f))
                                    TravelerStat("Streak", snapshot?.peerStreakCount?.let { "${it}d" } ?: "—", Color.White, Color.White.copy(alpha = 0.70f))
                                }
                            }
                            // Friend toggle — top-right corner of the card
                            IconButton(
                                onClick  = { vm.toggleFriend(enc.id, enc.isFriend, liveSnapshot?.peerUserId) },
                                modifier = Modifier.align(Alignment.TopEnd),
                            ) {
                                Icon(
                                    imageVector        = if (enc.isFriend) Icons.Filled.Star else Icons.Filled.Add,
                                    contentDescription = if (enc.isFriend) "Remove Friend" else "Add Friend",
                                    tint               = Color.White,
                                )
                            }
                        }

                        // Info cards — Last Seen, Signal, Distance
                        val dateStr = remember(enc.seenAt) {
                            SimpleDateFormat("MMM\u00A0d\u00A0\u00B7\u00A0HH:mm", Locale.getDefault())
                                .format(Date(enc.seenAt))
                        }
                        val (proximity, rssiDesc) = rssiToProximity(enc.rssi)
                        val ageDays = ((System.currentTimeMillis() - enc.seenAt) / 86_400_000L).toInt()
                        val relativeDate = when {
                            ageDays == 0 -> "Today"
                            ageDays == 1 -> "Yesterday"
                            ageDays < 7  -> "$ageDays days ago"
                            else         -> SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(enc.seenAt))
                        }
                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ElevatedCard(
                                modifier  = Modifier.weight(1f),
                                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp),
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                                    Text(
                                        text  = "Last Seen",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        text       = dateStr,
                                        style      = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        text  = relativeDate,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.secondary,
                                    )
                                }
                            }
                            ElevatedCard(
                                modifier  = Modifier.weight(1f),
                                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp),
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                                    Text(
                                        text  = "Signal",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        text       = "${enc.rssi} dBm",
                                        style      = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        text  = rssiDesc,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.secondary,
                                    )
                                }
                            }
                            ElevatedCard(
                                modifier  = Modifier.weight(1f),
                                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp),
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                                    Text(
                                        text  = "Distance",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        text       = proximity.substringAfter("  ").trim(),
                                        style      = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        text  = proximity.substringBefore("  ").trim(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.secondary,
                                    )
                                }
                            }
                        }
                    }

                    // Amber gradient divider
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

                    // ── Right column: RetroAchievements ──────────────────────
                    Column(
                        modifier            = Modifier
                            .weight(0.50f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        // Ghost Score
                        if (snapshot?.ghostGame != null) {
                            GhostScoreCard(snapshot)
                        }

                        // RetroAchievements — use liveSnapshot to pick up RA data fetched after initial load
                        if (liveSnapshot != null || snapshot != null) {
                            RetroSparkCard(liveSnapshot ?: snapshot!!)
                        }
                    }
                }
            } else {
                // ── Portrait: user card left | encounter info right ───────────
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    // ── Top row: rounded user card (left) | chips + button (right) ──
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment     = Alignment.Top,
                    ) {
                        // ── LEFT: Card — strong gradient + 4 decorative rotated squares (+ stats) ──
                        Box(
                            modifier = Modifier
                                .weight(0.52f)
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
                                        Triple(size.width * 0.92f,  size.width * 0.18f,  35f  to base * 2.0f),
                                        Triple(size.width * 1.10f,  size.width * 0.68f,  20f  to base * 1.55f),
                                        Triple(size.width * 0.50f,  size.width * 1.40f,  45f  to base * 1.80f),
                                        Triple(size.width * -0.05f, size.width * 0.52f, -15f  to base * 1.20f),
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
                            Column(
                                modifier            = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Row(
                                    verticalAlignment     = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    DiceBearAvatar(
                                        seed = snapshot?.avatarSeed?.takeIf { it.isNotBlank() }
                                            ?: snapshot?.rotatingId
                                            ?: enc.rotatingId,
                                        size = 56.dp,
                                    )
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            text       = snapshot?.displayName ?: "Unknown SparkyUser",
                                            style      = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color      = Color.White,
                                            maxLines   = 1,
                                            overflow   = TextOverflow.Ellipsis,
                                        )
                                        if (!snapshot?.greeting.isNullOrBlank()) {
                                            Text(
                                                text      = "\u201C${snapshot!!.greeting}\u201D",
                                                style     = MaterialTheme.typography.bodySmall,
                                                fontStyle = FontStyle.Italic,
                                                color     = Color.White.copy(alpha = 0.80f),
                                                maxLines  = 2,
                                                overflow  = TextOverflow.Ellipsis,
                                            )
                                        }
                                        if (snapshot == null) {
                                            Text(
                                                text  = "Profile exchange pending\u2026",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.White.copy(alpha = 0.60f),
                                            )
                                        }
                                    }
                                }
                                HorizontalDivider(color = Color.White.copy(alpha = 0.25f))
                                Row(
                                    modifier              = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                ) {
                                    TravelerStat("Volts",  snapshot?.peerVoltsTotal?.let { "%,d".format(it) } ?: "—", Color.White, Color.White.copy(alpha = 0.70f))
                                    TravelerStat("Badges", snapshot?.peerBadgesCount?.toString() ?: "—", Color.White, Color.White.copy(alpha = 0.70f))
                                    TravelerStat("Passes", snapshot?.peerPassesCount?.toString() ?: "—", Color.White, Color.White.copy(alpha = 0.70f))
                                    TravelerStat("Streak", snapshot?.peerStreakCount?.let { "${it}d" } ?: "—", Color.White, Color.White.copy(alpha = 0.70f))
                                }
                            }
                            // Friend toggle — top-right corner of the card
                            IconButton(
                                onClick  = { vm.toggleFriend(enc.id, enc.isFriend, liveSnapshot?.peerUserId) },
                                modifier = Modifier.align(Alignment.TopEnd),
                            ) {
                                Icon(
                                    imageVector        = if (enc.isFriend) Icons.Filled.Star else Icons.Filled.Add,
                                    contentDescription = if (enc.isFriend) "Remove Friend" else "Add Friend",
                                    tint               = Color.White,
                                )
                            }
                        }

                        // ── RIGHT: all encounter chips on one scrollable line + friend button ──
                        Column(
                            modifier            = Modifier.weight(0.48f),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            val dateStr = remember(enc.seenAt) {
                                SimpleDateFormat("MMM\u00A0d\u00A0\u00B7\u00A0HH:mm", Locale.getDefault())
                                    .format(Date(enc.seenAt))
                            }
                            val (proximity, rssiDesc) = rssiToProximity(enc.rssi)

                            val ageDays = ((System.currentTimeMillis() - enc.seenAt) / 86_400_000L).toInt()
                            val relativeDate = when {
                                ageDays == 0 -> "Today"
                                ageDays == 1 -> "Yesterday"
                                ageDays < 7  -> "$ageDays days ago"
                                else         -> SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(enc.seenAt))
                            }
                            // Info cards — side by side
                            Row(
                                modifier              = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                ElevatedCard(
                                    modifier  = Modifier.weight(1f),
                                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp),
                                ) {
                                    Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                                        Text(
                                            text  = "Last Seen",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Text(
                                            text       = dateStr,
                                            style      = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        Text(
                                            text  = relativeDate,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.secondary,
                                        )
                                    }
                                }
                                ElevatedCard(
                                    modifier  = Modifier.weight(1f),
                                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp),
                                ) {
                                    Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                                        Text(
                                            text  = "Signal",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Text(
                                            text       = "${enc.rssi} dBm",
                                            style      = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        Text(
                                            text  = rssiDesc,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.secondary,
                                        )
                                    }
                                }
                                ElevatedCard(
                                    modifier  = Modifier.weight(1f),
                                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp),
                                ) {
                                    Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                                        Text(
                                            text  = "Distance",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Text(
                                            text       = proximity.substringAfter("  ").trim(),
                                            style      = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        Text(
                                            text  = proximity.substringBefore("  ").trim(),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.secondary,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // ── Ghost score ───────────────────────────────────────────
                    if (snapshot?.ghostGame != null) {
                        GhostScoreCard(snapshot)
                    }

                    // ── RA profile — full width below the stats ───────────────
                    if (liveSnapshot != null || snapshot != null) {
                        RetroSparkCard(liveSnapshot ?: snapshot!!)
                    }
                }
            }
        }
    }
}

@Composable
private fun TravelerStat(
    label:      String,
    value:      String,
    valueColor: Color = Color.Unspecified,
    labelColor: Color = Color.Unspecified,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text       = value,
            style      = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color      = if (valueColor != Color.Unspecified) valueColor else MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text  = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (labelColor != Color.Unspecified) labelColor else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun GhostScoreCard(snapshot: com.thunderpass.data.db.entity.PeerProfileSnapshot) {
    val game  = snapshot.ghostGame  ?: return
    val score = snapshot.ghostScore
    Card(
        modifier  = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        colors    = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Row(
            modifier            = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment   = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("👻", style = MaterialTheme.typography.headlineMedium)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text  = game,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                if (score != null && score > 0L) {
                    Text(
                        text  = "Score: $score",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
            Text(
                text  = "beat it!",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}

/**
 * Rough RSSI → human-readable proximity.
 * Uses the Log-distance path loss model: distance = 10^((TxPower - RSSI) / (10 * n))
 * TxPower ≈ -59 dBm at 1 m, n ≈ 2.5 (indoor).
 */
private fun rssiToProximity(rssi: Int): Pair<String, String> {
    val txPower = -59.0
    val n = 2.5
    val distance = 10.0.pow((txPower - rssi) / (10.0 * n))
    val proximity = when {
        distance < 0.5 -> "Very close  (<\u00A00.5\u00A0m)"
        distance < 2.0 -> "Nearby  (~${distance.toInt()}\u00A0m)"
        distance < 8.0 -> "Moderate  (~${distance.toInt()}\u00A0m)"
        else           -> "Far  (~${distance.toInt()}\u00A0m)"
    }
    val desc = when {
        rssi >= -60  -> "Excellent"
        rssi >= -75  -> "Good"
        rssi >= -90  -> "Fair"
        else         -> "Weak"
    }
    return proximity to desc
}
