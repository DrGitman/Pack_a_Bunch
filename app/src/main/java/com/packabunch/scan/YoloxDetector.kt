package com.packabunch.scan

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import com.packabunch.packing.YoloxDecode
import java.io.Closeable
import java.nio.FloatBuffer

/**
 * YOLOX-Tiny on the phone, for naming what the scan found.
 *
 * The model ships inside the APK (assets/yolox_tiny.onnx, Apache-2.0) and runs through ONNX
 * Runtime, so it works offline and does not depend on Google Play to download anything — which
 * matters on phones where the Play Store is disabled. Detection is only for names: sizes come
 * from depth, never from a box a classifier drew.
 */
class YoloxDetector(context: Context) : Closeable {

    private val env = OrtEnvironment.getEnvironment()
    private val session: OrtSession = env.createSession(
        context.assets.open(MODEL).use { it.readBytes() },
        OrtSession.SessionOptions().apply { setIntraOpNumThreads(2) },
    )
    private val inputName = session.inputNames.first()

    /** Detections on an upright picture, boxes in 0..1 of it. */
    @Synchronized
    fun detect(picture: Bitmap, minScore: Float = YoloxDecode.MIN_SCORE): List<YoloxDecode.Detection> {
        val size = YoloxDecode.INPUT
        val r = YoloxDecode.ratio(picture.width, picture.height)
        // YOLOX's own preprocessing: scale to fit, top-left aligned, pad with grey 114.
        val canvas = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        Canvas(canvas).apply {
            drawColor(Color.rgb(114, 114, 114))
            val scaled = Bitmap.createScaledBitmap(
                picture, (picture.width * r).toInt().coerceAtLeast(1), (picture.height * r).toInt().coerceAtLeast(1), true,
            )
            drawBitmap(scaled, 0f, 0f, null)
            if (scaled !== picture) scaled.recycle()
        }
        val pixels = IntArray(size * size)
        canvas.getPixels(pixels, 0, size, 0, 0, size, size)
        canvas.recycle()

        // Channels first, BGR (the order it was trained in), 0..255 unnormalised.
        val plane = size * size
        val input = FloatBuffer.allocate(3 * plane)
        for (i in 0 until plane) {
            val p = pixels[i]
            input.put(i, (p and 0xFF).toFloat())                 // B
            input.put(plane + i, ((p shr 8) and 0xFF).toFloat())  // G
            input.put(2 * plane + i, ((p shr 16) and 0xFF).toFloat()) // R
        }
        OnnxTensor.createTensor(env, input, longArrayOf(1, 3, size.toLong(), size.toLong())).use { tensor ->
            session.run(mapOf(inputName to tensor)).use { result ->
                @Suppress("UNCHECKED_CAST")
                val out = (result[0].value as Array<Array<FloatArray>>)[0]
                val flat = FloatArray(out.size * out[0].size)
                for ((i, row) in out.withIndex()) row.copyInto(flat, i * row.size)
                return YoloxDecode.decode(flat, picture.width, picture.height, minScore)
            }
        }
    }

    override fun close() = session.close()

    private companion object {
        const val MODEL = "yolox_tiny.onnx"
    }
}
