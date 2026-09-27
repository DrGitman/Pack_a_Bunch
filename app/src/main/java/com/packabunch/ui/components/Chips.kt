package com.packabunch.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.packabunch.packing.MeasurementSource
import com.packabunch.ui.motion.Motion
import com.packabunch.ui.motion.pressScale
import com.packabunch.ui.theme.CameraEstimateText
import com.packabunch.ui.theme.CameraEstimateTint
import com.packabunch.ui.theme.ChromeAlt
import com.packabunch.ui.theme.Ground
import com.packabunch.ui.theme.NumeralChip
import com.packabunch.ui.theme.Outline
import com.packabunch.ui.theme.SurfaceMuted
import com.packabunch.ui.theme.TypedInText
import com.packabunch.ui.theme.TypedInTint
import com.packabunch.ui.theme.UiFamily

/**
 * The selectable chip — space presets, notification types, "what do you mostly pack".
 *
 * Selection animates the fill rather than snapping, which is the one place a 150 ms colour
 * tween earns its keep: it tells you the tap landed on *this* chip when several sit in a row.
 */
@Composable
fun SelectableChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background by animateColorAsState(
        targetValue = if (selected) ChromeAlt else Color.White,
        animationSpec = Motion.standardTween(Motion.SHORT_MS),
        label = "chipBackground",
    )
    val content by animateColorAsState(
        targetValue = if (selected) Ground else Color(0xFF6B5849),
        animationSpec = Motion.standardTween(Motion.SHORT_MS),
        label = "chipContent",
    )

    val shape = RoundedCornerShape(999.dp)
    Box(
        modifier = modifier
            .pressScale(pressedScale = 0.95f)
            .background(background, shape)
            .then(if (selected) Modifier else Modifier.border(BorderStroke(1.dp, Outline), shape))
            .clip(shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = content),
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            text = text,
            color = content,
            fontFamily = UiFamily,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            fontSize = 13.sp,
        )
    }
}

/**
 * A row of mutually exclusive options in one track — the plan's 3D / Top / Front / Side.
 * Every option gets the same width, so the row never wraps and never shifts as the choice
 * changes; the chosen one fills with the dark chip colour.
 */
@Composable
fun SegmentedChips(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(999.dp)
    androidx.compose.foundation.layout.Row(
        modifier = modifier
            .background(Color.White, shape)
            .border(BorderStroke(1.dp, Outline), shape)
            .padding(3.dp),
    ) {
        options.forEachIndexed { index, label ->
            val on = index == selected
            val background by animateColorAsState(if (on) ChromeAlt else Color.Transparent, Motion.standardTween(Motion.SHORT_MS), label = "segment")
            val content by animateColorAsState(if (on) Ground else Color(0xFF6B5849), Motion.standardTween(Motion.SHORT_MS), label = "segmentText")
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp)
                    .pressScale(pressedScale = 0.95f)
                    .background(background, shape)
                    .clip(shape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(color = content),
                        role = androidx.compose.ui.semantics.Role.RadioButton,
                        onClick = { onSelect(index) },
                    ),
                contentAlignment = androidx.compose.ui.Alignment.Center,
            ) {
                Text(
                    text = label,
                    color = content,
                    fontFamily = UiFamily,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                    fontSize = 13.sp,
                    maxLines = 1,
                )
            }
        }
    }
}

/** A small on/off chip — "See-through" beside the plan. Tighter than [SelectableChip]. */
@Composable
fun ToggleChip(
    text: String,
    on: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(999.dp)
    val background by animateColorAsState(if (on) ChromeAlt else Color.White, Motion.standardTween(Motion.SHORT_MS), label = "toggle")
    val content by animateColorAsState(if (on) Ground else Color(0xFF6B5849), Motion.standardTween(Motion.SHORT_MS), label = "toggleText")
    Box(
        modifier = modifier
            .height(32.dp)
            .pressScale(pressedScale = 0.95f)
            .background(background, shape)
            .then(if (on) Modifier else Modifier.border(BorderStroke(1.dp, Outline), shape))
            .clip(shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = content),
                role = androidx.compose.ui.semantics.Role.Switch,
                onClick = onToggle,
            )
            .padding(horizontal = 14.dp),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        Text(text, color = content, fontFamily = UiFamily, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium, fontSize = 12.5f.sp, maxLines = 1)
    }
}

/** A measurement, set in the numeric face so it lines up with every other measurement. */
@Composable
fun DimensionChip(
    text: String,
    modifier: Modifier = Modifier,
    background: Color = SurfaceMuted,
    contentColor: Color = Color(0xFF5C4A3A),
    /** Tighter, for the pack cards, where two chips have to share one line on a narrow phone. */
    compact: Boolean = false,
) {
    Text(
        text = text,
        style = if (compact) NumeralChip.copy(fontSize = 11.5.sp) else NumeralChip,
        color = contentColor,
        maxLines = 1,
        modifier = modifier
            .background(background, RoundedCornerShape(999.dp))
            .padding(horizontal = if (compact) 9.dp else 13.dp, vertical = if (compact) 5.dp else 7.dp),
    )
}

/**
 * Where a number came from. This is not decoration and it is not optional: the copy rules
 * require provenance wherever a stored dimension is shown, and there is deliberately no
 * variant of this badge that expresses a confidence level, because we do not have one.
 */
@Composable
fun ProvenanceBadge(
    source: MeasurementSource,
    modifier: Modifier = Modifier,
) {
    val (label, textColor, tint) = when (source) {
        MeasurementSource.CAMERA_ESTIMATE ->
            Triple("CAMERA ESTIMATE", CameraEstimateText, CameraEstimateTint)
        MeasurementSource.TYPED_IN ->
            Triple("TYPED IN", TypedInText, TypedInTint)
        // The wording is an instruction, not a label. A looked-up figure describes the
        // kind of thing, not the one in front of you, and the badge has to say so.
        MeasurementSource.SUGGESTED ->
            Triple("SUGGESTED, CHECK IT", Color(0xFF6E4708), Color(0xFFFAEEDA))
    }

    Text(
        text = label,
        color = textColor,
        fontFamily = UiFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 11.5f.sp,
        modifier = modifier
            .background(tint, RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

/** A count or status pill — "20 / 20", "3 packs". */
@Composable
fun CountPill(
    text: String,
    modifier: Modifier = Modifier,
    contentColor: Color = Color(0xFFB4761A),
    background: Color = Color(0xFFFAEEDA),
) {
    Text(
        text = text,
        style = NumeralChip.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium),
        color = contentColor,
        modifier = modifier
            .background(background, RoundedCornerShape(999.dp))
            .padding(horizontal = 11.dp, vertical = 5.dp),
    )
}
