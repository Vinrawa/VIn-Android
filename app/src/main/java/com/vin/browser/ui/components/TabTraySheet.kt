package com.vin.browser.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vin.browser.data.TabState
import com.vin.browser.data.TabThumbnailManager
import com.vin.browser.ui.theme.Radius
import com.vin.browser.ui.theme.Sizes
import com.vin.browser.ui.theme.Space
import com.vin.browser.ui.theme.VinTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabTraySheet(
    tabs: List<TabState>,
    activeTabId: String,
    incognitoCount: Int,
    onTabClick: (String) -> Unit,
    onCloseTab: (String) -> Unit,
    onCloseAllTabs: () -> Unit,
    onNewTab: () -> Unit,
    onNewIncognitoTab: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val brand = VinTheme.colors
    val view = LocalView.current
    var selectedSegment by remember { mutableIntStateOf(0) } // 0 = Standard, 1 = Incognito
    val standardTabs = tabs.filter { !it.isIncognito }
    val incognitoTabs = tabs.filter { it.isIncognito }
    val visibleTabs = if (selectedSegment == 1) incognitoTabs else standardTabs

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = scheme.surfaceContainer,
        modifier = modifier.fillMaxHeight(0.92f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Space.lg)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Space.xs),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${tabs.size} ${if (tabs.size == 1) "Tab" else "Tabs"}",
                    color = scheme.onSurface,
                    style = MaterialTheme.typography.titleLarge
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (tabs.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                onCloseAllTabs()
                            }
                        ) {
                            Icon(
                                Icons.Filled.DeleteSweep,
                                contentDescription = null,
                                tint = brand.danger,
                                modifier = Modifier.size(Sizes.iconSm)
                            )
                            Spacer(Modifier.width(Space.xs))
                            Text("Close All", color = brand.danger)
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Close Tab Tray",
                            tint = scheme.onSurfaceVariant,
                            modifier = Modifier.size(Sizes.iconMd)
                        )
                    }
                }
            }
            Spacer(Modifier.height(Space.xs))

            // Segmented control — pill family, 40dp
            Surface(
                shape = Radius.pill,
                color = scheme.surfaceContainerHigh,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Sizes.filterChipHeight)
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    TabSegmentButton(
                        label = "Tabs (${standardTabs.size})",
                        isSelected = selectedSegment == 0,
                        onClick = { selectedSegment = 0 },
                        modifier = Modifier.weight(1f)
                    )
                    TabSegmentButton(
                        label = "Incognito ($incognitoCount)",
                        isSelected = selectedSegment == 1,
                        onClick = { selectedSegment = 1 },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(Modifier.height(Space.md))

            if (visibleTabs.isEmpty()) {
                EmptyTabsState(
                    isIncognito = selectedSegment == 1,
                    modifier = Modifier.weight(1f)
                )
            } else {
                TabGrid(
                    tabs = visibleTabs,
                    activeTabId = activeTabId,
                    onTabClick = onTabClick,
                    onCloseTab = {
                        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                        onCloseTab(it)
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            // CTA — pill, 48dp
            Button(
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    if (selectedSegment == 1) onNewIncognitoTab() else onNewTab()
                },
                colors = ButtonDefaults.buttonColors(containerColor = scheme.primary),
                shape = Radius.pill,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Space.md)
                    .height(Sizes.minTouchTarget)
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White)
                Spacer(Modifier.width(Space.sm))
                Text(
                    if (selectedSegment == 1) "New Incognito Tab" else "New Tab",
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun TabGrid(
    tabs: List<TabState>,
    activeTabId: String,
    onTabClick: (String) -> Unit,
    onCloseTab: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        horizontalArrangement = Arrangement.spacedBy(Space.md),
        verticalArrangement = Arrangement.spacedBy(Space.md),
        modifier = modifier.fillMaxWidth()
    ) {
        items(tabs, key = { it.id }) { tab ->
            TabCardItem(
                tab = tab,
                isActive = tab.id == activeTabId,
                onClick = { onTabClick(tab.id) },
                onClose = { onCloseTab(tab.id) }
            )
        }
    }
}

@Composable
private fun EmptyTabsState(
    isIncognito: Boolean,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                if (isIncognito) Icons.Filled.VisibilityOff else Icons.Filled.Add,
                contentDescription = null,
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(Sizes.emptyStateIcon)
            )
            Spacer(Modifier.height(Space.md))
            Text(
                if (isIncognito) "Incognito Mode" else "No Open Tabs",
                style = MaterialTheme.typography.titleMedium,
                color = scheme.onSurface
            )
            Spacer(Modifier.height(Space.xs))
            Text(
                if (isIncognito)
                    "Pages you view in incognito won't be saved in your history or cookies."
                else
                    "Tap New Tab below to start browsing.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = Space.xl)
            )
        }
    }
}

