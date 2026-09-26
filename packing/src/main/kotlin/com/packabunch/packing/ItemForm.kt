package com.packabunch.packing

import kotlin.math.roundToInt

/**
 * What an item looks like, roughly — drawn in the plan and the packing guide so a cup reads
 * as a cup and a ball as a ball, instead of every item being the same brown box.
 *
 * ### What this is not
 *
 * It is never geometry the solver sees. [ItemSpec.dimensions] (and a collision
 * [ItemSpec.shape] if one was measured) decide where things go; the form only decides how the
 * box the solver placed is drawn. A form drawn inside a box is always smaller than the box, so
 * the picture can never show an item poking through a neighbour the solver kept clear of.
 *
 * It is an approximation of a *kind* of thing scaled to this item's measured size, never a
 * claim to be a model of this particular object.
 */
data class ItemForm(
    val family: FormFamily,
    /**
     * Top radius over bottom radius, for [FormFamily.TAPERED]: 1.4 is a cup wider at the rim,
     * 0.7 a flowerpot the other way up. 1 for everything else.
     */
    val topRatio: Float = 1f,
) {
    /** Compact text for storage: `TAPERED:1.40`. */
    fun encode(): String = "${family.name}:${"%.2f".format(java.util.Locale.ROOT, topRatio)}"

    companion object {
        /** Reads [encode]'s output. Anything unrecognised is null, never an exception. */
        fun decode(text: String?): ItemForm? {
            if (text.isNullOrBlank()) return null
            val parts = text.split(':')
            val family = runCatching { FormFamily.valueOf(parts[0]) }.getOrNull() ?: return null
            val ratio = parts.getOrNull(1)?.toFloatOrNull()?.takeIf { it in 0.2f..5f } ?: 1f
            return ItemForm(family, ratio)
        }

        /** The form a scan measured. */
        fun fromFit(fit: FittedObject): ItemForm = when (fit.shape) {
            ShapeFamily.BOX, ShapeFamily.IRREGULAR -> ItemForm(FormFamily.BOX)
            ShapeFamily.CYLINDER -> ItemForm(FormFamily.CYLINDER)
            ShapeFamily.SPHERE -> ItemForm(FormFamily.SPHERE)
            ShapeFamily.TAPERED -> {
                val top = fit.topRadiusMm ?: 1f; val bottom = fit.bottomRadiusMm ?: 1f
                ItemForm(FormFamily.TAPERED, (top / bottom.coerceAtLeast(1f)).coerceIn(0.3f, 3f).let { (it * 100).roundToInt() / 100f })
            }
        }

        /**
         * A form proposed from the item's name — the photo label or what the person typed.
         * Recognition proposes, the person can change the name, and none of it reaches the
         * solver. Only whole words match, so "Boxing gloves" is not a box and "Cupboard"
         * is not a cup.
         */
        fun guess(name: String): ItemForm? {
            val words = name.lowercase().split(Regex("[^a-z]+")).filter { it.isNotEmpty() }.toSet()
            if (words.isEmpty()) return null
            for ((keys, form) in NAME_FORMS) if (keys.any { it in words }) return form
            return null
        }

        private val NAME_FORMS: List<Pair<Set<String>, ItemForm>> = listOf(
            setOf("cup", "cups", "mug", "mugs", "tumbler", "glass", "glasses", "bucket", "beaker") to ItemForm(FormFamily.TAPERED, 1.3f),
            setOf("pot", "plant", "planter", "flowerpot", "vase") to ItemForm(FormFamily.TAPERED, 1.25f),
            setOf("can", "cans", "tin", "tins", "jar", "jars", "bottle", "bottles", "flask", "thermos",
                "canister", "candle", "tube", "cylinder", "roll", "rolls", "drum", "tub") to ItemForm(FormFamily.CYLINDER),
            setOf("ball", "balls", "globe", "sphere", "football", "basketball", "orange", "melon") to ItemForm(FormFamily.SPHERE),
            setOf("box", "boxes", "carton", "cartons", "crate", "book", "books", "case", "suitcase", "parcel", "package") to ItemForm(FormFamily.BOX),
        )
    }
}

enum class FormFamily { BOX, CYLINDER, TAPERED, SPHERE }
