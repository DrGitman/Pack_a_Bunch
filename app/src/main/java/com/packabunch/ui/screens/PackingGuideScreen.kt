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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.packabunch.packing.Placement
import com.packabunch.packing.Space
import com.packabunch.ui.PackEditorState
import com.packabunch.ui.components.CountPill
import com.packabunch.ui.components.ItemNumberTile
import com.packabunch.ui.components.PackAppBar
import com.packabunch.ui.components.PackCard
import com.packabunch.ui.components.PackTextButton
import com.packabunch.ui.components.PrimaryButton
import com.packabunch.ui.components.ScreenScaffold
import com.packabunch.ui.components.SecondaryButton
import com.packabunch.ui.components.StepProgress
import com.packabunch.ui.format.LengthUnit
import com.packabunch.ui.format.formatDimensions
import com.packabunch.ui.render.IsometricCrate
import com.packabunch.ui.theme.BrandTint
import com.packabunch.ui.theme.NumeralChip
import com.packabunch.ui.theme.Spacing
import com.packabunch.ui.theme.SurfaceMuted
import com.packabunch.ui.theme.TextPrimary
import com.packabunch.ui.theme.TextSecondary
import com.packabunch.ui.theme.TextTertiary
import com.packabunch.ui.theme.UiFamily
import com.packabunch.ui.theme.itemColor

/**
 * Packing guide — `design/artboards/PackingGuide.dc.html`.
 *
 * One item at a time, in the order the solver worked out, which is always bottom-up — you
 * are never asked to place something before the thing it rests on.
 *
 * Directions are given against the container's own front, back, left and right, never
 * against the camera. "Back right corner" means the same thing whichever way you happen to
 * be standing; "on the left of the screen" does not.
 */
