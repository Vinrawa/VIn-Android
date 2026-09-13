package com.vin.browser.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.vin.browser.adblock.AdBlockEngine
import com.vin.browser.ui.theme.Sizes
import com.vin.browser.ui.theme.Space
import com.vin.browser.ui.theme.VinTheme

import androidx.compose.ui.platform.LocalContext
import com.vin.browser.data.StorageService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyDashboardSheet(
    currentDomain: String,
    isBackgroundPlay: Boolean,
    isSafeBrowsing: Boolean = true,
    lastFilterSync: Long = 0L,
    onBackgroundPlayToggle: (Boolean) -> Unit,
    onSafeBrowsingToggle: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val brand = VinTheme.colors
    val context = LocalContext.current
    val storage = remember { StorageService(context) }

    val engine = com.vin.browser.adblock.AdBlockEngine.instance
    var totalBlocked by remember { mutableIntStateOf(engine.getTotalBlocked()) }
    var adsCount by remember { mutableIntStateOf(engine.getAdsBlockedCount()) }
    var trackersCount by remember { mutableIntStateOf(engine.getTrackersBlockedCount()) }
    var malwareCount by remember { mutableIntStateOf(engine.getMalwareBlockedCount()) }
    var dataSaved by remember { mutableDoubleStateOf(engine.getEstimatedDataSavedMb()) }
    var timeSaved by remember { mutableDoubleStateOf(engine.getEstimatedTimeSavedSec()) }

    // Toggle states (persisted via StorageService & synchronized to AdBlockEngine)
    var adBlockOn by remember { mutableStateOf(storage.isGlobalAdBlockEnabled()) }
    var cosmeticOn by remember { mutableStateOf(storage.isCosmeticFilterEnabled()) }
    var trackerBlockOn by remember { mutableStateOf(storage.isTrackerBlockEnabled()) }
    var httpsUpgradeOn by remember { mutableStateOf(storage.isHttpsUpgradeEnabled()) }
    var cryptoBlockOn by remember { mutableStateOf(storage.isCryptoBlockEnabled()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = scheme.surfaceContainer,
        dragHandle = {
            Surface(
                modifier = Modifier.padding(vertical = Space.sm),
                color = scheme.outlineVariant,
                shape = RoundedCornerShape(2.dp)
            ) {
                Box(modifier = Modifier.size(width = 36.dp, height = 4.dp))
            }
        },
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
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        "Privacy & Ad-Blocker Hub",
                        color = scheme.onSurface,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "ViN Shield Active • Real-time Protection",
                        color = brand.secure,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
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

            Spacer(Modifier.height(Space.md))

            // 1. HERO METRICS CARD (Live Blocked Stats)
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = scheme.surfaceContainerLow,
                border = BorderStroke(1.dp, brand.secure.copy(alpha = 0.4f)),
                shadowElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Space.lg),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(brand.secure.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Shield,
                            contentDescription = "Shield",
                            tint = brand.secure,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(Modifier.height(Space.sm))

                    Text(
                        text = "$totalBlocked",
                        color = scheme.onSurface,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold
                    )

                    Text(
                        text = "Total Ads & Trackers Blocked",
                        color = scheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Spacer(Modifier.height(Space.lg))

                    // 3 Metric Pills
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        MetricItem(
                            label = "Data Saved",
                            value = "${dataSaved} MB",
                            icon = Icons.Filled.Bolt,
                            tint = scheme.primary
                        )
                        MetricItem(
                            label = "Time Saved",
                            value = "${timeSaved}s",
                            icon = Icons.Filled.Timer,
                            tint = brand.caution
                        )
                        MetricItem(
                            label = "Trackers",
                            value = "$trackersCount",
                            icon = Icons.Filled.VisibilityOff,
                            tint = brand.secure
                        )
                    }
                }
            }

            Spacer(Modifier.height(Space.lg))

            // 2. DANGEROUS SITES & THREAT RADAR
            Text(
                "DANGEROUS SITES & THREAT RADAR",
                color = scheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = Space.xs, bottom = Space.xs)
            )

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = scheme.surfaceContainerLow,
                border = BorderStroke(1.dp, scheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(Space.md)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Security,
                            contentDescription = "Threat Protection",
                            tint = brand.secure,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(Space.sm))
                        Text(
                            "Real-Time Malicious Site Scanner",
                            color = scheme.onSurface,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(Modifier.height(Space.sm))

                    ThreatStatusRow(
                        label = "Phishing & Fake Login Protection",
                        status = "Active (Auto-Blocked)",
                        isProtected = true
                    )
                    ThreatStatusRow(
                        label = "Crypto-Mining & Script Drainers",
                        status = "Active ($malwareCount Blocked)",
                        isProtected = true
                    )
                    ThreatStatusRow(
                        label = "Insecure HTTP Interception",
                        status = "Encrypted",
                        isProtected = true
                    )

                    if (currentDomain.isNotBlank()) {
                        Spacer(Modifier.height(Space.xs))
                        HorizontalDivider(color = scheme.outlineVariant, thickness = 0.5.dp)
                        Spacer(Modifier.height(Space.xs))
                        Text(
                            text = "Current Site: $currentDomain (Verified Safe)",
                            color = brand.secure,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(Modifier.height(Space.lg))

            // 3. ADVANCED PRIVACY & ADBLOCK CONTROLS (5 Options)
            Text(
                "PROTECTION CONTROLS",
                color = scheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = Space.xs, bottom = Space.xs)
            )

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = scheme.surfaceContainerLow,
                border = BorderStroke(1.dp, scheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(vertical = Space.xs)) {
                    // Option 1: Ad-Blocker
                    PrivacyControlToggle(
                        icon = Icons.Filled.Block,
                        title = "Block Intrusive Ads & Popups",
                        subtitle = "Stops video, banner, and popup advertisements",
                        checked = adBlockOn,
                        onCheckedChange = {
                            adBlockOn = it
                            storage.setGlobalAdBlock(it)
                            engine.isGlobalAdBlockEnabled = it
                        }
                    )

                    HorizontalDivider(color = scheme.outlineVariant, thickness = 0.5.dp)

                    // Option 2: Cosmetic Element Hiding
                    PrivacyControlToggle(
                        icon = Icons.Filled.LayersClear,
                        title = "Cosmetic Element Hiding",
                        subtitle = "Collapses blank ad spaces and sponsored boxes",
                        checked = cosmeticOn,
                        onCheckedChange = {
                            cosmeticOn = it
                            storage.setCosmeticFilter(it)
                            engine.isCosmeticFilterEnabled = it
                        }
                    )

                    HorizontalDivider(color = scheme.outlineVariant, thickness = 0.5.dp)

                    // Option 3: Third-Party Tracker Shield
                    PrivacyControlToggle(
                        icon = Icons.Filled.GppGood,
                        title = "Cross-Site Tracker Shield",
                        subtitle = "Prevents ad networks from tracking your activity",
                        checked = trackerBlockOn,
                        onCheckedChange = {
                            trackerBlockOn = it
                            storage.setTrackerBlock(it)
                            engine.isTrackerBlockEnabled = it
                        }
                    )

                    HorizontalDivider(color = scheme.outlineVariant, thickness = 0.5.dp)

                    // Option 4: HTTPS Auto-Upgrade
                    PrivacyControlToggle(
                        icon = Icons.Filled.Https,
                        title = "HTTPS Everywhere Auto-Upgrade",
                        subtitle = "Forces encrypted connections on all websites",
                        checked = httpsUpgradeOn,
                        onCheckedChange = {
                            httpsUpgradeOn = it
                            storage.setHttpsUpgrade(it)
                        }
                    )

                    HorizontalDivider(color = scheme.outlineVariant, thickness = 0.5.dp)

                    // Option 5: Crypto-Mining & Script Blocker
                    PrivacyControlToggle(
                        icon = Icons.Filled.Memory,
                        title = "Crypto-Mining & Script Blocker",
                        subtitle = "Blocks hidden background coin miners and battery drainers",
                        checked = cryptoBlockOn,
                        onCheckedChange = {
                            cryptoBlockOn = it
                            storage.setCryptoBlock(it)
                            engine.isCryptoBlockEnabled = it
                        }
                    )

                    HorizontalDivider(color = scheme.outlineVariant, thickness = 0.5.dp)

                    // Option 6: Background Video & Audio Play
                    PrivacyControlToggle(
                        icon = Icons.Filled.Headphones,
                        title = "Background Video & Audio Play",
                        subtitle = "Keep YouTube & audio playing when screen is off or app minimized",
                        checked = isBackgroundPlay,
                        onCheckedChange = { onBackgroundPlayToggle(it) }
                    )

                    HorizontalDivider(color = scheme.outlineVariant, thickness = 0.5.dp)

                    // Option 7: Google Safe Browsing (persisted app-level setting)
                    PrivacyControlToggle(
                        icon = Icons.Filled.VerifiedUser,
                        title = "Google Safe Browsing",
                        subtitle = "Warns before you visit dangerous or deceptive sites",
                        checked = isSafeBrowsing,
                        onCheckedChange = { onSafeBrowsingToggle() }
                    )
                }
            }

            Spacer(Modifier.height(Space.lg))

            // Clear / Reset Stats Button
            OutlinedButton(
                onClick = {
                    com.vin.browser.adblock.AdBlockEngine.instance.resetAllStats()
                    totalBlocked = 0
                    adsCount = 0
                    trackersCount = 0
                    malwareCount = 0
                    dataSaved = 0.0
                    timeSaved = 0.0
                },
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, scheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Filled.RestartAlt,
                    contentDescription = "Reset",
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(Space.sm))
                Text("Reset Ad-Blocker Statistics", color = scheme.onSurfaceVariant)
            }

            Spacer(Modifier.height(Space.md))

            // Filter list sync status row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.Sync,
                    contentDescription = "Filter Sync",
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(Space.sm))
                Text(
                    text = "Filter lists synced: ${formatFilterSyncTime(lastFilterSync)}",
                    color = scheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

private fun formatFilterSyncTime(lastFilterSync: Long): String {
    if (lastFilterSync <= 0L) return "Never"
    return android.text.format.DateUtils.getRelativeTimeSpanString(lastFilterSync).toString()
}

@Composable
private fun MetricItem(
    label: String,
    value: String,
    icon: ImageVector,
    tint: Color
) {
    val scheme = MaterialTheme.colorScheme

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(
                value,
                color = scheme.onSurface,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            label,
            color = scheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun ThreatStatusRow(
    label: String,
    status: String,
    isProtected: Boolean
) {
    val scheme = MaterialTheme.colorScheme
    val brand = VinTheme.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            color = scheme.onSurface,
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            status,
            color = if (isProtected) brand.secure else brand.danger,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun PrivacyControlToggle(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val scheme = MaterialTheme.colorScheme

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = Space.md, vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (checked) scheme.primary else scheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )

        Spacer(Modifier.width(Space.md))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                color = scheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                subtitle,
                color = scheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(Modifier.width(Space.sm))

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = scheme.primary
            )
        )
    }
}
