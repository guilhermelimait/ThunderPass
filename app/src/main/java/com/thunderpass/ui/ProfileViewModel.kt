package com.thunderpass.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.thunderpass.ble.RotatingIdManager
import com.thunderpass.data.BadgeManager
import com.thunderpass.data.db.ThunderPassDatabase
import com.thunderpass.data.db.entity.MyProfile
import com.thunderpass.security.PayloadSigner
import com.thunderpass.supabase.ProfileRecord
import com.thunderpass.supabase.PublicKeyPatch
import com.thunderpass.supabase.SupabaseManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
        // Ensure every device has a Supabase session (anonymous if never signed in),
        // then pull the server profile and push back any local changes.
        viewModelScope.launch {
            ensureAnonymousSession()
            syncFromSupabase()
            syncToSupabase()
        }
    }

    /**
     * Signs in anonymously if no session exists yet.
     * This gives every device a stable server-side UUID without requiring the
     * user to register an account. The UUID is used for encounter verification
     * and 24-hour identity deduplication across all users.
     *
     * The session is auto-refreshed every 30 days by the Supabase SDK.
     * If the app is uninstalled or the device is factory-reset, the anonymous
     * session is lost and a new UUID is assigned on next launch.
     */
    private suspend fun ensureAnonymousSession() {
        runCatching {
            if (SupabaseManager.client.auth.currentSessionOrNull() == null) {
                SupabaseManager.client.auth.signInAnonymously()
            }
        }
    }

    /**
     * Pull the authenticated user's profile from Supabase and merge it into
     * the local Room DB. This makes the server the source of truth: if the
     * user reinstalls the app (or gets a new device with the same account),
     * their display name, greeting, avatar, volts, stickers etc. are restored
     * automatically on first connection.
     *
     * Merge strategy:
     * - Only applied when the server record's [updatedAt] is newer than the
     *   local row, so a locally-edited profile is never silently overwritten.
     * - Volts are always set to `max(local, server)` — they can only go up.
     */
    private suspend fun syncFromSupabase() {
        val userId = runCatching {
            SupabaseManager.client.auth.currentSessionOrNull()?.user?.id
        }.getOrNull() ?: return

        val records = runCatching {
            SupabaseManager.client.from("profiles")
                .select { filter { eq("id", userId) } }
                .decodeList<ProfileRecord>()
        }.getOrNull() ?: return

        val remote = records.firstOrNull() ?: return
        val local  = profileDao.get() ?: return

        // Only overwrite local data if server record is strictly newer
        if (remote.updatedAt <= local.updatedAt) return

        profileDao.upsert(
            local.copy(
                displayName   = remote.displayName.ifBlank { local.displayName },
                greeting      = remote.greeting.ifBlank { local.greeting },
                avatarKind    = remote.avatarKind.ifBlank { local.avatarKind },
                avatarColor   = remote.avatarColor.ifBlank { local.avatarColor },
                avatarSeed    = remote.avatarSeed.ifBlank { local.avatarSeed },
                retroUsername = remote.retroUsername.ifBlank { local.retroUsername },
                ghostGame     = remote.ghostGame.ifBlank { local.ghostGame },
                ghostScore    = if (remote.ghostScore > 0L) remote.ghostScore else local.ghostScore,
                stickersJson  = remote.stickersJson.ifBlank { local.stickersJson },
                voltsTotal    = maxOf(local.voltsTotal, remote.voltsTotal),
                updatedAt     = remote.updatedAt,
            )
        )
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
     * Persist the user's edited profile locally, then push to Supabase in the background.
     * Preserves installationId and id from the existing row.
     */
    fun save(
        displayName:   String,
        retroUsername: String = "",
        raApiKey:      String = "",
        avatarSeed:    String = "",
        greeting:      String = "",
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

            // Persist the Supabase userId locally so GattServer can broadcast it
            // in the GATT payload for 24-hour peer identity dedup.
            if (p.supabaseUserId != userId) {
                profileDao.updateSupabaseUserId(userId)
            }

            // Ensure this device has a P-256 signing key pair in the Android Keystore
            // and publish the public key to Supabase (profiles.public_key column).
            // The public key allows peers to verify payload signatures without any
            // server-side function — all verification happens client-side.
            val pubKey = runCatching { PayloadSigner.ensureKeyPairAndGetPublicKey() }.getOrNull()
            if (pubKey != null && p.payloadPublicKey != pubKey) {
                profileDao.updatePayloadPublicKey(pubKey)
            }

            runCatching {
                SupabaseManager.client.from("profiles").upsert(
                    ProfileRecord(
                        id             = userId,
                        installationId = p.installationId,
                        displayName    = p.displayName,
                        greeting       = p.greeting,
                        avatarKind     = p.avatarKind,
                        avatarColor    = p.avatarColor,
                        avatarSeed     = p.avatarSeed,
                        voltsTotal     = p.voltsTotal,
                        retroUsername  = p.retroUsername,
                        ghostGame      = p.ghostGame,
                        ghostScore     = p.ghostScore,
                        stickersJson   = p.stickersJson,
                        encounterCount = count,
                        updatedAt      = System.currentTimeMillis() / 1000,
                    )
                )
            }
            // Publish the device public key via a targeted upsert that only touches
            // `id` + `public_key`, leaving all other profile columns untouched.
            if (pubKey != null) {
                runCatching {
                    SupabaseManager.client.from("profiles")
                        .upsert(PublicKeyPatch(id = userId, publicKey = pubKey))
                }
            }
        }
    }

    /**
     * Fetches the local user's own RetroAchievements profile and caches it in
     * SharedPreferences via [RetroProfileCache] so the RA gallery shows fresh data.
     * Credentials must already be saved in [RetroAuthManager] before calling this.
     * Safe to call after every profile save — silently skips if creds are missing.
     */
    fun fetchAndCacheOwnRetroProfile() {
        viewModelScope.launch {
            val app          = getApplication<android.app.Application>()
            val auth         = com.thunderpass.retro.RetroAuthManager.getInstance(app)
            val p            = profileDao.get() ?: return@launch
            val raUsername   = p.retroUsername.trim()
            if (raUsername.isBlank()) return@launch
            // Fetch summary + softcore achievement count in parallel
            val (result, softcoreAchievements) = coroutineScope {
                val summaryDeferred      = async { com.thunderpass.retro.RetroRetrofitClient.fetchRetroMetadata(raUsername, auth) }
                val achievementsDeferred = async { com.thunderpass.retro.RetroRetrofitClient.fetchSoftcoreAchievementCount(raUsername, auth) }
                summaryDeferred.await() to achievementsDeferred.await()
            }
            result.getOrNull()?.let { raProfile ->
                com.thunderpass.retro.RetroProfileCache.save(
                    context                    = app,
                    username                   = raUsername,
                    points                     = raProfile.totalPoints,
                    softcorePoints             = raProfile.totalSoftcorePoints,
                    softcoreAchievementsEarned = softcoreAchievements,
                    games                      = raProfile.recentlyPlayed ?: emptyList(),
                    recentlyPlayedCount        = raProfile.recentlyPlayedCount,
                )
                // Grant "Shared Quest" badge: user has linked RA and loaded game data.
                BadgeManager.award(app, "shared_quest")
            }
        }
    }

    /**
     * Immediately persists a new avatar seed to the local DB without requiring
     * the user to press "Save Profile".  The change propagates to the Home screen's
     * walking animation and the nav-bar avatar icon through [HomeViewModel.avatarSeed],
     * which observes the same DB flow.
     *
     * Also bumps [MyProfile.updatedAt] and pushes to Supabase so that the cloud
     * record always reflects the latest avatar — otherwise a DB reset or a second
     * device would restore the stale avatar from Supabase on the next login.
     */
    fun saveAvatarSeed(seed: String) {
        viewModelScope.launch {
            val current = profileDao.get() ?: return@launch
            if (current.avatarSeed == seed) return@launch
            profileDao.upsert(current.copy(
                avatarSeed = seed,
                updatedAt  = System.currentTimeMillis() / 1000,
            ))
            syncToSupabase()
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
