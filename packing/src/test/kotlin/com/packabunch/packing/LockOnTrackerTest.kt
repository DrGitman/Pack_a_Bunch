package com.packabunch.packing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class LockOnTrackerTest {

    private fun box(cx: Float, cy: Float, w: Float = 0.2f, h: Float = 0.3f) = floatArrayOf(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2)
    private fun mouse(cx: Float, cy: Float) = LockOnTracker.Detection(box(cx, cy), "mouse")

    @Test
    fun `one mouse keeps one id as the camera pans`() {
        val t = LockOnTracker()
        val first = t.update(listOf(mouse(0.30f, 0.5f)))[0]
        var cx = 0.30f
        repeat(20) { cx += 0.03f; assertEquals(first, t.update(listOf(mouse(cx, 0.5f)))[0], "frame $it") }
    }

    @Test
    fun `a few missed frames do not start a new target`() {
        val t = LockOnTracker()
        val id = t.update(listOf(mouse(0.5f, 0.5f)))[0]
        repeat(4) { t.update(emptyList()) } // blurred frames, nothing detected
        assertEquals(id, t.update(listOf(mouse(0.52f, 0.5f)))[0])
    }

    @Test
    fun `two objects side by side keep their own ids`() {
        val t = LockOnTracker()
        val ids = t.update(listOf(mouse(0.3f, 0.5f), LockOnTracker.Detection(box(0.7f, 0.5f), "cup")))
        assertNotEquals(ids[0], ids[1])
        val again = t.update(listOf(LockOnTracker.Detection(box(0.71f, 0.5f), "cup"), mouse(0.31f, 0.5f)))
        assertEquals(ids[0], again[1]); assertEquals(ids[1], again[0])
    }

    @Test
    fun `a fast pan with no overlap still keeps the lock`() {
        val t = LockOnTracker()
        val id = t.update(listOf(mouse(0.30f, 0.5f)))[0]
        assertEquals(id, t.update(listOf(mouse(0.39f, 0.5f)))[0])
    }
}
