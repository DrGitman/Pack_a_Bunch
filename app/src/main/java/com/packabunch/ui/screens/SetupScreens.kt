package com.packabunch.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.packabunch.ui.components.IconTile
import com.packabunch.ui.components.PackIcons
import com.packabunch.ui.components.PackTextButton
import com.packabunch.ui.components.PrimaryButton
import com.packabunch.ui.components.ScreenHeading
import com.packabunch.ui.components.ScreenScaffold
import com.packabunch.ui.components.SecondaryButton
import com.packabunch.ui.components.SectionHeading
import com.packabunch.ui.components.UnitToggle
import com.packabunch.ui.format.LengthUnit
import com.packabunch.ui.motion.Motion
import com.packabunch.ui.motion.pressScale
import com.packabunch.ui.theme.BrandTint
import com.packabunch.ui.theme.Outline
import com.packabunch.ui.theme.Primary
import com.packabunch.ui.theme.Spacing
import com.packabunch.ui.theme.TextPrimary
import com.packabunch.ui.theme.TextSecondary
import com.packabunch.ui.theme.TextTertiary
import com.packabunch.ui.theme.UiFamily

/** What somebody mostly packs. Only ever changes which presets are suggested first. */
enum class PackingHabit(val label: String, val detail: String, val icon: ImageVector) {
    MOVING_HOUSE("Moving house", "Crates and cardboard boxes", PackIcons.Cube),
    LOADING_A_CAR("Loading a car", "Boots, roof boxes, trailers", PackIcons.Camera),
    STORAGE("Storage and shelves", "Bins, drawers, cupboards", PackIcons.Projects),
}

/**
 * Units and typical use — `design/artboards/OnbSetup.dc.html`.
 *
 * Two questions, both reversible, and the screen says so twice: "both of these are in
 * Settings later" and "this only changes the presets we suggest first". Neither answer
 * changes any measurement, any calculation, or anything about what the app can do — and an
 * onboarding question that quietly did would be a trap.
 */
