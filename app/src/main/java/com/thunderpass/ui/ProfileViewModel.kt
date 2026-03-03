package com.thunderpass.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.thunderpass.ble.RotatingIdManager
import com.thunderpass.data.db.ThunderPassDatabase
import com.thunderpass.data.db.entity.MyProfile
import com.thunderpass.github.DeviceFlowState
import com.thunderpass.github.GistSyncManager
import com.thunderpass.github.ThunderCard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProfileViewModel(app: Application) : AndroidViewModel(app) {

    private val profileDao   = ThunderPassDatabase.getInstance(app).myProfileDao()
    private val encounterDao = ThunderPassDatabase.getInstance(app).encounterDao()
    private val gistSync     = GistSyncManager.getInstance(app)

    // ── GitHub Gist Sync ──────────────────────────────────────────────────────
    private val _githubUsername = MutableStateFlow(gistSync.getUsername() ?: "")
    val githubUsername: StateFlow<String> = _githubUsername.asStateFlow()

    private val _deviceFlowState = MutableStateFlow<DeviceFlowState>(
        if (gistSync.isConnected()) DeviceFlowState.Connected(gistSync.getUsername() ?: "")
        else DeviceFlowState.Idle
    )
    val deviceFlowState: StateFlow<DeviceFlowState> = _deviceFlowState.asStateFlow()

    /** Kick off the GitHub Device Flow (step 1: get code, step 2: poll). */
    fun startDeviceFlow() {
        viewModelScope.launch {
            val step1 = gistSync.requestDeviceCode()
            _deviceFlowState.value = step1
            if (step1 is DeviceFlowState.AwaitingCode) {
                val result = gistSync.pollUntilAuthorized(step1.deviceCode, step1.interval)
                _deviceFlowState.value = result
                if (result is DeviceFlowState.Connected) {
                    _githubUsername.value = result.username
                }
            }
        }
    }

    fun disconnectGitHub() {
        gistSync.disconnect()
        _githubUsername.value = ""
        _deviceFlowState.value = DeviceFlowState.Idle
    }

    /** Push the current profile + stats to the user's ThunderPass Gist. */
    fun syncToGist() {
        viewModelScope.launch {
            val p = profileDao.get() ?: return@launch
            val count = encounterDao.countAll()
            gistSync.push(
                ThunderCard(
                    displayName     = p.displayName,
                    greeting        = p.greeting,
                    avatarKind      = p.avatarKind,
                    avatarColor     = p.avatarColor,
                    ghostGame       = p.ghostGame,
                    retroUsername   = p.retroUsername,
                    joules          = p.joulesTotal,
                    encounterCount  = count,
                    encounterStreak = 0,
                    stickers        = p.stickersJson.split(",").filter { it.isNotBlank() },
                    updatedAt       = System.currentTimeMillis() / 1000,
                )
            )
        }
    }
    // ─────────────────────────────────────────────────────────────────────────

    /** Current profile, always emits at least the default. */
    val profile: StateFlow<MyProfile> = profileDao.observe()
        .filterNotNull()
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            MyProfile(installationId = RotatingIdManager(app).installationId)
        )

    /**
     * Persist the user's edited profile.
     * Preserves installationId and id from the existing row.
     */
    fun save(
        displayName:   String,
        greeting:      String,
        retroUsername: String = "",
        ghostGame:     String = "",
        ghostScore:    Long   = 0L,
    ) {
        viewModelScope.launch {
            val current = profileDao.get()
                ?: MyProfile(installationId = RotatingIdManager(getApplication()).installationId)
            profileDao.upsert(
                current.copy(
                    displayName   = displayName.trim().ifEmpty { "Traveler" },
                    greeting      = greeting.trim(),
                    retroUsername = retroUsername.trim(),
                    ghostGame     = ghostGame.trim(),
                    ghostScore    = ghostScore,
                    updatedAt     = System.currentTimeMillis() / 1000,
                )
            )
        }
    }

    /**
     * Returns true if this is a first-run (profile is null or still has all defaults).
     * Called synchronously from the splash navigation check.
     */
    suspend fun isFirstRun(): Boolean {
        val profile = profileDao.get() ?: return true
        return profile.displayName == "Traveler" &&
               profile.greeting    == "Hey, greetings from ThunderPass!"
    }
}
