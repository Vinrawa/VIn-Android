package com.vin.browser.ui.screens

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vin.browser.data.SearchProviders
import com.vin.browser.data.SpeedDialItem
import com.vin.browser.ui.components.FaviconImage
import com.vin.browser.ui.theme.Sizes
import com.vin.browser.ui.theme.Space

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    selectedEngineId: String,
    speedDials: List<SpeedDialItem>,
    onOmniboxClick: () -> Unit,
    onSpeedDialClick: (String) -> Unit,
    onAddSpeedDial: (String, String) -> Unit,
    onUpdateSpeedDial: (String, String, String) -> Unit,
    onDeleteSpeedDial: (String) -> Unit,
    onEngineClick: () -> Unit,
    onMicClick: () -> Unit,
    onQrScanClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val view = LocalView.current
    val engine = SearchProviders.getEngine(selectedEngineId)

    var showAddDialog by remember { mutableStateOf(false) }
    var selectedItemForAction by remember { mutableStateOf<SpeedDialItem?>(null) }
    var editingItem by remember { mutableStateOf<SpeedDialItem?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(scheme.background)
    ) {
        // ── Compact Top Bar: G logo + Search + Mic + QR + VPN ──
        Surface(
            color = scheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.md, vertical = Space.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.sm)
            ) {
                // Google G logo
                FaviconImage(
                    domain = engine.directUrl,
                    size = 28.dp,
                    fallbackLetter = "G",
                )

                // Search placeholder text
                Text(
                    text = "Search or type web address",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onOmniboxClick() }
                        .padding(vertical = Space.xs),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Mic icon
                IconButton(
                    onClick = onMicClick,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Filled.Mic,
                        contentDescription = "Voice Search",
                        tint = scheme.onSurfaceVariant,
                        modifier = Modifier.size(Sizes.iconSm)
                    )
                }

                // QR Scanner icon
                IconButton(
                    onClick = onQrScanClick,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Filled.QrCodeScanner,
                        contentDescription = "Scan QR Code",
                        tint = scheme.onSurfaceVariant,
                        modifier = Modifier.size(Sizes.iconSm)
                    )
                }

                // VPN button
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color.Transparent,
                    border = BorderStroke(1.dp, scheme.onSurfaceVariant),
                    modifier = Modifier.clickable { /* VPN toggle */ }
                ) {
                    Text(
                        text = "VPN",
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(
                            horizontal = Space.sm,
                            vertical = Space.xs
                        )
                    )
                }
            }
        }

        // ── Speed Dial Section ──
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(top = Space.lg)
        ) {
            // Header: "Speed Dial" + list icon
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.lg),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Speed Dial",
                    color = scheme.onSurface,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )

                IconButton(
                    onClick = { showAddDialog = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Filled.ViewList,
                        contentDescription = "Speed Dial Options",
                        tint = scheme.onSurfaceVariant,
                        modifier = Modifier.size(Sizes.iconMd)
                    )
                }
            }

            Spacer(Modifier.height(Space.sm))

            // Horizontal scrollable speed dial row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = Space.lg),
                horizontalArrangement = Arrangement.spacedBy(Space.md)
            ) {
                speedDials.forEach { item ->
                    HomeSpeedDialTile(
                        item = item,
                        onClick = { onSpeedDialClick(item.url) },
                        onLongClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            selectedItemForAction = item
                        }
                    )
                }
                HomeAddTile(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                        showAddDialog = true
                    }
                )
            }
        }
    }

    // ── Context Action BottomSheet for Edit / Delete ──
    selectedItemForAction?.let { item ->
        ModalBottomSheet(
            onDismissRequest = { selectedItemForAction = null },
            containerColor = scheme.surfaceContainer
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.lg)
                    .padding(bottom = Space.xxl)
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = Space.sm)
                )

                SheetActionRow(
                    icon = Icons.Filled.Edit,
                    label = "Edit Shortcut",
                    tint = scheme.primary,
                    onClick = {
                        editingItem = item
                        selectedItemForAction = null
                    }
                )

                SheetActionRow(
                    icon = Icons.Filled.Delete,
                    label = "Remove Shortcut",
                    tint = scheme.error,
                    onClick = {
                        onDeleteSpeedDial(item.id)
                        selectedItemForAction = null
                    }
                )
            }
        }
    }

    // ── Add Shortcut Dialog ──
    if (showAddDialog) {
        var name by remember { mutableStateOf("") }
        var url by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add Shortcut", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name (e.g. YouTube)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text("URL (e.g. youtube.com)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (url.isNotBlank()) {
                            onAddSpeedDial(name, url)
                            showAddDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = scheme.primary)
                ) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("Cancel") }
            }
        )
    }

    // ── Edit Shortcut Dialog ──
    editingItem?.let { item ->
        var name by remember { mutableStateOf(item.title) }
        var url by remember { mutableStateOf(item.url) }

        AlertDialog(
            onDismissRequest = { editingItem = null },
            title = { Text("Edit Shortcut", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text("URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (url.isNotBlank()) {
                            onUpdateSpeedDial(item.id, name, url)
                            editingItem = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = scheme.primary)
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { editingItem = null }) { Text("Cancel") }
            }
        )
    }
}

// ── Horizontal Speed Dial Tile ──
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeSpeedDialTile(
    item: SpeedDialItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(72.dp)
            .clip(RoundedCornerShape(Sizes.chipRadius))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(vertical = Space.xs)
    ) {
        Surface(
            shape = RoundedCornerShape(Space.lg),
            color = scheme.surfaceContainerLow,
            border = BorderStroke(1.dp, scheme.outline),
            modifier = Modifier.size(60.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                FaviconImage(
                    domain = item.domain,
                    size = 60.dp,
                    shape = RoundedCornerShape(Space.lg),
                    fallbackLetter = item.iconLetter.ifBlank {
                        item.title.take(1)
                    }.uppercase(),
                    containerColor = scheme.surfaceContainerLow,
                    contentColor = scheme.primary
                )
            }
        }

        Spacer(Modifier.height(Space.xs))

        Text(
            text = item.title,
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

// ── Add Shortcut Tile ──
@Composable
private fun HomeAddTile(onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(72.dp)
            .clip(RoundedCornerShape(Sizes.chipRadius))
            .clickable(onClick = onClick)
            .padding(vertical = Space.xs)
    ) {
        Surface(
            shape = RoundedCornerShape(Space.lg),
            color = scheme.surfaceContainerLow,
            border = BorderStroke(1.dp, scheme.outlineVariant),
            modifier = Modifier.size(60.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = "Add Shortcut",
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(Sizes.iconLg)
                )
            }
        }

        Spacer(Modifier.height(Space.xs))

        Text(
            text = "Add",
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}

// ── Shared Sheet Action Row ──
@Composable
private fun SheetActionRow(
    icon: ImageVector,
    label: String,
    tint: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.menuRowHeight)
            .clip(RoundedCornerShape(Sizes.chipRadius))
            .clickable(onClick = onClick)
            .padding(horizontal = Space.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(Sizes.iconMd))
        Spacer(Modifier.width(Space.lg))
        Text(label, color = tint, style = MaterialTheme.typography.bodyLarge)
    }
}
