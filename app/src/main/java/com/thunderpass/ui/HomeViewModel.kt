package com.thunderpass.ui

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.thunderpass.BleService
import com.thunderpass.ble.ScanMode
import com.thunderpass.data.db.ThunderPassDatabase
import com.thunderpass.data.db.entity.Encounter
import com.thunderpass.data.db.entity.PeerProfileSnapshot
import com.thunderpass.retro.RetroAuthManager
import com.thunderpass.retro.RetroRepository
import com.thunderpass.supabase.OtaChecker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

data class EncounterWithProfile(
    val encounter: Encounter,
    val snapshot: PeerProfileSnapshot?,
)

class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val db = ThunderPassDatabase.getInstance(app)
    private val encounterDao = db.encounterDao()
    private val snapshotDao  = db.peerProfileSnapshotDao()
    private val profileDao   = db.myProfileDao()

    // ── SharedPreferences (Safe Zone persistence) ─────────────────────────────
    private val prefs = app.getSharedPreferences(BleService.PREFS_NAME, android.content.Context.MODE_PRIVATE)

    // ── Own profile observables ───────────────────────────────────────────────
    val installationId: StateFlow<String> = profileDao.observe()
        .filterNotNull()
        .map { it.installationId }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    /** The seed used to generate the user's DiceBear avatar. Falls back to installationId. */
    val avatarSeed: StateFlow<String> = profileDao.observe()
        .filterNotNull()
        .map { p -> p.avatarSeed.ifEmpty { p.installationId } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val displayName: StateFlow<String> = profileDao.observe()
        .filterNotNull()
        .map { p ->
            // Treat the never-customised default ("SparkyUser") as blank so the
            // device name is used until the user actually saves their own name.
            val saved = p.displayName
            if (saved.isBlank() || saved == "SparkyUser") {
                android.provider.Settings.Global.getString(
                    getApplication<Application>().contentResolver,
                    android.provider.Settings.Global.DEVICE_NAME
                )?.takeIf { it.isNotBlank() } ?: android.os.Build.MODEL
            } else saved
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, android.os.Build.MODEL)

    // ── Visual Shop unlocks ───────────────────────────────────────────────────
    private val _unlockedEffects = MutableStateFlow(
        prefs.getStringSet(PREF_SHOP_UNLOCKS, emptySet())?.toSet() ?: emptySet()
    )
    val unlockedEffects: StateFlow<Set<String>> = _unlockedEffects

    // ── Safe Zone ─────────────────────────────────────────────────────────────
    private val _safeZoneActive = MutableStateFlow(prefs.getBoolean(BleService.PREF_SAFE_ZONE, false))
    val safeZoneActive: StateFlow<Boolean> = _safeZoneActive

    // ── Service running state ─────────────────────────────────────────────────
    private val _serviceRunning = MutableStateFlow(prefs.getBoolean(BleService.PREF_SERVICE_ACTIVE, false))
    val serviceRunning: StateFlow<Boolean> = _serviceRunning

    // ── Scan mode ─────────────────────────────────────────────────────────────
    private val _scanMode = MutableStateFlow(
        runCatching { ScanMode.valueOf(prefs.getString(BleService.PREF_SCAN_MODE, ScanMode.BALANCED.name)!!) }
            .getOrDefault(ScanMode.BALANCED)
    )
    val scanMode: StateFlow<ScanMode> = _scanMode

    // ── Encounter count for the badge on the home screen ─────────────────────
    // Only confirmed encounters (GATT profile exchange succeeded) count toward the badge.
    val encounterCount: StateFlow<Int> = encounterDao.observeConfirmedCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    // ── Encounter streak ─────────────────────────────────────────────────────
    // Streak is computed only from confirmed encounters to avoid phantom BLE hits.
    val encounterStreak: StateFlow<Int> = encounterDao.observeConfirmed()
        .map { list -> computeStreak(list) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    // ── Energy (Volts) ────────────────────────────────────────────────────────
    val voltsTotal: StateFlow<Long> = profileDao.observe()
        .filterNotNull()
        .map { it.voltsTotal }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0L)

    // ── Owned Stickers ────────────────────────────────────────────────────────
    val ownedStickers: StateFlow<Set<String>> = profileDao.observe()
        .filterNotNull()
        .map { it.stickersJson.split(",").filter { k -> k.isNotBlank() }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    // ── Earned Badges ─────────────────────────────────────────────────────────
    /** Set of badge keys (from BadgeDef.key) the user has been dynamically awarded. */
    val earnedBadgeKeys: StateFlow<Set<String>> = profileDao.observe()
        .filterNotNull()
        .map { it.badgesJson.split(",").filter { k -> k.isNotBlank() }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    // ── Privacy mode (from Room MyProfile) ─────────────────────────────────
    val privacyMode: StateFlow<Boolean> = profileDao.observe()
        .filterNotNull()
        .map { it.privacyMode }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    // ── Full encounter list with resolved snapshots ─────────────────────────
    // Only confirmed encounters (peerSnapshotId != null) are surfaced — phantoms are excluded.
    // Display-level dedup: one card per unique peerUserId, keeping the most-recent encounter row.
    // isFriend is synthesised from the group: if ANY row for that identity is marked friend,
    // the deduped entry carries isFriend = true — so Sparks and Friends are always consistent.
    val encounters: StateFlow<List<EncounterWithProfile>> =
        encounterDao.observeConfirmed()
            .map { list ->
                val enriched = list.map { enc ->
                    EncounterWithProfile(
                        encounter = enc,
                        snapshot  = enc.peerSnapshotId?.let { snapshotDao.getById(it) },
                    )
                }
                enriched
                    .groupBy { it.snapshot?.peerUserId }
                    .flatMap { (key, group) ->
                        if (key != null) {
                            val latest    = group.maxByOrNull { it.encounter.seenAt }!!
                            val anyFriend = group.any { it.encounter.isFriend }
                            listOf(latest.copy(encounter = latest.encounter.copy(isFriend = anyFriend)))
                        } else {
                            // No stable identity (anonymous / privacy-mode peer) — show each row.
                            group
                        }
                    }
                    .sortedByDescending { it.encounter.seenAt }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ── Friends list — derived from the deduped encounters list ──────────────
    // Filtering here guarantees zero duplicates: the same dedup that powers Sparks
    // also powers Friends, so each identity appears at most once in both lists.
    val friends: StateFlow<List<EncounterWithProfile>> =
        encounters
            .map { list -> list.filter { it.encounter.isFriend } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ── OTA update check ─────────────────────────────────────────────────────
    private val _availableUpdate = MutableStateFlow<String?>(null)
    /** Non-null when a newer GitHub release exists. Value = the new version tag (e.g. "v0.2.0"). */
    val availableUpdate: StateFlow<String?> = _availableUpdate

    // ── Friend invite (from thunderpass://add-friend/{userId} deep links) ─────
    /** Non-null when the app was opened via a friend-invite link and is pending resolution. */
    private val _friendInviteUserId = MutableStateFlow<String?>(null)
    val friendInviteUserId: StateFlow<String?> = _friendInviteUserId

    /** Result of the most recent friend-invite resolution. */
    sealed class FriendInviteResult {
        data class Added(val displayName: String) : FriendInviteResult()
        object NotMetYet : FriendInviteResult()
    }
    private val _friendInviteResult = MutableStateFlow<FriendInviteResult?>(null)
    val friendInviteResult: StateFlow<FriendInviteResult?> = _friendInviteResult

    // ─────────────────────────────────────────────────────────────────────────

    init {
        // If the user had the service running before the process was killed
        // (e.g. system trim, force-stop-then-relaunch during testing), restart it
        // automatically so the toggle state stays in sync with reality.
        if (_serviceRunning.value) {
            startService()
        }
        // Check for OTA updates in the background on every app launch.
        viewModelScope.launch {
            _availableUpdate.value = OtaChecker.checkForUpdate(getApplication())
        }
        // Check for a pending friend invite from a thunderpass://add-friend deep link
        viewModelScope.launch {
            val settingsPrefs = getApplication<android.app.Application>()
                .getSharedPreferences("tp_settings", android.content.Context.MODE_PRIVATE)
            val pendingUserId = settingsPrefs.getString("pending_friend_invite", null)
            if (!pendingUserId.isNullOrBlank()) {
                _friendInviteUserId.value = pendingUserId
            }
        }
    }

    companion object {
        const val PREF_SHOP_UNLOCKS = "shop_unlocks"

        /** Compute current encounter streak in days. */
        private fun computeStreak(encounters: List<com.thunderpass.data.db.entity.Encounter>): Int {
            if (encounters.isEmpty()) return 0
            // Collect unique calendar-day start timestamps
            val seenDays = encounters.map { enc ->
                Calendar.getInstance().apply {
                    timeInMillis = enc.seenAt
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
            }.toSet()
            // Start from today (or yesterday if today has no encounters)
            var checkDay = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            if (checkDay !in seenDays) checkDay -= 86_400_000L
            var streak = 0
            while (checkDay in seenDays) {
                streak++
                checkDay -= 86_400_000L
            }
            return streak
        }
    }

    // ── Start / Stop BLE service ──────────────────────────────────────────────

    fun startService() {
        val ctx = getApplication<Application>()
        ContextCompat.startForegroundService(
            ctx,
            Intent(ctx, BleService::class.java).apply { action = BleService.ACTION_START }
        )
        _serviceRunning.value = true
        prefs.edit().putBoolean(BleService.PREF_SERVICE_ACTIVE, true).apply()
    }

    fun stopService() {
        val ctx = getApplication<Application>()
        ctx.startService(
            Intent(ctx, BleService::class.java).apply { action = BleService.ACTION_STOP }
        )
        _serviceRunning.value = false
        prefs.edit().putBoolean(BleService.PREF_SERVICE_ACTIVE, false).apply()
    }

    // ── Scan mode ─────────────────────────────────────────────────────────────

    fun setScanMode(mode: ScanMode) {
        _scanMode.value = mode
        val ctx = getApplication<Application>()
        ctx.startService(
            Intent(ctx, BleService::class.java).apply {
                action = BleService.ACTION_SET_SCAN_MODE
                putExtra(BleService.EXTRA_SCAN_MODE, mode.name)
            }
        )
    }

    // ── Visual Shop ───────────────────────────────────────────────────────────

    /**
     * Attempts to spend [amount] volts and unlock [effectKey].
     * Returns true on success, false if the balance is insufficient.
     */
    fun spendVolts(amount: Long, effectKey: String): Boolean {
        if (voltsTotal.value < amount) return false
        viewModelScope.launch {
            profileDao.spendVolts(amount)
            val newSet = _unlockedEffects.value + effectKey
            _unlockedEffects.value = newSet
            prefs.edit().putStringSet(PREF_SHOP_UNLOCKS, newSet).apply()
        }
        return true
    }
    // ── Friends ────────────────────────────────────────────────────────────────

    /**
     * Toggle the friend mark on an encounter.
     *
     * When a stable [peerUserId] is provided the flag is applied to ALL encounter
     * rows for that identity (via [EncounterDao.setFriendByUserId]), so historical
     * rows never cause the same person to appear twice in the Friends list.
     * Falls back to updating the single row when no identity key is available
     * (anonymous / privacy-mode peers without a userId).
     */
    fun toggleFriend(encounterId: Long, currentlyFriend: Boolean, peerUserId: String? = null) {
        viewModelScope.launch {
            val newState = !currentlyFriend
            if (peerUserId != null) {
                encounterDao.setFriendByUserId(peerUserId, newState)
            } else {
                encounterDao.setFriend(encounterId, newState)
            }
        }
    }

    // ── RetroAchievements — live peer snapshot + on-demand fetch ─────────────

    /** Live observable for a single peer snapshot. Emits whenever Room updates the row
     *  (e.g. after a background RA fetch), allowing EncounterDetailScreen to recompose. */
    fun observeSnapshotById(snapshotId: Long): Flow<PeerProfileSnapshot?> =
        snapshotDao.observeById(snapshotId)

    /**
     * Fetch the peer's RA data using the local user's API credentials.
     * Called when the encounter detail screen is opened and the peer has a
     * retroUsername but their RA data was never successfully retrieved.
     */
    fun refreshPeerRetro(snapshotId: Long, peerRetroUsername: String) {
        viewModelScope.launch {
            val auth = RetroAuthManager.getInstance(getApplication())
            if (!auth.hasCredentials()) return@launch
            RetroRepository.fetchAndCache(
                context      = getApplication(),
                peerUsername = peerRetroUsername,
                snapshotId   = snapshotId,
                snapshotDao  = snapshotDao,
                auth         = auth,
            )
        }
    }

    /**
     * Called when the user confirms or auto-resolves a friend invite deep link.
     * Looks up the encounter for [peerUserId] and marks it as friend if found.
     * Posts the result to [friendInviteResult] for the UI to act on.
     * Clears the stored invite from SharedPrefs regardless of outcome.
     */
    fun resolveFriendInvite(peerUserId: String) {
        viewModelScope.launch {
            // Clear from prefs immediately (don't re-show on next launch)
            getApplication<android.app.Application>()
                .getSharedPreferences("tp_settings", android.content.Context.MODE_PRIVATE)
                .edit().remove("pending_friend_invite").apply()
            _friendInviteUserId.value = null

            // Look up the encounter
            val snapshotId  = snapshotDao.getSnapshotIdByUserId(peerUserId)
            val encounterId = snapshotId?.let { encounterDao.getIdBySnapshotId(it) }
            if (encounterId != null) {
                encounterDao.setFriend(encounterId, true)
                val displayName = snapshotId.let { snapshotDao.getById(it) }?.displayName ?: "them"
                _friendInviteResult.value = FriendInviteResult.Added(displayName)
            } else {
                _friendInviteResult.value = FriendInviteResult.NotMetYet
            }
        }
    }

    /** Dismiss the friend invite result dialog. */
    fun dismissFriendInviteResult() {
        _friendInviteResult.value = null
    }

    // ── Privacy mode ────────────────────────────────────────────────────────

    /** Toggle privacy mode on/off. Persists in Room and takes effect on the next GATT exchange. */
    fun setPrivacyMode(enabled: Boolean) {
        viewModelScope.launch {
            val current = profileDao.get() ?: return@launch
            profileDao.upsert(current.copy(privacyMode = enabled))
        }
    }
    // ── Safe Zone ─────────────────────────────────────────────────────────────

    fun setSafeZone(active: Boolean) {
        _safeZoneActive.value = active
        prefs.edit().putBoolean(BleService.PREF_SAFE_ZONE, active).apply()
        if (_serviceRunning.value) {
            val ctx = getApplication<Application>()
            ctx.startService(
                Intent(ctx, BleService::class.java).apply {
                    action = BleService.ACTION_SET_SAFE_ZONE
                    putExtra(BleService.EXTRA_SAFE_ZONE, active)
                }
            )
        }
    }
}
