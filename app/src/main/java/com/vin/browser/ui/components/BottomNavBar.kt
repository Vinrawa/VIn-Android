package com.vin.browser.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vin.browser.ui.theme.Sizes
import com.vin.browser.ui.theme.Space

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BottomNavBar(
    tabCount: Int,
    isHome: Boolean,
    onHomeClick: () -> Unit,
    onSearchClick: () -> Unit,
    onNewTab: () -> Unit,
    onTabTrayClick: () -> Unit,
    onMenuClick: () -> Unit,
    onSwipeNextTab: () -> Unit,
    onSwipePrevTab: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val view = LocalView.current
    var totalDragOffset by remember { mutableFloatStateOf(0f) }
    val dragThreshold = 75f

    Surface(
        color = scheme.surfaceContainerHigh,
        shadowElevation = 8.dp,
        border = BorderStroke(0.5.dp, scheme.outlineVariant),
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { totalDragOffset = 0f },
                    onDragEnd = {
                        if (totalDragOffset > dragThreshold) {
                            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK )
                            onSwipePrevTab()
                        } else if (totalDragOffset < -dragThreshold) {
                            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK )
                            onSwipeNextTab()
                        }
                        totalDragOffset = 0f
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        totalDragOffset += dragAmount
                    }
                )
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(Sizes.bottomBarHeight)
                .padding(horizontal = Space.sm),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Home Button
            NavBarIconButton(
                icon = Icons.Filled.Home,
                contentDescription = "Home",
                enabled = true,
                isSelected = isHome,
                onClick = onHomeClick
            )

            // 2. Search Button
            NavBarIconButton(
                icon = Icons.Filled.Search,
                contentDescription = "Search",
                enabled = true,
                isSelected = false,
                onClick = onSearchClick
            )

            // 3. New Tab Button (+)
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .combinedClickable(onClick = onNewTab),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = "New Tab",
                    tint = scheme.onSurface,
                    modifier = Modifier.size(24.dp)
                )
            }

            // 4. Tab Count Button
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .combinedClickable(
                        onClick = onTabTrayClick,
                        onLongClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            onNewTab()
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Transparent,
                    border = BorderStroke(1.4.dp, scheme.onSurface),
                    modifier = Modifier.size(width = 26.dp, height = 22.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = if (tabCount >= 100) ":D" else tabCount.toString(),
                            color = scheme.onSurface,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // 5. Menu Button (?)
            NavBarIconButton(
                icon = Icons.Filled.Menu,
                contentDescription = "Menu",
                enabled = true,
                isSelected = false,
                onClick = onMenuClick
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NavBarIconButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    isSelected: Boolean = false,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme

    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .alpha(if (enabled) 1f else 0.35f)
            .combinedClickable(
                enabled = enabled,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = if (isSelected) scheme.primary else scheme.onSurface,
            modifier = Modifier.size(24.dp)
        )
    }
}