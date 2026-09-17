package com.packabunch.ui.theme

import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Generated from design-tokens.json — keep the two in step.

val Ground          = Color(0xFFF7EFE6)
val Surface         = Color(0xFFFFFFFF)
val SurfaceSunken   = Color(0xFFF0E5D9)
val SurfaceField    = Color(0xFFF9F4EE)
val SurfaceMuted    = Color(0xFFF4EDE4)
val BrandTint       = Color(0xFFF5E4D6)
val Outline         = Color(0xFFEADCCC)
val OutlineStrong   = Color(0xFFDCC6AF)
val Divider         = Color(0xFFF2E9DE)

val Primary         = Color(0xFFA65C34)
val PrimaryDark     = Color(0xFF7C4223)
val OnPrimary       = Color(0xFFFFFFFF)
val Chrome          = Color(0xFF3F2718)
val ChromeAlt       = Color(0xFF4A2E1F)
val Accent          = Color(0xFFE08A46)
// Authentication artboards: dark-card copy and warm body ink.
val HeroEyebrow     = Color(0xFFE8A76B)
val HeroBody        = Color(0xFFC9B29E)
val BodyInk         = Color(0xFF5C4A3A)
val EmptyCrateTop   = Color(0xFFE4D5C2)
val EmptyCrateLeft  = Color(0xFFCDB79E)
val EmptyCrateRight = Color(0xFFBCA48A)
val EmptyCrateStroke = Color(0xFFB79B79)

val TextPrimary     = Color(0xFF2B1D14)
val TextSecondary   = Color(0xFF7C6857)
val TextTertiary    = Color(0xFFA2907F)
val TextDisabled    = Color(0xFFB09A85)

val Success         = Color(0xFF3F7A5A)
val SuccessTint     = Color(0xFFE1EEE7)
val Caution         = Color(0xFFB4761A)
val CautionTint     = Color(0xFFFAEEDA)
val CautionText     = Color(0xFF6E4708)
val ErrorRed        = Color(0xFFB4402B)
val ErrorTint       = Color(0xFFFBE4DF)
val ErrorText       = Color(0xFF8E3322)

/** High-visibility accent for overlays drawn on the camera feed. */
val OnCamera        = Color(0xFFF2A03D)

/**
 * Item identity colours. Always paired with the item's number AND name —
 * colour is never the only signal (see UX.md, copy rules).
 */
val ItemColors = listOf(
    Color(0xFFA65C34),
    Color(0xFF3F7A5A),
    Color(0xFF3C5C9E),
    Color(0xFFB4761A),
    Color(0xFF6B4E9B),
    Color(0xFFA03A5B),
)

val ItemTints = listOf(
    Color(0xFFF2E0D4),
    Color(0xFFE1EEE7),
    Color(0xFFE2E8F3),
    Color(0xFFFAEEDA),
    Color(0xFFEDE6F3),
    Color(0xFFF7E3EA),
)

fun itemColor(index: Int) = ItemColors[index % ItemColors.size]
fun itemTint(index: Int) = ItemTints[index % ItemTints.size]

/** Where a stored dimension came from. Shown wherever the number is shown. */
enum class MeasurementSource { CameraEstimate, TypedIn }

val CameraEstimateText = Color(0xFF7C4223)
val CameraEstimateTint = Color(0xFFF5E4D6)
val TypedInText        = Color(0xFF3F5F7A)
val TypedInTint        = Color(0xFFE2EAF2)

val PackABunchColors = lightColorScheme(
    primary            = Primary,
    onPrimary          = OnPrimary,
    primaryContainer   = BrandTint,
    onPrimaryContainer = PrimaryDark,
    secondary          = Accent,
    onSecondary        = Chrome,
    background         = Ground,
    onBackground       = TextPrimary,
    surface            = Surface,
    onSurface          = TextPrimary,
    surfaceVariant     = SurfaceMuted,
    onSurfaceVariant   = TextSecondary,
    outline            = Outline,
    outlineVariant     = Divider,
    error              = ErrorRed,
    onError            = OnPrimary,
    errorContainer     = ErrorTint,
    onErrorContainer   = ErrorText,
)
