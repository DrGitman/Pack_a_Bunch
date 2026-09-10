package com.packabunch.ui.render

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.packabunch.packing.Box
import com.packabunch.packing.Placement
import com.packabunch.packing.Space
import com.packabunch.ui.motion.Motion
import com.packabunch.ui.theme.UiFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.launch

/**
 * The packed crate, drawn from the actual solver output.
 *
 * This is a renderer, not an illustration. Every face on screen comes from a [Placement]
 * in millimetres, so what you are looking at is the arrangement the engine validated — if
 * a box looked wrong here it would mean the plan was wrong, which is the point.
 *
 * Coordinates: the engine works in X right, Y back, Z up with the floor at z = 0. This
 * projects that to an isometric view and applies a yaw the user can drag. That conversion
 * lives here and nowhere else.
 */

/** Rotation applied about the vertical axis before projecting, in radians. */
private const val ISO_TILT = 0.5236f // 30°, the classic isometric angle

@Composable
fun IsometricCrate(
    space: Space,
    placements: List<Placement>,
    modifier: Modifier = Modifier,
    selectedInstanceId: String? = null,
    /** Placements after this index are drawn as ghosts — used by the packing guide. */
    revealedThrough: Int = Int.MAX_VALUE,
    itemColorFor: (Placement) -> Color,
    interactive: Boolean = true,
    animateEntrance: Boolean = true,
    items: List<com.packabunch.packing.ItemSpec> = emptyList(),
) {
    val surfaces = remember(items) { items.associate { item ->
        val mask = item.visualShape ?: (item.shape as? com.packabunch.packing.ItemShape.VoxelMask)
        item.id to mask?.let { voxelSurface(it.countX, it.countY, it.countZ, it.resolutionMm, it::isOccupied) }
    } }
    val scannedSurface = remember(space.scan) { space.scan?.effectiveGrid?.let { grid ->
        voxelSurface(grid.countX, grid.countY, grid.countZ, grid.resolutionMm) { x,y,z ->
            grid.cellAt(x,y,z) == com.packabunch.packing.Cell.SOLID
        }.map { face -> face.copy(points = face.points.map { p -> SurfacePoint(p.x+grid.originXMm, p.y+grid.originYMm, p.z+grid.originZMm) }) }
    } }
    var yaw by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    val settle = remember { Animatable(if (animateEntrance) 0f else 1f) }
    val textMeasurer = rememberTextMeasurer()

    // Items drop into place bottom-up, in packing order, so the entrance shows the sequence
    // rather than just being decoration.
    LaunchedEffect(placements, animateEntrance) {
        if (!animateEntrance) return@LaunchedEffect
        settle.snapTo(0f)
        settle.animateTo(1f, tween(durationMillis = Motion.MEDIUM_MS * 2, easing = Motion.Enter))
    }

    Canvas(
        modifier = modifier.then(
            if (!interactive) Modifier else Modifier.pointerInput(Unit) {
                detectHorizontalDragGestures { _, dragAmount ->
                    scope.launch { yaw += dragAmount * 0.006f }
                }
            },
        ),
    ) {
        val view = CrateView(space, size, yaw)

        if (scannedSurface == null) drawCrateShell(view, front = false)
        else drawSurface(view, scannedSurface, CrateBackLeft, 0.65f)

        val ordered = placements
            .sortedBy { it.sequenceIndex }
            .filterIndexed { index, _ -> index <= revealedThrough }
            .sortedBy { view.depthKey(it.box) }

        ordered.forEach { placement ->
            val order = placement.sequenceIndex
            val itemProgress = staggered(settle.value, order, placements.size)
            if (itemProgress <= 0f) return@forEach

            val surface = surfaces[placement.specId]
            if (surface != null) {
                drawSurface(view, surface.map { face -> face.copy(points = face.points.map { it.placed(placement) }) },
                    itemColorFor(placement), itemProgress * if (selectedInstanceId != null && placement.instanceId != selectedInstanceId) 0.34f else 1f)
                val index = items.indexOfFirst { it.id == placement.specId } + 1
                val point = view.project(placement.xMm + placement.orientedWidthMm / 2f,
                    placement.yMm + placement.orientedDepthMm / 2f, placement.zMm + placement.orientedHeightMm.toFloat())
                drawText(textMeasurer = textMeasurer, text = "$index", topLeft = point, style = TextStyle(color = CrateRim, fontSize = 13.sp))
            } else drawPlacement(
                view = view,
                placement = placement,
                base = itemColorFor(placement),
                progress = itemProgress,
                dimmed = selectedInstanceId != null && placement.instanceId != selectedInstanceId,
                textMeasurer = textMeasurer,
                itemNumber = items.indexOfFirst { it.id == placement.specId }.takeIf { it >= 0 }?.plus(1) ?: placement.sequenceIndex + 1,
            )
        }

        if (scannedSurface == null) drawCrateShell(view, front = true)
    }
}

