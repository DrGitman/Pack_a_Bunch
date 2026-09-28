package com.packabunch.packing

import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File

/**
 * The Python engine (`app/src/main/python/packscan.py`) run as a `python3 --serve` process, so the
 * JVM tests put exactly the code the phone runs through the same simulated scans as the Kotlin
 * engine. [start] returns null when python3, NumPy or OpenCV is missing; those tests then skip.
 */
class PythonScanMath private constructor(private val process: Process) : ScanMath, AutoCloseable {
    private val input: BufferedWriter = process.outputStream.bufferedWriter()
    private val output: BufferedReader = process.inputStream.bufferedReader()

    private fun call(json: String): Map<String, Any?> {
        input.write(json); input.write("\n"); input.flush()
        val line = output.readLine() ?: error("packscan.py stopped")
        @Suppress("UNCHECKED_CAST")
        val answer = Json.parse(line) as Map<String, Any?>
        answer["error"]?.let { error("packscan.py: $it") }
        return answer
    }

    override fun select(samples: List<DetectionPoints.Sample>, depthsMm: IntArray?, boxSpanPx: Float, focalPx: Float, inMask: BooleanArray?): List<Int> {
        val sb = StringBuilder("{\"op\":\"select\"")
        sb.append(",\"x\":").append(samples.joinToString(",", "[", "]") { it.point.xMm.toString() })
        sb.append(",\"y\":").append(samples.joinToString(",", "[", "]") { it.point.yMm.toString() })
        sb.append(",\"h\":").append(samples.joinToString(",", "[", "]") { it.point.hMm.toString() })
        sb.append(",\"central\":").append(samples.joinToString(",", "[", "]") { it.central.toString() })
        sb.append(",\"edge\":").append(samples.joinToString(",", "[", "]") { it.edge.toString() })
        if (depthsMm != null) sb.append(",\"depth\":").append(depthsMm.joinToString(",", "[", "]"))
        else sb.append(",\"depthWindow\":false")
        if (inMask != null) sb.append(",\"inMask\":").append(inMask.joinToString(",", "[", "]"))
        sb.append(",\"spanPx\":").append(boxSpanPx).append(",\"focalPx\":").append(focalPx).append('}')
        @Suppress("UNCHECKED_CAST")
        return (call(sb.toString())["keep"] as List<Double>).map { it.toInt() }
    }

    override fun fit(points: List<PlanePoint>, cameras: List<PlanePoint>, voxelMm: Float, weights: FloatArray?): FittedObject? {
        val sb = StringBuilder("{\"op\":\"fit\"")
        sb.append(",\"x\":").append(points.joinToString(",", "[", "]") { it.xMm.toString() })
        sb.append(",\"y\":").append(points.joinToString(",", "[", "]") { it.yMm.toString() })
        sb.append(",\"h\":").append(points.joinToString(",", "[", "]") { it.hMm.toString() })
        if (weights != null) sb.append(",\"weights\":").append(weights.joinToString(",", "[", "]"))
        sb.append(",\"cameras\":").append(cameras.joinToString(",", "[", "]") { "[${it.xMm},${it.yMm},${it.hMm}]" })
        sb.append(",\"voxel\":").append(voxelMm).append('}')
        @Suppress("UNCHECKED_CAST")
        val f = call(sb.toString())["fit"] as Map<String, Any?>? ?: return null
        fun num(k: String) = (f[k] as Double).toFloat()
        fun opt(k: String) = (f[k] as Double?)?.toFloat()
        @Suppress("UNCHECKED_CAST")
        return FittedObject(
            centreXMm = num("centreX"), centreYMm = num("centreY"), yawDegrees = num("yaw"),
            widthMm = num("width"), depthMm = num("depth"), heightMm = num("height"),
            shape = ShapeFamily.valueOf(f["shape"] as String),
            topRadiusMm = opt("topRadius"), bottomRadiusMm = opt("bottomRadius"),
            footprintHull = (f["hull"] as List<List<Double>>).map { it[0].toFloat() to it[1].toFloat() },
            observedAxes = (f["observed"] as List<String>).map { Axis.valueOf(it) }.toSet(),
            pointCount = (f["pointCount"] as Double).toInt(),
        )
    }

    override fun close() {
        runCatching { input.close() }
        process.destroy()
    }

    companion object {
        fun start(): PythonScanMath? = runCatching {
            val script = listOfNotNull(System.getenv("PACKSCAN"), "../app/src/main/python/packscan.py", "app/src/main/python/packscan.py").map(::File).first { it.exists() }
            val p = ProcessBuilder("python3", script.path, "--serve").redirectError(ProcessBuilder.Redirect.INHERIT).start()
            val math = PythonScanMath(p)
            val pong = math.call("{\"op\":\"ping\"}")
            println("Python engine up, OpenCV ${pong["opencv"]}")
            math
        }.getOrNull()
    }
}

/** Just enough JSON for the engine's answers: objects, arrays, numbers, strings, booleans, null. */
internal object Json {
    fun parse(s: String): Any? = Parser(s).value()

    private class Parser(val s: String) {
        var i = 0
        fun ws() { while (i < s.length && s[i].isWhitespace()) i++ }
        fun value(): Any? {
            ws()
            return when (s[i]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> str()
                't' -> { i += 4; true }
                'f' -> { i += 5; false }
                'n' -> { i += 4; null }
                else -> num()
            }
        }
        fun obj(): Map<String, Any?> {
            val m = LinkedHashMap<String, Any?>(); i++; ws()
            if (s[i] == '}') { i++; return m }
            while (true) {
                ws(); val k = str(); ws(); i++ // ':'
                m[k] = value(); ws()
                if (s[i++] == '}') return m
            }
        }
        fun arr(): List<Any?> {
            val l = ArrayList<Any?>(); i++; ws()
            if (s[i] == ']') { i++; return l }
            while (true) {
                l += value(); ws()
                if (s[i++] == ']') return l
            }
        }
        fun str(): String {
            val sb = StringBuilder(); i++
            while (s[i] != '"') { if (s[i] == '\\') { i++ }; sb.append(s[i++]) }
            i++
            return sb.toString()
        }
        fun num(): Double {
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] in "+-.eE")) i++
            return s.substring(start, i).toDouble()
        }
    }
}
