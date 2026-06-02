package com.tds.binarystars.presentation.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryBlue,
    secondary = PrimaryBlue,
    background = DarkBackground,
    surface = DarkCardBg,
    onBackground = TextDarkPrimary,
    onSurface = TextDarkPrimary,
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryBlue,
    secondary = PrimaryBlue,
    background = LightBackground,
    surface = LightCardBg,
    onBackground = TextLightPrimary,
    onSurface = TextLightPrimary,
)

@Composable
fun BinaryStarsTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) {
        DarkColorScheme
    } else {
        LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
