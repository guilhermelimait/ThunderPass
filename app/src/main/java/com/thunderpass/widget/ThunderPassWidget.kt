package com.thunderpass.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.currentState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.thunderpass.BleService
import com.thunderpass.R
import com.thunderpass.data.db.ThunderPassDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ─────────────────────────────────────────────────────────────────
//  Action: read current prefs, flip the BleService state
// ─────────────────────────────────────────────────────────────────
class ToggleThunderPassAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val prefs = context.getSharedPreferences(BleService.PREFS_NAME, Context.MODE_PRIVATE)
        val isRunning = prefs.getBoolean(BleService.PREF_SERVICE_ACTIVE, false)

        // Write new state immediately so the widget and app both reflect it
        // without waiting for BleService to process the intent.
        prefs.edit().putBoolean(BleService.PREF_SERVICE_ACTIVE, !isRunning).apply()

        val svcIntent = Intent(context, BleService::class.java).apply {
            action = if (isRunning) BleService.ACTION_STOP else BleService.ACTION_START
        }
        if (isRunning) {
            context.stopService(svcIntent)
        } else {
            ContextCompat.startForegroundService(context, svcIntent)
        }

        // Force-refresh all widget instances after the toggle
        ThunderPassWidget.refreshAll(context, !isRunning)
        ThunderPassWidget2x2.refreshAll(context, !isRunning)
    }
}

// ─────────────────────────────────────────────────────────────────
//  Widget definition  (2×1 profile card)
// ─────────────────────────────────────────────────────────────────
class ThunderPassWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    companion object {
        val IS_RUNNING_KEY = booleanPreferencesKey("service_running")

        suspend fun refreshAll(context: Context, isRunning: Boolean) {
            GlanceAppWidgetManager(context)
                .getGlanceIds(ThunderPassWidget::class.java)
                .forEach { id ->
                    updateAppWidgetState(context, PreferencesGlanceStateDefinition, id) { prefs ->
                        prefs.toMutablePreferences().also { it[IS_RUNNING_KEY] = isRunning }
                    }
                    ThunderPassWidget().update(context, id)
                }
        }
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Seed Glance state from SharedPreferences so the widget is accurate on first render
        val spRunning = context.getSharedPreferences(BleService.PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(BleService.PREF_SERVICE_ACTIVE, false)
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, id) { prefs ->
            prefs.toMutablePreferences().also { it[IS_RUNNING_KEY] = spRunning }
        }
        val data = withContext(Dispatchers.IO) {
            val db = ThunderPassDatabase.getInstance(context)
            val profile = db.myProfileDao().get()
            val seed = profile?.avatarSeed?.ifEmpty { profile.installationId } ?: "default"
            val avatar = loadAvatarBitmap(seed, context)
            ToggleWidgetData(
                name   = profile?.displayName?.takeIf { it.isNotBlank() } ?: "SparkyUser",
                volts  = profile?.voltsTotal ?: 0L,
                avatar = avatar,
            )
        }
        provideContent {
            val isRunning = currentState<Preferences>()[IS_RUNNING_KEY] ?: false
            WidgetContent(data, isRunning)
        }
    }

    @Composable
    private fun WidgetContent(data: ToggleWidgetData, isRunning: Boolean) {
        Row(
            modifier = GlanceModifier
                .fillMaxSize()
                .cornerRadius(16.dp)
                .background(ImageProvider(R.drawable.widget_gradient_bg))
                .clickable(onClick = actionRunCallback<ToggleThunderPassAction>())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // ── Avatar ───────────────────────────────────────────
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

            // ── Name + ON/OFF pill — fills remaining space ───────
            Column(
                modifier = GlanceModifier
                    .defaultWeight()
                    .clickable(onClick = actionRunCallback<ToggleThunderPassAction>()),
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

            // ── App logo + volts side by side on the right ───────
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
    }
}

private data class ToggleWidgetData(
    val name: String,
    val volts: Long,
    val avatar: Bitmap?,
)

// ─────────────────────────────────────────────────────────────────
//  Receiver registered in AndroidManifest.xml
// ─────────────────────────────────────────────────────────────────
class ThunderPassWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ThunderPassWidget()
}
