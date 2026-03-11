package com.thunderpass.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.thunderpass.BleService
import com.thunderpass.R
import com.thunderpass.data.db.ThunderPassDatabase
import com.thunderpass.ui.computeBadges
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ─────────────────────────────────────────────────────────────────
//  Action: toggle BleService and refresh both the 2×1 and 2×2 widgets
// ─────────────────────────────────────────────────────────────────
class ToggleThunderPass2x2Action : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val prefs = context.getSharedPreferences(BleService.PREFS_NAME, Context.MODE_PRIVATE)
        val isRunning = prefs.getBoolean(BleService.PREF_SERVICE_ACTIVE, false)

        prefs.edit().putBoolean(BleService.PREF_SERVICE_ACTIVE, !isRunning).apply()

        val svcIntent = Intent(context, BleService::class.java).apply {
            action = if (isRunning) BleService.ACTION_STOP else BleService.ACTION_START
        }
        if (isRunning) context.stopService(svcIntent)
        else ContextCompat.startForegroundService(context, svcIntent)

        // Refresh both widget families
        ThunderPassWidget.refreshAll(context, !isRunning)
        ThunderPassWidget2x2.refreshAll(context, !isRunning)
    }
}

// ─────────────────────────────────────────────────────────────────
//  Widget definition  (2×2 profile card + stats)
// ─────────────────────────────────────────────────────────────────
class ThunderPassWidget2x2 : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    companion object {
        val IS_RUNNING_KEY = booleanPreferencesKey("service_running_2x2")

        suspend fun refreshAll(context: Context, isRunning: Boolean) {
            GlanceAppWidgetManager(context)
                .getGlanceIds(ThunderPassWidget2x2::class.java)
                .forEach { id ->
                    updateAppWidgetState(context, PreferencesGlanceStateDefinition, id) { prefs ->
                        prefs.toMutablePreferences().also { it[IS_RUNNING_KEY] = isRunning }
                    }
                    ThunderPassWidget2x2().update(context, id)
                }
        }
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val spRunning = context.getSharedPreferences(BleService.PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(BleService.PREF_SERVICE_ACTIVE, false)
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, id) { prefs ->
            prefs.toMutablePreferences().also { it[IS_RUNNING_KEY] = spRunning }
        }

        val data = withContext(Dispatchers.IO) {
            val db = ThunderPassDatabase.getInstance(context)
            val profile = db.myProfileDao().get()
            val passes = db.encounterDao().countAll()
            val allEncounters = db.encounterDao().getAll()
            val streak = BleService.computeEncounterStreak(allEncounters)
            val earnedKeys = (profile?.badgesJson ?: "")
                .split(",").filter { it.isNotBlank() }.toSet()
            val badges = computeBadges(earnedKeys).count { it.tier > 0 }
            val seed = profile?.avatarSeed?.ifEmpty { profile.installationId } ?: "default"
            val avatar = loadAvatarBitmap(seed, context)
            Widget2x2Data(
                name   = profile?.displayName?.takeIf { it.isNotBlank() } ?: "SparkyUser",
                volts  = profile?.voltsTotal ?: 0L,
                avatar = avatar,
                passes = passes,
                badges = badges,
                streak = streak,
            )
        }

        provideContent {
            val isRunning = currentState<Preferences>()[IS_RUNNING_KEY] ?: false
            WidgetContent(data, isRunning)
        }
    }

    @Composable
    private fun WidgetContent(data: Widget2x2Data, isRunning: Boolean) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .cornerRadius(16.dp)
                .background(ImageProvider(R.drawable.widget_gradient_bg))
                .clickable(onClick = actionRunCallback<ToggleThunderPass2x2Action>())
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            // ── Top row: avatar + name/status + bolt/volts ───────
            Row(
                modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Avatar
                if (data.avatar != null) {
                    Image(
                        provider = ImageProvider(data.avatar),
                        contentDescription = "Avatar",
                        modifier = GlanceModifier
                            .size(44.dp)
                            .cornerRadius(22.dp),
                    )
                } else {
                    Box(
                        modifier = GlanceModifier
                            .size(44.dp)
                            .cornerRadius(22.dp)
                            .background(ColorProvider(Color(0x33FFFFFF))),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            provider = ImageProvider(R.drawable.ic_notification),
                            contentDescription = "Avatar",
                            modifier = GlanceModifier.size(22.dp),
                            colorFilter = ColorFilter.tint(ColorProvider(Color.White)),
                        )
                    }
                }

                Spacer(GlanceModifier.width(10.dp))

                // Name + status pill
                Column(
                    modifier = GlanceModifier
                        .defaultWeight()
                        .clickable(onClick = actionRunCallback<ToggleThunderPass2x2Action>()),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = data.name,
                        maxLines = 1,
                        style = TextStyle(
                            color      = ColorProvider(Color.White),
                            fontSize   = 13.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                    Spacer(GlanceModifier.height(4.dp))
                    Text(
                        text  = if (isRunning) "Scanning nearby" else "Tap to start scanning",
                        style = TextStyle(
                            color      = ColorProvider(Color.White),
                            fontSize   = 10.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                }

                Spacer(GlanceModifier.width(10.dp))

                // Bolt icon + volts
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        provider = ImageProvider(R.drawable.ic_notification),
                        contentDescription = "Volts",
                        modifier = GlanceModifier.size(28.dp),
                    )
                    Spacer(GlanceModifier.width(2.dp))
                    Text(
                        text  = "${data.volts}",
                        style = TextStyle(
                            color      = ColorProvider(Color.White),
                            fontSize   = 20.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                }
            }

            // ── Divider ──────────────────────────────────────────
            Spacer(
                GlanceModifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(ColorProvider(Color(0x33FFFFFF))),
            )

            // ── Bottom row: passes / badges / streak ─────────────
            Row(
                modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(GlanceModifier.defaultWeight())
                StatColumn("${data.passes}", "Passes")
                Spacer(GlanceModifier.defaultWeight())
                StatColumn("${data.badges}", "Badges")
                Spacer(GlanceModifier.defaultWeight())
                StatColumn("${data.streak}", "Streak")
                Spacer(GlanceModifier.defaultWeight())
            }
        }
    }

    @Composable
    private fun StatColumn(value: String, label: String) {
        Column(horizontalAlignment = Alignment.Horizontal.CenterHorizontally) {
            Text(
                text  = value,
                style = TextStyle(
                    color      = ColorProvider(Color.White),
                    fontSize   = 18.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Spacer(GlanceModifier.height(2.dp))
            Text(
                text  = label,
                style = TextStyle(
                    color    = ColorProvider(Color(0xCCFFFFFF)),
                    fontSize = 9.sp,
                ),
            )
        }
    }
}

private data class Widget2x2Data(
    val name: String,
    val volts: Long,
    val avatar: Bitmap?,
    val passes: Int,
    val badges: Int,
    val streak: Int,
)

// ─────────────────────────────────────────────────────────────────
//  Receiver registered in AndroidManifest.xml
// ─────────────────────────────────────────────────────────────────
class ThunderPassWidget2x2Receiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ThunderPassWidget2x2()
}
