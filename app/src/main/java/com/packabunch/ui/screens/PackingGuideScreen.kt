package com.packabunch.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.packabunch.ui.theme.Primary
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

    ArtboardPage(modifier) {
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
                            showControls = true,
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
            val nameOf = { placement: Placement -> state.items.firstOrNull { it.id == placement.specId }?.name ?: "item" }
            // `Guide step`: one white card — who, how big, then how, where and on what.
            Column(
                Modifier
                    .padding(horizontal = Spacing.gutter)
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(24.dp))
                    .padding(horizontal = 18.dp, vertical = 16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ItemNumberTile(
                        number = itemIndex + 1,
                        color = itemColor(itemIndex.coerceAtLeast(0)),
                        size = 44.dp,
                        cornerRadius = 14.dp,
                        fontSize = 17,
                    )
                    Spacer(Modifier.size(14.dp))
                    Column {
                        Text(
                            text = spec.name,
                            color = TextPrimary,
                            fontFamily = UiFamily,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 21.sp,
                            letterSpacing = (-0.5).sp,
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
                Instruction(GuideIcon.TURN, orientationSentence(current, spec.dimensions))
                Spacer(Modifier.height(10.dp))
                Instruction(GuideIcon.WHERE, positionSentence(current, space))
                Spacer(Modifier.height(10.dp))
                Instruction(GuideIcon.RESTS, restingSentence(current, placements, space, nameOf))
            }
        }

        PushDown()

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

private enum class GuideIcon { TURN, WHERE, RESTS }

/** One line of the step: its icon in the brand colour, then the sentence, key words in bold. */
@Composable
private fun Instruction(icon: GuideIcon, text: AnnotatedString) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Canvas(Modifier.padding(top = 3.dp).size(18.dp)) {
            val stroke = Stroke(1.7.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            val w = size.width; val h = size.height
            when (icon) {
                // A turning arrow: how it goes in.
                GuideIcon.TURN -> {
                    drawArc(Primary, -60f, 300f, false, Offset(w * 0.1f, h * 0.1f), Size(w * 0.8f, h * 0.8f), style = stroke)
                    val tip = Offset(w * 0.73f, h * 0.2f)
                    drawLine(Primary, tip, Offset(tip.x + w * 0.02f, tip.y + h * 0.26f), stroke.width, StrokeCap.Round)
                    drawLine(Primary, tip, Offset(tip.x - w * 0.24f, tip.y + h * 0.04f), stroke.width, StrokeCap.Round)
                }
                // A map pin: where it goes.
                GuideIcon.WHERE -> {
                    val pin = Path().apply {
                        moveTo(w * 0.5f, h * 0.95f)
                        cubicTo(w * 0.12f, h * 0.58f, w * 0.12f, h * 0.35f, w * 0.18f, h * 0.26f)
                        cubicTo(w * 0.3f, h * 0.04f, w * 0.7f, h * 0.04f, w * 0.82f, h * 0.26f)
                        cubicTo(w * 0.88f, h * 0.35f, w * 0.88f, h * 0.58f, w * 0.5f, h * 0.95f)
                        close()
                    }
                    drawPath(pin, Primary, style = stroke)
                    drawCircle(Primary, w * 0.12f, Offset(w * 0.5f, h * 0.38f), style = stroke)
                }
                // A floor with walls, and something standing on it: what it rests on.
                GuideIcon.RESTS -> {
                    val tray = Path().apply { moveTo(w * 0.08f, h * 0.35f); lineTo(w * 0.08f, h * 0.82f); lineTo(w * 0.92f, h * 0.82f); lineTo(w * 0.92f, h * 0.35f) }
                    drawPath(tray, Primary, style = stroke)
                    drawRect(Primary, Offset(w * 0.3f, h * 0.52f), Size(w * 0.4f, h * 0.3f), style = stroke)
                }
            }
        }
        Text(text = text, color = TextPrimary, fontFamily = UiFamily, fontSize = 15.sp, lineHeight = 22.sp)
    }
}

private fun AnnotatedString.Builder.bold(s: String) { withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(s) } }

