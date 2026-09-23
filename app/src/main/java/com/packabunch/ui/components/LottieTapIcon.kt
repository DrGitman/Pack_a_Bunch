package com.packabunch.ui.components

import androidx.annotation.RawRes
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
 * Icons drawn by Lottie files exported from the real artwork in Figma ("Icon … · Motion" on the
 * Animations page), so frame 0 is pixel-identical to the static icon each one replaces.
 *
 * LottieTapIcon plays once, from the start, on every tap, and is perfectly still the rest of the
 * time. EmptyCrate draws itself in once and then breathes for as long as the screen is empty.
 */

@Composable
fun LottieTapIcon(
    @RawRes animation: Int,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    enabled: Boolean = true,
) {
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(animation))
    var playing by remember { mutableStateOf(false) }

    val progress by animateLottieCompositionAsState(
        composition = composition,
        isPlaying = playing,
        restartOnPlay = true,
        iterations = 1,
    )
    LaunchedEffect(progress, playing) {
        if (playing && progress >= 1f) playing = false     // settle back on the resting frame
    }

    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .size(size)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                enabled = enabled,
            ) {
                playing = true
                onClick()
            }
            .semantics { contentDescription?.let { this.contentDescription = it } },
        contentAlignment = Alignment.Center,
    ) {
        LottieAnimation(
            composition = composition,
            progress = { if (playing) progress else 0f },
            modifier = Modifier.size(size),
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
