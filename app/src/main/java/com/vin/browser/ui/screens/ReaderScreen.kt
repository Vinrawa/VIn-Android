package com.vin.browser.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.TextDecrease
import androidx.compose.material.icons.filled.TextIncrease
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.vin.browser.ui.theme.Sizes
import com.vin.browser.ui.theme.Space
import com.vin.browser.ui.theme.VinTheme

/** Minimal article model extracted from the live WebView DOM by [com.vin.browser.engine.ReaderExtractor]. */
data class ReaderArticle(
    val title: String,
    val byline: String,
    val paragraphs: List<String>
)

/**
 * Full-screen Reader Mode overlay. Font size (14..24sp) lives only in memory,
 * resetting when the overlay is closed. Pure black background in dark theme for AMOLED.
 *
 * Typography note: body text is USER-SCALED, so it cannot use static type roles —
 * sizes derive from the slider. This is the app's single documented exception;
 * weights remain fixed constants, never ad-hoc.
 */
@Composable
fun ReaderScreen(
    article: ReaderArticle?,
    isLoading: Boolean,
    isFailed: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val brand = VinTheme.colors
    val backgroundColor = if (brand.isDark) Color(0xFF000000) else Color.White
    val textColor = if (brand.isDark) Color(0xFFE8E8E8) else Color(0xFF1A1A1A)
    var fontSize by remember { mutableIntStateOf(17) }
    val headlineSize = fontSize + 4

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
            // Tap-blocker: Compose passes taps through nodes without pointer
            // handlers — without this, blank regions of the reader (margins,
            // gaps between paragraphs) tap straight through to the live WebView
            // behind the overlay. Children with their own handlers (LazyColumn
            // scroll, buttons) win hit-testing; only fall-through zones consume.
            .pointerInput(Unit) { detectTapGestures { } }
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Chrome row: Close | title | type-size controls — all 48dp icon targets
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Close Reader View",
                    tint = textColor,
                    modifier = Modifier.size(Sizes.iconMd)
                )
            }
            Text(
                text = article?.title ?: "",
                color = textColor,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { fontSize = (fontSize + 1).coerceAtMost(24) }) {
                Icon(
                    Icons.Filled.TextIncrease,
                    contentDescription = "Increase Text Size",
                    tint = textColor,
                    modifier = Modifier.size(Sizes.iconMd)
                )
            }
            IconButton(onClick = { fontSize = (fontSize - 1).coerceAtLeast(14) }) {
                Icon(
                    Icons.Filled.TextDecrease,
                    contentDescription = "Decrease Text Size",
                    tint = textColor,
                    modifier = Modifier.size(Sizes.iconMd)
                )
            }
        }

        when {
            isLoading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = textColor)
            }

            // Failed state follows the app-wide pattern: icon → gap → message → action
            isFailed -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.AutoMirrored.Filled.MenuBook,
                        contentDescription = null,
                        tint = textColor.copy(alpha = 0.6f),
                        modifier = Modifier.size(Sizes.emptyStateIcon)
                    )
                    Spacer(Modifier.height(Space.md))
                    Text(
                        "Reader view is not available for this page",
                        color = textColor,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = Space.xl)
                    )
                    Spacer(Modifier.height(Space.lg))
                    Button(onClick = onClose) {
                        Text("Close")
                    }
                }
            }

            article != null -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                // 20dp horizontal: a touch more measure for long-form reading
                contentPadding = PaddingValues(horizontal = Space.xl, vertical = Space.lg),
                verticalArrangement = Arrangement.spacedBy(Space.md)
            ) {
                item {
                    Text(
                        text = article.title,
                        color = textColor,
                        fontSize = headlineSize.sp,
                        lineHeight = (headlineSize * 1.3f).sp,
                        fontWeight = FontWeight.SemiBold // matches the app's title weight
                    )
                    Spacer(Modifier.height(Space.xs))
                    Text(
                        text = article.byline,
                        color = textColor.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.labelMedium
                    )
                    Spacer(Modifier.height(Space.md))
                }
                items(article.paragraphs) { paragraph ->
                    Text(
                        text = paragraph,
                        color = textColor,
                        fontSize = fontSize.sp,
                        lineHeight = (fontSize * 1.4f).sp
                    )
                }
            }
        }
    }
}
