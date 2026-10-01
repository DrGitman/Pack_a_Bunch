package com.packabunch.scan

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.packabunch.data.cloud.ItemLookup
import com.packabunch.packing.Dimensions
import com.packabunch.packing.ItemForm
import com.packabunch.packing.LookupMerge
import com.packabunch.packing.PhotoEngine
import com.packabunch.packing.PhotoProgress
import com.packabunch.packing.PhotoSpace
import com.packabunch.packing.PlanarPose
import com.packabunch.packing.Raster
import com.packabunch.packing.ShapeFamily
import com.packabunch.packing.SizeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/** One photo to measure: upright, its lens geometry in its own pixels, and which way was up. */
class PhotoInput(
    val picture: Bitmap,
    val intrinsics: PlanarPose.Intrinsics,
    /** Up in camera axes (x right, y down, z forward), from the phone's gravity sensor; null for a gallery photo. */
    val up: DoubleArray?,
)

/** One thing measured from a photo. Sizes are camera estimates; the person checks each one. */
class PhotoItem(
    val name: String?,
    /** `[left, top, right, bottom]` in 0..1 of the picture. */
    val box: FloatArray,
    /** Its traced outline then its fitted 3D box, drawn on the picture: polylines, x, y pairs in 0..1. */
    val outline: List<FloatArray>,
    /** Where its name tag goes, 0..1 of the picture: the top of its outline. */
    val tag: Pair<Float, Float>,
    /** Its footprint on the surface, a closed polygon in 0..1 of the picture, for the faint glow under it. */
    val footprint: FloatArray?,
    val dimensions: Dimensions,
    val shape: ShapeFamily,
    val form: ItemForm?,
    /** Its own crop, for its photo. Sent to be identified only when "Look up items online" is on. */
    val crop: Bitmap?,
    /** Where its size came from, for the badge beside it. */
    val source: SizeSource = SizeSource.PHOTO,
)

/**
 * Measures everything in one photo, on the phone: runs the models (YOLOX, ML Kit, MiDaS), then
 * hands what they saw to [PhotoEngine] — the same engine the computer test runs — and names
 * anything YOLOX did not know with the on-phone labeller.
 *
 * With [lookup] (Settings, "Look up items online"), each item's cut-out — never the whole photo —
 * is then identified online, and a recognised item's real size sets the scale for the rest
 * ([LookupMerge]). Without it nothing leaves the phone. Every size says where it came from.
 */
class PhotoMeasure(private val context: Context, private val lookup: ItemLookup? = null) {

    private val engine = PhotoEngine(PythonEngine.lazy(context)) { Log.i(SCAN_TAG, it) }

