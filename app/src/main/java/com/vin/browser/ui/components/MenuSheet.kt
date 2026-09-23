package com.vin.browser.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
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
import com.vin.browser.adblock.AdBlockEngine
import com.vin.browser.ui.theme.Radius
import com.vin.browser.ui.theme.Sizes
import com.vin.browser.ui.theme.Space
import com.vin.browser.ui.theme.VinTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuSheet(
    isLiteMode: Boolean,
    isForcedDark: Boolean,
    isDesktopMode: Boolean,
    onDismiss: () -> Unit,
    onReload: () -> Unit,
    onShare: () -> Unit,
    onFindInPage: () -> Unit,
    onDesktopModeToggle: () -> Unit,
    onDownloadsClick: () -> Unit,
    onNewTab: () -> Unit,
    onNewIncognitoTab: () -> Unit,
    onAddBookmark: () -> Unit,
    onBookmarkClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onLiteModeToggle: () -> Unit,
    onForcedDarkToggle: () -> Unit,
    onPrivacyDashboardClick: () -> Unit,
    onDoctorClick: () -> Unit = {},
    onReaderViewClick: () -> Unit,
    onTranslateClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onReadingListClick: () -> Unit = {},
    onCopyLink: () -> Unit = {},
    onOpenExternal: () -> Unit = {},
    onQrGenerate: () -> Unit = {},
    onScreenshot: () -> Unit = {},
    onCookieManager: () -> Unit = {},
    onUserScripts: () -> Unit = {},
    onThemePicker: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val brand = VinTheme.colors
    val totalBlocked = AdBlockEngine.instance.getTotalBlocked()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = scheme.surfaceContainer,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.lg)
                .verticalScroll(rememberScrollState())
                .padding(bottom = Space.xxxl)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "ViN Browser",
                    color = scheme.onSurface,
                    style = MaterialTheme.typography.titleLarge
                )
                IconButton(onClick = onReload) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = "Reload Page",
                        tint = scheme.onSurface,
                        modifier = Modifier.size(Sizes.iconMd)
                    )
                }
            }
            Spacer(Modifier.height(Space.md))

            // Action grid -- weighted cells (same pattern as the Home speed-dial grid)
            Row(modifier = Modifier.fillMaxWidth()) {
                MenuGridAction(Icons.Filled.Share, "Share", false, onShare, Modifier.weight(1f))
                MenuGridAction(Icons.Filled.Search, "Find in Page", false, onFindInPage, Modifier.weight(1f))
                MenuGridAction(Icons.Filled.DesktopWindows, "Desktop Mode", isDesktopMode, onDesktopModeToggle, Modifier.weight(1f))
                MenuGridAction(Icons.Filled.Download, "Downloads", false, onDownloadsClick, Modifier.weight(1f))
            }
            Spacer(Modifier.height(Space.md))

            MenuSectionHeader("TABS & LIBRARY")
            MenuRow(Icons.Filled.Add, "New Tab", onNewTab)
            MenuRow(Icons.Filled.VisibilityOff, "New Incognito Tab", onNewIncognitoTab)
            MenuRow(Icons.Filled.BookmarkAdd, "Add Bookmark", onAddBookmark)
            MenuRow(Icons.Filled.Bookmarks, "Bookmarks", onBookmarkClick)
            MenuRow(Icons.Filled.History, "History", onHistoryClick)
            MenuRow(Icons.Filled.BookmarkBorder, "Reading List", onReadingListClick)

            MenuSectionHeader("PAGE")
            MenuRow(Icons.AutoMirrored.Filled.MenuBook, "Reader View", onReaderViewClick)
            MenuRow(Icons.Filled.Translate, "Translate Page?", onTranslateClick)

            MenuSectionHeader("TOOLS")
            MenuRow(Icons.Filled.ContentCopy, "Copy Link", onCopyLink)
            MenuRow(Icons.Filled.OpenInBrowser, "Open in External App", onOpenExternal)
            MenuRow(Icons.Filled.QrCode, "Generate QR Code", onQrGenerate)
            MenuRow(Icons.Filled.Screenshot, "Screenshot Page", onScreenshot)

            MenuSectionHeader("PRIVACY & PERFORMANCE")
            MenuRow(
                icon = Icons.Filled.Shield,
                label = "Ad-Blocker & Privacy Hub ($totalBlocked Blocked)",
                onClick = onPrivacyDashboardClick,
                tint = brand.secure
            )
            MenuRow(
                icon = Icons.Filled.HealthAndSafety,
                label = "System Health & Doctor",
                onClick = onDoctorClick,
                tint = Color(0xFF10B981)
            )
            MenuToggleRow(
                icon = if (isLiteMode) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                label = "Lite Mode",
                checked = isLiteMode,
                onToggle = onLiteModeToggle
            )
            MenuToggleRow(
                icon = Icons.Filled.DarkMode,
                label = "Force Dark Pages",
                checked = isForcedDark,
                onToggle = onForcedDarkToggle
            )

            MenuRow(Icons.Filled.Cookie, "Cookie Manager", onCookieManager)
            MenuRow(Icons.Filled.Code, "Userscripts", onUserScripts)

            MenuSectionHeader("SETTINGS")
            MenuRow(Icons.Filled.Palette, "Theme", onThemePicker)
            MenuRow(Icons.Filled.Settings, "Settings", onSettingsClick)
        }
    }
}

/** Navigation row -- SheetActionRow with the menu's neutral text color. */
@Composable
private fun MenuRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color? = null
) {
    val scheme = MaterialTheme.colorScheme
    SheetActionRow(
        icon = icon,
        label = label,
        tint = tint ?: scheme.onSurfaceVariant,
        labelColor = scheme.onSurface,
        onClick = onClick
    )
}

/** Toggle row -- thin container around the shared ToggleRowContent anatomy. */
@Composable
private fun MenuToggleRow(
    icon: ImageVector,
    label: String,
    checked: Boolean,
    onToggle: () -> Unit
) {
    ToggleRowContent(
        icon = icon,
        title = label,
        checked = checked,
        onToggle = onToggle,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.menuRowHeight)
            .clip(RoundedCornerShape(Radius.sm))
            .clickable(onClick = onToggle)
            .padding(horizontal = Space.sm)
    )
}

@Composable
private fun MenuSectionHeader(label: String) {
    val scheme = MaterialTheme.colorScheme
    Text(
        text = label,
        color = scheme.onSurfaceVariant,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.padding(start = Space.sm, top = Space.md, bottom = Space.xs)
    )
}

@Composable
private fun MenuGridAction(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(Radius.sm))
            .clickable(onClick = onClick)
            .padding(vertical = Space.xs)
    ) {
        Surface(
            shape = CircleShape,
            color = if (isActive) scheme.primary.copy(alpha = 0.2f) else scheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, if (isActive) scheme.primary else scheme.outlineVariant),
            modifier = Modifier.size(Sizes.gridActionCircle)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = null, // the label below announces it
                    tint = if (isActive) scheme.primary else scheme.onSurface,
                    modifier = Modifier.size(Sizes.iconMd)
                )
            }
        }
        Spacer(Modifier.height(Space.sm))
        Text(
            label,
            color = if (isActive) scheme.primary else scheme.onSurface,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}