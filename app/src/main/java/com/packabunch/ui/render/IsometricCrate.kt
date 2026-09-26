package com.packabunch.ui.render

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import com.packabunch.ui.theme.*
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
import androidx.compose.ui.platform.LocalContext
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
enum class PackingView(val label: String) { ISOMETRIC("3D"), TOP("Top"), FRONT("Front"), SIDE("Side") }

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
    showControls: Boolean = false,
    animateEntrance: Boolean = true,
    items: List<com.packabunch.packing.ItemSpec> = emptyList(),
) {
    val surfaces = remember(items) { items.associate { item ->
        val mask = item.visualShape ?: (item.shape as? com.packabunch.packing.ItemShape.VoxelMask)
        item.id to mask?.let { voxelSurface(it.countX, it.countY, it.countZ, it.resolutionMm, it::isOccupied) }
    } }
    // Everything not scanned is drawn as its geometry family when its name or measured form
    // says what it is — a sofa as a sofa, a bottle as a bottle — and as the plain box
    // otherwise. Display only: see FamilyMeshes.
    val context = LocalContext.current
    val families = remember(items) { items.filter { surfaces[it.id] == null }.mapNotNull { item ->
        FamilyMeshes.surfaceFor(context, item)?.let { item.id to it }
    }.toMap() }
    val scannedSurface = remember(space.scan) { space.scan?.effectiveGrid?.let { grid ->
        voxelSurface(grid.countX, grid.countY, grid.countZ, grid.resolutionMm) { x,y,z ->
            grid.cellAt(x,y,z) == com.packabunch.packing.Cell.SOLID
        }.map { face -> face.copy(points = face.points.map { p -> SurfacePoint(p.x+grid.originXMm, p.y+grid.originYMm, p.z+grid.originZMm) }) }
    } }
    var camera by rememberSaveable { mutableStateOf(PackingView.ISOMETRIC) }
    var seeThrough by rememberSaveable { mutableStateOf(true) }
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

    Column(modifier) {
    if (showControls) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            PackingView.entries.forEach { mode ->
                FilterChip(selected = camera == mode, onClick = { camera = mode; yaw = 0f }, label = { Text(mode.label, fontSize = 12.sp) })
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            FilterChip(selected = seeThrough, onClick = { seeThrough = !seeThrough }, label = { Text("See-through", fontSize = 12.sp) })
            Text(if (camera == PackingView.ISOMETRIC) "Drag to turn" else if (camera == PackingView.SIDE) "View from left" else "Nearest items in front", fontSize = 11.sp, color = TextSecondary, modifier = Modifier.padding(top = 14.dp))
        }
    }
    Canvas(
        modifier = Modifier.fillMaxWidth().weight(1f).then(
            if (!interactive || camera != PackingView.ISOMETRIC) Modifier else Modifier.pointerInput(camera) {
                detectHorizontalDragGestures { _, dragAmount ->
                    scope.launch { yaw += dragAmount * 0.006f }
                }
            },
        ),
    ) {
        val view = CrateView(space, size, yaw, camera)

        if (scannedSurface == null) drawCrateShell(view, front = false, seeThrough = seeThrough)
        else drawSurface(view, scannedSurface, CrateBackLeft, if (seeThrough) 0.15f else 1f)

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
                drawSurface(view, surface.map { face -> face.copy(points = face.points.map { it.placed(placement).let { p -> p.copy(z = p.z + (1f - itemProgress) * view.heightMm * 0.22f) } }) },
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
                mesh = families[placement.specId],
            )
        }

        if (scannedSurface == null) drawCrateShell(view, front = true, seeThrough = seeThrough)
    }
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
internal class CrateView(val space: Space, canvas: Size, val yaw: Float, val camera: PackingView) {
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
        when (camera) {
            PackingView.TOP -> return Offset(x, -y)
            PackingView.FRONT -> return Offset(x, -z)
            PackingView.SIDE -> return Offset(-y, -z)
            PackingView.ISOMETRIC -> Unit
        }
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
    fun depth(x: Float, y: Float, z: Float): Float = when (camera) {
        PackingView.TOP -> z
        PackingView.FRONT -> -y
        PackingView.SIDE -> -x
        PackingView.ISOMETRIC -> x * cos(yaw) - y * sin(yaw) + x * sin(yaw) + y * cos(yaw) + z
    }

