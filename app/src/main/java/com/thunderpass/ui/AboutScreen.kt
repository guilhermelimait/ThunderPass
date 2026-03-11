package com.thunderpass.ui

import android.content.Intent
import com.thunderpass.BuildConfig
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.thunderpass.R
import com.thunderpass.ui.theme.SpaceCyan
import com.thunderpass.ui.theme.VividPurple

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val shape   = RoundedCornerShape(20.dp)

    // Circle colors adapt to light / dark theme
    val isDark          = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val circleBackground = if (isDark) Color(0xFF0D0D0D) else Color.White
    val iconTint         = if (isDark) Color.White       else Color(0xFF0D0D0D)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("About") },
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
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                // 20 dp breathing room on every edge — card never touches the screen border
                .padding(horizontal = 20.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // ── Gradient card — all content lives inside ─────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(8.dp, shape)
                    .clip(shape)
                    .drawBehind {
                        drawRect(
                            brush = Brush.linearGradient(
                                colors = listOf(VividPurple, SpaceCyan),
                                start  = Offset(0f, 0f),
                                end    = Offset(size.width, size.height),
                            ),
                        )
                        // Decorative rotated squares (same pattern as HomeScreen cards)
                        val base = size.width * 0.32f
                        val positions = listOf(
                            Triple(size.width * 0.92f,  size.width * 0.18f,   35f to base * 2.0f),
                            Triple(size.width * 1.10f,  size.width * 0.68f,   20f to base * 1.55f),
                            Triple(size.width * 0.50f,  size.width * 1.40f,   45f to base * 1.80f),
                            Triple(size.width * -0.05f, size.width * 0.52f,  -15f to base * 1.20f),
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
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    // ── Author + bio + privacy ───────────────────────────────
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        verticalAlignment     = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        // Left — photo, tagline, social links
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier            = Modifier.width(104.dp),
                        ) {
                            SubcomposeAsyncImage(
                                model              = "https://avatars.githubusercontent.com/guilhermelimait",
                                contentDescription = "Developer photo",
                                contentScale       = ContentScale.Crop,
                                modifier           = Modifier
                                    .size(96.dp)
                                    .clip(CircleShape),
                            )
                            Text(
                                text      = "Tailored with AI and ⚡",
                                style     = MaterialTheme.typography.labelSmall,
                                color     = Color.White.copy(alpha = 0.85f),
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(2.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment     = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(circleBackground)
                                        .clickable {
                                            context.startActivity(
                                                Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/guilhermelimait/ThunderPass"))
                                            )
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        painter            = painterResource(R.drawable.ic_github),
                                        contentDescription = "GitHub",
                                        tint               = iconTint,
                                        modifier           = Modifier.size(20.dp),
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(circleBackground)
                                        .clickable {
                                            context.startActivity(
                                                Intent(Intent.ACTION_VIEW, Uri.parse("https://discord.gg/jVxQnp8Fy"))
                                            )
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        painter            = painterResource(R.drawable.ic_discord),
                                        contentDescription = "Discord",
                                        tint               = iconTint,
                                        modifier           = Modifier.size(20.dp),
                                    )
                                }
                            }
                        }

                        // Right — app description + privacy & security
                        Column(
                            modifier            = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text       = "ThunderPass",
                                style      = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color      = Color.White,
                            )
                            Text(
                                text  = "A modern StreetPass for Android handheld gaming devices. " +
                                        "Discovers nearby players via Bluetooth LE, exchanges profile cards, " +
                                        "tracks encounters, badges, and streaks.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.90f),
                            )
                            Text(
                                text  = "Built by a solo developer who missed StreetPass and decided to bring it back for modern hardware.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.75f),
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text       = "Privacy & Security",
                                style      = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color      = Color.White,
                            )
                            val securityLines = listOf(
                                "Profile data is stored locally on your device and encrypted at rest using AES-256-GCM.",
                                "Encounters exchanged over Bluetooth LE are protected with RSA asymmetric cryptography — only the intended recipient device can decrypt them.",
                                "Cryptographic keys are managed by the Android Keystore, hardware-backed where supported by the device.",
                                "ThunderPass has no internet connectivity, no external servers, no accounts, and no telemetry of any kind.",
                                "Your data never leaves your device unless you explicitly sync it to another device you own.",
                            )
                            securityLines.forEach { line ->
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier              = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        text  = "•",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.70f),
                                    )
                                    Text(
                                        text  = line,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.85f),
                                    )
                                }
                            }
                        }
                    }

                    // ── Version ───────────────────────────────────────────────
                    Text(
                        text  = "v${BuildConfig.VERSION_NAME} • Open source",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.55f),
                    )
                }
            }
        }
    }
}
