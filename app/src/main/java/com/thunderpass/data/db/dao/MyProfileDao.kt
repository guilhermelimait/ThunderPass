package com.thunderpass.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.thunderpass.data.db.entity.MyProfile
import kotlinx.coroutines.flow.Flow

@Dao
interface MyProfileDao {

    /** Observe profile changes in real time. */
    @Query("SELECT * FROM my_profile WHERE id = 1")
    fun observe(): Flow<MyProfile?>

    /** Get current profile once (suspend). */
    @Query("SELECT * FROM my_profile WHERE id = 1")
    suspend fun get(): MyProfile?

    /** Upsert — creates the row on first run, replaces on subsequent runs. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: MyProfile)

    /** Atomically add [amount] joules to the energy total. */
    @Query("UPDATE my_profile SET joulesTotal = joulesTotal + :amount WHERE id = 1")
    suspend fun addJoules(amount: Long)
}
