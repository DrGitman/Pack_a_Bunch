package com.packabunch.scan

import android.graphics.Bitmap
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import com.packabunch.packing.SensorBox
import java.util.concurrent.TimeUnit

/**
 * One detector box, in both orientations the item scan needs.
 *
 * [sensor] is `[left, top, right, bottom]` in 0..1 of the camera picture as the sensor sees it,
 * which is what the depth lines up with. [upright] is the same box in 0..1 of the upright
 * picture, which is what the photo crop is cut from.
 */
class ScanBox(
    val trackingId: Int?,
    val sensor: FloatArray,
    val upright: FloatArray,
    /** The camera frame the box was found in, so a stale box can be recognised. */
    val frameTimestampNs: Long,
    /** Width and height of the face seen from here, `w·d/f` (see DepthEdgeObjects), if known. */
    val faceMm: FloatArray? = null,
) {
    val area: Float get() = (sensor[2] - sensor[0]) * (sensor[3] - sensor[1])
}

/**
 * ML Kit object detection for the item scan, run on the camera's own thread.
 *
 * ML Kit answers in upright pixels of the turned picture; those are normalised by the turned
 * width and height and taken back to sensor coordinates ([SensorBox]) before anything is sampled
 * from the depth, which is sensor-oriented. Boxes without a tracking id are kept: the item
 * tracker matches them by position.
 */
class ItemScanDetector(stream: Boolean = true) {

    // Live video uses stream mode; a single photo uses single-image mode, which looks harder and
    // reports every object it can, not only the steadiest one.
    private val detector = ObjectDetection.getClient(
        ObjectDetectorOptions.Builder()
            .setDetectorMode(if (stream) ObjectDetectorOptions.STREAM_MODE else ObjectDetectorOptions.SINGLE_IMAGE_MODE)
            .enableMultipleObjects()
            .build(),
    )

    @Volatile var failed: Boolean = false
        private set

    /** Boxes in [picture] (sensor orientation). Blocks briefly; call off the main thread. */
    fun detect(picture: Bitmap, rotationDegrees: Int, timestampNs: Long): List<ScanBox> {
        val sensorW = picture.width; val sensorH = picture.height
        val swapped = rotationDegrees == 90 || rotationDegrees == 270
        val uprightW = (if (swapped) sensorH else sensorW).toFloat()
        val uprightH = (if (swapped) sensorW else sensorH).toFloat()
        return try {
            val objects = Tasks.await(detector.process(InputImage.fromBitmap(picture, rotationDegrees)), 2, TimeUnit.SECONDS)
            failed = false
            objects.map { o ->
                val b = o.boundingBox
                ScanBox(
                    trackingId = o.trackingId,
                    sensor = SensorBox.uprightToSensor(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat(),
                        sensorW, sensorH, rotationDegrees),
                    upright = floatArrayOf(
                        (b.left / uprightW).coerceIn(0f, 1f), (b.top / uprightH).coerceIn(0f, 1f),
                        (b.right / uprightW).coerceIn(0f, 1f), (b.bottom / uprightH).coerceIn(0f, 1f),
                    ),
                    frameTimestampNs = timestampNs,
                )
            }
        } catch (e: Exception) {
            failed = true
            android.util.Log.w(SCAN_TAG, "item detect", e)
            emptyList()
        }
    }

    fun close() = detector.close()
}
