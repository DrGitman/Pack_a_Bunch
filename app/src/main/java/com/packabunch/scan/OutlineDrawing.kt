package com.packabunch.scan

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.hypot

/**
 * The Figma "Outline states": one style per state, the same for every shape.
 *  - scanning: white 95 %, 1.7, dashed 6/4, soft glow 5;
 *  - another angle: amber #F2A03D, 1.7, dotted 2/4 on the unseen side only;
 *  - measured: white, 2.2 solid, glow 3 + 10, drawn on over 400 ms, base filled 16 % white.
 */
data class OutlineStyle(
    val argb: Long,
    val widthDp: Float,
    /** Dash on/off lengths in dp; 0 on means solid. */
    val dashOnDp: Float = 0f,
    val dashOffDp: Float = 0f,
    /** Soft glow radius in dp, 0 for none. */
    val glowDp: Float = 0f,
    /** A second, wider and fainter glow — the measured state's "3 + 10". */
    val wideGlowDp: Float = 0f,
    /** Round dots rather than dashes (the amber "another angle" edge). */
    val dotted: Boolean = false,
) {
    companion object {
        val SCANNING = OutlineStyle(0xF2FFFFFF, 1.7f, dashOnDp = 6f, dashOffDp = 4f, glowDp = 5f)
        val ANOTHER_ANGLE_SEEN = SCANNING
        val ANOTHER_ANGLE_UNSEEN = OutlineStyle(0xFFF2A03D, 1.7f, dashOnDp = 2f, dashOffDp = 4f, glowDp = 5f, dotted = true)
        val MEASURED = OutlineStyle(0xFFFFFFFF, 2.2f, glowDp = 3f, wideGlowDp = 10f)
        /** The space scan's opening: thin and dashed, like the scanning edge but quieter. */
        val OPENING = OutlineStyle(0xCCFFFFFF, 1.4f, dashOnDp = 5f, dashOffDp = 5f)
        const val MEASURED_FILL_ARGB = 0x29FFFFFFL   // 16 % white
        const val DRAW_ON_MS = 400L
    }
}

/** One line to draw over the camera, in view pixels. */
class OutlineStroke(
    val points: FloatArray,
    val style: OutlineStyle,
    /** Draw only this much of the line, for the draw-on; null for all of it. */
    val revealPx: Float? = null,
    val dashPhasePx: Float = 0f,
)

/** A filled area over the camera, in view pixels (the measured footprint). */
class OutlineFill(val points: FloatArray, val argb: Long)

class OutlineScene(val strokes: List<OutlineStroke> = emptyList(), val fills: List<OutlineFill> = emptyList())

/**
 * From the camera picture (sensor orientation, as the engine measures it) to the preview on
 * screen: turned upright, then scaled to fill the view and centre-cropped, as PreviewView does.
 */
class ViewMapping(
    private val rotation: Int, private val picW: Int, private val picH: Int,
    val viewW: Int, val viewH: Int,
) {
    private val uw = if (rotation % 180 == 0) picW else picH
    private val uh = if (rotation % 180 == 0) picH else picW
    private val scale = maxOf(viewW.toFloat() / uw, viewH.toFloat() / uh)
    private val offX = (uw * scale - viewW) / 2
    private val offY = (uh * scale - viewH) / 2

    fun toView(u: Double, v: Double): Pair<Float, Float> {
        val (x, y) = when (rotation) {
            90 -> (picH - v) to u
            180 -> (picW - u) to (picH - v)
            270 -> v to (picW - u)
            else -> u to v
        }
        return (x * scale - offX).toFloat() to (y * scale - offY).toFloat()
    }

    /** The reverse: a point on screen to the picture pixel under it. */
    fun toPicture(viewX: Float, viewY: Float): Pair<Double, Double> {
        val x = (viewX + offX) / scale.toDouble(); val y = (viewY + offY) / scale.toDouble()
        return when (rotation) {
            90 -> y to (picH - x)
            180 -> (picW - x) to (picH - y)
            270 -> (picW - y) to x
            else -> x to y
        }
    }
}

/** Draws [scene] over the camera preview. */
@Composable
fun OutlineLayer(scene: OutlineScene, density: Float, modifier: Modifier = Modifier.fillMaxSize()) {
    Canvas(modifier) {
        for (f in scene.fills) {
            val path = Path()
            for (i in 0 until f.points.size / 2) {
                if (i == 0) path.moveTo(f.points[0], f.points[1]) else path.lineTo(f.points[2 * i], f.points[2 * i + 1])
            }
            path.close()
            drawPath(path, Color(f.argb.toInt()))
        }
        for (s in scene.strokes) drawStroke(s, density)
    }
}

private fun DrawScope.drawStroke(s: OutlineStroke, density: Float) {
    val pts = s.revealPx?.let { truncate(s.points, it) } ?: s.points
    if (pts.size < 4) return
    val path = Path().apply {
        moveTo(pts[0], pts[1])
        for (i in 1 until pts.size / 2) lineTo(pts[2 * i], pts[2 * i + 1])
    }
    val st = s.style
    val color = Color(st.argb.toInt())
    val core = st.widthDp * density
    val effect = when {
        st.dotted -> PathEffect.dashPathEffect(floatArrayOf(0.01f, (st.dashOnDp + st.dashOffDp) * density), s.dashPhasePx)
        st.dashOnDp > 0f -> PathEffect.dashPathEffect(floatArrayOf(st.dashOnDp * density, st.dashOffDp * density), s.dashPhasePx)
        else -> null
    }
    val cap = if (st.dotted) StrokeCap.Round else StrokeCap.Butt
    val width = if (st.dotted) core * 1.6f else core
    // Glows first, widest and faintest underneath.
    if (st.wideGlowDp > 0f) drawPath(path, color.copy(alpha = color.alpha * 0.14f),
        style = Stroke(width + 2 * st.wideGlowDp * density, cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = effect))
    if (st.glowDp > 0f) drawPath(path, color.copy(alpha = color.alpha * 0.30f),
        style = Stroke(width + 2 * st.glowDp * density, cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = effect))
    drawPath(path, color, style = Stroke(width, cap = cap, join = StrokeJoin.Round, pathEffect = effect))
}

/** The first [length] pixels of a polyline. */
private fun truncate(p: FloatArray, length: Float): FloatArray {
    val out = ArrayList<Float>(); out += p[0]; out += p[1]
    var left = length
    for (i in 0 until p.size / 2 - 1) {
        val ax = p[2 * i]; val ay = p[2 * i + 1]; val bx = p[2 * i + 2]; val by = p[2 * i + 3]
        val d = hypot(bx - ax, by - ay)
        if (d >= left) { val f = if (d == 0f) 0f else left / d; out += ax + (bx - ax) * f; out += ay + (by - ay) * f; break }
        out += bx; out += by; left -= d
    }
    return out.toFloatArray()
}

@Suppress("unused") private val ORIGIN = Offset.Zero
