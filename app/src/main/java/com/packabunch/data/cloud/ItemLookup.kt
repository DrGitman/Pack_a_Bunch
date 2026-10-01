package com.packabunch.data.cloud

import android.graphics.Bitmap
import android.util.Base64
import com.packabunch.BuildConfig
import com.packabunch.auth.SupabaseAccount
import com.packabunch.packing.Dimensions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

/** What the lookup said one item is, and its real size. */
class LookedUp(
    val name: String,
    /** Brand and model when the exact product was recognised; empty otherwise. */
    val product: String,
    /** True when [size] is the maker's, false when it is the usual size of that kind of thing. */
    val exact: Boolean,
    val confidence: Float,
    val size: Dimensions,
)

/**
 * Asks the `identify-item` Supabase function what one found item is and how big it really is.
 *
 * Only the item's own cut-out goes, shrunk to at most 512 pixels, never the whole photo; the
 * function passes it to Gemini and keeps nothing. Signed-in people only, and only with "Look up
 * items online" on in Settings. Any failure — offline, slow, not recognised — is just null, and
 * the photo's own measurement stands.
 */
class ItemLookup(private val account: SupabaseAccount) {

    suspend fun identify(crop: Bitmap, hint: String?, measured: Dimensions): LookedUp? = withContext(Dispatchers.IO) {
        runCatching {
            val scale = (MAX_SIDE.toFloat() / maxOf(crop.width, crop.height)).coerceAtMost(1f)
            val small = Bitmap.createScaledBitmap(crop, (crop.width * scale).toInt().coerceAtLeast(1), (crop.height * scale).toInt().coerceAtLeast(1), true)
            val jpeg = ByteArrayOutputStream().also { small.compress(Bitmap.CompressFormat.JPEG, 82, it) }.toByteArray()
            if (small !== crop) small.recycle()
            val body = JSONObject()
                .put("image", Base64.encodeToString(jpeg, Base64.NO_WRAP))
                .put("hint", hint.orEmpty())
                .put("measuredMm", JSONArray(listOf(measured.widthMm, measured.depthMm, measured.heightMm)))
            val request = okhttp3.Request.Builder()
                .url(BuildConfig.SUPABASE_URL.trimEnd('/') + "/functions/v1/identify-item")
                .header("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
                .header("Authorization", "Bearer ${account.accessToken()}")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching null
                val r = JSONObject(response.body?.string().orEmpty())
                if (!r.optBoolean("identified")) return@runCatching null
                LookedUp(
                    name = r.getString("name"), product = r.optString("product"), exact = r.optBoolean("exact"),
                    confidence = r.optDouble("confidence", 0.0).toFloat(),
                    size = Dimensions(r.getInt("widthMm"), r.getInt("depthMm"), r.getInt("heightMm")),
                )
            }
        }.getOrNull()
    }

    private companion object {
        const val MAX_SIDE = 512
        val http: okhttp3.OkHttpClient = okhttp3.OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build()
    }
}
