package com.packabunch.ar

import android.media.Image
import com.google.ar.core.Frame
import java.nio.ByteOrder
import java.io.Closeable

/** Metric samples only: never accumulate interpolated pixels as independent evidence. */
internal class MeasurementDepth private constructor(
    private val image: Image,
    private val confidence: Image,
) : Closeable {
    val width get() = image.width
    val height get() = image.height
    val timestamp get() = image.timestamp
    private val plane = image.planes[0]
    private val quality = confidence.planes[0]
    private val data = plane.buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
    private val scores = quality.buffer.duplicate()

    fun at(x: Int, y: Int): Int = com.packabunch.packing.DepthConfidence.sample(
        data, scores, width, height, plane.rowStride, plane.pixelStride,
        quality.rowStride, quality.pixelStride, x, y,
    )
    override fun close() { try { confidence.close() } finally { image.close() } }

    companion object {
        fun acquire(frame: Frame): MeasurementDepth {
            val image = frame.acquireRawDepthImage16Bits()
            try {
                return MeasurementDepth(image, frame.acquireRawDepthConfidenceImage())
            } catch (e: Exception) {
                image.close()
                throw e
            }
        }
    }
}

