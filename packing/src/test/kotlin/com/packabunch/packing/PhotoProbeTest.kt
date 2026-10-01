package com.packabunch.packing

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import java.nio.FloatBuffer
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import javax.imageio.ImageIO
import kotlin.math.sqrt
import kotlin.test.Test

/**
 * The phone's photo scan, run on the computer: the same YOLOX and MiDaS model files through ONNX
 * Runtime, the same input preparation ([PhotoEngine.yoloxInput], [PhotoEngine.midasInput]), the
 * same [PhotoEngine], and the same Python fitter (`app/src/main/python/packscan.py`) the phone
 * runs through Chaquopy. Only ML Kit is missing — it exists only on Android — so the phone has
 * one more object finder than this.
 *
 * Each photo is treated as one picked from the gallery: no gravity reading, and the lens the app
 * assumes when a photo has no EXIF (a 26 mm-equivalent phone camera).
 *
 *     ./gradlew :packing:test --tests "*PhotoProbeTest*" -Pprobe.in=<folder> -Pprobe.out=<folder>
 *
 * The input folder holds `items/` and `space-<Name>/` folders of photos. Each result is drawn
 * onto its photo — every outline in green, a space's opening in yellow, names and sizes — and
 * written with the engine's own log, so every number can be traced back to why.
 */
class PhotoProbeTest {

    @Test
    fun `measure every photo in the probe folder`() {
        val inDir = System.getProperty("probe.in")?.let(::File)
        assumeTrue("no -Pprobe.in folder given", inDir != null && inDir.isDirectory)
        val outDir = File(System.getProperty("probe.out") ?: File(inDir, "out").path).apply { mkdirs() }
        val root = File(System.getProperty("probe.root")!!)
        val assets = File(root, "app/src/main/assets")
        val env = OrtEnvironment.getEnvironment()
        val yolox = env.createSession(File(assets, "yolox_tiny.onnx").readBytes(), OrtSession.SessionOptions())
        val midas = env.createSession(File(assets, "midas_small.onnx").readBytes(), OrtSession.SessionOptions())
        val report = StringBuilder()
        PythonBridge(File(root, "app/src/main/python")).use { python ->
            for (folder in inDir!!.listFiles()!!.filter { it.isDirectory }.sortedBy { it.name }) {
                val space = folder.name.startsWith("space-")
                val spaceName = folder.name.removePrefix("space-").takeIf { space }
                for (file in folder.listFiles()!!.filter { it.extension.lowercase() in setOf("jpg", "jpeg", "png") }.sorted()) {
                    val log = StringBuilder()
                    val engine = PhotoEngine(python) { log.appendLine(it) }
                    val img = ImageIO.read(file)
                    val pic = Raster(IntArray(img.width * img.height).also { img.getRGB(0, 0, img.width, img.height, it, 0, img.width) }, img.width, img.height)
                    // The app's lens guess for a photo with no EXIF: 26 mm equivalent (CameraFeed.photoFromGallery).
                    val f = 26.0 * sqrt((pic.width * pic.width + pic.height * pic.height).toDouble()) / 43.27
                    val k = PlanarPose.Intrinsics(f, f, pic.width / 2.0, pic.height / 2.0)
                    val started = System.nanoTime()
                    val line = withWatchdog(file.name) {
                        val rel = midas(env, midas, pic)
                        if (space) {
                            val s = engine.space(pic, k, null, rel, MIDAS_SIZE, spaceName)
                            draw(img, s?.edges.orEmpty().map { it.first to if (it.second) Color.YELLOW else GREEN },
                                s?.let { listOf(Triple("$spaceName  ${cm(it.dimensions)}", 0.5f, 0.06f)) }.orEmpty())
                            s?.let { "$spaceName: W×D×H ${cm(it.dimensions)}" + (it.opening?.let { o -> ", opening ${o.first / 10.0} × ${o.second / 10.0} cm" } ?: "") }
                                ?: "nothing measured"
                        } else {
                            val yolo = yolox(env, yolox, pic, PhotoEngine.PHOTO_MIN_SCORE)
                            val items = engine.items(pic, k, null, yolo, emptyList(), rel, MIDAS_SIZE, 20)
                            draw(img, items.flatMap { it.outline.map { o -> o to GREEN } },
                                items.map { Triple("${it.name ?: "Item"}  ${cm(it.dimensions)}", it.tag.first, it.tag.second) })
                            "${items.size} found" + items.joinToString("") { "\n    ${it.name ?: "Item"}: ${cm(it.dimensions)}  (${it.shape})" }
                        }
                    }
                    ImageIO.write(img, "png", File(outDir, "${folder.name}__${file.nameWithoutExtension}-proof.png"))
                    report.appendLine("${folder.name}/${file.name} (%.1f s): $line".format((System.nanoTime() - started) / 1e9))
                    log.lines().filter { it.isNotBlank() }.forEach { report.appendLine("    | $it") }
                }
            }
        }
        File(outDir, "results.txt").writeText(report.toString())
        println(report)
    }

