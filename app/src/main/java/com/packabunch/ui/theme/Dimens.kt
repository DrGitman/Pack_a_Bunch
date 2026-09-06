package com.packabunch.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val base = 16.dp
    val lg = 20.dp
    val xl = 24.dp
    val xxl = 32.dp

    /** Left and right padding on every screen. */
    val gutter = 20.dp
}

object Radius {
    val field = 16.dp
    val card = 22.dp
    val cardLarge = 26.dp
    val hero = 30.dp
    val sheet = 30.dp
    val iconTile = 15.dp
    val pill = 999.dp
}

object Size {
    /** Reserved for the real Android status bar. Never paint here. */
    val statusInset = 30.dp
    /** Reserved for the gesture strip. */
    val gestureInset = 28.dp

    val buttonPrimary = 56.dp
    val buttonSecondary = 54.dp
    val buttonText = 48.dp

    /** Nothing tappable goes below this. */
    val minTouchTarget = 44.dp

    val appBarRow = 44.dp
    val iconTile = 44.dp
    val icon = 21.dp
    val iconSmall = 19.dp
    val iconLarge = 24.dp

    val shutter = 76.dp
    val navPillHeight = 52.dp
    val navFab = 56.dp
}

object Strokes {
    val icon = 1.9f
    val iconBold = 2.3f
    val border = 1.5.dp
    val borderSelected = 2.dp
}

val PackABunchShapes = Shapes(
    extraSmall = RoundedCornerShape(Radius.iconTile),
    small = RoundedCornerShape(Radius.field),
    medium = RoundedCornerShape(Radius.card),
    large = RoundedCornerShape(Radius.cardLarge),
    extraLarge = RoundedCornerShape(Radius.hero),
)
