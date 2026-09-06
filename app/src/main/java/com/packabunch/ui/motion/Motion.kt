package com.packabunch.ui.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The app's motion vocabulary, in one place.
 *
 * The design tokens allow 150–250 ms and nothing longer, which rules out anything showy.
 * What is left is motion that does a job: telling you a control registered your finger,
 * showing where a sheet came from, and letting a number settle so you notice it changed.
 *
 * One rule that is not negotiable: nothing animates while the camera is scanning, and
 * haptics fire on a captured point or a completed packing step, never continuously.
 */
object Motion {

    /** Token: `durationShortMs`. Presses, toggles, chip selection. */
    const val SHORT_MS = 150

    /** Token: `durationMediumMs`. Screen changes, sheets, expanding cards. */
    const val MEDIUM_MS = 250

    /** Entrance stagger between items in a list. Deliberately small — this is a utility. */
    const val STAGGER_MS = 34

    /**
     * Decelerate. Things arriving on screen: sheets, screens, entering cards. Fast at the
     * start so it feels immediate, long tail so it lands softly.
     */
    val Enter: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)

    /** Accelerate. Things leaving: dismissed sheets, outgoing screens. */
    val Exit: Easing = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)

    /** Symmetrical. Things changing in place: a value updating, a chip filling. */
    val Standard: Easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)

    /** Press feedback. A spring, not a curve — it has to feel like a physical give. */
    fun <T> pressSpring(): FiniteAnimationSpec<T> = spring(
        dampingRatio = 0.62f,
        stiffness = Spring.StiffnessMediumLow,
    )

    fun <T> enterTween(durationMs: Int = MEDIUM_MS): FiniteAnimationSpec<T> =
        tween(durationMillis = durationMs, easing = Enter)

    fun <T> exitTween(durationMs: Int = SHORT_MS): FiniteAnimationSpec<T> =
        tween(durationMillis = durationMs, easing = Exit)

    fun <T> standardTween(durationMs: Int = MEDIUM_MS): FiniteAnimationSpec<T> =
        tween(durationMillis = durationMs, easing = Standard)
}

/**
 * Scales a control down slightly while it is held.
 *
 * Applied to the big terracotta buttons, cards and chips. The amount is tuned per surface:
 * a 56 dp button can take 0.97, a small chip needs less or it looks like it jumped.
 */
fun Modifier.pressScale(
    pressedScale: Float = 0.97f,
    enabled: Boolean = true,
): Modifier = composed {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) pressedScale else 1f,
        animationSpec = Motion.pressSpring(),
        label = "pressScale",
    )

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .pointerInput(enabled) {
            if (!enabled) return@pointerInput
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                pressed = true
                waitForUpOrCancellation()
                pressed = false
            }
        }
}

/**
 * Entrance for a list: each row fades up from a few dp below, one after another.
 *
 * [index] is the row's position, so the stagger reads top to bottom. Capped at eight rows
 * of delay — past that the last row would arrive late enough to feel broken rather than
 * choreographed.
 */
@Composable
fun rememberStaggeredEntrance(
    index: Int,
    enabled: Boolean = true,
    riseFrom: Dp = 10.dp,
): EntranceState {
    val progress = remember { Animatable(if (enabled) 0f else 1f) }

    LaunchedEffect(index, enabled) {
        if (!enabled) return@LaunchedEffect
        val delayMs = (index.coerceAtMost(8) * Motion.STAGGER_MS).toLong()
        kotlinx.coroutines.delay(delayMs)
        progress.animateTo(1f, Motion.enterTween(Motion.MEDIUM_MS))
    }

    return EntranceState(progress.value, riseFrom)
}

data class EntranceState(val progress: Float, val riseFrom: Dp)

fun Modifier.entrance(state: EntranceState): Modifier = composed {
    graphicsLayer {
        alpha = state.progress
        translationY = state.riseFrom.toPx() * (1f - state.progress)
    }
}
