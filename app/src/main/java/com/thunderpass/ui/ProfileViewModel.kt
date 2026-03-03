package com.thunderpass.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.thunderpass.ble.RotatingIdManager
import com.thunderpass.data.db.ThunderPassDatabase
import com.thunderpass.data.db.entity.MyProfile
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProfileViewModel(app: Application) : AndroidViewModel(app) {

    private val profileDao = ThunderPassDatabase.getInstance(app).myProfileDao()

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
