package com.packabunch.data.cloud

import com.packabunch.BuildConfig
import com.packabunch.auth.SupabaseAccount
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/** What the account remembers about how somebody measures. Null means "never saved". */
data class RemoteSettings(val unit: String, val habit: String?)

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

    suspend fun load(): RemoteSettings? = runCatching {
        withContext(Dispatchers.IO) {
            val rows = JSONArray(request("/rest/v1/user_settings?select=unit,habit&user_id=eq.$owner"))
            if (rows.length() == 0) return@withContext null
            val row = rows.getJSONObject(0)
            RemoteSettings(row.getString("unit"), row.optString("habit").takeIf { it.isNotBlank() })
        }
    }.getOrNull()

    suspend fun save(unit: String, habit: String?) {
        runCatching {
            withContext(Dispatchers.IO) {
                request(
                    path = "/rest/v1/user_settings?on_conflict=user_id",
                    body = JSONObject()
                        .put("user_id", owner)
                        .put("unit", unit)
                        .put("habit", habit ?: JSONObject.NULL)
                        .put("updated_at", java.time.Instant.now().toString()),
                    // Upsert: the row is created the first time and replaced after that.
                    prefer = "resolution=merge-duplicates,return=minimal",
                )
            }
        }
    }

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
