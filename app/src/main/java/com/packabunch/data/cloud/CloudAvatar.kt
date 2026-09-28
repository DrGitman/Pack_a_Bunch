package com.packabunch.data.cloud

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.packabunch.BuildConfig
import com.packabunch.auth.SupabaseAccount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * The profile photo, kept in Supabase storage under a folder named after the account.
 *
 * One file per person, replaced on every upload, so a photo never leaves an orphan behind and
 * the address stays stable. The bucket is public to read: an avatar is shown beside a name, and
 * signing every read would buy nothing.
 */
class CloudAvatar(private val account: SupabaseAccount, private val owner: String) {

    private val path get() = "$owner/avatar.jpg"

    val publicUrl: String
        get() = BuildConfig.SUPABASE_URL.trimEnd('/') + "/storage/v1/object/public/avatars/" + path

    /** Uploads what the picker returned. Returns the address to show, or null if it failed. */
    suspend fun upload(context: Context, picked: Uri): String? = runCatching {
        withContext(Dispatchers.IO) {
            val bytes = encodeAvatar(context, picked) ?: error("Couldn't read that picture.")
            send("POST", bytes, "image/jpeg")
            // Cache-busting: the address never changes, so without this the old photo lingers.
            publicUrl + "?v=" + System.currentTimeMillis()
        }
    }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }.getOrNull()

    suspend fun remove(): Boolean = runCatching {
        withContext(Dispatchers.IO) { send("DELETE", null, null) }
        true
    }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }.getOrDefault(false)

    private suspend fun send(method: String, body: ByteArray?, contentType: String?) {
        check(owner.isNotBlank() && account.userId == owner) { "Sign in again to update your photo." }
        val token = account.accessToken()
        val connection = URL(BuildConfig.SUPABASE_URL.trimEnd('/') + "/storage/v1/object/avatars/" + path)
            .openConnection() as HttpsURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.setRequestProperty("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("x-upsert", "true")
            if (contentType != null) connection.setRequestProperty("Content-Type", contentType)
            if (body != null) {
                connection.doOutput = true
                connection.outputStream.use { it.write(body) }
            }
            check(connection.responseCode in 200..299) { "Storage HTTP ${connection.responseCode}" }
            connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }
}

private const val AVATAR_PX = 512

/**
 * The picked file, decoded and written out again as a small JPEG — never the file itself.
 *
 * The bucket is public, so what goes up must be a picture and nothing else. Decoding proves
 * it is one: anything that is not an image fails here and is never sent. Re-encoding also
 * drops everything a camera file carries besides pixels, including where the photo was
 * taken, which would otherwise be readable by anyone with the address.
 */
internal fun encodeAvatar(context: Context, picked: Uri): ByteArray? {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    val boundsStream = resolver.openInputStream(picked) ?: return null
    // Bounds-only decoding returns null even for a valid image.
    boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }
    val longest = maxOf(bounds.outWidth, bounds.outHeight)
    if (longest <= 0) return null
    var sample = 1
    while (longest / (sample * 2) >= AVATAR_PX) sample *= 2
    val decoded = resolver.openInputStream(picked)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    } ?: return null
    val scale = AVATAR_PX.toFloat() / maxOf(decoded.width, decoded.height)
    val sized = if (scale < 1f) {
        Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt().coerceAtLeast(1),
            (decoded.height * scale).toInt().coerceAtLeast(1), true)
    } else decoded
    return try {
        ByteArrayOutputStream().use { out ->
            check(sized.compress(Bitmap.CompressFormat.JPEG, 88, out)) { "Could not encode the picture." }
            out.toByteArray()
        }
    } finally {
        if (sized !== decoded) sized.recycle()
        decoded.recycle()
    }
}
