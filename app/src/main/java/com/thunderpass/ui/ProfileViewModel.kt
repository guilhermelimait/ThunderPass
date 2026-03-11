package com.thunderpass.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.thunderpass.ble.RotatingIdManager
import com.thunderpass.data.BadgeManager
import com.thunderpass.data.db.ThunderPassDatabase
import com.thunderpass.data.db.entity.MyProfile
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

    init {
        // 5.1 — Auto-seed displayName from device name on first launch.
        // If the profile still has the hardcoded "SparkyUser" default (i.e. user
        // has never customised it), replace it with the device's friendly name
        // so BLE payloads and all UI show a meaningful name immediately.
        viewModelScope.launch {
            val p = profileDao.get() ?: return@launch
            val nameIsDefault = p.displayName.isBlank() || p.displayName == "SparkyUser"
            if (nameIsDefault) {
                val deviceName = android.provider.Settings.Global.getString(
                    getApplication<Application>().contentResolver,
                    android.provider.Settings.Global.DEVICE_NAME,
                )?.takeIf { it.isNotBlank() } ?: android.os.Build.MODEL
                profileDao.upsert(p.copy(displayName = deviceName))
            }
            // Auto-init greeting from device name if still at factory default.
            val p2 = profileDao.get() ?: p
            val greetingIsDefault = p2.greeting.isBlank() || p2.greeting == "Hey, greetings from ThunderPass!"
            if (greetingIsDefault) {
                val greetingName = p2.deviceType.ifBlank {
                    android.provider.Settings.Global.getString(
                        getApplication<Application>().contentResolver,
                        android.provider.Settings.Global.DEVICE_NAME,
                    )?.takeIf { it.isNotBlank() } ?: android.os.Build.MODEL
                }
                profileDao.upsert(p2.copy(greeting = "Hey, greetings from $greetingName"))
            }
            // Auto-detect + persist device type if not set yet
            if (p.deviceType.isBlank()) {
                profileDao.upsert(
                    (profileDao.get() ?: p).copy(deviceType = detectDeviceType())
                )
            }
            // Auto-generate a sparky avatar seed on first install so the profile card
            // and SparkyEditor sliders always agree. The fallback (empty → installationId)
            // showed a DiceBear UUID avatar that sliders could never reproduce.
            val p3 = profileDao.get() ?: p
            if (p3.avatarSeed.isBlank()) {
                profileDao.upsert(p3.copy(avatarSeed = randomSparkySeed()))
            }
        }
    }

    /**
     * Returns a human-friendly device type string based on Build properties.
     * E.g. "AYN Thor 2", "Retroid Pocket 4 Pro", or the raw model if unknown.
     */
    private fun detectDeviceType(): String {
        val manufacturer = android.os.Build.MANUFACTURER.trim()
        val model        = android.os.Build.MODEL.trim()
        val brand        = android.os.Build.BRAND.trim()
        // Normalise model string (replace underscores with spaces)
        val modelNorm = model.replace('_', ' ')
        return when {
            manufacturer.equals("AYN", ignoreCase = true) ||
            brand.equals("AYN", ignoreCase = true)        -> "AYN $modelNorm"
            modelNorm.contains("Retroid", ignoreCase = true) ||
            modelNorm.startsWith("RP", ignoreCase = true) -> modelNorm
            brand.equals("Anbernic", ignoreCase = true) ||
            manufacturer.equals("Anbernic", ignoreCase = true) -> "Anbernic $modelNorm"
            brand.equals("Powkiddy", ignoreCase = true) ||
            manufacturer.equals("Powkiddy", ignoreCase = true) -> "Powkiddy $modelNorm"
            else -> "$manufacturer $modelNorm".trim()
        }
    }

    /**
     * Persist the user's edited profile locally.
     * Preserves installationId and id from the existing row.
     */
    fun save(
        displayName:   String,
        retroUsername: String = "",
        raApiKey:      String = "",
        avatarSeed:    String = "",
        greeting:      String = "",
        country:       String = "",
        city:          String = "",
    ) {
        viewModelScope.launch {
            val current = profileDao.get()
                ?: MyProfile(installationId = RotatingIdManager(getApplication()).installationId)
            profileDao.upsert(
                current.copy(
                    displayName   = displayName.trim().ifEmpty { android.os.Build.MODEL },
                    retroUsername = retroUsername.trim(),
                    raApiKey      = raApiKey.trim().ifEmpty { current.raApiKey },
                    avatarSeed    = avatarSeed.ifEmpty { current.avatarSeed },
                    greeting      = greeting.trim().ifEmpty { current.greeting },
                    country       = country.trim().uppercase().take(2),
                    city          = city.trim(),
                    updatedAt     = System.currentTimeMillis() / 1000,
                )
            )
        }
    }

    /**
     * Immediately persists a new avatar seed to the local DB without requiring
     * the user to press "Save Profile".  The change propagates to the Home screen's
     * walking animation and the nav-bar avatar icon through [HomeViewModel.avatarSeed],
     * which observes the same DB flow.
     *
     * Also bumps [MyProfile.updatedAt] so the local timestamp is up to date.
     */
    fun saveAvatarSeed(seed: String) {
        viewModelScope.launch {
            val current = profileDao.get() ?: return@launch
            if (current.avatarSeed == seed) return@launch
            profileDao.upsert(current.copy(
                avatarSeed = seed,
                updatedAt  = System.currentTimeMillis() / 1000,
            ))
        }
    }

    /**
     * Returns true if this is a first-run (profile is null or still has all defaults).
     * "SparkyUser" is the entity default — treat it the same as a blank name so new
     * users are always routed through onboarding to set their actual name.
     */
    suspend fun isFirstRun(): Boolean {
        val profile = profileDao.get() ?: return true
        val nameIsDefault = profile.displayName.isBlank() || profile.displayName == "SparkyUser"
        return nameIsDefault && profile.greeting == "Hey, greetings from ThunderPass!"
    }
}
