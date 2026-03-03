package com.thunderpass.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.thunderpass.data.StickerManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StickerBookScreen(
    onBack: () -> Unit,
    vm: HomeViewModel = viewModel(),
) {
    val ownedKeys by vm.ownedStickers.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sticker Book") },
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
                .padding(horizontal = 16.dp),
        ) {
            // ── Progress header ──────────────────────────────────────────────
            val total   = StickerManager.ALL_STICKERS.size
            val earned  = ownedKeys.size
            Spacer(Modifier.height(8.dp))
            Row(
                modifier            = Modifier.fillMaxWidth(),
                verticalAlignment   = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text  = "Collected $earned / $total",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text  = "${"%.0f".format(earned.toFloat() / total * 100)}%",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            LinearProgressIndicator(
                progress      = { earned.toFloat() / total },
                modifier      = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            )

            Spacer(Modifier.height(4.dp))

            // ── 3-column sticker grid ────────────────────────────────────────
            LazyVerticalGrid(
                columns             = GridCells.Fixed(3),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding      = PaddingValues(vertical = 8.dp),
            ) {
                items(StickerManager.ALL_STICKERS) { sticker ->
                    val isOwned = sticker.key in ownedKeys
                    StickerCell(sticker = sticker, isOwned = isOwned)
                }
            }
        }
    }
}

@Composable
private fun StickerCell(sticker: StickerManager.Sticker, isOwned: Boolean) {
    val alpha = if (isOwned) 1f else 0.28f

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.8f),
        colors = CardDefaults.cardColors(
            containerColor = if (isOwned)
                MaterialTheme.colorScheme.secondaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier            = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text  = if (isOwned || !sticker.secret) sticker.icon else "❓",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text       = if (isOwned || !sticker.secret) sticker.name else "???",
                style      = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign  = TextAlign.Center,
                color      = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
            )
            if (isOwned) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text      = sticker.description,
                    style     = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    color     = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines  = 3,
                )
            }
        }
    }
}
