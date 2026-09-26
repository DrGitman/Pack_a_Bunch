package com.packabunch.packing

import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Keeps up to [maxItems] objects apart while one continuous sweep measures all of them.
 *
 * ### Identity
 * ML Kit's tracking id is the first clue, but it is not trusted on its own: it changes when an
 * object leaves the frame and comes back, and it sometimes jumps between neighbours. So an
 * observation belongs to an existing object when its tracking id is already known **or** its
 * points land on that object's footprint (grown by [MATCH_MARGIN_MM]). Only when neither holds
 * does a new object begin.
 *
 * ### No phantoms
 * The old scanner showed "1 of 3 measured" for two objects. Here an object that never gathers
 * enough confirmed surface to fit is dropped after [PHANTOM_FRAMES] frames out of view, and two
 * objects whose footprints overlap by more than half are merged into one — the same carton
 * seen as two detections from two sides.
 *
 * ### The cap
 * [maxItems] is product policy (20 on the free plan), applied here only by refusing to *start*
 * a new object; objects already being measured are never dropped for it.
 */
class ItemTracker(
    val maxItems: Int = FREE_PLAN_MAX_ITEMS,
    private val voxelMm: Float = ObjectCloud.DEFAULT_VOXEL_MM,
) {
    class Track internal constructor(val id: Int, val measurement: ItemMeasurement) {
        internal val ids = HashSet<Int>()

        /** ML Kit tracking ids this object has been known by. Used to find its detector box again. */
        val trackingIds: Set<Int> get() = ids
        internal var lastSeenFrame = 0
        internal var centreX = 0f
        internal var centreY = 0f

        /** Set by the app from the ML Kit label crop. Identity only — never used for size. */
        var label: String? = null

        val state: ItemScanState get() = measurement.state
        val fit: FittedObject? get() = (state as? ItemScanState.Measured)?.fit ?: measurement.lastFit
        val isMeasured: Boolean get() = measurement.isMeasured
    }

    /**
     * One detected object in one frame: its tracking id if ML Kit gave one, and its depth
     * points. [camera] overrides the frame's camera when this object stands on a different
     * surface from the others (heights are measured from each object's own surface).
     */
    data class Observation(val trackingId: Int?, val points: List<PlanePoint>, val camera: PlanePoint? = null)

    data class Update(
        /** Every live object, oldest first. Order is stable, so list positions never jump. */
        val tracks: List<Track>,
        /** True when an object was in view but not started because the cap was reached. */
        val capReached: Boolean,
        /** Which track each observation (by its index in the call) was given to. */
        val assigned: Map<Int, Int> = emptyMap(),
    ) {
        val measuredCount: Int get() = tracks.count { it.isMeasured }
    }

    private val tracks = ArrayList<Track>()
    private var nextId = 1
    private var frame = 0

    val all: List<Track> get() = tracks.toList()

    fun update(observations: List<Observation>, camera: PlanePoint): Update {
        frame++
        var capReached = false
        val fed = LinkedHashMap<Track, ArrayList<PlanePoint>>()
        val cams = HashMap<Track, PlanePoint>()

        val assigned = HashMap<Int, Track>()
        for ((index, obs) in observations.withIndex()) {
            if (obs.points.isEmpty()) continue
            val cx = median(obs.points.map { it.xMm })
            val cy = median(obs.points.map { it.yMm })
            val track = match(obs.trackingId, cx, cy) ?: run {
                if (obs.points.size < MIN_POINTS_TO_START) return@run null
                if (tracks.size >= maxItems) { capReached = true; return@run null }
                Track(nextId++, ItemMeasurement(voxelMm)).also { it.centreX = cx; it.centreY = cy; tracks += it }
            } ?: continue
            obs.trackingId?.let { id ->
                // A tracking id belongs to one object at a time.
                for (t in tracks) if (t !== track) t.ids.remove(id)
                track.ids += id
            }
            track.lastSeenFrame = frame
            assigned[index] = track
            fed.getOrPut(track) { ArrayList() } += obs.points
            obs.camera?.let { cams[track] = it }
        }

        for ((track, pts) in fed) {
            track.measurement.addFrame(pts, cams[track] ?: camera)
            track.measurement.lastFit?.let { track.centreX = it.centreXMm; track.centreY = it.centreYMm }
        }

        mergeDuplicates()
        tracks.removeAll { it.measurement.lastFit == null && !it.isMeasured && frame - it.lastSeenFrame > PHANTOM_FRAMES }
        // A merge can retire a track an observation was just given to; point it at the survivor.
        val live = tracks.associateBy { it.id }
        val resolved = assigned.mapValues { (_, t) -> live[t.id]?.id ?: tracks.firstOrNull { it.fit != null && t.fit != null && overlap(t.fit!!, it.fit!!) > MERGE_OVERLAP }?.id ?: t.id }
        return Update(tracks.toList(), capReached, resolved)
    }

    fun remove(id: Int) { tracks.removeAll { it.id == id } }

    fun clear() { tracks.clear() }

    // ---------------------------------------------------------------------------------------

    private fun match(trackingId: Int?, x: Float, y: Float): Track? {
        if (trackingId != null) tracks.firstOrNull { trackingId in it.ids }?.let { return it }
        var best: Track? = null
        var bestDist = Float.MAX_VALUE
        for (t in tracks) {
            val d = distanceOutside(t, x, y)
            if (d <= MATCH_MARGIN_MM && d < bestDist) { best = t; bestDist = d }
        }
        return best
    }

    /** How far (x, y) lies outside the track's footprint; zero inside. Falls back to its centre. */
    private fun distanceOutside(t: Track, x: Float, y: Float): Float {
        val fit = t.fit ?: return hypot(x - t.centreX, y - t.centreY)
        val (u, v) = local(fit, x, y)
        val du = (kotlin.math.abs(u) - fit.widthMm / 2).coerceAtLeast(0f)
        val dv = (kotlin.math.abs(v) - fit.depthMm / 2).coerceAtLeast(0f)
        return hypot(du, dv)
    }

    private fun local(fit: FittedObject, x: Float, y: Float): Pair<Float, Float> {
        val r = Math.toRadians(fit.yawDegrees.toDouble())
        val c = cos(r).toFloat(); val s = sin(r).toFloat()
        val dx = x - fit.centreXMm; val dy = y - fit.centreYMm
        return (dx * c + dy * s) to (-dx * s + dy * c)
    }

    /** Share of [a]'s footprint that lies inside [b]'s, by sampling a 9×9 grid over [a]. */
    private fun overlap(a: FittedObject, b: FittedObject): Float {
        val r = Math.toRadians(a.yawDegrees.toDouble())
        val c = cos(r).toFloat(); val s = sin(r).toFloat()
        var inside = 0
        for (i in 0 until 9) for (j in 0 until 9) {
            val u = (i / 8f - 0.5f) * a.widthMm
            val v = (j / 8f - 0.5f) * a.depthMm
            val x = a.centreXMm + u * c - v * s
            val y = a.centreYMm + u * s + v * c
            val (bu, bv) = local(b, x, y)
            if (kotlin.math.abs(bu) <= b.widthMm / 2 && kotlin.math.abs(bv) <= b.depthMm / 2) inside++
        }
        return inside / 81f
    }

    private fun mergeDuplicates() {
        var merged = true
        while (merged) {
            merged = false
            loop@ for (i in tracks.indices) for (j in i + 1 until tracks.size) {
                val a = tracks[i]; val b = tracks[j]
                val fa = a.fit ?: continue; val fb = b.fit ?: continue
                val smaller = if (fa.widthMm * fa.depthMm <= fb.widthMm * fb.depthMm) fa else fb
                val larger = if (smaller === fa) fb else fa
                if (overlap(smaller, larger) <= MERGE_OVERLAP) continue
                // Keep the measured one if there is one, otherwise the older.
                val (keep, drop) = if (b.isMeasured && !a.isMeasured) b to a else a to b
                keep.measurement.absorb(drop.measurement)
                keep.ids += drop.ids
                if (keep.label == null) keep.label = drop.label
                keep.lastSeenFrame = maxOf(keep.lastSeenFrame, drop.lastSeenFrame)
                tracks.remove(drop)
                merged = true
                break@loop
            }
        }
    }

    private fun median(v: List<Float>): Float = v.sorted()[v.size / 2]

    companion object {
        const val FREE_PLAN_MAX_ITEMS = 20
        const val MATCH_MARGIN_MM = 60f
        const val MERGE_OVERLAP = 0.5f
        const val PHANTOM_FRAMES = 45
        const val MIN_POINTS_TO_START = 30
    }
}
