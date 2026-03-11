package com.thunderpass.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.thunderpass.data.db.dao.EncounterDao
import com.thunderpass.data.db.dao.MyProfileDao
import com.thunderpass.data.db.dao.PeerProfileSnapshotDao
import com.thunderpass.data.db.entity.Encounter
import com.thunderpass.data.db.entity.MyProfile
import com.thunderpass.data.db.entity.PeerProfileSnapshot
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory
import java.security.SecureRandom

@Database(
    entities = [
        MyProfile::class,
        Encounter::class,
        PeerProfileSnapshot::class,
    ],
    version = 17,
    exportSchema = true,
)
abstract class ThunderPassDatabase : RoomDatabase() {

    abstract fun myProfileDao(): MyProfileDao
    abstract fun encounterDao(): EncounterDao
    abstract fun peerProfileSnapshotDao(): PeerProfileSnapshotDao

    companion object {
        private const val DB_NAME        = "thunderpass.db"
        private const val KEY_PREF_FILE  = "thunderpass_db_key"
        private const val KEY_PASSPHRASE = "db_passphrase_hex"

        @Volatile private var INSTANCE: ThunderPassDatabase? = null

        fun getInstance(context: Context): ThunderPassDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: run {
                    val passphrase = getOrCreateDatabaseKey(context.applicationContext)
                    val factory    = SupportFactory(SQLiteDatabase.getBytes(passphrase))
                    Room.databaseBuilder(
                        context.applicationContext,
                        ThunderPassDatabase::class.java,
                        DB_NAME
                    )
                        .openHelperFactory(factory)
                        .fallbackToDestructiveMigration() // replace with Migration objects before 1.0
                        .build()
                        .also { INSTANCE = it }
                }
            }

        /**
         * Retrieves or creates a 32-byte random passphrase stored in
         * [EncryptedSharedPreferences] (Android Keystore AES-256-GCM).
         * The passphrase never leaves the device and is inaccessible to other
         * apps even on a rooted device (protected by the hardware-backed Keystore).
         */
        private fun getOrCreateDatabaseKey(context: Context): CharArray {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            val prefs = EncryptedSharedPreferences.create(
                context, KEY_PREF_FILE, masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
            val existing = prefs.getString(KEY_PASSPHRASE, null)
            if (existing != null) return existing.toCharArray()
            // First run: generate a fresh 256-bit random key and persist it encrypted.
            val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
            val hex   = bytes.joinToString("") { "%02x".format(it) }
            prefs.edit().putString(KEY_PASSPHRASE, hex).apply()
            return hex.toCharArray()
        }
    }
}
