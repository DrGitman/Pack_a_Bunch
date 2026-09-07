package com.packabunch.packing

import kotlin.math.abs
import kotlin.math.max

/**
 * Deciding which number to use when more than one thing has an opinion.
 *
 * The order is fixed and it is not a matter of taste:
 *
 * 1. **What the scan measured.** It measured *the object in front of you* — this one, with
 *    its lid on and its dent in the corner.
 * 2. **What a catalogue says.** That describes a *different instance* of the same product.
 *    Useful when the scan could not see properly; never better than the scan when it could.
 * 3. **What the person types.** Always wins over both, because they are holding it.
 *
 * The rule people get backwards is the third one below. A precise-looking catalogue figure
 * is tempting, but "the manufacturer says this model is 330 mm" loses to "we measured yours
 * at 348 mm" every time. The catalogue is not measuring your object.
 */
object DimensionResolver {

    /**
     * How far apart two readings can be before they are treated as describing different
     * things rather than the same thing imprecisely. Ten per cent of the larger edge.
     */
    private const val DISAGREEMENT_FRACTION = 0.10

    /**
     * Above this, a scan is too coarse to rely on and a catalogue answer is worth trying.
     * Two centimetres of doubt on a single item is enough to ruin a snug pack.
     */
    private const val SCAN_TOLERANCE_LIMIT_MM = 20

    fun resolve(
        scanned: DetectedObject?,
        suggestion: SuggestedDimensions?,
        typed: Dimensions? = null,
    ): ResolvedDimensions {
        // The person holding the object outranks everything. No cleverness here on purpose.
        if (typed != null) {
            return ResolvedDimensions(
                dimensions = typed,
                source = MeasurementSource.TYPED_IN,
                toleranceMm = 0,
                note = "You typed this.",
                needsConfirming = false,
            )
        }

        val scanTrustworthy = scanned != null &&
            !scanned.looksMerged &&
            scanned.restsOnSupport &&
            scanned.toleranceMm <= SCAN_TOLERANCE_LIMIT_MM

        // The ordinary case: the scan saw it cleanly, so nothing else is consulted at all.
        if (scanTrustworthy && suggestion == null) {
            return scanned!!.asResolved()
        }

        if (scanTrustworthy && suggestion != null) {
            val disagrees = disagree(scanned!!.dimensions, suggestion.dimensions)
            return if (disagrees) {
                // Both had a clear look and they do not match. The scan measured *this*
                // object; the catalogue measured a different one off a production line. Keep
                // the scan, and say why the two differ rather than hiding it.
                scanned.asResolved(
                    note = "Measured from your scan. A catalogue lists this model as " +
                        "${suggestion.dimensions.summary()}, but yours measured differently — " +
                        "packaging, a lid or a case will do that.",
                    needsConfirming = true,
                )
            } else {
                // They agree, which is the one case where the catalogue adds something: it
                // is usually the tighter of the two, so it sharpens the figure.
                if (suggestion.toleranceMm < scanned.toleranceMm) {
                    suggestion.asResolved(
                        note = "${suggestion.sourceNote}. Matches what your scan measured.",
                    )
                } else {
                    scanned.asResolved(note = "Measured from your scan, and a catalogue agrees.")
                }
            }
        }

        // The scan could not see it properly. This is what the lookup is for.
        if (suggestion != null) {
            return suggestion.asResolved(
                note = buildString {
                    append(suggestion.sourceNote)
                    append(". ")
                    append(
                        when {
                            scanned == null -> "Nothing was measured for this one, so check it."
                            scanned.looksMerged ->
                                "The scan couldn't tell this apart from what it was touching."
                            !scanned.restsOnSupport ->
                                "The scan wasn't sure this was one object."
                            else -> "The scan was too coarse here."
                        },
                    )
                },
            )
        }

        // Neither worked. Nothing is invented; the item simply has no size yet.
        return ResolvedDimensions(
            dimensions = scanned?.dimensions,
            source = if (scanned != null) MeasurementSource.CAMERA_ESTIMATE else null,
            toleranceMm = scanned?.toleranceMm ?: 0,
            note = if (scanned == null) {
                "We couldn't measure this one. Type its size and everything else carries on."
            } else {
                "The scan wasn't confident about this one. Worth checking with a tape."
            },
            needsConfirming = true,
        )
    }

    /** True when two readings are far enough apart to be describing different things. */
    private fun disagree(a: Dimensions, b: Dimensions): Boolean {
        val pairs = listOf(
            a.widthMm to b.widthMm,
            a.depthMm to b.depthMm,
            a.heightMm to b.heightMm,
        )
        // Sorted, because a catalogue may list a box's edges in a different order than the
        // scan happened to orient it. Comparing width-to-width would report a disagreement
        // for an object simply lying the other way round.
        val sortedA = a.sortedEdgesMm()
        val sortedB = b.sortedEdgesMm()
        return sortedA.indices.any { i ->
            val larger = max(sortedA[i], sortedB[i]).toDouble()
            abs(sortedA[i] - sortedB[i]) > larger * DISAGREEMENT_FRACTION
        } && pairs.isNotEmpty()
    }

    private fun DetectedObject.asResolved(
        note: String = "Measured from your scan.",
        needsConfirming: Boolean = false,
    ) = ResolvedDimensions(
        dimensions = dimensions,
        source = MeasurementSource.CAMERA_ESTIMATE,
        toleranceMm = toleranceMm,
        note = note,
        needsConfirming = needsConfirming,
    )

    private fun SuggestedDimensions.asResolved(note: String) = ResolvedDimensions(
        dimensions = dimensions,
        source = MeasurementSource.SUGGESTED,
        toleranceMm = toleranceMm,
        note = note,
        // A looked-up figure is always worth a glance: it is not this object.
        needsConfirming = true,
    )

    private fun Dimensions.summary() = "$widthMm × $depthMm × $heightMm mm"
}

/**
 * One agreed answer, and the reasoning behind it.
 *
 * [note] is written for the user, not for a log. It is the difference between a field that
 * silently filled itself in and one that says where its number came from — which is what
 * lets somebody decide whether to trust it without measuring again.
 */
data class ResolvedDimensions(
    val dimensions: Dimensions?,
    val source: MeasurementSource?,
    val toleranceMm: Int,
    val note: String,
    val needsConfirming: Boolean,
) {
    val hasAnswer: Boolean get() = dimensions != null

    /**
     * Whether this needs a person's attention *for this particular pack*.
     *
     * Uncertainty only matters against the room available. Two centimetres of doubt is
     * nothing in a half-empty boot and decisive in a crate with four millimetres to spare,
     * so the clearance decides.
     *
     * Note this deliberately does **not** consult [needsConfirming]. Those are two different
     * jobs: [needsConfirming] decides whether the badge says "check it", which is a passive
     * mark on the field; this decides whether to *interrupt somebody*, which has to be
     * earned. A confidently-measured figure with ±20 mm still cannot answer a 5 mm gap, and
     * a rough one is nobody's problem when there is a hand's width to spare.
     */
    fun needsAttention(clearanceMm: Int): Boolean = toleranceMm >= clearanceMm
}
