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
import com.packabunch.packing.TierLimits
import com.packabunch.ui.components.Note
import com.packabunch.ui.components.NoteTone
import com.packabunch.ui.components.PackAppBar
import com.packabunch.ui.components.PackIcons
import com.packabunch.ui.components.PrimaryButton
import com.packabunch.ui.components.ScreenHeading
import com.packabunch.ui.components.ScreenScaffold
import com.packabunch.ui.components.StepLabel
import com.packabunch.ui.components.StepProgress
import com.packabunch.ui.components.warmShadow
import com.packabunch.ui.motion.Motion
import com.packabunch.ui.motion.pressScale
import com.packabunch.ui.theme.BrandTint
import com.packabunch.ui.theme.Outline
import com.packabunch.ui.theme.Primary
import com.packabunch.ui.theme.Spacing
import com.packabunch.ui.theme.SurfaceMuted
import com.packabunch.ui.theme.TextPrimary
import com.packabunch.ui.theme.TextSecondary
import com.packabunch.ui.theme.TextTertiary
import com.packabunch.ui.theme.UiFamily

enum class SpaceKind { BOX_SHAPED, ANY_SHAPE }

/**
 * What kind of space — `design/artboards/SpaceType.dc.html`.
 *
 * The fork between three typed numbers and a full sweep. Both are real products: box-shaped
 * is exact and works on every phone, and it is genuinely the right answer for a crate.
 *
 * The caveats on "any shape" are stated before the choice, not discovered after it: it
 * needs a phone that can sense depth and about a minute of sweeping. Letting somebody pick
 * it and only then find out their phone cannot do it would waste the one minute they were
 * willing to give us.
 */
@Composable
fun SpaceTypeScreen(
    selected: SpaceKind,
    depthCapable: Boolean,
    limits: TierLimits,
    onSelect: (SpaceKind) -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ScreenScaffold(modifier) {
        PackAppBar(title = "New pack", onBack = onBack)

        Column(Modifier.padding(horizontal = Spacing.gutter, vertical = 14.dp)) {
            StepProgress(step = 1, totalSteps = 3)
            Spacer(Modifier.height(10.dp))
            StepLabel(step = 1, totalSteps = 3, name = "The space")
            Spacer(Modifier.height(Spacing.sm))
            ScreenHeading("What kind of space is it?")
        }

        Column(
            Modifier.padding(horizontal = Spacing.gutter),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            KindCard(
                icon = PackIcons.Cube,
                title = "Box-shaped",
                body = "A crate, storage box, drawer or shelf. Four straight walls and a " +
                    "flat floor.",
                tags = listOf("Three measurements", "Fastest"),
                selected = selected == SpaceKind.BOX_SHAPED,
                enabled = true,
                onClick = { onSelect(SpaceKind.BOX_SHAPED) },
            )
            KindCard(
                icon = PackIcons.Camera,
                title = "Any shape",
                badge = if (depthCapable) "NEW" else "NOT ON THIS PHONE",
                body = "A car boot, a cupboard with shelves, an awkward corner. Sweep the " +
                    "camera round it and we map what's actually there.",
                tags = listOf("Wheel arches", "Sloping backs", "Shelves"),
                selected = selected == SpaceKind.ANY_SHAPE,
                enabled = depthCapable,
                onClick = { if (depthCapable) onSelect(SpaceKind.ANY_SHAPE) },
            )
        }

        Spacer(Modifier.height(Spacing.base))

        Column(Modifier.padding(horizontal = Spacing.gutter)) {
            if (!depthCapable) {
                Note(
                    text = "This phone can't sense depth, so mapping an irregular space " +
                        "isn't available on it. Box-shaped works on every phone and is exact.",
                    tone = NoteTone.Caution,
                    icon = PackIcons.Info,
                )
            } else {
                Text(
                    text = "Any shape needs a phone that can sense depth, and about a minute " +
                        "of sweeping. Box-shaped works on every phone.",
                    color = TextTertiary,
                    fontFamily = UiFamily,
                    fontSize = 12.5f.sp,
                    lineHeight = 19.sp,
                )
            }

            // The free tier can map a crate but not a car boot, and it says which is which
            // in litres rather than leaving it to be discovered at the kerb.
            if (selected == SpaceKind.ANY_SHAPE && limits.maxScannedSpaceLitres != null) {
                Spacer(Modifier.height(10.dp))
                Note(
                    text = "Free maps spaces up to ${limits.maxScannedSpaceLitres} litres — " +
                        "about a large crate. A car boot needs Pack Plus.",
                    tone = NoteTone.Caution,
                    icon = PackIcons.Lock,
                )
            }
        }

        Spacer(Modifier.weight(1f))

        Column(Modifier.padding(horizontal = Spacing.gutter)) {
            PrimaryButton(
                text = if (selected == SpaceKind.ANY_SHAPE) "Start mapping" else "Next",
                onClick = onContinue,
            )
        }

        Spacer(Modifier.height(Spacing.sm))
    }
}

@Composable
private fun KindCard(
    icon: ImageVector,
    title: String,
    body: String,
    tags: List<String>,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    badge: String? = null,
) {
    val shape = RoundedCornerShape(24.dp)
    val border by animateColorAsState(
        targetValue = if (selected) Primary else Outline,
        animationSpec = Motion.standardTween(Motion.SHORT_MS),
        label = "kindBorder",
    )

    Column(
        Modifier
            .fillMaxWidth()
            .pressScale(pressedScale = 0.99f, enabled = enabled)
            .warmShadow(if (selected) 8.dp else 4.dp, shape)
            .background(Color.White, shape)
            .border(if (selected) 2.dp else 1.dp, border, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(Spacing.base),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).background(BrandTint, RoundedCornerShape(15.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (enabled) Primary else TextTertiary,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.size(12.dp))
            Text(
                text = title,
                color = if (enabled) TextPrimary else TextTertiary,
                fontFamily = UiFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 18.sp,
            )
            if (badge != null) {
                Spacer(Modifier.size(8.dp))
                Text(
                    text = badge,
                    color = if (enabled) Primary else TextTertiary,
                    fontFamily = UiFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 10.sp,
                    letterSpacing = 0.6.sp,
                    modifier = Modifier
                        .background(BrandTint, RoundedCornerShape(999.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        Text(
            text = body,
            color = TextSecondary,
            fontFamily = UiFamily,
            fontSize = 14.sp,
            lineHeight = 21.sp,
        )

        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            tags.forEach { tag ->
                Text(
                    text = tag,
                    color = Color(0xFF6B5849),
                    fontFamily = UiFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.5f.sp,
                    modifier = Modifier
                        .background(SurfaceMuted, RoundedCornerShape(999.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
        }
    }
}
