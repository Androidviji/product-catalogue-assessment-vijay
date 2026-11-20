package com.example.catalogue.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** A neutral blue catalogue theme. Not dynamic colour, so the app looks the same on every device. */
private val LightColors = lightColorScheme(
    primary = Color(0xFF0B57A4),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD5E3FF),
    onPrimaryContainer = Color(0xFF001B3C),
    secondaryContainer = Color(0xFFE3E8F2),
    tertiaryContainer = Color(0xFFFFE08A),
    onTertiaryContainer = Color(0xFF3A2C00),
    background = Color(0xFFF8F9FC),
    surface = Color(0xFFF8F9FC),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA7C8FF),
    onPrimary = Color(0xFF003062),
    primaryContainer = Color(0xFF00468A),
    onPrimaryContainer = Color(0xFFD5E3FF),
    tertiaryContainer = Color(0xFF574400),
    onTertiaryContainer = Color(0xFFFFE08A),
)

@Composable
fun CatalogueTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
