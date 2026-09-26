package com.packabunch.ar

import android.media.Image
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions

/**
 * Finding the objects, rather than inferring them from depth.
 *
 * ### Why this replaced voxel clustering
 *
 * The scan used to look for objects by clustering the depth grid: anything solid and
 * standing clear of the support surface was called an object. On a phone with a real depth
 * sensor that is reasonable. On one without, the depth is noisy enough that it reported nine
 * objects on a table holding two, put boxes over bare floor, and split a single mug into
 * several fragments — because a cluster of noise is indistinguishable from a small object
 * when noise is all the evidence you have.
 *
 * A trained detector does not have that problem. It is asked "what objects are in this
 * picture", and empty floor is not an answer it gives. Depth is then used for the one thing
 * it is genuinely good at — how far away and how big — inside a region something else has
 * already vouched for.
 *
 * ### The part that fixes identity
 *
 * `STREAM_MODE` assigns a tracking id that survives across frames, so walking around a mug
 * keeps measuring the same mug. The old tracker had to re-match blobs by position every pass
 * and lost objects whenever the phone moved.
 */
class ObjectFinder {

    private val detector = ObjectDetection.getClient(
        ObjectDetectorOptions.Builder()
            // Tracking ids only exist in stream mode, and identity is half the point.
            .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
            // Five at once is the API's ceiling. A sweep accumulates across frames anyway,
            // so this caps what is in view, not what a pack can hold.
            .enableMultipleObjects()
            // Classification is deliberately off. This detector's built-in classifier knows
            // five categories in total — home goods, fashion goods, food, plants, places —
            // so a mug comes back as "Home goods", which is no use as a name and costs
            // inference time to produce. Naming is done separately by running image
            // labelling on each object's crop, where the vocabulary is hundreds of labels
            // and a mug comes back as a mug.
            .build(),
    )

    /** True while a frame is in flight, so frames are dropped rather than queued. */
    private val busy = java.util.concurrent.atomic.AtomicBoolean(false)

    @Volatile
    var found: List<FoundObject> = emptyList()
        private set

    /**
     * Takes ownership of one camera frame and returns immediately.
     *
     * **The image must stay open until inference finishes.** ML Kit reads it asynchronously,
     * so closing it here — which a `use` block would do — hands the detector a closed buffer
     * and it silently returns nothing. ARCore also caps how many images may be held at once,
     * so a frame that is never closed stops `acquireCameraImage` working within a second or
     * two. Both failures are quiet, which is why this comment is longer than the code.
     *
     * Deliberately fire and forget otherwise: the render loop must not wait on inference, and
     * a queue of stale frames is worse than a dropped one, because by the time an old frame
     * is analysed the phone is pointing somewhere else.
     */
    fun offer(image: Image, rotationDegrees: Int, onUpdate: () -> Unit) {
        if (!busy.compareAndSet(false, true)) {
            image.close()
            return
        }
        val input = runCatching { InputImage.fromMediaImage(image, rotationDegrees) }.getOrNull()
        if (input == null) {
            image.close()
            busy.set(false)
            return
        }
        detector.process(input)
            .addOnSuccessListener { objects ->
                found = objects.mapNotNull { detected ->
                    val id = detected.trackingId ?: return@mapNotNull null
                    FoundObject(
                        trackingId = id,
                        left = detected.boundingBox.left.toFloat() / input.width,
                        top = detected.boundingBox.top.toFloat() / input.height,
                        right = detected.boundingBox.right.toFloat() / input.width,
                        bottom = detected.boundingBox.bottom.toFloat() / input.height,
                        // Filled in later, by labelling this box's crop.
                        label = null,
                    )
                }
                onUpdate()
            }
            .addOnFailureListener { android.util.Log.w("ObjectFinder", "detect failed", it) }
            .addOnCompleteListener {
                image.close()
                busy.set(false)
            }
    }

    fun close() = detector.close()
}

/**
 * One object the detector is confident about, in 0..1 image space.
 *
 * Normalised rather than in pixels so the overlay does not have to know the analysis
 * resolution, which is not the preview resolution.
 */
data class FoundObject(
    val trackingId: Int,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    /** ML Kit's coarse class, when it offered one. A starting point for the item's name. */
    val label: String?,
)

