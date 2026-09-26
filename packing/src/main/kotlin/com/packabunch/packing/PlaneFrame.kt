package com.packabunch.packing

import kotlin.math.sqrt

/**
 * A point on or above the surface an item is standing on, in millimetres, in that surface's
 * own coordinates: [xMm] and [yMm] run along the surface, [hMm] is height above it.
 *
 * Measuring in the surface's frame rather than the world's is what makes "any shape" work.
 * The item's footprint is then just a set of 2D points, its height is just the largest `h`,
 * and a table that is slightly tilted — or a phone session that started crooked — changes
 * nothing, because everything is relative to the thing the item is actually resting on.
 */
data class PlanePoint(val xMm: Float, val yMm: Float, val hMm: Float)

/**
 * The support surface as a coordinate frame.
 *
 * Built from any point on the surface plus two directions in world space: one lying along
 * the surface ([alongAxis]) and its upward normal ([upAxis]). They need not be exactly
 * perpendicular or unit length — ARCore's are close but not exact after a pose update — so
 * the frame re-orthonormalises them rather than trusting them.
 *
 * World units are metres (ARCore's); plane units are millimetres (everything downstream).
 */
class PlaneFrame(
    originXM: Float,
    originYM: Float,
    originZM: Float,
    alongAxis: FloatArray,
    upAxis: FloatArray,
) {
    private val ox = originXM
    private val oy = originYM
    private val oz = originZM

    /** Unit up. */
    val up: FloatArray = normalise(upAxis)

    /** Unit along-surface X, with any component along [up] removed. */
    val axisX: FloatArray = normalise(reject(alongAxis, up))

    /** Unit along-surface Y, completing a right-handed frame with [axisX] and [up]. */
    val axisY: FloatArray = cross(up, axisX)

    fun toPlane(xM: Float, yM: Float, zM: Float): PlanePoint {
        val dx = xM - ox
        val dy = yM - oy
        val dz = zM - oz
        return PlanePoint(
            xMm = (dx * axisX[0] + dy * axisX[1] + dz * axisX[2]) * 1000f,
            yMm = (dx * axisY[0] + dy * axisY[1] + dz * axisY[2]) * 1000f,
            hMm = (dx * up[0] + dy * up[1] + dz * up[2]) * 1000f,
        )
    }

    /** The inverse of [toPlane]. Writes world metres into [out] (size ≥ 3). */
    fun toWorld(p: PlanePoint, out: FloatArray) {
        val x = p.xMm / 1000f
        val y = p.yMm / 1000f
        val h = p.hMm / 1000f
        out[0] = ox + x * axisX[0] + y * axisY[0] + h * up[0]
        out[1] = oy + x * axisX[1] + y * axisY[1] + h * up[1]
        out[2] = oz + x * axisX[2] + y * axisY[2] + h * up[2]
    }

    private companion object {
        fun normalise(v: FloatArray): FloatArray {
            val n = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
            require(n > 1e-6f) { "Zero-length axis" }
            return floatArrayOf(v[0] / n, v[1] / n, v[2] / n)
        }

        fun reject(v: FloatArray, unitNormal: FloatArray): FloatArray {
            val d = v[0] * unitNormal[0] + v[1] * unitNormal[1] + v[2] * unitNormal[2]
            return floatArrayOf(v[0] - d * unitNormal[0], v[1] - d * unitNormal[1], v[2] - d * unitNormal[2])
        }

        fun cross(a: FloatArray, b: FloatArray) = floatArrayOf(
            a[1] * b[2] - a[2] * b[1],
            a[2] * b[0] - a[0] * b[2],
            a[0] * b[1] - a[1] * b[0],
        )
    }
}
