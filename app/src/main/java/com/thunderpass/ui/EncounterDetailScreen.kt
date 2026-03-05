package com.thunderpass.ui

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
                title = { Text(item?.snapshot?.displayName ?: "Traveler") },
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

            if (isLandscape) {
                // ── Landscape: profile card left | encounter info right ────────
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                ) {
                    // ── Left panel: traveler profile card ─────────────────────
                    Box(
                        modifier = Modifier
                            .weight(0.48f)
                            .fillMaxHeight()
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primaryContainer,
                                        MaterialTheme.colorScheme.surface,
                                    ),
                                ),
                            ),
                    ) {
                        Column(
                            modifier            = Modifier
                                .fillMaxSize()
                                .padding(20.dp)
                                .verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            // Avatar — placeholder background circle (banner image TBD)
                            Box(
                                modifier         = Modifier
                                    .size(100.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center,
                            ) {
                                DiceBearAvatar(
                                    seed = snapshot?.avatarSeed?.takeIf { it.isNotBlank() }
                                        ?: snapshot?.rotatingId
                                        ?: enc.rotatingId,
                                    size = 100.dp,
                                )
                            }

                            // Name
                            Text(
                                text       = snapshot?.displayName ?: "Unknown Traveler",
                                style      = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                textAlign  = TextAlign.Center,
                            )

                            // Greeting / message
                            if (!snapshot?.greeting.isNullOrBlank()) {
                                Text(
                                    text      = "\u201C${snapshot!!.greeting}\u201D",
                                    style     = MaterialTheme.typography.bodyMedium,
                                    fontStyle = FontStyle.Italic,
                                    color     = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                )
                            }

                            if (snapshot == null) {
                                Text(
                                    text  = "Profile exchange pending\u2026",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline,
                                )
                            }

                            Spacer(Modifier.height(4.dp))
                            HorizontalDivider(modifier = Modifier.fillMaxWidth(0.6f))
                            Spacer(Modifier.height(4.dp))

                            // Stat row: Volts · Badges · Passes · Streak
                            Row(
                                modifier              = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                            ) {
                                TravelerStat(
                                    label = "Volts",
                                    value = snapshot?.peerVoltsTotal?.let { "%,d".format(it) } ?: "\u2014",
                                )
                                TravelerStat(
                                    label = "Badges",
                                    value = snapshot?.peerBadgesCount?.toString() ?: "\u2014",
                                )
                                TravelerStat(
                                    label = "Passes",
                                    value = snapshot?.peerPassesCount?.toString() ?: "\u2014",
                                )
                                TravelerStat(
                                    label = "Streak",
                                    value = snapshot?.peerStreakCount?.let { "${it}d" } ?: "\u2014",
                                )
                            }

                            Spacer(Modifier.height(4.dp))

                            // Add / Remove Friend button — inside the left card
                            val isFriend = enc.isFriend
                            FilledTonalButton(
                                onClick = { vm.toggleFriend(enc.id, isFriend) },
                                modifier = Modifier.fillMaxWidth(0.85f),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = if (isFriend)
                                        MaterialTheme.colorScheme.secondaryContainer
                                    else
                                        MaterialTheme.colorScheme.primaryContainer,
                                ),
                            ) {
                                Icon(
                                    imageVector        = if (isFriend) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                    contentDescription = null,
                                    modifier           = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(if (isFriend) "Remove Friend" else "Add Friend")
                            }
                        }
                    }

                    VerticalDivider()

                    // ── Right panel: encounter metadata ───────────────────────
                    Column(
                        modifier = Modifier
                            .weight(0.52f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text  = "ENCOUNTER INFO",
                            style = MaterialTheme.typography.labelSmall.copy(
                                letterSpacing = 1.5.sp,
                                fontWeight    = FontWeight.Bold,
                            ),
                            color = MaterialTheme.colorScheme.primary,
                        )

                        val dateStr = remember(enc.seenAt) {
                            SimpleDateFormat(
                                "EEEE, MMM d \u00B7 HH:mm",
                                Locale.getDefault(),
                            ).format(Date(enc.seenAt))
                        }
                        MetaCard(label = "Encountered on",    value = dateStr)

                        val (proximity, rssiDesc) = rssiToProximity(enc.rssi)
                        MetaCard(label = "Signal strength",   value = "$rssiDesc  (${enc.rssi}\u00A0dBm)")
                        MetaCard(label = "Estimated distance", value = proximity)

                        // Ghost Score
                        if (snapshot?.ghostGame != null) {
                            HorizontalDivider()
                            GhostScoreCard(snapshot)
                        }
                        // RetroAchievements
                        if (snapshot?.retroUsername != null) {
                            HorizontalDivider()
                            RetroSparkCard(snapshot)
                        }
                    }
                }
            } else {
                // ── Portrait: full-width user card + chips + info below ────────
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // ── Full-width card: avatar+name+greeting (left) | encounter info (right) ──
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape    = RoundedCornerShape(16.dp),
                        colors   = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f),
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    ) {
                        Row(
                            modifier              = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment     = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            // ── LEFT: avatar + name + greeting ────────────────────
                            Row(
                                modifier             = Modifier.weight(0.52f),
                                verticalAlignment    = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                DiceBearAvatar(
                                    seed = snapshot?.avatarSeed?.takeIf { it.isNotBlank() }
                                        ?: snapshot?.rotatingId
                                        ?: enc.rotatingId,
                                    size = 60.dp,
                                )
                                Column {
                                    Text(
                                        text       = snapshot?.displayName ?: "Unknown Traveler",
                                        style      = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        maxLines   = 1,
                                        overflow   = TextOverflow.Ellipsis,
                                    )
                                    if (!snapshot?.greeting.isNullOrBlank()) {
                                        Text(
                                            text      = "\u201C${snapshot!!.greeting}\u201D",
                                            style     = MaterialTheme.typography.bodySmall,
                                            fontStyle = FontStyle.Italic,
                                            color     = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines  = 2,
                                            overflow  = TextOverflow.Ellipsis,
                                        )
                                    }
                                    if (snapshot == null) {
                                        Text(
                                            text  = "Profile exchange pending\u2026",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline,
                                        )
                                    }
                                }
                            }

                            // ── RIGHT: encounter chips in a flow row + friend button ──
                            Column(
                                modifier            = Modifier.weight(0.48f),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                val dateStr = remember(enc.seenAt) {
                                    SimpleDateFormat("MMM d \u00B7 HH:mm", Locale.getDefault())
                                        .format(Date(enc.seenAt))
                                }
                                val (proximity, rssiDesc) = rssiToProximity(enc.rssi)

                                @OptIn(ExperimentalLayoutApi::class)
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalArrangement   = Arrangement.spacedBy(4.dp),
                                ) {
                                    SuggestionChip(
                                        onClick = {},
                                        label   = { Text(dateStr, style = MaterialTheme.typography.labelSmall) },
                                    )
                                    SuggestionChip(
                                        onClick = {},
                                        label   = { Text("${enc.rssi}\u00A0dBm \u00B7 $rssiDesc", style = MaterialTheme.typography.labelSmall) },
                                    )
                                    SuggestionChip(
                                        onClick = {},
                                        label   = { Text(proximity, style = MaterialTheme.typography.labelSmall) },
                                    )
                                }

                                val isFriend = enc.isFriend
                                FilledTonalButton(
                                    onClick  = { vm.toggleFriend(enc.id, isFriend) },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors   = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = if (isFriend)
                                            MaterialTheme.colorScheme.secondaryContainer
                                        else
                                            MaterialTheme.colorScheme.primaryContainer,
                                    ),
                                ) {
                                    Icon(
                                        imageVector        = if (isFriend) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                        contentDescription = null,
                                        modifier           = Modifier.size(16.dp),
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text  = if (isFriend) "Remove Friend" else "Add Friend",
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                }
                            }
                        }
                    }

                    // RetroAchievements — shown right below the user card when available
                    if (snapshot?.retroUsername != null) {
                        RetroSparkCard(snapshot)
                    }

                    // Ghost score
                    if (snapshot?.ghostGame != null) {
                        GhostScoreCard(snapshot)
                    }

                    // Stats row — always shown; values hidden behind "—" when peer didn't share
                    HorizontalDivider()
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        TravelerStat(
                            label = "Volts",
                            value = snapshot?.peerVoltsTotal?.let { "%,d".format(it) } ?: "\u2014",
                        )
                        TravelerStat(
                            label = "Badges",
                            value = snapshot?.peerBadgesCount?.toString() ?: "\u2014",
                        )
                        TravelerStat(
                            label = "Passes",
                            value = snapshot?.peerPassesCount?.toString() ?: "\u2014",
                        )
                        TravelerStat(
                            label = "Streak",
                            value = snapshot?.peerStreakCount?.let { "${it}d" } ?: "\u2014",
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TravelerStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text       = value,
            style      = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text  = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MetaCard(label: String, value: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun GhostScoreCard(snapshot: com.thunderpass.data.db.entity.PeerProfileSnapshot) {
    val game  = snapshot.ghostGame  ?: return
    val score = snapshot.ghostScore
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(
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
