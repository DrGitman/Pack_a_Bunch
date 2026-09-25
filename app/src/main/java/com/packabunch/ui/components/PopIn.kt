package com.packabunch.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/*
 * The pack list settling in when the screen opens or refreshes.
 *
 * Each row rises a little, fades up and lands with a light spring, one after another. The delay
 * comes from the row's index, so the order reads as the list filling top to bottom rather than
 * everything arriving at once.
 *
 *     val refresh by vm.refreshToken.collectAsState()
 *     LazyColumn {
 *         itemsIndexed(rows, key = { _, r -> r.id }) { i, row ->
 *             PackCard(row, Modifier.popIn(i, key = refresh))
 *         }
 *     }
 *
 * Pass a token that changes on refresh as [key] and the whole list replays. Section headers take
 * an index too, so "TODAY" lands just before the cards under it.
 */

private const val StepMillis = 55L
private const val MaxStagger = 8        // stop stacking delay past this, so long lists stay brisk

@Composable
fun Modifier.popIn(
    index: Int,
    key: Any? = Unit,
    rise: Dp = 14.dp,
    stepMillis: Long = StepMillis,
): Modifier {
    val progress = remember(key, index) { Animatable(0f) }
    LaunchedEffect(key, index) {
        progress.snapTo(0f)
        delay(minOf(index, MaxStagger) * stepMillis)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
        )
    }
    val risePx = with(LocalDensity.current) { rise.toPx() }
    return this.graphicsLayer {
        val p = progress.value
        alpha = p.coerceIn(0f, 1f)
        translationY = (1f - p) * risePx
        val s = 0.965f + 0.035f * p          // the spring carries it a hair past 1, which reads as a pop
        scaleX = s
        scaleY = s
    }
}
