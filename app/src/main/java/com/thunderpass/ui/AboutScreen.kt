package com.thunderpass.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun AboutScreen() {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Spacer(Modifier.height(32.dp))

        // Developer avatar
        DiceBearAvatar(
            seed     = "guilhermelimait",
            size     = 96.dp,
            modifier = Modifier.clip(CircleShape),
        )

        // Name + tagline
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text       = "Guilherme Lima",
                style      = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text  = "Made with ⚡ and ☕",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Bio card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors   = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            ),
        ) {
            Text(
                text    = "ThunderPass is an offline-first StreetPass app for Android handheld gaming " +
                          "devices. It discovers nearby players over Bluetooth LE, exchanges profile " +
                          "cards, and tracks encounters — no internet required.\n\n" +
                          "Built by a solo dev who wanted StreetPass back on modern hardware.",
                style   = MaterialTheme.typography.bodyMedium,
                color   = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // Ko-fi button
        Button(
            onClick  = {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://ko-fi.com/guilhermelimait/"))
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
            ),
        ) {
            Icon(
                imageVector        = Icons.Filled.LocalCafe,
                contentDescription = null,
                modifier           = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text       = "Support on Ko-fi",
                style      = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }

        // GitHub Issues button
        OutlinedButton(
            onClick  = {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://github.com/guilhermelimait/ThunderPass/issues"))
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            Text(
                text  = "Report an Issue on GitHub",
                style = MaterialTheme.typography.labelLarge,
            )
        }

        // Source / GitHub
        OutlinedButton(
            onClick  = {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://github.com/guilhermelimait/ThunderPass"))
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            Text(
                text  = "View Source on GitHub",
                style = MaterialTheme.typography.labelLarge,
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // Version
        Text(
            text       = "ThunderPass v0.7.2\nBluetooth LE • Zero cloud • Open source",
            style      = MaterialTheme.typography.bodySmall,
            color      = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign  = TextAlign.Center,
        )

        Spacer(Modifier.height(32.dp))
    }
}
