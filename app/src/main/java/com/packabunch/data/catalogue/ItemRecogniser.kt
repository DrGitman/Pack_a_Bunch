package com.packabunch.data.catalogue

import android.graphics.Bitmap
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Working out what a photographed thing *is*, so the catalogue has something to look up.
 *
 * ### The thing to be clear about
 *
 * **Recognising an object does not reveal its size.** Knowing "this is a shoe box" tells you
 * almost nothing useful: the spread across real shoe boxes is wider than the clearance most
 * packs have. So recognition is not a measuring technique and is never treated as one — it
 * produces a *query*, and what comes back is a suggestion with a tolerance that has to be
 * checked.
 *
 * ### Why three different readings of the same photo
 *
 * They are wildly different in worth, which is exactly why they are separate rungs:
 *
 *  - **[readBarcode]** — a GTIN is a specific product with published measurements. This is
 *    the only rung that can be genuinely precise. Most household objects have no barcode on
 *    them by the time you are packing, which is the catch.
 *  - **[readModelText]** — OCR of the brand and model printed on the thing. Underrated: a
 *    router, a monitor, an appliance usually has a model number on a label, and that maps to
 *    an exact catalogue entry. This is the rung that does the most work in practice.
 *  - **[labelCategory]** — what the object broadly is. The bundled model knows a few hundred
 *    everyday categories, which makes it good for *naming* the item and poor for sizing it.
 *
 * All three run on-device. Nothing is uploaded, so the promise that photos stay on the phone
 * survives this feature.
 */
class ItemRecogniser {

    private val labeler by lazy {
        ImageLabeling.getClient(
            ImageLabelerOptions.Builder()
                // Below this the label is a coin flip and would only produce a wrong name
                // for the user to correct. 0.6 let a black phone through as "Dog".
                .setConfidenceThreshold(0.75f)
                .build(),
        )
    }
    private val textRecogniser by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }
    private val barcodeScanner by lazy { BarcodeScanning.getClient() }

    /** Everything one photo can tell us, gathered in one pass. */
    suspend fun recognise(bitmap: Bitmap): Recognition {
        val image = InputImage.fromBitmap(bitmap, 0)
        return Recognition(
            barcode = readBarcode(image),
            modelText = readModelText(image),
            category = labelCategory(image),
        )
    }

    /** Live scanning needs a conservative object label, without OCR/barcode work per frame. */
    suspend fun scanCategory(bitmap: Bitmap): String? = suspendCancellableCoroutine { continuation ->
        labeler.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { labels ->
                val candidates = labels.filter { it.text.lowercase() !in NOT_AN_ITEM }.sortedByDescending { it.confidence }
                val best = candidates.firstOrNull()
                val runnerUp = candidates.getOrNull(1)?.confidence ?: 0f
                continuation.resume(best?.takeIf { it.confidence >= 0.85f && it.confidence - runnerUp >= 0.10f }?.text)
            }
            .addOnFailureListener { continuation.resume(null) }
    }

    /** An exact product code, when the object happens to still have one on it. */
    private suspend fun readBarcode(image: InputImage): String? =
        suspendCancellableCoroutine { continuation ->
            barcodeScanner.process(image)
                .addOnSuccessListener { codes ->
                    continuation.resume(codes.firstNotNullOfOrNull { it.rawValue })
                }
                .addOnFailureListener { continuation.resume(null) }
        }

    /**
     * Text printed on the object, filtered down to the lines that look like a brand or a
     * model number.
     *
     * Crude on purpose. A model number is usually short, contains digits, and sits on its
     * own line; prose and legal small print do not. Sending the whole OCR dump as a query
     * would match on packaging blurb and return confident nonsense.
     */
    private suspend fun readModelText(image: InputImage): String? =
        suspendCancellableCoroutine { continuation ->
            textRecogniser.process(image)
                .addOnSuccessListener { result ->
                    val candidate = result.textBlocks
                        .flatMap { it.lines }
                        .map { it.text.trim() }
                        .filter { line ->
                            line.length in 3..40 &&
                                line.any { it.isDigit() } &&
                                line.count { it.isWhitespace() } <= 4
                        }
                        .maxByOrNull { it.length }
                    continuation.resume(candidate)
                }
                .addOnFailureListener { continuation.resume(null) }
        }

    /**
     * What the thing broadly is.
     *
     * Good enough to name the item — "Item 1" is a poor default when the app could say
     * "Box" — and nowhere near good enough to size it. That is why the result flows into
     * [CatalogueSource.byCategory], the lowest-confidence rung, and never straight into a
     * dimension field.
     */
    private suspend fun labelCategory(image: InputImage): String? =
        suspendCancellableCoroutine { continuation ->
            labeler.process(image)
                .addOnSuccessListener { labels ->
                    continuation.resume(
                        labels.filter { it.text.lowercase() !in NOT_AN_ITEM }
                            .maxByOrNull { it.confidence }?.text,
                    )
                }
                .addOnFailureListener { continuation.resume(null) }
        }

    fun close() {
        labeler.close()
        textRecogniser.close()
        barcodeScanner.close()
    }

    private companion object {
        /**
         * Labels the bundled model gives that can never be the thing somebody put on the table
         * to pack: animals, people, the scene, a texture. On a tight crop of a dark phone the
         * model reaches for these — a phone on a table was tagged "Dog" — and a wrong name on
         * a tag is what makes the whole scan look broken. No label beats a wrong one; the tag
         * falls back to "Item 2".
         */
        val NOT_AN_ITEM = setOf(
            "dog", "cat", "bird", "horse", "cattle", "insect", "butterfly", "fish", "pet",
            "person", "selfie", "smile", "hand", "skin", "hair", "beard", "eyelash", "team",
            "crowd", "baby", "sky", "cloud", "sunset", "room",
            "building", "floor", "wall", "tile", "pattern", "textile", "leather", "wood",
            "metal", "event", "fun", "flesh", "night", "darkness", "interaction",
            // Body parts: a heater's grille came back as "Mouth".
            "mouth", "nose", "ear", "eye", "lip", "tooth", "teeth", "tongue", "face", "finger",
            "nail", "arm", "leg", "foot", "ceiling",
            // Vehicle parts and scenes: a round plastic tub came back as "Wheel". Nothing packed
            // on a table is one of these, so they are never a name; the item stays "Item" and
            // can be named or typed instead.
            "wheel", "tire", "tyre", "rim", "car", "vehicle", "bicycle", "motorcycle", "road",
            "desk", "table", "countertop", "shelf", "circle", "rectangle", "font", "screenshot",
        )
    }
}

/**
 * What one photo yielded.
 *
 * [category] is worth showing even when nothing else worked, because a suggested *name*
 * saves typing and cannot mislead. [asLookupRequest] is what goes to the catalogue.
 */
data class Recognition(
    val barcode: String?,
    val modelText: String?,
    val category: String?,
) {
    val foundSomething: Boolean get() = barcode != null || modelText != null || category != null

    fun asLookupRequest(): LookupRequest = LookupRequest(
        barcode = barcode,
        modelText = modelText,
        category = category,
    )

    /**
     * A name to prefill, which is a separate thing from a size and always editable.
     *
     * The screen labels it "Suggested label, tap to change" precisely so nobody reads it
     * as the app having identified their specific object.
     */
    fun suggestedName(): String? = category?.replaceFirstChar { it.uppercase() }
}
