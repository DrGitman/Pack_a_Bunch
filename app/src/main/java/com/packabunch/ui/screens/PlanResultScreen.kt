package com.packabunch.ui.screens

import androidx.compose.animation.core.animateIntAsState
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.packabunch.packing.PackingPlan
import com.packabunch.packing.UnplacedInstance
import com.packabunch.packing.UnplacedReason
import com.packabunch.ui.PackEditorState
import com.packabunch.ui.components.ItemNumberTile
import com.packabunch.ui.components.PackAppBar
import com.packabunch.ui.components.PackCard
import com.packabunch.ui.components.PackIconButton
import com.packabunch.ui.components.PackIcons
import com.packabunch.ui.components.PrimaryButton
import com.packabunch.ui.components.ScreenScaffold
import com.packabunch.ui.components.StatTile
import com.packabunch.ui.components.warmShadow
import com.packabunch.ui.format.LengthUnit
import com.packabunch.ui.format.formatDimensions
import com.packabunch.ui.motion.Motion
import com.packabunch.ui.render.IsometricCrate
import com.packabunch.ui.theme.BrandTint
import com.packabunch.ui.theme.CautionTint
import com.packabunch.ui.theme.ErrorRed
import com.packabunch.ui.theme.NumeralChip
import com.packabunch.ui.theme.Spacing
import com.packabunch.ui.theme.TextPrimary
import com.packabunch.ui.theme.UiFamily
import com.packabunch.ui.theme.itemColor

/**
 * Plan result — `design/artboards/PlanResult.dc.html`.
 *
 * Every number on this screen is one the engine actually computed, and the three captions
 * are load-bearing: "pieces placed", "modelled fill", "left over". Not "efficiency", not
 * "optimised", and no percentage without the basis attached.
 *
 * The unplaced card is the honest half of the screen. It names the item, says which kind of
 * failure it was, and — except for the one case that really is a proof — says outright that
 * this does not mean it can never fit.
 */
