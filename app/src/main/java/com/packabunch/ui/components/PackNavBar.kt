package com.packabunch.ui.components

import androidx.annotation.RawRes
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * The floating nav bar.
 *
 * One white pill exists for the whole bar and slides — and stretches — from the old destination to
 * the new one, while the page underneath cross-fades. Nothing about the page itself slides, so the
 * motion is all in the bar and switching tabs never feels like travel.
 *
 * Matches the bar drawn in ProjectsEmpty on the Screen Artboards page.
 */

private val BarColor = Color(0xFF3F2718)
private val PillColor = Color(0xFFFFFFFF)
private val FabColor = Color(0xFFE08A46)
private val SelectedContent = Color(0xFF2B1D14)
private val UnselectedContent = Color(0xFFE6D7BE)

private val BarHeight = 54.dp
private val PillHeight = 39.dp
private val BarPadding = 7.5.dp

private val PillSpring = spring<androidx.compose.ui.unit.Dp>(
    dampingRatio = 0.82f,
    stiffness = Spring.StiffnessMediumLow,
)
private val FadeEasing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
const val PageFadeMillis = 220

/** One destination in the bar. [animation] is the raw Lottie that plays when it is tapped. */
data class NavTab(
    val key: String,
    val label: String,
    @RawRes val animation: Int,
)

/** The two tabs, in bar order, so every screen shows the same bar in the same state. */
val PackDestinations = listOf(
    NavTab("projects", "Projects", com.packabunch.R.raw.icon_projects),
    NavTab("settings", "Settings", com.packabunch.R.raw.icon_settings),
)

@Composable
fun PackNavBar(
    destinations: List<NavTab>,
    selected: Int,
    onSelect: (Int) -> Unit,
    @RawRes fabAnimation: Int,
    onFabClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    // measured left edge and width of every destination, so the pill can travel to it
    val slots = remember { mutableStateMapOf<Int, Pair<Float, Float>>() }
    var barX by remember { mutableFloatStateOf(0f) }
    val slot = slots[selected]

    val pillX by animateDpAsState(
        targetValue = with(density) { (slot?.first ?: 0f).toDp() },
        animationSpec = PillSpring,
        label = "pill-x",
    )
    val pillW by animateDpAsState(
        targetValue = with(density) { (slot?.second ?: 0f).toDp() },
        animationSpec = PillSpring,
        label = "pill-w",
    )

    Box(
        modifier = modifier
            .height(BarHeight)
            .clip(RoundedCornerShape(BarHeight / 2))
            .background(BarColor)
            .onGloballyPositioned { barX = it.positionInRoot().x },
        contentAlignment = Alignment.CenterStart,
    ) {
        // the one pill, drawn behind everything
        if (slot != null) {
            Box(
                Modifier
                    .offset(x = pillX)
                    .width(pillW)
                    .height(PillHeight)
                    .clip(RoundedCornerShape(PillHeight / 2))
                    .background(PillColor),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = BarPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            destinations.forEachIndexed { i, dest ->
                NavItem(
                    dest = dest,
                    selected = i == selected,
                    onClick = { onSelect(i) },
                    modifier = Modifier.onGloballyPositioned { c ->
                        slots[i] = (c.positionInRoot().x - barX) to c.size.width.toFloat()
                    },
                )
                if (i == 0) {
                    Spacer(Modifier.width(4.dp))
                    LottieTapIcon(
                        animation = fabAnimation,
                        contentDescription = "New pack",
                        onClick = onFabClick,
                        size = 42.dp,
                        modifier = Modifier.clip(CircleShape).background(FabColor),
                    )
                    Spacer(Modifier.width(4.dp))
                }
            }
        }
    }
}

@Composable
private fun NavItem(
    dest: NavTab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(PillHeight)
            .widthIn(min = PillHeight)
            .padding(horizontal = if (selected) 14.dp else 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        LottieTapIcon(
            animation = dest.animation,
            contentDescription = dest.label,
            onClick = onClick,
            size = 20.dp,
        )
        if (selected) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = dest.label,
                color = SelectedContent,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}

/**
 * The page body. Put this above the bar — it cross-fades while the pill travels, so the two
 * movements read as one gesture.
 *
 *     Box {
 *         PackPages(selected) { page -> when (page) { 0 -> ProjectsScreen(); else -> SettingsScreen() } }
 *         PackNavBar(..., modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp))
 *     }
 */
@Composable
fun PackPages(
    selected: Int,
    modifier: Modifier = Modifier,
    content: @Composable (Int) -> Unit,
) {
    Crossfade(
        targetState = selected,
        animationSpec = tween(PageFadeMillis, easing = FadeEasing),
        modifier = modifier,
        label = "pack-pages",
    ) { page -> content(page) }
}
