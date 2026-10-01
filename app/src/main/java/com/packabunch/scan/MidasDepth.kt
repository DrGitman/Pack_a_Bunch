package com.packabunch.scan

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import com.packabunch.packing.PhotoEngine
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
        // MiDaS's own transform, shared with the computer test so both see the same pixels.
        val plane = SIZE * SIZE
        val input = FloatBuffer.wrap(PhotoEngine.midasInput(picture.toRaster(), SIZE))
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
