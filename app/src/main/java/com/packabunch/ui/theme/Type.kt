package com.packabunch.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Plus Jakarta Sans for UI, DM Mono for every measurement, count and percentage.
 *
 * Drop the .ttf files into res/font and replace these with FontFamily(Font(R.font.…)).
 * Roboto and the platform monospace are the intended fallbacks — the layout was sized
 * with enough slack to survive the swap.
 */
val UiFamily: FontFamily = FontFamily.SansSerif      // → Plus Jakarta Sans
val NumericFamily: FontFamily = FontFamily.Monospace // → DM Mono

val PackABunchTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = UiFamily, fontWeight = FontWeight.ExtraBold,
        fontSize = 33.sp, lineHeight = 39.sp, letterSpacing = (-1.0).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = UiFamily, fontWeight = FontWeight.ExtraBold,
        fontSize = 29.sp, lineHeight = 36.sp, letterSpacing = (-0.9).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = UiFamily, fontWeight = FontWeight.ExtraBold,
        fontSize = 25.sp, lineHeight = 32.sp, letterSpacing = (-0.6).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = UiFamily, fontWeight = FontWeight.Bold,
        fontSize = 18.sp, lineHeight = 24.sp, letterSpacing = (-0.2).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = UiFamily, fontWeight = FontWeight.Bold,
        fontSize = 15.5f.sp, lineHeight = 21.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = UiFamily, fontWeight = FontWeight.Normal,
        fontSize = 15.sp, lineHeight = 23.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = UiFamily, fontWeight = FontWeight.Normal,
        fontSize = 13.5f.sp, lineHeight = 20.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = UiFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 12.5f.sp, lineHeight = 18.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = UiFamily, fontWeight = FontWeight.ExtraBold,
        fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.6.sp,
    ),
)

/** Section headers: uppercase in the UI face, wide tracking. */
val SectionLabel = TextStyle(
    fontFamily = UiFamily, fontWeight = FontWeight.Bold,
    fontSize = 12.5f.sp, lineHeight = 18.sp, letterSpacing = 0.8.sp,
)

/** Big result numerals — "6/7", "71%". */
val NumeralLarge = TextStyle(
    fontFamily = NumericFamily, fontWeight = FontWeight.Normal,
    fontSize = 21.sp, lineHeight = 26.sp,
)

/** Dimension fields — "58.4". */
val NumeralField = TextStyle(
    fontFamily = NumericFamily, fontWeight = FontWeight.Normal,
    fontSize = 19.sp, lineHeight = 24.sp,
)

/** Dimension chips — "58×40×35 cm". */
val NumeralChip = TextStyle(
    fontFamily = NumericFamily, fontWeight = FontWeight.Normal,
    fontSize = 11.5f.sp, lineHeight = 16.sp,
)
