package com.packabunch.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.packabunch.ui.motion.Motion
import com.packabunch.ui.motion.pressScale
import com.packabunch.ui.theme.NumericFamily
import com.packabunch.ui.theme.OnCamera
import com.packabunch.ui.theme.OnCameraInk
import com.packabunch.ui.theme.Primary
import com.packabunch.ui.theme.ScanGlass
import com.packabunch.ui.theme.Success
import com.packabunch.ui.theme.TextPrimary
import com.packabunch.ui.theme.TextTertiary
import com.packabunch.ui.theme.UiFamily
import kotlin.math.roundToInt

/*
 * The controls that sit on top of a live camera: the item scan, the multi-item scan and the
 * space scan all use these, so the three read as one tool.
 *
 * ### Why everything is scaled by 4/3
 *
 * The scan screens were drawn in Figma on a 309-wide frame (`ItemScan · New`, `MultiScan ·
 * New`, `SpaceScan · New`), which is the app's 412 dp phone at three quarters. Every value is
 * lifted from those frames as drawn and converted here, once, rather than being rounded to a
 * grid at each call site — the rule in CLAUDE.md about artboard values applies to these frames
 * too. `42` in Figma is the 56 dp primary button; `11` is a 14.7 sp label.
 *
 * ### What is deliberately absent
 *
 * No vignette, no dimming scrim, no shadow panel under the controls. Each control carries its
 * own small glass background, so the picture of the thing being measured is never darkened.
 */

/** Figma px on the scan frames → dp. See the note at the top of this file. */
val Float.fd: Dp get() = (this * FIGMA_SCALE).dp
val Int.fd: Dp get() = (this * FIGMA_SCALE).dp

/** Figma px on the scan frames → sp. */
val Float.fs: TextUnit get() = (this * FIGMA_SCALE).sp
val Int.fs: TextUnit get() = (this * FIGMA_SCALE).sp

private const val FIGMA_SCALE = 4f / 3f

/** A pill of camera glass: the one background every scan control shares. */
@Composable
fun GlassPill(
    modifier: Modifier = Modifier,
    horizontal: Dp = 11.fd,
    vertical: Dp = 7.fd,
    gap: Dp = 7.fd,
    glass: Color = ScanGlass,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = modifier
            .background(glass, RoundedCornerShape(999.dp))
            // Width follows the words smoothly when they change, instead of the pill snapping.
            .animateContentSize(animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow))
            .padding(horizontal = horizontal, vertical = vertical),
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

/*
 * Motion on the scan screens.
 *
 * Every movement here reports something: a count went up, an object finished, the advice
 * changed, a control took the tap. Nothing loops for decoration over the camera — the one
 * repeating thing is the dot beside the advice, which breathes only while the scan is still
 * working and stops the moment there is nothing left to do.
 */

/** Text that rolls to its new value — up when it grows, like a counter ticking. */
@Composable
fun RollingText(
    text: String,
    color: Color,
    fontFamily: androidx.compose.ui.text.font.FontFamily,
    fontWeight: FontWeight,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    maxLines: Int = 1,
) {
    AnimatedContent(
        targetState = text,
        modifier = modifier,
        transitionSpec = {
            val up = initialState.firstNumber() <= targetState.firstNumber()
            (slideInVertically(tween(Motion.SHORT_MS + 60, easing = Motion.Enter)) { h -> if (up) h / 2 else -h / 2 } +
                fadeIn(tween(Motion.SHORT_MS))) togetherWith
                (slideOutVertically(tween(Motion.SHORT_MS, easing = Motion.Exit)) { h -> if (up) -h / 2 else h / 2 } +
                    fadeOut(tween(Motion.SHORT_MS - 60))) using SizeTransform(clip = false)
        },
        label = "rollingText",
    ) { value ->
        Text(
            value, color = color, fontFamily = fontFamily, fontWeight = fontWeight, fontSize = fontSize,
            letterSpacing = letterSpacing, maxLines = maxLines, overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun String.firstNumber(): Int = Regex("\\d+").find(this)?.value?.toIntOrNull() ?: 0

/** Appears with the app's pop: rises a touch, fades up, lands on a light spring. */
@Composable
fun Modifier.scanPopIn(delayMillis: Int = 0): Modifier {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (delayMillis > 0) kotlinx.coroutines.delay(delayMillis.toLong())
        progress.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow))
    }
    return graphicsLayer {
        val p = progress.value
        alpha = p.coerceIn(0f, 1f)
        val s = 0.82f + 0.18f * p
        scaleX = s; scaleY = s
        translationY = (1f - p) * 6.dp.toPx()
    }
}

