package com.packabunch.packing

/** Where a photo's scale came from: the one number a photo leaves open (see [PhotoScale]). */
enum class ScaleSource { CARD, KNOWN_OBJECT, TYPICAL }

/** Where an item's size in the end came from, for the badge beside it. */
enum class SizeSource {
    /** Measured from the photo. */
    PHOTO,
    /** The item was recognised and this is its real (or usual) size. */
    LOOKED_UP,
    /** Measured from the photo, with the scale set by another item that was recognised. */
    PHOTO_SCALED_BY_LOOKUP,
}

/**
 * Puts the online lookup's answers together with what the photo measured.
 *
 * One recognised thing fixes everything else: every length in a photo grows or shrinks with the
 * one number the photo leaves open, so if the photo measured a mug as 25 cm long and the mug is
 * really 12 cm, every other thing in that photo is too big by the same factor. The outlines do
 * not move — scale changes nothing in the picture.
 */
object LookupMerge {

    class Answer(val name: String, val exact: Boolean, val confidence: Float, val size: Dimensions)

    class Merged(val name: String?, val dimensions: Dimensions, val source: SizeSource)

    const val EXACT_CONFIDENCE = 0.6f
    const val TYPICAL_CONFIDENCE = 0.5f
    const val NAME_CONFIDENCE = 0.4f

    /**
     * @param measured each item's size from the photo.
     * @param names each item's name from the detectors, or null.
     * @param answers the lookup's answer for each item, or null where there was none.
     * @param scale where the photo's own scale came from. A card beats a typical size, so with one
     *   only exact products change.
     */
    fun merge(measured: List<Dimensions>, names: List<String?>, answers: List<Answer?>, scale: ScaleSource): List<Merged> {
        require(measured.size == names.size && names.size == answers.size)
        val exact = answers.map { it != null && it.exact && it.confidence >= EXACT_CONFIDENCE }
        val typical = answers.map { it != null && !it.exact && it.confidence >= TYPICAL_CONFIDENCE }
        // The factor every other size is out by, from the surest answers available.
        val factor = if (scale == ScaleSource.CARD) null else {
            val from = measured.indices.filter { exact[it] }.ifEmpty { if (scale == ScaleSource.TYPICAL) measured.indices.filter { typical[it] } else emptyList() }
            from.map { longest(answers[it]!!.size).toDouble() / longest(measured[it]).coerceAtLeast(1) }
                .sorted().let { f -> if (f.isEmpty()) null else f[f.size / 2] }
                ?.takeIf { it in 0.1..10.0 }
        }
        return measured.indices.map { i ->
            val a = answers[i]
            val name = if (a != null && (exact[i] || names[i] == null && a.confidence >= NAME_CONFIDENCE)) a.name else names[i]
            when {
                exact[i] -> Merged(name, orient(a!!.size, measured[i]), SizeSource.LOOKED_UP)
                typical[i] && scale == ScaleSource.TYPICAL && factor == null -> Merged(name, orient(a!!.size, measured[i]), SizeSource.LOOKED_UP)
                factor != null -> Merged(name, scaled(measured[i], factor), SizeSource.PHOTO_SCALED_BY_LOOKUP)
                else -> Merged(name, measured[i], SizeSource.PHOTO)
            }
        }
    }

    /** The real sizes laid onto the photo's axes: its longest side where the photo saw the longest, and so on. */
    fun orient(real: Dimensions, measured: Dimensions): Dimensions {
        val r = listOf(real.widthMm, real.depthMm, real.heightMm).sortedDescending()
        val m = listOf(measured.widthMm, measured.depthMm, measured.heightMm)
        val rank = m.indices.sortedByDescending { m[it] }
        val out = IntArray(3); rank.forEachIndexed { k, axis -> out[axis] = r[k] }
        return Dimensions(out[0], out[1], out[2])
    }

    private fun longest(d: Dimensions) = maxOf(d.widthMm, d.depthMm, d.heightMm)

    private fun scaled(d: Dimensions, f: Double) =
        Dimensions((d.widthMm * f).toInt().coerceAtLeast(1), (d.depthMm * f).toInt().coerceAtLeast(1), (d.heightMm * f).toInt().coerceAtLeast(1))
}
