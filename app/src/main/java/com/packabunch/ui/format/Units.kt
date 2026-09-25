package com.packabunch.ui.format

import com.packabunch.packing.Dimensions
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The only place millimetres turn into something a person reads, and the only place a
 * typed figure turns back into millimetres.
 *
 * Storage is integer mm everywhere else, deliberately. Doing the conversion at this one
 * boundary is what stops a value drifting as it moves between the item editor, the review
 * screen and the plan — round-tripping cm → mm → cm has to come back with what was typed.
 */
enum class LengthUnit(val label: String, val shortLabel: String) {
    CENTIMETRES("centimetres", "cm"),
    INCHES("inches", "in");

    val perUnitMm: Double get() = if (this == CENTIMETRES) 10.0 else 25.4
}

/** Millimetres to a display string, at the precision each unit actually warrants. */
fun formatLength(mm: Int, unit: LengthUnit): String {
    val value = mm / unit.perUnitMm
    // One decimal place, and no trailing ".0" — the artboards show both "58.4" and "33".
    val rounded = (value * 10).roundToInt() / 10.0
    return if (abs(rounded - rounded.roundToInt()) < 0.05) {
        rounded.roundToInt().toString()
    } else {
        String.format("%.1f", rounded)
    }
}

fun formatLengthWithUnit(mm: Int, unit: LengthUnit): String =
    "${formatLength(mm, unit)} ${unit.shortLabel}"

/** Editable fields must round-trip every stored millimetre, including inch values. */
fun formatEditableLength(mm: Int, unit: LengthUnit): String =
    java.math.BigDecimal.valueOf(mm / unit.perUnitMm)
        .setScale(if (unit == LengthUnit.INCHES) 2 else 1, java.math.RoundingMode.HALF_UP)
        .stripTrailingZeros().toPlainString()

/** The "58.4 × 39.6 × 35.0 cm" line. Multiplication sign, not the letter x. */
/**
 * The short form the pack cards use: "58×40×35 cm", whole numbers, no spaces around the ×.
 *
 * A card has room for two chips side by side only if this stays short; the full figures are
 * one tap away on the pack itself, where precision actually matters.
 */
fun formatDimensionsCompact(dimensions: Dimensions, unit: LengthUnit): String {
    fun short(mm: Int) = Math.round(if (unit == LengthUnit.INCHES) mm / 25.4 else mm / 10.0).toString()
    return "${short(dimensions.widthMm)}×${short(dimensions.depthMm)}×${short(dimensions.heightMm)} ${unit.shortLabel}"
}

fun formatDimensions(dimensions: Dimensions, unit: LengthUnit): String = buildString {
    append(formatLength(dimensions.widthMm, unit))
    append(" × ")
    append(formatLength(dimensions.depthMm, unit))
    append(" × ")
    append(formatLength(dimensions.heightMm, unit))
    append(' ')
    append(unit.shortLabel)
}

/**
 * A typed figure back to millimetres. Returns null for anything that is not a usable
 * positive number, so the caller shows the inline "check this" state rather than
 * substituting a zero and solving against it.
 */
fun parseLengthToMm(input: String, unit: LengthUnit): Int? {
    val cleaned = input.trim().replace(',', '.')
    if (cleaned.isEmpty()) return null
    val value = cleaned.toDoubleOrNull() ?: return null
    if (value <= 0.0 || !value.isFinite()) return null
    val mm = (value * unit.perUnitMm).roundToInt()
    return mm.takeIf { it > 0 }
}
