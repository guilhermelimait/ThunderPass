package com.thunderpass.ui

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ─────────────────────────────────────────────────────────────────────────────
//  Data model
// ─────────────────────────────────────────────────────────────────────────────

private data class AppUsageStat(
    val packageName: String,
    val appName: String,
    val totalTimeMs: Long
)

// ─────────────────────────────────────────────────────────────────────────────
//  Helpers
// ─────────────────────────────────────────────────────────────────────────────

private fun hasUsageStatsPermission(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
    } else {
        @Suppress("DEPRECATION")
        appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
    }
    return mode == AppOpsManager.MODE_ALLOWED
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m"
        else -> "<1m"
    }
}

private fun loadTodayStats(context: Context): List<AppUsageStat> {
    val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    val pm = context.packageManager

    // Query from midnight today until now
    val cal = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.HOUR_OF_DAY, 0)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }
    val startTime = cal.timeInMillis
    val endTime = System.currentTimeMillis()

    val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime)
        ?: return emptyList()

    return stats
        .filter { stat ->
            val pkg = stat.packageName
            stat.totalTimeInForeground > 60_000L &&   // at least 1 minute
                !pkg.startsWith("android") &&
                !pkg.startsWith("com.android.") &&
                pkg != context.packageName
        }
        .sortedByDescending { it.totalTimeInForeground }
        .take(5)
        .mapNotNull { stat ->
            val label = try {
                pm.getApplicationLabel(
                    pm.getApplicationInfo(stat.packageName, PackageManager.GET_META_DATA)
                ).toString()
            } catch (_: PackageManager.NameNotFoundException) {
                null   // skip apps that are no longer installed
            }
            label?.let {
                AppUsageStat(
                    packageName = stat.packageName,
                    appName = it,
                    totalTimeMs = stat.totalTimeInForeground
                )
            }
        }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Public composable
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun GamePlayStatsCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var permissionGranted by remember { mutableStateOf(hasUsageStatsPermission(context)) }
    var stats by remember { mutableStateOf<List<AppUsageStat>>(emptyList()) }

    // Re-check permission and (re)load data every time the card enters composition
    LaunchedEffect(permissionGranted) {
        if (permissionGranted) {
            stats = loadTodayStats(context)
        }
    }

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // ── Header ──────────────────────────────────────────────────────
            Text(
                text = "🎮 DAILY PLAY TIME",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = Color(0xFF00E5FF),
                letterSpacing = 1.sp
            )

            Spacer(Modifier.height(12.dp))

            if (!permissionGranted) {
                // ── No-permission prompt ─────────────────────────────────
                Text(
                    text = "Usage access is needed to track your daily gaming sessions.",
                    fontSize = 13.sp,
                    color = Color(0xFFAAAAAA),
                    lineHeight = 18.sp
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                        // Re-check after the user comes back (next recomposition)
                        permissionGranted = hasUsageStatsPermission(context)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00E5FF),
                        contentColor = Color.Black
                    )
                ) {
                    Text(
                        text = "GRANT ACCESS",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        letterSpacing = 1.sp
                    )
                }
            } else if (stats.isEmpty()) {
                // ── No data yet ─────────────────────────────────────────
                Text(
                    text = "No gaming sessions recorded yet today. Play something and check back!",
                    fontSize = 13.sp,
                    color = Color(0xFFAAAAAA),
                    lineHeight = 18.sp
                )
            } else {
                // ── Stats list ──────────────────────────────────────────
                val totalMs = stats.sumOf { it.totalTimeMs }
                val maxMs = stats.maxOf { it.totalTimeMs }

                Text(
                    text = "Total today: ${formatDuration(totalMs)}",
                    fontSize = 12.sp,
                    color = Color(0xFF888888),
                    fontFamily = FontFamily.Monospace
                )
                Spacer(Modifier.height(12.dp))

                stats.forEachIndexed { index, stat ->
                    AppUsageRow(stat = stat, maxMs = maxMs)
                    if (index < stats.lastIndex) Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Per-app row
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AppUsageRow(stat: AppUsageStat, maxMs: Long) {
    val barFraction = if (maxMs > 0) (stat.totalTimeMs.toFloat() / maxMs).coerceIn(0f, 1f) else 0f

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stat.appName,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFFEEEEEE),
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = formatDuration(stat.totalTimeMs),
                fontSize = 11.sp,
                color = Color(0xFF00E5FF),
                fontFamily = FontFamily.Monospace
            )
        }
        Spacer(Modifier.height(4.dp))
        // Progress bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(Color(0xFF2A2A2A), RoundedCornerShape(2.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(barFraction)
                    .height(4.dp)
                    .background(Color(0xFF00E5FF), RoundedCornerShape(2.dp))
            )
        }
    }
}