/** Each item waits its turn, so the animation reads as the packing order. */
private fun staggered(overall: Float, index: Int, count: Int): Float {
    if (count <= 0) return overall
    val slice = 1f / count
    val start = index * slice * 0.6f // overlap, or a 20-item plan would take far too long
    return ((overall - start) / (1f - start).coerceAtLeast(0.001f)).coerceIn(0f, 1f)
}

/**
 * The projection. Holds the scale and offset that map millimetres onto this canvas, so
 * every face is drawn through the same transform.
 */
private class CrateView(val space: Space, canvas: Size, val yaw: Float) {
    private val w = space.dimensions.widthMm.toFloat()
    private val d = space.dimensions.depthMm.toFloat()
    private val h = space.dimensions.heightMm.toFloat()

    private val cx = w / 2f
    private val cy = d / 2f

    private val scale: Float
    private val originX: Float
    private val originY: Float

    init {
        // Project the eight corners of the space and fit the result to the canvas, so a
        // long shallow drawer and a tall crate both fill the frame sensibly.
        val corners = listOf(
            Triple(0f, 0f, 0f), Triple(w, 0f, 0f), Triple(0f, d, 0f), Triple(w, d, 0f),
            Triple(0f, 0f, h), Triple(w, 0f, h), Triple(0f, d, h), Triple(w, d, h),
        ).map { (x, y, z) -> rawProject(x, y, z) }

        val minX = corners.minOf { it.x }
        val maxX = corners.maxOf { it.x }
        val minY = corners.minOf { it.y }
        val maxY = corners.maxOf { it.y }

        val padding = 0.88f
        scale = minOf(
            canvas.width * padding / (maxX - minX).coerceAtLeast(1f),
            canvas.height * padding / (maxY - minY).coerceAtLeast(1f),
        )
        originX = canvas.width / 2f - (minX + maxX) / 2f * scale
        originY = canvas.height / 2f - (minY + maxY) / 2f * scale
    }

    private fun rawProject(x: Float, y: Float, z: Float): Offset {
        val rx = (x - cx) * cos(yaw) - (y - cy) * sin(yaw)
        val ry = (x - cx) * sin(yaw) + (y - cy) * cos(yaw)
        return Offset(
            x = (rx - ry) * cos(ISO_TILT),
            y = (rx + ry) * sin(ISO_TILT) - z,
        )
    }

    fun project(xMm: Float, yMm: Float, zMm: Float): Offset {
        val raw = rawProject(xMm, yMm, zMm)
        return Offset(originX + raw.x * scale, originY + raw.y * scale)
    }

    /** Painter's algorithm: further from the camera is drawn first. */
    fun depthKey(box: Box): Float {
        val x = (box.minXMm + box.maxXMm) / 2f - cx
        val y = (box.minYMm + box.maxYMm) / 2f - cy
        val rx = x * cos(yaw) - y * sin(yaw)
        val ry = x * sin(yaw) + y * cos(yaw)
        return -(rx + ry) - box.minZMm / 1000f
    }

    val floorZ: Float get() = 0f
    val widthMm: Float get() = w
    val depthMm: Float get() = d
    val heightMm: Float get() = h
}

private fun DrawScope.quad(a: Offset, b: Offset, c: Offset, d: Offset, color: Color) {
    val path = Path().apply {
        moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c.x, c.y); lineTo(d.x, d.y); close()
    }
    drawPath(path, color)
}

private fun DrawScope.quadOutline(a: Offset, b: Offset, c: Offset, d: Offset, color: Color, width: Float) {
    val path = Path().apply {
        moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c.x, c.y); lineTo(d.x, d.y); close()
    }
    drawPath(path, color, style = Stroke(width = width))
}

