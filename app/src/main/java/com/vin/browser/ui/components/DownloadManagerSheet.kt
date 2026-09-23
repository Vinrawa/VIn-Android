package com.vin.browser.ui.components

import android.app.DownloadManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vin.browser.data.DownloadInfo
import com.vin.browser.ui.theme.Sizes
import com.vin.browser.ui.theme.Space
import com.vin.browser.ui.theme.VinTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadManagerSheet(
    downloads: List<DownloadInfo>,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val brand = VinTheme.colors
    var selectedTab by remember { mutableIntStateOf(0) }

    val activeDownloads = downloads.filter {
        it.status == DownloadManager.STATUS_RUNNING || it.status == DownloadManager.STATUS_PENDING || it.status == DownloadManager.STATUS_PAUSED
    }
    val completedDownloads = downloads.filter {
        it.status == DownloadManager.STATUS_SUCCESSFUL || it.status == DownloadManager.STATUS_FAILED
    }
    val visibleDownloads = if (selectedTab == 0) activeDownloads else completedDownloads

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
                        Icons.Filled.Download,
                        contentDescription = "Downloads",
                        tint = scheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(Space.sm))
                    Text(
                        "Downloads",
                        color = scheme.onSurface,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (downloads.isNotEmpty()) {
                        TextButton(onClick = onClearAll) {
                            Icon(
                                Icons.Filled.DeleteSweep,
                                contentDescription = "Clear",
                                tint = brand.danger,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Clear", color = brand.danger)
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

            // Tabs
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) {
                    Text("Active (${activeDownloads.size})", modifier = Modifier.padding(12.dp))
                }
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) {
                    Text("Completed (${completedDownloads.size})", modifier = Modifier.padding(12.dp))
                }
            }

            Spacer(Modifier.height(Space.sm))

            if (visibleDownloads.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyListState(
                        icon = Icons.Filled.Download,
                        title = if (selectedTab == 0) "No active downloads" else "No completed downloads"
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(Space.xs)
                ) {
                    items(visibleDownloads) { download ->
                        DownloadItemRow(download)
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadItemRow(download: DownloadInfo) {
    val scheme = MaterialTheme.colorScheme

    Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        color = scheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(Space.md)) {
            Text(
                text = download.fileName,
                color = scheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(Space.xs))
            if (download.totalBytes > 0) {
                LinearProgressIndicator(
                    progress = { (download.bytesDownloaded.toFloat() / download.totalBytes).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp),
                    color = when (download.status) {
                        DownloadManager.STATUS_SUCCESSFUL -> scheme.primary
                        DownloadManager.STATUS_FAILED -> scheme.error
                        else -> scheme.primary
                    },
                    trackColor = scheme.surfaceContainerHighest,
                )
            }
            Spacer(Modifier.height(Space.xs))
            Text(
                text = formatBytes(download.bytesDownloaded),
                color = scheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "${"%.1f".format(bytes / (1024.0 * 1024.0))} MB"
    }
}