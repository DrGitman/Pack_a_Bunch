package com.packabunch.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Item photos, kept in the app's own storage and nowhere else.
 *
 * The promise made on the camera screens is that photos stay in this app on this phone, so
 * these are copied in rather than referenced: a picker Uri is a temporary grant that stops
 * resolving once the app restarts, and nothing here is ever uploaded. They are not synced
 * either — a file path from another phone would mean nothing on this one.
 */
object ItemPhotos {

    private fun dir(context: Context) = File(context.filesDir, "item-photos").apply { mkdirs() }

    /**
     * The photo for an item, or null if it hasn't got one.
     *
     * Derived from the id rather than stored against the item on purpose. Photos never leave
     * the phone, so a synced column would be empty for every other device and the first
     * incoming version of a pack would wipe the local file's path. The file being there is
     * the whole record.
     */
    fun pathFor(context: Context, itemId: String): String? =
        File(dir(context), "$itemId.jpg").takeIf { it.exists() }?.absolutePath

    /**
     * Stores a picked image and returns its path, or null if it is not a picture that decodes.
     *
     * The picture is decoded and written out again rather than copied byte for byte. Whatever
     * the picker hands over — a 40 MB camera original, or a file that is not an image at all —
     * what lands here is a JPEG no longer than [MAX_PX] on its longest side, and it no longer
     * carries where the photo was taken.
     */
    suspend fun store(context: Context, itemId: String, picked: Uri): String? =
        withContext(Dispatchers.IO) {
            val bitmap = runCatching { decode(context, picked) }.getOrNull() ?: return@withContext null
            try {
                store(context, itemId, bitmap)
            } finally {
                bitmap.recycle()
            }
        }

    private fun decode(context: Context, picked: Uri): android.graphics.Bitmap? {
        val resolver = context.contentResolver
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(picked)?.use { android.graphics.BitmapFactory.decodeStream(it, null, bounds) }
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        if (longest <= 0) return null
        var sample = 1
        while (longest / (sample * 2) >= MAX_PX) sample *= 2
        val decoded = resolver.openInputStream(picked)?.use {
            android.graphics.BitmapFactory.decodeStream(it, null, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        val scale = MAX_PX.toFloat() / maxOf(decoded.width, decoded.height)
        if (scale >= 1f) return decoded
        return android.graphics.Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt().coerceAtLeast(1),
            (decoded.height * scale).toInt().coerceAtLeast(1), true).also { decoded.recycle() }
    }

    /** Longest side of a stored item photo. Plenty for a thumbnail and for the plan's colour. */
    private const val MAX_PX = 1600

    /**
     * Stores a picture the app took itself — the crop of an object from the item scan — as
     * that item's photo. Same place and name as a picked photo, so everything that shows item
     * photos picks it up without knowing where it came from.
     */
    suspend fun store(context: Context, itemId: String, bitmap: android.graphics.Bitmap): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val target = File(dir(context), "$itemId.jpg")
                target.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it) }
                target.absolutePath
            }.getOrNull()
        }

    /** Deletes the file behind a stored path. Missing files are not an error. */
    fun remove(path: String?) {
        if (path != null) runCatching { File(path).delete() }
    }
}
