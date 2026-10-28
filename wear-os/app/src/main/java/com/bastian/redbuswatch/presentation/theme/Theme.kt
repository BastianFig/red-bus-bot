package com.bastian.redbuswatch.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme

val RedPrimary   = Color(0xFFE30613)
val BlueBus      = Color(0xFF0093B3)
val BackgroundDark = Color(0xFF000000)
val SurfaceDark  = Color(0xFF1A1A1A)
val OnBackground = Color(0xFFFFFFFF)
val OnSurface    = Color(0xFFE0E0E0)

private val RedBusColorScheme = ColorScheme(
    primary            = RedPrimary,
    onPrimary          = OnBackground,
    primaryContainer   = Color(0xFF8B0000),
    onPrimaryContainer = OnBackground,
    secondary          = BlueBus,
    onSecondary        = OnBackground,
    background         = BackgroundDark,
    onBackground       = OnBackground,
    surfaceContainer     = Color(0xFF1B1B1D),
    surfaceContainerHigh = Color(0xFF2A2A2D),
    onSurface            = OnBackground,
    onSurfaceVariant     = Color(0xFFB8B8BC),
)

@Composable
fun RedBusWatchTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = RedBusColorScheme,
        content = content
    )
}