/**
 * Names each detected object by labelling its own crop.
 *
 * Labelling the whole frame gives one label for the scene, so a mug beside a carton produces
 * a single answer that is wrong for at least one of them. Cropping to each detector box first
 * means each object is labelled on its own evidence — and the crop is also the photograph
 * worth keeping against the item, so the same work serves both.
 *
 * Runs off the detection path on purpose. A name arriving a beat late is unnoticeable; a
 * dropped frame in the tracking loop is not.
 */
class ObjectNamer(private val recogniser: com.packabunch.data.catalogue.ItemRecogniser) {

    private val named = java.util.concurrent.ConcurrentHashMap<Int, String>()
    private val inFlight = java.util.Collections.synchronizedSet(mutableSetOf<Int>())

    fun nameFor(trackingId: Int): String? = named[trackingId]

    /** Seeds a name without running inference. Used by tests, and by a restored session. */
    internal fun remember(trackingId: Int, name: String) {
        named[trackingId] = name
    }

    /**
     * Labels anything not already named. Each tracking id is attempted once, because the
     * label will not improve on a second look and the inference is not free.
     */
    suspend fun name(frame: android.graphics.Bitmap, objects: List<FoundObject>) {
        for (item in objects) {
            if (named.containsKey(item.trackingId)) continue
            if (!inFlight.add(item.trackingId)) continue
            try {
                val crop = item.cropFrom(frame) ?: continue
                recogniser.recognise(crop).suggestedName()?.let { named[item.trackingId] = it }
            } finally {
                inFlight.remove(item.trackingId)
            }
        }
    }

    fun clear() {
        named.clear()
        inFlight.clear()
    }
}

/** This object's own pixels, with a small margin so an edge-tight box keeps some context. */
internal fun FoundObject.cropFrom(frame: android.graphics.Bitmap): android.graphics.Bitmap? {
    val margin = 0.06f
    val x = ((left - margin) * frame.width).toInt().coerceIn(0, frame.width - 1)
    val y = ((top - margin) * frame.height).toInt().coerceIn(0, frame.height - 1)
    val w = ((right - left + margin * 2) * frame.width).toInt().coerceAtMost(frame.width - x)
    val h = ((bottom - top + margin * 2) * frame.height).toInt().coerceAtMost(frame.height - y)
    // Anything this small is not worth labelling and crashes the crop.
    if (w < 24 || h < 24) return null
    return runCatching { android.graphics.Bitmap.createBitmap(frame, x, y, w, h) }.getOrNull()
}

/**
 * A camera frame as a Bitmap, for the labeller.
 *
 * ARCore hands over YUV_420_888, which nothing in the image stack takes directly. Going via
 * JPEG is not the fastest route, but this runs about once a second off the render thread
 * rather than per frame, and it avoids hand-rolling a colour conversion that would be one
 * more thing to get subtly wrong.
 */
internal fun Image.toBitmap(rotationDegrees: Int): android.graphics.Bitmap? = runCatching {
    val yBuffer = planes[0].buffer
    val uBuffer = planes[1].buffer
    val vBuffer = planes[2].buffer
    val bytes = ByteArray(yBuffer.remaining() + uBuffer.remaining() + vBuffer.remaining())
    yBuffer.get(bytes, 0, yBuffer.remaining())
    val afterY = bytes.size - uBuffer.remaining() - vBuffer.remaining()
    vBuffer.get(bytes, afterY, vBuffer.remaining())
    uBuffer.get(bytes, afterY + vBuffer.remaining(), uBuffer.remaining())

    val out = java.io.ByteArrayOutputStream()
    android.graphics.YuvImage(bytes, android.graphics.ImageFormat.NV21, width, height, null)
        .compressToJpeg(android.graphics.Rect(0, 0, width, height), 80, out)
    val flat = android.graphics.BitmapFactory.decodeByteArray(out.toByteArray(), 0, out.size())
        ?: return@runCatching null

    if (rotationDegrees == 0) return@runCatching flat
    val matrix = android.graphics.Matrix().apply { postRotate(rotationDegrees.toFloat()) }
    android.graphics.Bitmap.createBitmap(flat, 0, 0, flat.width, flat.height, matrix, true)
}.getOrNull()
