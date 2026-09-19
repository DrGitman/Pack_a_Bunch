package com.packabunch.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import com.packabunch.ui.nav.sheetEnter
import com.packabunch.ui.nav.sheetExit
import com.packabunch.ui.theme.BrandTint
import com.packabunch.ui.theme.Chrome
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
            PackTextButton(text = "Undo", onClick = onUndo, color = Color(0xFFE08A46))
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
    Column(modifier.fillMaxWidth()) {
        Text(
            text = "PICK UP WHERE YOU LEFT OFF",
            color = com.packabunch.ui.theme.TextTertiary,
            fontFamily = UiFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 11.5f.sp,
            letterSpacing = 0.8.sp,
        )

        Spacer(Modifier.height(10.dp))

        PackCard(elevation = 10.dp, contentPadding = Spacing.base, background = BrandTint) {
            Text(
                text = "You were half way through $packName",
                color = TextPrimary,
                fontFamily = UiFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "The app closed during packing. Everything up to step $step was saved.",
                color = TextSecondary,
                fontFamily = UiFamily,
                fontSize = 13.5f.sp,
                lineHeight = 20.sp,
            )

            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                StatTile(
                    value = "$step/$totalSteps",
                    caption = "step",
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    value = piecesIn.toString(),
                    caption = if (piecesIn == 1) "piece in" else "pieces in",
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(12.dp))

            PrimaryButton(text = "Carry on packing", onClick = onCarryOn)
            Spacer(Modifier.height(8.dp))
            SecondaryButton(text = "Start over", onClick = onStartOver)
        }
    }
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
                    "carry on for free, or keep both with Pack-a-Bunch Pro.",
                color = TextSecondary,
                fontFamily = UiFamily,
                fontSize = 14.5f.sp,
                lineHeight = 21.sp,
            )

            Spacer(Modifier.height(Spacing.lg))

            PrimaryButton(text = "Keep both with Pack-a-Bunch Pro", onClick = onUpgrade)
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
