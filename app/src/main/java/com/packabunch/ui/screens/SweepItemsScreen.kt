package com.packabunch.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.packabunch.packing.MeasuredDimensions
import com.packabunch.packing.SweptObject
import com.packabunch.ui.components.ItemNumberTile
import com.packabunch.ui.components.Note
import com.packabunch.ui.components.NoteTone
import com.packabunch.ui.components.PackIconButton
import com.packabunch.ui.components.PackIcons
import com.packabunch.ui.components.PackTextButton
import com.packabunch.ui.components.PrimaryButton
import com.packabunch.ui.format.LengthUnit
import com.packabunch.ui.format.formatDimensions
import com.packabunch.ui.motion.Motion
import com.packabunch.ui.motion.entrance
import com.packabunch.ui.motion.rememberStaggeredEntrance
import com.packabunch.ui.theme.NumeralChip
import com.packabunch.ui.theme.NumeralLarge
import com.packabunch.ui.theme.Success
import com.packabunch.ui.theme.SuccessTint
import com.packabunch.ui.theme.TextPrimary
import com.packabunch.ui.theme.TextSecondary
import com.packabunch.ui.theme.TextTertiary
import com.packabunch.ui.theme.UiFamily
import com.packabunch.ui.theme.itemColor

/**
 * Sweeping a pile of things — the primary way items get into a pack.
 *
 * There is no shutter button, and that is the whole design. You walk round what you have
 * put out and the list beside the camera fills up as objects resolve, each turning green on
 * its own schedule. A shoe box in the open finishes in seconds; the thing behind the sofa
 * says "walk round this one" until you do.
 *
 * That per-row honesty is the point. A single spinner over the whole scan would tell you
 * nothing about *which* object needs another angle, and you would end up sweeping the same
 * corner repeatedly with no idea whether it was helping.
 */
