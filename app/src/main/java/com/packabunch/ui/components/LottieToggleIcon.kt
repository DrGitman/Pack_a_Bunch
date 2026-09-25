package com.packabunch.ui.components

import androidx.annotation.RawRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.rememberLottieComposition

/*
 * A Lottie icon used as a two-state toggle rather than a tap flourish.
 *
 * [LottieTapIcon] always plays forward and settles back on frame 0, which is right for a button
 * but wrong for a switch: a switch has to stay where you put it. This one treats the timeline as
 * the distance between two states — off is frame 0, on is the last frame — and scrubs between
 * them, so turning it off plays the animation backwards and lands exactly on the resting icon.
 *
 * Used for the password field's eye ("Icon eye · Motion"), where frame 0 is the plain eye and the
 * end of the timeline is the eye struck through, with the wink happening in between.
 */

private val Standard = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

@Composable
fun LottieToggleIcon(
    @RawRes animation: Int,
    on: Boolean,
    onToggle: () -> Unit,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    /** The area that takes the tap. Kept at 44dp so the control is hittable at any icon size. */
    touchTarget: Dp = 44.dp,
    tint: Color? = null,
    enabled: Boolean = true,
) {
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(animation))
    val progress = remember { Animatable(if (on) 1f else 0f) }

    // Played faster than its authored 750ms: the password text flips the instant you tap, so a
    // slower icon reads as lag rather than as motion. This lands with the text, not after it.
    val durationMillis = 240

    LaunchedEffect(on, composition) {
        if (composition == null) return@LaunchedEffect
        progress.animateTo(
            targetValue = if (on) 1f else 0f,
            animationSpec = tween(durationMillis, easing = Standard),
        )
    }

    val interaction = remember { MutableInteractionSource() }

    // The icon takes only its own size in the layout; the tap area overflows around it with
    // requiredSize, so a comfortable target never makes the row it sits in taller.
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .requiredSize(touchTarget)
                // The press squash lives here rather than in the Lottie file on purpose. Baked
                // into the timeline it would play backwards too, landing after the reveal instead
                // of on the tap; driven from the gesture it fires on touch-down in both directions.
                .pressScale(interaction, pressedScale = 0.9f)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .clickable(
                    interactionSource = interaction,
                    indication = LocalIndication.current,
                    enabled = enabled,
                    onClick = onToggle,
                )
                .semantics { contentDescription?.let { this.contentDescription = it } },
            contentAlignment = Alignment.Center,
        ) {
            LottieAnimation(
                composition = composition,
                progress = { progress.value },
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
}

/**
 * The password field's reveal control.
 *
 * @param visible true when the password is currently shown in clear text — which is when the eye
 *   wears its strike-through, since the icon describes what tapping it would undo.
 */
@Composable
fun PasswordVisibilityToggle(
    visible: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color(0xFF8A7364),
    size: Dp = 30.dp,
) {
    LottieToggleIcon(
        animation = com.packabunch.R.raw.icon_eye,
        on = visible,
        onToggle = onToggle,
        contentDescription = if (visible) "Hide password" else "Show password",
        modifier = modifier,
        size = size,
        tint = tint,
    )
}
