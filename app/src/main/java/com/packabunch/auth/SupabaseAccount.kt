package com.packabunch.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.packabunch.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.security.MessageDigest
import java.util.UUID
import javax.net.ssl.HttpsURLConnection

/** Native Google ID token exchange. No Google client secret or database password in the app. */
class SupabaseAccount(context: Context) {
    private val vault = SessionVault(context.applicationContext)
    private val mutex = Mutex()
    private var session: JSONObject? = vault.read()?.let { runCatching { JSONObject(it) }.getOrNull() }
        ?.takeIf { it.optString("project_url") == BuildConfig.SUPABASE_URL.trimEnd('/') }
    val configured: Boolean get() = BuildConfig.SUPABASE_URL.startsWith("https://") &&
        BuildConfig.SUPABASE_PUBLISHABLE_KEY.isNotBlank()
    val email: String? get() = session?.optJSONObject("user")?.optString("email")?.takeIf { it.isNotBlank() }
    val userId: String? get() = session?.optJSONObject("user")?.optString("id")?.takeIf { it.isNotBlank() }
    val hasSession: Boolean get() = userId != null && session?.optString("refresh_token")?.isNotBlank() == true

    suspend fun signInWithPassword(email: String, password: String) = mutex.withLock {
        check(configured) { "Account service is not configured." }
        save(request("/auth/v1/token?grant_type=password",
            JSONObject().put("email", email.trim()).put("password", password)))
    }

    /** Confirmation-required signup does not grant access before Supabase issues a session. */
    suspend fun signUp(email: String, password: String): Boolean = mutex.withLock {
        check(configured) { "Account service is not configured." }
        val result = request("/auth/v1/signup",
            JSONObject().put("email", email.trim()).put("password", password))
        if (result.has("access_token") && !result.isNull("access_token")) { save(result); true } else false
    }

    suspend fun sendRecovery(email: String) {
        request("/auth/v1/recover", JSONObject().put("email", email.trim()))
    }

    suspend fun restore(): Boolean {
        if (!hasSession) return false
        return try { accessToken(); true } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { false }
    }

    suspend fun signIn(context: Context) = mutex.withLock {
        check(configured) { "Supabase setup is not complete for this build." }
        check(BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()) { "Google sign-in is not configured for this build. Use email and password." }
        val nonce = UUID.randomUUID().toString()
        val hashed = MessageDigest.getInstance("SHA-256").digest(nonce.toByteArray())
            .joinToString("") { "%02x".format(it) }
        val option = GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID)
            .setNonce(hashed).build()
        val credential = CredentialManager.create(context).getCredential(context,
            GetCredentialRequest.Builder().addCredentialOption(option).build()).credential
        require(credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            "Google did not return a sign-in credential."
        }
        val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
        val response = request("/auth/v1/token?grant_type=id_token",
            JSONObject().put("provider", "google").put("id_token", idToken).put("nonce", nonce))
        save(response)
    }

    /** Refresh is serialized so rotating refresh tokens cannot race on concurrent requests. */
    suspend fun accessToken(): String = mutex.withLock {
        val current = session ?: error("Sign in to your account first.")
        if (current.optLong("expires_at") <= System.currentTimeMillis() / 1000 + 60) {
            save(request("/auth/v1/token?grant_type=refresh_token",
                JSONObject().put("refresh_token", current.getString("refresh_token"))))
        }
        session!!.getString("access_token")
    }

    suspend fun signOut(context: Context) = mutex.withLock {
        // Local sign-out always locks the app, even when the server cannot be reached.
        val token = session?.optString("access_token")
        withContext(Dispatchers.IO) { vault.clear() }
        session = null
        try { if (token != null) request("/auth/v1/logout?scope=local", JSONObject(), token) }
        catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { }
        try { CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest()) }
        catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { }
    }

    private suspend fun save(response: JSONObject) {
        require(response.has("access_token") && response.has("refresh_token") && response.has("user")) {
            "The server did not return a valid account session."
        }
        if (!response.has("expires_at")) response.put("expires_at",
            System.currentTimeMillis() / 1000 + response.getLong("expires_in"))
        response.put("project_url", BuildConfig.SUPABASE_URL.trimEnd('/'))
        withContext(Dispatchers.IO) { vault.write(response.toString()) }
        session = response
    }

    private suspend fun request(path: String, body: JSONObject, token: String? = null): JSONObject =
        withContext(Dispatchers.IO) {
            val connection = URL(BuildConfig.SUPABASE_URL.trimEnd('/') + path).openConnection() as HttpsURLConnection
            try {
                connection.requestMethod = "POST"
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 15_000
                connection.readTimeout = 20_000
                connection.setRequestProperty("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
                connection.setRequestProperty("Content-Type", "application/json")
                if (token != null) connection.setRequestProperty("Authorization", "Bearer $token")
                connection.doOutput = true
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                val status = connection.responseCode
                if (status !in 200..299) {
                    // Never log response bodies or tokens. Show an actionable, non-sensitive error.
                    error(when (status) {
                        400, 401, 403, 422 -> if (path.contains("grant_type=id_token"))
                            "Google sign-in was rejected. Check the Google provider, client IDs and signing fingerprint."
                            else "Couldn't authenticate. Check your email and password, confirm your email if required, and try again."
                        429 -> "Too many attempts. Please wait and try again."
                        else -> "The account service is unavailable (HTTP $status). Try again."
                    })
                }
                val text = connection.inputStream.bufferedReader().use { it.readText() }
                if (text.isBlank()) JSONObject() else JSONObject(text)
            } finally { connection.disconnect() }
        }
}
