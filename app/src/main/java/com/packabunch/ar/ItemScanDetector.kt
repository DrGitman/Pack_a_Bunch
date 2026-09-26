package com.packabunch.ar

import android.media.Image
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import com.packabunch.packing.SensorBox
import java.util.concurrent.atomic.AtomicBoolean

/**
 * One detector box, in both orientations the item scan needs.
 *
 * [sensor] is `[left, top, right, bottom]` in 0..1 of the camera sensor's image, which is what
 * the depth image lines up with. [upright] is the same box in 0..1 of the upright picture,
 * which is what the photo crop is cut from.
 */
class ScanBox(
    val trackingId: Int?,
    val sensor: FloatArray,
    val upright: FloatArray,
    /** The camera frame the box was found in, so a stale box can be recognised. */
    val frameTimestampNs: Long,
) {
    val area: Float get() = (sensor[2] - sensor[0]) * (sensor[3] - sensor[1])
}

/**
 * ML Kit object detection for the item scan.
 *
 * A separate class from [ObjectFinder] on purpose — that one belongs to the space scan and is
 * being changed independently. Two things differ here:
 *
 * 1. **Orientation.** ML Kit answers in upright pixels of the *rotated* image. Those are
 *    normalised by the rotated width and height and turned back into sensor coordinates
 *    ([SensorBox]) before anything is sampled from the depth image, which is sensor-oriented.
 * 2. **Untracked boxes are kept.** A box without a tracking id still has depth behind it; the
 *    item tracker matches it by position instead.
 *
 * Frames are dropped, never queued: a stale box is worse than none.
 */
class ItemScanDetector {

    private val detector = ObjectDetection.getClient(
        ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
            .enableMultipleObjects()
            .build(),
    )

    private val busy = AtomicBoolean(false)

    @Volatile
    var latest: List<ScanBox> = emptyList()
        private set

    /** Takes ownership of [image] and closes it once ML Kit is done with it. */
    fun offer(image: Image, rotationDegrees: Int, timestampNs: Long) {
        if (!busy.compareAndSet(false, true)) { image.close(); return }
        val sensorW = image.width
        val sensorH = image.height
        val input = runCatching { InputImage.fromMediaImage(image, rotationDegrees) }.getOrNull()
        if (input == null) { image.close(); busy.set(false); return }
        val swapped = rotationDegrees == 90 || rotationDegrees == 270
        val uprightW = (if (swapped) sensorH else sensorW).toFloat()
        val uprightH = (if (swapped) sensorW else sensorH).toFloat()
        detector.process(input)
            .addOnSuccessListener { objects ->
                latest = objects.map { o ->
                    val b = o.boundingBox
                    ScanBox(
                        trackingId = o.trackingId,
                        sensor = SensorBox.uprightToSensor(
                            b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat(),
                            sensorW, sensorH, rotationDegrees,
                        ),
                        upright = floatArrayOf(
                            (b.left / uprightW).coerceIn(0f, 1f), (b.top / uprightH).coerceIn(0f, 1f),
                            (b.right / uprightW).coerceIn(0f, 1f), (b.bottom / uprightH).coerceIn(0f, 1f),
                        ),
                        frameTimestampNs = timestampNs,
                    )
                }
            }
            .addOnFailureListener { android.util.Log.w(AR_TAG, "item detect failed", it) }
            .addOnCompleteListener {
                image.close()
                busy.set(false)
            }
    }

    fun clear() { latest = emptyList() }

    fun close() = detector.close()
}
