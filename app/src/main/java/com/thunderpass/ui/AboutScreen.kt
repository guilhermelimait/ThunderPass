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
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // ── Top row: avatar+name on left, bio on right ────────────
                    Row(
                        modifier          = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        // Left — avatar + name + tagline
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.width(110.dp),
                        ) {
                            SubcomposeAsyncImage(
                                model              = "https://storage.ko-fi.com/cdn/useruploads/81b005da-03c3-4771-992a-09a0f8f77595_bc4f05e0-07c0-4ed6-a9-13f16968d95b.png",
                                contentDescription = "Guilherme Lima",
                                contentScale       = ContentScale.Crop,
                                modifier           = Modifier
                                    .size(80.dp)
                                    .clip(CircleShape),
                                loading = {
                                    Box(
                                        modifier = Modifier
                                            .size(80.dp)
                                            .clip(CircleShape)
                                            .background(Color.White.copy(alpha = 0.2f)),
                                    )
                                },
                                error = {
                                    DiceBearAvatar(seed = "guilhermelimait", size = 80.dp, modifier = Modifier.clip(CircleShape))
                                },
                            )
                            Text(
                                text       = "Guilherme Lima",
                                style      = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color      = Color.White,
                                textAlign  = TextAlign.Center,
                            )
                            Text(
                                text      = "Made with ⚡ and ☕",
                                style     = MaterialTheme.typography.labelSmall,
                                color     = Color.White.copy(alpha = 0.85f),
                                textAlign = TextAlign.Center,
                            )
                        }

                        // Right — app bio
                        Text(
                            text      = "ThunderPass is an offline-first StreetPass app for Android handheld gaming " +
                                        "devices. It discovers nearby players over Bluetooth LE, exchanges profile " +
                                        "cards, and tracks encounters — no internet required.\n\n" +
                                        "Built by a solo dev who wanted StreetPass back on modern hardware.",
                            style     = MaterialTheme.typography.bodyMedium,
                            color     = Color.White.copy(alpha = 0.9f),
                            modifier  = Modifier.weight(1f),
                        )
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.25f))

                    // Social / support icon buttons — circles adapt to theme
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment     = Alignment.CenterVertically,
                    ) {
                        // Ko-fi
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(circleBackground)
                                .clickable {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse("https://ko-fi.com/guilhermelimait/"))
                                    )
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector        = Icons.Filled.LocalCafe,
                                contentDescription = "Support on Ko-fi",
                                tint               = iconTint,
                                modifier           = Modifier.size(28.dp),
                            )
                        }

                        Spacer(Modifier.width(28.dp))

                        // GitHub
                        Box(
                            modifier = Modifier
                                .size(64.dp)
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
                                modifier           = Modifier.size(28.dp),
                            )
                        }

                        Spacer(Modifier.width(28.dp))

                        // Discord
                        Box(
                            modifier = Modifier
                                .size(64.dp)
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
                                modifier           = Modifier.size(28.dp),
                            )
                        }
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.25f))

                    // Version
                    Text(
                        text      = "ThunderPass v${BuildConfig.VERSION_NAME}\nBluetooth LE • Zero cloud • Open source",
                        style     = MaterialTheme.typography.bodySmall,
                        color     = Color.White.copy(alpha = 0.75f),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