@Composable
fun SweepItemsScreen(
    objects: List<SweptObject>,
    unit: LengthUnit,
    isTracking: Boolean,
    fallbackFor: (SweptObject) -> com.packabunch.packing.SuggestedDimensions?,
    onDone: (List<SweptObject>) -> Unit,
    onAddManually: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    cameraPreview: @Composable () -> Unit = {},
    /** What the detector can see right now, in 0..1 image space. */
    found: List<com.packabunch.ar.FoundObject> = emptyList(),
) {
    val settled = objects.count { it.settled }
    // Collapsed by default: while sweeping, seeing the thing you are pointing at matters more
    // than reading the list. Tap the handle to check the measurements.
    var expanded by remember { mutableStateOf(false) }

    Box(modifier.fillMaxSize().background(Color.Black)) {
        cameraPreview()

        ScanOverlay(found, Modifier.fillMaxSize())

        Column(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PackIconButton(
                    icon = PackIcons.Back,
                    contentDescription = "Back",
                    onClick = onBack,
                    tint = Color.White,
                    background = Color(0x55000000),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "$settled of ${objects.size} measured",
                    style = NumeralLarge.copy(fontSize = 15.sp),
                    color = Color.White,
                    modifier = Modifier
                        .background(Color(0x66000000), RoundedCornerShape(999.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.White, RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(bottom = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(width = 44.dp, height = 4.dp)
                        .background(Color(0xFFE2D5C6), RoundedCornerShape(999.dp)),
                )
            }

            Text(
                text = when {
                    !isTracking -> "Move slowly over the floor or table to find the surface"
                    objects.isEmpty() -> "Point at your things and walk round them"
                    settled == objects.size -> "Visible items ready to review"
                    expanded -> "Keep moving, the ones still waiting say what they need"
                    else -> "$settled measured, ${objects.size - settled} still going"
                },
                color = TextPrimary,
                fontFamily = UiFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
            )

            if (objects.isEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Leave a gap between things. Two boxes touching read as one.",
                    color = TextSecondary,
                    fontFamily = UiFamily,
                    fontSize = 13.5f.sp,
                    lineHeight = 20.sp,
                )
            }

            Spacer(Modifier.height(12.dp))

            if (expanded) {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 220.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(objects, key = { it.id }) { swept ->
                        val index = objects.indexOf(swept)
                        val enter = rememberStaggeredEntrance(index)
                        SweptRow(
                            index = index,
                            swept = swept,
                            unit = unit,
                            fallback = fallbackFor(swept),
                            modifier = Modifier.entrance(enter),
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            PrimaryButton(
                text = if (settled == 0) "Nothing measured yet" else "Use these $settled",
                onClick = { onDone(objects.filter { it.settled }) },
                enabled = settled > 0,
            )

            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                PackTextButton(text = "Add one by hand instead", onClick = onAddManually)
            }
        }
    }
}

@Composable
private fun SweptRow(
    index: Int,
    swept: SweptObject,
    unit: LengthUnit,
    fallback: com.packabunch.packing.SuggestedDimensions?,
    modifier: Modifier = Modifier,
) {
    val background by animateColorAsState(
        targetValue = if (swept.settled) SuccessTint else Color(0xFFF4EDE4),
        animationSpec = Motion.standardTween(),
        label = "sweptRow",
    )
    val measured = swept.measured(fallback)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(background, RoundedCornerShape(18.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        ItemNumberTile(
            number = index + 1,
            color = itemColor(index),
            size = 32.dp,
            cornerRadius = 11.dp,
            fontSize = 13,
        )

        Column(Modifier.weight(1f)) {
            Text(
                text = formatDimensions(swept.detected.dimensions, unit),
                style = NumeralChip.copy(fontSize = 13.sp),
                color = TextPrimary,
            )

            // Per row, not per scan: what this particular object is still waiting for.
            Text(
                text = swept.waitingFor,
                color = if (swept.settled) Success else TextTertiary,
                fontFamily = UiFamily,
                fontWeight = if (swept.settled) FontWeight.SemiBold else FontWeight.Normal,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 2.dp),
            )

            // An object measured on two axes with the third filled in says so here rather
            // than presenting all three as equally solid.
            if (measured != null && measured.summary == MeasuredDimensions.Provenance.PARTLY_MEASURED) {
                Text(
                    text = "${measured.estimatedAxes().joinToString()} filled in, never in view",
                    color = Color(0xFF8A6A2E),
                    fontFamily = UiFamily,
                    fontSize = 11.5f.sp,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }

        if (swept.settled) {
            Icon(
                PackIcons.Check,
                contentDescription = "Measured",
                tint = Success,
                modifier = Modifier.size(19.dp),
            )
        }
    }
}

/**
 * The brackets drawn around each object the detector can see.
 *
 * These are flat rectangles in image space, not projected cubes. That is a deliberate step
 * back: the cubes were geometrically honest about a cluster of depth noise, which meant they
 * were confidently wrong — boxes over bare floor, one mug split into fragments. A bracket
 * around something a trained detector has actually identified is worth more than a cube
 * around something nothing can vouch for.
 *
 * The glow is three strokes at falling alpha rather than an emissive shader. At these line
 * weights over a camera feed the result is the same bloom for a few draw calls instead of a
 * whole renderer.
 */
@Composable
private fun ScanOverlay(
    found: List<com.packabunch.ar.FoundObject>,
    modifier: Modifier = Modifier,
) {
    val pulse = androidx.compose.animation.core.rememberInfiniteTransition(label = "scan")
    val phase by pulse.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(1_600, easing = androidx.compose.animation.core.LinearEasing),
        ),
        label = "phase",
    )

    androidx.compose.foundation.Canvas(modifier) {
        found.forEach { item ->
            val l = item.left * size.width
            val t = item.top * size.height
            val r = item.right * size.width
            val b = item.bottom * size.height
            if (r <= l || b <= t) return@forEach

            val colour = Color(0xFFFFB259)
            // A quarter of the shorter side, so brackets stay proportionate on any box.
            val arm = kotlin.math.min(r - l, b - t) * 0.24f

            fun glow(ax: Float, ay: Float, bx: Float, by: Float) {
                val a = androidx.compose.ui.geometry.Offset(ax, ay)
                val c = androidx.compose.ui.geometry.Offset(bx, by)
                drawLine(colour.copy(alpha = 0.10f), a, c, 14f, androidx.compose.ui.graphics.StrokeCap.Round)
                drawLine(colour.copy(alpha = 0.30f), a, c, 7f, androidx.compose.ui.graphics.StrokeCap.Round)
                drawLine(colour, a, c, 3f, androidx.compose.ui.graphics.StrokeCap.Round)
            }

            // Corner brackets only. A full rectangle over a busy surface reads as clutter.
            glow(l, t, l + arm, t); glow(l, t, l, t + arm)
            glow(r, t, r - arm, t); glow(r, t, r, t + arm)
            glow(l, b, l + arm, b); glow(l, b, l, b - arm)
            glow(r, b, r - arm, b); glow(r, b, r, b - arm)

            // A light travelling down the box: the app is working on this one.
            val y = t + (b - t) * phase
            val fade = kotlin.math.sin(phase * Math.PI).toFloat()
            drawLine(
                colour.copy(alpha = 0.45f * fade),
                androidx.compose.ui.geometry.Offset(l, y),
                androidx.compose.ui.geometry.Offset(r, y),
                2.5f,
            )
        }
    }
}
