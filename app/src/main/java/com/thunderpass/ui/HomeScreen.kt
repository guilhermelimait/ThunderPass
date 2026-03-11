package com.thunderpass.ui

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.WorkspacePremium
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.thunderpass.ui.theme.SpaceCyan
import com.thunderpass.ui.theme.VividPurple
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import android.content.res.Configuration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import com.thunderpass.R
import com.thunderpass.steps.DAILY_VOLT_CAP
import com.thunderpass.steps.STEPS_PER_VOLT

private val BLE_PERMISSIONS = arrayOf(
    Manifest.permission.BLUETOOTH_SCAN,
    Manifest.permission.BLUETOOTH_ADVERTISE,
    Manifest.permission.BLUETOOTH_CONNECT,
    Manifest.permission.POST_NOTIFICATIONS,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToDetail: (Long) -> Unit = {},
    onNavigate: (String) -> Unit = {},
    vm: HomeViewModel = viewModel(),
) {
    val context          = LocalContext.current
    val serviceRunning   by vm.serviceRunning.collectAsState()
    val bleEnabled       by vm.bleEnabled.collectAsState()
    val autoWalkEnabled  by vm.autoWalkEnabled.collectAsState()
    val voltsTotal       by vm.voltsTotal.collectAsState()
    val stepVoltsToday   by vm.stepVoltsToday.collectAsState()
    val stepsToday       by vm.stepsToday.collectAsState()
    val installationId   by vm.installationId.collectAsState()
    val avatarSeed       by vm.avatarSeed.collectAsState()
    val displayName      by vm.displayName.collectAsState()
    val encounters       by vm.encounters.collectAsState()

    // Request ACTIVITY_RECOGNITION after BLE permissions are granted (non-blocking — step Volts
    // degrade gracefully to 0 if the user denies; the BLE UI is unaffected).
    val stepPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* result ignored — StepVoltManager handles sensor absent / permission denied */ }

    var allGranted by remember {
        mutableStateOf(
            BLE_PERMISSIONS.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
        )
    }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results -> allGranted = results.values.all { it } }

    // BT enable launcher: when system dialog confirms BT on, start the service
    val btEnableLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) vm.startService()
    }

    // Auto-request BLE permissions on first composition if not yet granted
    LaunchedEffect(Unit) {
        if (!allGranted) {
            permLauncher.launch(BLE_PERMISSIONS)
        }
    }

    // Request ACTIVITY_RECOGNITION only after BLE permissions are granted,
    // so the user doesn't see two permission dialogs at once.
    LaunchedEffect(allGranted) {
        if (allGranted && ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACTIVITY_RECOGNITION
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            stepPermLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
        }
    }

    HomeScreenContent(
        allGranted = allGranted,
        displayName = displayName,
        serviceRunning = serviceRunning,
        bleEnabled = bleEnabled,
        autoWalkEnabled = autoWalkEnabled,
        avatarSeed = avatarSeed.ifEmpty { installationId },
        encounters = encounters,
        voltsTotal = voltsTotal,
        stepVoltsToday = stepVoltsToday,
        stepsToday = stepsToday,
        onToggleService = {
            if (!allGranted) {
                // Permissions denied — navigate to in-app Settings, highlighting the Permissions area
                onNavigate(Routes.settings(highlight = "permissions"))
            } else if (serviceRunning) {
                vm.stopService()
            } else if (!bleEnabled) {
                // BLE disabled in Settings — do not start
            } else {
                val btAdapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
                if (btAdapter?.isEnabled == true) {
                    vm.startService()
                } else {
                    @Suppress("DEPRECATION")
                    btEnableLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                }
            }
        },
        onNavigateToDetail = onNavigateToDetail,
        onNavigate = onNavigate,
        onGrantPermissions = { permLauncher.launch(BLE_PERMISSIONS) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreenContent(
    allGranted: Boolean,
    displayName: String,
    serviceRunning: Boolean,
    bleEnabled: Boolean = true,
    autoWalkEnabled: Boolean = false,
    avatarSeed: String,
    encounters: List<EncounterWithProfile>,
    voltsTotal: Long,
    stepVoltsToday: Long = 0L,
    stepsToday: Long = 0L,
    onToggleService: () -> Unit,
    onNavigateToDetail: (Long) -> Unit,
    onNavigate: (String) -> Unit = {},
    onGrantPermissions: () -> Unit
) {
    var skyColor by remember { mutableStateOf(Color(0xFFB4D4EE)) } // synced from WalkingSceneCard

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

        if (isLandscape) {
            // Landscape: left=info, right=animation 50% width x 50% HEIGHT, 12dp gap
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                val density = LocalDensity.current
                var bannerHeightPx by remember { mutableStateOf(0) }
                var navHeightPx by remember { mutableStateOf(0) }
                val animLandscapeH = maxWidth * (maxWidth / maxHeight) / 6f  // fallback
                Row(modifier = Modifier.fillMaxSize()) {
                    // Left panel
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        // Header banner — gradient card with decorative squares
                        val landscapeBannerColors = if (!allGranted) listOf(Color(0xFFB71C1C), Color(0xFFD32F2F)) else listOf(VividPurple, SpaceCyan)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .onGloballyPositioned { bannerHeightPx = it.size.height }
                                .shadow(8.dp, RoundedCornerShape(16.dp))
                                .clip(RoundedCornerShape(16.dp))
                                .drawBehind {
                                    drawRect(
                                        brush = Brush.linearGradient(
                                            colors = landscapeBannerColors,
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
                                verticalAlignment     = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier              = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                            ) {
                                // Left: user avatar
                                DiceBearAvatar(
                                    seed     = avatarSeed.ifEmpty { "default" },
                                    size     = 40.dp,
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .clickable { onNavigate("profile") },
                                )
                                // Centre: name + scanning status (tap to toggle service)
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) { onToggleService() },
                                ) {
                                    Text(
                                        text       = displayName,
                                        style      = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color      = Color.White,
                                        maxLines   = 1,
                                        overflow   = TextOverflow.Ellipsis,
                                        modifier   = Modifier.clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) { onToggleService() },
                                    )
                                    Text(
                                        text  = if (!allGranted) "Enable BLE in Settings to start playing" else if (!bleEnabled) "BLE disabled in Settings" else if (serviceRunning) "Scanning nearby" else "Tap to start scanning",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.80f),
                                    )
                                }
                                // Right: Volts balance → Shop
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier
                                        .clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) { onNavigate("shop") }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                ) {
                                    Icon(
                                        painter = painterResource(com.thunderpass.R.drawable.ic_notification),
                                        contentDescription = "Volts",
                                        tint = Color(0xFFFFD700),
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Text(
                                        text = "$voltsTotal",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                    )
                                }
                            }
                        }
                        Box(modifier = Modifier.onGloballyPositioned { navHeightPx = it.size.height }) {
                            NavShortcuts(onNavigate = onNavigate)
                        }

                        RetroGallerySection(modifier = Modifier.fillMaxWidth())
                    }

                    // Amber gradient divider between panels
                    Box(
                        modifier = Modifier
                            .fillMaxHeight(0.85f)
                            .width(3.dp)
                            .align(Alignment.CenterVertically)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0xFFCC8F00).copy(alpha = 0.2f),
                                        Color(0xFFCC8F00),
                                        Color(0xFFCC5500),
                                        Color(0xFFCC8F00).copy(alpha = 0.2f),
                                    )
                                )
                            )
                    )

                    // Right panel: step progress (above) → animation → LastPassedBy
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        StepProgressCard(
                            stepsToday     = stepsToday,
                            stepVoltsToday = stepVoltsToday,
                            skyColor       = skyColor,
                            modifier       = if (bannerHeightPx > 0)
                                Modifier.height(with(density) { bannerHeightPx.toDp() })
                            else Modifier,
                        )
                        val animH = if (navHeightPx > 0) with(density) { navHeightPx.toDp() } else animLandscapeH
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(animH),
                        ) {
                            WalkingSceneCard(
                                avatarSeed        = avatarSeed.ifEmpty { "default" },
                                serviceRunning    = serviceRunning,
                                fillHeight        = true,
                                onSkyColorChanged = { skyColor = it },
                            )
                        }
                        val lastEnc = encounters.firstOrNull()
                        if (lastEnc != null) {
                            LastPassedByCard(
                                encounter = lastEnc,
                                onClick   = { onNavigateToDetail(lastEnc.encounter.id) },
                            )
                        }
                    }
                }
            }
        } else {
            // ── Portrait layout ───────────────────────────────────────────────
            // Order: greeting → toggle → animation (1/3 height) → last-passed-by → nav buttons
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .windowInsetsPadding(WindowInsets.statusBars),
            ) {
                val density = LocalDensity.current
                var bannerHeightPx by remember { mutableStateOf(0) }
                val animHeight = maxHeight / 3

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp),
                ) {
                    Spacer(Modifier.height(16.dp))

                    // ── 1. Greeting banner — gradient card with decorative squares ─
                    val portraitBannerColors = if (!allGranted) listOf(Color(0xFFB71C1C), Color(0xFFD32F2F)) else listOf(VividPurple, SpaceCyan)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onGloballyPositioned { bannerHeightPx = it.size.height }
                            .shadow(8.dp, RoundedCornerShape(16.dp))
                            .clip(RoundedCornerShape(16.dp))
                            .drawBehind {
                                drawRect(
                                    brush = Brush.linearGradient(
                                        colors = portraitBannerColors,
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
                            modifier              = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            // Left: user avatar
                            DiceBearAvatar(
                                seed     = avatarSeed.ifEmpty { "default" },
                                size     = 52.dp,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable { onNavigate("profile") },
                            )
                            // Centre: name + scanning status (tap to toggle service)
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) { onToggleService() },
                            ) {
                                Text(
                                    text       = displayName,
                                    style      = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color      = Color.White,
                                    maxLines   = 1,
                                    overflow   = TextOverflow.Ellipsis,
                                    modifier   = Modifier.clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) { onToggleService() },
                                )
                                Text(
                                    text  = if (!allGranted) "Enable BLE in Settings to start playing" else if (!bleEnabled) "BLE disabled in Settings" else if (serviceRunning) "Scanning nearby" else "Tap to start scanning",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.80f),
                                )
                            }
                            // Right: Volts balance → Shop
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier
                                    .clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) { onNavigate("shop") }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                            ) {
                                Icon(
                                    painter = painterResource(com.thunderpass.R.drawable.ic_notification),
                                    contentDescription = "Volts",
                                    tint = Color(0xFFFFD700),
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    text = "$voltsTotal",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    // ── 2. Step progress toward next Volt ────────────────────
                    StepProgressCard(
                        stepsToday     = stepsToday,
                        stepVoltsToday = stepVoltsToday,
                        skyColor       = skyColor,
                        modifier       = if (bannerHeightPx > 0)
                            Modifier.height(with(density) { bannerHeightPx.toDp() })
                        else Modifier,
                    )

                    Spacer(Modifier.height(8.dp))

                    // ── 3. Animation ───────────────────────────────────────────
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(animHeight),
                    ) {
                        WalkingSceneCard(
                            avatarSeed        = avatarSeed.ifEmpty { "default" },
                            serviceRunning    = serviceRunning,
                            fillHeight        = true,
                            onSkyColorChanged = { skyColor = it },
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    // ── 4. Last Passed By ──────────────────────────────────────
                    val lastEnc = encounters.firstOrNull()
                    if (lastEnc != null) {
                        LastPassedByCard(
                            encounter = lastEnc,
                            onClick   = { onNavigateToDetail(lastEnc.encounter.id) },
                        )
                        Spacer(Modifier.height(10.dp))
                    }

                    // ── 5. RetroAchievements galleries (above nav buttons) ─────
                    RetroGallerySection(modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))

                    // ── 6. Navigation buttons grid ─────────────────────────────
                    NavShortcuts(onNavigate = onNavigate)
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Step progress bar — horizontal bar showing steps toward the next Volt
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun StepProgressCard(
    stepsToday: Long,
    stepVoltsToday: Long,
    skyColor: Color = Color(0xFFB4D4EE),
    modifier: Modifier = Modifier,
) {
    val stepsInBucket = (stepsToday % STEPS_PER_VOLT).toInt()
    val fraction      = stepsInBucket.toFloat() / STEPS_PER_VOLT
    val full          = stepVoltsToday >= DAILY_VOLT_CAP

    val barColor    = if (full) Color(0xFFFFD700) else Color(0xFFCC8F00)
    val trackColor  = Color.White.copy(alpha = 0.35f)
    val dotColor    = Color.White.copy(alpha = 0.5f)
    val dotCount    = 11 // 0, 10, 20 … 100

    // Animate the background color smoothly to stay in sync with the animation
    val animatedSky by animateColorAsState(targetValue = skyColor, animationSpec = tween(600), label = "sky")

    // Animate text color smoothly as the sky changes (no hard flip)
    val onSky by animateColorAsState(
        targetValue    = if (animatedSky.luminance() > 0.4f) Color(0xFF1A1C22) else Color.White,
        animationSpec  = tween(600),
        label          = "onSky",
    )

    Card(
        modifier  = modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        colors    = CardDefaults.cardColors(containerColor = animatedSky),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            // Top row: step count on left, "100 steps = 1 ⚡" on right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text       = if (full) "\u26A1 $stepVoltsToday / $DAILY_VOLT_CAP Volts today"
                                 else "$stepsInBucket / $STEPS_PER_VOLT steps",
                    style      = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color      = if (full) Color(0xFFFFD700) else onSky,
                )
                Text(
                    text  = "$STEPS_PER_VOLT steps = 1 \u26A1",
                    style = MaterialTheme.typography.labelSmall,
                    color = onSky.copy(alpha = 0.75f),
                )
            }

            Spacer(Modifier.height(8.dp))

            // Horizontal progress bar with dot markers
            androidx.compose.foundation.Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp),
            ) {
                val barH      = 6.dp.toPx()
                val dotRadius = 3.5.dp.toPx()
                val yCenter   = size.height / 2
                val barTop    = yCenter - barH / 2
                val barLeft   = dotRadius
                val barRight  = size.width - dotRadius
                val barWidth  = barRight - barLeft

                // Track background
                drawRoundRect(
                    color         = trackColor,
                    topLeft       = Offset(barLeft, barTop),
                    size          = Size(barWidth, barH),
                    cornerRadius  = androidx.compose.ui.geometry.CornerRadius(barH / 2),
                )

                // Filled progress
                val fillWidth = barWidth * fraction
                if (fillWidth > 0f) {
                    drawRoundRect(
                        color         = barColor,
                        topLeft       = Offset(barLeft, barTop),
                        size          = Size(fillWidth, barH),
                        cornerRadius  = androidx.compose.ui.geometry.CornerRadius(barH / 2),
                    )
                }

                // Dot markers at every 10 steps
                for (i in 0 until dotCount) {
                    val dotX = barLeft + barWidth * i / (dotCount - 1)
                    val dotFraction = i.toFloat() / (dotCount - 1)
                    val filled = dotFraction <= fraction
                    drawCircle(
                        color  = if (filled) barColor else dotColor,
                        radius = dotRadius,
                        center = Offset(dotX, yCenter),
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Step-Volt daily progress card — shows today's step-earned Volts (0..100)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun StepVoltsCard(stepVoltsToday: Long, stepsToday: Long = 0L, modifier: Modifier = Modifier) {
    val fraction = (stepVoltsToday.toFloat() / DAILY_VOLT_CAP).coerceIn(0f, 1f)
    val full     = stepVoltsToday >= DAILY_VOLT_CAP

    Card(
        modifier = modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(12.dp),
        colors   = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        ),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("🚶", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text  = "Step Volts",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Icon(
                        imageVector        = Icons.Filled.ElectricBolt,
                        contentDescription = null,
                        tint               = if (full) Color(0xFFFFD700) else Color(0xFFCC8F00),
                        modifier           = Modifier.size(13.dp),
                    )
                    Text(
                        text      = "$stepVoltsToday / $DAILY_VOLT_CAP",
                        style     = MaterialTheme.typography.labelMedium,
                        color     = if (full) Color(0xFFFFD700)
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (full) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text  = "$stepsToday steps today (${STEPS_PER_VOLT} steps = 1 \u26A1)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress          = { fraction },
                modifier          = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color             = if (full) Color(0xFFFFD700) else Color(0xFFCC8F00),
                trackColor        = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Volts info card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun VoltsInfoCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        ),
    ) {
        Column(
            modifier            = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text       = "HOW TO EARN VOLTS",
                style      = MaterialTheme.typography.labelSmall,
                color      = MaterialTheme.colorScheme.primary,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            )
            Text(
                text  = "⚡ Meet a new SparkyUser via BLE — 100 V\n" +
                         "⚡ Unlock a Badge — 50–200 V\n" +
                         "⚡ RetroAchievements activity — up to 500 V\n" +
                         "⚡ Streak bonuses for daily Sparks",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Last Passed By strip
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun LastPassedByCard(encounter: EncounterWithProfile, onClick: () -> Unit) {
    val isOwnDevice = encounter.snapshot?.avatarKind == "own_device"
    val ownDevicePalette = listOf(
        Color(0xFFFFB300), // Amber
        Color(0xFF06B6D4), // Cyan
        Color(0xFF7C3AED), // Vivid Purple
    )
    val ownDeviceColorSeed = encounter.snapshot?.peerInstId?.takeIf { it.isNotBlank() }
        ?: encounter.snapshot?.avatarSeed?.takeIf { it.isNotBlank() }
        ?: encounter.encounter.rotatingId
    val ownDeviceColor = ownDevicePalette[kotlin.math.abs(ownDeviceColorSeed.hashCode()) % ownDevicePalette.size]
    val name = encounter.snapshot?.displayName
        ?.takeIf { it.isNotBlank() } ?: "Unknown SparkyUser"
    val seed = encounter.snapshot?.rotatingId ?: encounter.encounter.rotatingId
    val ago  = relativeTimeString(encounter.encounter.seenAt)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
    ) {
        Row(
            modifier          = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (isOwnDevice) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            color = ownDeviceColor,
                            shape = RoundedCornerShape(10.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector        = Icons.Filled.SportsEsports,
                        contentDescription = "Paired device",
                        tint               = androidx.compose.ui.graphics.Color.White,
                        modifier           = Modifier.size(24.dp),
                    )
                }
            } else {
                DiceBearAvatar(
                    seed     = encounter.snapshot?.avatarSeed?.takeIf { it.isNotBlank() } ?: seed,
                    size     = 40.dp,
                    modifier = Modifier.clip(CircleShape),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text       = if (isOwnDevice) "Your Other Device" else "Last Passed By",
                    style      = MaterialTheme.typography.labelSmall,
                    color      = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text       = name,
                    style      = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis,
                )
            }
            Text(
                text  = ago,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Returns a compact human-readable relative time string (e.g. "2 h ago", "Just now"). */
internal fun relativeTimeString(epochMillis: Long): String {
    val diff = System.currentTimeMillis() - epochMillis
    return when {
        diff < 60_000L               -> "Just now"
        diff < 3_600_000L            -> "${diff / 60_000L} min ago"
        diff < 86_400_000L           -> "${diff / 3_600_000L} h ago"
        diff < 7 * 86_400_000L       -> "${diff / 86_400_000L} d ago"
        else                         -> "${diff / (7 * 86_400_000L)} wk ago"
    }
}


// ─────────────────────────────────────────────────────────────────────────────
// Permission prompt
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PermissionPrompt(onGrant: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier            = Modifier.padding(top = 64.dp),
    ) {
        Text(
            text       = "Permissions needed",
            style      = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text  = "ThunderPass needs Bluetooth Scan, Advertise, Connect, and " +
                    "Notification permissions to discover and exchange profiles with nearby devices.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onGrant) { Text("Grant Permissions") }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Navigation shortcut buttons — always-visible 2×3 grid, badge-style square tiles
// ─────────────────────────────────────────────────────────────────────────────

private data class NavEntry(
    val label:       String,
    val icon:        ImageVector,
    val route:       String,
    val accentColor: Color,
    val gradientEnd: Color,
)

private val NAV_ENTRIES = listOf(
    NavEntry("Passes",   Icons.Filled.ElectricBolt,     "encounters", Color(0xFFFFB300), Color(0xFFFF6F00)),
    NavEntry("Profile",  Icons.Filled.Person,           "profile",    Color(0xFF2196F3), Color(0xFF0D47A1)),
    NavEntry("Badges",   Icons.Filled.WorkspacePremium, "badges",     Color(0xFF7B1FA2), Color(0xFFAD1457)),
    NavEntry("Shop",     Icons.Filled.ShoppingCart,     "shop",       Color(0xFFE64A19), Color(0xFFF57F17)),
    NavEntry("Settings", Icons.Filled.Settings,         "settings",   Color(0xFF37474F), Color(0xFF546E7A)),
    NavEntry("About",    Icons.Filled.LocalCafe,        "about",      Color(0xFF00796B), Color(0xFF00ACC1)),
)

@Composable
internal fun NavShortcuts(onNavigate: (String) -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier            = Modifier.fillMaxWidth(),
    ) {
        NAV_ENTRIES.chunked(3).forEach { rowEntries ->
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowEntries.forEach { entry ->
                    NavSquareButton(
                        entry    = entry,
                        onClick  = { onNavigate(entry.route) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun NavSquareButton(
    entry:    NavEntry,
    onClick:  () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .shadow(6.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .drawBehind {
                // Badge-style gradient background
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(entry.accentColor, entry.gradientEnd),
                        start  = Offset(0f, 0f),
                        end    = Offset(size.width, size.height),
                    ),
                )
                // Decorative radial glow circles — radii proportional to button
                // height so they look identical on every screen size / density.
                val cx = size.width * 0.88f
                val cy = size.height * 0.12f
                for (ratio in listOf(0.65f, 0.90f, 1.15f)) {
                    drawCircle(
                        color  = Color.White.copy(alpha = 0.09f),
                        radius = size.height * ratio,
                        center = Offset(cx, cy),
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector        = entry.icon,
                contentDescription = entry.label,
                tint               = Color.White,
                modifier           = Modifier.size(22.dp),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text       = entry.label,
                style      = MaterialTheme.typography.labelSmall,
                color      = Color.White,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    MaterialTheme {
        HomeScreenContent(
            allGranted = true,
            displayName = "Gui",
            serviceRunning = true,
            avatarSeed = "test-id",
            encounters = emptyList(),
            voltsTotal = 2500,
            stepVoltsToday = 42,
            onToggleService = {},
            onNavigateToDetail = {},
            onGrantPermissions = {}
        )
    }
}