/** "1 of 2 measured", "Car boot 72% mapped" — the counter in the top right. */
@Composable
fun ScanStatusPill(lead: String?, value: String, modifier: Modifier = Modifier) {
    GlassPill(modifier, gap = 6.fd) {
        if (lead != null) {
            Text(lead, color = Color.White, fontFamily = UiFamily, fontWeight = FontWeight.SemiBold, fontSize = 11.fs)
        }
        RollingText(
            value,
            color = if (lead != null) Color.White.copy(alpha = 0.8f) else Color.White,
            fontFamily = NumericFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 11.fs,
        )
    }
}

/**
 * One instruction at a time, with a dot that says whether the scan is still working. The dot
 * breathes while working and settles green when done; new advice slides in over the old.
 */
@Composable
fun ScanGuidancePill(text: String, working: Boolean, modifier: Modifier = Modifier) {
    val dotColor by animateColorAsState(if (working) OnCamera else Success, tween(Motion.MEDIUM_MS), label = "dot")
    val breathe = rememberInfiniteTransition(label = "breathe")
    val pulse by breathe.animateFloat(
        initialValue = 1f, targetValue = 0.35f,
        animationSpec = infiniteRepeatable(tween(900, easing = Motion.Standard), RepeatMode.Reverse),
        label = "pulse",
    )
    GlassPill(modifier, horizontal = 13.fd, vertical = 8.fd) {
        Box(Modifier.size(7.fd).graphicsLayer { alpha = if (working) pulse else 1f }.background(dotColor, CircleShape))
        AnimatedContent(
            targetState = text,
            transitionSpec = {
                (fadeIn(tween(Motion.SHORT_MS + 80, delayMillis = 60)) + slideInVertically(tween(Motion.MEDIUM_MS, easing = Motion.Enter)) { it / 3 }) togetherWith
                    fadeOut(tween(Motion.SHORT_MS - 60)) using SizeTransform(clip = false)
            },
            label = "guidance",
        ) { advice ->
            Text(advice, color = Color.White, fontFamily = UiFamily, fontWeight = FontWeight.Medium, fontSize = 11.fs, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** The 34 / 42 px round glass buttons with a plain icon. */
@Composable
fun GlassCircleButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    diameter: Dp = 42.fd,
    iconSize: Dp = 19.fd,
    active: Boolean = false,
) {
    PackIconButton(
        icon = icon,
        contentDescription = contentDescription,
        onClick = onClick,
        modifier = modifier,
        tint = if (active) TextPrimary else Color.White,
        size = diameter,
        iconSize = iconSize,
        background = if (active) OnCamera else ScanGlass,
    )
}

/**
 * A round glass button whose icon is one of the app's Lottie icons ("Icon … · Motion"),
 * recoloured white for the camera. The whole circle takes the tap; the icon plays on touch-down.
 */
@Composable
fun GlassLottieButton(
    @androidx.annotation.RawRes animation: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    diameter: Dp = 42.fd,
    iconSize: Dp = 22.fd,
) {
    val interaction = remember { MutableInteractionSource() }
    val view = LocalView.current
    Box(
        modifier = modifier
            .size(diameter)
            .pressScale(pressedScale = 0.92f, interactionSource = interaction)
            .background(ScanGlass, CircleShape)
            .clip(CircleShape)
            .combinedClickable(
                interactionSource = interaction,
                indication = androidx.compose.material3.ripple(color = Color.White),
                role = Role.Button,
                onClickLabel = contentDescription,
                onClick = {
                    view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                    onClick()
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        LottieTapIcon(
            animation = animation,
            contentDescription = contentDescription,
            onClick = null,
            size = iconSize,
            tint = Color.White,
            interactionSource = interaction,
        )
    }
}

/**
 * The torch. Turning it on fills the circle amber, flips the bolt dark and gives it a small
 * spark — the one control on the screen whose state you need to read at a glance.
 */
@Composable
fun TorchButton(on: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val view = LocalView.current
    val fill by animateColorAsState(if (on) OnCamera else ScanGlass, tween(Motion.SHORT_MS + 40), label = "torchFill")
    val tint by animateColorAsState(if (on) TextPrimary else Color.White, tween(Motion.SHORT_MS + 40), label = "torchTint")
    val spark = remember { Animatable(0f) }
    LaunchedEffect(on) {
        if (on) { spark.snapTo(0f); spark.animateTo(1f, tween(420, easing = Motion.Enter)) } else spark.snapTo(0f)
    }
    Box(
        modifier = modifier
            .size(42.fd)
            .pressScale(pressedScale = 0.92f, interactionSource = interaction)
            .background(fill, CircleShape)
            .clip(CircleShape)
            .combinedClickable(
                interactionSource = interaction,
                indication = androidx.compose.material3.ripple(color = Color.White),
                role = Role.Switch,
                onClickLabel = if (on) "Turn torch off" else "Turn torch on",
                onClick = {
                    view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                    onToggle()
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        // A ring that flashes out from the bolt as it turns on.
        Canvas(Modifier.size(42.fd)) {
            val p = spark.value
            if (p > 0f && p < 1f) drawCircle(Color.White.copy(alpha = 0.55f * (1f - p)), radius = size.minDimension / 2 * (0.45f + 0.5f * p), style = Stroke(1.5.dp.toPx()))
        }
        val wobble = if (spark.value in 0.001f..0.999f) kotlin.math.sin(spark.value * Math.PI.toFloat() * 3f) * 10f * (1f - spark.value) else 0f
        Icon(
            PackIcons.Bolt, contentDescription = null, tint = tint,
            modifier = Modifier.size(19.fd).graphicsLayer { rotationZ = wobble },
        )
    }
}

/**
 * "✓ Done · 1 item" / "✓ Use 9 items" — the only filled control on a scan screen. It warms
 * from faded to full colour when there is something to hand over, the tick draws itself in,
 * and the count rolls as objects finish.
 */
@Composable
fun ScanDoneButton(text: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val view = LocalView.current
    val shape = RoundedCornerShape(999.dp)
    val fill by animateColorAsState(if (enabled) Primary else Primary.copy(alpha = 0.45f), tween(Motion.MEDIUM_MS), label = "doneFill")
    val ink by animateFloatAsState(if (enabled) 1f else 0.7f, tween(Motion.MEDIUM_MS), label = "doneInk")
    val tick = remember { Animatable(if (enabled) 1f else 0f) }
    LaunchedEffect(enabled) {
        if (enabled) { tick.snapTo(0f); tick.animateTo(1f, tween(360, delayMillis = 80, easing = Motion.Enter)) } else tick.snapTo(0f)
    }
    Row(
        modifier = modifier
            .height(42.fd)
            .pressScale(pressedScale = 0.95f, enabled = enabled, interactionSource = interaction)
            .background(fill, shape)
            .clip(shape)
            .combinedClickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = androidx.compose.material3.ripple(color = Color.White),
                role = Role.Button,
                onClick = {
                    view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                    onClick()
                },
            )
            .animateContentSize(spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow))
            .padding(horizontal = 20.fd),
        horizontalArrangement = Arrangement.spacedBy(8.fd),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(15.fd)) {
            val w = size.width; val h = size.height
            val path = androidx.compose.ui.graphics.Path().apply { moveTo(w * 0.12f, h * 0.52f); lineTo(w * 0.40f, h * 0.78f); lineTo(w * 0.90f, h * 0.24f) }
            drawTrimmed(path, if (enabled) tick.value else 1f, Color.White.copy(alpha = ink), 2.2f.fd.toPx())
        }
        RollingText(text, Color.White.copy(alpha = ink), UiFamily, FontWeight.Bold, 14.fs)
    }
}

/** Draws [fraction] of [path] from its start: a line drawing itself in. */
fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTrimmed(
    path: androidx.compose.ui.graphics.Path,
    fraction: Float,
    color: Color,
    width: Float,
) {
    if (fraction <= 0f) return
    val measure = androidx.compose.ui.graphics.PathMeasure().apply { setPath(path, false) }
    val out = androidx.compose.ui.graphics.Path()
    measure.getSegment(0f, measure.length * fraction.coerceIn(0f, 1f), out, true)
    drawPath(out, color, style = Stroke(width, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
}

/**
 * A measurement pill on the edge it measures: "W 20.4 cm". Height is amber so the one
 * vertical number stands apart from the two footprint numbers at a glance. Pills pop in one
 * after another ([appearDelayMillis]) the moment an object is measured, and the number rolls
 * if the measurement sharpens afterwards.
 */
@Composable
fun DimensionPill(
    axis: String,
    value: String,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
    appearDelayMillis: Int = 0,
) {
    Row(
        modifier = modifier
            .scanPopIn(appearDelayMillis)
            .background(if (highlight) OnCamera else Color.White, RoundedCornerShape(999.dp))
            .padding(horizontal = 8.fd, vertical = 4.fd),
        horizontalArrangement = Arrangement.spacedBy(5.fd),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(axis, color = if (highlight) OnCameraInk else TextTertiary, fontFamily = NumericFamily, fontWeight = FontWeight.Medium, fontSize = 9.5f.fs)
        RollingText(value, TextPrimary, NumericFamily, FontWeight.Medium, 11.fs)
    }
}

/** What an object's tag leads with. */
enum class ScanBadge { Measured, Working, NeedsYou }

/**
 * The tag that floats over an object: its state badge and a few words.
 * [compact] is the MultiScan size (21 px tall), used once there are several objects to label.
 *
 * It pops in when the object is first found. When the object finishes, the ring gives way to
 * a green disc that springs up and a tick that draws itself — the moment worth noticing.
 */
@Composable
fun ObjectTag(
    text: String,
    badge: ScanBadge,
    progress: Float,
    compact: Boolean,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val badgeSize = if (compact) 12.fd else 14.fd
    GlassPill(
        modifier = modifier
            .scanPopIn()
            .widthIn(max = 200.fd)
            .clip(RoundedCornerShape(999.dp))
            .then(
                if (onClick != null || onLongClick != null) Modifier.combinedClickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = { onClick?.invoke() },
                    onLongClick = onLongClick,
                ) else Modifier,
            ),
        horizontal = if (compact) 8.fd else 9.fd,
        vertical = if (compact) 4.fd else 5.fd,
        gap = if (compact) 5.fd else 6.fd,
    ) {
        Crossfade(targetState = badge, animationSpec = tween(Motion.SHORT_MS), label = "badge") { state ->
            when (state) {
                ScanBadge.Measured -> MeasuredBadge(Modifier.size(badgeSize))
                ScanBadge.Working -> {
                    val shown by animateFloatAsState(progress.coerceIn(0f, 1f), spring(dampingRatio = 1f, stiffness = Spring.StiffnessLow), label = "ring")
                    ProgressRing(shown, Modifier.size(if (compact) 12.fd else 13.fd))
                }
                ScanBadge.NeedsYou -> InfoBadge(Modifier.size(badgeSize).scanPopIn())
            }
        }
        RollingText(
            text, Color.White, UiFamily,
            if (badge == ScanBadge.Measured) FontWeight.SemiBold else FontWeight.Medium,
            if (compact) 10.fs else 11.fs,
        )
    }
}

/** Green disc that springs up, then a tick that draws itself in. */
@Composable
fun MeasuredBadge(modifier: Modifier = Modifier) {
    val disc = remember { Animatable(0f) }
    val tick = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        disc.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium))
    }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(90)
        tick.animateTo(1f, tween(260, easing = Motion.Enter))
    }
    Canvas(modifier) {
        val r = size.minDimension / 2 * disc.value
        drawCircle(Success, r)
        val w = size.width; val h = size.height
        val path = androidx.compose.ui.graphics.Path().apply { moveTo(w * 0.28f, h * 0.52f); lineTo(w * 0.44f, h * 0.67f); lineTo(w * 0.73f, h * 0.36f) }
        drawTrimmed(path, tick.value, Color.White, size.minDimension * 0.12f)
    }
}

/** Amber ring filling clockwise from twelve o'clock over a faint track. */
@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    track: Color = Color.White.copy(alpha = 0.25f),
    strokeFraction: Float = 0.2f,
) {
    Canvas(modifier) {
        val w = size.minDimension * strokeFraction
        val inset = w / 2
        val arcSize = Size(size.width - w, size.height - w)
        drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(w))
        drawArc(OnCamera, -90f, 360f * progress.coerceIn(0f, 1f), false, Offset(inset, inset), arcSize, style = Stroke(w, cap = StrokeCap.Round))
    }
}