/** Which way up, described by what the person can see rather than by an axis name. */
private fun orientationSentence(
    placement: Placement,
    original: com.packabunch.packing.Dimensions,
): AnnotatedString {
    val upright = placement.orientedHeightMm == original.heightMm
    val turned = placement.orientedWidthMm == original.depthMm &&
        placement.orientedDepthMm == original.widthMm
    return buildAnnotatedString {
        when {
            upright && !turned -> { append("Keep it the "); bold("normal way up"); append(", facing you.") }
            upright && turned -> { append("Keep it "); bold("upright"); append(", but turn it a quarter turn so the long side runs front to back.") }
            placement.orientedHeightMm == original.widthMm -> { append("Tip it onto its "); bold("side"); append(".") }
            else -> { append("Lay it "); bold("flat"); append(", long side running left to right.") }
        }
    }
}

/** Named against the container's own edges. Never "left of the screen". */
private fun positionSentence(placement: Placement, space: Space): AnnotatedString {
    val bounds = space.volume().boundsMm
    val nearLeft = placement.xMm - bounds.minXMm
    val nearRight = bounds.maxXMm - placement.box.maxXMm
    val nearFront = placement.yMm - bounds.minYMm
    val nearBack = bounds.maxYMm - placement.box.maxYMm

    val side = if (nearLeft <= nearRight) "left" else "right"
    val end = if (nearFront <= nearBack) "front" else "back"
    val touching = minOf(nearLeft, nearRight) < 15 && minOf(nearFront, nearBack) < 15

    return buildAnnotatedString {
        if (touching) {
            append("Push it into the "); bold("$end $side"); append(" corner, touching both walls.")
        } else {
            append("Set it towards the "); bold("$end $side"); append(".")
        }
    }
}

/**
 * What it stands on, named: the space's floor or the item under it, and — on the floor —
 * the thing already beside it, which is what a person actually lines it up against.
 */
private fun restingSentence(placement: Placement, all: List<Placement>, space: Space, nameOf: (Placement) -> String): AnnotatedString {
    val floor = space.name.trim().takeIf { it.isNotEmpty() }?.lowercase()?.let { "the $it floor" } ?: "the floor of the space"
    if (placement.zMm == 0) {
        val beside = all
            .filter { it.sequenceIndex < placement.sequenceIndex && it.zMm == 0 && touchesSideways(it, placement) }
            .maxByOrNull { it.sequenceIndex }
        return AnnotatedString(
            when {
                beside == null -> "It sits on $floor."
                beside.sequenceIndex == placement.sequenceIndex - 1 -> "It sits on $floor, beside the ${nameOf(beside).lowercase()} you just placed."
                else -> "It sits on $floor, beside the ${nameOf(beside).lowercase()} from step ${beside.sequenceIndex + 1}."
            },
        )
    }
    val under = all.firstOrNull {
        it.box.maxZMm == placement.zMm && it.box.coversFootprintOf(placement.box)
    }
    return AnnotatedString(
        if (under != null) "It rests on the ${nameOf(under).lowercase()} from step ${under.sequenceIndex + 1}."
        else "It rests on what is already packed beneath it.",
    )
}

/** Two boxes standing side by side: faces within a centimetre and a half, and overlapping along them. */
private fun touchesSideways(a: Placement, b: Placement): Boolean {
    val gap = 15
    val overlapX = a.xMm < b.box.maxXMm && b.xMm < a.box.maxXMm
    val overlapY = a.yMm < b.box.maxYMm && b.yMm < a.box.maxYMm
    val xTouch = kotlin.math.abs(a.box.maxXMm - b.xMm) <= gap || kotlin.math.abs(b.box.maxXMm - a.xMm) <= gap
    val yTouch = kotlin.math.abs(a.box.maxYMm - b.yMm) <= gap || kotlin.math.abs(b.box.maxYMm - a.yMm) <= gap
    return (xTouch && overlapY) || (yTouch && overlapX)
}

private fun ordinal(n: Int): String = when (n) {
    1 -> "1st"
    2 -> "2nd"
    3 -> "3rd"
    else -> "${n}th"
}
