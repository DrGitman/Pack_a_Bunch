package com.packabunch.data.cloud

import android.content.SharedPreferences
import com.packabunch.BuildConfig
import com.packabunch.auth.SupabaseAccount
import com.packabunch.data.Project
import com.packabunch.data.ProjectRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import java.security.MessageDigest
import javax.net.ssl.HttpsURLConnection

data class CloudSyncState(val running: Boolean=false, val backedUp: Boolean=false,
    val message: String="Waiting to sync", val conflicts: Int=0)

/** Room stays usable offline; failed requests never mark a local revision as backed up. */
class CloudPackSync(private val repository: ProjectRepository, private val account: SupabaseAccount,
    private val owner: String, private val preferences: SharedPreferences) {
    private val mutex=Mutex()
    private val _state=MutableStateFlow(CloudSyncState())
    val state=_state.asStateFlow()
    fun localChanged() { _state.value=_state.value.copy(backedUp=false,message="Changes waiting to sync") }
    private fun fingerprint(p: Project?): String = if(p==null) "deleted" else MessageDigest.getInstance("SHA-256")
        .digest(CloudPackCodec.encode(p.copy(updatedAtMillis=0),owner).toString().toByteArray()).joinToString("") { "%02x".format(it) }
    private fun remember(id: String, version: Long, fingerprint: String) {
        check(preferences.edit().putLong("v:$id",version).putString("f:$id",fingerprint).commit())
    }

    suspend fun sync() = mutex.withLock { withContext(Dispatchers.IO) {
        if(account.userId!=owner) return@withContext
        _state.value=_state.value.copy(running=true,backedUp=false,message="Syncing your packs")
        try {
            var conflicts=0
            val remote=linkedMapOf<String,JSONObject>()
            var offset=0
            do {
                val page=JSONArray(request("/rest/v1/packs?select=id,client_id,row_version,deleted_at&order=id&limit=100&offset=$offset"))
                repeat(page.length()) { i -> val row=page.getJSONObject(i); remote[row.getString("client_id")]=row }
                offset+=page.length()
            } while(page.length()==100)
            val local=repository.snapshot().associateBy { it.id }
            val known=preferences.all.keys.filter { it.startsWith("v:") }.map { it.removePrefix("v:") }
            for(id in (local.keys+remote.keys+known).sorted()) {
                val before=repository.project(id)
                val hash=fingerprint(before)
                val baseline=preferences.getString("f:$id",null)
                val expected=preferences.getLong("v:$id",0)
                val r=remote[id]
                val rv=r?.getLong("row_version") ?: 0
                val cid=CloudPackCodec.cloudId(owner,id)
                if(r!=null && rv!=expected) {
                    val incoming=if(r.isNull("deleted_at")) CloudPackCodec.decode(JSONObject(request("/rest/v1/rpc/read_pack",JSONObject().put("p_id",cid)))) else null
                    val remoteHash=fingerprint(incoming)
                    val conflict=hash!=baseline && hash!=remoteHash && before!=null
                    // Compare under the repository lock: edits made while fetching are left for the next pass.
                    val applied=repository.replaceFromCloud(id,before,incoming,conflict) { fingerprint(it)==hash }
                    if(!applied) continue
                    if(conflict) conflicts++
                    remember(id,rv,remoteHash)
                } else if(hash!=baseline && (before!=null || expected>0)) {
                    val body=JSONObject().put("p_id",cid).put("p_expected",expected)
                        .put("p_deleted",before==null).put("p_document",before?.let { CloudPackCodec.encode(it,owner) } ?: JSONObject())
                    val version=request("/rest/v1/rpc/write_pack",body).trim().toLong()
                    remember(id,version,hash)
                }
            }
            // A concurrent edit or a preserved conflict copy still needs an upload.
            val current=repository.snapshot()
            val pending=current.any { fingerprint(it)!=preferences.getString("f:${it.id}",null) } ||
                preferences.all.keys.filter { it.startsWith("f:") }.any { key ->
                    val id=key.removePrefix("f:"); current.none { it.id==id } && preferences.getString(key,null)!="deleted"
                }
            _state.value=CloudSyncState(false,!pending,when {
                conflicts>0 -> "Both versions kept. Look for packs named Conflict copy."
                pending -> "Changes waiting to sync"
                else -> "Your packs are backed up to your account."
            },conflicts)
        } catch(e: CancellationException) { throw e }
        catch(_: Exception) { _state.value=CloudSyncState(message="Couldn't sync. Your packs are safe on this phone; retry when connected.") }
    } }

    private suspend fun request(path: String, body: JSONObject?=null): String {
        check(account.userId==owner)
        val token=account.accessToken()
        val c=URL(BuildConfig.SUPABASE_URL.trimEnd('/')+path).openConnection() as HttpsURLConnection
        try {
            c.instanceFollowRedirects=false; c.connectTimeout=15000; c.readTimeout=30000
            c.requestMethod=if(body==null) "GET" else "POST"
            c.setRequestProperty("apikey",BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            c.setRequestProperty("Authorization","Bearer $token")
            c.setRequestProperty("Content-Type","application/json")
            if(body!=null) { c.doOutput=true; c.outputStream.use { it.write(body.toString().toByteArray()) } }
            check(c.responseCode in 200..299) { "Sync HTTP ${c.responseCode}" }
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally { c.disconnect() }
    }
}
