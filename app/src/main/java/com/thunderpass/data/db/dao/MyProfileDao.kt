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

    /** Atomically add [amount] volts to the energy total. */
    @Query("UPDATE my_profile SET joulesTotal = joulesTotal + :amount WHERE id = 1")
    suspend fun addVolts(amount: Long)

    /** Atomically spend [amount] volts, clamping to zero (never goes negative). */
    @Query("UPDATE my_profile SET joulesTotal = MAX(0, joulesTotal - :amount) WHERE id = 1")
    suspend fun spendVolts(amount: Long)

    /**
     * Persist the Supabase auth UUID locally after sign-in so the GATT server can
     * include it in the profile payload for peer identity dedup.
     */
    @Query("UPDATE my_profile SET supabaseUserId = :userId WHERE id = 1")
    suspend fun updateSupabaseUserId(userId: String)
}
