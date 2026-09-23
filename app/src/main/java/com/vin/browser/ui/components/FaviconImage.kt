package com.vin.browser.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest

private val FaviconInsetLarge = 11.dp
private val FaviconInsetMedium = 6.dp
private val FaviconInsetSmall = 2.dp

private fun faviconInsetFor(size: Dp): Dp = when {
    size >= 48.dp -> FaviconInsetLarge
    size >= 32.dp -> FaviconInsetMedium
    else -> FaviconInsetSmall
}

/**
 * Site icon loader with real high-resolution brand favicon rendering
 * and typographic fallback.
 *
 * PRIVACY: favicons are fetched from DuckDuckGo's icon proxy, NOT Google's
 * s2 service -- previously every domain the user touched (including incognito
 * tabs, speed dials, history rows and search results) was disclosed to Google
 * as a favicon request. [isPrivate] skips the network entirely: private tabs
 * render the typographic fallback only.
 */
@Composable
fun FaviconImage(
    domain: String,
    modifier: Modifier = Modifier,
    bitmap: Bitmap? = null,
    size: Dp = 24.dp,
    shape: Shape = CircleShape,
    fallbackLetter: String = domain.take(1).uppercase(),
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    contentColor: Color = MaterialTheme.colorScheme.primary,
    isPrivate: Boolean = false
) {
    val host = remember(domain) { hostOf(domain) }
    val inset = remember(size) { faviconInsetFor(size) }

    // DuckDuckGo high-resolution icon proxy (no query strings, no user profile)
    val faviconUrl = "https://icons.duckduckgo.com/ip3/$host.ico"
    // Never fetch for private surfaces or blank hosts; letter fallback renders below.
    val mayFetchRemotely = !isPrivate && host.isNotBlank() && !host.contains(" ")

    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(containerColor),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(inset),
            )
        } else {
            // Fallback letter rendered underneath
            Text(
                text = fallbackLetter,
                color = contentColor,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = (size.value * 0.4f).sp,
                    fontWeight = FontWeight.Bold
                )
            )

            if (mayFetchRemotely) {
                // Real high-resolution brand favicon
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(faviconUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = "$host logo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(inset)
                )
            }
        }
    }
}

/** Reduces a URL, bare host or "host/path" label down to a registrable host. */
internal fun hostOf(raw: String): String = raw
    .substringAfter("://")
    .substringBefore('/')
    .substringBefore('?')
    .substringBefore(':')
    .removePrefix("www.")
    .ifBlank { raw }
