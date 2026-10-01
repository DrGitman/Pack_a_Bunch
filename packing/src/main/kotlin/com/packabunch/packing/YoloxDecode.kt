package com.packabunch.packing

import kotlin.math.exp

/**
 * Turns YOLOX's raw output into named boxes.
 *
 * YOLOX (Megvii, Apache-2.0) is a YOLO-family detector trained on COCO's 80 classes — the
 * vocabulary the Pysource "identify any object" tutorials use. COCO has `cup`, `bottle`, `bowl`,
 * `book`, `suitcase`; it has no `wheel`, which is what ML Kit's generic labeller kept calling a
 * food container. Kept free of Android so it can be checked off the phone.
 *
 * The released ONNX models return, per anchor, `[x, y, w, h, objectness, 80 class scores]` with
 * objectness and classes already through a sigmoid and the box still relative to its grid cell.
 * Anchors run over the stride-8 grid, then 16, then 32, each row by row.
 */
object YoloxDecode {

    const val INPUT = 416
    private val STRIDES = intArrayOf(8, 16, 32)
    private const val VALUES = 85

    /** A named box, `[left, top, right, bottom]` in 0..1 of the picture that was detected on. */
    class Detection(val label: String, val score: Float, val box: FloatArray)

    /** Scale that fits a picture into the square input, as YOLOX's own preprocessing does. */
    fun ratio(width: Int, height: Int) = minOf(INPUT.toFloat() / width, INPUT.toFloat() / height)

    fun decode(
        output: FloatArray, width: Int, height: Int,
        minScore: Float = MIN_SCORE, maxOverlap: Float = MAX_OVERLAP,
    ): List<Detection> {
        val r = ratio(width, height)
        val raw = ArrayList<Detection>()
        var anchor = 0
        for (stride in STRIDES) {
            val cells = INPUT / stride
            for (gy in 0 until cells) for (gx in 0 until cells) {
                val o = anchor++ * VALUES
                if (o + VALUES > output.size) break
                var best = 0; var bestScore = 0f
                for (c in 0 until 80) {
                    val sc = output[o + 5 + c]
                    if (sc > bestScore) { bestScore = sc; best = c }
                }
                val score = output[o + 4] * bestScore
                if (score < minScore) continue
                val label = COCO[best]
                if (label in NOT_ITEMS) continue
                val cx = (output[o] + gx) * stride; val cy = (output[o + 1] + gy) * stride
                val w = exp(output[o + 2]) * stride; val h = exp(output[o + 3]) * stride
                raw += Detection(label, score, floatArrayOf(
                    ((cx - w / 2) / r / width).coerceIn(0f, 1f), ((cy - h / 2) / r / height).coerceIn(0f, 1f),
                    ((cx + w / 2) / r / width).coerceIn(0f, 1f), ((cy + h / 2) / r / height).coerceIn(0f, 1f),
                ))
            }
        }
        // Non-maximum suppression, per class.
        val kept = ArrayList<Detection>()
        for (d in raw.sortedByDescending { it.score }) {
            if (kept.none { it.label == d.label && StandingObjects.overlap(it.box, d.box) > maxOverlap }) kept += d
        }
        return kept
    }

    /** Friendly item name for a COCO class. */
    fun itemName(label: String): String = FRIENDLY[label] ?: label.replaceFirstChar { it.uppercase() }

    const val MIN_SCORE = 0.35f
    const val MAX_OVERLAP = 0.45f

    val COCO = arrayOf(
        "person", "bicycle", "car", "motorcycle", "airplane", "bus", "train", "truck", "boat", "traffic light",
        "fire hydrant", "stop sign", "parking meter", "bench", "bird", "cat", "dog", "horse", "sheep", "cow",
        "elephant", "bear", "zebra", "giraffe", "backpack", "umbrella", "handbag", "tie", "suitcase", "frisbee",
        "skis", "snowboard", "sports ball", "kite", "baseball bat", "baseball glove", "skateboard", "surfboard",
        "tennis racket", "bottle", "wine glass", "cup", "fork", "knife", "spoon", "bowl", "banana", "apple",
        "sandwich", "orange", "broccoli", "carrot", "hot dog", "pizza", "donut", "cake", "chair", "couch",
        "potted plant", "bed", "dining table", "toilet", "tv", "laptop", "mouse", "remote", "keyboard", "cell phone",
        "microwave", "oven", "toaster", "sink", "refrigerator", "book", "clock", "vase", "scissors", "teddy bear",
        "hair drier", "toothbrush",
    )

    /**
     * Classes that are never an item to pack: people, animals, vehicles, street furniture, and
     * the table the items stand on.
     */
    val NOT_ITEMS = setOf(
        "person", "car", "motorcycle", "airplane", "bus", "train", "truck", "boat", "traffic light",
        "fire hydrant", "stop sign", "parking meter", "bird", "cat", "dog", "horse", "sheep", "cow",
        "elephant", "bear", "zebra", "giraffe", "dining table", "toilet", "sink",
    )

    private val FRIENDLY = mapOf(
        "tv" to "TV", "cell phone" to "Phone", "sports ball" to "Ball", "hair drier" to "Hair dryer",
        "potted plant" to "Plant", "wine glass" to "Glass", "remote" to "Remote control",
    )
}
