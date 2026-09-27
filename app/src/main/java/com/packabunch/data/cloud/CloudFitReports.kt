package com.packabunch.data.cloud

import com.packabunch.BuildConfig
import com.packabunch.auth.SupabaseAccount
import com.packabunch.packing.Dimensions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/** Why an item didn't fit, as picked on the "It doesn't fit" page. */
enum class FitReason(val key: String) {
    ITEM_BIGGER("item_bigger"),
    SPACE_SMALLER("space_smaller"),
    IN_THE_WAY("in_the_way"),
    OTHER_ARRANGEMENT("other_arrangement"),
    SKIPPED("skipped"),
}

/**
 * Sends a "didn't fit" report to the fit_reports table, so plans can be checked against what
 * really happened. The reason and the sizes only — no names, nothing typed. Best effort: a
 * report that cannot be sent is dropped, never retried into someone's data allowance, and never
 * holds up the person who is standing there with a box that won't go in.
 */
class CloudFitReports(private val account: SupabaseAccount) {

    suspend fun send(
        reason: FitReason,
        item: Dimensions?,
        space: Dimensions?,
        spaceMeasured: String?,
        spaceScanned: Boolean,
        step: Int,
        steps: Int,
    ) {
        runCatching {
            withContext(Dispatchers.IO) {
                val body = JSONObject()
                    .put("reason", reason.key)
                    .put("space_scanned", spaceScanned)
                    .put("step", step)
                    .put("steps", steps.coerceAtLeast(1))
                item?.let { body.put("item_width_mm", it.widthMm).put("item_depth_mm", it.depthMm).put("item_height_mm", it.heightMm) }
                space?.let { body.put("space_width_mm", it.widthMm).put("space_depth_mm", it.depthMm).put("space_height_mm", it.heightMm) }
                spaceMeasured?.let { body.put("space_measured", it) }
                val token = account.accessToken()
                val connection = URL(BuildConfig.SUPABASE_URL.trimEnd('/') + "/rest/v1/fit_reports").openConnection() as HttpsURLConnection
                try {
                    connection.instanceFollowRedirects = false
                    connection.connectTimeout = 15_000
                    connection.readTimeout = 20_000
                    connection.requestMethod = "POST"
                    connection.setRequestProperty("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
                    connection.setRequestProperty("Authorization", "Bearer $token")
                    connection.setRequestProperty("Content-Type", "application/json")
                    // Reports can't be read back, so nothing is asked for in return.
                    connection.setRequestProperty("Prefer", "return=minimal")
                    connection.doOutput = true
                    connection.outputStream.use { it.write(body.toString().toByteArray()) }
                    connection.responseCode
                } finally {
                    connection.disconnect()
                }
            }
        }.onFailure { if (it is CancellationException) throw it }
    }
}
