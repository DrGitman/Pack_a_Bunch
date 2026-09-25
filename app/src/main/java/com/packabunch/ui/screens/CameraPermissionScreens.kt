package com.packabunch.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.packabunch.ui.components.IconTile
import com.packabunch.ui.components.PackAppBar
import com.packabunch.ui.components.PackIcons
import com.packabunch.ui.components.PrimaryButton
import com.packabunch.ui.components.ScreenScaffold
import com.packabunch.ui.components.SecondaryButton
import com.packabunch.ui.theme.BrandTint
import com.packabunch.ui.theme.BodyInk
import com.packabunch.ui.theme.Ground
import com.packabunch.ui.theme.Outline
import com.packabunch.ui.theme.Primary
import com.packabunch.ui.theme.Spacing
import com.packabunch.ui.theme.Success
import com.packabunch.ui.theme.SurfaceField
import com.packabunch.ui.theme.TextPrimary
import com.packabunch.ui.theme.TextSecondary
import com.packabunch.ui.theme.UiFamily

/*
 * The two faces of the camera permission, one for each side of the system prompt.
 *
 * Which one shows is decided by whether Android has ever been asked on this phone, not by
 * anything about the account: the permission belongs to the install. Before the first ask
 * the sheet explains what the prompt is about; afterwards the only reason to be standing
 * here is that the answer was no, so the page says so and offers the two ways forward.
 */

/**
 * Android will ask for the camera next — `design/artboards/CameraRationale.dc.html`.
 *
 * Shown once, immediately before the system prompt, so the prompt is not the first mention
 * of the camera. The third bullet is the one that matters: saying no costs nothing, and the
 * sheet says that before the prompt takes the decision away.
 */
@Composable
fun CameraRationaleSheet(
    onContinue: () -> Unit,
    onNotNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().background(Ground)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0x802B1D14))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) { onNotNow() },
        )

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.White, RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                .navigationBarsPadding()
                .padding(horizontal = Spacing.gutter)
                .padding(top = 14.dp, bottom = 34.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(width = 40.dp, height = 4.dp)
                    .background(Color(0xFFE2D5C6), RoundedCornerShape(999.dp)),
            )

            Spacer(Modifier.height(22.dp))

            IconTile(
                icon = PackIcons.Camera,
                tint = Primary,
                background = BrandTint,
                size = 88.dp,
                iconSize = 42.dp,
                cornerRadius = 30.dp,
            )

            Spacer(Modifier.height(20.dp))

            Text(
                text = "Android will ask for the camera next",
                color = TextPrimary,
                fontFamily = UiFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 24.sp,
                lineHeight = 30.sp,
                letterSpacing = (-0.5).sp,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(10.dp))

            Text(
                text = "Here's exactly what it's for, so the system prompt isn't a surprise.",
                color = TextSecondary,
                fontFamily = UiFamily,
                fontSize = 14.5f.sp,
                lineHeight = 22.sp,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(20.dp))

            Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
                ReassurancePoint(
                    "Used only while you're measuring a space or taking a photo of an item.",
                    background = SurfaceField,
                    cornerRadius = 18.dp,
                )
                ReassurancePoint(
                    "Nothing is recorded or uploaded. Photos you take stay in this app on this phone.",
                    background = SurfaceField,
                    cornerRadius = 18.dp,
                )
                ReassurancePoint(
                    "Say no and everything still works, you just type the numbers instead.",
                    background = SurfaceField,
                    cornerRadius = 18.dp,
                )
            }

            Spacer(Modifier.height(20.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SecondaryButton(
                    text = "Not now",
                    onClick = onNotNow,
                    modifier = Modifier.weight(1f),
                    contentColor = BodyInk,
                )
                PrimaryButton(
                    text = "Continue",
                    onClick = onContinue,
                    modifier = Modifier.weight(1f),
                    height = 54.dp,
                )
            }
        }
    }
}

/**
 * Camera access is off — `design/artboards/CameraDenied.dc.html`.
 *
 * A dead end only if typing is a dead end, which it isn't, so the primary button is the one
 * that carries on rather than the one that sends you to Android settings.
 */
@Composable
fun CameraDeniedScreen(
    onTypeInstead: () -> Unit,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ScreenScaffold(modifier) {
        PackAppBar(title = "Measure with the camera", onBack = onBack)

        Column(
            Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 0.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(58.dp))

            IconTile(
                icon = PackIcons.CameraOff,
                tint = Primary,
                background = BrandTint,
                size = 96.dp,
                iconSize = 44.dp,
                cornerRadius = 32.dp,
            )

            Spacer(Modifier.height(26.dp))

            Text(
                text = "Camera access is off",
                color = TextPrimary,
                fontFamily = UiFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 25.sp,
                lineHeight = 32.sp,
                letterSpacing = (-0.6).sp,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(11.dp))

            Text(
                text = "Pack a Bunch needs the camera to measure a space by pointing at it. " +
                    "You can turn it on in Android settings, or just type your measurements instead.",
                color = TextSecondary,
                fontFamily = UiFamily,
                fontSize = 15.sp,
                lineHeight = 23.sp,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(Modifier.height(28.dp))

        ReassurancePoint(
            text = "Typing measurements does everything the camera does. " +
                "Nothing in the app is locked behind it.",
            background = Color.White,
            cornerRadius = 22.dp,
            modifier = Modifier.padding(horizontal = Spacing.gutter),
        )

        PushDown()

        Column(
            Modifier.padding(horizontal = Spacing.gutter),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PrimaryButton(text = "Type the measurements", onClick = onTypeInstead)
            SecondaryButton(
                text = "Open Android settings",
                onClick = onOpenSettings,
                backgroundColor = Color.Transparent,
            )
        }

        Spacer(Modifier.height(Spacing.md))
    }
}

/** The ticked line both screens use to say what is, and isn't, given up. */
@Composable
private fun ReassurancePoint(
    text: String,
    background: Color,
    cornerRadius: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .background(background, RoundedCornerShape(cornerRadius))
            .padding(if (background == Color.White) 16.dp else 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            PackIcons.Check,
            contentDescription = null,
            tint = Success,
            modifier = Modifier.size(20.dp).padding(top = 1.dp),
        )
        Text(
            text = text,
            color = TextPrimary,
            fontFamily = UiFamily,
            fontSize = 14.sp,
            lineHeight = 21.sp,
        )
    }
}