    private fun cm(d: Dimensions) = "%.1f × %.1f × %.1f cm".format(d.widthMm / 10.0, d.depthMm / 10.0, d.heightMm / 10.0)

    /** Every outline (polylines in 0..1) and label (text, x, y in 0..1) drawn onto the photo. */
    private fun draw(img: BufferedImage, lines: List<Pair<FloatArray, Color>>, labels: List<Triple<String, Float, Float>>) {
        val g = img.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        val px = maxOf(img.width, img.height) / 600f
        g.stroke = BasicStroke(2f * px)
        for ((p, c) in lines) {
            g.color = c
            for (i in 0 until p.size / 2 - 1) g.drawLine((p[2 * i] * img.width).toInt(), (p[2 * i + 1] * img.height).toInt(), (p[2 * i + 2] * img.width).toInt(), (p[2 * i + 3] * img.height).toInt())
        }
        g.font = Font(Font.SANS_SERIF, Font.BOLD, (11 * px).toInt().coerceAtLeast(10))
        for ((text, x, y) in labels) {
            val fm = g.fontMetrics; val tw = fm.stringWidth(text)
            val lx = (x * img.width - tw / 2).toInt().coerceIn(0, maxOf(0, img.width - tw))
            val ly = (y * img.height - 4 * px).toInt().coerceIn(fm.ascent + 2, img.height - 2)
            g.color = Color(0, 0, 0, 200); g.fillRect(lx - 3, ly - fm.ascent - 1, tw + 6, fm.height + 2)
            g.color = Color.WHITE; g.drawString(text, lx, ly)
        }
        g.dispose()
    }

    /** Runs one photo with a time limit; a hang prints where the engine was stuck. */
    private fun withWatchdog(name: String, block: () -> String): String {
        val pool = Executors.newSingleThreadExecutor { r -> Thread(r, "probe-$name").apply { isDaemon = true } }
        val job = pool.submit<String> { block() }
        return try { job.get(LIMIT_S, TimeUnit.SECONDS) } catch (e: TimeoutException) {
            val stuck = Thread.getAllStackTraces().entries.first { it.key.name == "probe-$name" }.value
            job.cancel(true)
            "HUNG after ${LIMIT_S}s at:\n" + stuck.take(12).joinToString("\n") { "      at $it" }
        } catch (e: java.util.concurrent.ExecutionException) {
            "FAILED: ${e.cause}\n" + e.cause!!.stackTrace.take(8).joinToString("\n") { "      at $it" }
        } finally { pool.shutdownNow() }
    }

    private fun yolox(env: OrtEnvironment, s: OrtSession, pic: Raster, minScore: Float): List<YoloxDecode.Detection> {
        val n = YoloxDecode.INPUT.toLong()
        OnnxTensor.createTensor(env, FloatBuffer.wrap(PhotoEngine.yoloxInput(pic)), longArrayOf(1, 3, n, n)).use { t ->
            s.run(mapOf(s.inputNames.first() to t)).use { r ->
                @Suppress("UNCHECKED_CAST")
                val out = (r[0].value as Array<Array<FloatArray>>)[0]
                val flat = FloatArray(out.size * out[0].size)
                for ((i, row) in out.withIndex()) row.copyInto(flat, i * row.size)
                return YoloxDecode.decode(flat, pic.width, pic.height, minScore)
            }
        }
    }

    private fun midas(env: OrtEnvironment, s: OrtSession, pic: Raster): FloatArray {
        val n = MIDAS_SIZE.toLong()
        OnnxTensor.createTensor(env, FloatBuffer.wrap(PhotoEngine.midasInput(pic, MIDAS_SIZE)), longArrayOf(1, 3, n, n)).use { t ->
            s.run(mapOf(s.inputNames.first() to t)).use { r ->
                return FloatArray(MIDAS_SIZE * MIDAS_SIZE).also { (r[0] as OnnxTensor).floatBuffer.get(it) }
            }
        }
    }

    /**
     * The phone's Python fitter, run by the computer's Python: the same `packscan.py`, called with
     * the same JSON the app's PythonEngine sends.
     */
    private class PythonBridge(pythonDir: File) : ScanMath, AutoCloseable {
        private val proc: Process
        init {
            val script = File.createTempFile("packscan_bridge", ".py").apply {
                deleteOnExit()
                writeText("""
                    |import sys
                    |sys.path.insert(0, r"${pythonDir.absolutePath}")
                    |import packscan
                    |for line in sys.stdin:
                    |    fn, payload = line.rstrip("\n").split("\t", 1)
                    |    sys.stdout.write(str(getattr(packscan, fn)(payload)).replace("\n", " ") + "\n")
                    |    sys.stdout.flush()
                """.trimMargin())
            }
            proc = ProcessBuilder("python", "-u", script.absolutePath).redirectError(ProcessBuilder.Redirect.INHERIT).start()
        }
        private val toPy = proc.outputStream.bufferedWriter()
        private val fromPy = proc.inputStream.bufferedReader()

