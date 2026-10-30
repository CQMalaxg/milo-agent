package com.malaxg.milo.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = SageSoft,
    onPrimary = Ink,
    secondary = Gold,
    tertiary = Coral,
    background = NightCanvas,
    surface = NightPaper,
    onSurface = NightInk,
    onBackground = NightInk,
)

private val LightColorScheme = lightColorScheme(
    primary = Sage,
    onPrimary = Paper,
    secondary = Gold,
    onSecondary = Ink,
    tertiary = Coral,
    background = Canvas,
    surface = Paper,
    onSurface = Ink,
    onBackground = Ink,
)

@Composable
fun MiloagentTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content,
    )
}
