package com.packabunch.data.cloud

import com.packabunch.BuildConfig
import com.packabunch.auth.SupabaseAccount
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * Sends the "How are we doing?" card to the app_feedback table: the stars, the words if any,
 * how many packs the person has finished and the app version. Best effort, like fit reports:
 * true when it arrived, false when it could not be sent (the card says so and lets them retry).
 */
class CloudFeedback(private val account: SupabaseAccount) {

    suspend fun send(stars: Int, comment: String?, packsFinished: Int): Boolean =
        runCatching {
            withContext(Dispatchers.IO) {
                val body = JSONObject()
                    .put("stars", stars.coerceIn(1, 5))
                    .put("packs_finished", packsFinished.coerceAtLeast(0))
                    .put("app_version", BuildConfig.VERSION_NAME.take(40))
                comment?.trim()?.takeIf { it.isNotEmpty() }?.let { body.put("comment", it.take(1000)) }
                val token = account.accessToken()
                val connection = URL(BuildConfig.SUPABASE_URL.trimEnd('/') + "/rest/v1/app_feedback").openConnection() as HttpsURLConnection
                try {
                    connection.instanceFollowRedirects = false
                    connection.connectTimeout = 15_000
                    connection.readTimeout = 20_000
                    connection.requestMethod = "POST"
                    connection.setRequestProperty("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
                    connection.setRequestProperty("Authorization", "Bearer $token")
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.setRequestProperty("Prefer", "return=minimal")
                    connection.doOutput = true
                    connection.outputStream.use { it.write(body.toString().toByteArray()) }
                    connection.responseCode in 200..299
                } finally {
                    connection.disconnect()
                }
            }
        }.onFailure { if (it is CancellationException) throw it }.getOrDefault(false)
}
