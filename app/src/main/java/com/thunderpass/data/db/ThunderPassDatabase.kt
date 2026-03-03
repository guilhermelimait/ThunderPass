package com.thunderpass.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.thunderpass.data.db.dao.EncounterDao
import com.thunderpass.data.db.dao.MyProfileDao
import com.thunderpass.data.db.dao.PeerProfileSnapshotDao
import com.thunderpass.data.db.entity.Encounter
import com.thunderpass.data.db.entity.MyProfile
import com.thunderpass.data.db.entity.PeerProfileSnapshot

@Database(
    entities = [
        MyProfile::class,
        Encounter::class,
        PeerProfileSnapshot::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class ThunderPassDatabase : RoomDatabase() {

    abstract fun myProfileDao(): MyProfileDao
    abstract fun encounterDao(): EncounterDao
    abstract fun peerProfileSnapshotDao(): PeerProfileSnapshotDao

    companion object {
        private const val DB_NAME = "thunderpass.db"

        @Volatile private var INSTANCE: ThunderPassDatabase? = null

        fun getInstance(context: Context): ThunderPassDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    ThunderPassDatabase::class.java,
                    DB_NAME
                )
                    .fallbackToDestructiveMigration() // replace with Migration objects before 1.0
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
