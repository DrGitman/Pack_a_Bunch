package com.packabunch.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.packabunch.packing.PackingPlan
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
import com.packabunch.ui.components.StatTile
import com.packabunch.ui.format.LengthUnit
import com.packabunch.ui.format.formatLengthWithUnit
import com.packabunch.ui.motion.pressScale
import com.packabunch.ui.theme.CautionTint
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
 * No arrangement found — `design/artboards/NoArrangement.dc.html`.
 *
 * The single most important sentence in the app is on this screen: *that doesn't mean it's
 * impossible — it means this search didn't find a way.* The engine is a bounded heuristic,
 * and a heuristic failing is not a proof. Any wording that implies otherwise is a claim we
 * cannot support.
 *
 * The suggestions are computed from the actual inputs, not written in advance — how many
 * items are pinned upright, what the edge gap really is, which item is genuinely the
 * awkward one.
 */
@Composable
fun NoArrangementScreen(
    spaceName: String,
    spaceSummary: String,
    uprightItemCount: Int,
    edgeGapMm: Int,
    awkwardItemName: String?,
    unit: LengthUnit,
    onAllowTurning: () -> Unit,
    onShrinkGap: () -> Unit,
    onRemoveAwkward: () -> Unit,
    onBackToItems: () -> Unit,
    onChangeSpace: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ScreenScaffold(modifier) {
        PackAppBar(title = spaceName, subtitle = spaceSummary, onBack = onBack)

        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.gutter),
        ) {
            Spacer(Modifier.height(Spacing.md))

            IconTile(
                icon = PackIcons.Info,
                tint = Color(0xFFB4761A),
                background = CautionTint,
                size = 56.dp,
                iconSize = 26.dp,
            )

            Spacer(Modifier.height(Spacing.base))
            ScreenHeading("No arrangement found for these items")
            Spacer(Modifier.height(10.dp))

            Text(
                text = "Every ordering we tried left something out. That doesn't mean it's " +
                    "impossible — it means this search didn't find a way.",
                color = TextSecondary,
                fontFamily = UiFamily,
                fontSize = 15.sp,
                lineHeight = 23.sp,
            )

            Spacer(Modifier.height(Spacing.lg))
            SectionHeading("Things that usually help")
            Spacer(Modifier.height(10.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (uprightItemCount > 0) {
                    FixRow(
                        icon = PackIcons.Rotate,
                        title = "Let more items turn",
                        detail = "$uprightItemCount ${if (uprightItemCount == 1) "item is" else "items are"} " +
                            "set to stay upright",
                        onClick = onAllowTurning,
                    )
                }
                if (edgeGapMm > 0) {
                    FixRow(
                        icon = PackIcons.Ruler,
                        title = "Shrink the edge gap",
                        detail = "Currently ${formatLengthWithUnit(edgeGapMm, unit)} on every side",
                        onClick = onShrinkGap,
                    )
                }
                if (awkwardItemName != null) {
                    FixRow(
                        icon = PackIcons.Trash,
                        title = "Take one thing out",
                        detail = "$awkwardItemName is the awkward one",
                        onClick = onRemoveAwkward,
                    )
                }
            }

            Spacer(Modifier.height(Spacing.xl))
        }

        Column(
            Modifier.padding(horizontal = Spacing.gutter),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PrimaryButton(text = "Back to the items", onClick = onBackToItems)
            SecondaryButton(text = "Change the space", onClick = onChangeSpace)
        }

        Spacer(Modifier.height(Spacing.sm))
    }
}

/**
 * Solver timeout — `design/artboards/SolverTimeout.dc.html`.
 *
 * The engine always returns the best plan it validated before the budget ran out, so there
 * is genuinely something to take. The screen offers it rather than throwing the work away,
 * and says plainly that nothing is lost either way.
 */
