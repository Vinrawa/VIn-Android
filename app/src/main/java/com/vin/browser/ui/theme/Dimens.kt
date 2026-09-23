package com.vin.browser.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Spacing and sizing tokens. The old UI used 19 different arbitrary dp values
 * for padding; this is a 4dp-based scale with named steps.
 */
object Space {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp
    val huge = 40.dp
}

object Sizes {
    /** Material accessibility minimum for anything tappable. */
    val minTouchTarget = 48.dp

    val iconXs = 14.dp
    val iconSm = 18.dp
    val iconMd = 22.dp
    val iconLg = 26.dp

    val faviconSm = 20.dp
    val faviconMd = 28.dp
    val faviconLg = 40.dp
    val faviconXl = 52.dp

    // Omnibox family: one height, one radius (true pill) shared by every search bar
    val omniboxHeight = 52.dp
    val omniboxRadius = 26.dp
    val omniboxChipInset = 6.dp

    // Top bar chips (URL pill, incognito chip, shield badge) share one height
    val topBarChipHeight = 40.dp
    val filterChipHeight = 40.dp
    val chipRadius = 12.dp

    val navBarHeight = 60.dp
    val bottomBarHeight = 56.dp
    val topBarHeight = 56.dp
    val progressHeight = 2.dp
    val hairline = 0.5.dp

    val heroEmblem = 76.dp
    val heroIcon = 40.dp
    val emptyStateIcon = 40.dp
    val speedDialTile = 56.dp
    val speedDialColumn = 64.dp

    /** Standard height for list rows in sheets/menus (52dp + 48dp min touch). */
    val menuRowHeight = 52.dp
    val gridActionCircle = 48.dp
}