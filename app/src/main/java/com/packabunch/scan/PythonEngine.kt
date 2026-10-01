package com.packabunch.scan

import android.content.Context
import android.util.Log
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.packabunch.packing.Axis
import com.packabunch.packing.DetectionPoints
import com.packabunch.packing.FittedObject
import com.packabunch.packing.KotlinScanMath
import com.packabunch.packing.PlanePoint
import com.packabunch.packing.ScanMath
import com.packabunch.packing.Opening
import com.packabunch.packing.ShapeFamily
import com.packabunch.packing.SpaceBox
import com.packabunch.packing.SpaceFace
import org.json.JSONArray
import org.json.JSONObject

/**
 * The measuring engine the scans run: `app/src/main/python/packscan.py` (NumPy and OpenCV) on the
 * phone's own Python, through Chaquopy. It traces each object's body from the depth points and
 * measures it; the scene engine supplies the scale (every depth pixel in millimetres, from the
 * card in view) and where the phone is.
 *
 * If Python cannot start or a call fails, that call falls back to [KotlinScanMath] — a scan
 * never stops because of the engine.
 */
class PythonEngine private constructor(internal val module: PyObject) : ScanMath {

    override fun select(samples: List<DetectionPoints.Sample>, depthsMm: IntArray?, boxSpanPx: Float, focalPx: Float, inMask: BooleanArray?): List<Int> =
        runCatching {
            val p = JSONObject()
            p.put("x", JSONArray(samples.map { it.point.xMm.toDouble() }))
            p.put("y", JSONArray(samples.map { it.point.yMm.toDouble() }))
            p.put("h", JSONArray(samples.map { it.point.hMm.toDouble() }))
            p.put("central", JSONArray(samples.map { it.central }))
            p.put("edge", JSONArray(samples.map { it.edge }))
            if (depthsMm != null) p.put("depth", JSONArray(depthsMm.toList())) else p.put("depthWindow", false)
            if (inMask != null) p.put("inMask", JSONArray(inMask.toList()))
            p.put("spanPx", boxSpanPx.toDouble()).put("focalPx", focalPx.toDouble())
            val keep = JSONObject(module.callAttr("select_json", p.toString()).toString()).getJSONArray("keep")
            List(keep.length()) { keep.getInt(it) }
        }.getOrElse {
            Log.w(SCAN_TAG, "python select failed, using Kotlin", it)
            KotlinScanMath.select(samples, depthsMm, boxSpanPx, focalPx, inMask)
        }

    override fun fit(points: List<PlanePoint>, cameras: List<PlanePoint>, voxelMm: Float, weights: FloatArray?): FittedObject? =
        runCatching {
            val p = JSONObject()
            p.put("x", JSONArray(points.map { it.xMm.toDouble() }))
            p.put("y", JSONArray(points.map { it.yMm.toDouble() }))
            p.put("h", JSONArray(points.map { it.hMm.toDouble() }))
            if (weights != null) p.put("weights", JSONArray(weights.map { it.toDouble() }))
            p.put("cameras", JSONArray(cameras.map { JSONArray(listOf(it.xMm.toDouble(), it.yMm.toDouble(), it.hMm.toDouble())) }))
            p.put("voxel", voxelMm.toDouble())
            val answer = JSONObject(module.callAttr("fit_json", p.toString()).toString())
            if (answer.isNull("fit")) return@runCatching null
            val f = answer.getJSONObject("fit")
            fun opt(k: String) = if (f.isNull(k)) null else f.getDouble(k).toFloat()
            val hull = f.getJSONArray("hull")
            val observed = f.getJSONArray("observed")
            FittedObject(
                centreXMm = f.getDouble("centreX").toFloat(), centreYMm = f.getDouble("centreY").toFloat(),
                yawDegrees = f.getDouble("yaw").toFloat(),
                widthMm = f.getDouble("width").toFloat(), depthMm = f.getDouble("depth").toFloat(),
                heightMm = f.getDouble("height").toFloat(),
                shape = ShapeFamily.valueOf(f.getString("shape")),
                topRadiusMm = opt("topRadius"), bottomRadiusMm = opt("bottomRadius"),
                footprintHull = List(hull.length()) { hull.getJSONArray(it).let { q -> q.getDouble(0).toFloat() to q.getDouble(1).toFloat() } },
                observedAxes = List(observed.length()) { Axis.valueOf(observed.getString(it)) }.toSet(),
                pointCount = f.getInt("pointCount"),
            )
        }.getOrElse {
            Log.w(SCAN_TAG, "python fit failed, using Kotlin", it)
            KotlinScanMath.fit(points, cameras, voxelMm, weights)
        }

