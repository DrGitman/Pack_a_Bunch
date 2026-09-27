package com.packabunch.ui.render

import android.graphics.BitmapFactory
import com.packabunch.data.catalogue.ItemRecogniser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * What an item's photo shows, for choosing the shape it is drawn as.
 *
 * Only asked when the name says nothing: "Item 3" with a photo of a guitar is drawn as a
 * guitar rather than a box. A name that does say something always wins, because the person
 * chose it. The label never reaches the item — it is not written back as a name, and like
 * every family shape it is display only and never touches a size.
 *
 * Runs on the phone with the same recogniser as the scan, so the photo stays on the phone.
 */
object PhotoLabels {

    private data class Key(val path: String, val modified: Long)

    private val found = ConcurrentHashMap<Key, String>()
    private val nothing = ConcurrentHashMap.newKeySet<Key>()

    /** Suspends while the photo is read and labelled. Null when nothing confident was seen. */
    suspend fun of(path: String): String? {
        val file = File(path)
        val key = Key(path, file.lastModified())
        found[key]?.let { return it }
        if (key in nothing) return null
        val label = runCatching { read(file) }.getOrNull()
        if (label == null) nothing += key else found[key] = label
        return label
    }

    private suspend fun read(file: File): String? {
        val bitmap = withContext(Dispatchers.IO) { decodeSmall(file) } ?: return null
        val recogniser = ItemRecogniser()
        return try {
            recogniser.recognise(bitmap).category
        } finally {
            recogniser.close()
            bitmap.recycle()
        }
    }

    private fun decodeSmall(file: File): android.graphics.Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        if (longest <= 0) return null
        var sample = 1
        while (longest / (sample * 2) >= LABEL_PX) sample *= 2
        return BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    /** The labeller is trained on images about this size; a full photo only costs time. */
    private const val LABEL_PX = 512
}
