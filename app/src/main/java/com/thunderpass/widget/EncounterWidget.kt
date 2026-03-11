package com.thunderpass.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.thunderpass.MainActivity
import com.thunderpass.data.db.ThunderPassDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

/** ThunderYellow for widget text */
private val WidgetYellow = Color(0xFFFFD600)
/** Dark background matching app dark theme */
private val WidgetBg = Color(0xFF1A1A1A)

/**
 * Home-screen widget showing total + today encounter counts.
 * Call [EncounterWidget.Companion.updateAll] from BleService after each new encounter.
 */
class EncounterWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val (total, today) = withContext(Dispatchers.IO) {
            val dao = ThunderPassDatabase.getInstance(context).encounterDao()
            val dayStart = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            Pair(dao.countAll(), dao.countSince(dayStart))
        }

        provideContent {
            EncounterWidgetContent(total = total, today = today)
        }
    }

    companion object {
        /** Trigger a data-refresh and redraw on all placed widgets. */
        suspend fun refresh(context: Context) {
            EncounterWidget().updateAll(context)
        }
    }
}

@Composable
private fun EncounterWidgetContent(total: Int, today: Int) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(WidgetBg)
            .padding(12.dp)
            .clickable(actionStartActivity<MainActivity>()),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        // Header row
        Text(
            text = "\u26A1 ThunderPass",
            style = TextStyle(
                color = ColorProvider(WidgetYellow),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            ),
        )

        Spacer(GlanceModifier.height(8.dp))

        // Stats row
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
        ) {
            WidgetStat(label = "Total", value = total.toString())
            Spacer(GlanceModifier.width(16.dp))
            WidgetStat(label = "Today", value = today.toString())
        }
    }
}

@Composable
private fun WidgetStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.Horizontal.CenterHorizontally) {
        Text(
            text = value,
            style = TextStyle(
                color = ColorProvider(WidgetYellow),
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
        Text(
            text = label,
            style = TextStyle(
                color = ColorProvider(Color.White),
                fontSize = 11.sp,
            ),
        )
    }
}

class EncounterWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = EncounterWidget()
}
