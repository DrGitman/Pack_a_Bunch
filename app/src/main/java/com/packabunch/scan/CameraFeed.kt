package com.packabunch.scan

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.hardware.camera2.CameraCharacteristics
import android.util.Log
import android.util.Size
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.packabunch.packing.PlanarPose
import java.util.concurrent.Executors

/** One camera picture for the scan, in the sensor's own orientation, with its lens geometry. */
class CameraFrame(
    /** The picture as the sensor sees it (not turned upright). */
    val picture: Bitmap,
    /** Degrees to turn [picture] clockwise to make it upright on screen. */
    val rotationDegrees: Int,
    val timestampNs: Long,
    /** Focal length and centre in [picture]'s pixels. */
    val intrinsics: PlanarPose.Intrinsics,
)

/**
 * The phone's back camera through CameraX: a live preview for the person and, alongside it,
 * pictures for the measuring engine.
 *
 * Focal length comes from the camera itself — Camera2's own calibration where the phone has it,
 * otherwise its focal length and sensor size — scaled to the picture the engine is given. The
 * person never types a camera number in, and it is never guessed from a phone model.
 */
class CameraFeed(private val context: Context) {

    private var camera: Camera? = null
    private var provider: ProcessCameraProvider? = null
    private val analysisThread = Executors.newSingleThreadExecutor { r -> Thread(r, "scan-camera") }

    /** Starts preview into [preview] and hands frames to [onFrame], one at a time; slow consumers drop frames. */
    fun start(owner: LifecycleOwner, preview: PreviewView, onFrame: (CameraFrame) -> Unit, onError: (String) -> Unit) {
        // A scan is a minute of holding the phone still; the screen must not time out halfway.
        preview.keepScreenOn = true
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                val p = future.get(); provider = p
                // 4:3 for both, so the preview shows the same picture the engine measures.
                val selector = ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                    .setResolutionStrategy(ResolutionStrategy(ANALYSIS_SIZE, ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                    .build()
                val previewUse = Preview.Builder()
                    .setResolutionSelector(ResolutionSelector.Builder().setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY).build())
                    .build().also { it.surfaceProvider = preview.surfaceProvider }
                val analysis = ImageAnalysis.Builder()
                    .setResolutionSelector(selector)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()
                analysis.setAnalyzer(analysisThread) { proxy -> deliver(proxy, onFrame) }
                p.unbindAll()
                camera = p.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, previewUse, analysis)
            } catch (t: Throwable) {
                Log.e(SCAN_TAG, "camera start", t)
                onError("The camera couldn't start. Another app may be using it.")
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun setTorch(on: Boolean): Boolean {
        val c = camera ?: return false
        if (c.cameraInfo.hasFlashUnit().not()) return false
        c.cameraControl.enableTorch(on)
        return true
    }

    val hasTorch: Boolean get() = camera?.cameraInfo?.hasFlashUnit() == true

    fun stop() {
        runCatching { provider?.unbindAll() }
        camera = null
    }

    fun release() {
        stop()
        analysisThread.shutdownNow()
    }

    private fun deliver(proxy: ImageProxy, onFrame: (CameraFrame) -> Unit) {
        try {
            val bitmap = proxy.toBitmap()
            val k = intrinsicsFor(bitmap.width, bitmap.height) ?: return
            onFrame(CameraFrame(bitmap, proxy.imageInfo.rotationDegrees, proxy.imageInfo.timestamp, k))
        } catch (t: Throwable) {
            Log.w(SCAN_TAG, "camera frame", t)
        } finally {
            proxy.close()
        }
    }

    private var cached: Triple<Int, Int, PlanarPose.Intrinsics>? = null

    @SuppressLint("UnsafeOptInUsageError")
    @OptIn(ExperimentalCamera2Interop::class)
    private fun intrinsicsFor(width: Int, height: Int): PlanarPose.Intrinsics? {
        cached?.let { (w, h, k) -> if (w == width && h == height) return k }
        val info = camera?.cameraInfo ?: return null
        val c2 = Camera2CameraInfo.from(info)
        val active = c2.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE) ?: return null
        // The picture is the active array scaled to fit and centre-cropped to its shape.
        val scale = maxOf(width.toDouble() / active.width(), height.toDouble() / active.height())
        val offX = (active.width() * scale - width) / 2; val offY = (active.height() * scale - height) / 2
        val calibration = c2.getCameraCharacteristic(CameraCharacteristics.LENS_INTRINSIC_CALIBRATION)
        val k = if (calibration != null && calibration[0] > 0f && calibration[1] > 0f) {
            // fx, fy, cx, cy, skew in pixels of the pre-correction array; near enough to the active one.
            PlanarPose.Intrinsics(
                calibration[0] * scale, calibration[1] * scale,
                (calibration[2] - active.left) * scale - offX, (calibration[3] - active.top) * scale - offY,
            )
        } else {
            val focal = c2.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull() ?: return null
            val sensor = c2.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE) ?: return null
            val pixels = c2.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE) ?: return null
            // Millimetres to pixels of the full pixel array, then to the active array, then to the picture.
            val fx = focal / sensor.width * pixels.width * scale
            val fy = focal / sensor.height * pixels.height * scale
            PlanarPose.Intrinsics(fx, fy, width / 2.0, height / 2.0)
        }
        cached = Triple(width, height, k)
        Log.i(SCAN_TAG, "intrinsics ${width}x$height fx=%.1f fy=%.1f cx=%.1f cy=%.1f (%s)".format(k.fx, k.fy, k.cx, k.cy,
            if (calibration != null) "calibrated" else "from focal length"))
        return k
    }

    companion object {
        /**
         * The engine's picture: big enough that a bank card at arm's length spans well over a hundred
         * pixels (its edges set the scale for everything), small enough to keep several frames a second.
         */
        val ANALYSIS_SIZE = Size(1280, 960)
    }
}

