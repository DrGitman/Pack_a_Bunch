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

    /** Copies a picked image in and returns its path, or null if it could not be read. */
    suspend fun store(context: Context, itemId: String, picked: Uri): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val target = File(dir(context), "$itemId.jpg")
                context.contentResolver.openInputStream(picked)?.use { input ->
                    target.outputStream().use(input::copyTo)
                } ?: return@runCatching null
                target.absolutePath
            }.getOrNull()
        }

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
