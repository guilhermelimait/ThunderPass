package com.thunderpass.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable

/** ThunderPass dark colour scheme. */
private val ThunderDarkColorScheme = darkColorScheme(
    primary            = BurntOrangeLight,
    onPrimary          = DeepSpaceBlack,
    primaryContainer   = BurntOrange,
    onPrimaryContainer = SpaceWhite,

    secondary          = SpaceCyan,
    onSecondary        = DeepSpaceBlack,

    background         = DeepSpaceBlack,
    onBackground       = SpaceWhite,

    surface            = DeepSpaceNavy,
    onSurface          = SpaceWhite,

    surfaceVariant     = DeepSpaceRaised,
    onSurfaceVariant   = SpaceMuted,

    error              = SpaceError,
    onError            = SpaceWhite,

    outline            = SpaceSubtle,
    outlineVariant     = DeepSpaceRaised,
)

/** ThunderPass light colour scheme — clean, modern, bright. */
private val ThunderLightColorScheme = lightColorScheme(
    primary            = BurntOrange,
    onPrimary          = SpaceWhite,
    primaryContainer   = BurntOrangeLight,
    onPrimaryContainer = BurntOrangeDark,

    secondary          = SpaceCyan,
    onSecondary        = LightOnSurface,

    background         = LightBackground,
    onBackground       = LightOnSurface,

    surface            = LightSurface,
    onSurface          = LightOnSurface,

    surfaceVariant     = LightSurfaceVar,
    onSurfaceVariant   = LightOnSurfaceV,

    error              = LightError,
    onError            = SpaceWhite,

    outline            = LightOutline,
    outlineVariant     = LightSurfaceVar,
)

@Composable
fun ThunderPassTheme(
    darkTheme: Boolean = false,   // light by default per design direction
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) ThunderDarkColorScheme else ThunderLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        content     = content,
    )
}
