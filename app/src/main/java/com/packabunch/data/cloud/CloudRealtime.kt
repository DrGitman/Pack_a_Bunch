package com.packabunch.data.cloud

import com.packabunch.BuildConfig
import com.packabunch.auth.SupabaseAccount
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/**
 * Tells the app the moment one of this account's packs changes on the server, so sync runs
 * then — instead of the app asking the server every thirty seconds whether anything happened.
 *
 * Asking on a timer kept the radio waking up for as long as the app lived, in the background
 * included, and still showed a change from another phone up to half a minute late. This holds
 * one quiet Supabase Realtime connection open while the app is on screen, and nothing at all
 * when it is not.
 *
 * Only this account's rows arrive: Realtime applies the same row-level security as every
 * other read, using the person's own token. Any change to a pack, its items or its plan
 * stamps the pack row, so watching `packs` alone covers all of them. What arrives is only a
 * nudge — sync then fetches and merges exactly as it always does.
 */
class CloudRealtime(
    private val account: SupabaseAccount,
    private val onChange: () -> Unit,
) {
    private val client = OkHttpClient()
    private var scope: CoroutineScope? = null
    private var socket: WebSocket? = null
    private var ref = 0

    /** Opens the connection, reconnecting after failures, until [stop]. Safe to call twice. */
    fun start() {
        if (scope != null) return
        val s = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = s
        s.launch { connectLoop() }
    }

    fun stop() {
        scope?.cancel()
        scope = null
        socket?.close(1000, null)
        socket = null
    }

    private suspend fun CoroutineScope.connectLoop() {
        var wait = FIRST_RETRY_MS
        while (isActive) {
            val closed = CompletableDeferred<Unit>()
            val opened = CompletableDeferred<Boolean>()
            val token = runCatching { account.accessToken() }.getOrNull()
            if (token != null) {
                socket = client.newWebSocket(Request.Builder().url(address()).build(), Listener(token, opened, closed))
                val keepAlive = launch { keepAlive() }
                if (opened.await()) wait = FIRST_RETRY_MS
                closed.await()
                keepAlive.cancel()
            }
            // A dropped connection is retried with a growing pause: a phone out of signal
            // should not spend its battery reconnecting every second.
            delay(wait)
            wait = (wait * 2).coerceAtMost(MAX_RETRY_MS)
        }
    }

    /** The server closes a silent socket, and an hour-old token stops being accepted. */
    private suspend fun keepAlive() {
        var sinceToken = 0L
        while (true) {
            delay(HEARTBEAT_MS)
            send("phoenix", "heartbeat", JSONObject())
            sinceToken += HEARTBEAT_MS
            if (sinceToken >= TOKEN_REFRESH_MS) {
                sinceToken = 0
                runCatching { account.accessToken() }.getOrNull()?.let {
                    send(TOPIC, "access_token", JSONObject().put("access_token", it))
                }
            }
        }
    }

    private fun send(topic: String, event: String, payload: JSONObject) {
        ref++
        socket?.send(JSONObject().put("topic", topic).put("event", event).put("payload", payload).put("ref", ref.toString()).toString())
    }

    private fun address(): String =
        BuildConfig.SUPABASE_URL.trimEnd('/').replaceFirst("https://", "wss://") +
            "/realtime/v1/websocket?vsn=1.0.0&apikey=" + URLEncoder.encode(BuildConfig.SUPABASE_PUBLISHABLE_KEY, "UTF-8")

    private inner class Listener(
        private val token: String,
        private val opened: CompletableDeferred<Boolean>,
        private val closed: CompletableDeferred<Unit>,
    ) : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            val changes = JSONArray().put(JSONObject().put("event", "*").put("schema", "public").put("table", "packs"))
            send(TOPIC, "phx_join", JSONObject()
                .put("config", JSONObject().put("postgres_changes", changes))
                .put("access_token", token))
            opened.complete(true)
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val message = runCatching { JSONObject(text) }.getOrNull() ?: return
            if (message.optString("event") == "postgres_changes") onChange()
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(1000, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            opened.complete(false)
            closed.complete(Unit)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            opened.complete(false)
            closed.complete(Unit)
        }
    }

    private companion object {
        const val TOPIC = "realtime:packs"
        const val HEARTBEAT_MS = 25_000L
        const val TOKEN_REFRESH_MS = 10 * 60_000L
        const val FIRST_RETRY_MS = 2_000L
        const val MAX_RETRY_MS = 5 * 60_000L
    }
}