@Composable
fun PackingGuideScreen(
    state: PackEditorState,
    unit: LengthUnit,
    onStepChange: (Int) -> Unit,
    onMarkPacked: (String, Boolean) -> Unit,
    onFinished: () -> Unit = {},
    onBack: () -> Unit,
    onDoesntFit: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val plan = state.plan
    val space = state.space
    val placements = plan?.placements?.sortedBy { it.sequenceIndex }.orEmpty()
    val step = state.guideStep.coerceIn(0, (placements.size - 1).coerceAtLeast(0))
    val current = placements.getOrNull(step)
    val spec = current?.let { placement -> state.items.firstOrNull { it.id == placement.specId } }
    val itemIndex = state.items.indexOfFirst { it.id == current?.specId }

    ScreenScaffold(modifier) {
        PackAppBar(
            title = "Packing guide",
            onBack = onBack,
            actions = {
                CountPill(
                    text = "${state.packedInstanceIds.size} packed",
                    contentColor = Color(0xFF3F7A5A),
                    background = Color(0xFFE1EEE7),
                )
            },
        )

        Column(Modifier.padding(horizontal = Spacing.gutter, vertical = 12.dp)) {
            StepProgress(step = step + 1, totalSteps = placements.size.coerceAtLeast(1))
            Spacer(Modifier.height(10.dp))
            Text(
                text = "STEP ${step + 1} OF ${placements.size}",
                color = TextTertiary,
                fontFamily = UiFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.5f.sp,
                letterSpacing = 0.3.sp,
            )
        }

        Column(Modifier.padding(horizontal = Spacing.gutter)) {
            PackCard(shape = RoundedCornerShape(28.dp), elevation = 10.dp, contentPadding = 12.dp) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .background(BrandTint, RoundedCornerShape(22.dp)),
                ) {
                    if (space != null && plan != null) {
                        IsometricCrate(
                            items = state.items,
                            space = space,
                            placements = plan.placements,
                            selectedInstanceId = current?.instanceId,
                            revealedThrough = step,
                            modifier = Modifier.fillMaxWidth().height(260.dp),
                            itemColorFor = { placement ->
                                itemColor(state.items.indexOfFirst { it.id == placement.specId })
                            },
                            animateEntrance = false,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(Spacing.base))

        if (current != null && spec != null && space != null) {
            Column(Modifier.padding(horizontal = Spacing.gutter)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ItemNumberTile(
                        number = itemIndex + 1,
                        color = itemColor(itemIndex.coerceAtLeast(0)),
                        size = 38.dp,
                        cornerRadius = 13.dp,
                        fontSize = 15,
                    )
                    Spacer(Modifier.size(12.dp))
                    Column {
                        Text(
                            text = spec.name,
                            color = TextPrimary,
                            fontFamily = UiFamily,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 20.sp,
                            letterSpacing = (-0.4).sp,
                        )
                        Text(
                            text = buildString {
                                append(formatDimensions(spec.dimensions, unit))
                                val copies = placements.count { it.specId == spec.id }
                                if (copies > 1) {
                                    val nth = placements
                                        .filter { it.specId == spec.id }
                                        .indexOfFirst { it.instanceId == current.instanceId } + 1
                                    append(" · ${ordinal(nth)} of $copies")
                                }
                            },
                            style = NumeralChip,
                            color = TextTertiary,
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                Instruction(text = orientationSentence(current, spec.dimensions))
                Spacer(Modifier.height(8.dp))
                Instruction(text = positionSentence(current, space))
                Spacer(Modifier.height(8.dp))
                Instruction(text = restingSentence(current, placements))
            }
        }

        Spacer(Modifier.weight(1f))

        Column(Modifier.padding(horizontal = Spacing.gutter)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (step > 0) {
                    SecondaryButton(
                        text = "Back",
                        onClick = { onStepChange(step - 1) },
                        modifier = Modifier.weight(1f),
                    )
                }
                PrimaryButton(
                    text = if (step >= placements.size - 1) "Done" else "Placed it",
                    onClick = {
                        current?.let { onMarkPacked(it.instanceId, true) }
                        if (step < placements.size - 1) onStepChange(step + 1) else onFinished()
                    },
                    modifier = Modifier.weight(1f),
                )
            }
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                PackTextButton(
                    text = "It doesn't fit here",
                    onClick = onDoesntFit,
                    color = Color(0xFF8E3322),
                )
            }
        }

        Spacer(Modifier.height(Spacing.sm))
    }
}

@Composable
private fun Instruction(text: String) {
    Text(
        text = text,
        color = TextSecondary,
        fontFamily = UiFamily,
        fontSize = 15.sp,
        lineHeight = 23.sp,
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceMuted, RoundedCornerShape(14.dp))
            .padding(12.dp),
    )
}

/** Which way up, described by what the person can see rather than by an axis name. */
private fun orientationSentence(
    placement: Placement,
    original: com.packabunch.packing.Dimensions,
): String {
    val upright = placement.orientedHeightMm == original.heightMm
    val turned = placement.orientedWidthMm == original.depthMm &&
        placement.orientedDepthMm == original.widthMm
    return when {
        upright && !turned -> "Keep it the normal way up, facing you."
        upright && turned -> "Keep it upright, but turn it a quarter turn so the long side runs front to back."
        placement.orientedHeightMm == original.widthMm -> "Tip it onto its side."
        else -> "Lay it flat, long side running left to right."
    }
}

/** Named against the container's own edges. Never "left of the screen". */
private fun positionSentence(placement: Placement, space: Space): String {
    val bounds = space.volume().boundsMm
    val nearLeft = placement.xMm - bounds.minXMm
    val nearRight = bounds.maxXMm - placement.box.maxXMm
    val nearFront = placement.yMm - bounds.minYMm
    val nearBack = bounds.maxYMm - placement.box.maxYMm

    val side = if (nearLeft <= nearRight) "left" else "right"
    val end = if (nearFront <= nearBack) "front" else "back"
    val touching = minOf(nearLeft, nearRight) < 15 && minOf(nearFront, nearBack) < 15

    return if (touching) {
        "Push it into the $end $side corner, touching both walls."
    } else {
        "Set it towards the $end $side."
    }
}

private fun restingSentence(placement: Placement, all: List<Placement>): String {
    if (placement.zMm == 0) return "It sits on the floor of the space."
    val under = all.firstOrNull {
        it.box.maxZMm == placement.zMm && it.box.coversFootprintOf(placement.box)
    }
    return if (under != null) {
        "It rests on the item you placed at step ${under.sequenceIndex + 1}."
    } else {
        "It rests on what is already packed beneath it."
    }
}

private fun ordinal(n: Int): String = when (n) {
    1 -> "1st"
    2 -> "2nd"
    3 -> "3rd"
    else -> "${n}th"
}

