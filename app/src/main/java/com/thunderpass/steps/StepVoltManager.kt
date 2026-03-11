package com.thunderpass.steps

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import com.thunderpass.data.BadgeManager
import com.thunderpass.data.db.ThunderPassDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

private const val TAG = "ThunderPass/StepVolts"

// Separate prefs file — no overlap with thunderpass_prefs (BLE/service state)
private const val PREFS = "thunderpass_steps"
private const val KEY_DAY_EPOCH      = "steps_day_epoch"
private const val KEY_BASELINE       = "steps_baseline"
private const val KEY_VOLTS_TODAY    = "steps_volts_today"
private const val KEY_LIFETIME_VOLTS = "steps_volts_lifetime"
private const val KEY_STEPS_TODAY    = "steps_steps_today"

const val STEPS_PER_VOLT  = 100
const val DAILY_VOLT_CAP  = 100

/**
 * Tracks the hardware step counter and awards up to [DAILY_VOLT_CAP] Volts per calendar day
 * at a rate of 1 Volt per [STEPS_PER_VOLT] steps — mirroring the Nintendo 3DS Play Coin system.
 *
 * State lives in plain SharedPreferences (three keys).  Volts are credited via the existing
 * [com.thunderpass.data.db.dao.MyProfileDao.addVolts] atomic SQL call so the overall Volt
 * balance is always authoritative in the encrypted Room database.
 *
 * Register / unregister via [start] / [stop] from the BleService lifecycle.
 */
object StepVoltManager : SensorEventListener {

    private var sensorManager: SensorManager? = null
    private lateinit var appContext: Context
    private val scope = CoroutineScope(Dispatchers.IO)

    fun start(context: Context) {
        appContext = context.applicationContext
        val sm = context.getSystemService(SensorManager::class.java) ?: return
        sensorManager = sm
        val sensor = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) ?: run {
            Log.w(TAG, "No step counter sensor — step Volts disabled on this device.")
            return
        }
        sm.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        Log.i(TAG, "Step counter registered.")
    }

    fun stop(context: Context) {
        sensorManager?.unregisterListener(this)
        sensorManager = null
        Log.i(TAG, "Step counter unregistered.")
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_STEP_COUNTER) return
        val totalSteps = event.values[0].toLong()
        val prefs      = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val today      = todayEpoch()

        // Day rolled over — reset baseline and today's volt counter
        val savedDay = prefs.getLong(KEY_DAY_EPOCH, -1L)
        if (savedDay != today) {
            prefs.edit()
                .putLong(KEY_DAY_EPOCH,   today)
                .putLong(KEY_BASELINE,    totalSteps)
                .putLong(KEY_VOLTS_TODAY, 0L)
                .putLong(KEY_STEPS_TODAY, 0L)
                .apply()
            Log.i(TAG, "New day — step baseline reset at $totalSteps cumulative steps.")
            return
        }

        val baseline   = prefs.getLong(KEY_BASELINE, totalSteps)
        val stepsToday = (totalSteps - baseline).coerceAtLeast(0L)
        val voltsToday = prefs.getLong(KEY_VOLTS_TODAY, 0L)

        // Always persist raw step count so the UI can show immediate feedback
        prefs.edit().putLong(KEY_STEPS_TODAY, stepsToday).apply()

        // How many Volts has today's step-count earned in total?
        val voltsEarnedTotal = stepsToday / STEPS_PER_VOLT
        // How many are new since last check?
        val newVolts         = (voltsEarnedTotal - voltsToday).coerceAtLeast(0L)
        // Cap at the remaining daily budget
        val remaining        = (DAILY_VOLT_CAP - voltsToday).coerceAtLeast(0L)
        val awardable        = newVolts.coerceAtMost(remaining)

        if (awardable <= 0L) return

        val updatedToday   = voltsToday + awardable
        val lifetimeBefore = prefs.getLong(KEY_LIFETIME_VOLTS, 0L)
        val lifetimeAfter  = lifetimeBefore + awardable

        prefs.edit()
            .putLong(KEY_VOLTS_TODAY,    updatedToday)
            .putLong(KEY_LIFETIME_VOLTS, lifetimeAfter)
            .apply()

        scope.launch {
            val db = ThunderPassDatabase.getInstance(appContext)
            db.myProfileDao().addVolts(awardable)
            Log.i(TAG, "Awarded $awardable step-Volt(s) (today: $updatedToday / $DAILY_VOLT_CAP, lifetime: $lifetimeAfter)")

            val profile        = db.myProfileDao().get() ?: return@launch
            val earned         = profile.badgesJson.split(",").map { it.trim() }.toSet()

            if ("first_step" !in earned) {
                BadgeManager.award(appContext, "first_step")
            }
            if (updatedToday >= DAILY_VOLT_CAP && "daily_walker" !in earned) {
                BadgeManager.award(appContext, "daily_walker")
            }
            if (lifetimeAfter >= 10_000L && "marathon" !in earned) {
                BadgeManager.award(appContext, "marathon")
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    /** Returns the calendar day number (days since epoch) in the device's local time zone. */
    private fun todayEpoch(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis / 86_400_000L
    }

    // ── Helpers for UI ────────────────────────────────────────────────────────

    /** How many step-Volts have been awarded today (0..DAILY_VOLT_CAP). */
    fun voltsToday(context: Context): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_VOLTS_TODAY, 0L)

    /** Day epoch that KEY_VOLTS_TODAY belongs to — if stale (different from today), return 0. */
    fun voltsTodayLive(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return if (prefs.getLong(KEY_DAY_EPOCH, -1L) == todayEpoch())
            prefs.getLong(KEY_VOLTS_TODAY, 0L)
        else 0L
    }

    /** Raw steps taken today (for UI display). */
    fun stepsTodayLive(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return if (prefs.getLong(KEY_DAY_EPOCH, -1L) == todayEpoch())
            prefs.getLong(KEY_STEPS_TODAY, 0L)
        else 0L
    }
}