@Composable
fun OnbSetupScreen(
    unit: LengthUnit,
    habit: PackingHabit?,
    onUnitChange: (LengthUnit) -> Unit,
    onHabitChange: (PackingHabit) -> Unit,
    onContinue: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    ArtboardPage(modifier) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter, vertical = 6.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            if (onBack != null) com.packabunch.ui.components.PackIconButton(PackIcons.Back, "Back", onBack)
            Spacer(Modifier.weight(1f))
            PackTextButton(text = "Skip", onClick = onSkip, color = TextTertiary)
        }

        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp)) {
            Text(
                text = "TWO QUICK ONES",
                color = Primary,
                fontFamily = UiFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 12.5f.sp,
                letterSpacing = 1.2.sp,
            )
            Spacer(Modifier.height(12.dp))
            Text("Set it up the way you measure", color = TextPrimary, fontFamily = UiFamily,
                fontSize = 30.sp, lineHeight = 37.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp)
            Spacer(Modifier.height(11.dp))
            Text(
                text = "Both of these are in Settings later, so nothing here is a commitment.",
                color = TextSecondary,
                fontFamily = UiFamily,
                fontSize = 14.5f.sp,
                lineHeight = 21.sp,
            )
        }

        Spacer(Modifier.height(Spacing.xl))

        Column(Modifier.padding(horizontal = Spacing.gutter)) {
            SectionHeading("Measure in")
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LengthUnit.entries.forEach { candidate ->
                    Column(Modifier.weight(1f).background(Color.White, RoundedCornerShape(22.dp))
                        .border(if (unit == candidate) 2.dp else 1.dp, if (unit == candidate) Primary else Outline, RoundedCornerShape(22.dp))
                        .clickable { onUnitChange(candidate) }.padding(horizontal = 14.dp, vertical = 18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(candidate.shortLabel, color = TextPrimary, fontSize = 24.sp, fontFamily = com.packabunch.ui.theme.NumericFamily)
                        Spacer(Modifier.height(6.dp))
                        Text(if (candidate == LengthUnit.CENTIMETRES) "Centimetres" else "Inches", color = TextSecondary, fontFamily = UiFamily, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                }
            }
        }

        Spacer(Modifier.height(Spacing.lg))

        Column(Modifier.padding(horizontal = Spacing.gutter)) {
            SectionHeading("What are you packing, mostly?")
            Spacer(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PackingHabit.entries.forEach { candidate ->
                    HabitRow(
                        habit = candidate,
                        selected = habit == candidate,
                        onClick = { onHabitChange(candidate) },
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = "This only changes the presets we suggest first.",
                color = TextTertiary,
                fontFamily = UiFamily,
                fontSize = 12.5f.sp,
            )
        }

        Spacer(Modifier.weight(1f))

        Column(Modifier.padding(horizontal = Spacing.gutter)) {
            PrimaryButton(text = "Continue", onClick = onContinue)
        }

        Spacer(Modifier.height(Spacing.base))
    }
}

@Composable
private fun HabitRow(habit: PackingHabit, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    val border by animateColorAsState(
        targetValue = if (selected) Primary else Outline,
        animationSpec = Motion.standardTween(Motion.SHORT_MS),
        label = "habitBorder",
    )
    val background by animateColorAsState(
        targetValue = Color.White,
        animationSpec = Motion.standardTween(Motion.SHORT_MS),
        label = "habitBackground",
    )

    Row(
        Modifier
            .fillMaxWidth()
            .pressScale(pressedScale = 0.99f)
            .background(background, shape)
            .border(if (selected) 2.dp else 1.dp, border, shape)
            .clickable(onClick = onClick)
            .padding(15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Box(Modifier.size(44.dp).background(BrandTint, RoundedCornerShape(15.dp)), contentAlignment = Alignment.Center) {
            Icon(habit.icon, contentDescription = null, tint = Primary, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = habit.label,
                color = TextPrimary,
                fontFamily = UiFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
            )
            Text(
                text = habit.detail,
                color = TextTertiary,
                fontFamily = UiFamily,
                fontSize = 12.5f.sp,
            )
        }
        if (selected) {
            Icon(
                PackIcons.Check,
                contentDescription = null,
                tint = Primary,
                modifier = Modifier.size(19.dp),
            )
        }
    }
}

/**
 * Notification rationale — `design/artboards/OnbNotifPrompt.dc.html`.
 *
 * Shown before Android's `POST_NOTIFICATIONS` prompt, and **never on first launch** — asking
 * for permission to send messages before the app has done anything useful is how the system
 * prompt gets denied permanently.
 *
 * It lists every kind of notification that exists. Not a summary, not "and more": if a type
 * is not on this list it should not exist, which is the same rule the settings screen holds.
 */
@Composable
fun NotificationRationaleScreen(
    onAllow: () -> Unit,
    onNotNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ArtboardPage(modifier) {
        Spacer(Modifier.height(Spacing.xxl))

        Column(Modifier.padding(horizontal = Spacing.gutter)) {
            IconTile(
                icon = PackIcons.Bell,
                tint = Primary,
                background = BrandTint,
                size = 56.dp,
                iconSize = 26.dp,
            )

            Spacer(Modifier.height(Spacing.base))
            ScreenHeading("Can we let you know about your packs?")
            Spacer(Modifier.height(10.dp))
            Text(
                text = "This is the complete list. There is nothing else, and no marketing.",
                color = TextSecondary,
                fontFamily = UiFamily,
                fontSize = 15.sp,
                lineHeight = 23.sp,
            )

            Spacer(Modifier.height(Spacing.base))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NotificationKind("A pack you left half-finished", "Once, the day after you stop")
                NotificationKind("Backup finished or failed", "Only when it needs you")
                NotificationKind("Subscription and billing", "Renewals, failed payments, expiry")
                NotificationKind("New kinds of space", "A few times a year at most")
                NotificationKind("How a pack went", "Helps us check the model against real crates")
            }

            Spacer(Modifier.height(Spacing.base))

            Text(
                text = "Every one of these can be switched off separately, and saying no here " +
                    "changes nothing about how the app works.",
                color = TextTertiary,
                fontFamily = UiFamily,
                fontSize = 12.5f.sp,
                lineHeight = 19.sp,
            )
        }

        Spacer(Modifier.weight(1f))

        Column(
            Modifier.padding(horizontal = Spacing.gutter),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PrimaryButton(text = "Yes, that's fine", onClick = onAllow)
            SecondaryButton(text = "Not now", onClick = onNotNow)
        }

        Spacer(Modifier.height(Spacing.base))
    }
}

@Composable
private fun NotificationKind(title: String, detail: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(18.dp))
            .padding(15.dp),
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Icon(
            PackIcons.Check,
            contentDescription = null,
            tint = Primary,
            modifier = Modifier.size(17.dp),
        )
        Column {
            Text(
                text = title,
                color = TextPrimary,
                fontFamily = UiFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
            )
            Text(
                text = detail,
                color = TextTertiary,
                fontFamily = UiFamily,
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )
        }
    }
}



