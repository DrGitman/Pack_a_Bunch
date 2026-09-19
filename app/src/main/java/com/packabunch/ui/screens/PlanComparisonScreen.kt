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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.packabunch.packing.TierLimits
import com.packabunch.ui.components.PackAppBar
import com.packabunch.ui.components.PackIcons
import com.packabunch.ui.components.PackTextButton
import com.packabunch.ui.components.PrimaryButton
import com.packabunch.ui.components.ScreenScaffold
import com.packabunch.ui.theme.BrandTint
import com.packabunch.ui.theme.Divider
import com.packabunch.ui.theme.NumeralChip
import com.packabunch.ui.theme.Primary
import com.packabunch.ui.theme.Spacing
import com.packabunch.ui.theme.Success
import com.packabunch.ui.theme.TextPrimary
import com.packabunch.ui.theme.TextSecondary
import com.packabunch.ui.theme.TextTertiary
import com.packabunch.ui.theme.UiFamily

/**
 * Free vs Pack-a-Bunch Pro — `design/artboards/PlanComparison.dc.html`.
 *
 * ### One line in the artboard had to change
 *
 * It labelled the piece cap *"a solver limit, not a price one"*. That was true when both
 * tiers stopped at twenty; it is not true now that Plus lifts it. It is the same claim
 * `LimitPieces` made and the same reason it had to be rewritten — a comparison table is
 * exactly where somebody checks whether they are being told the truth, so a false line here
 * costs more than the feature earns.
 *
 * What replaces it is the honest version: twenty is where the free plan stops, there is a
 * separate technical ceiling that applies to everybody, and both are stated.
 *
 * The closing paragraph is the important part of the screen and is unchanged: paying does
 * not make the measurements better or the packing cleverer.
 */
private data class ComparisonRow(
    val label: String,
    val detail: String?,
    val free: String,
    val plus: String,
    val freeIsYes: Boolean = false,
    val plusIsYes: Boolean = false,
)

@Composable
fun PlanComparisonScreen(
    price: String?,
    onSubscribe: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val free = TierLimits.FREE
    val plus = TierLimits.PLUS

    val rows = listOf(
        ComparisonRow(
            label = "Measure and scan a space",
            detail = "Typed, camera, or a full sweep",
            free = "", plus = "",
            freeIsYes = true, plusIsYes = true,
        ),
        ComparisonRow(
            label = "Arrangement and packing guide",
            detail = "The whole thing, every step",
            free = "", plus = "",
            freeIsYes = true, plusIsYes = true,
        ),
        ComparisonRow(
            label = "Pieces in one pack",
            // The honest framing: the free number is commercial, the ceiling is not.
            detail = "Free stops at ${free.maxPiecesPerPack}. Past a few hundred the search " +
                "can't return anything worth trusting — that limit is on both.",
            free = "${free.maxPiecesPerPack}",
            plus = "∞",
        ),
        ComparisonRow(
            label = "Size of space you can scan",
            detail = "A crate is 50–80 L. A car boot is 300–500 L.",
            free = "${free.maxScannedSpaceLitres} L",
            plus = "Any",
        ),
        ComparisonRow(
            label = "Scans a day",
            detail = "Sweeping costs battery and compute",
            free = "${free.maxScansPerDay}",
            plus = "∞",
        ),
        ComparisonRow(
            label = "Packs you can keep",
            detail = null,
            free = "${free.maxSavedPacks}",
            plus = "∞",
        ),
        ComparisonRow(
            label = "Reuse items across packs",
            detail = "Measure a thing once",
            free = "—",
            plus = "",
            plusIsYes = true,
        ),
    )

    ScreenScaffold(modifier) {
        PackAppBar(title = "Free and Pack-a-Bunch Pro", onBack = onBack)

        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.gutter),
        ) {
            Spacer(Modifier.height(Spacing.sm))

            Text(
                text = "Everything that makes the app work is free. Pack-a-Bunch Pro is for keeping " +
                    "things, and for the big spaces.",
                color = TextSecondary,
                fontFamily = UiFamily,
                fontSize = 15.sp,
                lineHeight = 23.sp,
            )

            Spacer(Modifier.height(Spacing.lg))

            Row(Modifier.fillMaxWidth()) {
                Spacer(Modifier.weight(1f))
                ColumnHeading("FREE", Modifier.width(64.dp), TextTertiary)
                ColumnHeading("PRO", Modifier.width(64.dp), Primary)
            }

            Spacer(Modifier.height(6.dp))

            rows.forEach { row ->
                Box(Modifier.fillMaxWidth().height(1.dp).background(Divider))
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f).padding(end = 10.dp)) {
                        Text(
                            text = row.label,
                            color = TextPrimary,
                            fontFamily = UiFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.5f.sp,
                        )
                        row.detail?.let {
                            Text(
                                text = it,
                                color = TextTertiary,
                                fontFamily = UiFamily,
                                fontSize = 12.sp,
                                lineHeight = 17.sp,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                    CellValue(row.free, row.freeIsYes, Modifier.width(64.dp), TextSecondary)
                    CellValue(row.plus, row.plusIsYes, Modifier.width(64.dp), Primary)
                }
            }

            Box(Modifier.fillMaxWidth().height(1.dp).background(Divider))

            Spacer(Modifier.height(Spacing.lg))

            // The most important paragraph on the screen. It is a commitment about what may
            // ever be sold, not marketing copy.
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(BrandTint, RoundedCornerShape(20.dp))
                    .padding(16.dp),
            ) {
                Text(
                    text = "Paying doesn't make the measurements better or the packing " +
                        "cleverer. Anything that isn't reliable yet isn't sold — it's just " +
                        "missing, for everyone.",
                    color = Color(0xFF7C4223),
                    fontFamily = UiFamily,
                    fontSize = 13.5f.sp,
                    lineHeight = 20.sp,
                )
            }

            Spacer(Modifier.height(Spacing.xl))
        }

        Column(Modifier.padding(horizontal = Spacing.gutter)) {
            PrimaryButton(
                text = price?.let { "Get Pack-a-Bunch Pro — $it" } ?: "Pack-a-Bunch Pro",
                onClick = onSubscribe,
                enabled = price != null,
            )
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Cancel any time in Google Play.",
                    color = TextTertiary,
                    fontFamily = UiFamily,
                    fontSize = 12.5f.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        Spacer(Modifier.height(Spacing.base))
    }
}

@Composable
private fun ColumnHeading(text: String, modifier: Modifier, color: Color) {
    Text(
        text = text,
        color = color,
        fontFamily = UiFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 10.5f.sp,
        letterSpacing = 0.8.sp,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        modifier = modifier,
    )
}

@Composable
private fun CellValue(text: String, isYes: Boolean, modifier: Modifier, color: Color) {
    Box(modifier, contentAlignment = Alignment.Center) {
        if (isYes) {
            Icon(
                PackIcons.Check,
                contentDescription = "Included",
                tint = Success,
                modifier = Modifier.size(19.dp),
            )
        } else {
            Text(
                text = text,
                style = NumeralChip.copy(fontSize = 14.sp),
                color = color,
            )
        }
    }
}