    override fun selectSpace(samples: List<DetectionPoints.Sample>, maxHeightMm: Float): List<Int> = runCatching {
        val p = JSONObject()
        p.put("x", JSONArray(samples.map { it.point.xMm.toDouble() }))
        p.put("y", JSONArray(samples.map { it.point.yMm.toDouble() }))
        p.put("h", JSONArray(samples.map { it.point.hMm.toDouble() }))
        p.put("central", JSONArray(samples.map { it.central }))
        p.put("maxH", maxHeightMm.toDouble())
        val keep = JSONObject(module.callAttr("select_space_json", p.toString()).toString()).getJSONArray("keep")
        List(keep.length()) { keep.getInt(it) }
    }.getOrElse {
        Log.w(SCAN_TAG, "python space select failed, using Kotlin", it)
        KotlinScanMath.selectSpace(samples, maxHeightMm)
    }

    override fun fitSpace(points: List<PlanePoint>, cameras: List<PlanePoint>, weights: FloatArray?): SpaceBox? = runCatching {
        val p = JSONObject()
        p.put("x", JSONArray(points.map { it.xMm.toDouble() }))
        p.put("y", JSONArray(points.map { it.yMm.toDouble() }))
        p.put("h", JSONArray(points.map { it.hMm.toDouble() }))
        if (weights != null) p.put("weights", JSONArray(weights.map { it.toDouble() }))
        p.put("cameras", JSONArray(cameras.map { JSONArray(listOf(it.xMm.toDouble(), it.yMm.toDouble(), it.hMm.toDouble())) }))
        val answer = JSONObject(module.callAttr("fit_space_json", p.toString()).toString())
        if (answer.isNull("space")) return@runCatching null
        val f = answer.getJSONObject("space")
        val cov = f.getJSONObject("coverage")
        SpaceBox(
            centreXMm = f.getDouble("centreX").toFloat(), centreYMm = f.getDouble("centreY").toFloat(),
            yawDegrees = f.getDouble("yaw").toFloat(),
            widthMm = f.getDouble("width").toFloat(), depthMm = f.getDouble("depth").toFloat(), heightMm = f.getDouble("height").toFloat(),
            coverage = SpaceFace.entries.associateWith { cov.optDouble(it.name, 0.0).toFloat() },
            opening = if (f.isNull("opening")) null else f.getJSONArray("opening").let { Opening(widthMm = it.getInt(0), heightMm = it.getInt(1)) },
            cameraInside = f.getBoolean("cameraInside"),
            pointCount = f.getInt("pointCount"),
        )
    }.getOrElse {
        Log.w(SCAN_TAG, "python space fit failed, using Kotlin", it)
        KotlinScanMath.fitSpace(points, cameras, weights)
    }

    /**
     * Traces every detector box's object in a small camera frame (RGB, sensor orientation).
     * One byte per pixel: 0 nothing, i + 1 box i's object. Null when Python is not running.
     */
    fun segmentFrame(rgb: ByteArray, width: Int, height: Int, boxes: List<FloatArray>): ByteArray? = runCatching {
        val json = JSONArray(boxes.map { b -> JSONArray(b.map { it.toDouble() }) }).toString()
        module.callAttr("segment_frame", rgb, width, height, json).toJava(ByteArray::class.java)
    }.onFailure { Log.w(SCAN_TAG, "python segmentation failed", it) }.getOrNull()

