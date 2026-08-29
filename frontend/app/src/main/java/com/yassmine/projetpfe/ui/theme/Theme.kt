package com.yassmine.projetpfe.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = ConvenePrimary,
    secondary = ConveneSecondary,
    tertiary = ConveneTertiary,
    background = BackgroundLight,
    surface = SurfaceContainerLow,
    onPrimary = White,
    onBackground = TextDark,
    onSurface = TextDark,
    error = ErrorRed
)

@Composable
fun ConveneTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = AppTypography,
        content = content
    )
}

@Composable
fun MeetFlowTheme(content: @Composable () -> Unit) = ConveneTheme(content)


