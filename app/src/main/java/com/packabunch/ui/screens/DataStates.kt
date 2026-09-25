package com.packabunch.ui.screens

import com.packabunch.ui.components.swallowTaps
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.packabunch.ui.components.IconTile
import com.packabunch.ui.components.PackCard
import com.packabunch.ui.components.PackIcons
import com.packabunch.ui.components.PackTextButton
import com.packabunch.ui.components.PrimaryButton
import com.packabunch.ui.components.SecondaryButton
import com.packabunch.ui.components.StatTile
import com.packabunch.ui.components.warmShadow
import com.packabunch.ui.motion.pressScale
import com.packabunch.ui.nav.sheetEnter
import com.packabunch.ui.nav.sheetExit
import com.packabunch.ui.theme.BrandTint
import com.packabunch.ui.theme.Accent
import com.packabunch.ui.theme.Chrome
import com.packabunch.ui.theme.HeroBody
import com.packabunch.ui.theme.HeroEyebrow
import com.packabunch.ui.theme.ErrorRed
import com.packabunch.ui.theme.ErrorTint
import com.packabunch.ui.theme.Ground
import com.packabunch.ui.theme.NumeralChip
import com.packabunch.ui.theme.Spacing
import com.packabunch.ui.theme.TextPrimary
import com.packabunch.ui.theme.TextSecondary
import com.packabunch.ui.theme.UiFamily

/**
 * Delete confirmation — `design/artboards/DeleteConfirm.dc.html`.
 *
 * Names the pack and says exactly what goes with it. "Delete this pack?" alone leaves the
 * photos unaccounted for, and a person who assumed those survived has lost something they
 * did not agree to lose.
 */
@Composable
fun DeleteConfirmDialog(
    packName: String,
    itemCount: Int,
    photoCount: Int,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0x942B1D14))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) { onCancel() },
        )

        Column(
            Modifier
                .align(Alignment.Center)
                .padding(horizontal = Spacing.xl)
                .warmShadow(24.dp, RoundedCornerShape(28.dp))
                .background(Color.White, RoundedCornerShape(28.dp))
                .padding(Spacing.lg),
        ) {
            IconTile(
                icon = PackIcons.Trash,
                tint = ErrorRed,
                background = ErrorTint,
                size = 48.dp,
                iconSize = 22.dp,
            )

            Spacer(Modifier.height(Spacing.base))

            Text(
                text = "Delete “$packName”?",
                color = TextPrimary,
                fontFamily = UiFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 21.sp,
                letterSpacing = (-0.4).sp,
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = buildString {
                    append("Its $itemCount ")
                    append(if (itemCount == 1) "item goes" else "items go")
                    if (photoCount > 0) {
                        append(" and $photoCount ")
                        append(if (photoCount == 1) "photo goes" else "photos go")
                    }
                    append(" with it. This can't be undone once you leave the screen.")
                },
                color = TextSecondary,
                fontFamily = UiFamily,
                fontSize = 14.5f.sp,
                lineHeight = 21.sp,
            )

            Spacer(Modifier.height(Spacing.lg))

            PrimaryButton(text = "Delete it", onClick = onConfirm)
            Spacer(Modifier.height(8.dp))
            SecondaryButton(text = "Keep it", onClick = onCancel)
        }
    }
}

/**
 * Deleted, with undo — `design/artboards/DeletedProject.dc.html`.
 *
 * The undo is the real safety net, which is why the confirmation above can stay a single
 * tap. It stays up for [durationMillis] and then the deletion is final.
 */
@Composable
fun DeletedProjectBar(
    packName: String,
    itemCount: Int,
    visible: Boolean,
    onUndo: () -> Unit,
    onExpired: () -> Unit,
    modifier: Modifier = Modifier,
    durationMillis: Long = 6_000L,
) {
    LaunchedEffect(visible, packName) {
        if (!visible) return@LaunchedEffect
        kotlinx.coroutines.delay(durationMillis)
        onExpired()
    }

    AnimatedVisibility(visible = visible, enter = sheetEnter, exit = sheetExit, modifier = modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.base)
                .warmShadow(14.dp, RoundedCornerShape(20.dp))
                .background(Chrome, RoundedCornerShape(20.dp))
                .padding(horizontal = Spacing.base, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Deleted “$packName”",
                    color = Ground,
                    fontFamily = UiFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.5f.sp,
                )
                Text(
                    text = "Its $itemCount ${if (itemCount == 1) "item" else "items"} and " +
                        "photos went with it.",
                    color = Color(0xFFC9B29E),
                    fontFamily = UiFamily,
                    fontSize = 12.5f.sp,
                )
            }
            // A filled pill, not a word: taking the pack back is the whole point of the bar,
            // and it has only a few seconds to be noticed.
            Text(
                text = "Undo",
                color = Chrome,
                fontFamily = UiFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 14.5f.sp,
                modifier = Modifier
                    .pressScale(pressedScale = 0.94f)
                    .background(Accent, RoundedCornerShape(999.dp))
                    .clickable(onClick = onUndo)
                    .padding(horizontal = 22.dp, vertical = 13.dp),
            )
        }
    }
}