@Composable
private fun TabSegmentButton(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .fillMaxHeight()
            .padding(Space.xxs)
            .clip(Radius.pill)
            .background(if (isSelected) scheme.primary else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (isSelected) Color.White else scheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1
        )
    }
}

@Composable
private fun TabCardItem(
    tab: TabState,
    isActive: Boolean,
    onClick: () -> Unit,
    onClose: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val thumbnail = remember(tab.id) { TabThumbnailManager.getThumbnail(tab.id) }
    var dragOffsetX by remember { mutableFloatStateOf(0f) }

    // Incognito cards keep a fixed dark scheme regardless of app theme
    val cardColor = if (tab.isIncognito) Color(0xFF15151C) else scheme.surfaceContainerHigh
    val cardBorder = if (tab.isIncognito) Color(0xFF2C2C38) else scheme.outlineVariant
    val titleColor = if (tab.isIncognito) Color(0xFFE4E4EA) else scheme.onSurface
    val previewColor = if (tab.isIncognito) Color(0xFF0E0E13) else scheme.surfaceContainerLow

    Surface(
        shape = RoundedCornerShape(Radius.md),
        color = cardColor,
        border = BorderStroke(
            if (isActive) 2.dp else 1.dp,
            if (isActive) scheme.primary else cardBorder
        ),
        shadowElevation = if (isActive) 6.dp else 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .offset(x = dragOffsetX.dp)
            .pointerInput(tab.id) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (dragOffsetX > 80f || dragOffsetX < -80f) onClose()
                        else dragOffsetX = 0f
                    },
                    onHorizontalDrag = { _, dragAmount -> dragOffsetX += dragAmount * 0.5f }
                )
            }
            .clip(RoundedCornerShape(Radius.md))
            .clickable { onClick() }
    ) {
        // Hibernated tabs read dimmed — their favicon/preview bitmaps were released
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(if (tab.isHibernated) 0.75f else 1f)
        ) {
            // Preview (16:10)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 10f)
                    .background(previewColor),
                contentAlignment = Alignment.Center
            ) {
                if (thumbnail != null) {
                    Image(
                        bitmap = thumbnail.asImageBitmap(),
                        contentDescription = "Tab preview",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        Icons.Filled.Language,
                        contentDescription = null,
                        tint = scheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(Sizes.emptyStateIcon)
                    )
                }
                // Incognito badge (no preview is ever captured → always visible)
                if (tab.isIncognito) {
                    Icon(
                        Icons.Filled.VisibilityOff,
                        contentDescription = "Incognito tab",
                        tint = Color(0xFF9CA3AF),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(Space.sm)
                            .size(Sizes.iconXs)
                    )
                }
                // Close — 32dp target, ripple clipped to its circle
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(Space.xs)
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f))
                        .clickable { onClose() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Close Tab",
                        tint = Color.White,
                        modifier = Modifier.size(Sizes.iconSm)
                    )
                }
            }
            // Info row: favicon + title + hibernation state
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.sm, vertical = Space.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FaviconImage(
                    domain = tab.url,
                    size = Sizes.faviconSm,
                    fallbackLetter = tab.title.take(1).uppercase()
                )
                Spacer(Modifier.width(Space.xs))
                Text(
                    text = if (tab.isHome) "New Tab" else tab.title,
                    color = titleColor,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (tab.isHibernated) {
                    Icon(
                        Icons.Filled.Bedtime,
                        contentDescription = "Hibernated tab",
                        tint = scheme.onSurfaceVariant,
                        modifier = Modifier.size(Sizes.iconXs)
                    )
                }
            }
        }
    }
}
