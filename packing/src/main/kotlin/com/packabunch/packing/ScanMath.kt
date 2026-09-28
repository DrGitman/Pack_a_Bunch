package com.packabunch.packing

/**
 * The two steps of an item scan that turn depth into a measurement, behind one door so the
 * engine doing them can be swapped without touching anything around them:
 *
 * - [select]: which of the depth samples inside a detector box are the object's body;
 * - [fit]: the object's size, turn and shape from all the points gathered so far.
 *
 * The app runs the Python engine (`app/src/main/python/packscan.py`, OpenCV and NumPy, through
 * Chaquopy). [KotlinScanMath] is the original engine; it stays as the fallback when Python cannot
 * start, and as the reference the Python engine is tested against.
 */
interface ScanMath {
    fun select(
        samples: List<DetectionPoints.Sample>,
        /** Each sample's raw depth in mm, for the depth window around the middle of the box. */
        depthsMm: IntArray?,
        /** The box's longer side and the focal length, both in depth-image pixels. */
        boxSpanPx: Float,
        focalPx: Float,
        /** Which samples lie inside the object's outline traced in the picture; null when not traced. */
        inMask: BooleanArray? = null,
    ): List<Int>

    fun fit(points: List<PlanePoint>, cameras: List<PlanePoint>, voxelMm: Float, weights: FloatArray?): FittedObject?

    /** A space scan's frame: its walls, everything standing connected (see [DetectionPoints.select]). */
    fun selectSpace(samples: List<DetectionPoints.Sample>, maxHeightMm: Float): List<Int> =
        DetectionPoints.select(samples, minCentralShare = 0f, minCentralSamples = 0, cellMm = DetectionPoints.SPACE_CELL_MM, maxHeightMm = maxHeightMm)

    /** The inside of a space as a box (see [SpaceFitter.fit]). */
    fun fitSpace(points: List<PlanePoint>, cameras: List<PlanePoint>, weights: FloatArray?): SpaceBox? =
        SpaceFitter.fit(points, cameras, weights)
}

/** The original Kotlin engine: [BoxDepth], [DetectionPoints] and [ShapeFitter]. */
object KotlinScanMath : ScanMath {
    override fun select(samples: List<DetectionPoints.Sample>, depthsMm: IntArray?, boxSpanPx: Float, focalPx: Float, inMask: BooleanArray?): List<Int> {
        val traced = inMask?.takeIf { m -> m.size == samples.size && samples.indices.count { samples[it].central && m[it] } >= BoxDepth.MIN_CENTRAL }
        if (depthsMm == null) {
            val pool = samples.indices.filter { traced == null || traced[it] }
            return DetectionPoints.select(pool.map { samples[it] }).map { pool[it] }
        }
        val central = BooleanArray(samples.size) { samples[it].central }
        val keep = BoxDepth.keepRange(depthsMm, central, boxSpanPx, focalPx) ?: return emptyList()
        val inside = samples.indices.filter { depthsMm[it] in keep && (traced == null || traced[it]) }
        return DetectionPoints.select(inside.map { samples[it] }).map { inside[it] }
    }

    override fun fit(points: List<PlanePoint>, cameras: List<PlanePoint>, voxelMm: Float, weights: FloatArray?) =
        ShapeFitter.fit(points, cameras, voxelMm, weights)
}
