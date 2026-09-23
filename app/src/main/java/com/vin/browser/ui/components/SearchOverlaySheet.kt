package com.vin.browser.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vin.browser.data.SearchProviders
import com.vin.browser.data.SuggestionItem
import com.vin.browser.ui.theme.Sizes
import com.vin.browser.ui.theme.Space
import com.vin.browser.ui.theme.VinTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchOverlaySheet(
    initialQuery: String,
    selectedEngineId: String,
    suggestions: List<SuggestionItem>,
    onSearch: (String) -> Unit,
    onQueryChange: (String) -> Unit,
    onEngineClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var queryText by remember { mutableStateOf(initialQuery) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val engine = SearchProviders.getEngine(selectedEngineId)
    val scheme = MaterialTheme.colorScheme

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = scheme.surfaceContainerHigh,
        dragHandle = null,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.lg)
                .padding(top = Space.md, bottom = Space.xl)
        ) {
            // Search Input Row -- same pill family as the home omnibox
            Surface(
                shape = RoundedCornerShape(Sizes.omniboxRadius),
                color = scheme.surfaceContainerHighest,
                border = BorderStroke(1.dp, scheme.outlineVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Sizes.omniboxHeight)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = Space.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = scheme.onSurfaceVariant,
                            modifier = Modifier.size(Sizes.iconMd)
                        )
                    }

                    BasicTextField(
                        value = queryText,
                        onValueChange = {
                            queryText = it
                            onQueryChange(it)
                        },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface),
                        cursorBrush = SolidColor(scheme.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                if (queryText.isNotBlank()) {
                                    onSearch(queryText)
                                    focusManager.clearFocus()
                                }
                            }
                        ),
                        decorationBox = { innerTextField ->
                            Box {
                                if (queryText.isEmpty()) {
                                    Text(
                                        "Search or enter URL...",
                                        color = scheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                }
                                innerTextField()
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester)
                    )

                    if (queryText.isNotEmpty()) {
                        IconButton(
                            onClick = { queryText = "" },
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Clear",
                                tint = scheme.onSurfaceVariant,
                                modifier = Modifier.size(Sizes.iconMd)
                            )
                        }
                    }

                    // Engine Icon Picker Button
                    Surface(
                        shape = CircleShape,
                        color = scheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, scheme.primary.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .padding(end = Space.xs)
                            .size(36.dp)
                            .clickable { onEngineClick() }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            FaviconImage(
                                domain = engine.directUrl,
                                size = 36.dp,
                                fallbackLetter = if (engine.id == "all") "ALL" else engine.shortName.take(1),
                            )
                        }
                    }
                }
            }

            // Bang shortcut hint (only while typing the bang prefix)
            if (queryText.startsWith("!") && queryText.length <= 4) {
                Spacer(Modifier.height(Space.sm))
                Text(
                    text = "Try !yt, !w, !gh, !r, !g",
                    color = scheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(start = Space.sm)
                )
            }

            // Live suggestions: history matches + network autocomplete
            if (suggestions.isNotEmpty()) {
                Spacer(Modifier.height(Space.sm))
                Surface(
                    shape = RoundedCornerShape(Space.lg),
                    color = scheme.surfaceContainerHighest,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp)
                    ) {
                        items(suggestions) { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = Sizes.menuRowHeight)
                                    .clickable {
                                        onSearch(item.value)
                                        focusManager.clearFocus()
                                    }
                                    .padding(horizontal = Space.md),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    if (item.isHistory) Icons.Filled.History else Icons.Filled.Search,
                                    contentDescription = if (item.isHistory) "History" else "Suggestion",
                                    tint = scheme.onSurfaceVariant,
                                    modifier = Modifier.size(Sizes.iconSm)
                                )
                                Spacer(Modifier.width(Space.md))
                                Text(
                                    text = item.label,
                                    color = scheme.onSurface,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (item.isHistory) FontWeight.Medium else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            LaunchedEffect(Unit) {
                focusRequester.requestFocus()
            }
        }
    }
}