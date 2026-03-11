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
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.thunderpass.ble.NearbyDeviceState
import com.thunderpass.data.db.entity.MyProfile
import com.thunderpass.retro.RetroAuthManager
import com.thunderpass.security.PairedSyncManager
import com.thunderpass.ui.theme.SpaceCyan
import com.thunderpass.ui.theme.VividPurple
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

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
        onSave         = { name, retroUser, seed, greeting, raApiKey, country, city ->
            vm.save(name, retroUser, raApiKey = raApiKey, avatarSeed = seed, greeting = greeting,
                country = country, city = city)
            RetroAuthManager.getInstance(context)
                .saveCredentials(apiUser = retroUser, apiKey = raApiKey)
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
    onSave:         (String, String, String, String, String, String, String) -> Unit = { _, _, _, _, _, _, _ -> },
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
    var draftCountry       by remember { mutableStateOf(profile.country) }
    var draftCity          by remember { mutableStateOf(profile.city) }
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
            draftCountry       = profile.country
            draftCity          = profile.city
            avatarSeed         = profile.avatarSeed.ifEmpty { profile.installationId }
        }
    }

    // Auto-save with 600 ms debounce; LaunchedEffect cancels prior coroutine on key change.
    LaunchedEffect(draftName, draftGreeting, draftRetroUsername, draftRaApiKey, avatarSeed, draftCountry, draftCity) {
        if (hasModified) {
            kotlinx.coroutines.delay(600)
            onSave(draftName, draftRetroUsername, avatarSeed, draftGreeting, draftRaApiKey, draftCountry, draftCity)
        }
    }

    val badgeCount = remember { ALL_BADGES.count { it.tier > 0 } }

    fun saveNow() {
        onSave(draftName, draftRetroUsername, avatarSeed, draftGreeting, draftRaApiKey, draftCountry, draftCity)
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
                    Text(if (firstRun) "Set Up Profile" else "My Sparky", fontWeight = FontWeight.Bold)
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
                        onEditSparky   = onEditSparky,
                    )
                    ProfileFormSection(
                        draftName             = draftName,
                        draftGreeting         = draftGreeting,
                        draftRetroUsername    = draftRetroUsername,
                        draftRaApiKey         = draftRaApiKey,
                        draftCountry          = draftCountry,
                        draftCity             = draftCity,
                        onNameChange          = { draftName = it; hasModified = true },
                        onGreetingChange      = onGreetingChange,
                        onRetroUsernameChange = { draftRetroUsername = it.trimEnd(); hasModified = true },
                        onRaApiKeyChange      = { draftRaApiKey = it; hasModified = true },
                        onCountryChange       = { draftCountry = it; hasModified = true },
                        onCityChange          = { draftCity = it; hasModified = true },
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
                    PairedDevicesSection()
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
                    onEditSparky   = onEditSparky,
                )
                ProfileFormSection(
                    draftName             = draftName,
                    draftGreeting         = draftGreeting,
                    draftRetroUsername    = draftRetroUsername,
                    draftRaApiKey         = draftRaApiKey,
                    draftCountry          = draftCountry,
                    draftCity             = draftCity,
                    onNameChange          = { draftName = it; hasModified = true },
                    onGreetingChange      = onGreetingChange,
                    onRetroUsernameChange = { draftRetroUsername = it.trimEnd(); hasModified = true },
                    onRaApiKeyChange      = { draftRaApiKey = it; hasModified = true },
                    onCountryChange       = { draftCountry = it; hasModified = true },
                    onCityChange          = { draftCity = it; hasModified = true },
                )
                RetroGallerySection(modifier = Modifier.fillMaxWidth())
                PairedDevicesSection()
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

            if (profile.greeting.isNotBlank()) {
                Text(
                    text     = profile.greeting,
                    style    = MaterialTheme.typography.bodySmall,
                    color    = Color.White.copy(alpha = 0.85f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }

            // Flag + city + device on one line
            val locationDeviceParts = buildList {
                if (profile.country.isNotBlank()) add(locationEmoji(profile.country))
                if (profile.city.isNotBlank()) add(profile.city)
                if (profile.deviceType.isNotBlank()) {
                    // Strip manufacturer prefix if present (e.g. "AYN Thor 2" from "AYN Thor 2")
                    // deviceType is stored as model-only already, just show it
                    add("· ${profile.deviceType}")
                }
            }
            if (locationDeviceParts.isNotEmpty()) {
                Text(
                    text     = locationDeviceParts.joinToString(" "),
                    style    = MaterialTheme.typography.bodySmall,
                    color    = Color.White.copy(alpha = 0.80f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
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

// ── Location emoji helpers ─────────────────────────────────────────────────────

/** Returns the visual emoji for a location code — planet emoji or country flag. */
internal fun locationEmoji(code: String): String {
    PLANET_EMOJI[code.uppercase()]?.let { return it }
    return countryCodeToFlag(code)
}

/** Converts an ISO 3166-1 alpha-2 code (e.g. "BR") to the corresponding flag emoji (🇧🇷). */
internal fun countryCodeToFlag(code: String): String {
    if (code.length != 2) return ""
    val upper = code.uppercase()
    return upper.map { char ->
        String(Character.toChars(0x1F1E6 - 'A'.code + char.code))
    }.joinToString("")
}

// ── Profile form fields ───────────────────────────────────────────────────────

@Composable
private fun ProfileFormSection(
    draftName:             String,
    draftGreeting:         String,
    draftRetroUsername:    String,
    draftRaApiKey:         String,
    draftCountry:          String,
    draftCity:             String,
    onNameChange:          (String) -> Unit,
    onGreetingChange:      (String) -> Unit,
    onRetroUsernameChange: (String) -> Unit,
    onRaApiKeyChange:      (String) -> Unit,
    onCountryChange:       (String) -> Unit,
    onCityChange:          (String) -> Unit,
) {
    Column(
        modifier            = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value         = draftName,
            onValueChange = onNameChange,
            label         = { Text("Sparky Name") },
            placeholder   = { Text("Your Sparky's name") },
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
        var apiKeyVisible by remember { mutableStateOf(false) }
        OutlinedTextField(
            value         = draftRaApiKey,
            onValueChange = onRaApiKeyChange,
            label         = { Text("RetroAchievements Web API Key") },
            placeholder   = { Text("Your RA Web API key") },
            singleLine    = true,
            modifier      = Modifier.fillMaxWidth(),
            visualTransformation = if (apiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                    Icon(
                        imageVector = if (apiKeyVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = if (apiKeyVisible) "Hide API key" else "Show API key",
                    )
                }
            },
            supportingText = { Text("Required to load your RA stats") },
        )

        // ── Location section ──────────────────────────────────────────
        CountryDropdown(
            selectedCode = draftCountry,
            onCodeChange = onCountryChange,
        )
        OutlinedTextField(
            value         = draftCity,
            onValueChange = { if (it.length <= 50) onCityChange(it) },
            label         = { Text("City") },
            placeholder   = { Text("Your city") },
            singleLine    = true,
            modifier      = Modifier.fillMaxWidth(),
        )
    }
}

// ── Origin dropdown (editable autocomplete) ──────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CountryDropdown(
    selectedCode: String,
    onCodeChange: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var inputText by remember { mutableStateOf("") }

    // Build display label from selected code
    val selectedLabel = remember(selectedCode) {
        if (selectedCode.isBlank()) ""
        else {
            val entry = ALL_LOCATIONS.firstOrNull { it.first == selectedCode.uppercase() }
            if (entry != null) "${locationEmoji(selectedCode)} ${entry.second}" else selectedCode
        }
    }

    // Sync input text: clear when opening (so keyboard can type), show label when closed
    LaunchedEffect(expanded) {
        inputText = if (expanded) "" else selectedLabel
    }
    LaunchedEffect(selectedLabel) {
        if (!expanded) inputText = selectedLabel
    }

    val filtered = remember(inputText, expanded) {
        if (!expanded || inputText.isBlank()) ALL_LOCATIONS
        else ALL_LOCATIONS.filter { (_, name) ->
            name.contains(inputText, ignoreCase = true)
        }
    }

    ExposedDropdownMenuBox(
        expanded         = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value         = inputText,
            onValueChange = { inputText = it; if (!expanded) expanded = true },
            label         = { Text("Origin") },
            placeholder   = { Text("Search or select…") },
            trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            singleLine    = true,
            modifier      = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryEditable),
        )
        ExposedDropdownMenu(
            expanded         = expanded,
            onDismissRequest = { expanded = false },
        ) {
            // "None" option to clear
            DropdownMenuItem(
                text    = { Text("— None —") },
                onClick = { onCodeChange(""); expanded = false },
            )
            filtered.forEach { (code, name) ->
                DropdownMenuItem(
                    text    = { Text("${locationEmoji(code)} $name") },
                    onClick = { onCodeChange(code); expanded = false },
                )
            }
        }
    }
}

// ── Planet + Country data ─────────────────────────────────────────────────────

/** Planet codes use X1–X9 (no clash with ISO alpha-2 country codes). */
private val PLANET_LIST = listOf(
    "X1" to "Mercury",
    "X2" to "Venus",
    "X3" to "Earth",
    "X4" to "Mars",
    "X5" to "Jupiter",
    "X6" to "Saturn",
    "X7" to "Uranus",
    "X8" to "Neptune",
    "X9" to "Pluto",
)

/** Colored emoji for each planet, respecting their visual identity. */
internal val PLANET_EMOJI = mapOf(
    "X1" to "\uD83C\uDF11",  // Mercury  — 🌑 dark gray
    "X2" to "\uD83C\uDF15",  // Venus    — 🌕 bright yellow
    "X3" to "\uD83C\uDF0D",  // Earth    — 🌍 blue marble
    "X4" to "\uD83D\uDD34",  // Mars     — 🔴 red
    "X5" to "\uD83D\uDFE0",  // Jupiter  — 🟠 orange
    "X6" to "\uD83E\uDE90",  // Saturn   — 🪐 ringed
    "X7" to "\uD83D\uDD35",  // Uranus   — 🔵 ice blue
    "X8" to "\uD83D\uDFE3",  // Neptune  — 🟣 deep purple
    "X9" to "\u26AA",         // Pluto    — ⚪ icy white
)

private val COUNTRY_LIST = listOf(
    "AR" to "Argentina", "AU" to "Australia", "AT" to "Austria", "BE" to "Belgium",
    "BR" to "Brazil", "CA" to "Canada", "CL" to "Chile", "CN" to "China",
    "CO" to "Colombia", "CZ" to "Czech Republic", "DK" to "Denmark", "FI" to "Finland",
    "FR" to "France", "DE" to "Germany", "GR" to "Greece", "HK" to "Hong Kong",
    "HU" to "Hungary", "IN" to "India", "ID" to "Indonesia", "IE" to "Ireland",
    "IL" to "Israel", "IT" to "Italy", "JP" to "Japan", "KR" to "South Korea",
    "MY" to "Malaysia", "MX" to "Mexico", "NL" to "Netherlands", "NZ" to "New Zealand",
    "NO" to "Norway", "PE" to "Peru", "PH" to "Philippines", "PL" to "Poland",
    "PT" to "Portugal", "RO" to "Romania", "RU" to "Russia", "SA" to "Saudi Arabia",
    "SG" to "Singapore", "ZA" to "South Africa", "ES" to "Spain", "SE" to "Sweden",
    "CH" to "Switzerland", "TW" to "Taiwan", "TH" to "Thailand", "TR" to "Turkey",
    "UA" to "Ukraine", "AE" to "UAE", "GB" to "United Kingdom", "US" to "United States",
    "UY" to "Uruguay", "VE" to "Venezuela", "VN" to "Vietnam",
)

/** Planets on top, then countries. */
private val ALL_LOCATIONS = PLANET_LIST + COUNTRY_LIST

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

// ── Paired Devices Section ────────────────────────────────────────────────────

@Composable
private fun PairedDevicesSection() {
    val context         = LocalContext.current
    var pairedDevices   by remember { mutableStateOf(PairedSyncManager.pairedDevices(context)) }
    var confirmRemoveId by remember { mutableStateOf<String?>(null) }
    val nearbyDevice    by NearbyDeviceState.device.collectAsState()
    val ownDevicePalette = remember { listOf(Color(0xFFFFB300), Color(0xFF06B6D4), Color(0xFF7C3AED)) }

    if (pairedDevices.isEmpty()) return

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text       = "Paired Devices",
            style      = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier   = Modifier.padding(bottom = 8.dp),
        )
        pairedDevices.forEach { pd ->
            val isNearby = nearbyDevice?.let { nd ->
                nd.instId == pd.instId &&
                    (System.currentTimeMillis() - nd.lastSyncMs) < 5L * 60 * 1000
            } ?: false
            val wasSynced = isNearby && (nearbyDevice?.synced == true)
            val iconColor = ownDevicePalette[abs(pd.instId.hashCode()) % ownDevicePalette.size]

            Card(
                shape    = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            ) {
                Row(
                    modifier          = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier         = Modifier.size(40.dp).background(iconColor, RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector        = Icons.Filled.SportsEsports,
                            contentDescription = null,
                            tint               = Color.White,
                            modifier           = Modifier.size(22.dp),
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        val label = pd.deviceType.ifBlank { pd.displayName }
                        Text(
                            text       = label,
                            style      = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            maxLines   = 1,
                        )
                        val timeStr = remember(pd.lastSeenMs) {
                            SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(pd.lastSeenMs))
                        }
                        Text(
                            text  = if (isNearby) if (wasSynced) "Synced" else "Nearby"
                                    else "Last seen: $timeStr",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isNearby) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            maxLines = 1,
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Button(
                        onClick        = { confirmRemoveId = pd.instId },
                        colors         = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor   = MaterialTheme.colorScheme.onErrorContainer,
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        shape          = RoundedCornerShape(8.dp),
                    ) {
                        Text("Remove", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }

    if (confirmRemoveId != null) {
        AlertDialog(
            onDismissRequest = { confirmRemoveId = null },
            title = { Text("Remove Paired Device") },
            text  = {
                val name = pairedDevices.find { it.instId == confirmRemoveId }
                    ?.let { it.deviceType.ifBlank { it.displayName } } ?: "this device"
                Text("Remove $name from your paired devices? You can pair it again later.")
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemoveId?.let { id ->
                        PairedSyncManager.removePairedDevice(context, id)
                        pairedDevices = PairedSyncManager.pairedDevices(context)
                    }
                    confirmRemoveId = null
                }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemoveId = null }) { Text("Cancel") }
            },
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
