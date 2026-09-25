package com.packabunch.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
 * The transient feedback strips — "Your pack, its items and the two pieces already in stay saved",
 * "Deleted \"Camping bin\"".
 *
 * From "Feedback / Saved pack · Motion" and "Feedback / Deleted pack · Motion". It rises 8dp,
 * fades up and settles from 98%; it dwells; then it fades out while drifting 4dp back down, so the
 * exit reads as the strip receding rather than a light being switched off.
 *
 * The fade and the rise are separate animations on purpose: the fade is a plain 300ms tween and
 * the rise is a spring, and they finish at different moments. That offset is most of why it feels
 * settled rather than mechanical.
 *
 * The component owns its own lifecycle: it plays in, dwells, plays out, then calls [onDismiss].
 * Keep it composed until that fires, or the exit never gets to run.
 *
 *     var toast by remember { mutableStateOf<String?>(null) }
 *     toast?.let { PackToast(it, onDismiss = { toast = null }) }
 */

enum class PackToastStyle { Reassurance, Removal }

data class PackToastAction(val label: String, val onClick: () -> Unit)

// Figma: fade 0.12→0.38, rise 0.12→0.52, scale 0.12→0.55, dwell to 3.40, exit 3.40→3.75.
private const val EnterFadeMillis = 300
private const val ExitFadeMillis = 350
private const val CheckDelayMillis = 160L
private const val CheckDrawMillis = 320
private val Rise = 8.dp
private val ExitDrift = 4.dp
private const val EnterScale = 0.98f
private val Standard = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
private val SettleSpring = spring<Float>(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)

private val ReassuranceFill = Color(0xFFF0E5D9)
private val ReassuranceText = Color(0xFF7C4223)
private val RemovalFill = Color(0xFF3F2718)
private val RemovalText = Color(0xFFFFFFFF)
private val ActionFill = Color(0xFFE08A46)
private val ActionText = Color(0xFF2B1D14)

@Composable
fun PackToast(
    message: String,
    modifier: Modifier = Modifier,
    style: PackToastStyle = PackToastStyle.Reassurance,
    dwellMillis: Long = 3_000L,
    action: PackToastAction? = null,
    onDismiss: () -> Unit = {},
) {
    val fade = remember { Animatable(0f) }      // opacity in
    val settle = remember { Animatable(0f) }    // rise + scale, sprung
    val exit = remember { Animatable(0f) }      // 0 = present, 1 = gone

    LaunchedEffect(Unit) {
        launch { settle.animateTo(1f, SettleSpring) }
        fade.animateTo(1f, tween(EnterFadeMillis, easing = Standard))
        delay(dwellMillis)
        exit.animateTo(1f, tween(ExitFadeMillis, easing = Standard))
        onDismiss()
    }

    val density = LocalDensity.current
    val riseP = with(density) { Rise.toPx() }
    val driftP = with(density) { ExitDrift.toPx() }

    val fill = if (style == PackToastStyle.Reassurance) ReassuranceFill else RemovalFill
    val ink = if (style == PackToastStyle.Reassurance) ReassuranceText else RemovalText

    Row(
        modifier = modifier
            .graphicsLayer {
                val s = settle.value
                val x = exit.value
                alpha = (fade.value * (1f - x)).coerceIn(0f, 1f)
                translationY = (1f - s) * riseP + x * driftP
                val k = EnterScale + (1f - EnterScale) * s
                scaleX = k
                scaleY = k
            }
            .background(fill, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (style == PackToastStyle.Reassurance) {
            // The tick draws itself on rather than appearing whole — Figma PATH_TRIM_END 0.28→0.60.
            val trim = remember { Animatable(0f) }
            LaunchedEffect(Unit) {
                delay(CheckDelayMillis)
                trim.animateTo(1f, tween(CheckDrawMillis, easing = Standard))
            }
            SavedCheck(progress = trim.value, color = ReassuranceText, modifier = Modifier.size(13.dp))
        }

        Text(
            text = message,
            color = ink,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            modifier = Modifier.weight(1f, fill = false),
        )

        if (action != null) {
            val press = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .pressScale(press)
                    .clickable(interactionSource = press, indication = null, onClick = action.onClick)
                    .background(ActionFill, RoundedCornerShape(50))
                    .padding(horizontal = 13.dp, vertical = 7.dp),
            ) {
                Text(action.label, color = ActionText, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

/** The confirmation tick, drawn to [progress] along its own length. */
@Composable
private fun SavedCheck(progress: Float, color: Color, modifier: Modifier = Modifier) {
    val measure = remember { PathMeasure() }
    val source = remember { Path() }
    val drawn = remember { Path() }

    Box(
        modifier.drawBehind {
            source.reset()
            source.moveTo(size.width * 0.06f, size.height * 0.52f)
            source.lineTo(size.width * 0.38f, size.height * 0.84f)
            source.lineTo(size.width * 0.94f, size.height * 0.16f)

            measure.setPath(source, false)
            drawn.reset()
            measure.getSegment(0f, measure.length * progress.coerceIn(0f, 1f), drawn, true)

            drawPath(
                drawn,
                color,
                style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        },
    )
}