// Crate shell, in the palette the artboards use for it.
// One brown family, light to dark, in the manner of a dimensioned reference drawing.
private val CrateFloor = Color(0xFFEADCCC)
private val CrateBackLeft = Color(0xFFF2E7DA)
private val CrateBackRight = Color(0xFFF7EFE6)
private val CrateFrontLeft = Color(0xFFD9BE9E)
private val CrateFrontRight = Color(0xFFC9A986)
private val CrateRim = Color(0xFF7C4223)
private val CrateEdge = Color(0xFF9A6A45)

/**
 * The container itself. Drawn in two passes: the far walls and floor before the contents,
 * the near walls after, so items sit inside the crate rather than floating over a picture
 * of one.
 */
private fun DrawScope.drawCrateShell(view: CrateView, front: Boolean) {
    val w = view.widthMm
    val d = view.depthMm
    val h = view.heightMm

    fun p(x: Float, y: Float, z: Float) = view.project(x, y, z)

    if (!front) {
        // Floor.
        quad(p(0f, 0f, 0f), p(w, 0f, 0f), p(w, d, 0f), p(0f, d, 0f), CrateFloor)
        quadOutline(p(0f, 0f, 0f), p(w, 0f, 0f), p(w, d, 0f), p(0f, d, 0f), CrateEdge, 1.4f)

        // The two walls furthest from the camera, chosen by which way the crate is turned.
        quad(p(0f, d, 0f), p(w, d, 0f), p(w, d, h), p(0f, d, h), CrateBackRight)
        quad(p(w, 0f, 0f), p(w, d, 0f), p(w, d, h), p(w, 0f, h), CrateBackLeft)
        quadOutline(p(0f, d, 0f), p(w, d, 0f), p(w, d, h), p(0f, d, h), CrateEdge, 1.4f)
        quadOutline(p(w, 0f, 0f), p(w, d, 0f), p(w, d, h), p(w, 0f, h), CrateEdge, 1.4f)
    } else {
        // Near walls, drawn at partial opacity so the load stays visible through them.
        quad(
            p(0f, 0f, 0f), p(w, 0f, 0f), p(w, 0f, h), p(0f, 0f, h),
            CrateFrontLeft.copy(alpha = 0.22f),
        )
        quad(
            p(0f, 0f, 0f), p(0f, d, 0f), p(0f, d, h), p(0f, 0f, h),
            CrateFrontRight.copy(alpha = 0.22f),
        )
        quadOutline(
            p(0f, 0f, 0f), p(w, 0f, 0f), p(w, 0f, h), p(0f, 0f, h),
            CrateEdge.copy(alpha = 0.55f), 1.4f,
        )
        quadOutline(
            p(0f, 0f, 0f), p(0f, d, 0f), p(0f, d, h), p(0f, 0f, h),
            CrateEdge.copy(alpha = 0.55f), 1.4f,
        )

        // The rim, at full strength — it is what makes the opening readable.
        quadOutline(
            p(0f, 0f, h), p(w, 0f, h), p(w, d, h), p(0f, d, h),
            CrateRim, 2.6f,
        )
        listOf(
            Triple(0f, 0f, 0f), Triple(w, 0f, 0f), Triple(0f, d, 0f), Triple(w, d, 0f),
        ).forEach { (x, y, _) ->
            drawLine(CrateRim, p(x, y, 0f), p(x, y, h), strokeWidth = 1.8f)
        }
    }
}

