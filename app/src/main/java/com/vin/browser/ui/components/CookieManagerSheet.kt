package com.vin.browser.ui.components

import android.webkit.CookieManager
import android.webkit.WebView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cookie
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vin.browser.ui.theme.Sizes
import com.vin.browser.ui.theme.Space
import com.vin.browser.ui.theme.VinTheme

data class CookieEntry(val name: String, val value: String, val domain: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CookieManagerSheet(
    domain: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val brand = VinTheme.colors
    val cookieManager = remember { CookieManager.getInstance() }

    var allCookies by remember { mutableStateOf<List<CookieEntry>>(emptyList()) }
    var cookieCount by remember { mutableIntStateOf(0) }

    // CookieManager expects a full URL (scheme + host); passing the bare domain
    // like "youtube.com" returns nothing on many WebView builds. Also probe both
    // schemes -- session cookies can live under http:// on legacy sites.
    val siteUrl = remember(domain) {
        if (domain.startsWith("http://") || domain.startsWith("https://")) domain
        else "https://$domain"
    }
    val altUrl = remember(siteUrl) {
        if (siteUrl.startsWith("https://")) "http://${siteUrl.removePrefix("https://")}" else null
    }

    fun readCookies(): List<CookieEntry> {
        val cookies = mutableListOf<CookieEntry>()
        val raw = (cookieManager.getCookie(siteUrl) ?: "") +
            (altUrl?.let { cookieManager.getCookie(it) } ?: "")
        if (raw.isNotBlank()) {
            raw.split(";").distinct().forEach { single ->
                val parts = single.trim().split("=", limit = 2)
                if (parts.size == 2) {
                    cookies.add(CookieEntry(parts[0].trim(), parts[1].trim(), domain))
                }
            }
        }
        return cookies.distinctBy { it.name }
    }

    LaunchedEffect(domain) {
        allCookies = readCookies()
        cookieCount = allCookies.size
    }

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
                        Icons.Filled.Cookie,
                        contentDescription = "Cookies",
                        tint = scheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(Space.sm))
                    Column {
                        Text(
                            "Cookie Manager",
                            color = scheme.onSurface,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "$cookieCount cookies on $domain",
                            color = scheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
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

            Spacer(Modifier.height(Space.sm))
            HorizontalDivider(color = scheme.outlineVariant)

            if (allCookies.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyListState(
                        icon = Icons.Filled.Cookie,
                        title = "No cookies for this site"
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
                    items(allCookies) { cookie ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = Space.xs),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    cookie.name,
                                    color = scheme.onSurface,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    cookie.value.take(60),
                                    color = scheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            // Clear buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Space.sm),
                horizontalArrangement = Arrangement.spacedBy(Space.sm)
            ) {
                OutlinedButton(
                    onClick = {
                        // WebView has no "delete cookie by name" API; the standard
                        // technique is overwriting each cookie with an expired
                        // empty value, then flushing to disk.
                        allCookies.forEach { cookie ->
                            runCatching {
                                cookieManager.setCookie(
                                    siteUrl,
                                    "${cookie.name}=; Path=/; Expires=Thu, 01 Jan 1970 00:00:00 GMT"
                                )
                            }
                        }
                        cookieManager.flush()
                        allCookies = emptyList()
                        cookieCount = 0
                    },
                    modifier = Modifier.weight(1f),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                ) {
                    Text("Clear for $domain", color = scheme.onSurfaceVariant, maxLines = 1)
                }
                Button(
                    onClick = {
                        cookieManager.removeAllCookies(null)
                        cookieManager.flush()
                        allCookies = emptyList()
                        cookieCount = 0
                    },
                    modifier = Modifier.weight(1f),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = brand.danger)
                ) {
                    Icon(Icons.Filled.DeleteSweep, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(Space.xs))
                    Text("Clear All", color = Color.White)
                }
            }
        }
    }
}