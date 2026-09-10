package com.packabunch.ui.render

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.packabunch.packing.Placement
import com.packabunch.packing.Space
import com.packabunch.ui.theme.UiFamily

/** One horizontal band of the pack: everything whose base sits at the same height. */
data class PackLayer(
    val index: Int,
    val baseMm: Int,
    val topMm: Int,
    val placements: List<Placement>,
)

/**
 * Splits a plan into layers by base height.
 *
 * Items resting on the floor are layer one, items resting on those are layer two, and so
 * on. Grouping by base height rather than by slicing at fixed intervals is what makes a
 * layer correspond to something you actually do — one pass of putting things in.
 */
fun layersOf(placements: List<Placement>): List<PackLayer> = placements
    .groupBy { it.zMm }
    .toSortedMap()
    .entries
    .mapIndexed { index, (base, items) ->
        PackLayer(
            index = index,
            baseMm = base,
            topMm = items.maxOf { it.box.maxZMm },
            placements = items.sortedBy { it.sequenceIndex },
        )
    }

/**
 * One layer, seen from directly above — `design/artboards/PlanLayers.dc.html`.
 *
 * Deliberately flat and unshaded: this is a plan view, and the moment it gets perspective
 * it stops being something you can compare against the real thing by standing over it.
 *
 * Free space is outlined rather than filled, and it is the *arithmetic* remainder of the
 * layer's footprint. It is not a promise that anything else would fit there — leftover room
 * in a layer is usually several disconnected slivers.
 */
@Composable
fun LayerView(
    space: Space,
    layer: PackLayer,
    modifier: Modifier = Modifier,
    colorFor: (Placement) -> Color,
    selectedInstanceId: String? = null,
    items: List<com.packabunch.packing.ItemSpec> = emptyList(),
) {
    val measurer = rememberTextMeasurer()
    val surfaces = androidx.compose.runtime.remember(items) { items.associate { item ->
        val mask = item.visualShape ?: (item.shape as? com.packabunch.packing.ItemShape.VoxelMask)
        item.id to mask?.let { voxelSurface(it.countX,it.countY,it.countZ,it.resolutionMm,it::isOccupied) }
    } }

    Canvas(modifier) {
        val bounds = space.volume().boundsMm
        val spaceWidth = bounds.widthMm.toFloat().coerceAtLeast(1f)
        val spaceDepth = bounds.depthMm.toFloat().coerceAtLeast(1f)

        // Fit the footprint, keeping the aspect ratio, with room for the edge labels.
        val padding = 28.dp.toPx()
        val available = Size(size.width - padding * 2, size.height - padding * 2)
        val scale = minOf(available.width / spaceWidth, available.height / spaceDepth)
        val drawnWidth = spaceWidth * scale
        val drawnDepth = spaceDepth * scale
        val originX = (size.width - drawnWidth) / 2f
        val originY = (size.height - drawnDepth) / 2f

        fun x(mm: Int) = originX + (mm - bounds.minXMm) * scale
        fun y(mm: Int) = originY + (mm - bounds.minYMm) * scale

        // The container footprint.
        drawRect(
            color = Color(0xFFF0E5D9),
            topLeft = Offset(originX, originY),
            size = Size(drawnWidth, drawnDepth),
        )
        drawRect(
            color = Color(0xFF8E4E28),
            topLeft = Offset(originX, originY),
            size = Size(drawnWidth, drawnDepth),
            style = Stroke(width = 2.4f),
        )

        // Free space in this layer, marked but not filled.
        drawRect(
            color = Color(0xFFB09A85),
            topLeft = Offset(originX, originY),
            size = Size(drawnWidth, drawnDepth),
            style = Stroke(
                width = 1.4f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)),
            ),
        )

        layer.placements.forEach { placement ->
            val left = x(placement.box.minXMm)
            val top = y(placement.box.minYMm)
            val w = placement.box.widthMm * scale
            val d = placement.box.depthMm * scale
            val base = colorFor(placement)
            val dimmed = selectedInstanceId != null && placement.instanceId != selectedInstanceId

            val surface = surfaces[placement.specId]
            if (surface != null) surface.forEach { face ->
                val points = face.points.map { it.placed(placement) }
                val path = androidx.compose.ui.graphics.Path().apply {
                    points.forEachIndexed { i,p ->
                        val px = originX + (p.x - bounds.minXMm) * scale
                        val py = originY + (p.y - bounds.minYMm) * scale
                        if (i == 0) moveTo(px,py) else lineTo(px,py)
                    }
                    close()
                }
                drawPath(path,base.copy(alpha=if(dimmed)0.30f else 0.92f))
            } else {
            drawRect(
                color = base.copy(alpha = if (dimmed) 0.30f else 0.92f),
                topLeft = Offset(left, top),
                size = Size(w, d),
            )
            drawRect(
                color = Color.White.copy(alpha = if (dimmed) 0.3f else 0.85f),
                topLeft = Offset(left, top),
                size = Size(w, d),
                style = Stroke(width = 2f),
            )
            }

            if (dimmed) return@forEach

            // The step number, centred, but only when the rectangle can hold it legibly.
            val label = (items.indexOfFirst { it.id == placement.specId }.takeIf { it >= 0 }?.plus(1) ?: placement.sequenceIndex + 1).toString()
            val laid = measurer.measure(
                text = label,
                style = TextStyle(
                    fontFamily = UiFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp,
                    color = Color.White,
                ),
            )
            if (laid.size.width < w * 0.7f && laid.size.height < d * 0.7f) {
                drawText(
                    textLayoutResult = laid,
                    topLeft = Offset(
                        left + (w - laid.size.width) / 2f,
                        top + (d - laid.size.height) / 2f,
                    ),
                )
            }
        }

        // Edges named the way the packing guide names them, so the two agree.
        val edgeStyle = TextStyle(
            fontFamily = UiFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            color = Color(0xFFA2907F),
        )
        fun label(text: String, at: Offset) {
            val laid = measurer.measure(text, edgeStyle)
            drawText(laid, topLeft = Offset(at.x - laid.size.width / 2f, at.y - laid.size.height / 2f))
        }
        label("BACK", Offset(originX + drawnWidth / 2f, originY - 14.dp.toPx()))
        label("FRONT", Offset(originX + drawnWidth / 2f, originY + drawnDepth + 14.dp.toPx()))
        label("LEFT", Offset(originX - 16.dp.toPx(), originY + drawnDepth / 2f))
        label("RIGHT", Offset(originX + drawnWidth + 16.dp.toPx(), originY + drawnDepth / 2f))
    }
}
