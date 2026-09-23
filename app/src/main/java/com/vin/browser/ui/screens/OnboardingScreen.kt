package com.vin.browser.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vin.browser.data.SearchProviders
import com.vin.browser.ui.components.FaviconImage
import com.vin.browser.ui.theme.Radius
import com.vin.browser.ui.theme.Sizes
import com.vin.browser.ui.theme.Space
import com.vin.browser.ui.theme.VinTheme
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(
    selectedEngineId: String,
    onSelectEngine: (String) -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val brand = VinTheme.colors
    val pagerState = rememberPagerState(pageCount = { 2 })
    val coroutineScope = rememberCoroutineScope()

    Surface(
        color = scheme.background,
        modifier = modifier
            .fillMaxSize()
            // Tap-blocker: onboarding composes OVER the interactive Home screen --
            // without this, taps in blank areas beside the engine grid fall
            // through to the omnibox / speed dials underneath
            .pointerInput(Unit) { detectTapGestures { } }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(Space.lg)
        ) {
            // Top row: morphing page dots + Skip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.sm)
                ) {
                    repeat(2) { index ->
                        // targetPage (not currentPage) so the dot reacts the moment
                        // the swipe crosses the halfway point, not after settle
                        val active = pagerState.targetPage == index
                        val dotWidth by animateDpAsState(
                            targetValue = if (active) 20.dp else 8.dp,
                            animationSpec = tween(200), label = "dot_width"
                        )
                        val dotColor by animateColorAsState(
                            targetValue = if (active) scheme.primary else scheme.surfaceContainerHighest,
                            animationSpec = tween(200), label = "dot_color"
                        )
                        Box(
                            modifier = Modifier
                                .size(width = dotWidth, height = 8.dp)
                                .clip(Radius.pill)
                                .background(dotColor)
                        )
                    }
                }
                TextButton(onClick = onFinish) {
                    Text("Skip", color = scheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(Space.md))

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) { page ->
                if (page == 0) {
                    // Screen 1: Choose Search Engine
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Choose Your Search Engine",
                            style = MaterialTheme.typography.headlineSmall,
                            color = scheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(Space.xs))
                        Text(
                            "Select your primary search engine. You can change this anytime.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(Space.lg))
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            horizontalArrangement = Arrangement.spacedBy(Space.sm),
                            verticalArrangement = Arrangement.spacedBy(Space.sm),
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            items(SearchProviders.engines) { engine ->
                                EngineOptionCard(
                                    iconDomain = engine.directUrl,
                                    fallbackLetter = if (engine.id == "all") "ALL" else engine.shortName.take(1),
                                    name = engine.name,
                                    isSelected = engine.id == selectedEngineId,
                                    onClick = { onSelectEngine(engine.id) }
                                )
                            }
                        }
                    }
                } else {
                    // Screen 2: ViN Shield Intro
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(Space.md),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // Hero emblem -- Sizes.heroEmblem (the app's emblem scale);
                        // icon keeps the original ~53% proportion
                        Box(
                            modifier = Modifier
                                .size(Sizes.heroEmblem)
                                .clip(CircleShape)
                                .background(brand.secure.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.Shield,
                                contentDescription = "ViN Shield",
                                tint = brand.secure,
                                modifier = Modifier.size(Sizes.heroIcon)
                            )
                        }
                        Spacer(Modifier.height(Space.lg))
                        Text(
                            "ViN Shield Protection",
                            style = MaterialTheme.typography.headlineMedium,
                            color = scheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(Space.sm))
                        Text(
                            "Block intrusive ads, malicious trackers, cookie popups, and crypto-miners out of the box with zero setup.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = scheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = Space.lg)
                        )
                    }
                }
            }

            // CTA -- pill button family, 48dp
            Button(
                onClick = {
                    if (pagerState.currentPage == 0) {
                        coroutineScope.launch { pagerState.animateScrollToPage(1) }
                    } else {
                        onFinish()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = scheme.primary),
                shape = Radius.pill,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Sizes.minTouchTarget)
            ) {
                Text(text = if (pagerState.currentPage == 0) "Continue" else "Get Started")
            }
        }
    }
}

@Composable
private fun EngineOptionCard(
    iconDomain: String,
    fallbackLetter: String,
    name: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(Radius.md),
        color = if (isSelected) scheme.surfaceContainerHighest else scheme.surfaceContainerLow,
        border = BorderStroke(
            if (isSelected) 2.dp else 1.dp, // 2/1 border = the app's selection idiom
            if (isSelected) scheme.primary else scheme.outlineVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.minTouchTarget)
            .clip(RoundedCornerShape(Radius.md))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Space.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FaviconImage(
                domain = iconDomain,
                size = Sizes.faviconSm,
                fallbackLetter = fallbackLetter
            )
            Spacer(Modifier.width(Space.sm))
            Text(
                name,
                style = MaterialTheme.typography.bodyMedium, // weight swap removed --
                color = scheme.onSurface,                     // selection carried by
                modifier = Modifier.weight(1f)                // border + container + check
            )
            if (isSelected) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = "Selected",
                    tint = scheme.primary,
                    modifier = Modifier.size(Sizes.iconSm)
                )
            }
        }
    }
}