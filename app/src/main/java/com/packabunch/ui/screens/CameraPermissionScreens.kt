package com.packabunch.ui.screens

import com.packabunch.ui.components.swallowTaps
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import com.packabunch.ui.theme.SurfaceSunken
import com.packabunch.ui.theme.TextTertiary
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
                .swallowTaps()
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

/**
 * One more thing to install — `design/artboards/ArServicesInstall.dc.html`.
 *
 * A 100 MB download is a real thing to ask for, so the size is named and the way out is
 * offered in the same breath rather than buried under the install button.
 */
@Composable
fun ArServicesInstallScreen(
    onInstall: () -> Unit,
    onTypeInstead: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ScreenScaffold(modifier) {
        PackAppBar(title = "Measure with the camera", onBack = onBack)

        GateHeader(
            icon = PackIcons.Download,
            tint = Primary,
            tile = BrandTint,
            title = "One more thing to install",
            body = "Camera measuring uses Google Play Services for AR. It's a free Google " +
                "download and this phone doesn't have it yet.",
            topGap = 56.dp,
        )

        Spacer(Modifier.height(26.dp))

        Row(
            Modifier
                .padding(horizontal = Spacing.gutter)
                .fillMaxWidth()
                .background(Color.White, RoundedCornerShape(22.dp))
                .padding(Spacing.base),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            IconTile(
                icon = PackIcons.PlayStore,
                tint = BodyInk,
                background = SurfaceSunken,
                size = 46.dp,
                iconSize = 24.dp,
                cornerRadius = 15.dp,
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Google Play Services for AR",
                    color = TextPrimary,
                    fontFamily = UiFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.5f.sp,
                )
                Text(
                    text = "Made by Google · about 100 MB",
                    modifier = Modifier.padding(top = 2.dp),
                    color = Color(0xFF8A7565),
                    fontFamily = UiFamily,
                    fontSize = 12.5f.sp,
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        Text(
            text = "You don't have to install anything. Typed measurements work right now and " +
                "give you the same plan.",
            modifier = Modifier
                .padding(horizontal = Spacing.gutter)
                .fillMaxWidth()
                .background(SurfaceSunken, RoundedCornerShape(20.dp))
                .padding(14.dp),
            color = Color(0xFF6B5849),
            fontFamily = UiFamily,
            fontSize = 13.5f.sp,
            lineHeight = 20.sp,
        )

        PushDown()

        Column(
            Modifier.padding(horizontal = Spacing.gutter),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PrimaryButton(text = "Get it from Google Play", onClick = onInstall)
            SecondaryButton(
                text = "Type the measurements instead",
                onClick = onTypeInstead,
                backgroundColor = Color.Transparent,
            )
        }

        Spacer(Modifier.height(Spacing.md))
    }
}

/**
 * This phone can't measure by camera — `design/artboards/ArUnavailable.dc.html`.
 *
 * The only one of these three with nothing to try. It says the limit is the hardware so
 * nobody goes looking for an update, then spends the rest of the page on what still works.
 */
@Composable
fun ArUnavailableScreen(
    onTypeInstead: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ScreenScaffold(modifier) {
        PackAppBar(title = "Measure with the camera", onBack = onBack)

        GateHeader(
            icon = PackIcons.PhoneOff,
            tint = Color(0xFF8A7565),
            tile = SurfaceSunken,
            title = "This phone can't measure by camera",
            body = "Camera measuring needs depth sensing that this phone doesn't provide. " +
                "That's a hardware limit, not something an update will fix.",
            topGap = 54.dp,
        )

        Spacer(Modifier.height(26.dp))

        Column(
            Modifier
                .padding(horizontal = Spacing.gutter)
                .fillMaxWidth()
                .background(Color.White, RoundedCornerShape(22.dp))
                .padding(18.dp),
        ) {
            Text(
                text = "Everything else works normally",
                color = TextPrimary,
                fontFamily = UiFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 14.5f.sp,
            )
            Spacer(Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                StillWorks("Typed measurements, in centimetres or inches")
                StillWorks("Photos of your items")
                StillWorks("The arrangement and the step-by-step packing guide")
            }
        }

        PushDown()

        Column(Modifier.padding(horizontal = Spacing.gutter)) {
            PrimaryButton(text = "Type the measurements", onClick = onTypeInstead)
            Spacer(Modifier.height(10.dp))
            Text(
                text = "We won't ask you about camera measuring again on this phone.",
                modifier = Modifier.fillMaxWidth(),
                color = TextTertiary,
                fontFamily = UiFamily,
                fontSize = 12.5f.sp,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(Modifier.height(Spacing.md))
    }
}

/** A ticked line with no card of its own, for the list inside the "still works" card. */
@Composable
private fun StillWorks(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
        Icon(
            PackIcons.Check,
            contentDescription = null,
            tint = Success,
            modifier = Modifier.size(19.dp).padding(top = 2.dp),
        )
        Text(
            text = text,
            color = BodyInk,
            fontFamily = UiFamily,
            fontSize = 14.sp,
            lineHeight = 21.sp,
        )
    }
}

/** The tile, headline and paragraph every one of these gate pages opens with. */
@Composable
private fun ColumnScope.GateHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    tile: Color,
    title: String,
    body: String,
    topGap: androidx.compose.ui.unit.Dp,
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(topGap))
        IconTile(
            icon = icon,
            tint = tint,
            background = tile,
            size = 96.dp,
            iconSize = 44.dp,
            cornerRadius = 32.dp,
        )
        Spacer(Modifier.height(26.dp))
        Text(
            text = title,
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
            text = body,
            color = TextSecondary,
            fontFamily = UiFamily,
            fontSize = 15.sp,
            lineHeight = 23.sp,
            textAlign = TextAlign.Center,
        )
    }
}
