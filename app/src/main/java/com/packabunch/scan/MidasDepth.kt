package com.packabunch.scan

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import java.io.Closeable
import java.nio.FloatBuffer

/**
 * MiDaS v2.1 small: relative depth from a single photo, on the phone.
 *
 * The depth model at the heart of HarshdeepJ's Object_Volume_Detector (theirs is the large DPT
 * variant; this is the same authors' mobile one, MIT-licensed, bundled in the APK). It needs no
 * special sensor, no movement and no time to build up, and it gives an answer for every pixel of
 * an ordinary camera picture.
 *
 * Output is relative inverse depth, larger = nearer, no units; [com.packabunch.packing.MonoDepthScale]
 * turns it into millimetres.
 */
class MidasDepth(context: Context) : Closeable {

    private val env = OrtEnvironment.getEnvironment()
    private val session: OrtSession = env.createSession(
        context.assets.open(MODEL).use { it.readBytes() },
        OrtSession.SessionOptions().apply { setIntraOpNumThreads(2) },
    )
    private val inputName = session.inputNames.first()

    /** Relative inverse depth on a [SIZE]×[SIZE] grid stretched over the whole [picture]. */
    @Synchronized
    fun run(picture: Bitmap): FloatArray {
        val scaled = Bitmap.createScaledBitmap(picture, SIZE, SIZE, true)
        val pixels = IntArray(SIZE * SIZE)
        scaled.getPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE)
        if (scaled !== picture) scaled.recycle()

        // RGB, channels first, ImageNet normalisation — MiDaS's own transform.
        val plane = SIZE * SIZE
        val input = FloatBuffer.allocate(3 * plane)
        for (i in 0 until plane) {
            val p = pixels[i]
            input.put(i, (((p shr 16) and 0xFF) / 255f - 0.485f) / 0.229f)
            input.put(plane + i, (((p shr 8) and 0xFF) / 255f - 0.456f) / 0.224f)
            input.put(2 * plane + i, ((p and 0xFF) / 255f - 0.406f) / 0.225f)
        }
        OnnxTensor.createTensor(env, input, longArrayOf(1, 3, SIZE.toLong(), SIZE.toLong())).use { tensor ->
            session.run(mapOf(inputName to tensor)).use { result ->
                val out = (result[0] as OnnxTensor).floatBuffer
                return FloatArray(plane).also { out.get(it) }
            }
        }
    }

    override fun close() = session.close()

    companion object {
        const val SIZE = 256
        private const val MODEL = "midas_small.onnx"
    }
}
