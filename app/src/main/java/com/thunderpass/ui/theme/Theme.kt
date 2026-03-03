package com.thunderpass.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable

/** ThunderPass is dark-only — matches the space / night-mode design language. */
private val ThunderDarkColorScheme = darkColorScheme(
    primary            = VividPurple,
    onPrimary          = SpaceWhite,
    primaryContainer   = VividPurpleDark,
    onPrimaryContainer = VividPurpleLight,

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

@Composable
fun ThunderPassTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = ThunderDarkColorScheme,
        content     = content,
    )
}
