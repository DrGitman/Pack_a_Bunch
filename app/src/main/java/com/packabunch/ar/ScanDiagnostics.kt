package com.packabunch.ar

import android.os.Build
import android.os.SystemClock
import android.util.Log
import org.json.JSONObject
import java.util.UUID

/** Local logcat only: no pictures, coordinates, item names, or account information. */
internal class ScanDiagnostics(private val scanner: String) {
    private val run = UUID.randomUUID().toString()
    private var lastLogMs = -2000L

    @Synchronized fun record(stage: String, details: String = "") {
        val now = SystemClock.elapsedRealtime()
        if (now - lastLogMs < 2000L) return
        lastLogMs = now
        Log.i(AR_TAG, JSONObject().put("event", "scan_pipeline")
            .put("scanner", scanner).put("run", run).put("stage", stage)
            .put("device", "${Build.MANUFACTURER} ${Build.MODEL}")
            .put("details", details).toString())
    }
}
