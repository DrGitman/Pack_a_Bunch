package com.packabunch.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/*
 * The press squash every tappable surface in the app shares.
 *
 * Down is a short ease-out; the way back up is a spring, so the finger-lift is the part that
 * carries the life. Getting those two the wrong way round is what made the Figma originals feel
 * dead — a spring compressed into 90 ms has no room to oscillate, and a plain ease on the recovery
 * is the half you actually watch.
 *
 * Matches "Button / Undo · Motion" and the press group on every "Icon … · Motion" frame.
 *
 *     val press = remember { MutableInteractionSource() }
 *     Box(Modifier.pressScale(press).clickable(press, null) { … })
 */

private val PressDownEasing = CubicBezierEasing(0f, 0f, 0.58f, 1f)   // Figma EASE_OUT
private const val PressDownMillis = 100                             // Figma 0.00 → 0.10
private val PressUpSpring = spring<Float>(dampingRatio = 0.62f, stiffness = Spring.StiffnessMedium)

@Composable
fun Modifier.pressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.94f,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale = remember { Animatable(1f) }

    LaunchedEffect(pressed) {
        if (pressed) {
            scale.animateTo(pressedScale, tween(PressDownMillis, easing = PressDownEasing))
        } else {
            // Only spring back if we actually went down — otherwise the first composition
            // would fire a pointless settle.
            if (scale.value != 1f) scale.animateTo(1f, PressUpSpring)
        }
    }

    return this.graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
}
