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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.packabunch.packing.Obstruction
import com.packabunch.packing.ObstructionKind
import com.packabunch.packing.ScanCompleteness
import com.packabunch.packing.ScanReport
import com.packabunch.packing.ScannedSpace
import com.packabunch.packing.UnknownRegion
import com.packabunch.ui.components.IconTile
import com.packabunch.ui.components.Note
import com.packabunch.ui.components.NoteTone
import com.packabunch.ui.components.PackAppBar
import com.packabunch.ui.components.PackCard
import com.packabunch.ui.components.PackIcons
import com.packabunch.ui.components.PackSwitch
import com.packabunch.ui.components.PrimaryButton
import com.packabunch.ui.components.ScreenHeading
import com.packabunch.ui.components.ScreenScaffold
import com.packabunch.ui.components.SecondaryButton
import com.packabunch.ui.components.SectionHeading
import com.packabunch.ui.components.StatTile
import com.packabunch.ui.theme.BrandTint
import com.packabunch.ui.theme.Caution
import com.packabunch.ui.theme.CautionTint
import com.packabunch.ui.theme.NumeralChip
import com.packabunch.ui.theme.Primary
import com.packabunch.ui.theme.Spacing
import com.packabunch.ui.theme.SurfaceField
import com.packabunch.ui.theme.TextPrimary
import com.packabunch.ui.theme.TextSecondary
import com.packabunch.ui.theme.TextTertiary
import com.packabunch.ui.theme.UiFamily
import kotlin.math.roundToInt

private fun litres(mm3: Long): String = "${(mm3 / 1_000_000.0).roundToInt()} L"

/**
 * What we mapped — `design/artboards/SpaceScanReview.dc.html`.
 *
 * Every figure comes from [ScanReport], which is computed from the grid rather than
 * estimated. Usable volume is what the solver will actually plan into, with unseen space
 * counted as solid.
 */
@Composable
fun SpaceScanReviewScreen(
    scan: ScannedSpace,
    report: ScanReport,
    onContinue: () -> Unit,
    onScanAgain: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ScreenScaffold(modifier) {
        PackAppBar(title = "What we mapped", onBack = onBack)

        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.gutter),
        ) {
            Spacer(Modifier.height(Spacing.md))

            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                StatTile(
                    value = litres(report.usableVolumeMm3),
                    caption = "usable volume",
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    value = "${(report.observedFraction * 100).roundToInt()}%",
                    caption = "of it mapped",
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    value = litres(report.unknownVolumeMm3),
                    caption = "never seen",
                    valueColor = if (report.unknownVolumeMm3 > 0) Caution else TextPrimary,
                    modifier = Modifier.weight(1f),
                )
            }

            if (report.completeness != ScanCompleteness.COMPLETE) {
                Spacer(Modifier.height(Spacing.base))
                Note(
                    title = "There are gaps in the map",
                    text = "Unseen space counts as solid, so you'd pack into " +
                        "${litres(report.usableVolumeMm3)} instead of " +
                        "${litres(report.optimisticVolumeMm3)}, safe, just smaller than " +
                        "the space really is.",
                    tone = NoteTone.Caution,
                    icon = PackIcons.Warning,
                )
            }

            if (scan.obstructions.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.lg))
                SectionHeading("Things in the way")
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    scan.obstructions.forEach { obstruction ->
                        ObstructionSummaryRow(
                            obstruction = obstruction,
                            resolutionMm = scan.baseGrid.resolutionMm,
                        )
                    }
                }
            }

            Spacer(Modifier.height(Spacing.xl))
        }

        Column(
            Modifier.padding(horizontal = Spacing.gutter),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PrimaryButton(text = "What's staying in?", onClick = onContinue)
            SecondaryButton(text = "Map it again", onClick = onScanAgain, icon = PackIcons.Camera)
        }

        Spacer(Modifier.height(Spacing.sm))
    }
}

