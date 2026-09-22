package com.packabunch.data.catalogue

import com.packabunch.packing.Dimensions
import com.packabunch.packing.SuggestedDimensions
import com.packabunch.packing.SuggestionConfidence
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

/**
 * Product dimensions by barcode, from Open Food Facts.
 *
 * ### Why this source and not a knowledge graph
 *
 * Wikidata was measured before being trusted, and it fails badly for this: searching it for
 * "suitcase" returns *paintings called Suitcase*, because museums catalogue artwork
 * dimensions and nobody catalogues household goods. Four of ten everyday packing items
 * returned data and every one of those was an artwork. A lookup that quietly answers
 * "washing machine: 174 cm wide" with a canvas's measurements is worse than no lookup.
 * Its public endpoint also rate-limits to about one request a minute under load, which
 * rules it out for a scan of sixty things regardless.
 *
 * Open Food Facts is narrower but honest: entries are real products keyed by the barcode
 * actually printed on them, and the size field — when present — is the packaging.
 *
 * ### The catch, stated plainly
 *
 * Coverage is mostly groceries, and the field is free text written by contributors
 * ("330 ml", "20x15x8 cm", "1 kg"). Only some of it parses to three lengths, which is why
 * [parsePackageSize] refuses anything it cannot read rather than guessing. A miss here is
 * fine — the depth scan already measured the real object.
 */
class OpenFoodFactsCatalogue(
    private val userAgent: String = "PackABunch/0.1 (Android; packing assistant)",
) : CatalogueSource {

    override suspend fun byBarcode(gtin: String): SuggestedDimensions? =
        withContext(Dispatchers.IO) {
            val clean = gtin.filter { it.isDigit() }
            if (clean.length !in 8..14) return@withContext null

            val body = fetch(
                "https://world.openfoodfacts.org/api/v2/product/$clean" +
                    "?fields=product_name,brands,product_quantity,packaging_size,quantity",
            ) ?: return@withContext null

            val json = runCatching { JSONObject(body) }.getOrNull() ?: return@withContext null
            if (json.optInt("status", 0) != 1) return@withContext null

            val product = json.optJSONObject("product") ?: return@withContext null
            val name = listOfNotNull(
                product.optString("brands").takeIf { it.isNotBlank() },
                product.optString("product_name").takeIf { it.isNotBlank() },
            ).joinToString(" ").ifBlank { "Product $clean" }

            val sizeText = listOf("packaging_size", "quantity")
                .firstNotNullOfOrNull { product.optString(it).takeIf { s -> s.isNotBlank() } }
                ?: return@withContext null

            val dimensions = parsePackageSize(sizeText) ?: return@withContext null

            SuggestedDimensions(
                dimensions = dimensions.paddedBy(ROUND_ITEMS_UP_MM),
                // The published packaging size is the bare product. Whatever is actually in
                // front of somebody may have a sleeve, a box, or a bag around it.
                toleranceMm = 10,
                sourceNote = "$name, packaging size on Open Food Facts",
                confidence = SuggestionConfidence.EXACT_PRODUCT,
            )
        }

    override suspend fun byModel(query: String): SuggestedDimensions? = null

    override suspend fun byCategory(category: String): SuggestedDimensions? = null

    private fun fetch(url: String): String? = try {
        (URL(url).openConnection() as HttpURLConnection).run {
            requestMethod = "GET"
            setRequestProperty("User-Agent", userAgent)
            connectTimeout = 4_000
            readTimeout = 4_000
            if (responseCode == 200) inputStream.bufferedReader().use { it.readText() } else null
        }
    } catch (t: Throwable) {
        // A lookup failing is a non-event: the fields simply stay as they were.
        null
    }

    private companion object {
        /**
         * Reads "20 x 15 x 8 cm" and its variants into three lengths.
         *
         * Refuses everything else. Contributors write "330 ml", "1 kg", "6 pack" into the
         * same field, and inventing a shape from a volume would produce a confident wrong
         * answer — exactly the failure mode this whole design is built to avoid.
         */
        val TRIPLE = Regex(
            """(\d+(?:[.,]\d+)?)\s*[x×*]\s*(\d+(?:[.,]\d+)?)\s*[x×*]\s*(\d+(?:[.,]\d+)?)\s*(mm|cm|m|in)?""",
            RegexOption.IGNORE_CASE,
        )

        fun parsePackageSize(text: String): Dimensions? {
            val match = TRIPLE.find(text) ?: return null
            val (a, b, c, unit) = match.destructured

            fun toMm(raw: String): Int? {
                val value = raw.replace(',', '.').toDoubleOrNull() ?: return null
                val perUnit = when (unit.lowercase()) {
                    "mm" -> 1.0
                    "m" -> 1000.0
                    "in" -> 25.4
                    // Blank defaults to centimetres, which is how these fields are almost
                    // always written. Anything absurd is rejected below.
                    else -> 10.0
                }
                return (value * perUnit).roundToInt().takeIf { it in 1..5_000 }
            }

            val width = toMm(a) ?: return null
            val depth = toMm(b) ?: return null
            val height = toMm(c) ?: return null
            return Dimensions(width, depth, height)
        }

        fun Dimensions.paddedBy(mm: Int) =
            Dimensions(widthMm + mm, depthMm + mm, heightMm + mm)
    }
}

/**
 * Remembers every answer, so a lookup is asked for at most once.
 *
 * Two jobs. The obvious one is speed — a repeat is a map read rather than a round trip. The
 * one that matters more is that the app keeps working with no signal: somebody packing a
 * van in a car park has terrible connectivity, and anything already seen still resolves.
 *
 * Misses are cached too. Knowing a barcode has no useful entry is worth as much as knowing
 * it does, and it stops the same doomed request being made sixty times during one scan.
 */
class CachingCatalogue(
    private val delegate: CatalogueSource,
    private val maxEntries: Int = 500,
) : CatalogueSource {

    private val cache = object : LinkedHashMap<String, Box>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Box>?): Boolean =
            size > maxEntries
    }

    /** A present-but-null answer is a remembered miss, which is different from "not asked". */
    private class Box(val value: SuggestedDimensions?)

    override suspend fun byBarcode(gtin: String): SuggestedDimensions? =
        memoise("gtin:$gtin") { delegate.byBarcode(gtin) }

    override suspend fun byModel(query: String): SuggestedDimensions? =
        memoise("model:${query.lowercase()}") { delegate.byModel(query) }

    override suspend fun byCategory(category: String): SuggestedDimensions? =
        memoise("cat:${category.lowercase()}") { delegate.byCategory(category) }

    private suspend fun memoise(
        key: String,
        fetch: suspend () -> SuggestedDimensions?,
    ): SuggestedDimensions? {
        synchronized(cache) { cache[key] }?.let { return it.value }
        val fetched = fetch()
        synchronized(cache) { cache[key] = Box(fetched) }
        return fetched
    }
}
