package com.vin.browser.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vin.browser.doctor.AutoTuneResult
import com.vin.browser.doctor.HealthMetrics

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DoctorDiagnosticSheet(
    metrics: HealthMetrics,
    autoTuneResult: AutoTuneResult?,
    isTuning: Boolean,
    onRunAutoTune: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0D0E15),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFF334155))
            )
        },
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF1E293B)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.HealthAndSafety,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "System Health & Doctor",
                            color = Color(0xFFF1F5F9),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "ViN Core Performance Monitor",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp
                        )
                    }
                }

                // Grade Pill
                val gradeColor = when (metrics.statusGrade) {
                    "OPTIMAL" -> Color(0xFF10B981)
                    "GOOD" -> Color(0xFF3B82F6)
                    else -> Color(0xFFF59E0B)
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(gradeColor.copy(alpha = 0.15f))
                        .border(1.dp, gradeColor.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = metrics.statusGrade,
                        color = gradeColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            // Score Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF131522))
                    .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(18.dp))
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Overall Health Score",
                            color = Color(0xFF94A3B8),
                            fontSize = 13.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = metrics.healthScore.toString() + "/100",
                            color = Color(0xFFF8FAFC),
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Black
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = if (metrics.healthScore >= 85) "Engine running at maximum efficiency" else "Tap Auto-Tune to optimize memory & latency",
                            color = Color(0xFF64748B),
                            fontSize = 11.sp
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1E2235)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            progress = { metrics.healthScore / 100f },
                            modifier = Modifier.size(48.dp),
                            color = if (metrics.healthScore >= 85) Color(0xFF10B981) else Color(0xFF3B82F6),
                            trackColor = Color(0xFF334155),
                            strokeWidth = 4.dp
                        )
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = null,
                            tint = Color(0xFFCBD5E1),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Metrics Grid (2x2)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard(
                    icon = Icons.Default.Bolt,
                    iconTint = Color(0xFF38BDF8),
                    title = "Latency (Avg)",
                    value = if (metrics.avgLoadTimeMs > 0) metrics.avgLoadTimeMs.toString() + " ms" else "Ready",
                    subtext = metrics.latencyRating,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    icon = Icons.Default.Memory,
                    iconTint = Color(0xFFA855F7),
                    title = "JVM Heap RAM",
                    value = metrics.usedMemoryMb.toString() + " MB",
                    subtext = metrics.memoryPercent.toString() + "% of " + metrics.maxMemoryMb + "MB",
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(10.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard(
                    icon = Icons.Default.Shield,
                    iconTint = Color(0xFF10B981),
                    title = "AdBlock Shield",
                    value = metrics.trackersBlockedTotal.toString() + " Blocked",
                    subtext = "O(1) Domain Filter Active",
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    icon = Icons.Default.Layers,
                    iconTint = Color(0xFFF59E0B),
                    title = "Active Tabs",
                    value = metrics.activeTabsCount.toString() + " Open",
                    subtext = if (metrics.backgroundPlayActive) "Background Audio: Active" else "Background Audio: Idle",
                    modifier = Modifier.weight(1f)
                )
            }

            // Auto-Tune Result Card (if applied)
            AnimatedVisibility(
                visible = autoTuneResult != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                if (autoTuneResult != null) {
                    Spacer(Modifier.height(14.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF064E3B).copy(alpha = 0.4f))
                            .border(1.dp, Color(0xFF10B981).copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                            .padding(14.dp)
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF34D399),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "Auto-Tune Completed (+~" + autoTuneResult.memoryFreedMb + "MB Freed)",
                                    color = Color(0xFF34D399),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            autoTuneResult.optimizationsApplied.forEach { action ->
                                Text(
                                    text = "* " + action,
                                    color = Color(0xFFA7F3D0),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // Auto-Tune Button
            Button(
                onClick = onRunAutoTune,
                enabled = !isTuning,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF3B82F6),
                    disabledContainerColor = Color(0xFF1E293B)
                )
            ) {
                if (isTuning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("Tuning & Optimizing...", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                } else {
                    Icon(
                        imageVector = Icons.Default.AutoFixHigh,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Run Auto-Tune & Flush Cache", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun MetricCard(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    value: String,
    subtext: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF131522))
            .border(1.dp, Color(0xFF1E2235), RoundedCornerShape(14.dp))
            .padding(12.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = title,
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = value,
                color = Color(0xFFF1F5F9),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtext,
                color = Color(0xFF64748B),
                fontSize = 10.sp,
                maxLines = 1
            )
        }
    }
}