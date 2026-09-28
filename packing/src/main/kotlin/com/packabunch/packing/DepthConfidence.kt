package com.packabunch.packing

import java.nio.ByteBuffer

/** Reads Android depth/confidence planes without assuming tightly packed image rows. */
object DepthConfidence {
    fun sample(
        depth: ByteBuffer, confidence: ByteBuffer, width: Int, height: Int,
        depthRowStride: Int, depthPixelStride: Int,
        confidenceRowStride: Int, confidencePixelStride: Int,
        x: Int, y: Int, minimumConfidence: Int = 128,
    ): Int {
        if (x !in 0 until width || y !in 0 until height) return 0
        val score = confidence.get(y * confidenceRowStride + x * confidencePixelStride).toInt() and 0xff
        if (score < minimumConfidence) return 0
        val offset = y * depthRowStride + x * depthPixelStride
        // D_16 is little endian irrespective of the buffer's default byte order.
        return (depth.get(offset).toInt() and 0xff) or
            ((depth.get(offset + 1).toInt() and 0xff) shl 8)
    }
}
