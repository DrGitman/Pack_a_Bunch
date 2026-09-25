package com.packabunch.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/**
 * One light theme, deliberately. The palette is a warm cream ground with terracotta
 * primary and deep-brown chrome — see docs/design-tokens.json, which these files are
 * generated from. There is no dynamic colour: the item identity colours and the
 * provenance badges have to stay exactly as specified or they stop meaning anything.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PackABunchTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PackABunchColors,
        typography = PackABunchTypography,
        shapes = PackABunchShapes,
    ) {
        // Android's default press ripple is grey, which reads as a dirty box on a cream ground.
        // Set once here so every tappable thing in the app presses in the brand's terracotta.
        // Two ripples exist: Material's, and the plain one every bare `clickable` uses. The
        // second is grey and ignores the first's configuration, which is why presses kept
        // showing a grey box. Both are set here so a press is terracotta everywhere.
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalRippleConfiguration provides
                androidx.compose.material3.RippleConfiguration(color = Primary),
            androidx.compose.foundation.LocalIndication provides
                androidx.compose.material3.ripple(color = Primary),
            content = content,
        )
    }
}
