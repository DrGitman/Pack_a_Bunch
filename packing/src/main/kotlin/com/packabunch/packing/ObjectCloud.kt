package com.packabunch.packing

import kotlin.math.floor

/**
 * Everything the camera has seen of one object, in its support surface's frame, deduplicated
 * into small voxels.
 *
 * ### Why voxels, and why "frames" rather than "hits"
 *
 * A single depth frame lands dozens of samples in the same few millimetres, so counting raw
 * samples would make one noisy frame look like strong evidence. Each voxel instead counts how
 * many *separate frames* saw something there. A voxel only becomes part of the object once
 * [MIN_SIGHTINGS] frames agree — depth-from-motion noise rarely lands in the same place twice,
 * a real surface always does.
 *
 * Isolated voxels (fewer than [MIN_NEIGHBOURS] confirmed neighbours) are dropped as well. A
 * surface is continuous; a speck floating in the air beside it is not part of the object.
 *
 * The cloud coarsens itself if it grows past [maxVoxels], so a suitcase costs about the same
 * as a mug.
 */
class ObjectCloud(
    voxelMm: Float = DEFAULT_VOXEL_MM,
    private val maxVoxels: Int = 16_000,
    /**
     * Points at or below this height are dropped as the surface itself. An object's cloud
     * wants that ([MIN_HEIGHT_MM]); a space's cloud wants its floor, so it passes a negative
     * value and keeps everything.
     */
    private val minHeightMm: Float = MIN_HEIGHT_MM,
    /**
     * See [snapshot]. A space is swept past, not orbited, so its edges are seen in far fewer
     * frames than its middle; it uses a lower share than an object does.
     */
    private val relativeSightings: Float = RELATIVE_SIGHTINGS,
) {
    var voxelMm: Float = voxelMm
        private set

    private class Voxel(var frames: Int, var lastFrame: Int, var sx: Double, var sy: Double, var sh: Double, var n: Int)

    private var voxels = HashMap<Long, Voxel>()
    private var frame = 0

    /** Frames folded in so far, including ones that contributed nothing. */
    val framesAdded: Int get() = frame

    /** Raw voxel count, confirmed or not. */
    val size: Int get() = voxels.size

    /** Adds one frame's points. Points at or below [minHeightMm] are the surface, not the object. */
    fun add(points: Iterable<PlanePoint>) {
        frame++
        for (p in points) {
            if (p.hMm <= minHeightMm || p.xMm.isNaN() || p.yMm.isNaN() || p.hMm.isNaN()) continue
            if (voxels.size >= maxVoxels) coarsen()
            val k = key(p.xMm, p.yMm, p.hMm)
            val v = voxels[k]
            if (v == null) {
                voxels[k] = Voxel(1, frame, p.xMm.toDouble(), p.yMm.toDouble(), p.hMm.toDouble(), 1)
            } else {
                if (v.lastFrame != frame) {
                    v.frames++
                    v.lastFrame = frame
                }
                v.sx += p.xMm
                v.sy += p.yMm
                v.sh += p.hMm
                v.n++
            }
        }
    }

    /** Confirmed surface points and, for each, how many separate frames saw it. */
    class Snapshot(val points: List<PlanePoint>, val weights: FloatArray)

    /**
     * The object's surface as it is currently known: one point per confirmed, connected voxel,
     * at the mean of everything that landed in it.
     */
    fun points(): List<PlanePoint> = snapshot().points

    /**
     * Like [points], with each point's sighting count as a weight.
     *
     * Confirmation is also *relative*: a voxel must have been seen in at least
     * [RELATIVE_SIGHTINGS] of the frames that the well-seen part of the object was (its 90th
     * percentile). A real surface keeps being seen as long as it is in view; a depth outlier
     * that happened to land twice in the same place over a long sweep does not.
     */
    fun snapshot(): Snapshot {
        if (voxels.isEmpty()) return Snapshot(emptyList(), FloatArray(0))
        val counts = IntArray(voxels.size); var i = 0
        for (v in voxels.values) counts[i++] = v.frames
        counts.sort()
        val p90 = counts[((counts.size - 1) * 0.9).toInt()]
        val need = maxOf(MIN_SIGHTINGS, kotlin.math.ceil(p90 * relativeSightings).toInt())
        val confirmed = HashMap<Long, Voxel>()
        for ((k, v) in voxels) if (v.frames >= need) confirmed[k] = v
        val out = ArrayList<PlanePoint>(confirmed.size)
        val w = ArrayList<Float>(confirmed.size)
        for ((k, v) in confirmed) {
            if (confirmedNeighbours(k, confirmed) < MIN_NEIGHBOURS) continue
            out += PlanePoint((v.sx / v.n).toFloat(), (v.sy / v.n).toFloat(), (v.sh / v.n).toFloat())
            w += v.frames.toFloat()
        }
        return Snapshot(out, w.toFloatArray())
    }

    private fun confirmedNeighbours(k: Long, confirmed: Map<Long, Voxel>): Int {
        val i = unpack(k, 42)
        val j = unpack(k, 21)
        val h = unpack(k, 0)
        var n = 0
        for (di in -1..1) for (dj in -1..1) for (dh in -1..1) {
            if (di == 0 && dj == 0 && dh == 0) continue
            if (confirmed.containsKey(pack(i + di, j + dj, h + dh))) {
                n++
                if (n >= MIN_NEIGHBOURS) return n
            }
        }
        return n
    }

    /**
     * Folds another cloud of the same surface frame into this one — used when two tracks turn
     * out to be the same object seen from two sides. Sightings are not added together: a
     * voxel both clouds saw in the same frame was still only seen once.
     */
    fun absorb(other: ObjectCloud) {
        while (other.voxelMm > voxelMm) coarsen()
        for (v in other.voxels.values) {
            if (voxels.size >= maxVoxels) coarsen()
            val k = key((v.sx / v.n).toFloat(), (v.sy / v.n).toFloat(), (v.sh / v.n).toFloat())
            val m = voxels[k]
            if (m == null) {
                voxels[k] = Voxel(v.frames, frame, v.sx, v.sy, v.sh, v.n)
            } else {
                m.frames = maxOf(m.frames, v.frames)
                m.sx += v.sx; m.sy += v.sy; m.sh += v.sh; m.n += v.n
            }
        }
    }

    private fun coarsen() {
        val old = voxels
        voxelMm *= 2f
        voxels = HashMap(old.size / 4 + 16)
        for (v in old.values) {
            val k = key((v.sx / v.n).toFloat(), (v.sy / v.n).toFloat(), (v.sh / v.n).toFloat())
            val m = voxels[k]
            if (m == null) {
                voxels[k] = Voxel(v.frames, v.lastFrame, v.sx, v.sy, v.sh, v.n)
            } else {
                m.frames = maxOf(m.frames, v.frames)
                m.lastFrame = maxOf(m.lastFrame, v.lastFrame)
                m.sx += v.sx
                m.sy += v.sy
                m.sh += v.sh
                m.n += v.n
            }
        }
    }

    private fun key(x: Float, y: Float, h: Float): Long =
        pack(floor(x / voxelMm).toInt(), floor(y / voxelMm).toInt(), floor(h / voxelMm).toInt())

    companion object {
        const val DEFAULT_VOXEL_MM = 5f

        /** Separate frames that must agree before a voxel is believed. */
        const val MIN_SIGHTINGS = 2

        /** Share of the well-seen surface's sightings a voxel needs. */
        const val RELATIVE_SIGHTINGS = 0.12f

        /** Confirmed neighbours a voxel needs to count as surface rather than a stray speck. */
        const val MIN_NEIGHBOURS = 2

        /** Anything this close to the support surface is the surface itself. */
        const val MIN_HEIGHT_MM = 6f

        private const val BIAS = 1 shl 20
        private const val MASK = (1L shl 21) - 1

        private fun pack(i: Int, j: Int, h: Int): Long =
            ((i + BIAS).toLong() and MASK shl 42) or ((j + BIAS).toLong() and MASK shl 21) or ((h + BIAS).toLong() and MASK)

        private fun unpack(k: Long, shift: Int): Int = ((k ushr shift) and MASK).toInt() - BIAS
    }
}