    suspend fun items(input: PhotoInput, maxItems: Int, onProgress: (PhotoProgress) -> Unit): List<PhotoItem> = withContext(Dispatchers.Default) {
        val pic = input.picture

        // 1. Finding the things. A still photo can afford to look harder than a live feed: a
        //    lower YOLOX bar, and ML Kit in single-image mode.
        onProgress(PhotoProgress(0, 0.1f))
        val yolo = runCatching { YoloxDetector(context).use { it.detect(pic, PhotoEngine.PHOTO_MIN_SCORE) } }.getOrElse { Log.w(SCAN_TAG, "yolox", it); emptyList() }
        onProgress(PhotoProgress(0, 0.3f))
        val mlkit = runCatching { ItemScanDetector(stream = false).let { d -> d.detect(pic, 0, 0L).also { d.close() } } }.getOrElse { emptyList() }
        onProgress(PhotoProgress(0, 0.45f))
        val rel = runCatching { MidasDepth(context).use { it.run(pic) } }.getOrElse { Log.w(SCAN_TAG, "depth", it); null }

        // 2–3. Tracing and sizing: the shared engine.
        val found = engine.items(pic.toRaster(), input.intrinsics, input.up, yolo, mlkit.map { it.upright }, rel, MidasDepth.SIZE, maxItems, onProgress)
        val last = PhotoProgress(2, 1f, found = found.size, traced = found.size, outlines = found.map { it.outline })

        // 4. Naming them: YOLOX's names already; the on-phone labeller for anything it did not know;
        //    then, if switched on, the online lookup for what each one really is and its real size.
        val recogniser = com.packabunch.data.catalogue.ItemRecogniser()
        val crops = found.map { crop(pic, it.box) }
        val names = found.mapIndexed { i, item ->
            onProgress(last.copy(stage = 3, fraction = 0.2f * (i + 1) / found.size))
            item.name ?: crops[i]?.let { c -> runCatching { recogniser.scanCategory(c) }.getOrNull()?.replaceFirstChar { it.uppercase() } }
        }
        val answers = if (lookup == null) found.map { null } else coroutineScope {
            found.indices.map { i ->
                async { crops[i]?.let { c -> lookup.identify(c, names[i], found[i].dimensions) } }
            }.awaitAll()
        }
        onProgress(last.copy(stage = 3, fraction = 0.9f))
        val merged = LookupMerge.merge(found.map { it.dimensions }, names,
            answers.map { a -> a?.let { LookupMerge.Answer(it.name, it.exact, it.confidence, it.size) } }, found.firstOrNull()?.scale ?: com.packabunch.packing.ScaleSource.TYPICAL)
        Log.i(SCAN_TAG, "photo lookup: " + found.indices.joinToString(" | ") { i ->
            "${names[i]} ${found[i].dimensions.let { "${it.widthMm}x${it.depthMm}x${it.heightMm}" }} -> " +
                (answers[i]?.let { "${it.name} '${it.product}' exact=${it.exact} conf=%.2f".format(it.confidence) } ?: "no answer") +
                " = ${merged[i].dimensions.let { "${it.widthMm}x${it.depthMm}x${it.heightMm}" }} ${merged[i].source}"
        })
        onProgress(last.copy(stage = 3, fraction = 1f))
        found.mapIndexed { i, item ->
            val name = merged[i].name
            PhotoItem(name, item.box, item.outline, item.tag, item.footprint, merged[i].dimensions, item.shape,
                item.form ?: name?.let(ItemForm::guess), crops[i], merged[i].source)
        }
    }

    suspend fun space(input: PhotoInput, spaceName: String?, onProgress: (PhotoProgress) -> Unit): PhotoSpace? = withContext(Dispatchers.Default) {
        onProgress(PhotoProgress(0, 0.1f))
        val rel = runCatching { MidasDepth(context).use { it.run(input.picture) } }.getOrElse { Log.w(SCAN_TAG, "depth", it); return@withContext null }
        engine.space(input.picture.toRaster(), input.intrinsics, input.up, rel, MidasDepth.SIZE, spaceName, onProgress)
    }

    private fun crop(pic: Bitmap, b: FloatArray): Bitmap? {
        val m = 0.02f
        val x = ((b[0] - m) * pic.width).toInt().coerceIn(0, pic.width - 1)
        val y = ((b[1] - m) * pic.height).toInt().coerceIn(0, pic.height - 1)
        val cw = ((b[2] - b[0] + 2 * m) * pic.width).toInt().coerceAtMost(pic.width - x)
        val chh = ((b[3] - b[1] + 2 * m) * pic.height).toInt().coerceAtMost(pic.height - y)
        if (cw < 16 || chh < 16) return null
        return runCatching { Bitmap.createBitmap(pic, x, y, cw, chh) }.getOrNull()
    }
}

/** The picture's pixels for the shared engine. */
fun Bitmap.toRaster(): Raster = Raster(IntArray(width * height).also { getPixels(it, 0, width, 0, 0, width, height) }, width, height)

/** Closes models after one photo: they are big, and a photo scan is a one-off. */
private inline fun <T : java.io.Closeable, R> T.use(block: (T) -> R): R = try { block(this) } finally { close() }
