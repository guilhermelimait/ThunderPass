package com.thunderpass.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.thunderpass.ble.RotatingIdManager
import com.thunderpass.data.db.ThunderPassDatabase
import com.thunderpass.data.db.entity.MyProfile
import com.thunderpass.supabase.ProfileRecord
import com.thunderpass.supabase.SupabaseManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProfileViewModel(app: Application) : AndroidViewModel(app) {

    private val profileDao   = ThunderPassDatabase.getInstance(app).myProfileDao()
    private val encounterDao = ThunderPassDatabase.getInstance(app).encounterDao()

    /** Current profile, always emits at least the default. */
    val profile: StateFlow<MyProfile> = profileDao.observe()
        .filterNotNull()
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            MyProfile(installationId = RotatingIdManager(app).installationId)
        )

    /**
     * Persist the user's edited profile locally, then push to Supabase in the background.
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
            syncToSupabase()
        }
    }

    /**
     * Push the current local profile + stats to the `profiles` table in Supabase.
     * Silently skipped if the user has no active session.
     */
    fun syncToSupabase() {
        viewModelScope.launch {
            val userId = SupabaseManager.client.auth.currentSessionOrNull()?.user?.id ?: return@launch
            val p      = profileDao.get() ?: return@launch
            val count  = encounterDao.countAll()

            runCatching {
                SupabaseManager.client.from("profiles").upsert(
                    ProfileRecord(
                        id             = userId,
                        installationId = p.installationId,
                        displayName    = p.displayName,
                        greeting       = p.greeting,
                        avatarKind     = p.avatarKind,
                        avatarColor    = p.avatarColor,
                        joulesTotal    = p.joulesTotal,
                        retroUsername  = p.retroUsername,
                        ghostGame      = p.ghostGame,
                        ghostScore     = p.ghostScore,
                        stickersJson   = p.stickersJson,
                        encounterCount = count,
                        updatedAt      = System.currentTimeMillis() / 1000,
                    )
                )
            }
        }
    }

    /**
     * Returns true if this is a first-run (profile is null or still has all defaults).
     */
    suspend fun isFirstRun(): Boolean {
        val profile = profileDao.get() ?: return true
        return profile.displayName == "Traveler" &&
               profile.greeting    == "Hey, greetings from ThunderPass!"
    }
}
