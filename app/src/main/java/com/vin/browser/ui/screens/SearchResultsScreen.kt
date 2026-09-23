package com.vin.browser.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vin.browser.data.SearchResult
import com.vin.browser.ui.components.FaviconImage
import com.vin.browser.ui.components.shimmerEffect
import com.vin.browser.ui.theme.Sizes
import com.vin.browser.ui.theme.Space
import com.vin.browser.ui.theme.VinTheme

@Composable
fun SearchResultsScreen(
    results: List<SearchResult>,
    isSearching: Boolean,
    query: String,
    onResultClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val brand = VinTheme.colors

    // Real provider filters derived from the actual result mix (replaces the old
    // decorative Images/News/Videos tabs that filtered nothing).
    val providerOrder = listOf("Official", "Bookmarks", "History", "DuckDuckGo", "Wikipedia", "Reddit", "GitHub", "Stack Overflow", "Web")
    val availableProviders = results.map { it.engineName }.distinct()
        .sortedBy { p -> providerOrder.indexOf(p).let { if (it == -1) Int.MAX_VALUE else it } }
    var selectedFilter by remember { mutableStateOf("All") }
    val visibleResults = if (selectedFilter == "All") results
    else results.filter { it.engineName == selectedFilter }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(scheme.background)
    ) {
        // Provider filter chips
        LazyRow(
            contentPadding = PaddingValues(horizontal = Space.lg, vertical = Space.sm),
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
            modifier = Modifier.fillMaxWidth()
        ) {
            val filters = listOf("All") + availableProviders
            items(filters) { filter ->
                val isSelected = filter == selectedFilter
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isSelected) scheme.surfaceContainerHighest else scheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, if (isSelected) scheme.primary else scheme.outlineVariant),
                    modifier = Modifier.clickable { selectedFilter = filter }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = filter,
                            color = if (isSelected) scheme.onSurface else scheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                        if (filter == "All") {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "${results.size}",
                                color = scheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = scheme.outlineVariant, thickness = 0.5.dp)

        if (isSearching) {
            // Skeleton Shimmer Loading Cards
            LazyColumn(
                contentPadding = PaddingValues(horizontal = Space.lg, vertical = Space.md),
                verticalArrangement = Arrangement.spacedBy(Space.md)
            ) {
                item {
                    Box(
                        modifier = Modifier
                            .width(140.dp)
                            .height(14.dp)
                            .shimmerEffect(shape = RoundedCornerShape(4.dp))
                    )
                    Spacer(Modifier.height(Space.sm))
                }

                items(5) {
                    SkeletonResultCard()
                }
            }
        } else if (visibleResults.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(Space.huge),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No results found for \"$query\"",
                    color = scheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = Space.lg, vertical = Space.md),
                verticalArrangement = Arrangement.spacedBy(Space.sm)
            ) {
                // Hero card: best-ranked result stands out
                val hero = visibleResults.first()
                item(key = "hero-${hero.id}") {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = scheme.surfaceContainerHigh,
                        border = BorderStroke(1.5.dp, scheme.primary.copy(alpha = 0.55f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onResultClick(hero.url) }
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(Space.lg)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = scheme.primary.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        "TOP RESULT",
                                        color = scheme.primary,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                                Spacer(Modifier.width(Space.sm))
                                Text(
                                    text = hero.engineName,
                                    color = scheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            Spacer(Modifier.height(Space.sm))
                            Text(
                                hero.title,
                                color = scheme.onSurface,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(Space.xs))
                            Text(
                                hero.snippet,
                                color = scheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                lineHeight = 19.sp
                            )
                        }
                    }
                }

                items(visibleResults.drop(1), key = { it.id }) { result ->
                    ResultCard(result = result, onResultClick = onResultClick)
                }
            }
        }
    }
}

@Composable
private fun ResultCard(
    result: SearchResult,
    onResultClick: (String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val brand = VinTheme.colors

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = scheme.surfaceContainerLow,
        border = BorderStroke(
            1.dp,
            if (result.isOfficial) brand.secure.copy(alpha = 0.6f) else scheme.outlineVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onResultClick(result.url) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Space.md)
        ) {
            // Favicon + provider badge + Official Badge
            Row(verticalAlignment = Alignment.CenterVertically) {
                FaviconImage(
                    domain = result.domain,
                    size = Sizes.faviconSm
                )

                Spacer(Modifier.width(Space.sm))

                Text(
                    text = result.domain.take(38),
                    color = scheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                Spacer(Modifier.width(Space.sm))

                // Provider chip: shows WHERE this result came from (Reddit, GitHub,
                // Wikipedia, your History...) -- the unified search is transparent
                // about its sources.
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = scheme.surfaceContainerHighest
                ) {
                    Text(
                        text = result.engineName,
                        color = scheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }

                if (result.isOfficial) {
                    Spacer(Modifier.width(Space.xs))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = brand.secureContainer,
                        border = BorderStroke(0.5.dp, brand.secure.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.Shield,
                                contentDescription = "Official",
                                tint = brand.secure,
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "Official",
                                color = brand.onSecureContainer,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else if (result.isSmallWeb) {
                    Spacer(Modifier.width(Space.xs))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = brand.secureContainer,
                    ) {
                        Text(
                            "Indie Web",
                            color = brand.onSecureContainer,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(Space.xs))

            // Title
            Text(
                result.title,
                color = scheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(Space.xxs))

            // Snippet
            Text(
                result.snippet,
                color = scheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 19.sp
            )
        }
    }
}

@Composable
private fun SkeletonResultCard() {
    val scheme = MaterialTheme.colorScheme

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = scheme.surfaceContainerLow,
        border = BorderStroke(1.dp, scheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(Space.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(Sizes.faviconSm)
                        .shimmerEffect(shape = CircleShape)
                )
                Spacer(Modifier.width(Space.sm))
                Box(
                    modifier = Modifier
                        .width(120.dp)
                        .height(12.dp)
                        .shimmerEffect(shape = RoundedCornerShape(3.dp))
                )
            }

            Spacer(Modifier.height(Space.sm))

            Box(
                modifier = Modifier
                    .fillMaxWidth(0.75f)
                    .height(16.dp)
                    .shimmerEffect(shape = RoundedCornerShape(4.dp))
            )

            Spacer(Modifier.height(Space.sm))

            Box(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .height(12.dp)
                    .shimmerEffect(shape = RoundedCornerShape(3.dp))
            )
            Spacer(Modifier.height(Space.xs))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(12.dp)
                    .shimmerEffect(shape = RoundedCornerShape(3.dp))
            )
        }
    }
}
