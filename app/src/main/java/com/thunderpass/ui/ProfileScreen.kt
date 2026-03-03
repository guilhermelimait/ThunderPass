package com.thunderpass.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.thunderpass.retro.RetroAuthManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onBack:              () -> Unit,
    onNavigateToHome:    () -> Unit = onBack,
    onNavigateToEncounters: () -> Unit = {},
    firstRun:            Boolean      = false,
    onComplete:          (() -> Unit)? = null,
    vm: ProfileViewModel = viewModel(),
) {
    val profile by vm.profile.collectAsState()

    var draftName     by remember(profile.displayName) { mutableStateOf(profile.displayName) }
    var draftGreeting by remember(profile.greeting)     { mutableStateOf(profile.greeting) }
    var draftRetroUsername by remember(profile.retroUsername) { mutableStateOf(profile.retroUsername) }
    val context = LocalContext.current
    val retroAuth = remember { RetroAuthManager.getInstance(context) }
    var draftRaApiKey  by remember { mutableStateOf(retroAuth.getApiKey()) }
    var draftRaApiUser by remember { mutableStateOf(retroAuth.getApiUser()) }
    var saved by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (firstRun) "Set Up Profile" else "My Profile") },
                navigationIcon = {
                    if (!firstRun) {
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
        bottomBar = {
            if (!firstRun) {
                NavigationBar {
                    NavigationBarItem(
                        selected = false,
                        onClick  = onNavigateToHome,
                        icon     = { Icon(Icons.Filled.Home, contentDescription = "Home") },
                        label    = { Text("Home") },
                    )
                    NavigationBarItem(
                        selected = false,
                        onClick  = onNavigateToEncounters,
                        icon     = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Encounters") },
                        label    = { Text("Encounters") },
                    )
                    NavigationBarItem(
                        selected = true,
                        onClick  = { /* already here */ },
                        icon     = { Icon(Icons.Filled.Person, contentDescription = "Profile") },
                        label    = { Text("Profile") },
                    )
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(16.dp))

            if (firstRun) {
                Card(
                    colors   = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text     = "Welcome to ThunderPass! \uD83D\uDC4B\nSet up your profile to get started.",
                        style    = MaterialTheme.typography.bodyMedium,
                        color    = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }

            DiceBearAvatar(
                seed = profile.installationId.ifEmpty { "default" },
                size = 96.dp,
            )

            Text(
                text  = "Your avatar is auto-generated and unique to you.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

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
                value               = draftRaApiKey,
                onValueChange       = { draftRaApiKey = it; saved = false },
                label               = { Text("RA API Key") },
                singleLine          = true,
                modifier            = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(),
                supportingText      = { Text("From retroachievements.org/settings — kept encrypted on device") },
            )

            OutlinedTextField(
                value          = draftRaApiUser,
                onValueChange  = { draftRaApiUser = it; saved = false },
                label          = { Text("RA API Username") },
                singleLine     = true,
                modifier       = Modifier.fillMaxWidth(),
                supportingText = { Text("Usually the same as your RA username") },
            )

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick  = {
                    vm.save(draftName, draftGreeting, draftRetroUsername)
                    if (draftRaApiKey.isNotBlank() && draftRaApiUser.isNotBlank()) {
                        retroAuth.saveCredentials(draftRaApiKey.trim(), draftRaApiUser.trim())
                    }
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
