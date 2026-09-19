package com.grii.sidoarjo.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val GriiLightColorScheme = lightColorScheme(
    primary = Color.White,
    onPrimary = GriiRedPrimary,
    primaryContainer = Color.White,
    onPrimaryContainer = GriiRedPrimary,
    secondary = GriiGoldAccent,
    onSecondary = GriiRedPrimary,
    background = Color.Transparent,
    onBackground = GriiRedPrimary,
    surface = Color.White,
    onSurface = GriiRedPrimary,
    surfaceVariant = Color(0xFFFAFAFA),
    onSurfaceVariant = GriiRedPrimary.copy(alpha = 0.8f),
    outline = GriiRedPrimary,
    error = ErrorColor
)

private val GriiDarkColorScheme = lightColorScheme(
    primary = Color.White,
    onPrimary = GriiRedPrimary,
    primaryContainer = Color.White,
    onPrimaryContainer = GriiRedPrimary,
    secondary = GriiGoldAccent,
    onSecondary = GriiRedPrimary,
    background = Color.Transparent,
    onBackground = GriiRedPrimary,
    surface = Color.White,
    onSurface = GriiRedPrimary,
    surfaceVariant = Color(0xFFFAFAFA),
    onSurfaceVariant = GriiRedPrimary.copy(alpha = 0.8f),
    outline = GriiRedPrimary,
    error = ErrorColor
)

@Composable
fun GriiSidoarjoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) GriiDarkColorScheme else GriiLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

val MaterialTheme.success: Color
    @Composable
    get() = SuccessColor
