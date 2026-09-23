package com.vin.browser.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

/**
 * Type scale. Sizes are deliberately a small set of steps -- the old UI used
 * 10/11/12/13/14/15/16/18/24/38sp with ad-hoc weights, which is what made the
 * hierarchy read as noise.
 */
private val Sans = FontFamily.SansSerif

private val TrimBoth = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

private fun style(
    size: Int,
    lineHeight: Int,
    weight: FontWeight,
    tracking: Double = 0.0,
) = TextStyle(
    fontFamily = Sans,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    letterSpacing = tracking.sp,
    lineHeightStyle = TrimBoth,
)

val VinTypography = Typography(
    displayLarge = style(44, 52, FontWeight.Bold, -1.0),
    displayMedium = style(36, 44, FontWeight.Bold, -0.8),
    displaySmall = style(30, 38, FontWeight.Bold, -0.6),

    headlineLarge = style(28, 36, FontWeight.SemiBold, -0.5),
    headlineMedium = style(24, 32, FontWeight.SemiBold, -0.4),
    headlineSmall = style(20, 28, FontWeight.SemiBold, -0.2),

    titleLarge = style(18, 26, FontWeight.SemiBold, -0.1),
    titleMedium = style(16, 24, FontWeight.SemiBold, 0.0),
    titleSmall = style(14, 20, FontWeight.SemiBold, 0.1),

    bodyLarge = style(16, 24, FontWeight.Normal, 0.0),
    bodyMedium = style(14, 21, FontWeight.Normal, 0.1),
    bodySmall = style(12, 18, FontWeight.Normal, 0.2),

    labelLarge = style(14, 20, FontWeight.Medium, 0.1),
    labelMedium = style(12, 16, FontWeight.Medium, 0.4),
    labelSmall = style(11, 14, FontWeight.Medium, 0.5),
)