@Composable
fun SolverTimeoutScreen(
    bestSoFar: PackingPlan,
    orderingsTried: Int,
    orderingsTotal: Int,
    onUseBestSoFar: () -> Unit,
    onKeepLooking: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ScreenScaffold(modifier) {
        PackAppBar(title = "Working out the pack", onBack = onBack)

        Column(Modifier.padding(horizontal = Spacing.gutter)) {
            Spacer(Modifier.height(Spacing.md))

            Text(
                text = "Trying different orders · $orderingsTried of $orderingsTotal",
                color = TextTertiary,
                fontFamily = UiFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.5f.sp,
            )

            Spacer(Modifier.height(Spacing.sm))
            ScreenHeading("This one is taking a while")
            Spacer(Modifier.height(10.dp))

            Text(
                text = "${bestSoFar.metrics.requestedInstanceCount} pieces with rotations " +
                    "makes a lot of combinations. We already have a valid arrangement — you " +
                    "can take it, or let the search keep running for a better one.",
                color = TextSecondary,
                fontFamily = UiFamily,
                fontSize = 15.sp,
                lineHeight = 23.sp,
            )

            Spacer(Modifier.height(Spacing.lg))
            SectionHeading("Best so far")
            Spacer(Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                StatTile(
                    value = "${bestSoFar.metrics.placedInstanceCount}/" +
                        "${bestSoFar.metrics.requestedInstanceCount}",
                    caption = "pieces placed",
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    value = "${bestSoFar.metrics.modelledFillPercent}%",
                    caption = "modelled fill",
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Spacer(Modifier.weight(1f))

        Column(
            Modifier.padding(horizontal = Spacing.gutter),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PrimaryButton(text = "Use what we have", onClick = onUseBestSoFar)
            SecondaryButton(text = "Keep looking", onClick = onKeepLooking)
            Text(
                text = "Nothing is lost either way — your items stay saved.",
                color = TextTertiary,
                fontFamily = UiFamily,
                fontSize = 12.5f.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(Spacing.base))
    }
}

/**
 * It doesn't fit — `design/artboards/DoesntFit.dc.html`.
 *
 * Reached from the packing guide when reality disagrees with the plan, which is the moment
 * the app is most likely to lose someone. Four causes, each routing somewhere useful, and
 * the promise that nothing is erased is stated because a person mid-pack will not risk
 * tapping otherwise.
 */
@Composable
fun DoesntFitScreen(
    itemName: String,
    itemSummary: String,
    stepNumber: Int,
    totalSteps: Int,
    piecesAlreadyIn: Int,
    onItemBigger: () -> Unit,
    onSpaceSmaller: () -> Unit,
    onObstruction: () -> Unit,
    onReplan: () -> Unit,
    onSkipAndCarryOn: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ScreenScaffold(modifier) {
        PackAppBar(title = "It doesn't fit", onBack = onBack)

        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.gutter),
        ) {
            Spacer(Modifier.height(Spacing.sm))

            Text(
                text = itemName,
                color = TextPrimary,
                fontFamily = UiFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 22.sp,
                letterSpacing = (-0.4).sp,
            )
            Text(
                text = "$itemSummary · step $stepNumber of $totalSteps",
                style = com.packabunch.ui.theme.NumeralChip,
                color = TextTertiary,
            )

            Spacer(Modifier.height(Spacing.base))

            Text(
                text = "What's actually wrong?",
                color = TextPrimary,
                fontFamily = UiFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Pick the closest one and we'll take you straight there. Nothing gets " +
                    "erased either way.",
                color = TextSecondary,
                fontFamily = UiFamily,
                fontSize = 14.sp,
                lineHeight = 21.sp,
            )

            Spacer(Modifier.height(Spacing.base))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FixRow(
                    icon = PackIcons.Cube,
                    title = "The item is bigger than I said",
                    detail = "Takes you to $itemName's measurements. Handles and lids catch " +
                        "people out.",
                    onClick = onItemBigger,
                )
                FixRow(
                    icon = PackIcons.Ruler,
                    title = "The space is smaller than I said",
                    detail = "Back to the space measurements. Inside walls are often thicker " +
                        "than they look.",
                    onClick = onSpaceSmaller,
                )
                FixRow(
                    icon = PackIcons.Warning,
                    title = "Something's in the way",
                    detail = "A lip, a handle inside, a bar across the top. The plan assumes " +
                        "an empty, clear space.",
                    onClick = onObstruction,
                )
                FixRow(
                    icon = PackIcons.Rotate,
                    title = "It fits, just not like that",
                    detail = "We'll look for a different arrangement with everything you've " +
                        "already got in.",
                    onClick = onReplan,
                )
            }

            Spacer(Modifier.height(Spacing.base))

            Note(
                text = "Your pack, its items and the $piecesAlreadyIn " +
                    "${if (piecesAlreadyIn == 1) "piece" else "pieces"} already in stay saved.",
                tone = NoteTone.Confirmed,
                icon = PackIcons.Check,
            )

            Spacer(Modifier.height(Spacing.xl))
        }

        Column(Modifier.padding(horizontal = Spacing.gutter)) {
            SecondaryButton(text = "Skip it and carry on", onClick = onSkipAndCarryOn)
        }

        Spacer(Modifier.height(Spacing.base))
    }
}

/**
 * Oversize item — `design/artboards/OversizeItem.dc.html`.
 *
 * One of only two states in the app allowed to sound final, because it genuinely is a
 * proof: the item's shortest edge exceeds the space's longest, so no rotation can save it.
 *
 * Saving it anyway is still offered. Somebody assembling a list should not be blocked from
 * recording a thing just because it will not fit — it is listed as unplaced, honestly.
 */
@Composable
fun OversizeItemScreen(
    itemName: String,
    longestItemEdgeMm: Int,
    longestSpaceEdgeMm: Int,
    unit: LengthUnit,
    onFixSize: () -> Unit,
    onUseBiggerSpace: () -> Unit,
    onSaveAnyway: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ScreenScaffold(modifier) {
        PackAppBar(title = "Add an item", onBack = onBack)

        Column(Modifier.padding(horizontal = Spacing.gutter)) {
            Spacer(Modifier.height(Spacing.md))

            IconTile(
                icon = PackIcons.Warning,
                tint = ErrorRed,
                background = ErrorTint,
                size = 56.dp,
                iconSize = 26.dp,
            )

            Spacer(Modifier.height(Spacing.base))
            ScreenHeading("Too big for this space")
            Spacer(Modifier.height(10.dp))

            Text(
                text = "At ${formatLengthWithUnit(longestItemEdgeMm, unit)} it won't go into " +
                    "a space that is ${formatLengthWithUnit(longestSpaceEdgeMm, unit)} " +
                    "across, whichever way you turn it.",
                color = TextSecondary,
                fontFamily = UiFamily,
                fontSize = 15.sp,
                lineHeight = 23.sp,
            )

            Spacer(Modifier.height(Spacing.lg))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MeasureCompare(
                    label = "Longest item edge",
                    value = formatLengthWithUnit(longestItemEdgeMm, unit),
                    problem = true,
                )
                MeasureCompare(
                    label = "Longest space edge",
                    value = formatLengthWithUnit(longestSpaceEdgeMm, unit),
                    problem = false,
                )
            }
        }

        Spacer(Modifier.weight(1f))

        Column(
            Modifier.padding(horizontal = Spacing.gutter),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PrimaryButton(text = "Fix the size", onClick = onFixSize)
            SecondaryButton(text = "Use a bigger space instead", onClick = onUseBiggerSpace)
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                com.packabunch.ui.components.PackTextButton(
                    text = "Save it anyway — it won't be placed",
                    onClick = onSaveAnyway,
                    color = TextTertiary,
                )
            }
        }

        Spacer(Modifier.height(Spacing.sm))
    }
}

@Composable
private fun MeasureCompare(label: String, value: String, problem: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (problem) ErrorTint else SurfaceField, RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = if (problem) Color(0xFF8E3322) else TextSecondary,
            fontFamily = UiFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = NumeralLarge.copy(fontSize = 17.sp),
            color = if (problem) ErrorRed else TextPrimary,
        )
    }
}

@Composable
private fun FixRow(
    icon: ImageVector,
    title: String,
    detail: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .pressScale(pressedScale = 0.99f)
            .background(Color.White, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = Primary, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = TextPrimary,
                fontFamily = UiFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
            )
            Text(
                text = detail,
                color = TextTertiary,
                fontFamily = UiFamily,
                fontSize = 12.5f.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        Icon(
            PackIcons.Forward,
            contentDescription = null,
            tint = Color(0xFFC3B0A0),
            modifier = Modifier.size(18.dp),
        )
    }
}
