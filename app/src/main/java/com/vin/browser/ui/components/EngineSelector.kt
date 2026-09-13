package com.vin.browser.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vin.browser.data.SearchProviders
import com.vin.browser.ui.theme.Sizes
import com.vin.browser.ui.theme.Space

@Composable
fun EngineSelector(
    selectedId: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = scheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, scheme.outlineVariant),
        shadowElevation = 12.dp,
        modifier = modifier
            .fillMaxWidth()
            .padding(Space.lg),
    ) {
        Column(modifier = Modifier.padding(Space.lg)) {
            Text(
                text = "Search Engine",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = scheme.onSurface,
                modifier = Modifier.padding(bottom = Space.md),
            )

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(Space.xs),
                modifier = Modifier.heightIn(max = 400.dp)
            ) {
                items(SearchProviders.engines) { engine ->
                    val isSelected = engine.id == selectedId
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) scheme.surfaceContainerHighest else Color.Transparent,
                        border = if (isSelected) BorderStroke(1.dp, scheme.primary.copy(alpha = 0.5f)) else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(engine.id) },
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Space.md, vertical = Space.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            FaviconImage(
                                domain = engine.directUrl,
                                size = Sizes.faviconMd,
                                fallbackLetter = if (engine.id == "all") "ALL" else engine.shortName.take(1),
                            )

                            Spacer(Modifier.width(Space.md))

                            Text(
                                text = engine.name,
                                color = if (isSelected) scheme.primary else scheme.onSurface,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                modifier = Modifier.weight(1f)
                            )

                            if (isSelected) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = "Selected",
                                    tint = scheme.primary,
                                    modifier = Modifier.size(Sizes.iconMd),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
