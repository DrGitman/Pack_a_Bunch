package com.packabunch.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.packabunch.packing.OpeningCheck
import com.packabunch.ui.components.IconTile
import com.packabunch.ui.components.Note
import com.packabunch.ui.components.NoteTone
import com.packabunch.ui.components.PackAppBar
import com.packabunch.ui.components.PackIcons
import com.packabunch.ui.components.PrimaryButton
import com.packabunch.ui.components.ScreenHeading
import com.packabunch.ui.components.ScreenScaffold
import com.packabunch.ui.components.SecondaryButton
import com.packabunch.ui.components.SectionHeading
import com.packabunch.ui.format.LengthUnit
import com.packabunch.ui.format.formatLength
import com.packabunch.ui.theme.ErrorRed
import com.packabunch.ui.theme.ErrorTint
import com.packabunch.ui.theme.NumeralLarge
import com.packabunch.ui.theme.Primary
import com.packabunch.ui.theme.Spacing
import com.packabunch.ui.theme.SurfaceField
import com.packabunch.ui.theme.TextPrimary
import com.packabunch.ui.theme.TextSecondary
import com.packabunch.ui.theme.TextTertiary
import com.packabunch.ui.theme.UiFamily

/**
 * It won't go through — `design/artboards/OpeningTooSmall.dc.html`.
 *
 * The one failure the volume model cannot see. There is room inside and the item still
 * cannot get in, so this screen exists to say that plainly and then offer the three things
 * that actually help.
 *
 * The numbers are the item's smallest cross-section against the opening — the best case
 * after checking all six orientations, not a convenient pairing.
 */
@Composable
fun OpeningTooSmallScreen(
    itemName: String,
    check: OpeningCheck,
    otherPiecesStillFit: Int,
    unit: LengthUnit,
    onPlanWithout: () -> Unit,
    onMeasureOpening: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ScreenScaffold(modifier) {
        PackAppBar(title = "It won't go through", onBack = onBack)

        Column(Modifier.padding(horizontal = Spacing.gutter)) {
            Spacer(Modifier.height(Spacing.sm))

            IconTile(
                icon = PackIcons.Warning,
                tint = ErrorRed,
                background = ErrorTint,
                size = 56.dp,
                iconSize = 26.dp,
            )

            Spacer(Modifier.height(Spacing.base))

            ScreenHeading("There's room inside — but no way in")

            Spacer(Modifier.height(10.dp))

            Text(
                text = "The $itemName fits in the space. It doesn't fit through the opening " +
                    "at any angle we tried.",
                color = TextSecondary,
                fontFamily = UiFamily,
                fontSize = 15.sp,
                lineHeight = 23.sp,
            )
        }

        Spacer(Modifier.height(Spacing.lg))

        Column(Modifier.padding(horizontal = Spacing.gutter)) {
            SectionHeading("The tight direction")
            Spacer(Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ComparisonTile(
                    label = "OPENING",
                    value = "${formatLength(check.openingWidthMm, unit)} × " +
                        "${formatLength(check.openingHeightMm, unit)}",
                    unitLabel = unit.shortLabel,
                    modifier = Modifier.weight(1f),
                    highlight = false,
                )
                ComparisonTile(
                    label = "SMALLEST FACE",
                    value = "${formatLength(check.tightestFaceWidthMm, unit)} × " +
                        "${formatLength(check.tightestFaceHeightMm, unit)}",
                    unitLabel = unit.shortLabel,
                    modifier = Modifier.weight(1f),
                    highlight = true,
                )
            }

            Spacer(Modifier.height(10.dp))

            Text(
                text = if (check.diagonalMightWork) {
                    "All six orientations checked. Corner-first isn't ruled out by the " +
                        "arithmetic, but we can't promise the manoeuvre exists."
                } else {
                    "All six orientations checked, plus tilting it corner-first."
                },
                color = TextTertiary,
                fontFamily = UiFamily,
                fontSize = 12.5f.sp,
                lineHeight = 19.sp,
            )
        }

        Spacer(Modifier.height(Spacing.lg))

        Column(Modifier.padding(horizontal = Spacing.gutter)) {
            SectionHeading("What usually works")
            Spacer(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Suggestion("Fold the back seats down — the opening changes with them")
                Suggestion("Re-measure the opening — ours came from the scan, not a tape")
                Suggestion(
                    if (otherPiecesStillFit > 0) {
                        "Take it out of this pack — the other $otherPiecesStillFit still fit"
                    } else {
                        "Take it out of this pack"
                    },
                )
            }
        }

        Spacer(Modifier.weight(1f))

        Column(
            Modifier.padding(horizontal = Spacing.gutter),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PrimaryButton(text = "Plan without the $itemName", onClick = onPlanWithout)
            SecondaryButton(
                text = "Measure the opening myself",
                onClick = onMeasureOpening,
                icon = PackIcons.Ruler,
            )
        }

        Spacer(Modifier.height(Spacing.sm))
    }
}

@Composable
private fun ComparisonTile(
    label: String,
    value: String,
    unitLabel: String,
    highlight: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .background(
                if (highlight) ErrorTint else SurfaceField,
                RoundedCornerShape(20.dp),
            )
            .padding(14.dp),
    ) {
        Text(
            text = label,
            color = if (highlight) ErrorRed else TextTertiary,
            fontFamily = UiFamily,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 10.5f.sp,
            letterSpacing = 0.7.sp,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = value,
            style = NumeralLarge.copy(fontSize = 19.sp),
            color = if (highlight) Color(0xFF8E3322) else TextPrimary,
        )
        Text(
            text = unitLabel,
            color = TextTertiary,
            fontFamily = UiFamily,
            fontSize = 11.5f.sp,
        )
    }
}

@Composable
private fun Suggestion(text: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(18.dp))
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Icon(
            PackIcons.Check,
            contentDescription = null,
            tint = Primary,
            modifier = Modifier.size(18.dp),
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
