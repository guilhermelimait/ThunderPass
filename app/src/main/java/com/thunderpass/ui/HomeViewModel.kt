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
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    val displayName: StateFlow<String> = profileDao.observe()
        .filterNotNull()
        .map { it.displayName }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "Traveler")

    // ── Safe Zone ─────────────────────────────────────────────────────────────
    private val _safeZoneActive = MutableStateFlow(prefs.getBoolean(BleService.PREF_SAFE_ZONE, false))
    val safeZoneActive: StateFlow<Boolean> = _safeZoneActive

    // ── Service running state ─────────────────────────────────────────────────
    private val _serviceRunning = MutableStateFlow(false)
    val serviceRunning: StateFlow<Boolean> = _serviceRunning

    // ── Scan mode ─────────────────────────────────────────────────────────────
    private val _scanMode = MutableStateFlow(ScanMode.BALANCED)
    val scanMode: StateFlow<ScanMode> = _scanMode

    // ── Encounter count for the badge on the home screen ─────────────────────
    val encounterCount: StateFlow<Int> = encounterDao.observeCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    // ── Encounter streak ─────────────────────────────────────────────────────
    val encounterStreak: StateFlow<Int> = encounterDao.observeAll()
        .map { list -> computeStreak(list) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    // ── Energy (Joules) ───────────────────────────────────────────────────────
    val joulesTotal: StateFlow<Long> = profileDao.observe()
        .filterNotNull()
        .map { it.joulesTotal }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    // ── Full encounter list with resolved snapshots ───────────────────────────
    val encounters: StateFlow<List<EncounterWithProfile>> =
        MutableStateFlow(emptyList<EncounterWithProfile>()).also { flow ->
            viewModelScope.launch {
                encounterDao.observeAll().collect { list ->
                    val enriched = list.map { enc ->
                        EncounterWithProfile(
                            encounter = enc,
                            snapshot  = enc.peerSnapshotId?.let { snapshotDao.getById(it) }
                        )
                    }
                    flow.value = enriched
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ─────────────────────────────────────────────────────────────────────────

    companion object {
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
    }

    fun stopService() {
        val ctx = getApplication<Application>()
        ctx.startService(
            Intent(ctx, BleService::class.java).apply { action = BleService.ACTION_STOP }
        )
        _serviceRunning.value = false
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
