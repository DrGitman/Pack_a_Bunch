package com.packabunch.scan

import android.os.Build
import android.os.SystemClock
import android.util.Log
import org.json.JSONObject
import java.util.UUID

/** Local logcat only: no pictures, coordinates, item names, or account information. */
internal class ScanDiagnostics(private val scanner: String) {
    private val run = UUID.randomUUID().toString()
    /** Each stage is throttled on its own, so a chatty stage never hides a quiet one. */
    private val lastLogMs = HashMap<String, Long>()

    @Synchronized fun record(stage: String, details: String = "") {
        val now = SystemClock.elapsedRealtime()
        if (now - (lastLogMs[stage] ?: -2000L) < 1000L) return
        lastLogMs[stage] = now
        Log.i(SCAN_TAG, JSONObject().put("event", "scan_pipeline")
            .put("scanner", scanner).put("run", run).put("stage", stage)
            .put("device", "${Build.MANUFACTURER} ${Build.MODEL}")
            .put("details", details).toString())
    }
}
