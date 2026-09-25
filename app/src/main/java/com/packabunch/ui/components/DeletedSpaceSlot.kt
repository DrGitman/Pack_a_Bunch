package com.packabunch.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
 * The gap a deleted space leaves behind — a dashed outline reading "Camping bin was here".
 *
 * From "Feedback / Deleted space · Motion", which is the best-staged of the hand-built frames and
 * needed no correction: the dashed outline arrives first and settles from 98%, and the label
 * follows about 140ms later, rising 3dp. That small lag is what stops it reading as one flat card
 * fading in — the frame lands, then the writing appears inside it.
 */

private val DashColor = Color(0xFFD9C8B4)
private val LabelInk = Color(0xFF7C6857)

// Figma: outline 0.00→0.50 (scale sprung), label 0.14→0.54.
private const val OutlineFadeMillis = 420
private const val LabelDelayMillis = 140L
private const val LabelFadeMillis = 360
private const val OutlineScale = 0.98f
private val LabelRise = 3.dp
private val Standard = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
private val SettleSpring = spring<Float>(dampingRatio = 0.84f, stiffness = Spring.StiffnessMediumLow)

@Composable
fun DeletedSpaceSlot(
    label: String,
    modifier: Modifier = Modifier,
) {
    val outlineFade = remember { Animatable(0f) }
    val outlineSettle = remember { Animatable(0f) }
    val labelFade = remember { Animatable(0f) }
    val labelRise = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        launch { outlineSettle.animateTo(1f, SettleSpring) }
        launch {
            delay(LabelDelayMillis)
            launch { labelRise.animateTo(1f, SettleSpring) }
            labelFade.animateTo(1f, tween(LabelFadeMillis, easing = Standard))
        }
        outlineFade.animateTo(1f, tween(OutlineFadeMillis, easing = Standard))
    }

    val riseP = with(LocalDensity.current) { LabelRise.toPx() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(74.dp)
            .graphicsLayer {
                alpha = outlineFade.value
                val s = OutlineScale + (1f - OutlineScale) * outlineSettle.value
                scaleX = s
                scaleY = s
            }
            .drawBehind {
                val w = 1.4.dp.toPx()
                drawRoundRect(
                    DashColor,
                    topLeft = Offset(w / 2, w / 2),
                    size = Size(size.width - w, size.height - w),
                    cornerRadius = CornerRadius(14.dp.toPx()),
                    style = Stroke(
                        width = w,
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(5.dp.toPx(), 4.dp.toPx()),
                            0f,
                        ),
                    ),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = LabelInk,
            fontSize = 12.sp,
            modifier = Modifier.graphicsLayer {
                alpha = labelFade.value
                translationY = (1f - labelRise.value) * riseP
            },
        )
    }
}
