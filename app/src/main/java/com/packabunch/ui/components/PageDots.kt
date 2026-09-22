package com.packabunch.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import kotlin.math.abs

/*
 * Onboarding page dots — the active dot is a 26×8 pill, the rest are 8×8 dots, 8 dp apart.
 * Moving to another page shrinks the old pill back to a dot while the new dot stretches into
 * the pill and the colours cross-fade. Same spec as "Page dots · Motion" in Figma.
 */

private val ActiveColor = Color(0xFFA65C34)
private val InactiveColor = Color(0xFFDFCEB8)
private val DotSize = 8.dp
private val PillWidth = 26.dp
private val Gap = 8.dp
private val Standard = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)   // Material "standard" easing
private const val MoveMillis = 350

/**
 * Use this when the onboarding screens are pages of a HorizontalPager.
 * The dots follow the finger while swiping, not just when a page settles.
 */
@Composable
fun PageDots(pagerState: PagerState, modifier: Modifier = Modifier) {
    val position = pagerState.currentPage + pagerState.currentPageOffsetFraction
    DotsRow(count = pagerState.pageCount, position = position, modifier = modifier)
}

/**
 * Use this when every onboarding step is its own screen (separate navigation destinations).
 * Pass the step the user came from, and the pill slides from there to [current] when the screen
 * appears. Leave [previous] null on the first screen.
 */
@Composable
fun PageDots(current: Int, count: Int, modifier: Modifier = Modifier, previous: Int? = null) {
    val position = remember { Animatable((previous ?: current).toFloat()) }
    LaunchedEffect(current) {
        position.animateTo(current.toFloat(), tween(MoveMillis, easing = Standard))
    }
    DotsRow(count = count, position = position.value, modifier = modifier)
}

@Composable
private fun DotsRow(count: Int, position: Float, modifier: Modifier) {
    val shown = position.coerceIn(0f, (count - 1).toFloat())
    Row(
        modifier = modifier.semantics { contentDescription = "Step ${shown.toInt() + 1} of $count" },
        horizontalArrangement = Arrangement.spacedBy(Gap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { i ->
            // 1 when this dot is the current page, 0 when it's a page or more away.
            // Kept linear so the two changing dots always add up to the same width and the row
            // doesn't wobble; the easing comes from the tween (or from the finger when swiping).
            val t = (1f - abs(shown - i)).coerceIn(0f, 1f)
            Box(
                Modifier
                    .height(DotSize)
                    .width(lerp(DotSize, PillWidth, t))
                    .clip(CircleShape)
                    .background(lerp(InactiveColor, ActiveColor, t)),
            )
        }
    }
}
