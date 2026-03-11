package com.thunderpass.ui

import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thunderpass.ble.SyncGattClient
import com.thunderpass.ble.SyncGattServer
import com.thunderpass.data.db.ThunderPassDatabase
import kotlinx.coroutines.delay

// Gradient colors matching NavSquareButton style from HomeScreen
private val SenderAccent    = Color(0xFF2196F3)
private val SenderGradEnd   = Color(0xFF0D47A1)
private val ReceiverAccent  = Color(0xFF00796B)
private val ReceiverGradEnd = Color(0xFF00ACC1)

/**
 * Device-to-device profile sync screen using SAS (Short Authentication String) numeric comparison.
 *
 * ## Sender tab (primary device)
 * Start Sync → advertise → sender shows code 1 → receiver types it → transfer → receiver shows code 2 → sender types it → done.
 *
 * ## Receiver tab (secondary device)
 * Search → scan → connect → receiver types code 1 from sender → transfer → receiver shows code 2 → sender types it → done.
 *
 * All data is encrypted end-to-end with AES-256-GCM over an ECDH P-256 key exchange.
 * Confirmation codes are HMAC-SHA256 derived. No cleartext ever travels over BLE.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceSyncScreen(onBack: () -> Unit) {
    val context     = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs        = listOf("Sender", "Receiver")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Device Sync", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected  = selectedTab == index,
                        onClick   = { selectedTab = index },
                        text      = { Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) },
                    )
                }
            }

            when (selectedTab) {
                0 -> SenderTab(context, onBack)
                1 -> ReceiverTab(context, onBack)
            }
        }
    }
}

// ── Gradient rounded-rectangle background (matches NavSquareButton style) ─────

@Composable
private fun GradientPanel(
    accentColor: Color,
    gradientEnd: Color,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .shadow(6.dp, RoundedCornerShape(16.dp))
            .graphicsLayer { shape = RoundedCornerShape(16.dp); clip = true }
            .drawBehind {
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(accentColor, gradientEnd),
                        start  = Offset(0f, 0f),
                        end    = Offset(size.width, size.height),
                    ),
                )
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
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            content = content,
        )
    }
}

@Composable
private fun SyncStatusChipWhite(status: SyncStatus) {
    if (status == SyncStatus.Idle) return
    val text = when (status) {
        SyncStatus.Scanning       -> "Scanning…"
        SyncStatus.Waiting        -> "Waiting…"
        SyncStatus.Transferring   -> "Expecting confirmation…"
        SyncStatus.Syncing        -> "Codes confirmed! Syncing…"
        is SyncStatus.ConfirmCode -> "Code ${status.step}"
        is SyncStatus.Success     -> status.message
        is SyncStatus.Error       -> status.message
        SyncStatus.Idle           -> return
    }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color.White.copy(alpha = 0.2f),
    ) {
        Text(
            text     = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style    = MaterialTheme.typography.labelMedium,
            color    = Color.White,
        )
    }
}

// ── Sender tab ────────────────────────────────────────────────────────────────

@Composable
private fun SenderTab(context: Context, onBack: () -> Unit) {
    val db          = remember { ThunderPassDatabase.getInstance(context) }

    var status    by remember { mutableStateOf<SyncStatus>(SyncStatus.Idle) }
    var server    by remember { mutableStateOf<SyncGattServer?>(null) }
    var codeInput by remember { mutableStateOf("") }
    var codeError by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose { server?.stop() }
    }

    GradientPanel(
        accentColor = SenderAccent,
        gradientEnd = SenderGradEnd,
        modifier    = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Spacer(Modifier.height(16.dp))

        when (val s = status) {
            is SyncStatus.ConfirmCode -> {
                if (s.step == 1) {
                    // ── Step 1: Sender DISPLAYS code, receiver types it ──
                    // Auto-confirm locally; flow proceeds when receiver enters the code.
                    LaunchedEffect(s.code) { server?.confirmStep(1) }
                    Text(
                        text  = "Verification Code 1",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.7f),
                    )
                    Spacer(Modifier.height(8.dp))
                    val formatted = s.code.take(3) + " " + s.code.drop(3)
                    Text(
                        text          = formatted,
                        style         = MaterialTheme.typography.displaySmall,
                        fontFamily    = FontFamily.Monospace,
                        fontWeight    = FontWeight.Bold,
                        color         = Color.White,
                        letterSpacing = 4.sp,
                        textAlign     = TextAlign.Center,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text  = "Tell this code to the receiver",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = Color.White,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text  = "Waiting for receiver…",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.5f),
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { server?.stop(); server = null; codeInput = ""; codeError = false; status = SyncStatus.Idle },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White.copy(alpha = 0.22f),
                            contentColor   = Color.White,
                        ),
                        modifier = Modifier.fillMaxWidth(0.7f),
                    ) { Text("Cancel", fontSize = 13.sp) }
                } else {
                    // ── Step 2: Sender TYPES code shown on receiver ──
                    val isPortrait = LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT
                    Text(
                        text  = "Enter Code 2",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.7f),
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text  = "Type the code shown on the receiver",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(6.dp))
                    // Code digit boxes
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Spacer(Modifier.weight(1f))
                        for (i in 0 until 6) {
                            val ch = codeInput.getOrNull(i)
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(36.dp)
                                    .border(
                                        width = 1.5.dp,
                                        color = if (codeError) Color(0xFFFF5252)
                                                else if (i == codeInput.length) Color.White
                                                else Color.White.copy(alpha = 0.4f),
                                        shape = RoundedCornerShape(8.dp),
                                    ),
                            ) {
                                Text(
                                    text       = ch?.toString() ?: "",
                                    style      = MaterialTheme.typography.titleLarge,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color      = Color.White,
                                )
                            }
                        }
                        Spacer(Modifier.weight(1f))
                    }
                    if (codeError) {
                        Spacer(Modifier.height(4.dp))
                        Text("Code does not match", style = MaterialTheme.typography.labelSmall, color = Color(0xFFFF5252))
                    }
                    Spacer(Modifier.height(6.dp))
                    // Keypad (no ✓ — auto-confirms when 6th digit matches)
                    val keyRows: List<List<String>> = if (isPortrait) {
                        listOf(
                            listOf("1", "2", "3", "4", "5"),
                            listOf("6", "7", "8", "9", "0"),
                            listOf("\u2715", "\u232B", "", "", ""),
                        )
                    } else {
                        listOf(
                            listOf("1", "2", "3", "4", "5", "6"),
                            listOf("7", "8", "9", "0", "\u232B", "\u2715"),
                        )
                    }
                    Column(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        for (row in keyRows) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.fillMaxWidth().weight(1f),
                            ) {
                                for (key in row) {
                                    if (key.isEmpty()) {
                                        Spacer(Modifier.weight(1f))
                                    } else {
                                        val isBack    = key == "\u232B"
                                        val isCancel  = key == "\u2715"
                                        val enabled = when {
                                            isBack    -> codeInput.isNotEmpty()
                                            isCancel  -> true
                                            else      -> codeInput.length < 6
                                        }
                                        val bgColor = when {
                                            isCancel -> Color.White.copy(alpha = 0.08f)
                                            else     -> Color.White.copy(alpha = 0.15f)
                                        }
                                        val fgColor = when {
                                            !enabled -> Color.White.copy(alpha = 0.3f)
                                            isCancel -> Color.White.copy(alpha = 0.7f)
                                            else     -> Color.White
                                        }
                                        Surface(
                                            shape   = RoundedCornerShape(10.dp),
                                            color   = bgColor,
                                            onClick = {
                                                when {
                                                    isCancel -> {
                                                        server?.stop(); server = null
                                                        codeInput = ""; codeError = false
                                                        status = SyncStatus.Idle
                                                    }
                                                    isBack -> {
                                                        codeInput = codeInput.dropLast(1)
                                                        codeError = false
                                                    }
                                                    else -> {
                                                        val next = codeInput + key
                                                        codeInput = next
                                                        codeError = false
                                                        if (next.length == 6) {
                                                            if (next == s.code) {
                                                                server?.confirmStep(2)
                                                                codeInput = ""; codeError = false
                                                                status = SyncStatus.Waiting
                                                            } else { codeError = true; codeInput = "" }
                                                        }
                                                    }
                                                }
                                            },
                                            enabled  = enabled,
                                            modifier = Modifier.weight(1f).fillMaxHeight(),
                                        ) {
                                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                Text(
                                                    text       = if (isCancel) "Cancel" else key,
                                                    style      = if (isCancel) MaterialTheme.typography.bodyMedium
                                                                 else MaterialTheme.typography.headlineSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color      = fgColor,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            else -> {
                SyncStatusChipWhite(status)
                if (status != SyncStatus.Idle) Spacer(Modifier.height(12.dp))

                when (status) {
                    SyncStatus.Idle -> {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = null,
                            tint     = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.size(48.dp),
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = {
                                status = SyncStatus.Waiting
                                val srv = SyncGattServer(context, db.myProfileDao())
                                srv.onShowConfirmCode = { code, step ->
                                    status = SyncStatus.ConfirmCode(code, step)
                                }
                                srv.onSyncComplete = { _ -> status = SyncStatus.Syncing; server = null }
                                srv.onSyncFailed   = { reason -> status = SyncStatus.Error(reason); server = null }
                                server = srv
                                srv.start()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor   = SenderGradEnd,
                            ),
                            modifier = Modifier.fillMaxWidth(0.7f),
                        ) {
                            Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Start Sync")
                        }
                    }
                    SyncStatus.Scanning, SyncStatus.Waiting, SyncStatus.Transferring -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = Color.White,
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { server?.stop(); server = null; status = SyncStatus.Idle },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White.copy(alpha = 0.22f),
                                contentColor   = Color.White,
                            ),
                            modifier = Modifier.fillMaxWidth(0.7f),
                        ) { Text("Cancel", fontSize = 13.sp) }
                    }
                    SyncStatus.Syncing -> {
                        LaunchedEffect(Unit) { delay(3000); status = SyncStatus.Success("Sync complete!") }
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = Color.White,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text  = "Codes confirmed!\nSyncing…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                        )
                    }
                    is SyncStatus.Success -> {
                        Button(
                            onClick = onBack,
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = SenderGradEnd),
                            modifier = Modifier.fillMaxWidth(0.7f),
                        ) { Text("Done") }
                    }
                    is SyncStatus.Error -> {
                        Button(
                            onClick = { status = SyncStatus.Idle },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = SenderGradEnd),
                            modifier = Modifier.fillMaxWidth(0.7f),
                        ) { Text("Retry") }
                    }
                    else -> {}
                }

                Spacer(Modifier.height(16.dp))
                Text(
                    text  = "Both devices must have Bluetooth enabled and be near each other.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

// ── Receiver tab ──────────────────────────────────────────────────────────────

@Composable
private fun ReceiverTab(context: Context, onBack: () -> Unit) {
    val db        = remember { ThunderPassDatabase.getInstance(context) }
    var status    by remember { mutableStateOf<SyncStatus>(SyncStatus.Idle) }
    var client    by remember { mutableStateOf<SyncGattClient?>(null) }
    var codeInput by remember { mutableStateOf("") }
    var codeError by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose { client?.stop() }
    }

    GradientPanel(
        accentColor = ReceiverAccent,
        gradientEnd = ReceiverGradEnd,
        modifier    = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        when (val s = status) {
            is SyncStatus.ConfirmCode -> {
                if (s.step == 1) {
                    // ── Step 1: Receiver TYPES code shown on sender ──
                    val isPortrait = LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT
                    Text(
                        text  = "Enter Code 1",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.7f),
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text  = "Tap the code shown on the sender",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(6.dp))
                    // Code digit boxes
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Spacer(Modifier.weight(1f))
                        for (i in 0 until 6) {
                            val ch = codeInput.getOrNull(i)
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(36.dp)
                                    .border(
                                        width = 1.5.dp,
                                        color = if (codeError) Color(0xFFFF5252)
                                                else if (i == codeInput.length) Color.White
                                                else Color.White.copy(alpha = 0.4f),
                                        shape = RoundedCornerShape(8.dp),
                                    ),
                            ) {
                                Text(
                                    text       = ch?.toString() ?: "",
                                    style      = MaterialTheme.typography.titleLarge,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color      = Color.White,
                                )
                            }
                        }
                        Spacer(Modifier.weight(1f))
                    }
                    if (codeError) {
                        Spacer(Modifier.height(4.dp))
                        Text("Code does not match", style = MaterialTheme.typography.labelSmall, color = Color(0xFFFF5252))
                    }
                    Spacer(Modifier.height(6.dp))
                    // Keypad (no ✓ — auto-confirms when 6th digit matches)
                    val keyRows: List<List<String>> = if (isPortrait) {
                        listOf(
                            listOf("1", "2", "3", "4", "5"),
                            listOf("6", "7", "8", "9", "0"),
                            listOf("\u2715", "\u232B", "", "", ""),
                        )
                    } else {
                        listOf(
                            listOf("1", "2", "3", "4", "5", "6"),
                            listOf("7", "8", "9", "0", "\u232B", "\u2715"),
                        )
                    }
                    Column(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        for (row in keyRows) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.fillMaxWidth().weight(1f),
                            ) {
                                for (key in row) {
                                    if (key.isEmpty()) {
                                        Spacer(Modifier.weight(1f))
                                    } else {
                                        val isBack    = key == "\u232B"
                                        val isCancel  = key == "\u2715"
                                        val enabled = when {
                                            isBack    -> codeInput.isNotEmpty()
                                            isCancel  -> true
                                            else      -> codeInput.length < 6
                                        }
                                        val bgColor = when {
                                            isCancel -> Color.White.copy(alpha = 0.08f)
                                            else     -> Color.White.copy(alpha = 0.15f)
                                        }
                                        val fgColor = when {
                                            !enabled -> Color.White.copy(alpha = 0.3f)
                                            isCancel -> Color.White.copy(alpha = 0.7f)
                                            else     -> Color.White
                                        }
                                        Surface(
                                            shape   = RoundedCornerShape(10.dp),
                                            color   = bgColor,
                                            onClick = {
                                                when {
                                                    isCancel -> {
                                                        client?.stop(); client = null
                                                        codeInput = ""; codeError = false
                                                        status = SyncStatus.Idle
                                                    }
                                                    isBack -> {
                                                        codeInput = codeInput.dropLast(1)
                                                        codeError = false
                                                    }
                                                    else -> {
                                                        val next = codeInput + key
                                                        codeInput = next
                                                        codeError = false
                                                        if (next.length == 6) {
                                                            if (next == s.code) {
                                                                client?.confirmStep(1)
                                                                codeInput = ""; codeError = false
                                                                status = SyncStatus.Transferring
                                                            } else { codeError = true; codeInput = "" }
                                                        }
                                                    }
                                                }
                                            },
                                            enabled  = enabled,
                                            modifier = Modifier.weight(1f).fillMaxHeight(),
                                        ) {
                                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                Text(
                                                    text       = if (isCancel) "Cancel" else key,
                                                    style      = if (isCancel) MaterialTheme.typography.bodyMedium
                                                                 else MaterialTheme.typography.headlineSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color      = fgColor,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // ── Step 2: Receiver DISPLAYS code, sender types it ──
                    // No auto-confirm — waits for STEP2_ACK_MAGIC from server.
                    Text(
                        text  = "Verification Code 2",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.7f),
                    )
                    Spacer(Modifier.height(8.dp))
                    val formatted = s.code.take(3) + " " + s.code.drop(3)
                    Text(
                        text          = formatted,
                        style         = MaterialTheme.typography.displaySmall,
                        fontFamily    = FontFamily.Monospace,
                        fontWeight    = FontWeight.Bold,
                        color         = Color.White,
                        letterSpacing = 4.sp,
                        textAlign     = TextAlign.Center,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text  = "Tell this code to the sender",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = Color.White,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text  = "Waiting for sender…",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.5f),
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { client?.stop(); client = null; codeInput = ""; codeError = false; status = SyncStatus.Idle },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White.copy(alpha = 0.22f),
                            contentColor   = Color.White,
                        ),
                        modifier = Modifier.fillMaxWidth(0.7f),
                    ) { Text("Cancel", fontSize = 13.sp) }
                }
            }
            else -> {
                if (status == SyncStatus.Idle) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint     = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(48.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                }

                SyncStatusChipWhite(status)
                Spacer(Modifier.height(16.dp))

                when (status) {
                    SyncStatus.Idle -> {
                        Text(
                            text  = "Volts are preserved — the higher value is kept.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = {
                                status = SyncStatus.Scanning
                                val cli = SyncGattClient(context, db.myProfileDao())
                                cli.onShowConfirmCode = { code, step ->
                                    status = SyncStatus.ConfirmCode(code, step)
                                }
                                cli.onSyncComplete = { _ -> status = SyncStatus.Syncing; client = null }
                                cli.onSyncFailed   = { reason -> status = SyncStatus.Error(reason); client = null }
                                client = cli
                                cli.start()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor   = ReceiverGradEnd,
                            ),
                            modifier = Modifier.fillMaxWidth(0.7f),
                        ) {
                            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Search Devices")
                        }
                    }
                    SyncStatus.Scanning, SyncStatus.Waiting -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = Color.White,
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { client?.stop(); client = null; status = SyncStatus.Idle },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White.copy(alpha = 0.22f),
                                contentColor   = Color.White,
                            ),
                            modifier = Modifier.fillMaxWidth(0.7f),
                        ) { Text("Cancel", fontSize = 13.sp) }
                    }
                    SyncStatus.Transferring -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = Color.White,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text  = "Expecting confirmation\ncode from sender…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { client?.stop(); client = null; status = SyncStatus.Idle },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White.copy(alpha = 0.22f),
                                contentColor   = Color.White,
                            ),
                            modifier = Modifier.fillMaxWidth(0.7f),
                        ) { Text("Cancel", fontSize = 13.sp) }
                    }
                    SyncStatus.Syncing -> {
                        LaunchedEffect(Unit) { delay(3000); status = SyncStatus.Success("Sync complete!") }
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = Color.White,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text  = "Codes confirmed!\nSyncing…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                        )
                    }
                    is SyncStatus.Success -> {
                        Button(
                            onClick = onBack,
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = ReceiverGradEnd),
                            modifier = Modifier.fillMaxWidth(0.7f),
                        ) { Text("Done") }
                    }
                    is SyncStatus.Error -> {
                        Button(
                            onClick = { status = SyncStatus.Idle },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = ReceiverGradEnd),
                            modifier = Modifier.fillMaxWidth(0.7f),
                        ) { Text("Retry") }
                    }
                    else -> {}
                }

                Spacer(Modifier.height(16.dp))
                Text(
                    text  = "Your installation ID is never changed.\nProfile data is replaced by the sender's profile.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

// ── State model ───────────────────────────────────────────────────────────────

private sealed class SyncStatus {
    data object Idle         : SyncStatus()
    data object Scanning     : SyncStatus()
    data object Waiting      : SyncStatus()
    data object Transferring : SyncStatus()
    data object Syncing      : SyncStatus()
    data class  ConfirmCode(val code: String, val step: Int) : SyncStatus()
    data class  Success(val message: String) : SyncStatus()
    data class  Error(val message: String)   : SyncStatus()
}
