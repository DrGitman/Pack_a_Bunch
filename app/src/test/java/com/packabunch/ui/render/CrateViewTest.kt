package com.packabunch.ui.render

import androidx.compose.ui.geometry.Size
import com.packabunch.packing.Dimensions
import com.packabunch.packing.Space
import org.junit.Assert.*
import org.junit.Test

class CrateViewTest {
    private fun view(mode: PackingView, yaw: Float = 0f) = CrateView(
        Space("test", "Space", Dimensions(1000, 800, 600)), Size(400f, 300f), yaw, mode,
    )

    @Test fun orthographicViewsCollapseOnlyTheirViewingAxis() {
        val top = view(PackingView.TOP)
        assertEquals(top.project(100f, 200f, 0f), top.project(100f, 200f, 600f))
        assertTrue(top.depth(100f, 200f, 600f) > top.depth(100f, 200f, 0f))
        val front = view(PackingView.FRONT)
        assertEquals(front.project(100f, 0f, 300f), front.project(100f, 800f, 300f))
        assertTrue(front.depth(100f, 0f, 300f) > front.depth(100f, 800f, 300f))
        val side = view(PackingView.SIDE)
        assertEquals(side.project(0f, 200f, 300f), side.project(1000f, 200f, 300f))
        assertTrue(side.depth(0f, 200f, 300f) > side.depth(1000f, 200f, 300f))
    }

    @Test fun turningHalfwayReversesHorizontalOcclusion() {
        val initial = view(PackingView.ISOMETRIC)
        val turned = view(PackingView.ISOMETRIC, Math.PI.toFloat())
        assertTrue(initial.depth(900f, 700f, 0f) > initial.depth(100f, 100f, 0f))
        assertTrue(turned.depth(900f, 700f, 0f) < turned.depth(100f, 100f, 0f))
    }

    @Test fun everyViewFitsAllSpaceCornersOnCanvas() {
        PackingView.entries.forEach { mode ->
            val camera = view(mode, 1.2f)
            for (x in listOf(0f, 1000f)) for (y in listOf(0f, 800f)) for (z in listOf(0f, 600f)) {
                val p = camera.project(x, y, z)
                assertTrue("$mode: $p", p.x in 0f..400f && p.y in 0f..300f)
            }
        }
    }
}
