package com.vin.browser.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vin.browser.data.UserScript
import com.vin.browser.ui.theme.Sizes
import com.vin.browser.ui.theme.Space
import com.vin.browser.ui.theme.VinTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserScriptSheet(
    scripts: List<UserScript>,
    onToggleEnabled: (String) -> Unit,
    onSave: (UserScript) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val brand = VinTheme.colors
    val view = LocalView.current
    var showEditor by remember { mutableStateOf(false) }
    var editingScript by remember { mutableStateOf<UserScript?>(null) }

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
                        Icons.Filled.Code,
                        contentDescription = "Userscripts",
                        tint = scheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(Space.sm))
                    Text(
                        "Userscripts",
                        color = scheme.onSurface,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = {
                        editingScript = null
                        showEditor = true
                    }, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = "Add Script",
                            tint = scheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
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

            if (scripts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyListState(
                        icon = Icons.Filled.Code,
                        title = "No userscripts",
                        subtitle = "Add custom JavaScript or CSS to enhance pages"
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
                    items(scripts, key = { it.id }) { script ->
                        UserScriptRow(
                            script = script,
                            onToggle = {
                                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                                onToggleEnabled(script.id)
                            },
                            onEdit = {
                                editingScript = script
                                showEditor = true
                            },
                            onDelete = {
                                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                                onDelete(script.id)
                            }
                        )
                    }
                }
            }
        }
    }

    if (showEditor) {
        ScriptEditorSheet(
            script = editingScript,
            onSave = { onSave(it); showEditor = false },
            onDismiss = { showEditor = false }
        )
    }
}

@Composable
private fun UserScriptRow(
    script: UserScript,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme

    Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        color = scheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Space.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    script.name,
                    color = scheme.onSurface,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    if (script.isCss) "CSS" else "JavaScript" + (script.domain?.let { " ? $it" } ?: " ? Global"),
                    color = scheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1
                )
            }
            Switch(
                checked = script.isEnabled,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = androidx.compose.ui.graphics.Color.White,
                    checkedTrackColor = scheme.primary
                )
            )
            IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Filled.Code,
                    contentDescription = "Edit",
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(Sizes.iconSm)
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Delete",
                    tint = scheme.error,
                    modifier = Modifier.size(Sizes.iconSm)
                )
            }
        }
    }
}