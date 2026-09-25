package com.packabunch.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * The "what went wrong" option list on the Doesn't-fit screen.
 *
 * From "Selection / Packing issue · Motion". Picking an option cross-fades the outline and the
 * ring over 180ms while the filled dot springs up from nothing — the outgoing row fades rather
 * than cutting, so at any instant you can see both where the selection was and where it is going.
 *
 *     OptionList(
 *         options = issues,
 *         selected = picked,
 *         onSelect = { picked = it },
 *         title = { it.title },
 *         detail = { it.detail },
 *     )
 */

private val RowFill = Color(0xFFFFFFFF)
private val RowBorder = Color(0xFFEDE0D0)
private val RowBorderActive = Color(0xFFE08A46)
private val TitleInk = Color(0xFF2B1D14)
private val DetailInk = Color(0xFF7C6857)
private val RingIdle = Color(0xFFD9C8B4)
private val RingActive = Color(0xFFE08A46)

// Figma: outline/ring cross-fade over 0.18s; dot scale 0.01 → 1 across 0.34s, sprung.
private const val CrossFadeMillis = 180
private val DotSpring = spring<Float>(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium)

@Composable
fun <T> OptionList(
    options: List<T>,
    selected: T?,
    onSelect: (T) -> Unit,
    title: (T) -> String,
    modifier: Modifier = Modifier,
    detail: ((T) -> String?)? = null,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        options.forEach { option ->
            OptionRow(
                title = title(option),
                detail = detail?.invoke(option),
                active = option == selected,
                onClick = { onSelect(option) },
            )
        }
    }
}

@Composable
private fun OptionRow(
    title: String,
    detail: String?,
    active: Boolean,
    onClick: () -> Unit,
) {
    // One shared progress drives the outline, the ring and the dot, so they can never disagree
    // about which row is selected mid-transition.
    val lit by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = tween(CrossFadeMillis),
        label = "option-lit",
    )
    val dot by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = if (active) DotSpring else tween(CrossFadeMillis),
        label = "option-dot",
    )

    val press = remember { MutableInteractionSource() }

    Row(
        modifier = Modifier
            .pressScale(press, pressedScale = 0.985f)
            .selectable(selected = active, interactionSource = press, indication = null, onClick = onClick)
            .background(RowFill, RoundedCornerShape(14.dp))
            .drawBehind {
                // Both borders are painted here so they cross-fade in place rather than one
                // shape being swapped for another.
                val r = 14.dp.toPx()
                val w = 1.dp.toPx()
                drawRoundRect(
                    RowBorder.copy(alpha = 1f - lit),
                    topLeft = Offset(w / 2, w / 2),
                    size = Size(size.width - w, size.height - w),
                    cornerRadius = CornerRadius(r),
                    style = Stroke(w),
                )
                if (lit > 0f) {
                    val aw = 1.6.dp.toPx()
                    drawRoundRect(
                        RowBorderActive.copy(alpha = lit),
                        topLeft = Offset(aw / 2, aw / 2),
                        size = Size(size.width - aw, size.height - aw),
                        cornerRadius = CornerRadius(r),
                        style = Stroke(aw),
                    )
                }
            }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(11.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .padding(top = 1.dp)
                .size(17.dp)
                .border(1.4.dp, lerpColor(RingIdle, RingActive, lit), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    .graphicsLayer {
                        scaleX = dot
                        scaleY = dot
                        alpha = dot
                    }
                    .background(RingActive, CircleShape),
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = TitleInk, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            if (detail != null) {
                Text(detail, color = DetailInk, fontSize = 11.sp, lineHeight = 15.sp)
            }
        }
    }
}

private fun lerpColor(from: Color, to: Color, t: Float): Color {
    val k = t.coerceIn(0f, 1f)
    return Color(
        red = from.red + (to.red - from.red) * k,
        green = from.green + (to.green - from.green) * k,
        blue = from.blue + (to.blue - from.blue) * k,
        alpha = from.alpha + (to.alpha - from.alpha) * k,
    )
}