/** One item: three visible faces plus its number, shaded so the form reads without an outline. */
private fun DrawScope.drawPlacement(
    view: CrateView,
    placement: Placement,
    base: Color,
    progress: Float,
    dimmed: Boolean,
    textMeasurer: TextMeasurer,
    itemNumber: Int,
) {
    val box = placement.box

    // Items fall the last little way into position, which is what makes the sequence legible.
    val dropMm = (1f - progress) * view.heightMm * 0.22f
    val alpha = (if (dimmed) 0.34f else 1f) * progress

    val x0 = box.minXMm.toFloat()
    val x1 = box.maxXMm.toFloat()
    val y0 = box.minYMm.toFloat()
    val y1 = box.maxYMm.toFloat()
    val z0 = box.minZMm.toFloat() + dropMm
    val z1 = box.maxZMm.toFloat() + dropMm

    fun p(x: Float, y: Float, z: Float) = view.project(x, y, z)

    // Technical-drawing style: pale flat fills with a strong outline in the same hue, the
    // way a dimensioned reference drawing reads. The three faces stay tonally separated so
    // the form is legible, but the *outline* is what carries the shape — which is why this
    // survives being shrunk to a project-card thumbnail where shading alone would mush.
    val ink = base.darken(0.28f).copy(alpha = alpha)
    val top = base.lighten(0.62f).copy(alpha = alpha)
    val left = base.lighten(0.34f).copy(alpha = alpha)
    val right = base.lighten(0.12f).copy(alpha = alpha)

    val topFace = listOf(p(x0, y0, z1), p(x1, y0, z1), p(x1, y1, z1), p(x0, y1, z1))
    val leftFace = listOf(p(x0, y0, z0), p(x1, y0, z0), p(x1, y0, z1), p(x0, y0, z1))
    val rightFace = listOf(p(x0, y0, z0), p(x0, y1, z0), p(x0, y1, z1), p(x0, y0, z1))

    quad(topFace[0], topFace[1], topFace[2], topFace[3], top)
    quad(leftFace[0], leftFace[1], leftFace[2], leftFace[3], left)
    quad(rightFace[0], rightFace[1], rightFace[2], rightFace[3], right)

    val stroke = 2.2f
    quadOutline(topFace[0], topFace[1], topFace[2], topFace[3], ink, stroke)
    quadOutline(leftFace[0], leftFace[1], leftFace[2], leftFace[3], ink, stroke)
    quadOutline(rightFace[0], rightFace[1], rightFace[2], rightFace[3], ink, stroke)

    if (dimmed || progress < 0.85f) return

    // Number badge on the top face. Colour is never the only signal — the number goes with it.
    val centre = p((x0 + x1) / 2f, (y0 + y1) / 2f, z1)
    val label = itemNumber.toString()
    val measured = textMeasurer.measure(
        text = label,
        style = TextStyle(
            fontFamily = UiFamily,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 13.sp,
            color = base.darken(0.1f),
        ),
    )
    val radius = 12.dp.toPx()
    if (radius * 2 > minOf(abs(p(x1, y0, z1).x - p(x0, y0, z1).x), 40f) * 1.6f) return

    drawCircle(Color.White.copy(alpha = alpha), radius, centre)
    drawCircle(
        color = base.darken(0.28f).copy(alpha = alpha),
        radius = radius,
        center = centre,
        style = Stroke(width = 1.8f),
    )
    drawText(
        textLayoutResult = measured,
        topLeft = Offset(
            centre.x - measured.size.width / 2f,
            centre.y - measured.size.height / 2f,
        ),
    )
}

private fun Color.lighten(amount: Float) = Color(
    red = red + (1f - red) * amount,
    green = green + (1f - green) * amount,
    blue = blue + (1f - blue) * amount,
    alpha = alpha,
)

private fun Color.darken(amount: Float) = Color(
    red = red * (1f - amount),
    green = green * (1f - amount),
    blue = blue * (1f - amount),
    alpha = alpha,
)

/** Painter ordering with pale app-colour fills and dimension-drawing outlines. */
private fun DrawScope.drawSurface(view: CrateView, faces: List<SurfaceFace>, base: Color, alpha: Float) {
    fun depth(face: SurfaceFace): Float = face.points.sumOf { p ->
        (p.x * cos(view.yaw) - p.y * sin(view.yaw) + p.x * sin(view.yaw) + p.y * cos(view.yaw) + p.z).toDouble()
    }.toFloat()
    faces.sortedBy { depth(it) }.forEach { face ->
        val p = face.points.map { view.project(it.x,it.y,it.z) }
        val fill = base.lighten(if (face.side == 5) 0.62f else if (face.side < 2) 0.34f else 0.12f).copy(alpha=alpha)
        quad(p[0],p[1],p[2],p[3],fill)
        quadOutline(p[0],p[1],p[2],p[3],base.darken(0.28f).copy(alpha=alpha*0.4f),0.7f)
    }
}