    fun depthKey(box: Box) = depth((box.minXMm + box.maxXMm) / 2f,
        (box.minYMm + box.maxYMm) / 2f, (box.minZMm + box.maxZMm) / 2f)

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
/**
 * The container itself. Drawn in two passes: the far walls and floor before the contents,
 * the near walls after, so items sit inside the crate rather than floating over a picture
 * of one.
 */
private fun DrawScope.drawCrateShell(view: CrateView, front: Boolean, seeThrough: Boolean) {
    val faces = cuboidFaces(0f, view.widthMm, 0f, view.depthMm, 0f, view.heightMm).filter { it.side != 5 }
    val centreDepth = view.depth(view.widthMm / 2f, view.depthMm / 2f, view.heightMm / 2f)
    faces.filter { face -> (face.points.map { view.depth(it.x, it.y, it.z) }.average() > centreDepth) == front }
        .sortedBy { face -> face.points.sumOf { view.depth(it.x,it.y,it.z).toDouble() } }
        .forEach { face ->
            val p = face.points.map { view.project(it.x,it.y,it.z) }
            // See-through off means solid: the near walls hide what is behind them, as the real
            // box would. (They used to stay at 22 %, which read as see-through either way.)
            quad(p[0],p[1],p[2],p[3], CrateBackLeft.copy(alpha = if (seeThrough) 0.08f else 1f))
            quadOutline(p[0],p[1],p[2],p[3], CrateEdge.copy(alpha = 0.65f), 1.4f)
        }
}

private fun cuboidFaces(x0: Float, x1: Float, y0: Float, y1: Float, z0: Float, z1: Float): List<SurfaceFace> =
    voxelSurface(1, 1, 1, 1) { _,_,_ -> true }.map { face -> face.copy(points = face.points.map { p ->
        SurfacePoint(x0 + p.x * (x1-x0), y0 + p.y * (y1-y0), z0 + p.z * (z1-z0))
    }) }

/**
 * One item plus its number, shaded so the form reads without an outline.
 *
 * [mesh] is the item's geometry family in its own axes, when it has one; it is turned into
 * the plan by the same [placed] transform as a scanned surface, so a bottle the solver laid on
 * its side is drawn lying down, and it fills exactly the box the solver reserved. Without one
 * the item is the plain box.
 */
private fun DrawScope.drawPlacement(
    view: CrateView,
    placement: Placement,
    base: Color,
    progress: Float,
    dimmed: Boolean,
    textMeasurer: TextMeasurer,
    itemNumber: Int,
    mesh: List<SurfaceFace>? = null,
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

    val faces = mesh?.map { face ->
        face.copy(
            points = face.points.map { it.placed(placement).let { q -> q.copy(z = q.z + dropMm) } },
            normal = face.normal?.turned(placement),
        )
    } ?: cuboidFaces(x0,x1,y0,y1,z0,z1)
    drawSurface(view, faces, base, alpha)

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

/**
 * Painter ordering with pale app-colour fills and dimension-drawing outlines.
 *
 * Each face is lightened by how it faces: 0.62 for a lid, 0.34 for a face across the width,
 * 0.12 for one across the depth or underneath, blended for anything in between —
 * `0.12 + 0.5·max(nz, 0) + 0.22·nx²`. For axis faces that gives exactly the three values the
 * plan has always used, so scanned items and plain boxes look as they did; the geometry
 * families' angled faces (a bottle's shoulder, a sofa's arm) fall between them.
 *
 * Only faces that carry a normal (the family meshes) are culled when they face away: they are
 * closed shells, so their back faces are hidden anyway, and dropping them keeps a dimmed or
 * fading item from showing its far side through its near one. Voxel faces are left exactly as
 * they were drawn before.
 */
private fun DrawScope.drawSurface(view: CrateView, faces: List<SurfaceFace>, base: Color, alpha: Float) {
    fun depth(face: SurfaceFace): Float = face.points.sumOf { view.depth(it.x,it.y,it.z).toDouble() }.toFloat()
    val o = view.depth(0f, 0f, 0f)
    val towardViewer = floatArrayOf(view.depth(1f, 0f, 0f) - o, view.depth(0f, 1f, 0f) - o, view.depth(0f, 0f, 1f) - o)
    faces.filter { face ->
        val n = face.normal ?: return@filter true
        n.x * towardViewer[0] + n.y * towardViewer[1] + n.z * towardViewer[2] > -1e-3f
    }.sortedBy { depth(it) }.forEach { face ->
        val p = face.points.map { view.project(it.x,it.y,it.z) }
        val n = face.normal ?: sideNormal(face.side)
        val fill = base.lighten(0.12f + 0.5f * n.z.coerceAtLeast(0f) + 0.22f * n.x * n.x).copy(alpha=alpha)
        quad(p[0],p[1],p[2],p[3],fill)
        quadOutline(p[0],p[1],p[2],p[3],base.darken(0.28f).copy(alpha=alpha*0.4f),0.7f)
    }
}

/** The outward normal of a [voxelSurface] face: axis `side / 2`, positive when `side` is odd. */
private fun sideNormal(side: Int): SurfacePoint {
    val sign = if (side % 2 == 1) 1f else -1f
    return when (side / 2) { 0 -> SurfacePoint(sign, 0f, 0f); 1 -> SurfacePoint(0f, sign, 0f); else -> SurfacePoint(0f, 0f, sign) }
}
