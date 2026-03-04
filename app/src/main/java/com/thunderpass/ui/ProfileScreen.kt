package com.thunderpass.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.thunderpass.retro.RetroAuthManager
import com.thunderpass.ui.theme.SpaceCyan
import com.thunderpass.ui.theme.VividPurple
import java.util.UUID
import androidx.compose.ui.tooling.preview.Preview
import com.thunderpass.data.db.entity.MyProfile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    firstRun:            Boolean       = false,
    onComplete:          (() -> Unit)? = null,
    onBack:              (() -> Unit)? = null,
    vm:                  ProfileViewModel = viewModel(),
    homeVm:              HomeViewModel    = viewModel(),
) {
    val profile by vm.profile.collectAsState()
    val encounterCount by homeVm.encounterCount.collectAsState()
    val streak         by homeVm.encounterStreak.collectAsState()
    val voltsTotal     by homeVm.voltsTotal.collectAsState()

    ProfileScreenContent(
        profile = profile,
        encounterCount = encounterCount,
        streak = streak,
        voltsTotal = voltsTotal,
        firstRun = firstRun,
        onSave = { name, retroUser, retroKey, seed ->
            vm.save(name, retroUser, avatarSeed = seed)
            // RetroAuthManager logic...
        },
        onAvatarSeedChange = { vm.saveAvatarSeed(it) },
        onComplete = onComplete,
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreenContent(
    profile: MyProfile,
    encounterCount: Int,
    streak: Int,
    voltsTotal: Long,
    firstRun: Boolean = false,
    onSave: (String, String, String, String) -> Unit = { _, _, _, _ -> },
    onAvatarSeedChange: ((String) -> Unit)? = null,
    onComplete: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
) {
    var draftName          by remember(profile.displayName)   { mutableStateOf(profile.displayName) }
    var draftRetroUsername by remember(profile.retroUsername) { mutableStateOf(profile.retroUsername) }
    var draftRaApiKey      by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf(false) }

    var avatarSeed by remember(profile.avatarSeed.ifEmpty { profile.installationId }) {
        mutableStateOf(profile.avatarSeed.ifEmpty { profile.installationId })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (firstRun) "Set Up Profile" else "My Profile") },
                navigationIcon = {
                    if (!firstRun && onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                )
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (firstRun) {
                Card(
                    colors   = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                ) {
                    Text(
                        text     = "Welcome to ThunderPass! \uD83D\uDC4B\nSet up your profile to get started.",
                        style    = MaterialTheme.typography.bodyMedium,
                        color    = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }

            // ── Hero: gradient banner + overlapping avatar + randomize ──────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
            ) {
                // Gradient banner
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .background(Brush.linearGradient(listOf(VividPurple, SpaceCyan))),
                )
                // Large avatar centered, overlapping the banner bottom
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .align(Alignment.BottomCenter)
                        .clip(CircleShape)
                        .border(3.dp, MaterialTheme.colorScheme.background, CircleShape),
                ) {
                    DiceBearAvatar(
                        seed = avatarSeed.ifEmpty { profile.installationId },
                        size = 100.dp,
                    )
                }
                // Shuffle / randomize avatar button (top-right corner of banner)
                IconButton(
                    onClick = {
                        val newSeed = UUID.randomUUID().toString()
                        avatarSeed = newSeed
                        onAvatarSeedChange?.invoke(newSeed)
                        saved = false
                    },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                ) {
                    Icon(
                        imageVector        = Icons.Filled.Refresh,
                        contentDescription = "Randomize avatar",
                        tint               = Color.White,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Avatar seed picker ─────────────────────────────────────────
            AvatarSeedPicker(
                currentSeed = avatarSeed,
                onSeedSelected = { seed ->
                    avatarSeed = seed
                    onAvatarSeedChange?.invoke(seed)
                    saved = false
                },
            )

            Spacer(Modifier.height(4.dp))
            Row(
                modifier              = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                ProfileStatChip(icon = "⚡", value = voltsTotal.toString(),    label = "Volts")
                ProfileStatChip(icon = "🤝", value = encounterCount.toString(), label = "Passes")
                ProfileStatChip(icon = "🔥", value = streak.toString(),         label = "Streak")
            }

            Spacer(Modifier.height(8.dp))

            GamePlayStatsCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )

            Spacer(Modifier.height(8.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // ── Badges — horizontal gallery, achieved only, highest tier first ─
                val achievedBadges = remember(Unit) {
                    ALL_BADGES.filter { it.tier > 0 }.sortedByDescending { it.tier }
                }
                if (achievedBadges.isNotEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text       = "BADGES",
                            style      = MaterialTheme.typography.labelSmall,
                            color      = MaterialTheme.colorScheme.outline,
                            fontFamily = FontFamily.Monospace,
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding        = PaddingValues(horizontal = 4.dp),
                        ) {
                            items(achievedBadges) { badge ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    ThunderShield(
                                        tier          = badge.tier,
                                        categoryColor = badge.category.accentColor,
                                        darkBg        = categoryDarkBg(badge.category, badge.tier),
                                        size          = 52.dp,
                                    )
                                    Text(
                                        text      = badge.label,
                                        style     = MaterialTheme.typography.labelSmall,
                                        color     = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines  = 2,
                                        overflow  = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center,
                                        modifier  = Modifier.width(60.dp),
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                OutlinedTextField(
                    value          = draftName,
                    onValueChange  = { draftName = it; saved = false },
                    label          = { Text("Display name") },
                    singleLine     = true,
                    modifier       = Modifier.fillMaxWidth(),
                    supportingText = { Text("Shown to nearby ThunderPass users") },
                )

                OutlinedTextField(
                    value          = draftRetroUsername,
                    onValueChange  = { draftRetroUsername = it; saved = false },
                    label          = { Text("\uD83C\uDFAE RetroAchievements Username") },
                    singleLine     = true,
                    modifier       = Modifier.fillMaxWidth(),
                    supportingText = { Text("Optional — share your RA stats on your Spark Card") },
                )

                OutlinedTextField(
                    value                = draftRaApiKey,
                    onValueChange        = { draftRaApiKey = it; saved = false },
                    label                = { Text("RA API Key") },
                    singleLine           = true,
                    modifier             = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                    supportingText       = { Text("From retroachievements.org/settings — kept on device") },
                )

                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick  = {
                        onSave(draftName, draftRetroUsername, draftRaApiKey, avatarSeed)
                        saved = true
                        if (firstRun) onComplete?.invoke()
                    },
                ) {
                    Text(
                        when {
                            firstRun -> "Start exploring!"
                            saved    -> "\u2713 Saved"
                            else     -> "Save Profile"
                        }
                    )
                }

                Spacer(Modifier.height(4.dp))

                // Share ID: displayname-slug + 4-char suffix from installationId for uniqueness.
                // Shows "private-user" when privacy mode is active.
                val shareId = remember(profile.displayName, profile.installationId, profile.privacyMode) {
                    if (profile.privacyMode) {
                        "private-user"
                    } else {
                        val suffix = profile.installationId.takeLast(4).lowercase()
                        val nameSlug = profile.displayName
                            .lowercase()
                            .replace(Regex("[^a-z0-9]+"), "-")
                            .trim('-')
                            .ifEmpty { "traveler" }
                        "$nameSlug-$suffix"
                    }
                }
                Text(
                    text  = "Share ID",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
                Text(
                    text  = shareId,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    fontFamily = FontFamily.Monospace,
                )
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun ProfileStatChip(icon: String, value: String, label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text  = icon,
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text       = value,
            style      = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color      = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text  = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ProfileScreenPreview() {
    MaterialTheme {
        ProfileScreenContent(
            profile = MyProfile(
                installationId = "test-id",
                displayName = "Gui",
                greeting = "Hey there!",
                voltsTotal = 2500
            ),
            encounterCount = 12,
            streak = 3,
            voltsTotal = 2500
        )
    }
}

// ── Avatar Seed Picker ────────────────────────────────────────────────────────────

/**
 * Horizontally-scrollable strip of avatar candidates.
 * - Shows 5 alternative seeds + the currently-selected one (always visible first).
 * - Tapping a candidate immediately selects it (calls [onSeedSelected]).
 * - The 🎲 button regenerates the 5 alternative candidates without changing
 *   the current selection.
 *
 * Because [onSeedSelected] is wired to [ProfileViewModel.saveAvatarSeed], the
 * walking animation and nav-bar icon update straight away.
 */
@Composable
fun AvatarSeedPicker(
    currentSeed:    String,
    onSeedSelected: (String) -> Unit,
    modifier:       Modifier = Modifier,
) {
    // 5 candidate seeds — regenerated when the dice button is tapped
    var candidates by remember { mutableStateOf(List(5) { UUID.randomUUID().toString() }) }

    // All tiles to display: current seed first, then the 5 candidates
    // (filter out the current seed from candidates so we never show a duplicate)
    val tiles = remember(currentSeed, candidates) {
        listOf(currentSeed) + candidates.filter { it != currentSeed }.take(5)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            verticalAlignment    = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            Text(
                text      = "AVATAR",
                style     = MaterialTheme.typography.labelSmall,
                color     = MaterialTheme.colorScheme.outline,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            )
            Spacer(Modifier.weight(1f))
            // Dice button — rolls 5 new candidate seeds
            FilledTonalIconButton(
                onClick  = { candidates = List(5) { UUID.randomUUID().toString() } },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector        = Icons.Filled.Casino,
                    contentDescription = "Roll new avatars",
                    modifier           = Modifier.size(18.dp),
                )
            }
        }

        LazyRow(
            contentPadding        = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(tiles) { seed ->
                val isSelected = seed == currentSeed
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .then(
                            if (isSelected) Modifier.border(
                                width = 3.dp,
                                color = MaterialTheme.colorScheme.primary,
                                shape = CircleShape,
                            ) else Modifier.border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant,
                                shape = CircleShape,
                            )
                        )
                        .clickable { onSeedSelected(seed) },
                    contentAlignment = Alignment.Center,
                ) {
                    DiceBearAvatar(
                        seed     = seed,
                        size     = 60.dp,
                        modifier = Modifier.padding(2.dp),
                    )
                }
            }
        }
    }
}
