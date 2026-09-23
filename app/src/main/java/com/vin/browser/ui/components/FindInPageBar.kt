package com.vin.browser.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vin.browser.ui.theme.Space

@Composable
fun FindInPageBar(
    query: String,
    currentIndex: Int,
    totalMatches: Int,
    onQueryChange: (String) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Surface(
        color = scheme.surfaceContainerHigh,
        shadowElevation = 6.dp,
        border = BorderStroke(0.5.dp, scheme.outlineVariant),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.sm, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Text Input Box
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = scheme.surfaceContainerLow,
                border = BorderStroke(1.dp, scheme.outline),
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = Space.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        singleLine = true,
                        textStyle = TextStyle(
                            color = scheme.onSurface,
                            fontSize = 14.sp
                        ),
                        cursorBrush = SolidColor(scheme.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { onNext() }),
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester)
                    )

                    // Match Counter (e.g. 3/17)
                    if (query.isNotBlank()) {
                        Text(
                            text = if (totalMatches > 0) "${currentIndex + 1}/$totalMatches" else "0/0",
                            color = if (totalMatches > 0) scheme.primary else scheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.width(4.dp))

            // Previous Button (?)
            IconButton(onClick = onPrevious, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Filled.KeyboardArrowUp,
                    contentDescription = "Previous Match",
                    tint = scheme.onSurface,
                    modifier = Modifier.size(22.dp)
                )
            }

            // Next Button (?)
            IconButton(onClick = onNext, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = "Next Match",
                    tint = scheme.onSurface,
                    modifier = Modifier.size(22.dp)
                )
            }

            // Close Button (?)
            IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Close Find in Page",
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}