    companion object {
        @Volatile private var engine: ScanMath? = null

        /** The frame tracer, once Python is up; null before that or on a phone where it will not start. */
        fun tracer(): PythonEngine? = engine as? PythonEngine

        /**
         * The engine, started on first use — which is on the scan's worker thread, never the
         * screen's: Python takes a second or so to wake up the first time.
         */
        fun lazy(context: Context): ScanMath {
            val app = context.applicationContext
            return object : ScanMath {
                override fun select(samples: List<DetectionPoints.Sample>, depthsMm: IntArray?, boxSpanPx: Float, focalPx: Float, inMask: BooleanArray?) =
                    get(app).select(samples, depthsMm, boxSpanPx, focalPx, inMask)
                override fun fit(points: List<PlanePoint>, cameras: List<PlanePoint>, voxelMm: Float, weights: FloatArray?) =
                    get(app).fit(points, cameras, voxelMm, weights)
                override fun selectSpace(samples: List<DetectionPoints.Sample>, maxHeightMm: Float) = get(app).selectSpace(samples, maxHeightMm)
                override fun fitSpace(points: List<PlanePoint>, cameras: List<PlanePoint>, weights: FloatArray?) = get(app).fitSpace(points, cameras, weights)
            }
        }

        /**
         * Proves on the phone that Python is really doing the measuring: which Python, NumPy and
         * OpenCV it has, and what it measures a known 100 × 60 × 40 mm box as, next to the Kotlin
         * engine's answer. One log line each, at start-up.
         */
        private fun selfTest(engine: PythonEngine) {
            try {
                val py = Python.getInstance()
                val version = py.getModule("sys").get("version").toString().substringBefore(" ")
                val numpy = py.getModule("numpy").get("__version__").toString()
                val cv = engine.module.get("cv2")?.toString()
                Log.i(SCAN_TAG, "python started: python=$version numpy=$numpy opencv=${if (cv == null || cv == "None") "absent" else "present"}")
                val points = ArrayList<PlanePoint>()
                val rnd = java.util.Random(1)
                repeat(3000) {
                    // Points on the top and the four sides of a 100 × 60 × 40 mm box.
                    val face = rnd.nextInt(5); val a = rnd.nextFloat(); val b = rnd.nextFloat()
                    points += when (face) {
                        0 -> PlanePoint(-50 + 100 * a, -30 + 60 * b, 40f)
                        1 -> PlanePoint(-50 + 100 * a, -30f, 40 * b)
                        2 -> PlanePoint(-50 + 100 * a, 30f, 40 * b)
                        3 -> PlanePoint(-50f, -30 + 60 * a, 40 * b)
                        else -> PlanePoint(50f, -30 + 60 * a, 40 * b)
                    }
                }
                val cameras = listOf(PlanePoint(0f, -400f, 300f), PlanePoint(400f, 0f, 300f), PlanePoint(0f, 400f, 300f), PlanePoint(-400f, 0f, 300f))
                fun describe(f: FittedObject?) = f?.let { "%.0f x %.0f x %.0f mm %s".format(it.widthMm, it.depthMm, it.heightMm, it.shape) } ?: "no fit"
                val t0 = android.os.SystemClock.elapsedRealtime()
                val p = engine.fit(points, cameras, 5f, null)
                val t1 = android.os.SystemClock.elapsedRealtime()
                val k = KotlinScanMath.fit(points, cameras, 5f, null)
                val t2 = android.os.SystemClock.elapsedRealtime()
                Log.i(SCAN_TAG, "python self-test, true 100 x 60 x 40 mm BOX: python ${describe(p)} in ${t1 - t0} ms; kotlin ${describe(k)} in ${t2 - t1} ms")
            } catch (t: Throwable) {
                Log.e(SCAN_TAG, "python self-test failed", t)
            }
        }

        /** Starts Python in the background, so the first scan does not wait for it. */
        fun warmUp(context: Context) {
            val app = context.applicationContext
            Thread({ get(app) }, "python-warm-up").apply { isDaemon = true }.start()
        }

        /** The Python engine, started once; the Kotlin one if Python will not start on this phone. */
        fun get(context: Context): ScanMath = engine ?: synchronized(this) {
            engine ?: runCatching {
                if (!Python.isStarted()) Python.start(AndroidPlatform(context.applicationContext))
                PythonEngine(Python.getInstance().getModule("packscan")).also { selfTest(it) }
            }.getOrElse {
                Log.e(SCAN_TAG, "Python engine did not start; measuring with Kotlin", it)
                KotlinScanMath
            }.also { engine = it }
        }
    }
}
