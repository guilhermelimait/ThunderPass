package com.thunderpass.widget

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
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
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.thunderpass.BleService
import com.thunderpass.MainActivity
import com.thunderpass.R
import com.thunderpass.data.db.ThunderPassDatabase
import com.thunderpass.ui.computeBadges
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val ToggleOnBg  = ColorProvider(Color(0xFFFFC107))
private val ToggleOffBg = ColorProvider(Color(0xFF111111))
private val ToggleOnFg  = ColorProvider(Color(0xFF1A1100))
private val ToggleOffFg = ColorProvider(Color.White)

/**
 * 4w×2h full widget — gradient landscape card with DiceBear avatar,
 * display name, personal phrase, volts/passes/badges, and round bolt toggle.
 */
class FullWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val (data, isRunning) = withContext(Dispatchers.IO) {
            val db = ThunderPassDatabase.getInstance(context)
            val profile = db.myProfileDao().get()
            val passes = db.encounterDao().countAll()
            val earnedKeys = (profile?.badgesJson ?: "")
                .split(",").filter { it.isNotBlank() }.toSet()
            val badges = computeBadges(earnedKeys).count { it.tier > 0 }
            val seed = profile?.avatarSeed?.ifEmpty { profile.installationId } ?: "default"
            val avatar = loadAvatarBitmap(seed, context)
            val prefs = context.getSharedPreferences(BleService.PREFS_NAME, Context.MODE_PRIVATE)
            val running = prefs.getBoolean(BleService.PREF_SERVICE_ACTIVE, false)
            Pair(
                FullProfileData(
                    name    = profile?.displayName?.takeIf { it.isNotBlank() } ?: "SparkyUser",
                    phrase  = profile?.greeting?.takeIf { it.isNotBlank() } ?: "",
                    volts   = profile?.voltsTotal ?: 0L,
                    passes  = passes,
                    badges  = badges,
                    avatar  = avatar,
                ),
                running,
            )
        }
        provideContent { FullContent(data, isRunning) }
    }

    @Composable
    private fun FullContent(data: FullProfileData, isRunning: Boolean) {
        Row(
            modifier = GlanceModifier
                .fillMaxSize()
                .cornerRadius(16.dp)
                .background(ImageProvider(R.drawable.widget_gradient_bg))
                .padding(12.dp)
                .clickable(actionStartActivity<MainActivity>()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // ── Avatar ──────────────────────────────────────────
            if (data.avatar != null) {
                Image(
                    provider = ImageProvider(data.avatar),
                    contentDescription = "Avatar",
                    modifier = GlanceModifier
                        .size(56.dp)
                        .cornerRadius(28.dp),
                )
            } else {
                Box(
                    modifier = GlanceModifier
                        .size(56.dp)
                        .cornerRadius(28.dp)
                        .background(ColorProvider(Color(0x33FFFFFF))),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        provider = ImageProvider(R.drawable.ic_notification),
                        contentDescription = "Avatar",
                        modifier = GlanceModifier.size(26.dp),
                        colorFilter = ColorFilter.tint(ColorProvider(Color.White)),
                    )
                }
            }

            Spacer(GlanceModifier.width(10.dp))

            // ── Name + phrase + stats ───────────────────────────
            Column(
                modifier = GlanceModifier.defaultWeight(),
            ) {
                Text(
                    text = data.name,
                    maxLines = 1,
                    style = TextStyle(
                        color = ColorProvider(Color.White),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )

                if (data.phrase.isNotEmpty()) {
                    Spacer(GlanceModifier.height(2.dp))
                    Text(
                        text = data.phrase,
                        maxLines = 2,
                        style = TextStyle(
                            color = ColorProvider(Color(0xCCFFFFFF)),
                            fontSize = 11.sp,
                        ),
                    )
                }

                Spacer(GlanceModifier.height(6.dp))

                Row {
                    Text(
                        text = "⚡${data.volts}",
                        style = TextStyle(color = ColorProvider(Color.White), fontSize = 12.sp, fontWeight = FontWeight.Bold),
                    )
                    Spacer(GlanceModifier.width(8.dp))
                    Text(
                        text = "🤝${data.passes}",
                        style = TextStyle(color = ColorProvider(Color.White), fontSize = 12.sp, fontWeight = FontWeight.Bold),
                    )
                    Spacer(GlanceModifier.width(8.dp))
                    Text(
                        text = "🏆${data.badges}",
                        style = TextStyle(color = ColorProvider(Color.White), fontSize = 12.sp, fontWeight = FontWeight.Bold),
                    )
                }
            }

            Spacer(GlanceModifier.width(10.dp))

            // ── Round thunder bolt toggle ────────────────────────
            Box(
                modifier = GlanceModifier
                    .size(48.dp)
                    .cornerRadius(24.dp)
                    .background(if (isRunning) ToggleOnBg else ToggleOffBg)
                    .clickable(onClick = actionRunCallback<ToggleThunderPassAction>()),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    provider = ImageProvider(R.drawable.ic_notification),
                    contentDescription = "Toggle ThunderPass",
                    modifier = GlanceModifier.size(24.dp),
                    colorFilter = ColorFilter.tint(
                        if (isRunning) ToggleOnFg else ToggleOffFg
                    ),
                )
            }
        }
    }

    companion object {
        suspend fun refresh(context: Context) {
            FullWidget().updateAll(context)
        }
    }
}

private data class FullProfileData(
    val name: String,
    val phrase: String,
    val volts: Long,
    val passes: Int,
    val badges: Int,
    val avatar: Bitmap?,
)

class FullWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FullWidget()
}
