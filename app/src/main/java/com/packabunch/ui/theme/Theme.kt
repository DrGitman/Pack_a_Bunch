package com.packabunch.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/**
 * One light theme, deliberately. The palette is a warm cream ground with terracotta
 * primary and deep-brown chrome — see docs/design-tokens.json, which these files are
 * generated from. There is no dynamic colour: the item identity colours and the
 * provenance badges have to stay exactly as specified or they stop meaning anything.
 */
@Composable
fun PackABunchTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PackABunchColors,
        typography = PackABunchTypography,
        shapes = PackABunchShapes,
        content = content,
    )
}
