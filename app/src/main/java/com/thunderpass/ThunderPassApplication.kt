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

            // Award "Alfa Tester" to every user who installs before v0.7 officially launches.
            // Award "Beta Tester" to every user who installs before v0.8 officially launches.
            // Runs here (after the profile row is guaranteed to exist) rather than in
            // ProfileViewModel where a startup race could fire before the row is created.
            com.thunderpass.data.BadgeManager.award(
                this@ThunderPassApplication,
                "alfa_tester",
                "beta_tester",
            )

            // Award "Shared Quest" retroactively on every start if the user already has
            // RetroAchievements data cached (meaning they previously linked their account
            // and downloaded game data successfully).
            val raCache = com.thunderpass.retro.RetroProfileCache.load(this@ThunderPassApplication)
            if (raCache != null) {
                com.thunderpass.data.BadgeManager.award(this@ThunderPassApplication, "shared_quest")
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