@Composable
fun PlanResultScreen(
    state: PackEditorState,
    unit: LengthUnit,
    onStartPacking: () -> Unit,
    onEditItems: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val plan = state.plan
    val space = state.space

    ScreenScaffold(modifier) {
        PackAppBar(
            title = state.name.ifEmpty { "Your pack" },
            subtitle = space?.let { formatDimensions(it.dimensions, unit) },
            onBack = onBack,
            actions = {
                PackIconButton(
                    icon = PackIcons.MoreVertical,
                    contentDescription = "More",
                    onClick = {},
                    tint = Color(0xFF5C4A3A),
                )
            },
        )

        Spacer(Modifier.height(Spacing.md))

        Column(Modifier.padding(horizontal = Spacing.gutter)) {
            PackCard(
                shape = RoundedCornerShape(28.dp),
                elevation = 12.dp,
                contentPadding = 14.dp,
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .background(BrandTint, RoundedCornerShape(22.dp)),
                ) {
                    if (plan != null && space != null) {
                        IsometricCrate(
                            space = space,
                            placements = plan.placements,
                            modifier = Modifier.fillMaxWidth().height(300.dp),
                            itemColorFor = { placement ->
                                itemColor(state.items.indexOfFirst { it.id == placement.specId })
                            },
                        )
                    }

                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                            .background(Color(0xF0FFFFFF), RoundedCornerShape(999.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        androidx.compose.material3.Icon(
                            PackIcons.Rotate,
                            contentDescription = null,
                            tint = Color(0xFF8A7565),
                            modifier = Modifier.size(15.dp),
                        )
                        Text(
                            "Drag to turn",
                            color = Color(0xFF7C6857),
                            fontFamily = UiFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.5f.sp,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        if (plan != null) {
            // The fill figure counts up rather than appearing. It is the one number people
            // look at, and a beat of movement is what makes them read the caption under it.
            val fill by animateIntAsState(
                targetValue = plan.metrics.modelledFillPercent,
                animationSpec = Motion.standardTween(Motion.MEDIUM_MS * 2),
                label = "modelledFill",
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                StatTile(
                    value = "${plan.metrics.placedInstanceCount}/${plan.metrics.requestedInstanceCount}",
                    caption = "pieces placed",
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    value = "$fill%",
                    caption = "modelled fill",
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    value = plan.metrics.unplacedInstanceCount.toString(),
                    caption = "left over",
                    valueColor = if (plan.metrics.unplacedInstanceCount > 0) ErrorRed else TextPrimary,
                    modifier = Modifier.weight(1f),
                )
            }

            plan.unplaced.firstOrNull()?.let { unplaced ->
                Spacer(Modifier.height(12.dp))
                Column(Modifier.padding(horizontal = Spacing.gutter)) {
                    UnplacedCard(
                        unplaced = unplaced,
                        plan = plan,
                        index = state.items.indexOfFirst { it.id == unplaced.specId },
                        dimensions = state.items
                            .firstOrNull { it.id == unplaced.specId }
                            ?.let { formatDimensions(it.dimensions, unit) },
                    )
                }
            }
        }

        Spacer(Modifier.weight(1f))

        Row(
            modifier = Modifier.padding(horizontal = Spacing.gutter),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(56.dp)
                    .warmShadow(2.dp, RoundedCornerShape(28.dp))
                    .background(Color.White, RoundedCornerShape(28.dp)),
                contentAlignment = Alignment.Center,
            ) {
                PackIconButton(
                    icon = PackIcons.Pencil,
                    contentDescription = "Edit items",
                    onClick = onEditItems,
                    tint = Color(0xFF7C4223),
                )
            }
            PrimaryButton(
                text = "Start packing",
                onClick = onStartPacking,
                enabled = plan != null && plan.placements.isNotEmpty(),
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(Spacing.sm))
    }
}

@Composable
private fun UnplacedCard(
    unplaced: UnplacedInstance,
    plan: PackingPlan,
    index: Int,
    dimensions: String?,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(CautionTint, RoundedCornerShape(22.dp))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ItemNumberTile(
                number = index + 1,
                color = itemColor(index.coerceAtLeast(0)),
                size = 30.dp,
            )
            Spacer(Modifier.size(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "${unplaced.name} ${headline(unplaced.reason)}",
                    color = TextPrimary,
                    fontFamily = UiFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.5f.sp,
                )
                if (dimensions != null) {
                    Text(
                        text = dimensions,
                        style = NumeralChip,
                        color = Color(0xFF8A6A2E),
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }

        Text(
            text = explanation(unplaced.reason, plan),
            color = Color(0xFF7A5A25),
            fontFamily = UiFamily,
            fontSize = 13.5f.sp,
            lineHeight = 20.sp,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

private fun headline(reason: UnplacedReason): String = when (reason) {
    UnplacedReason.LARGER_THAN_THE_SPACE -> "is bigger than the space"
    UnplacedReason.WILL_NOT_FIT_THROUGH_THE_OPENING -> "won't go through the opening"
    UnplacedReason.NO_ROOM_IN_THIS_ARRANGEMENT -> "didn't fit"
    UnplacedReason.TIME_BUDGET_REACHED -> "wasn't reached"
}

/**
 * The wording that keeps the app honest.
 *
 * Only one of these four is a proof, and only that one is allowed to sound final. The
 * others say what actually happened — this search did not find room — and then say plainly
 * that it is not the same as never fitting.
 */
private fun explanation(reason: UnplacedReason, plan: PackingPlan): String = when (reason) {
    UnplacedReason.LARGER_THAN_THE_SPACE ->
        "No way round this one: it is larger than the space itself, whichever way it is turned."

    UnplacedReason.WILL_NOT_FIT_THROUGH_THE_OPENING ->
        "There is room inside, but no way in. All six orientations were checked. " +
            "Re-measure the opening, or leave this one out of the pack."

    UnplacedReason.NO_ROOM_IN_THIS_ARRANGEMENT ->
        "This arrangement couldn't place it. Try letting it lie flat, or taking out " +
            "something else. That doesn't prove it can never fit."

    UnplacedReason.TIME_BUDGET_REACHED ->
        "The search ran out of time before reaching it, using \"${plan.strategy}\". " +
            "Fewer pieces would let it finish."
}
