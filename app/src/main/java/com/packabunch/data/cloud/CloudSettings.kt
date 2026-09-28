package com.packabunch.data.cloud

import com.packabunch.BuildConfig
import com.packabunch.auth.SupabaseAccount
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/** What the account remembers about how somebody measures. Null means "never saved". */
data class RemoteSettings(val unit: String, val habit: String?, val avatarUrl: String?)

/**
 * The measuring preferences, kept against the account rather than the phone.
 *
 * The rule the rest of the app relies on: **an account that has settings wins.** The choices
 * made before signing in are only a guess for a brand new account, and must never overwrite
 * what somebody already set on another phone.
 *
 * Every call is best effort. Failing to reach Supabase leaves the local preferences alone,
 * because measuring in centimetres offline is better than an error nobody can act on.
 */
class CloudSettings(private val account: SupabaseAccount, private val owner: String) {

    /**
     * The account's settings: a success holding null when it has never saved any, a failure
     * when they could not be read. The two must not be confused — a failed read taken for
     * "never saved" once pushed a new phone's blanks over the account, profile photo and all.
     */
    suspend fun load(): Result<RemoteSettings?> = runCatching {
        withContext(Dispatchers.IO) {
            val rows = JSONArray(request("/rest/v1/user_settings?select=unit,habit,avatar_url&user_id=eq.$owner"))
            if (rows.length() == 0) return@withContext null
            val row = rows.getJSONObject(0)
            RemoteSettings(
                unit = row.getString("unit"),
                habit = row.optString("habit").takeIf { it.isNotBlank() },
                avatarUrl = row.optString("avatar_url").takeIf { it.isNotBlank() },
            )
        }
    }

    /** Saves go one at a time, in the order they were asked for, so an older one never lands last. */
    private val order = kotlinx.coroutines.sync.Mutex()

    /**
     * Saves the unit and habit — and never the photo. Only [saveAvatar] writes that, because a
     * new phone saves its setup choices before the account's settings have even loaded, and a
     * save that carried "no photo" erased the photo from the account for every phone.
     * The upsert merges: columns not sent keep what the account already has.
     */
    suspend fun save(unit: String, habit: String?) = order.withLock {
        runCatching {
            withContext(Dispatchers.IO) {
                request(
                    path = "/rest/v1/user_settings?on_conflict=user_id",
                    body = JSONObject()
                        .put("user_id", owner)
                        .put("unit", unit)
                        .put("habit", habit ?: JSONObject.NULL)
                        .put("updated_at", java.time.Instant.now().toString()),
                    prefer = "resolution=merge-duplicates,return=minimal",
                )
            }
        }
    }

    /** The photo alone: a new one, or null when the person removed it. */
    suspend fun saveAvatar(unit: String, avatarUrl: String?) = order.withLock {
        runCatching {
            withContext(Dispatchers.IO) {
                val photo = JSONObject()
                    .put("avatar_url", avatarUrl ?: JSONObject.NULL)
                    .put("updated_at", java.time.Instant.now().toString())
                // Change only the photo on the row the account already has…
                val updated = JSONArray(patch("/rest/v1/user_settings?user_id=eq.$owner", photo))
                // …and only when there is no row yet, make one, with this phone's unit.
                if (updated.length() == 0) request(
                    path = "/rest/v1/user_settings?on_conflict=user_id",
                    body = photo.put("user_id", owner).put("unit", unit),
                    prefer = "resolution=merge-duplicates,return=minimal",
                )
            }
        }
    }

    /** A PATCH, which HttpsURLConnection cannot send; returns the changed rows. */
    private suspend fun patch(path: String, body: JSONObject): String {
        check(account.userId == owner)
        val token = account.accessToken()
        val request = okhttp3.Request.Builder()
            .url(BuildConfig.SUPABASE_URL.trimEnd('/') + path)
            .header("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            .header("Authorization", "Bearer $token")
            .header("Prefer", "return=representation")
            .patch(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        http.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "Settings HTTP ${response.code}" }
            return response.body?.string().orEmpty().ifBlank { "[]" }
        }
    }

    private val http = okhttp3.OkHttpClient()

    private suspend fun request(path: String, body: JSONObject? = null, prefer: String? = null): String {
        check(account.userId == owner)
        val token = account.accessToken()
        val connection = URL(BuildConfig.SUPABASE_URL.trimEnd('/') + path).openConnection() as HttpsURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
connection.requestMethod = if (body == null) "GET" else "POST"
            connection.setRequestProperty("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Content-Type", "application/json")
            if (prefer != null) connection.setRequestProperty("Prefer", prefer)
            if (body != null) {
                connection.doOutput = true
                connection.outputStream.use { it.write(body.toString().toByteArray()) }
            }
            check(connection.responseCode in 200..299) { "Settings HTTP ${connection.responseCode}" }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } catch (e: CancellationException) {
            throw e
        } finally {
            connection.disconnect()
        }
    }
}
