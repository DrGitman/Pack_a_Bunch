package com.packabunch.ui.screens

import androidx.compose.animation.core.animateIntAsState
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.packabunch.packing.UnplacedReason
import com.packabunch.ui.PackEditorState
import com.packabunch.ui.components.Note
import com.packabunch.ui.components.NoteTone
import com.packabunch.ui.components.PackAppBar
import com.packabunch.ui.components.PackCard
import com.packabunch.ui.components.PackIcons
import com.packabunch.ui.components.PrimaryButton
import com.packabunch.ui.components.ScreenScaffold
import com.packabunch.ui.components.StatTile
import com.packabunch.ui.motion.Motion
import com.packabunch.ui.motion.pressScale
import com.packabunch.ui.render.IsometricCrate
import com.packabunch.ui.render.LayerView
import com.packabunch.ui.render.layersOf
import com.packabunch.ui.theme.BrandTint
import com.packabunch.ui.theme.ChromeAlt
import com.packabunch.ui.theme.ErrorRed
import com.packabunch.ui.theme.Ground
import com.packabunch.ui.theme.NumeralChip
import com.packabunch.ui.theme.Spacing
import com.packabunch.ui.theme.Success
import com.packabunch.ui.theme.SuccessTint
import com.packabunch.ui.theme.TextPrimary
import com.packabunch.ui.theme.TextSecondary
import com.packabunch.ui.theme.TextTertiary
import com.packabunch.ui.theme.UiFamily
import com.packabunch.ui.theme.itemColor
import kotlin.math.roundToInt

/**
 * A plan for a scanned space — `design/artboards/PlanIrregular.dc.html`.
 *
 * Different from the crate result in the ways that matter for an irregular space:
 *
 *  - Capacity is stated in **litres of usable volume**, not as three dimensions. A boot has
 *    no meaningful width × depth × height, and quoting its bounding box would overstate it.
 *  - The obstructions that shaped the answer are named, because "it fits between the arches"
 *    is the explanation for an arrangement that would otherwise look arbitrary.
 *  - Anything blocked by the opening is called out separately from anything that simply did
 *    not fit — they are different problems with different fixes.
 */
@Composable
fun PlanIrregularScreen(
    state: PackEditorState,
    onStartLoading: () -> Unit,
    onFixOpening: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val plan = state.plan
    val space = state.space
    val scan = space?.scan

    val usableLitres = ((space?.usableVolumeMm3 ?: 0L) / 1_000_000.0).roundToInt()
    val fixedObstructions = scan?.obstructions?.count { !it.removable } ?: 0

    ArtboardPage(modifier) {
        PackAppBar(
            title = state.name.ifEmpty { "Your space" },
            subtitle = buildString {
                append("$usableLitres L mapped")
                if (fixedObstructions > 0) {
                    append(" · $fixedObstructions ")
                    append(if (fixedObstructions == 1) "fixed obstruction" else "fixed obstructions")
                }
            },
            onBack = onBack,
        )

        Spacer(Modifier.height(Spacing.md))

        Column(Modifier.padding(horizontal = Spacing.gutter)) {
            PackCard(shape = RoundedCornerShape(28.dp), elevation = 12.dp, contentPadding = 14.dp) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .background(BrandTint, RoundedCornerShape(22.dp)),
                ) {
                    if (plan != null && space != null) {
                            IsometricCrate(
                            showControls = true,
                            items = state.items,
                                space = space,
                                placements = plan.placements,
                                modifier = Modifier.fillMaxWidth().height(300.dp),
                                itemColorFor = { placement ->
                                    itemColor(state.items.indexOfFirst { it.id == placement.specId })
                                },
                            )
                    }


                }
            }
        }

        Spacer(Modifier.height(14.dp))

        if (plan != null) {
            val fill by animateIntAsState(
                targetValue = plan.metrics.modelledFillPercent,
                animationSpec = Motion.standardTween(Motion.MEDIUM_MS * 2),
                label = "irregularFill",
            )

            Row(
                Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter),
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

            Spacer(Modifier.height(12.dp))

            val blockedByOpening = plan.unplaced.filter {
                it.reason == UnplacedReason.WILL_NOT_FIT_THROUGH_THE_OPENING
            }

            Column(Modifier.padding(horizontal = Spacing.gutter)) {
                when {
                    blockedByOpening.isNotEmpty() -> {
                        val first = blockedByOpening.first()
                        Note(
                            title = "${first.name} won't go through the opening",
                            text = "There's room inside, but no way in. That's a different " +
                                "problem from not fitting, see what usually works.",
                            tone = NoteTone.Problem,
                            icon = PackIcons.Warning,
                        )
                        Spacer(Modifier.height(10.dp))
                        com.packabunch.ui.components.SecondaryButton(
                            text = "Look at the opening",
                            onClick = { onFixOpening(first.specId) },
                        )
                    }

                    plan.metrics.unplacedInstanceCount == 0 -> Note(
                        title = "Everything goes in",
                        text = "And every piece can be lifted in, in this order, nothing is " +
                            "placed behind something you'd have to load first.",
                        tone = NoteTone.Confirmed,
                        icon = PackIcons.Check,
                    )

                    else -> Note(
                        text = "${plan.metrics.unplacedInstanceCount} left over. This " +
                            "arrangement couldn't place them, that isn't proof they can " +
                            "never fit.",
                        tone = NoteTone.Caution,
                        icon = PackIcons.Info,
                    )
                }
            }
        }

        PushDown()

        Column(Modifier.padding(horizontal = Spacing.gutter)) {
            PrimaryButton(
                text = "Start loading",
                onClick = onStartLoading,
                enabled = plan != null && plan.placements.isNotEmpty(),
            )
        }

        Spacer(Modifier.height(Spacing.sm))
    }
}

@Composable
private fun ViewChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .pressScale(pressedScale = 0.96f)
            .background(if (selected) ChromeAlt else Color.White, RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 8.dp),
    ) {
        Text(
            text = text,
            color = if (selected) Ground else Color(0xFF8A7565),
            fontFamily = UiFamily,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            fontSize = 12.5f.sp,
        )
    }
}
