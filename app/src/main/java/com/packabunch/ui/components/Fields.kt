package com.packabunch.ui.components
import com.packabunch.ui.motion.validationShake

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.packabunch.ui.format.LengthUnit
import com.packabunch.ui.motion.Motion
import com.packabunch.ui.motion.pressScale
import com.packabunch.ui.theme.ErrorRed
import com.packabunch.ui.theme.FieldLabel
import com.packabunch.ui.theme.NumeralField
import com.packabunch.ui.theme.Outline
import com.packabunch.ui.theme.Primary
import com.packabunch.ui.theme.SurfaceField
import com.packabunch.ui.theme.SurfaceMuted
import com.packabunch.ui.theme.TextPrimary
import com.packabunch.ui.theme.TextTertiary
import com.packabunch.ui.theme.UiFamily

/** The cm / in segmented control. The thumb slides; it does not cut. */
@Composable
fun UnitToggle(
    unit: LengthUnit,
    onUnitChange: (LengthUnit) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val horizontal = if (compact) 12.dp else 14.dp
    val vertical = if (compact) 5.dp else 6.dp
    val fontSize = if (compact) 12.sp else 12.5f.sp

    Row(
        modifier = modifier
            .background(SurfaceMuted, RoundedCornerShape(999.dp))
            .padding(3.dp),
    ) {
        LengthUnit.entries.forEach { candidate ->
            val selected = candidate == unit
            val background by animateColorAsState(
                targetValue = if (selected) Color.White else Color.Transparent,
                animationSpec = Motion.standardTween(Motion.SHORT_MS),
                label = "unitBackground",
            )
            val content by animateColorAsState(
                targetValue = if (selected) TextPrimary else Color(0xFF9E8B79),
                animationSpec = Motion.standardTween(Motion.SHORT_MS),
                label = "unitContent",
            )

            Box(
                modifier = Modifier
                    .then(
                        if (selected) Modifier.warmShadow(2.dp, RoundedCornerShape(999.dp))
                        else Modifier,
                    )
                    .background(background, RoundedCornerShape(999.dp))
                    .clip(RoundedCornerShape(999.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(color = Primary),
                    ) { onUnitChange(candidate) }
                    .padding(horizontal = horizontal, vertical = vertical),
            ) {
                Text(
                    text = candidate.shortLabel,
                    color = content,
                    fontFamily = UiFamily,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                    fontSize = fontSize,
                )
            }
        }
    }
}

/** The 50 × 30 switch from the artboards. The knob springs across rather than jumping. */
@Composable
fun PackSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val track by animateColorAsState(
        targetValue = when {
            !enabled -> Color(0xFFE7DDD2)
            checked -> Primary
            else -> Color(0xFFDCD1C4)
        },
        animationSpec = Motion.standardTween(Motion.SHORT_MS),
        label = "switchTrack",
    )
    val knobOffset by animateDpAsState(
        targetValue = if (checked) 23.dp else 3.dp,
        animationSpec = Motion.pressSpring(),
        label = "switchKnob",
    )

    Box(
        modifier = modifier
            .size(width = 50.dp, height = 30.dp)
            .background(track, RoundedCornerShape(15.dp))
            .clip(RoundedCornerShape(15.dp))
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onCheckedChange(!checked) },
    ) {
        Box(
            modifier = Modifier
                .offset(x = knobOffset, y = 3.dp)
                .size(24.dp)
                .warmShadow(2.dp, RoundedCornerShape(12.dp))
                .background(Color.White, RoundedCornerShape(12.dp)),
        )
    }
}

/** The − n + quantity control. */
@Composable
fun Stepper(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    range: IntRange = 1..99,
    background: Color = Color.White,
    buttonBackground: Color = SurfaceMuted,
) {
    Row(
        modifier = modifier
            .background(background, RoundedCornerShape(999.dp))
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StepperButton("−", enabled = value > range.first, background = buttonBackground) {
            onValueChange((value - 1).coerceIn(range))
        }
        Text(
            text = value.toString(),
            style = NumeralField.copy(fontSize = 16.sp),
            color = TextPrimary,
            modifier = Modifier.width(20.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        StepperButton("+", enabled = value < range.last, background = buttonBackground) {
            onValueChange((value + 1).coerceIn(range))
        }
    }
}

@Composable
private fun StepperButton(
    glyph: String,
    enabled: Boolean,
    background: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .pressScale(pressedScale = 0.9f, enabled = enabled)
            .background(background, RoundedCornerShape(17.dp))
            .clip(RoundedCornerShape(17.dp))
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = Primary),
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = glyph,
            color = if (enabled) Color(0xFF5C4A3A) else Color(0xFFC3B0A0),
            fontFamily = UiFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp,
        )
    }
}