/**
 * Interrupted session — `design/artboards/InterruptedSession.dc.html`.
 *
 * Shown when the app was killed mid-pack. Everything up to the last confirmed step is
 * genuinely saved, because the pack is written after every confirmed action rather than on
 * a save button — a session can be interrupted between any two taps.
 */
@Composable
fun InterruptedSessionCard(
    packName: String,
    step: Int,
    totalSteps: Int,
    piecesIn: Int,
    onCarryOn: () -> Unit,
    onStartOver: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(Chrome, RoundedCornerShape(28.dp))
            .padding(Spacing.lg),
    ) {
        Row(
            Modifier
                .background(Color(0x2EE08A46), RoundedCornerShape(999.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            androidx.compose.material3.Icon(
                PackIcons.Clock,
                contentDescription = null,
                tint = HeroEyebrow,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = "PICK UP WHERE YOU LEFT OFF",
                color = HeroEyebrow,
                fontFamily = UiFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 11.sp,
                letterSpacing = 0.6.sp,
            )
        }

        Spacer(Modifier.height(14.dp))

        Text(
            text = "You were half way through $packName",
            modifier = Modifier.widthIn(max = 250.dp),
            color = Ground,
            fontFamily = UiFamily,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 22.sp,
            lineHeight = 28.sp,
            letterSpacing = (-0.5).sp,
        )

        Spacer(Modifier.height(9.dp))

        Text(
            text = "The app closed during packing. Everything up to step $step was saved.",
            modifier = Modifier.widthIn(max = 250.dp),
            color = HeroBody,
            fontFamily = UiFamily,
            fontSize = 14.sp,
            lineHeight = 21.sp,
        )

        Spacer(Modifier.height(14.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DarkChip("Step $step of $totalSteps")
            DarkChip("$piecesIn ${if (piecesIn == 1) "piece" else "pieces"} in")
        }

        Spacer(Modifier.height(18.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Text(
                text = "Carry on packing",
                modifier = Modifier
                    .weight(1f)
                    .pressScale(pressedScale = 0.96f)
                    .background(Accent, RoundedCornerShape(25.dp))
                    .clickable(onClick = onCarryOn)
                    .padding(vertical = 16.dp),
                color = Chrome,
                fontFamily = UiFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 15.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Text(
                text = "Start over",
                modifier = Modifier
                    .pressScale(pressedScale = 0.96f)
                    .border(1.5.dp, Color(0x47F7EFE6), RoundedCornerShape(25.dp))
                    .clickable(onClick = onStartOver)
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                color = Color(0xFFE7D8C7),
                fontFamily = UiFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.5f.sp,
            )
        }
    }
}

/** The translucent cream pill that carries a number on the dark resume card. */
@Composable
private fun DarkChip(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .background(Color(0x24F7EFE6), RoundedCornerShape(999.dp))
            .padding(horizontal = 11.dp, vertical = 6.dp),
        color = Ground,
        fontFamily = UiFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 11.5f.sp,
    )
}

/**
 * Saved-pack limit — `design/artboards/LimitSavedPacks.dc.html`.
 *
 * Unlike the piece cap, there is no pretence about what this is: it is a commercial limit
 * and the screen says so. What it also does is refuse to hold the existing pack hostage —
 * replacing it is offered as a real, free choice.
 */
@Composable
fun LimitSavedPacksSheet(
    existingPackName: String,
    allowed: Int,
    onReplace: () -> Unit,
    onUpgrade: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0x7A2B1D14))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) { onCancel() },
        )

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.White, RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                .swallowTaps()
                .padding(horizontal = Spacing.gutter)
                .padding(top = 14.dp, bottom = 30.dp),
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(width = 40.dp, height = 4.dp)
                        .background(Color(0xFFE2D5C6), RoundedCornerShape(999.dp)),
                )
            }

            Spacer(Modifier.height(Spacing.base))

            Text(
                text = if (allowed == 1) "Free keeps one pack" else "Free keeps $allowed packs",
                color = TextPrimary,
                fontFamily = UiFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 22.sp,
                letterSpacing = (-0.4).sp,
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "You already have “$existingPackName”. You can replace it and " +
                    "carry on for free, or keep both with Pack a Bunch Pro.",
                color = TextSecondary,
                fontFamily = UiFamily,
                fontSize = 14.5f.sp,
                lineHeight = 21.sp,
            )

            Spacer(Modifier.height(Spacing.lg))

            PrimaryButton(text = "Keep both with Pack a Bunch Pro", onClick = onUpgrade)
            Spacer(Modifier.height(8.dp))
            // Replacing is a genuine free route out, not a punishment. The old pack is named
            // so nobody replaces the wrong one by accident.
            SecondaryButton(
                text = "Replace “$existingPackName”",
                onClick = onReplace,
            )
            Spacer(Modifier.height(4.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                PackTextButton(text = "Not now", onClick = onCancel, color = com.packabunch.ui.theme.TextTertiary)
            }
        }
    }
}
