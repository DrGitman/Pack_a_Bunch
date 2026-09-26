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
    /** Screen-space boxes for what the camera can currently see, refreshed every frame. */
    overlays: List<com.packabunch.ar.ObjectOverlay> = emptyList(),
) {
    val settled = objects.count { it.settled }
    // Collapsed by default: while sweeping, seeing the thing you are pointing at matters more
    // than reading the list. Tap the handle to check the measurements.
    var expanded by remember { mutableStateOf(false) }

    Box(modifier.fillMaxSize().background(Color.Black)) {
        cameraPreview()

        ScanOverlay(overlays, Modifier.fillMaxSize())

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
 * The boxes drawn over the camera while sweeping.
 *
 * Without these the screen is a live camera feed with a counter on it, and nothing tells you
 * the app can see anything — which reads as broken even when the measurement is going fine.
 *
 * The glow is done by stroking each edge three times at falling alpha and rising width rather
 * than by an emissive shader. On a camera feed at these line weights the result is the same
 * bloom, and it costs a few draw calls instead of a renderer.
 *
 * Amber while measuring, the app's green once measured. Corners are picked out heavier than
 * the edges between them: over a busy bench a full even wireframe turns into visual soup,
 * while bracketed corners still read as a box at a glance.
 */
@Composable
private fun ScanOverlay(
    overlays: List<com.packabunch.ar.ObjectOverlay>,
    modifier: Modifier = Modifier,
) {
    // One slow sweep, shared by every box, so the screen reads as alive while it works.
    val pulse = androidx.compose.animation.core.rememberInfiniteTransition(label = "scan")
    val phase by pulse.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(1_800, easing = androidx.compose.animation.core.LinearEasing),
        ),
        label = "phase",
    )

    androidx.compose.foundation.Canvas(modifier) {
        overlays.forEach { overlay ->
            val p = overlay.corners.map {
                androidx.compose.ui.geometry.Offset(it.first * size.width, it.second * size.height)
            }
            if (p.size != 8) return@forEach

            val colour = if (overlay.settled) Color(0xFF6FE3A8) else Color(0xFFFFB259)

            // Three passes: a wide dim halo, a mid pass, then the bright core on top.
            fun glowLine(a: androidx.compose.ui.geometry.Offset, b: androidx.compose.ui.geometry.Offset, core: Float) {
                drawLine(colour.copy(alpha = 0.10f), a, b, core * 5f, androidx.compose.ui.graphics.StrokeCap.Round)
                drawLine(colour.copy(alpha = 0.28f), a, b, core * 2.4f, androidx.compose.ui.graphics.StrokeCap.Round)
                drawLine(colour, a, b, core, androidx.compose.ui.graphics.StrokeCap.Round)
            }

            // A measured box gets a faint lid, which is what makes it read as solid.
            if (overlay.settled) {
                val lid = androidx.compose.ui.graphics.Path().apply {
                    moveTo(p[4].x, p[4].y)
                    for (i in 5..7) lineTo(p[i].x, p[i].y)
                    close()
                }
                drawPath(lid, colour.copy(alpha = 0.14f))
            }

            // Corner brackets: a short stub along each of the three edges meeting a corner.
            for (i in 0 until 4) {
                val base = p[i]
                val top = p[i + 4]
                val nextBase = p[(i + 1) % 4]
                val prevBase = p[(i + 3) % 4]
                fun stub(from: androidx.compose.ui.geometry.Offset, to: androidx.compose.ui.geometry.Offset) =
                    glowLine(from, androidx.compose.ui.geometry.lerp(from, to, 0.28f), 3f)
                stub(base, nextBase)
                stub(base, prevBase)
                stub(base, top)
                stub(top, p[4 + (i + 1) % 4])
                stub(top, p[4 + (i + 3) % 4])
                stub(top, base)
            }

            // The faint full outline behind the brackets, so the shape is still readable.
            for (i in 0 until 4) {
                drawLine(colour.copy(alpha = 0.22f), p[i], p[(i + 1) % 4], 1.5f)
                drawLine(colour.copy(alpha = 0.32f), p[4 + i], p[4 + (i + 1) % 4], 1.5f)
                drawLine(colour.copy(alpha = 0.22f), p[i], p[i + 4], 1.5f)
            }

            // A light sweeping up an unfinished box: the app is still working on this one.
            if (!overlay.settled) {
                val t = phase
                val left = androidx.compose.ui.geometry.lerp(p[0], p[4], t)
                val right = androidx.compose.ui.geometry.lerp(p[1], p[5], t)
                val fade = kotlin.math.sin(t * Math.PI).toFloat()
                drawLine(colour.copy(alpha = 0.55f * fade), left, right, 2.5f)
                drawLine(colour.copy(alpha = 0.18f * fade), left, right, 9f)
            }
        }
    }
}
