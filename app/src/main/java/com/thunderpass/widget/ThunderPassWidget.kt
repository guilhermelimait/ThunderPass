package com.thunderpass.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.thunderpass.BleService

// ─────────────────────────────────────────────────────────────────
//  Widget colours (not using MaterialTheme — Glance has its own)
// ─────────────────────────────────────────────────────────────────
private val BgColor        = ColorProvider(Color(0xCC111122))
private val TitleColor     = ColorProvider(Color.White)
private val StatusOnColor  = ColorProvider(Color(0xFFFFC107))  // amber/gold
private val StatusOffColor = ColorProvider(Color(0x99FFFFFF))  // translucent white
private val ToggleLabelOn  = "⚡ ON"
private val ToggleLabelOff = "OFF"

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

        val svcIntent = Intent(context, BleService::class.java).apply {
            action = if (isRunning) BleService.ACTION_STOP else BleService.ACTION_START
        }
        if (isRunning) {
            context.stopService(svcIntent)
        } else {
            ContextCompat.startForegroundService(context, svcIntent)
        }

        // Force-refresh this widget after the toggle
        ThunderPassWidget().update(context, glanceId)
    }
}

// ─────────────────────────────────────────────────────────────────
//  Widget definition
// ─────────────────────────────────────────────────────────────────
class ThunderPassWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = context.getSharedPreferences(BleService.PREFS_NAME, Context.MODE_PRIVATE)
        val isRunning = prefs.getBoolean(BleService.PREF_SERVICE_ACTIVE, false)

        provideContent {
            WidgetContent(isRunning)
        }
    }

    @Composable
    private fun WidgetContent(isRunning: Boolean) {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(BgColor)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: title + status
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(
                        text = "⚡ ThunderPass",
                        style = TextStyle(
                            color = TitleColor,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        text = if (isRunning) "Service running" else "Service stopped",
                        style = TextStyle(
                            color = if (isRunning) StatusOnColor else StatusOffColor,
                            fontSize = 11.sp
                        )
                    )
                }

                Spacer(modifier = GlanceModifier.width(8.dp))

                // Right: toggle button
                Box(
                    modifier = GlanceModifier
                        .size(72.dp, 36.dp)
                        .background(
                            if (isRunning)
                                ColorProvider(Color(0xFFFFC107))
                            else
                                ColorProvider(Color(0x33FFFFFF))
                        )
                        .clickable(
                            onClick = actionRunCallback<ToggleThunderPassAction>()
                        )
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isRunning) ToggleLabelOn else ToggleLabelOff,
                        style = TextStyle(
                            color = if (isRunning)
                                ColorProvider(Color(0xFF111122))
                            else
                                TitleColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────
//  Receiver registered in AndroidManifest.xml
// ─────────────────────────────────────────────────────────────────
class ThunderPassWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ThunderPassWidget()
}
