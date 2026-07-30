package io.github.kioskrelay.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val KioskBackground = Color(0xFF071A2B)
val KioskSurface = Color(0xFF0B2842)
val KioskSurfaceRaised = Color(0xFF103552)
val KioskBlue = Color(0xFF2F80ED)
val KioskCyan = Color(0xFF26D9E8)
val KioskSuccess = Color(0xFF35D07F)
val KioskWarning = Color(0xFFFFB648)
val KioskError = Color(0xFFFF6B7A)

private val KioskColorScheme = darkColorScheme(
    primary = KioskBlue,
    secondary = KioskCyan,
    tertiary = KioskSuccess,
    background = KioskBackground,
    surface = KioskSurface,
    surfaceVariant = KioskSurfaceRaised,
    onPrimary = Color.White,
    onSecondary = Color(0xFF001E26),
    onBackground = Color.White,
    onSurface = Color.White,
    onSurfaceVariant = Color(0xFFB9D2E4),
    error = KioskError,
)

@Composable
fun KioskRelayTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = KioskColorScheme,
        typography = KioskTypography,
        content = content,
    )
}
