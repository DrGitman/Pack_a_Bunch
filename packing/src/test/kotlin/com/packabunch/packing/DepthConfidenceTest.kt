package com.packabunch.packing

import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.assertEquals

class DepthConfidenceTest {
    private val depth = ByteBuffer.allocate(32).apply {
        put(12, 0xe8.toByte()); put(13, 3) // second row, second pixel: 1000 mm
    }
    private val confidence = ByteBuffer.allocate(16).apply { put(6, 255.toByte()) }
    private fun sample(x: Int, y: Int) = DepthConfidence.sample(depth, confidence, 2, 2, 8, 4, 4, 2, x, y)

    @Test fun paddedRowsAndPixelStridesPreserveMetricDepth() {
        assertEquals(1000, sample(1, 1))
    }
    @Test fun uncertainDepthIsNotMeasurementEvidence() {
        confidence.put(6, 127)
        assertEquals(0, sample(1, 1))
        confidence.put(6, 128.toByte())
        assertEquals(1000, sample(1, 1))
    }
    @Test fun missingAndOutsideSamplesAreInvalid() {
        assertEquals(0, sample(0, 0))
        assertEquals(0, sample(-1, 0))
        assertEquals(0, sample(2, 1))
    }
}
