package com.contextauth.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF2867A8),
    secondary = Color(0xFF40746A),
    tertiary = Color(0xFF7A5F2B),
    background = Color(0xFFF8FAFC),
    surface = Color(0xFFFFFFFF),
    error = Color(0xFFB3261E)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9FC9FF),
    secondary = Color(0xFFA8D4CA),
    tertiary = Color(0xFFE2C481),
    background = Color(0xFF101418),
    surface = Color(0xFF171C20),
    error = Color(0xFFFFB4AB)
)

@Composable
fun ContextAuthLabTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (androidx.compose.foundation.isSystemInDarkTheme()) DarkColors else LightColors,
        content = content
    )
}
