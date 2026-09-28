package com.packabunch.ar

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
import com.packabunch.packing.ShapeFamily
import org.json.JSONArray
import org.json.JSONObject

/**
 * The measuring engine the scans run: `app/src/main/python/packscan.py` (NumPy and OpenCV) on the
 * phone's own Python, through Chaquopy. It traces each object's body from the depth points and
 * measures it; ARCore still supplies the scale (every depth pixel in millimetres) and where the
 * phone is.
 *
 * If Python cannot start or a call fails, that call falls back to [KotlinScanMath] — a scan
 * never stops because of the engine.
 */
class PythonEngine private constructor(private val module: PyObject) : ScanMath {

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
            Log.w(AR_TAG, "python select failed, using Kotlin", it)
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
            Log.w(AR_TAG, "python fit failed, using Kotlin", it)
            KotlinScanMath.fit(points, cameras, voxelMm, weights)
        }

    /**
     * Traces every detector box's object in a small camera frame (RGB, sensor orientation).
     * One byte per pixel: 0 nothing, i + 1 box i's object. Null when Python is not running.
     */
    fun segmentFrame(rgb: ByteArray, width: Int, height: Int, boxes: List<FloatArray>): ByteArray? = runCatching {
        val json = JSONArray(boxes.map { b -> JSONArray(b.map { it.toDouble() }) }).toString()
        module.callAttr("segment_frame", rgb, width, height, json).toJava(ByteArray::class.java)
    }.onFailure { Log.w(AR_TAG, "python segmentation failed", it) }.getOrNull()

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
                PythonEngine(Python.getInstance().getModule("packscan"))
            }.getOrElse {
                Log.e(AR_TAG, "Python engine did not start; measuring with Kotlin", it)
                KotlinScanMath
            }.also { engine = it }
        }
    }
}
