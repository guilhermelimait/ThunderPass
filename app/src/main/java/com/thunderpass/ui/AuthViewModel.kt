package com.thunderpass.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thunderpass.supabase.SupabaseManager
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.OTP
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Represents each step of the email OTP sign-in flow. */
sealed class AuthState {
    /** Not started — show email input. */
    object Idle : AuthState()
    /** Waiting for the API call to return. */
    object Loading : AuthState()
    /** Code was sent — show the 6-digit OTP input. */
    data class AwaitingOtp(val email: String) : AuthState()
    /** Fully authenticated with a valid session. */
    object Authenticated : AuthState()
    /** Something went wrong — show error and let user retry. */
    data class Error(val message: String) : AuthState()
}

class AuthViewModel : ViewModel() {

    private val auth = SupabaseManager.client.auth

    private val _state = MutableStateFlow<AuthState>(
        if (auth.currentSessionOrNull() != null) AuthState.Authenticated else AuthState.Idle
    )
    val state: StateFlow<AuthState> = _state.asStateFlow()

    init {
        // React to session changes triggered externally — e.g. magic link deep link
        // processed by handleDeeplinks() in MainActivity before the ViewModel existed,
        // or arriving while the app is already open.
        viewModelScope.launch {
            auth.sessionStatus.collect { status ->
                if (status is SessionStatus.Authenticated &&
                    _state.value !is AuthState.Authenticated
                ) {
                    _state.value = AuthState.Authenticated
                }
            }
        }
    }

    /**
     * Step 1 — send a 6-digit OTP to [email].
     * Supabase creates the account automatically if it doesn't exist yet.
     */
    fun requestOtp(email: String) {
        viewModelScope.launch {
            _state.value = AuthState.Loading
            try {
                auth.signInWith(OTP) {
                    this.email = email.trim()
                    this.createUser = true
                }
                _state.value = AuthState.AwaitingOtp(email.trim())
            } catch (e: Exception) {
                _state.value = AuthState.Error(e.message ?: "Failed to send code")
            }
        }
    }

    /**
     * Step 2 — verify the 6-digit [code] the user received.
     * On success the session is persisted for ~30 days automatically.
     */
    fun verifyOtp(email: String, code: String) {
        viewModelScope.launch {
            _state.value = AuthState.Loading
            try {
                auth.verifyEmailOtp(
                    type  = OtpType.Email.EMAIL,
                    email = email,
                    token = code.trim(),
                )
                _state.value = AuthState.Authenticated
            } catch (e: Exception) {
                _state.value = AuthState.Error(e.message ?: "Invalid or expired code")
            }
        }
    }

    /** Called by AuthScreen after a successful Google One-Tap sign-in. */
    fun onGoogleSignInSuccess() {
        _state.value = AuthState.Authenticated
    }

    /** Called by AuthScreen when Google sign-in returns an error. */
    fun onGoogleSignInError(message: String) {
        _state.value = AuthState.Error(message)
    }

    /** Reset the OTP step so the user can re-enter their email. */
    fun resetToEmail() {
        _state.value = AuthState.Idle
    }

    /** Sign out — clears the local session. */
    fun signOut() {
        viewModelScope.launch {
            runCatching { auth.signOut() }
            _state.value = AuthState.Idle
        }
    }
}
