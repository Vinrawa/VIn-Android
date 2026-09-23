package com.vin.browser.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vin.browser.data.UserScript
import com.vin.browser.ui.theme.Space
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScriptEditorSheet(
    script: UserScript?,
    onSave: (UserScript) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme

    var name by remember { mutableStateOf(script?.name ?: "") }
    var domain by remember { mutableStateOf(script?.domain ?: "") }
    var isCss by remember { mutableStateOf(script?.isCss ?: false) }
    var code by remember { mutableStateOf(script?.code ?: "") }

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
                Text(
                    if (script != null) "Edit Script" else "New Script",
                    color = scheme.onSurface,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Close",
                        tint = scheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(Modifier.height(Space.md))

            // Name
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Script Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(Modifier.height(Space.sm))

            // Domain (optional)
            OutlinedTextField(
                value = domain,
                onValueChange = { domain = it },
                label = { Text("Domain (optional, empty = all sites)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(Modifier.height(Space.sm))

            // Type toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Space.sm)
            ) {
                FilterChip(
                    selected = !isCss,
                    onClick = { isCss = false },
                    label = { Text("JavaScript") },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = isCss,
                    onClick = { isCss = true },
                    label = { Text("CSS") },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(Space.sm))

            // Code editor
            OutlinedTextField(
                value = code,
                onValueChange = { code = it },
                label = { Text(if (isCss) "CSS Code" else "JavaScript Code") },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = scheme.surfaceContainerLow,
                    unfocusedContainerColor = scheme.surfaceContainerLow
                )
            )

            Spacer(Modifier.height(Space.xl))

            // Save button
            Button(
                onClick = {
                    if (name.isNotBlank() && code.isNotBlank()) {
                        val finalDomain = domain.ifBlank { null }
                        val savedScript = if (script != null) {
                            script.copy(
                                name = name,
                                domain = finalDomain,
                                code = code,
                                isCss = isCss
                            )
                        } else {
                            UserScript(
                                id = UUID.randomUUID().toString(),
                                name = name,
                                domain = finalDomain,
                                code = code,
                                isCss = isCss
                            )
                        }
                        onSave(savedScript)
                    }
                },
                enabled = name.isNotBlank() && code.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = scheme.primary)
            ) {
                Icon(Icons.Filled.Save, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Space.sm))
                Text("Save Script", color = Color.White)
            }
        }
    }
}