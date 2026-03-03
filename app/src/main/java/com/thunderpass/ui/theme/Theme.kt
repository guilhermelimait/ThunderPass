package com.thunderpass.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary          = ThunderYellow,
    onPrimary        = ThunderGray,
    primaryContainer = ThunderYellowDark,
    background       = ThunderGray,
    surface          = ThunderSurface,
    onSurface        = ThunderOnSurface,
    secondary        = ThunderPurpleLight,
)

private val LightColorScheme = lightColorScheme(
    primary          = ThunderYellowDark,
    onPrimary        = ThunderGray,
    primaryContainer = ThunderYellow,
    secondary        = ThunderPurple,
)

@Composable
fun ThunderPassTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+ but we keep our brand colors by default
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else      -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content     = content,
    )
}