/** The labelled text field — "NAME" over the value, with the terracotta caret. */
@Composable
fun LabelledTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    focused: Boolean = false,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                if (focused) SurfaceField else Color.White,
                RoundedCornerShape(16.dp),
            )
            .then(
                if (focused) Modifier.border(BorderStroke(1.5.dp, Primary), RoundedCornerShape(16.dp))
                else Modifier,
            )
            .padding(horizontal = 14.dp, vertical = 11.dp),
    ) {
        Text(text = label.uppercase(), style = FieldLabel, color = Color(0xFF8A7565))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = TextStyle(
                fontFamily = UiFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                color = TextPrimary,
            ),
            cursorBrush = SolidColor(Primary),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 3.dp),
            decorationBox = { inner ->
                if (value.isEmpty() && placeholder.isNotEmpty()) {
                    Text(
                        text = placeholder,
                        color = TextTertiary,
                        fontFamily = UiFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                    )
                }
                inner()
            },
        )
    }
}

/**
 * One dimension. The row layout from CreateSpace: label, the value in the numeric face,
 * then the unit. [error] turns the border red and is what blocks Next — it never silently
 * corrects what was typed.
 */
@Composable
fun DimensionField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    unit: LengthUnit,
    modifier: Modifier = Modifier,
    focused: Boolean = false,
    error: Boolean = false,
) {
    val borderColor = when {
        error -> ErrorRed
        focused -> Primary
        else -> Outline
    }
    val background = if (focused && !error) Color.White else SurfaceField

    Row(
        modifier = modifier
            .validationShake(error)
            .fillMaxWidth()
            .background(background, RoundedCornerShape(14.dp))
            .border(BorderStroke(1.5.dp, borderColor), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = label,
            color = Color(0xFF8A7565),
            fontFamily = UiFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 12.5f.sp,
            modifier = Modifier.width(44.dp),
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = NumeralField.copy(fontSize = 17.sp, color = TextPrimary),
            cursorBrush = SolidColor(Primary),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f),
        )
        Text(
            text = unit.shortLabel,
            color = TextTertiary,
            fontFamily = UiFamily,
            fontSize = 12.5f.sp,
        )
    }
}

/** The stacked variant used in the item editor's three-across grid. */
@Composable
fun StackedDimensionField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    error: Boolean = false,
) {
    Column(
        modifier = modifier
            .validationShake(error)
            .background(SurfaceField, RoundedCornerShape(16.dp))
            .border(
                BorderStroke(1.5.dp, if (error) ErrorRed else Outline),
                RoundedCornerShape(16.dp),
            )
            .padding(horizontal = 12.dp, vertical = 11.dp),
    ) {
        Text(
            text = label,
            color = Color(0xFF8A7565),
            fontFamily = UiFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 11.5f.sp,
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = NumeralField.copy(color = TextPrimary),
            cursorBrush = SolidColor(Primary),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
    }
}

/** A settings-style row: title, supporting line, and a switch. */
@Composable
fun SwitchRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(SurfaceField, RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = TextPrimary,
                fontFamily = UiFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.5f.sp,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = Color(0xFF8A7565),
                    fontFamily = UiFamily,
                    fontSize = 12.5f.sp,
                    lineHeight = 17.sp,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
        }
        PackSwitch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

/** Height reserved for a row so a screen's rhythm survives a missing subtitle. */
val MinRowHeight = 44.dp

@Composable
fun RowSpacerToMinimumTarget(modifier: Modifier = Modifier) {
    Box(modifier.height(MinRowHeight))
}

@Composable
fun FieldIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color) {
    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
}
