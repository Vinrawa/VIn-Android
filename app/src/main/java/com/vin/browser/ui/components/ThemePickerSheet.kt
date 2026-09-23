package com.vin.browser.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vin.browser.ui.theme.Space

data class ThemeOption(
    val id: String,
    val name: String,
    val preview: List<Color>,
    val isDark: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemePickerSheet(
    currentPreset: String,
    onSelectTheme: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme

    val themes = listOf(
        ThemeOption("system", "System Default", listOf(Color(0xFFF8FAFC), Color(0xFF0B0E14)), false),
        ThemeOption("light", "Light", listOf(Color(0xFFF8FAFC), Color(0xFF2563EB), Color(0xFFE2E8F0)), false),
        ThemeOption("dark", "Dark", listOf(Color(0xFF0B0E14), Color(0xFF3B82F6), Color(0xFF1D2026)), true),
        ThemeOption("amoled", "AMOLED Black", listOf(Color.Black, Color(0xFF3B82F6), Color(0xFF111111)), true),
        ThemeOption("sepia", "Sepia", listOf(Color(0xFFF5E6D3), Color(0xFF8B6914), Color(0xFFE8D5C0)), false),
    )

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
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Palette,
                        contentDescription = "Theme",
                        tint = scheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(Space.sm))
                    Text(
                        "Choose Theme",
                        color = scheme.onSurface,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
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

            Spacer(Modifier.height(Space.lg))

            themes.forEach { theme ->
                val isSelected = theme.id == currentPreset
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isSelected) scheme.primary.copy(alpha = 0.1f) else scheme.surfaceContainerLow,
                    border = BorderStroke(
                        if (isSelected) 2.dp else 1.dp,
                        if (isSelected) scheme.primary else scheme.outlineVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = Space.xs)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onSelectTheme(theme.id) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Space.md),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Color preview dots
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            theme.preview.forEach { color ->
                                val circleModifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                if (color == Color.Black || color == Color(0xFF0B0E14) || color == Color(0xFF111111)) {
                                    Box(
                                        modifier = circleModifier.border(1.dp, Color(0xFF424754), CircleShape)
                                    )
                                } else {
                                    Box(modifier = circleModifier)
                                }
                            }
                        }

                        Spacer(Modifier.width(Space.md))

                        Text(
                            theme.name,
                            color = scheme.onSurface,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )

                        if (isSelected) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = "Selected",
                                tint = scheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}