package com.packabunch.packing

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Finding items by what stands up off the table, with no detector at all.
 *
 * Mirrors the scene that failed on the phone: a cup and a food container a few centimetres apart
 * on a dark table, where the detector returned no boxes but depth was fine.
 */
class StandingObjectsTest {

    private val random = Random(11)
    private val table = -0.40f

    private class Scene {
        val world = ArrayList<Float>()
        val uv = ArrayList<Float>()
        fun add(x: Float, y: Float, z: Float) {
            world += x; world += y; world += z
            // A simple overhead view: enough for "does the box cover the right place".
            uv += (0.5f + x).coerceIn(0f, 1f); uv += (0.5f - z).coerceIn(0f, 1f)
        }
    }

    private fun Scene.surface(n: Int = 3000) = repeat(n) {
        add((random.nextFloat() - 0.5f) * 0.9f, table + (random.nextFloat() - 0.5f) * 0.008f, (random.nextFloat() - 0.5f) * 0.7f)
    }

    /** An upright object whose footprint is centred at (cx, cz). */
    private fun Scene.item(cx: Float, cz: Float, w: Float, d: Float, h: Float, n: Int = 500) = repeat(n) {
        add(cx + (random.nextFloat() - 0.5f) * w, table + random.nextFloat() * h, cz + (random.nextFloat() - 0.5f) * d)
    }

    private fun Scene.find() = StandingObjects.find(world.toFloatArray(), uv.toFloatArray(), table)

    @Test
    fun `a cup and a container a few centimetres apart are two objects`() {
        val scene = Scene().apply {
            surface()
            item(-0.12f, 0f, 0.08f, 0.08f, 0.12f)          // cup
            item(0.06f, 0f, 0.18f, 0.12f, 0.07f)           // container, 5 cm gap
        }

        assertEquals(2, scene.find().size, "the gap between them must keep them apart")
    }

    @Test
    fun `each box sits over its own object`() {
        val found = Scene().apply {
            surface()
            item(-0.12f, 0f, 0.08f, 0.08f, 0.12f)
            item(0.10f, 0f, 0.10f, 0.10f, 0.08f)
        }.find()

        val centres = found.map { (it.box[0] + it.box[2]) / 2 }.sorted()
        assertTrue(kotlin.math.abs(centres[0] - (0.5f - 0.12f)) < 0.03f, "cup box centre ${centres[0]}")
        assertTrue(kotlin.math.abs(centres[1] - (0.5f + 0.10f)) < 0.03f, "container box centre ${centres[1]}")
    }

    @Test
    fun `a bare table is not an object`() {
        assertTrue(Scene().apply { surface() }.find().isEmpty(), "depth noise on the table must not become items")
    }

    @Test
    fun `a few stray points above the table are not an object`() {
        val scene = Scene().apply {
            surface()
            repeat(8) { add(random.nextFloat() * 0.4f - 0.2f, table + 0.05f, random.nextFloat() * 0.3f - 0.15f) }
        }
        assertTrue(scene.find().isEmpty())
    }

    @Test
    fun `overlap is intersection over union so a big box cannot claim a small one`() {
        val carton = floatArrayOf(0.05f, 0.05f, 0.95f, 0.95f)
        val cup = floatArrayOf(0.20f, 0.30f, 0.30f, 0.60f)
        val cupAgain = floatArrayOf(0.19f, 0.29f, 0.31f, 0.61f)

        assertTrue(StandingObjects.overlap(cup, cupAgain) > 0.7f)
        assertTrue(StandingObjects.overlap(cup, carton) < 0.1f, "containment alone is not a match")
    }

    @Test
    fun `sensor and upright boxes round trip at every rotation`() {
        val box = floatArrayOf(0.1f, 0.2f, 0.4f, 0.7f)
        for (rotation in listOf(0, 90, 180, 270)) {
            val up = SensorBox.sensorToUpright(box, rotation)
            val back = SensorBox.uprightToSensor(up[0] * 1000, up[1] * 1000, up[2] * 1000, up[3] * 1000,
                if (rotation % 180 == 0) 1000 else 1000, 1000, rotation)
            for (i in 0..3) assertTrue(kotlin.math.abs(back[i] - box[i]) < 1e-4f, "rotation $rotation: ${back.toList()}")
        }
    }
}
