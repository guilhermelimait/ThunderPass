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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class EncounterWithProfile(
    val encounter: Encounter,
    val snapshot: PeerProfileSnapshot?,
)

class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val db = ThunderPassDatabase.getInstance(app)
    private val encounterDao = db.encounterDao()
    private val snapshotDao  = db.peerProfileSnapshotDao()
    private val profileDao   = db.myProfileDao()

    // ── Own profile observables ───────────────────────────────────────────────
    val installationId: StateFlow<String> = profileDao.observe()
        .map { it.installationId }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    val displayName: StateFlow<String> = profileDao.observe()
        .map { it.displayName }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "Traveler")

    // ── Service running state ─────────────────────────────────────────────────
    private val _serviceRunning = MutableStateFlow(false)
    val serviceRunning: StateFlow<Boolean> = _serviceRunning

    // ── Scan mode ─────────────────────────────────────────────────────────────
    private val _scanMode = MutableStateFlow(ScanMode.BALANCED)
    val scanMode: StateFlow<ScanMode> = _scanMode

    // ── Encounter count for the badge on the home screen ─────────────────────
    val encounterCount: StateFlow<Int> = encounterDao.observeCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

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
}
