package com.packabunch.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
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

/** A measurement, set in the numeric face so it lines up with every other measurement. */
@Composable
fun DimensionChip(
    text: String,
    modifier: Modifier = Modifier,
    background: Color = SurfaceMuted,
    contentColor: Color = Color(0xFF5C4A3A),
) {
    Text(
        text = text,
        style = NumeralChip,
        color = contentColor,
        modifier = modifier
            .background(background, RoundedCornerShape(999.dp))
            .padding(horizontal = 13.dp, vertical = 7.dp),
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
