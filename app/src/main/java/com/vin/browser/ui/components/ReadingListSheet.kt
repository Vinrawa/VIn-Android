package com.vin.browser.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vin.browser.data.ReadingListItem
import com.vin.browser.ui.theme.Sizes
import com.vin.browser.ui.theme.Space
import com.vin.browser.ui.theme.VinTheme
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadingListSheet(
    items: List<ReadingListItem>,
    onItemClick: (String) -> Unit,
    onRemoveItem: (String) -> Unit,
    onSaveOffline: (String) -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val brand = VinTheme.colors
    val view = LocalView.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = scheme.surfaceContainer,
        modifier = modifier.fillMaxHeight(0.85f)
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.BookmarkBorder,
                        contentDescription = "Reading List",
                        tint = scheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(Space.sm))
                    Text(
                        "Reading List",
                        color = scheme.onSurface,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (items.isNotEmpty()) {
                        TextButton(onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                            onClearAll()
                        }) {
                            Icon(
                                Icons.Filled.DeleteSweep,
                                contentDescription = "Clear",
                                tint = brand.danger,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Clear All", color = brand.danger, fontWeight = FontWeight.Bold)
                        }
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Close",
                            tint = scheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(Space.sm))
            HorizontalDivider(color = scheme.outlineVariant)

            if (items.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyListState(
                        icon = Icons.Filled.BookmarkBorder,
                        title = "No saved articles",
                        subtitle = "Tap the menu → Reading List to save pages for later"
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(vertical = Space.sm),
                    verticalArrangement = Arrangement.spacedBy(Space.xs)
                ) {
                    items(items, key = { it.id }) { item ->
                        ReadingListItemRow(
                            item = item,
                            onClick = { onItemClick(item.url) },
                            onRemove = { onRemoveItem(item.id) },
                            onSaveOffline = { onSaveOffline(item.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadingListItemRow(
    item: ReadingListItem,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    onSaveOffline: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val view = LocalView.current
    val dateFormat = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FaviconImage(
            domain = item.url,
            size = Sizes.faviconSm,
            fallbackLetter = item.title.take(1).uppercase()
        )
        Spacer(Modifier.width(Space.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                color = scheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = dateFormat.format(Date(item.addedAt)),
                color = scheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1
            )
        }

        // Offline badge
        if (item.isOfflineReady) {
            Icon(
                Icons.Filled.CloudDownload,
                contentDescription = "Saved offline",
                tint = scheme.primary,
                modifier = Modifier.size(Sizes.iconSm)
            )
            Spacer(Modifier.width(Space.xs))
        }

        // More options
        IconButton(
            onClick = {
                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                onSaveOffline()
            },
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                Icons.Filled.CloudDownload,
                contentDescription = "Save Offline",
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(Sizes.iconSm)
            )
        }

        IconButton(
            onClick = {
                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                onRemove()
            },
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "Remove",
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(Sizes.iconSm)
            )
        }
    }
}
