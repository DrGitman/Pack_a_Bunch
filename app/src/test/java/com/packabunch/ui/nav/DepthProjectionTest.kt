package com.packabunch.ar

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class DepthProjectionTest {
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
