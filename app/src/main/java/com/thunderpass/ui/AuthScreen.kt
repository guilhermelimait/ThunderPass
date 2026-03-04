package com.thunderpass.ui

import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.thunderpass.R
import com.thunderpass.supabase.SupabaseManager
import io.github.jan.supabase.compose.auth.composable.NativeSignInResult
import io.github.jan.supabase.compose.auth.composable.rememberSignInWithGoogle
import io.github.jan.supabase.compose.auth.composeAuth

/**
 * Two-step email OTP auth screen:
 *   Step 1 — user enters their email address → Supabase sends a 6-digit code
 *   Step 2 — user types the code from their inbox → session is established for ~30 days
 *
 * [onAuthenticated] is called once the session is confirmed. Navigation happens there.
 */
@Composable
fun AuthScreen(
    onAuthenticated: () -> Unit,
    onSkip: () -> Unit = {},
    vm: AuthViewModel = viewModel(),
) {
    val context = LocalContext.current
    val state by vm.state.collectAsState()

    // Detect Google Play Services — absent on most retro gaming handhelds
    val hasGooglePlayServices = remember {
        try {
            context.packageManager.getApplicationInfo("com.google.android.gms", 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    var googleLoading by remember { mutableStateOf(false) }
    var googleErrorMsg by remember { mutableStateOf<String?>(null) }

    // Google One-Tap sign-in launcher (wired to Supabase ComposeAuth)
    val googleSignInState = SupabaseManager.client.composeAuth.rememberSignInWithGoogle(
        onResult = { result ->
            googleLoading = false
            when (result) {
                is NativeSignInResult.Success      -> vm.onGoogleSignInSuccess()
                is NativeSignInResult.Error        -> googleErrorMsg = result.message
                is NativeSignInResult.ClosedByUser -> googleErrorMsg =
                    "Google sign-in unavailable. Make sure a Google account is signed in on this device and that Google sign-in is enabled in the app dashboard."
                is NativeSignInResult.NetworkError -> googleErrorMsg = "Network error — check your connection and try again."
            }
        }
    )

    // Auto-navigate when auth succeeds (e.g. session already exists on relaunch)
    LaunchedEffect(state) {
        if (state is AuthState.Authenticated) onAuthenticated()
    }

    // Show Google error as a snackbar-style overlay
    if (googleErrorMsg != null) {
        AlertDialog(
            onDismissRequest = { googleErrorMsg = null },
            title = { Text("Google Sign-In") },
            text  = { Text(googleErrorMsg!!) },
            confirmButton = {
                TextButton(onClick = { googleErrorMsg = null }) { Text("OK") }
            },
        )
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier              = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            verticalArrangement   = Arrangement.Center,
            horizontalAlignment   = Alignment.CenterHorizontally,
        ) {
            // ── Logo ─────────────────────────────────────────────────────────
            Image(
                painter            = painterResource(R.drawable.logo),
                contentDescription = "ThunderPass",
                modifier           = Modifier.height(90.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text  = "Sign in with your email to sync your profile card",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(40.dp))

            when (val s = state) {
                // ── Step 1: email input ───────────────────────────────────────
                is AuthState.Idle, is AuthState.Error -> {
                    var email by remember { mutableStateOf("") }

                    if (s is AuthState.Error) {
                        Text(
                            text  = s.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(12.dp))
                    }

                    OutlinedTextField(
                        value         = email,
                        onValueChange = { email = it },
                        label         = { Text("Email address") },
                        singleLine    = true,
                        modifier      = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction    = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { if (email.isNotBlank()) vm.requestOtp(email) }
                        ),
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick  = { vm.requestOtp(email) },
                        enabled  = email.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Send code")
                    }

                    // ── OR divider ────────────────────────────────────────────
                    Spacer(Modifier.height(20.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier          = Modifier.fillMaxWidth(),
                    ) {
                        HorizontalDivider(modifier = Modifier.weight(1f))
                        Text(
                            text     = "  or  ",
                            style    = MaterialTheme.typography.bodySmall,
                            color    = MaterialTheme.colorScheme.outline,
                        )
                        HorizontalDivider(modifier = Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(16.dp))

                    // ── Google One-Tap (only shown when GMS available) ────────
                    if (hasGooglePlayServices) {
                        OutlinedButton(
                            onClick  = {
                                googleLoading = true
                                googleErrorMsg = null
                                googleSignInState.startFlow()
                            },
                            enabled  = !googleLoading,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (googleLoading) {
                                CircularProgressIndicator(
                                    modifier  = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                )
                                Spacer(Modifier.width(10.dp))
                                Text("Signing in…")
                            } else {
                                Image(
                                    painter            = painterResource(R.drawable.ic_google),
                                    contentDescription = null,
                                    modifier           = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(10.dp))
                                Text("Continue with Google")
                            }
                        }
                    } else {
                        // GMS not available (retro gaming handheld) — use email OTP
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors   = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            ),
                        ) {
                            Text(
                                text      = "Google sign-in is not available on this device. Use email sign-in above — it works without Google Play Services.",
                                style     = MaterialTheme.typography.bodySmall,
                                color     = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier  = Modifier.padding(12.dp),
                                textAlign = TextAlign.Center,
                            )
                        }
                    }

                    // ── Skip option ───────────────────────────────────────────
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        onClick  = onSkip,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text  = "Skip for now",
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                }

                // ── Loading spinner ───────────────────────────────────────────
                is AuthState.Loading -> {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text  = "Please wait…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }

                // ── Step 2: OTP code input ────────────────────────────────────
                is AuthState.AwaitingOtp -> {
                    val focusRequester = remember { FocusRequester() }
                    var code by remember { mutableStateOf("") }
                    var codeError by remember { mutableStateOf(false) }

                    LaunchedEffect(Unit) { focusRequester.requestFocus() }

                    Text(
                        text      = "Check your inbox",
                        style     = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text      = "We sent an 8-digit code to\n${s.email}",
                        style     = MaterialTheme.typography.bodySmall,
                        color     = MaterialTheme.colorScheme.outline,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(24.dp))
                    OutlinedTextField(
                        value         = code,
                        onValueChange = {
                            if (it.length <= 8 && it.all(Char::isDigit)) {
                                code      = it
                                codeError = false
                                if (it.length == 8) vm.verifyOtp(s.email, it)
                            }
                        },
                        label         = { Text("8-digit code") },
                        isError       = codeError,
                        singleLine    = true,
                        modifier      = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.NumberPassword,
                            imeAction    = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (code.length == 8) vm.verifyOtp(s.email, code)
                                else codeError = true
                            }
                        ),
                        textStyle = MaterialTheme.typography.headlineMedium.copy(
                            textAlign = TextAlign.Center,
                            letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified,
                        ),
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick  = {
                            if (code.length == 8) vm.verifyOtp(s.email, code)
                            else codeError = true
                        },
                        enabled  = code.length == 8,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Confirm")
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { vm.resetToEmail() }) {
                        Text("Use a different email")
                    }
                }

                // Authenticated — LaunchedEffect handles navigation
                is AuthState.Authenticated -> {
                    CircularProgressIndicator()
                }
            }
        }
    }
}