        @Synchronized private fun call(fn: String, payload: JSONObject): JSONObject {
            toPy.write("$fn\t$payload\n"); toPy.flush()
            return JSONObject(fromPy.readLine() ?: error("python stopped"))
        }

        private fun xyh(p: JSONObject, pts: List<PlanePoint>) {
            p.put("x", JSONArray(pts.map { it.xMm.toDouble() })); p.put("y", JSONArray(pts.map { it.yMm.toDouble() })); p.put("h", JSONArray(pts.map { it.hMm.toDouble() }))
        }

        override fun select(samples: List<DetectionPoints.Sample>, depthsMm: IntArray?, boxSpanPx: Float, focalPx: Float, inMask: BooleanArray?) =
            KotlinScanMath.select(samples, depthsMm, boxSpanPx, focalPx, inMask)

        override fun fit(points: List<PlanePoint>, cameras: List<PlanePoint>, voxelMm: Float, weights: FloatArray?): FittedObject? {
            val p = JSONObject(); xyh(p, points)
            p.put("cameras", JSONArray(cameras.map { JSONArray(listOf(it.xMm.toDouble(), it.yMm.toDouble(), it.hMm.toDouble())) }))
            p.put("voxel", voxelMm.toDouble())
            val a = call("fit_json", p)
            if (a.isNull("fit")) return null
            val f = a.getJSONObject("fit")
            fun opt(k: String) = if (f.isNull(k)) null else f.getDouble(k).toFloat()
            val hull = f.getJSONArray("hull"); val observed = f.getJSONArray("observed")
            return FittedObject(
                centreXMm = f.getDouble("centreX").toFloat(), centreYMm = f.getDouble("centreY").toFloat(), yawDegrees = f.getDouble("yaw").toFloat(),
                widthMm = f.getDouble("width").toFloat(), depthMm = f.getDouble("depth").toFloat(), heightMm = f.getDouble("height").toFloat(),
                shape = ShapeFamily.valueOf(f.getString("shape")), topRadiusMm = opt("topRadius"), bottomRadiusMm = opt("bottomRadius"),
                footprintHull = List(hull.length()) { hull.getJSONArray(it).let { q -> q.getDouble(0).toFloat() to q.getDouble(1).toFloat() } },
                observedAxes = List(observed.length()) { Axis.valueOf(observed.getString(it)) }.toSet(), pointCount = f.getInt("pointCount"),
            )
        }

        override fun selectSpace(samples: List<DetectionPoints.Sample>, maxHeightMm: Float): List<Int> {
            val p = JSONObject(); xyh(p, samples.map { it.point })
            p.put("central", JSONArray(samples.map { it.central })); p.put("maxH", maxHeightMm.toDouble())
            val keep = call("select_space_json", p).getJSONArray("keep")
            return List(keep.length()) { keep.getInt(it) }
        }

        override fun fitSpace(points: List<PlanePoint>, cameras: List<PlanePoint>, weights: FloatArray?): SpaceBox? {
            val p = JSONObject(); xyh(p, points)
            p.put("cameras", JSONArray(cameras.map { JSONArray(listOf(it.xMm.toDouble(), it.yMm.toDouble(), it.hMm.toDouble())) }))
            val a = call("fit_space_json", p)
            if (a.isNull("space")) return null
            val f = a.getJSONObject("space"); val cov = f.getJSONObject("coverage")
            return SpaceBox(
                centreXMm = f.getDouble("centreX").toFloat(), centreYMm = f.getDouble("centreY").toFloat(), yawDegrees = f.getDouble("yaw").toFloat(),
                widthMm = f.getDouble("width").toFloat(), depthMm = f.getDouble("depth").toFloat(), heightMm = f.getDouble("height").toFloat(),
                coverage = SpaceFace.entries.associateWith { cov.optDouble(it.name, 0.0).toFloat() },
                opening = if (f.isNull("opening")) null else f.getJSONArray("opening").let { Opening(widthMm = it.getInt(0), heightMm = it.getInt(1)) },
                cameraInside = f.getBoolean("cameraInside"), pointCount = f.getInt("pointCount"),
            )
        }

        override fun close() { runCatching { toPy.close() }; proc.waitFor(5, TimeUnit.SECONDS); proc.destroy() }
    }

    private companion object {
        const val MIDAS_SIZE = 256
        const val LIMIT_S = 120L
        val GREEN = Color(0, 255, 0)
    }
}
