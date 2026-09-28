package com.packabunch.ar

import com.google.ar.core.Coordinates2d
import com.google.ar.core.Frame

/** Depth uses GPU texture geometry, which can be cropped differently from the CPU image. */
internal class DepthImageMapping(private val frame: Frame) {
    private val basis = FloatArray(6).also {
        frame.transformCoordinates2d(Coordinates2d.TEXTURE_NORMALIZED,
            floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f), Coordinates2d.IMAGE_NORMALIZED, it)
    }

    fun imagePoint(u: Float, v: Float) = floatArrayOf(
        basis[0] + u * (basis[2]-basis[0]) + v * (basis[4]-basis[0]),
        basis[1] + u * (basis[3]-basis[1]) + v * (basis[5]-basis[1]),
    )

    fun depthBox(box: FloatArray): FloatArray {
        val mapped = FloatArray(8)
        frame.transformCoordinates2d(Coordinates2d.IMAGE_NORMALIZED,
            floatArrayOf(box[0],box[1], box[2],box[1], box[2],box[3], box[0],box[3]),
            Coordinates2d.TEXTURE_NORMALIZED, mapped)
        val xs = listOf(mapped[0],mapped[2],mapped[4],mapped[6])
        val ys = listOf(mapped[1],mapped[3],mapped[5],mapped[7])
        return floatArrayOf(xs.min(),ys.min(),xs.max(),ys.max())
    }
}
