package com.vin.browser.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Stitch ViN Browser Tonal Palette
 * Optimized for pure AMOLED Dark and high-contrast professional UX.
 */

// Core Slate Surfaces (from Stitch DESIGN.md)
val DarkBackground = Color(0xFF0B0E14)
val DarkSurfaceLow = Color(0xFF161B26)
val DarkSurfaceContainer = Color(0xFF1D2026)
val DarkSurfaceHigh = Color(0xFF272A31)
val DarkSurfaceHighest = Color(0xFF32353C)
val DarkOutline = Color(0xFF232B3E)
val DarkOutlineVariant = Color(0xFF424754)

// Text & Accents
val TextPrimary = Color(0xFFF1F5F9)
val TextSecondary = Color(0xFF94A3B8)
val AccentBlue = Color(0xFF3B82F6)
val AccentBlueContainer = Color(0xFF1D4ED8)
val AccentGreen = Color(0xFF10B981)
val AccentGreenContainer = Color(0xFF064E3B)
val AccentRed = Color(0xFFEF4444)

val VinDarkColors = darkColorScheme(
    primary = AccentBlue,
    onPrimary = Color.White,
    primaryContainer = AccentBlueContainer,
    onPrimaryContainer = Color(0xFFDBEAFE),
    secondary = AccentGreen,
    onSecondary = Color(0xFF003824),
    secondaryContainer = AccentGreenContainer,
    onSecondaryContainer = Color(0xFFD1FAE5),
    error = AccentRed,
    onError = Color.White,
    background = DarkBackground,
    onBackground = TextPrimary,
    surface = DarkBackground,
    onSurface = TextPrimary,
    surfaceVariant = DarkSurfaceContainer,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = DarkBackground,
    surfaceContainerLow = DarkSurfaceLow,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceHigh,
    surfaceContainerHighest = DarkSurfaceHighest,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
)

val VinLightColors = lightColorScheme(
    primary = Color(0xFF2563EB),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDBEAFE),
    onPrimaryContainer = Color(0xFF1E3A8A),
    secondary = Color(0xFF059669),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD1FAE5),
    onSecondaryContainer = Color(0xFF064E3B),
    error = Color(0xFFDC2626),
    onError = Color.White,
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFF8FAFC),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFE2E8F0),
    onSurfaceVariant = Color(0xFF64748B),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF1F5F9),
    surfaceContainer = Color(0xFFE2E8F0),
    surfaceContainerHigh = Color(0xFFCBD5E1),
    surfaceContainerHighest = Color(0xFF94A3B8),
    outline = Color(0xFFCBD5E1),
    outlineVariant = Color(0xFFE2E8F0),
)

@androidx.compose.runtime.Immutable
data class VinColors(
    val secure: Color,
    val secureContainer: Color,
    val onSecureContainer: Color,
    val caution: Color,
    val danger: Color,
    val auroraStart: Color,
    val auroraMid: Color,
    val auroraEnd: Color,
    val isDark: Boolean,
)

val VinDarkExtended = VinColors(
    secure = AccentGreen,
    secureContainer = Color(0xFF0C2E24),
    onSecureContainer = Color(0xFF6EE7B7),
    caution = Color(0xFFFBBF24),
    danger = AccentRed,
    auroraStart = Color(0xFF1E3A8A),
    auroraMid = AccentBlue,
    auroraEnd = Color(0xFF7C3AED),
    isDark = true,
)

val VinLightExtended = VinColors(
    secure = Color(0xFF059669),
    secureContainer = Color(0xFFD1FAE5),
    onSecureContainer = Color(0xFF064E3B),
    caution = Color(0xFFD97706),
    danger = Color(0xFFDC2626),
    auroraStart = Color(0xFF93C5FD),
    auroraMid = Color(0xFF3B82F6),
    auroraEnd = Color(0xFFA78BFA),
    isDark = false,
)