@Composable
private fun ObstructionSummaryRow(obstruction: Obstruction, resolutionMm: Int) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(SurfaceField, RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconTile(
            icon = PackIcons.Cube,
            tint = Primary,
            background = BrandTint,
            size = 36.dp,
            iconSize = 18.dp,
            cornerRadius = 12.dp,
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = obstruction.label,
                color = TextPrimary,
                fontFamily = UiFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.5f.sp,
            )
            Text(
                text = "${litres(obstruction.volumeMm3(resolutionMm))} lost",
                style = NumeralChip,
                color = TextTertiary,
            )
        }
        if (!obstruction.removable) {
            Text(
                text = "FIXED",
                color = TextTertiary,
                fontFamily = UiFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 10.sp,
                letterSpacing = 0.6.sp,
            )
        }
    }
}

/**
 * What's staying in — `design/artboards/SpaceObstructions.dc.html`.
 *
 * Toggling something off recomputes the usable volume from the grid immediately, because
 * the whole point is to show what taking the parcel shelf out is actually worth.
 *
 * The line at the bottom is a real limitation, not a disclaimer: anything left in is packed
 * *around*, never stacked on, because a scan cannot tell what will take weight.
 */
@Composable
fun SpaceObstructionsScreen(
    scan: ScannedSpace,
    report: ScanReport,
    onToggle: (String, Boolean) -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ScreenScaffold(modifier) {
        PackAppBar(title = "What's staying in?", onBack = onBack)

        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.gutter),
        ) {
            Spacer(Modifier.height(Spacing.sm))

            Text(
                text = "We found ${scan.obstructions.size} things in there. Turn off anything " +
                    "you'll take out and the space grows.",
                color = TextSecondary,
                fontFamily = UiFamily,
                fontSize = 14.sp,
                lineHeight = 21.sp,
            )

            Spacer(Modifier.height(Spacing.base))

            PackCard(elevation = 8.dp, contentPadding = Spacing.base) {
                Text(
                    text = "SPACE TO PACK INTO",
                    color = TextTertiary,
                    fontFamily = UiFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.5f.sp,
                    letterSpacing = 0.8.sp,
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "${(report.usableVolumeMm3 / 1_000_000.0).roundToInt()}",
                        style = com.packabunch.ui.theme.NumeralLarge.copy(fontSize = 34.sp),
                        color = TextPrimary,
                    )
                    Spacer(Modifier.size(6.dp))
                    Text(
                        text = "litres",
                        color = TextSecondary,
                        fontFamily = UiFamily,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(bottom = 5.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VolumeChip("${litres(report.volumeAsFoundMm3)} as found")
                    VolumeChip("${litres(report.volumeIfEmptiedMm3)} if emptied")
                }
            }

            Spacer(Modifier.height(Spacing.lg))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                scan.obstructions.forEach { obstruction ->
                    ObstructionToggleRow(
                        obstruction = obstruction,
                        resolutionMm = scan.baseGrid.resolutionMm,
                        onToggle = { onToggle(obstruction.id, it) },
                    )
                }
            }

            Spacer(Modifier.height(Spacing.base))

            Note(
                text = "Anything left in is packed around, not on top of. We can't tell what " +
                    "will take weight.",
                icon = PackIcons.Info,
            )

            Spacer(Modifier.height(Spacing.xl))
        }

        Column(Modifier.padding(horizontal = Spacing.gutter)) {
            PrimaryButton(
                text = "Use ${litres(report.usableVolumeMm3)}",
                onClick = onContinue,
            )
        }

        Spacer(Modifier.height(Spacing.sm))
    }
}

@Composable
private fun VolumeChip(text: String) {
    Text(
        text = text,
        style = NumeralChip,
        color = Color(0xFF7C4223),
        modifier = Modifier
            .background(BrandTint, RoundedCornerShape(999.dp))
            .padding(horizontal = 11.dp, vertical = 6.dp),
    )
}

@Composable
private fun ObstructionToggleRow(
    obstruction: Obstruction,
    resolutionMm: Int,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = obstruction.label,
                color = TextPrimary,
                fontFamily = UiFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.5f.sp,
            )
            Text(
                text = "${litres(obstruction.volumeMm3(resolutionMm))} · " +
                    describeKind(obstruction.kind),
                color = TextTertiary,
                fontFamily = UiFamily,
                fontSize = 12.5f.sp,
            )
        }
        // Structure has no switch at all rather than a disabled one — offering a control
        // that cannot work is worse than not offering it.
        if (obstruction.removable) {
            PackSwitch(checked = obstruction.includedInPack, onCheckedChange = onToggle)
        } else {
            Text(
                text = "FIXED",
                color = TextTertiary,
                fontFamily = UiFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 10.sp,
                letterSpacing = 0.6.sp,
            )
        }
    }
}

