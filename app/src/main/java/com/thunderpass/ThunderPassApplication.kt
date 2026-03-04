package com.thunderpass

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.crossfade
import coil3.svg.SvgDecoder
import com.thunderpass.ble.RotatingIdManager
import com.thunderpass.data.db.ThunderPassDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Application entry point.
 * • Initialises the Room singleton and seeds an empty [MyProfile] row on first run.
 * • Provides a Coil [ImageLoader] with SVG support for DiceBear avatar fetching.
 */
class ThunderPassApplication : Application(), SingletonImageLoader.Factory {

    /** App-scoped coroutine scope for one-time init work. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        appScope.launch {
            val db         = ThunderPassDatabase.getInstance(this@ThunderPassApplication)
            val profileDao = db.myProfileDao()
            val encounterDao = db.encounterDao()

            if (profileDao.get() == null) {
                profileDao.upsert(
                    com.thunderpass.data.db.entity.MyProfile(
                        installationId = RotatingIdManager(this@ThunderPassApplication)
                            .installationId
                    )
                )
            }

            // ── Volts recalculation ───────────────────────────────────────────
            // Each spark (completed GATT exchange) earns 100 V.  If the counter
            // fell behind due to a DB wipe, reinstall, or a future data-restore,
            // top it up so it's always at least encounterCount * 100.
            val totalEncounters = encounterDao.countAll()
            val expectedVolts   = totalEncounters.toLong() * 100L
            profileDao.get()?.let { p ->
                if (p.voltsTotal < expectedVolts) {
                    profileDao.addVolts(expectedVolts - p.voltsTotal)
                }
            }
        }
    }

    /** Coil picks this up automatically via [SingletonImageLoader.Factory]. */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(SvgDecoder.Factory()) }
            .crossfade(true)
            .build()
}
