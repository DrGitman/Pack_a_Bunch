@file:OptIn(ExperimentalTextApi::class)

package com.packabunch.ui.theme

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.packabunch.R

/**
 * Plus Jakarta Sans for UI, DM Mono for every measurement, count and percentage.
 *
 * Plus Jakarta Sans ships as one variable font, so each weight is an axis setting on the
 * same file rather than eight separate files. Variable fonts need API 26, which is minSdk.
 *
 * DM Mono is here for one reason: lining numerals. It is what keeps "58.4 × 39.6 × 35.0 cm"
 * lining up between the review screen, the item list and the plan, so measurements stay
 * comparable between screens and never read as prose.
 */

private fun jakarta(weight: Int) = Font(
    resId = R.font.plus_jakarta_sans,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val UiFamily: FontFamily = FontFamily(
    jakarta(400),
    jakarta(500),
    jakarta(600),
    jakarta(700),
    jakarta(800),
)

val NumericFamily: FontFamily = FontFamily(
    Font(R.font.dm_mono_regular, FontWeight.Normal),
    Font(R.font.dm_mono_medium, FontWeight.Medium),
)

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

/** The small label above a field — "NAME", "STEP 1 OF 3 · THE SPACE". */
val FieldLabel = TextStyle(
    fontFamily = UiFamily, fontWeight = FontWeight.SemiBold,
    fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp,
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
