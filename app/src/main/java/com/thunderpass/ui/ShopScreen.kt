package com.thunderpass.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopScreen(
    onBack: () -> Unit,
    vm: HomeViewModel = viewModel(),
) {
    val joulesTotal    by vm.joulesTotal.collectAsState()
    val unlockedEffects by vm.unlockedEffects.collectAsState()
    var confirmEffect  by remember { mutableStateOf<ShopItem?>(null) }
    var toastMessage   by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Visual Shop") },
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
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            // ── Balance banner ───────────────────────────────────────────────
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color    = MaterialTheme.colorScheme.primaryContainer,
                shape    = MaterialTheme.shapes.medium,
            ) {
                Row(
                    modifier            = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment   = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text       = "⚡ Your Energy",
                        style      = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color      = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        text       = "%,d J".format(joulesTotal),
                        style      = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color      = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }

            Text(
                text  = "Unlock visual profile effects — spent Joules are permanent.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // ── Shop items ───────────────────────────────────────────────────
            SHOP_ITEMS.forEach { item ->
                val isUnlocked = item.key in unlockedEffects
                ShopItemCard(
                    item       = item,
                    isUnlocked = isUnlocked,
                    canAfford  = joulesTotal >= item.price,
                    onBuy      = { confirmEffect = item },
                )
            }

            // ── Toast ────────────────────────────────────────────────────────
            toastMessage?.let { msg ->
                Snackbar(
                    modifier    = Modifier.padding(top = 8.dp),
                    action      = { TextButton(onClick = { toastMessage = null }) { Text("OK") } },
                ) { Text(msg) }
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    // ── Purchase confirmation dialog ─────────────────────────────────────────
    confirmEffect?.let { item ->
        AlertDialog(
            onDismissRequest = { confirmEffect = null },
            title   = { Text("Unlock ${item.name}?") },
            text    = {
                Text("Spend ${"%,d".format(item.price)} J to permanently unlock this effect. You currently have ${"%,d".format(joulesTotal)} J.")
            },
            confirmButton = {
                TextButton(onClick = {
                    val success = vm.spendJoules(item.price, item.key)
                    toastMessage = if (success) "🎉 ${item.name} unlocked!" else "Not enough Joules!"
                    confirmEffect = null
                }) { Text("Unlock") }
            },
            dismissButton = {
                TextButton(onClick = { confirmEffect = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun ShopItemCard(
    item: ShopItem,
    isUnlocked: Boolean,
    canAfford: Boolean,
    onBuy: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(
            containerColor = if (isUnlocked)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            else
                MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier            = Modifier.padding(16.dp),
            verticalAlignment   = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(item.icon, style = MaterialTheme.typography.displaySmall)

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text       = item.name,
                    style      = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text  = item.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text  = "%,d J".format(item.price),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            if (isUnlocked) {
                AssistChip(
                    onClick  = {},
                    enabled  = false,
                    label    = { Text("Owned") },
                    leadingIcon = { Text("✓") },
                )
            } else {
                Button(
                    onClick  = onBuy,
                    enabled  = canAfford,
                ) {
                    Text("Buy")
                }
            }
        }
    }
}

// ── Static shop catalogue ────────────────────────────────────────────────────
data class ShopItem(
    val key:         String,
    val icon:        String,
    val name:        String,
    val description: String,
    val price:       Long,
)

val SHOP_ITEMS = listOf(
    ShopItem(
        key         = "crt_scanlines",
        icon        = "📺",
        name        = "CRT Scanlines",
        description = "Overlays a retro scanline effect on your Spark Card — show off your old-school roots.",
        price       = 500L,
    ),
    ShopItem(
        key         = "pixelated_aura",
        icon        = "✨",
        name        = "Pixelated Aura",
        description = "Surrounds your avatar with a shimmering pixel-art glow. You earned it.",
        price       = 1_000L,
    ),
    ShopItem(
        key         = "thunder_trail",
        icon        = "⚡",
        name        = "Thunder Trail",
        description = "Electrified entry animation when your Spark Card loads on another player's screen.",
        price       = 2_500L,
    ),
)