private fun describeKind(kind: ObstructionKind): String = when (kind) {
    ObstructionKind.PART_OF_THE_STRUCTURE -> "part of the space"
    ObstructionKind.REMOVABLE -> "lifts out"
    ObstructionKind.UNIDENTIFIED -> "we don't know what it is"
    ObstructionKind.SOFT -> "squashy, treated as solid"
}

/**
 * Gaps in the map — `design/artboards/ScanIncomplete.dc.html`.
 *
 * Names each unseen patch with its own volume, because one lump total tells you nothing
 * about where to point the phone. Carrying on is allowed, and the screen states the exact
 * cost of doing so in litres rather than warning vaguely.
 */
@Composable
fun ScanIncompleteScreen(
    report: ScanReport,
    onSweepAgain: () -> Unit,
    onUseSmaller: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ScreenScaffold(modifier) {
        PackAppBar(title = "Gaps in the map", onBack = onBack)

        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.gutter),
        ) {
            Spacer(Modifier.height(Spacing.sm))

            ScreenHeading(
                if (report.unknownRegions.size == 1) "One patch we never saw"
                else "${report.unknownRegions.size} patches we never saw",
            )

            Spacer(Modifier.height(10.dp))

            Text(
                text = "We're not going to invent what's behind them. Either sweep those " +
                    "spots, or we treat them as solid and plan around them.",
                color = TextSecondary,
                fontFamily = UiFamily,
                fontSize = 15.sp,
                lineHeight = 23.sp,
            )

            Spacer(Modifier.height(Spacing.base))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                report.unknownRegions.take(4).forEachIndexed { index, region ->
                    UnknownRegionRow(index = index, region = region)
                }
            }

            Spacer(Modifier.height(Spacing.base))

            Box(
                Modifier
                    .fillMaxWidth()
                    .background(CautionTint, RoundedCornerShape(20.dp))
                    .padding(14.dp),
            ) {
                Column {
                    Text(
                        text = "If you carry on anyway",
                        color = Color(0xFF6E4708),
                        fontFamily = UiFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.5f.sp,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Every patch counts as solid. You'd pack into " +
                            "${litres(report.usableVolumeMm3)} instead of " +
                            "${litres(report.optimisticVolumeMm3)}, safe, just smaller " +
                            "than the space really is.",
                        color = Color(0xFF7A5A25),
                        fontFamily = UiFamily,
                        fontSize = 13.5f.sp,
                        lineHeight = 20.sp,
                    )
                }
            }

            Spacer(Modifier.height(Spacing.xl))
        }

        Column(
            Modifier.padding(horizontal = Spacing.gutter),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PrimaryButton(text = "Go back and sweep them", onClick = onSweepAgain)
            SecondaryButton(
                text = "Use the smaller ${litres(report.usableVolumeMm3)}",
                onClick = onUseSmaller,
            )
        }

        Spacer(Modifier.height(Spacing.sm))
    }
}

@Composable
private fun UnknownRegionRow(index: Int, region: UnknownRegion) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconTile(
            icon = PackIcons.Warning,
            tint = Caution,
            background = CautionTint,
            size = 36.dp,
            iconSize = 18.dp,
            cornerRadius = 12.dp,
        )
        Column(Modifier.weight(1f)) {
            Text(
                // The geometry knows where and how big, but not what it is called. Inventing
                // "under the parcel shelf" would be a guess presented as an observation.
                text = region.label ?: "Patch ${index + 1}",
                color = TextPrimary,
                fontFamily = UiFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.5f.sp,
            )
            Text(
                text = "about ${region.litres.roundToInt()} L unknown",
                style = NumeralChip,
                color = TextTertiary,
            )
        }
    }
}
