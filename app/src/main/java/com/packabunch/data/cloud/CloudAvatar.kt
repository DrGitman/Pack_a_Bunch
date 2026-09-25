package com.packabunch.data.cloud

import android.content.Context
import android.net.Uri
import com.packabunch.BuildConfig
import com.packabunch.auth.SupabaseAccount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
            val bytes = context.contentResolver.openInputStream(picked)?.use { it.readBytes() }
                ?: error("Couldn't read that picture.")
            send("POST", bytes, context.contentResolver.getType(picked) ?: "image/jpeg")
            // Cache-busting: the address never changes, so without this the old photo lingers.
            publicUrl + "?v=" + System.currentTimeMillis()
        }
    }.getOrNull()

    suspend fun remove(): Boolean = runCatching {
        withContext(Dispatchers.IO) { send("DELETE", null, null) }
        true
    }.getOrDefault(false)

    private suspend fun send(method: String, body: ByteArray?, contentType: String?) {
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
