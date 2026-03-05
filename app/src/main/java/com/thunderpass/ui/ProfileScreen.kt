package com.thunderpass.ui

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.thunderpass.data.db.entity.MyProfile
import com.thunderpass.retro.RetroAuthManager
import com.thunderpass.ui.theme.SpaceCyan
import com.thunderpass.ui.theme.VividPurple

// ── Entry point ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    firstRun:     Boolean           = false,
    onComplete:   (() -> Unit)?     = null,
    onBack:       (() -> Unit)?     = null,
    onEditSparky: (() -> Unit)?     = null,
    vm:           ProfileViewModel  = viewModel(),
    homeVm:       HomeViewModel     = viewModel(),
) {
    val profile        by vm.profile.collectAsState()
    val encounterCount by homeVm.encounterCount.collectAsState()
    val streak         by homeVm.encounterStreak.collectAsState()
    val voltsTotal     by homeVm.voltsTotal.collectAsState()
    val context        = LocalContext.current

    ProfileScreenContent(
        profile        = profile,
        encounterCount = encounterCount,
        streak         = streak,
        voltsTotal     = voltsTotal,
        firstRun       = firstRun,
        onSave         = { name, retroUser, seed, greeting, raApiKey ->
            vm.save(name, retroUser, raApiKey = raApiKey, avatarSeed = seed, greeting = greeting)
            RetroAuthManager.getInstance(context)
                .saveCredentials(apiUser = retroUser, apiKey = raApiKey)
            vm.fetchAndCacheOwnRetroProfile()
        },
        onAvatarSeedChange = { vm.saveAvatarSeed(it) },
        onEditSparky       = onEditSparky,
        onComplete         = onComplete,
        onBack             = onBack,
    )
}

