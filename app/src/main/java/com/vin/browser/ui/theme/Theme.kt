package com.vin.browser.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

val LocalVinColors = staticCompositionLocalOf { VinDarkExtended }

/** Accessor for the brand roles Material 3 doesn't model. */
object VinTheme {
    val colors: VinColors
        @Composable @ReadOnlyComposable get() = LocalVinColors.current
}

// AMOLED Black color scheme -- pure black surfaces for OLED screens
private val AmoledDarkColors = darkColorScheme(
    primary = Color(0xFF3B82F6),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF1D4ED8),
    onPrimaryContainer = Color(0xFFDBEAFE),
    secondary = Color(0xFF10B981),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF064E3B),
    onSecondaryContainer = Color(0xFFD1FAE5),
    error = Color(0xFFEF4444),
    onError = Color.White,
    background = Color.Black,
    onBackground = Color(0xFFF1F5F9),
    surface = Color.Black,
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = Color(0xFF111111),
    onSurfaceVariant = Color(0xFF94A3B8),
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0A0A0A),
    surfaceContainer = Color(0xFF111111),
    surfaceContainerHigh = Color(0xFF1A1A1A),
    surfaceContainerHighest = Color(0xFF222222),
    outline = Color(0xFF222222),
    outlineVariant = Color(0xFF333333),
)

// Sepia color scheme -- warm tones for comfortable reading
private val SepiaColors = lightColorScheme(
    primary = Color(0xFF8B6914),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD4A937),
    onPrimaryContainer = Color(0xFF3D2E06),
    secondary = Color(0xFF6B8E23),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD4E8C0),
    onSecondaryContainer = Color(0xFF2D3B14),
    error = Color(0xFFDC2626),
    onError = Color.White,
    background = Color(0xFFF5E6D3),
    onBackground = Color(0xFF2C1810),
    surface = Color(0xFFF5E6D3),
    onSurface = Color(0xFF2C1810),
    surfaceVariant = Color(0xFFE8D5C0),
    onSurfaceVariant = Color(0xFF6B5B4F),
    surfaceContainerLowest = Color(0xFFFFFBF5),
    surfaceContainerLow = Color(0xFFF0DFCA),
    surfaceContainer = Color(0xFFE8D5C0),
    surfaceContainerHigh = Color(0xFFDCC8AF),
    surfaceContainerHighest = Color(0xFFD0B89E),
    outline = Color(0xFFC4A882),
    outlineVariant = Color(0xFFE0CCBA),
)

private val SepiaExtended = VinColors(
    secure = Color(0xFF6B8E23),
    secureContainer = Color(0xFFE8F0D8),
    onSecureContainer = Color(0xFF2D3B14),
    caution = Color(0xFFD4A937),
    danger = Color(0xFFDC2626),
    auroraStart = Color(0xFF8B6914),
    auroraMid = Color(0xFF6B8E23),
    auroraEnd = Color(0xFF8B4513),
    isDark = false,
)

private val AmoledExtended = VinColors(
    secure = Color(0xFF10B981),
    secureContainer = Color(0xFF0C2E24),
    onSecureContainer = Color(0xFF6EE7B7),
    caution = Color(0xFFFBBF24),
    danger = Color(0xFFEF4444),
    auroraStart = Color(0xFF1E3A8A),
    auroraMid = Color(0xFF3B82F6),
    auroraEnd = Color(0xFF7C3AED),
    isDark = true,
)

@Composable
fun VinBrowserTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    themePreset: String = "system",
    content: @Composable () -> Unit,
) {
    val (colorScheme, extended) = when (themePreset) {
        "light" -> VinLightColors to VinLightExtended
        "dark" -> VinDarkColors to VinDarkExtended
        "amoled" -> AmoledDarkColors to AmoledExtended
        "sepia" -> SepiaColors to SepiaExtended
        else -> if (darkTheme) VinDarkColors to VinDarkExtended else VinLightColors to VinLightExtended
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(LocalVinColors provides extended) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = VinTypography,
            shapes = VinShapes,
            content = content,
        )
    }
}