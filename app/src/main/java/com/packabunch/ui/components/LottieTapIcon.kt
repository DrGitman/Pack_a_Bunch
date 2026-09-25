package com.packabunch.ui.components

import androidx.annotation.RawRes
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieClipSpec
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition

/*
 * Icons drawn by Lottie files exported from the artwork in Figma ("Icon … · Motion" on the
 * Animations page), so frame 0 is pixel-identical to the static icon each one replaces.
 *
 * The single-colour icons (gear, account, bell, package, restore, info, the Projects mark) are
 * exported in one flat colour and recoloured here with [tint], so the same file serves the cream
 * version in the bar and the terracotta version inside the white pill, and can cross-fade between
 * them. Leave [tint] null for multi-colour files like the orange + button.
 */

@Composable
fun LottieTapIcon(
    @RawRes animation: Int,
    contentDescription: String?,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    tint: Color? = null,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    /** Play once as soon as it appears, as well as on tap. Used when an icon swaps into the pill. */
    playOnAppear: Boolean = false,
) {
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(animation))
    var playing by remember { mutableStateOf(playOnAppear) }

    val progress by animateLottieCompositionAsState(
        composition = composition,
        isPlaying = playing,
        restartOnPlay = true,
        iterations = 1,
    )
    LaunchedEffect(progress, playing) {
        if (playing && progress >= 1f) playing = false     // settle back on the resting frame
    }

    val ownSource = remember { MutableInteractionSource() }
    val interaction = interactionSource ?: ownSource

    // An icon sitting inside a bigger tap target (a settings row, say) isn't clickable itself.
    // It borrows the row's interaction source instead, so a touch anywhere on the row plays it.
    if (onClick == null && interactionSource != null) {
        LaunchedEffect(interaction) {
            interaction.interactions.collect { if (it is PressInteraction.Press) playing = true }
        }
    }

    val clicks = if (onClick == null) Modifier else Modifier.clickable(
        interactionSource = interaction,
        indication = LocalIndication.current,
        enabled = enabled,
    ) {
        playing = true
        onClick()
    }

    Box(
        modifier = modifier
            .size(size)
            .then(clicks)
            .semantics { contentDescription?.let { this.contentDescription = it } },
        contentAlignment = Alignment.Center,
    ) {
        LottieAnimation(
            composition = composition,
            progress = { if (playing) progress else 0f },
            modifier = Modifier
                .size(size)
                .then(
                    if (tint == null) Modifier else Modifier
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            drawRect(tint, blendMode = BlendMode.SrcIn)
                        },
                ),
        )
    }
}

/** The empty-state crate: plays the `intro` marker once, then loops `loop` forever. */
@Composable
fun EmptyCrate(
    @RawRes animation: Int,
    modifier: Modifier = Modifier,
    size: Dp = 138.dp,
) {
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(animation))
    var introDone by remember { mutableStateOf(false) }

    val progress by animateLottieCompositionAsState(
        composition = composition,
        clipSpec = LottieClipSpec.Marker(if (introDone) "loop" else "intro"),
        iterations = if (introDone) LottieConstants.IterateForever else 1,
    )
    LaunchedEffect(progress, introDone) {
        if (!introDone && progress >= 1f) introDone = true
    }

    LottieAnimation(
        composition = composition,
        progress = { progress },
        modifier = modifier.size(size),
    )
}
