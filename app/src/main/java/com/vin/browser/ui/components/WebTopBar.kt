package com.vin.browser.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vin.browser.ui.theme.Sizes
import com.vin.browser.ui.theme.Space
import com.vin.browser.ui.theme.VinTheme

@Composable
fun WebTopBar(
    title: String,
    url: String,
    isSecure: Boolean,
    isLoading: Boolean,
    progress: Int,
    trackersBlocked: Int,
    isIncognito: Boolean = false,
    onShieldClick: () -> Unit,
    onUrlClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val brand = VinTheme.colors

    val displayUrl = remember(url) {
        url.removePrefix("https://").removePrefix("http://").removePrefix("www.")
    }

    // Animated smooth progress bar
    val animatedProgress by animateFloatAsState(
        targetValue = progress / 100f,
        animationSpec = tween(durationMillis = 150),
        label = "web_progress"
    )
    val progressAlpha by animateFloatAsState(
        targetValue = if (progress in 1..99) 1f else 0f,
        animationSpec = tween(durationMillis = 250),
        label = "web_progress_alpha"
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Surface(
            color = scheme.surfaceContainerHigh,
            shadowElevation = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Sizes.topBarHeight)
                    .padding(horizontal = Space.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.sm)
            ) {
                // Omnibox URL Display (Pill Shape) -- full-height chip family
                Surface(
                    shape = RoundedCornerShape(Sizes.topBarChipHeight / 2),
                    color = scheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, if (!isSecure) brand.danger.copy(alpha = 0.6f) else scheme.outlineVariant),
                    modifier = Modifier
                        .weight(1f)
                        .height(Sizes.topBarChipHeight)
                        .clickable { onUrlClick() }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = Space.md),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left Security Indicator / Loading Spinner
                        Box(
                            modifier = Modifier
                                .size(Sizes.topBarChipHeight)
                                .clickable { onShieldClick() },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isLoading && progress in 1..99) {
                                CircularProgressIndicator(
                                    strokeWidth = 2.dp,
                                    color = scheme.primary,
                                    modifier = Modifier.size(Sizes.iconSm)
                                )
                            } else if (isSecure) {
                                Icon(
                                    Icons.Filled.Lock,
                                    contentDescription = "Secure HTTPS",
                                    tint = scheme.onSurfaceVariant,
                                    modifier = Modifier.size(Sizes.iconSm)
                                )
                            } else {
                                Icon(
                                    Icons.Filled.Warning,
                                    contentDescription = "Insecure HTTP",
                                    tint = brand.danger,
                                    modifier = Modifier.size(Sizes.iconSm)
                                )
                            }
                        }

                        Spacer(Modifier.width(Space.xs))

                        // URL / Domain with Insecure Warning prefix if HTTP
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (!isSecure) {
                                Text(
                                    text = "Not secure -- ",
                                    color = brand.danger,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                            }

                            Text(
                                text = if (displayUrl.isNotBlank()) displayUrl else title,
                                color = if (isSecure) scheme.onSurface else brand.danger,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Incognito indicator chip -- same height as the URL pill
                if (isIncognito) {
                    Surface(
                        shape = RoundedCornerShape(Sizes.chipRadius),
                        color = Color(0xFF14141B),
                        border = BorderStroke(1.dp, Color(0xFF2C2C38)),
                        modifier = Modifier.height(Sizes.topBarChipHeight)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxHeight()
                                .padding(horizontal = Space.sm),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.VisibilityOff,
                                contentDescription = "Incognito",
                                tint = Color(0xFFB8BCC8),
                                modifier = Modifier.size(Sizes.iconXs + 2.dp)
                            )
                            Spacer(Modifier.width(Space.xs))
                            Text(
                                text = "Incognito",
                                color = Color(0xFFB8BCC8),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Right Side: Emerald Shield Blocked Counter Badge (same chip family)
                Surface(
                    shape = RoundedCornerShape(Sizes.chipRadius),
                    color = brand.secure.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, brand.secure.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .height(Sizes.topBarChipHeight)
                        .widthIn(min = 62.dp)
                        .clickable { onShieldClick() }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(horizontal = Space.sm),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Filled.Shield,
                            contentDescription = "ViN Shield",
                            tint = brand.secure,
                            modifier = Modifier.size(Sizes.iconSm)
                        )
                        Spacer(Modifier.width(Space.xs))
                        Text(
                            text = if (trackersBlocked > 99) "99+" else trackersBlocked.toString(),
                            color = brand.secure,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Thin Red Warning Strip if Insecure HTTP
        if (!isSecure) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(brand.danger)
            )
        }

        // Smooth Horizontal Page Load Progress Bar
        if (progressAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Sizes.progressHeight)
                    .alpha(progressAlpha)
                    .background(scheme.surfaceContainerHighest)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction = animatedProgress.coerceIn(0f, 1f))
                        .background(scheme.primary)
                )
            }
        }
    }
}