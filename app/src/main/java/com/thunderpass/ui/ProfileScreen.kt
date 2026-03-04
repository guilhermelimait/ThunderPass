package com.thunderpass.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
    val joulesTotal    by homeVm.joulesTotal.collectAsState()

    ProfileScreenContent(
        profile = profile,
        encounterCount = encounterCount,
        streak = streak,
        joulesTotal = joulesTotal,
        firstRun = firstRun,
        onSave = { name, greeting, retroUser, retroKey ->
            vm.save(name, greeting, retroUser)
            // RetroAuthManager logic...
        },
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
    joulesTotal: Long,
    firstRun: Boolean = false,
    onSave: (String, String, String, String) -> Unit = { _, _, _, _ -> },
    onComplete: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
) {
    var draftName          by remember(profile.displayName)   { mutableStateOf(profile.displayName) }
    var draftGreeting      by remember(profile.greeting)      { mutableStateOf(profile.greeting) }
    var draftRetroUsername by remember(profile.retroUsername) { mutableStateOf(profile.retroUsername) }
    var draftRaApiKey      by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf(false) }

    var avatarSeed by remember(profile.installationId) { mutableStateOf(profile.installationId) }

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
                        avatarSeed = UUID.randomUUID().toString()
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

            // ── Stats row ───────────────────────────────────────────────────
            Row(
                modifier              = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                ProfileStatChip(icon = "⚡", value = joulesTotal.toString(),    label = "Joules")
                ProfileStatChip(icon = "🤝", value = encounterCount.toString(), label = "Passes")
                ProfileStatChip(icon = "🔥", value = streak.toString(),         label = "Streak")
            }

            Spacer(Modifier.height(8.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CompactVoltBadge(
                    joulesTotal = profile.joulesTotal,
                    modifier    = Modifier.fillMaxWidth(),
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // ── Badges ───────────────────────────────────────────────────
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text       = "BADGES",
                        style      = MaterialTheme.typography.labelSmall,
                        color      = MaterialTheme.colorScheme.outline,
                        fontFamily = FontFamily.Monospace,
                    )
                    RarityLegend()
                    BadgeShelf(modifier = Modifier.fillMaxWidth())
                    Text(
                        text  = "Unlock badges by playing, exploring, and connecting.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
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
                    value          = draftGreeting,
                    onValueChange  = { draftGreeting = it; saved = false },
                    label          = { Text("Greeting message") },
                    singleLine     = false,
                    minLines       = 2,
                    maxLines       = 4,
                    modifier       = Modifier.fillMaxWidth(),
                    supportingText = { Text("Exchanged when you meet someone") },
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

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
                        onSave(draftName, draftGreeting, draftRetroUsername, draftRaApiKey)
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
                Text(
                    text  = "Installation ID (never shared directly)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
                Text(
                    text  = profile.installationId.ifEmpty { "\u2014" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outlineVariant,
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
                joulesTotal = 2500
            ),
            encounterCount = 12,
            streak = 3,
            joulesTotal = 2500
        )
    }
}
