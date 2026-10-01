package com.packabunch.debug

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.net.Uri
import android.os.Bundle
import android.util.Log
import com.packabunch.scan.PhotoMeasure
import com.packabunch.scan.photoFromGallery
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import java.io.File

/**
 * Debug-only proof: runs the app's own photo engine — the gallery path, [PhotoMeasure] unchanged —
 * on every picture in files/probe-in and saves each result drawn over it to files/probe-out, as
 * OpenCV's drawContours would: every outline in green, 3 px, with the name and sizes found.
 *
 *     adb shell am start -n com.packabunch.debug/com.packabunch.debug.PhotoProbeActivity [--es kind space] [--es name "Car boot"]
 */
class PhotoProbeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val kind = intent.getStringExtra("kind") ?: "items"
        val spaceName = intent.getStringExtra("name")
        val dir = getExternalFilesDir(null)!!
        val outDir = File(dir, "probe-out").apply { mkdirs() }
        MainScope().launch {
            val files = File(dir, "probe-in").listFiles().orEmpty().filter { it.extension.lowercase() in setOf("jpg", "jpeg", "png") }.sorted()
            for (f in files) {
                val input = photoFromGallery(this@PhotoProbeActivity, Uri.fromFile(f)) ?: continue
                val pic = input.picture.copy(Bitmap.Config.ARGB_8888, true)
                val c = Canvas(pic); val w = pic.width.toFloat(); val h = pic.height.toFloat()
                val px = maxOf(w, h) / 1000f
                val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0, 255, 0); style = Paint.Style.STROKE; strokeWidth = 3f * px }
                val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 26f * px; isFakeBoldText = true }
                val back = Paint().apply { color = Color.argb(200, 0, 0, 0) }
                fun label(s: String, x: Float, y: Float) {
                    val tw = text.measureText(s)
                    val lx = (x - tw / 2).coerceIn(0f, w - tw); val ly = y.coerceIn(text.textSize * 1.4f, h)
                    c.drawRect(lx - 6 * px, ly - text.textSize * 1.1f, lx + tw + 6 * px, ly + text.textSize * 0.3f, back)
                    c.drawText(s, lx, ly, text)
                }
                fun poly(p: FloatArray) {
                    if (p.size < 4) return
                    val path = Path().apply { moveTo(p[0] * w, p[1] * h); for (i in 1 until p.size / 2) lineTo(p[2 * i] * w, p[2 * i + 1] * h) }
                    c.drawPath(path, line)
                }
                val report = StringBuilder("${f.name}: ")
                if (kind == "space") {
                    val s = PhotoMeasure(this@PhotoProbeActivity).space(input, spaceName) {}
                    if (s == null) report.append("nothing measured") else {
                        s.edges.forEach { (seg, opening) -> line.color = if (opening) Color.YELLOW else Color.rgb(0, 255, 0); poly(seg) }
                        val d = s.dimensions
                        report.append("W ${cm(d.widthMm)} × D ${cm(d.depthMm)} × H ${cm(d.heightMm)} cm")
                        label("${spaceName ?: "Space"}  ${cm(d.widthMm)} × ${cm(d.depthMm)} × ${cm(d.heightMm)} cm", w / 2, 40f * px)
                    }
                } else {
                    val items = PhotoMeasure(this@PhotoProbeActivity).items(input, 20) {}
                    report.append("${items.size} found")
                    for (it in items) {
                        it.outline.forEach(::poly)
                        val d = it.dimensions
                        val s = "${it.name ?: "Item"}  ${cm(d.widthMm)} × ${cm(d.depthMm)} × ${cm(d.heightMm)} cm"
                        report.append(" | $s")
                        label(s, it.tag.first * w, it.tag.second * h - 10 * px)
                    }
                }
                File(outDir, f.nameWithoutExtension + "-proof.png").outputStream().use { pic.compress(Bitmap.CompressFormat.PNG, 100, it) }
                Log.i("PhotoProbe", report.toString())
            }
            Log.i("PhotoProbe", "done ${files.size}")
            finish()
        }
    }

    private fun cm(mm: Int) = "%.1f".format(mm / 10f)
}
