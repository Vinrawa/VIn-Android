package com.vin.browser.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vin.browser.ui.theme.Radius
import com.vin.browser.ui.theme.Sizes
import com.vin.browser.ui.theme.Space

/** Drag handle for ModalBottomSheets. */
@Composable
fun VinDragHandle(
    color: Color = MaterialTheme.colorScheme.outlineVariant,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.padding(vertical = Space.sm),
        color = color,
        shape = RoundedCornerShape(2.dp)
    ) {
        Box(modifier = Modifier.size(width = 36.dp, height = 4.dp))
    }
}

/** Standard sheet header with title and close button. */
@Composable
fun SheetHeader(
    title: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Space.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = scheme.onSurface,
            style = MaterialTheme.typography.titleLarge
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            action?.invoke()
            IconButton(onClick = onClose) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Close",
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(Sizes.iconMd)
                )
            }
        }
    }
}

/** Standard action row for sheets and menus. */
@Composable
fun SheetActionRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    labelColor: Color = Color.Unspecified
) {
    val scheme = MaterialTheme.colorScheme
    val textCol = if (labelColor == Color.Unspecified) tint else labelColor

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.menuRowHeight)
            .clip(RoundedCornerShape(Radius.sm))
            .clickable(onClick = onClick)
            .padding(horizontal = Space.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(Sizes.iconMd)
        )
        Spacer(Modifier.width(Space.lg))
        Text(
            text = label,
            color = textCol,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

/** Standard toggle row content with Switch. */
@Composable
fun ToggleRowContent(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (checked) scheme.primary else scheme.onSurfaceVariant,
            modifier = Modifier.size(Sizes.iconMd)
        )
        Spacer(Modifier.width(Space.lg))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = scheme.onSurface,
                style = MaterialTheme.typography.bodyLarge
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = scheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        Spacer(Modifier.width(Space.sm))
        Switch(
            checked = checked,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = scheme.primary
            )
        )
    }
}

/** Search result or engine tag badge. */
@Composable
fun ResultBadge(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Surface(
        shape = RoundedCornerShape(Radius.xs),
        color = containerColor,
        modifier = modifier
    ) {
        Text(
            text = text,
            color = contentColor,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = Space.xs, vertical = Space.xxs)
        )
    }
}

/** Generic list entry row (history/bookmarks). */
@Composable
fun ListEntryRow(
    title: String,
    subtitle: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onTrailingClick: (() -> Unit)? = null
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.sm))
            .clickable(onClick = onClick)
            .padding(vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()
        Spacer(Modifier.width(Space.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = scheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                color = scheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (onTrailingClick != null) {
            IconButton(onClick = onTrailingClick) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = null,
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(Sizes.iconSm)
                )
            }
        }
    }
}

/** Standard empty list placeholder state. */
@Composable
fun EmptyListState(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                icon,
                contentDescription = null,
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(Sizes.emptyStateIcon)
            )
            Spacer(Modifier.height(Space.md))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = scheme.onSurface
            )
            if (subtitle != null) {
                Spacer(Modifier.height(Space.xs))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = Space.xl)
                )
            }
        }
    }
}