// ── Content ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreenContent(
    profile:        MyProfile,
    encounterCount: Int,
    streak:         Int,
    voltsTotal:     Long,
    firstRun:       Boolean                                         = false,
    onSave:         (String, String, String, String, String) -> Unit = { _, _, _, _, _ -> },
    onAvatarSeedChange: ((String) -> Unit)?                    = null,
    onEditSparky:   (() -> Unit)?                              = null,
    onComplete:     (() -> Unit)?                              = null,
    onBack:         (() -> Unit)?                              = null,
) {
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    var hasModified        by remember { mutableStateOf(false) }
    var draftName          by remember { mutableStateOf(profile.displayName) }
    var draftGreeting      by remember { mutableStateOf(profile.greeting) }
    var draftRetroUsername by remember { mutableStateOf(profile.retroUsername) }
    var draftRaApiKey      by remember { mutableStateOf(profile.raApiKey) }
    var avatarSeed         by remember {
        mutableStateOf(profile.avatarSeed.ifEmpty { profile.installationId })
    }

    // Sync drafts from DB once on first meaningful emission, but stop once edited.
    LaunchedEffect(profile) {
        if (!hasModified) {
            draftName          = profile.displayName
            draftGreeting      = profile.greeting
            draftRetroUsername = profile.retroUsername
            draftRaApiKey      = profile.raApiKey
            avatarSeed         = profile.avatarSeed.ifEmpty { profile.installationId }
        }
    }

    // Auto-save with 600 ms debounce; LaunchedEffect cancels prior coroutine on key change.
    LaunchedEffect(draftName, draftGreeting, draftRetroUsername, draftRaApiKey, avatarSeed) {
        if (hasModified) {
            kotlinx.coroutines.delay(600)
            onSave(draftName, draftRetroUsername, avatarSeed, draftGreeting, draftRaApiKey)
        }
    }

    val badgeCount = remember { ALL_BADGES.count { it.tier > 0 } }

    fun randomizeAvatar() {
        // Build a random sparky|... seed directly so the profile card and the
        // SparkyEditor sliders always show the exact same avatar — no UUID randomness
        // that DiceBear resolves differently from what sliders can represent.
        val seed = randomSparkySeed()
        avatarSeed = seed
        onAvatarSeedChange?.invoke(seed)
        hasModified = true
    }

    fun saveNow() {
        onSave(draftName, draftRetroUsername, avatarSeed, draftGreeting, draftRaApiKey)
    }

    // Shared greeting handler: strip injection-prone chars but allow spaces; cap at 60.
    val onGreetingChange: (String) -> Unit = { v ->
        val safe = v.replace(Regex("""[<>'";&|\\/*]"""), "")
        if (safe.length <= 60) { draftGreeting = safe; hasModified = true }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (firstRun) "Set Up Profile" else "My Profile", fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    if (!firstRun && onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        if (isLandscape) {
            Row(modifier = Modifier.fillMaxSize().padding(padding)) {
                // ── Left panel: card + form ────────────────────────────────
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    ProfileUserCard(
                        profile        = profile,
                        avatarSeed     = avatarSeed,
                        encounterCount = encounterCount,
                        streak         = streak,
                        voltsTotal     = voltsTotal,
                        badgeCount     = badgeCount,
                        onRandomize    = ::randomizeAvatar,
                        onEditSparky   = onEditSparky,
                    )
                    ProfileFormSection(
                        draftName             = draftName,
                        draftGreeting         = draftGreeting,
                        draftRetroUsername    = draftRetroUsername,
                        draftRaApiKey         = draftRaApiKey,
                        onNameChange          = { draftName = it; hasModified = true },
                        onGreetingChange      = onGreetingChange,
                        onRetroUsernameChange = { draftRetroUsername = it.trimEnd(); hasModified = true },
                        onRaApiKeyChange      = { draftRaApiKey = it; hasModified = true },
                    )
                    if (firstRun) {
                        Button(
                            onClick  = { saveNow(); onComplete?.invoke() },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Start exploring! 🚀") }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                // Amber divider
                Box(
                    modifier = Modifier
                        .fillMaxHeight(0.85f)
                        .width(3.dp)
                        .align(Alignment.CenterVertically)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color(0xFFFFB300).copy(alpha = 0.15f),
                                    Color(0xFFFFB300),
                                    Color(0xFFFF6F00),
                                    Color(0xFFFFB300).copy(alpha = 0.15f),
                                )
                            )
                        ),
                )

                // ── Right panel: RA gallery ────────────────────────────────
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    RetroGallerySection(modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                }
            }
        } else {
            // ── Portrait: single scrollable column ────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Spacer(Modifier.height(4.dp))
                ProfileUserCard(
                    profile        = profile,
                    avatarSeed     = avatarSeed,
                    encounterCount = encounterCount,
                    streak         = streak,
                    voltsTotal     = voltsTotal,
                    badgeCount     = badgeCount,
                    onRandomize    = ::randomizeAvatar,
                    onEditSparky   = onEditSparky,
                )
                ProfileFormSection(
                    draftName             = draftName,
                    draftGreeting         = draftGreeting,
                    draftRetroUsername    = draftRetroUsername,
                    draftRaApiKey         = draftRaApiKey,
                    onNameChange          = { draftName = it; hasModified = true },
                    onGreetingChange      = onGreetingChange,
                    onRetroUsernameChange = { draftRetroUsername = it.trimEnd(); hasModified = true },
                    onRaApiKeyChange      = { draftRaApiKey = it; hasModified = true },
                )
                RetroGallerySection(modifier = Modifier.fillMaxWidth())
                if (firstRun) {
                    Button(
                        onClick  = { saveNow(); onComplete?.invoke() },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Start exploring! 🚀") }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

// ── Gradient user card ────────────────────────────────────────────────────────

@Composable
private fun ProfileUserCard(
    profile:        MyProfile,
    avatarSeed:     String,
    encounterCount: Int,
    streak:         Int,
    voltsTotal:     Long,
    badgeCount:     Int,
    onRandomize:    () -> Unit,
    onEditSparky:   (() -> Unit)?,
    modifier:       Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .drawBehind {
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(VividPurple, SpaceCyan),
                        start  = Offset(0f, 0f),
                        end    = Offset(size.width, size.height),
                    ),
                )
                // Decorative rotating squares
                val base = size.width * 0.32f
                val decorations = listOf(
                    Triple(size.width * 0.92f,   size.width * 0.18f,  35f  to base * 2.0f),
                    Triple(size.width * 1.10f,   size.width * 0.68f,  20f  to base * 1.55f),
                    Triple(size.width * 0.50f,   size.width * 1.40f,  45f  to base * 1.80f),
                    Triple(size.width * -0.05f,  size.width * 0.52f, -15f  to base * 1.20f),
                )
                for ((cx, cy, rotAndSz) in decorations) {
                    val (deg, sqSz) = rotAndSz
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
        // Dice / randomise button — top-right
        IconButton(
            onClick  = onRandomize,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp),
        ) {
            Icon(
                imageVector        = Icons.Filled.Casino,
                contentDescription = "Randomize avatar",
                tint               = Color.White,
            )
        }

        Column(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 20.dp),
            horizontalAlignment   = Alignment.CenterHorizontally,
            verticalArrangement   = Arrangement.spacedBy(10.dp),
        ) {
            // Avatar: tapping opens SparkyEditor
            DiceBearAvatar(
                seed     = avatarSeed.ifEmpty { "default" },
                size     = 88.dp,
                modifier = Modifier
                    .clip(CircleShape)
                    .then(
                        if (onEditSparky != null)
                            Modifier.clickable { onEditSparky() }
                        else Modifier
                    ),
            )

            Text(
                text       = profile.displayName.ifBlank { "SparkyUser" },
                style      = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color      = Color.White,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis,
            )

            if (profile.deviceType.isNotBlank()) {
                Text(
                    text  = "🎮 ${profile.deviceType}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.80f),
                )
            }

            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                ProfileStatChip("⚡", voltsTotal.toString(),    "Volts")
                ProfileStatChip("🤝", encounterCount.toString(), "Passes")
                ProfileStatChip("🏆", badgeCount.toString(),    "Badges")
                ProfileStatChip("🔥", streak.toString(),        "Streak")
            }
        }
    }
}

// ── Profile form fields ───────────────────────────────────────────────────────

@Composable
private fun ProfileFormSection(
    draftName:             String,
    draftGreeting:         String,
    draftRetroUsername:    String,
    draftRaApiKey:         String,
    onNameChange:          (String) -> Unit,
    onGreetingChange:      (String) -> Unit,
    onRetroUsernameChange: (String) -> Unit,
    onRaApiKeyChange:      (String) -> Unit,
) {
    Column(
        modifier            = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value         = draftName,
            onValueChange = onNameChange,
            label         = { Text("Display name") },
            placeholder   = { Text("Shown to nearby Spark users") },
            singleLine    = true,
            modifier      = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value          = draftGreeting,
            onValueChange  = onGreetingChange,
            label          = { Text("Personal phrase") },
            placeholder    = { Text("Shared when you pass someone") },
            singleLine     = true,
            modifier       = Modifier.fillMaxWidth(),
            supportingText = {
                val remaining = 60 - draftGreeting.length
                Text("$remaining characters left")
            },
        )
        OutlinedTextField(
            value         = draftRetroUsername,
            onValueChange = onRetroUsernameChange,
            label         = { Text("RetroAchievements Username") },
            placeholder   = { Text("Your RA username") },
            singleLine    = true,
            modifier      = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value         = draftRaApiKey,
            onValueChange = onRaApiKeyChange,
            label         = { Text("RetroAchievements Web API Key") },
            placeholder   = { Text("Your RA Web API key") },
            singleLine    = true,
            modifier      = Modifier.fillMaxWidth(),
            supportingText = { Text("Required to load your RA stats") },
        )
    }
}

// ── Stat chip (white text, for use on gradient background) ───────────────────

@Composable
private fun ProfileStatChip(icon: String, value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = icon,  style = MaterialTheme.typography.bodyMedium)
        Text(
            text       = value,
            style      = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color      = Color.White,
        )
        Text(
            text  = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.75f),
        )
    }
}

// ── Preview ───────────────────────────────────────────────────────────────────

@Preview(showBackground = true)
@Composable
fun ProfileScreenPreview() {
    MaterialTheme {
        ProfileScreenContent(
            profile = MyProfile(
                installationId = "test-id",
                displayName    = "Gui",
                greeting       = "Hey there!",
                deviceType     = "AYN Thor 2",
                voltsTotal     = 2500,
            ),
            encounterCount = 12,
            streak         = 3,
            voltsTotal     = 2500,
        )
    }
}
