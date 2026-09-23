package com.vin.browser.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape

/** Sweeping placeholder fill, driven off the active colour scheme. */
fun Modifier.shimmerEffect(shape: Shape? = null): Modifier = composed {
    val scheme = MaterialTheme.colorScheme
    val base = scheme.surfaceContainerHigh
    val highlight = scheme.surfaceContainerHighest

    val transition = rememberInfiniteTransition(label = "shimmer")
    val offset by transition.animateFloat(
        initialValue = -400f,
        targetValue = 1400f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1300, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerOffset",
    )

    background(
        brush = Brush.linearGradient(
            colors = listOf(base, highlight, base),
            start = Offset(offset - 260f, 0f),
            end = Offset(offset, 220f),
        ),
        shape = shape ?: MaterialTheme.shapes.small,
    )
}