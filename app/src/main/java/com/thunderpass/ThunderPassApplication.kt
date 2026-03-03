package com.thunderpass

import android.app.Application
import com.thunderpass.ble.RotatingIdManager
import com.thunderpass.data.db.ThunderPassDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Application entry point.
 * Initialises the Room singleton and seeds an empty [MyProfile] row on first run,
 * so every other component can assume the row always exists.
 */
class ThunderPassApplication : Application() {

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
}
