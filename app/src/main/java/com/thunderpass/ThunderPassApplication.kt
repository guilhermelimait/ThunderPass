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
            val profileDao = ThunderPassDatabase.getInstance(this@ThunderPassApplication)
                .myProfileDao()

            if (profileDao.get() == null) {
                profileDao.upsert(
                    com.thunderpass.data.db.entity.MyProfile(
                        installationId = RotatingIdManager(this@ThunderPassApplication)
                            .installationId
                    )
                )
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