/** The amber outlined "i": this one needs the person, not more sweeping. */
@Composable
fun InfoBadge(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val s = size.minDimension
        val w = s * 0.11f
        drawCircle(OnCamera, radius = s / 2 - w / 2, style = Stroke(w))
        drawCircle(OnCamera, radius = s * 0.085f, center = Offset(s / 2, s * 0.3f))
        drawLine(OnCamera, Offset(s / 2, s * 0.46f), Offset(s / 2, s * 0.74f), strokeWidth = w * 1.1f, cap = StrokeCap.Round)
    }
}

/** How a label sits relative to its anchor point. */
enum class AnchorAlign { Above, Centre, Below }

/** A label pinned to a point on the camera image, given in 0..1 of the view. */
class AnchoredLabel(
    val key: Any,
    val x: Float,
    val y: Float,
    val align: AnchorAlign,
    val content: @Composable () -> Unit,
)

/**
 * Places labels on the points the AR renderer projected for them. One layout for all of them,
 * so a frame's worth of moving tags is a single placement pass rather than a recomposition per
 * tag. Labels are kept on screen: an object half out of view still shows its tag at the edge.
 */
@Composable
fun AnchoredLabels(labels: List<AnchoredLabel>, modifier: Modifier = Modifier) {
    Layout(
        content = { labels.forEach { label -> key(label.key) { label.content() } } },
        modifier = modifier,
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val placeables = measurables.map { it.measure(loose) }
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeables.forEachIndexed { i, p ->
                val label = labels[i]
                val x = label.x * constraints.maxWidth - p.width / 2f
                val y = when (label.align) {
                    AnchorAlign.Above -> label.y * constraints.maxHeight - p.height
                    AnchorAlign.Centre -> label.y * constraints.maxHeight - p.height / 2f
                    AnchorAlign.Below -> label.y * constraints.maxHeight
                }
                p.place(
                    x.roundToInt().coerceIn(0, (constraints.maxWidth - p.width).coerceAtLeast(0)),
                    y.roundToInt().coerceIn(0, (constraints.maxHeight - p.height).coerceAtLeast(0)),
                )
            }
        }
    }
}
