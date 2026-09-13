package com.vin.browser.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.vin.browser.data.SiteControlSettings
import com.vin.browser.data.SiteTrustInfo
import com.vin.browser.ui.theme.Radius
import com.vin.browser.ui.theme.Sizes
import com.vin.browser.ui.theme.Space
import com.vin.browser.ui.theme.VinTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrustSheet(
    trustInfo: SiteTrustInfo,
    siteSettings: SiteControlSettings,
    onUpdateSettings: (SiteControlSettings) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val brand = VinTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = scheme.surfaceContainer,
        dragHandle = { VinDragHandle(scheme.outlineVariant) },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.lg)
                .verticalScroll(rememberScrollState())
                .padding(bottom = Space.xxxl)
        ) {
            // Site header card
            Surface(
                color = scheme.surfaceContainerHigh,
                shape = RoundedCornerShape(Radius.md),
                border = BorderStroke(1.dp, scheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(Space.lg),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FaviconImage(
                        domain = trustInfo.domain,
                        size = Sizes.faviconXl
                    )
                    Spacer(Modifier.width(Space.md))
                    Column {
                        Text(
                            text = trustInfo.domain,
                            color = scheme.onSurface,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(Space.xxs))
                        Text(
                            text = if (trustInfo.isSecure)
                                "Verified Safe • ${trustInfo.trackersBlocked} Trackers Blocked"
                            else "Unencrypted Connection",
                            color = if (trustInfo.isSecure) brand.secure else brand.danger,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
            Spacer(Modifier.height(Space.xl))

            // Permissions
            Text(
                "Permissions",
                color = scheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = Space.sm)
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                SettingCard(
                    icon = Icons.Filled.CameraAlt, title = "CAMERA", value = siteSettings.camera,
                    onClick = {
                        val next = when (siteSettings.camera) { "Ask" -> "Allow"; "Allow" -> "Block"; else -> "Ask" }
                        onUpdateSettings(siteSettings.copy(camera = next))
                    },
                    modifier = Modifier.weight(1f)
                )
                SettingCard(
                    icon = Icons.Filled.Mic, title = "MICROPHONE", value = siteSettings.microphone,
                    onClick = {
                        val next = when (siteSettings.microphone) { "Ask" -> "Allow"; "Allow" -> "Block"; else -> "Ask" }
                        onUpdateSettings(siteSettings.copy(microphone = next))
                    },
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(Space.sm))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                SettingCard(
                    icon = Icons.Filled.LocationOn, title = "LOCATION", value = siteSettings.location,
                    onClick = {
                        val next = when (siteSettings.location) { "Ask" -> "Allow"; "Allow" -> "Block"; else -> "Ask" }
                        onUpdateSettings(siteSettings.copy(location = next))
                    },
                    modifier = Modifier.weight(1f)
                )
                SettingCard(
                    icon = Icons.Filled.Download, title = "DOWNLOADS", value = siteSettings.downloads,
                    onClick = {
                        val next = when (siteSettings.downloads) { "Default" -> "Allow"; "Allow" -> "Block"; else -> "Default" }
                        onUpdateSettings(siteSettings.copy(downloads = next))
                    },
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(Space.xl))

            // Settings
            Text(
                "Settings",
                color = scheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = Space.sm)
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                SettingCard(
                    icon = Icons.Filled.Shield, title = "ADVERTISEMENTS",
                    value = if (siteSettings.adsBlocked) "Blocked" else "Allowed",
                    onClick = { onUpdateSettings(siteSettings.copy(adsBlocked = !siteSettings.adsBlocked)) },
                    modifier = Modifier.weight(1f)
                )
                SettingCard(
                    icon = Icons.Filled.Image, title = "IMAGES",
                    value = if (siteSettings.imagesEnabled) "Default" else "Off",
                    onClick = { onUpdateSettings(siteSettings.copy(imagesEnabled = !siteSettings.imagesEnabled)) },
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(Space.sm))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                SettingCard(
                    icon = Icons.Filled.Window, title = "POPUPS",
                    value = if (siteSettings.popupsBlocked) "Blocked" else "Allowed",
                    onClick = { onUpdateSettings(siteSettings.copy(popupsBlocked = !siteSettings.popupsBlocked)) },
                    modifier = Modifier.weight(1f)
                )
                SettingCard(
                    icon = Icons.Filled.Devices, title = "DEVICE MODE",
                    value = if (siteSettings.desktopMode) "Desktop" else "Mobile",
                    onClick = { onUpdateSettings(siteSettings.copy(desktopMode = !siteSettings.desktopMode)) },
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(Space.sm))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                SettingCard(
                    icon = Icons.Filled.Headphones, title = "PLAY AUDIO",
                    value = if (siteSettings.audioMuted) "Mute" else "Default",
                    onClick = { onUpdateSettings(siteSettings.copy(audioMuted = !siteSettings.audioMuted)) },
                    modifier = Modifier.weight(1f)
                )
                SettingCard(
                    icon = Icons.AutoMirrored.Filled.OpenInNew, title = "OPEN APPS",
                    value = if (siteSettings.openApps) "Allow" else "Default",
                    onClick = { onUpdateSettings(siteSettings.copy(openApps = !siteSettings.openApps)) },
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(Space.xl))

            // SSL Certificate
            Text(
                "SSL Certificate",
                color = scheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = Space.sm)
            )
            Surface(
                shape = RoundedCornerShape(Radius.md),
                color = scheme.surfaceContainerLow,
                border = BorderStroke(1.dp, scheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(Space.md)) {
                    CertField("Domain", trustInfo.domain)
                    Spacer(Modifier.height(Space.md))
                    CertField(
                        "Connection",
                        if (trustInfo.isSecure) "Encrypted (HTTPS)" else "Not Encrypted"
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingCard(
    icon: ImageVector,
    title: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        color = scheme.surfaceContainerHigh,
        shape = RoundedCornerShape(Radius.sm),
        border = BorderStroke(1.dp, scheme.outlineVariant),
        modifier = modifier
            .clip(RoundedCornerShape(Radius.sm))
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(Space.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null, // the visible title text announces it
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(Sizes.iconSm)
            )
            Spacer(Modifier.width(Space.sm))
            Column {
                Text(
                    text = title,
                    color = scheme.onSurface,
                    style = MaterialTheme.typography.labelSmall
                )
                Spacer(Modifier.height(Space.xxs))
                Text(
                    text = value,
                    color = scheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun CertField(label: String, value: String) {
    val scheme = MaterialTheme.colorScheme
    Column {
        Text(
            text = label,
            color = scheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall
        )
        Spacer(Modifier.height(Space.xxs))
        Text(
            text = value,
            color = scheme.onSurface,
            style = MaterialTheme.typography.labelLarge
        )
    }
}
