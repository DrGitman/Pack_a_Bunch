package com.packabunch.ui.render

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.Color
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * The colour an item is drawn in, taken from its own photo.
 *
 * A plan of twenty brown boxes asks the person to match numbers to a list. A blue cup drawn
 * blue and a red notebook drawn red can be found at a glance, which is the whole reason the
 * photo exists. The number still goes on every item, so colour is never the only signal.
 *
 * Only the middle of the picture is read — the object is what the photo was aimed at, and
 * its edges are the table. Vivid pixels count for more than grey ones, or a red notebook on a
 * grey table averages out to a pinkish grey that matches nothing.
 *
 * The result is kept between [MIN_VALUE] and [MAX_VALUE] in brightness. The plan shades each
 * face lighter by how it faces the light; a white photo would come out as all-white faces
 * with no form, and a black one as a silhouette.
 */
object PhotoColours {

    private data class Key(val path: String, val modified: Long)

    private val found = ConcurrentHashMap<Key, Color>()
    private val unreadable = ConcurrentHashMap.newKeySet<Key>()

    /** Decodes a small copy of the photo, so call it off the main thread. Null if unreadable. */
    fun of(path: String): Color? {
        val file = File(path)
        // Keyed on the file's time as well, so replacing the photo replaces the colour.
        val key = Key(path, file.lastModified())
        found[key]?.let { return it }
        if (key in unreadable) return null
        val colour = runCatching { measure(file) }.getOrNull()
        if (colour == null) unreadable += key else found[key] = colour
        return colour
    }

    private fun measure(file: File): Color? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        if (longest <= 0) return null
        var sample = 1
        while (longest / (sample * 2) >= SAMPLE_PX) sample *= 2
        val bitmap = BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null
        try {
            val x0 = bitmap.width / 5; val x1 = bitmap.width - x0
            val y0 = bitmap.height / 5; val y1 = bitmap.height - y0
            var r = 0.0; var g = 0.0; var b = 0.0; var total = 0.0
            for (y in y0 until y1) for (x in x0 until x1) {
                val p = bitmap.getPixel(x, y)
                val pr = (p shr 16 and 0xFF) / 255.0
                val pg = (p shr 8 and 0xFF) / 255.0
                val pb = (p and 0xFF) / 255.0
                val weight = 0.15 + (maxOf(pr, pg, pb) - minOf(pr, pg, pb))
                r += pr * weight; g += pg * weight; b += pb * weight; total += weight
            }
            if (total <= 0.0) return null
            return readable((r / total).toFloat(), (g / total).toFloat(), (b / total).toFloat())
        } finally {
            bitmap.recycle()
        }
    }

    private fun readable(r: Float, g: Float, b: Float): Color {
        val hsv = FloatArray(3)
        android.graphics.Color.RGBToHSV((r * 255).toInt(), (g * 255).toInt(), (b * 255).toInt(), hsv)
        hsv[2] = hsv[2].coerceIn(MIN_VALUE, MAX_VALUE)
        return Color(android.graphics.Color.HSVToColor(hsv))
    }

    /** The photo is read at about this many pixels on its longest side. The average needs no more. */
    private const val SAMPLE_PX = 64
    private const val MIN_VALUE = 0.35f
    private const val MAX_VALUE = 0.72f
}
