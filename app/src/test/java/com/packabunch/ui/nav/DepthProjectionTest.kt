package com.packabunch.ar

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class DepthProjectionTest {
    @Test fun `a 300 mm object retains its size as the camera distance changes`() {
        val projection = DepthProjection(800f, 600f, 320f, 240f)
        for (depthMm in listOf(500, 1000, 2000)) {
            val span = (300f * projection.fx / depthMm).toInt()
            val left = projection.point(320 - span / 2, 240, depthMm)
            val right = projection.point(320 + span / 2, 240, depthMm)
            assertEquals(300f, (right[0] - left[0]) * 1000f, 0.01f)
        }
    }

    @Test fun `off axis depth is optical z rather than length of the ray`() {
        val p = DepthProjection(400f, 200f, 320f, 240f).point(520, 140, 1000)
        assertArrayEquals(floatArrayOf(.5f, .5f, -1f), p, .0001f)
    }
    @Test fun `principal point is one metre forward and upper right is up right`() {
        val projection=DepthProjection(100f,100f,50f,40f)
        assertArrayEquals(floatArrayOf(0f,0f,-1f),projection.point(50,40,1000),0.0001f)
        assertArrayEquals(floatArrayOf(0.5f,0.2f,-1f),projection.point(100,20,1000),0.0001f)
    }
    @Test fun `changing depth resolution preserves the same physical ray`() {
        val full=DepthProjection.scaled(500f,500f,320f,240f,640,480,640,480)
        val small=DepthProjection.scaled(500f,500f,320f,240f,640,480,160,120)
        assertArrayEquals(full.point(400,200,2000),small.point(100,50,2000),0.0001f)
    }
}