internal const val SCAN_TAG = "PackScan"

/** This frame turned upright, with its lens geometry turned with it, ready to measure. */
fun CameraFrame.toPhotoInput(up: DoubleArray?): PhotoInput {
    val r = rotationDegrees; val k = intrinsics
    val w = picture.width.toDouble(); val h = picture.height.toDouble()
    val bmp = if (r == 0) picture else Bitmap.createBitmap(picture, 0, 0, picture.width, picture.height,
        android.graphics.Matrix().apply { postRotate(r.toFloat()) }, true)
    val kk = when (r) {
        90 -> PlanarPose.Intrinsics(k.fy, k.fx, h - k.cy, k.cx)
        180 -> PlanarPose.Intrinsics(k.fx, k.fy, w - k.cx, h - k.cy)
        270 -> PlanarPose.Intrinsics(k.fy, k.fx, k.cy, w - k.cx)
        else -> k
    }
    return PhotoInput(bmp, kk, up)
}

/**
 * A photo from the gallery, upright and no larger than 1600 px, with its focal length from the
 * photo's own details (35 mm equivalent) or a typical phone lens's when it has none.
 */
suspend fun photoFromGallery(context: Context, uri: android.net.Uri): PhotoInput? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
    runCatching {
        val resolver = context.contentResolver
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1600) sample *= 2
        val raw = resolver.openInputStream(uri)?.use {
            android.graphics.BitmapFactory.decodeStream(it, null, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return@runCatching null
        val exif = resolver.openInputStream(uri)?.use { android.media.ExifInterface(it) }
        val turn = when (exif?.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1)) {
            android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        val bmp = if (turn == 0f) raw else Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, android.graphics.Matrix().apply { postRotate(turn) }, true)
        val f35 = exif?.getAttributeInt(android.media.ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM, 0)?.takeIf { it > 0 } ?: 26
        val f = f35 * kotlin.math.hypot(bmp.width.toDouble(), bmp.height.toDouble()) / 43.27
        PhotoInput(bmp, PlanarPose.Intrinsics(f, f, bmp.width / 2.0, bmp.height / 2.0), null)
    }.getOrNull()
}
