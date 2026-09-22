package com.packabunch.data.catalogue

import com.packabunch.packing.Dimensions
import com.packabunch.packing.SuggestedDimensions
import com.packabunch.packing.SuggestionConfidence

/**
 * Looking up how big a kind of thing usually is.
 *
 * ### What this is for
 *
 * Typing three measurements is where people give up. A lookup that fills them in and asks
 * you to glance at them is the difference between a pack somebody finishes and one they
 * abandon. That is the whole value.
 *
 * ### What it is not
 *
 * It is not a measurement, and nothing here is allowed to pretend otherwise. A catalogue
 * knows the published size of a *model*; it cannot know whether yours has a case on it, a
 * lid, or a bent corner. Every answer therefore comes back as [SuggestedDimensions] with a
 * tolerance and a source note, renders as **SUGGESTED — CHECK IT**, and becomes the user's
 * own `TYPED_IN` figure the moment they touch it.
 *
 * ### Where the data comes from
 *
 * A curated catalogue we control, queried through [CatalogueSource]. Deliberately **not**
 * client-side scraping of a third-party site: those parsers break silently in the field,
 * cannot be fixed without a release, and reference sites like dimensions.com licence their
 * data rather than give it away. Legitimate inputs are manufacturer specs, barcode/GTIN
 * feeds, and — best of all long term — measurements our own users confirmed, which are the
 * real external envelope rather than a marketing spec.
 *
 * The bias is asymmetric on purpose: see [ROUND_ITEMS_UP_MM].
 */
interface CatalogueSource {
    /** An exact product, from a scanned barcode. The only route to [SuggestionConfidence.EXACT_PRODUCT]. */
    suspend fun byBarcode(gtin: String): SuggestedDimensions?

    /** Brand and model read off the object, matched against the catalogue. */
    suspend fun byModel(query: String): SuggestedDimensions?

    /** A category prior. Wide by nature — a range, never close to a measurement. */
    suspend fun byCategory(category: String): SuggestedDimensions?
}

/**
 * Failing to place something that would have fitted is an annoyance. Promising a fit that
 * does not exist loses the user at the kerb with a full boot. So suggested item sizes are
 * nudged **up** and suggested space sizes would be nudged **down** — the error is pushed
 * towards "you have more room than we said".
 */
const val ROUND_ITEMS_UP_MM: Int = 5

/**
 * A catalogue with nothing behind it yet.
 *
 * Returns null for everything, on purpose. When a real source is connected this is the only
 * class that changes. Until then the app behaves exactly as if lookup did not exist, which
 * is the honest state — a lookup that returned plausible invented numbers would be far
 * worse than no lookup at all.
 */
class EmptyCatalogue : CatalogueSource {
    override suspend fun byBarcode(gtin: String): SuggestedDimensions? = null
    override suspend fun byModel(query: String): SuggestedDimensions? = null
    override suspend fun byCategory(category: String): SuggestedDimensions? = null
}

/**
 * A small on-device catalogue of genuinely standardised things.
 *
 * These are sizes fixed by a published standard rather than by a manufacturer's whim — a
 * sheet of A4 is 210 × 297 mm everywhere, a Euro pallet is 1200 × 800 mm by definition.
 * That makes them safe to ship in the binary and safe to state with a tight tolerance.
 *
 * It stops well short of a product database. Anything model-specific belongs behind
 * [CatalogueSource] and a real data agreement.
 */
class StandardSizesCatalogue : CatalogueSource {

    override suspend fun byBarcode(gtin: String): SuggestedDimensions? = null

    override suspend fun byModel(query: String): SuggestedDimensions? = null

    override suspend fun byCategory(category: String): SuggestedDimensions? {
        val entry = STANDARDS[category.trim().lowercase()] ?: return null
        return entry.copy(
            dimensions = entry.dimensions.paddedBy(ROUND_ITEMS_UP_MM),
        )
    }

    private companion object {
        /**
         * Standardised sizes only, each with the standard named in its note so the user can
         * judge it. Tolerances reflect real variation: a "shoe box" has no standard at all
         * and gets a wide one, which is exactly the signal that it needs measuring.
         */
        val STANDARDS: Map<String, SuggestedDimensions> = mapOf(
            "a4 paper ream" to SuggestedDimensions(
                dimensions = Dimensions(297, 210, 52),
                toleranceMm = 6,
                sourceNote = "ISO 216 A4, 500-sheet ream",
                confidence = SuggestionConfidence.MATCHED_MODEL,
            ),
            "euro pallet" to SuggestedDimensions(
                dimensions = Dimensions(1200, 800, 144),
                toleranceMm = 5,
                sourceNote = "EN 13698-1 standard pallet",
                confidence = SuggestionConfidence.MATCHED_MODEL,
            ),
            "shoe box" to SuggestedDimensions(
                dimensions = Dimensions(330, 200, 120),
                // No standard exists, and the spread is wider than most packs can absorb.
                // The tolerance says so rather than hiding it.
                toleranceMm = 45,
                sourceNote = "typical adult shoe box, varies a lot by brand",
                confidence = SuggestionConfidence.CATEGORY_ONLY,
            ),
            "banker's box" to SuggestedDimensions(
                dimensions = Dimensions(400, 320, 260),
                toleranceMm = 20,
                sourceNote = "common archive box size",
                confidence = SuggestionConfidence.CATEGORY_ONLY,
            ),
        )

        fun Dimensions.paddedBy(mm: Int) = Dimensions(
            widthMm = widthMm + mm,
            depthMm = depthMm + mm,
            heightMm = heightMm + mm,
        )
    }
}

/**
 * The confidence ladder, walked from the top down.
 *
 * Barcode beats model, model beats category, and nothing beats a person with a tape
 * measure. The first hit wins because a worse-confidence answer never improves a better one
 * — averaging a precise spec against a vague category prior would only make the precise one
 * worse.
 */
class CatalogueLookup(private val sources: List<CatalogueSource>) {

    suspend fun lookup(request: LookupRequest): SuggestedDimensions? {
        for (source in sources) {
            request.barcode?.let { code ->
                source.byBarcode(code)?.let { return it }
            }
        }
        for (source in sources) {
            request.modelText?.let { text ->
                source.byModel(text)?.let { return it }
            }
        }
        for (source in sources) {
            request.category?.let { category ->
                source.byCategory(category)?.let { return it }
            }
        }
        return null
    }
}

/**
 * What we know about the thing being looked up. All three are optional: a photo might give
 * a category and nothing else, a barcode gives a code and nothing else.
 */
data class LookupRequest(
    val barcode: String? = null,
    val modelText: String? = null,
    val category: String? = null,
)

/**
 * What the item editor does with an answer.
 *
 * Optimistic fill: the fields are populated straight away and the user carries on. There is
 * no loading screen and no spinner in the way — if a better answer arrives later it
 * replaces an untouched field silently, and if the lookup fails nothing visibly broke
 * because nothing visibly started.
 */
sealed interface SuggestionState {
    data object None : SuggestionState

    /** Filled in from a suggestion, untouched by the user. Still replaceable. */
    data class Filled(val suggestion: SuggestedDimensions) : SuggestionState

    /**
     * The user edited it. From here the value is theirs, the badge reads "typed in", and no
     * later suggestion may overwrite it.
     */
    data object UserOwned : SuggestionState
}
