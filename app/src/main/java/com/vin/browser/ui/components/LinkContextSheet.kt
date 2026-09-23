package com.vin.browser.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tab
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vin.browser.ui.theme.Space

/** What the user long-pressed: a link, an image, or an image wrapped in a link. */
data class LinkTarget(
    val url: String,
    val imageUrl: String? = null
)

/** One row in the menu: icon, label, and the action to run. */
private data class LinkAction(val icon: androidx.compose.ui.graphics.vector.ImageVector, val label: String, val action: () -> Unit)

/**
 * Long-press menu for links and images inside the page.
 * All actions run on-device; nothing is uploaded anywhere.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkContextSheet(
    target: LinkTarget,
    onDismiss: () -> Unit,
    onOpenInNewTab: (String) -> Unit,
    onOpenInBackground: (String) -> Unit,
    onDownload: (String) -> Unit
) {
    val context = LocalContext.current
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scheme = MaterialTheme.colorScheme
    val url = target.url

    val copyAction: () -> Unit = {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("url", url))
        android.widget.Toast.makeText(context, "Link copied", android.widget.Toast.LENGTH_SHORT).show()
        onDismiss()
    }
    val shareAction: () -> Unit = {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, url)
        }
        context.startActivity(Intent.createChooser(send, null))
        onDismiss()
    }

    val actions = buildList {
        add(LinkAction(Icons.Filled.Tab, "Open in new tab") { onDismiss(); onOpenInNewTab(url) })
        if (target.imageUrl == null) {
            add(LinkAction(Icons.Filled.VisibilityOff, "Open in background") { onDismiss(); onOpenInBackground(url) })
        }
        add(LinkAction(Icons.Filled.ContentCopy, "Copy link", copyAction))
        add(LinkAction(Icons.Filled.Share, "Share", shareAction))
        add(LinkAction(Icons.Filled.Download, "Download") { onDismiss(); onDownload(url) })
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = Space.xl)
        ) {
            Text(
                text = url,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            )
            for (item in actions) {
                SheetActionRow(
                    icon = item.icon,
                    label = item.label,
                    onClick = item.action
                )
            }
        }
    }
}