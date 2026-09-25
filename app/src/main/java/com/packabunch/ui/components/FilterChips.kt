package com.packabunch.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * The All / In progress / Packed filter on the Projects screen.
 *
 * One brown pill exists for the whole row and slides — and stretches — to whichever chip you tap,
 * while that chip's label turns white and the old one's returns to brown. Nothing pops.
 *
 * Every background is painted by the Row itself rather than by each chip, which is what lets the
 * brown pill sit above the white capsules and below the labels without a second layout pass.
 */

private val ChipFill = Color(0xFFFFFFFF)
private val ChipBorder = Color(0xFFE6D9C8)
private val ChipSelected = Color(0xFF3F2718)
private val ChipText = Color(0xFF2B1D14)
private val ChipTextSelected = Color(0xFFFFFFFF)

private val ChipHeight = 38.dp
private val ChipGap = 10.dp
private val Standard = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
private const val ColourMillis = 260
private val ChipSpring = spring<Dp>(dampingRatio = 0.84f, stiffness = Spring.StiffnessMediumLow)

@Composable
fun FilterChips(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val bounds = remember { mutableStateMapOf<Int, Pair<Float, Float>>() }   // index -> (x, width)
    val here = bounds[selected]

    val pillX by animateDpAsState(
        targetValue = with(density) { (here?.first ?: 0f).toDp() },
        animationSpec = ChipSpring,
        label = "chip-x",
    )
    val pillW by animateDpAsState(
        targetValue = with(density) { (here?.second ?: 0f).toDp() },
        animationSpec = ChipSpring,
        label = "chip-w",
    )

    Row(
        modifier = modifier
            .height(ChipHeight)
            .drawBehind {
                val r = CornerRadius(size.height / 2f)
                val stroke = 1.dp.toPx()
                // the unselected capsules
                bounds.values.forEach { (x, w) ->
                    drawRoundRect(ChipFill, Offset(x, 0f), Size(w, size.height), r)
                    drawRoundRect(
                        ChipBorder,
                        Offset(x + stroke / 2, stroke / 2),
                        Size(w - stroke, size.height - stroke),
                        r,
                        style = Stroke(stroke),
                    )
                }
                // the one that travels
                if (here != null) {
                    drawRoundRect(
                        ChipSelected,
                        Offset(pillX.toPx(), 0f),
                        Size(pillW.toPx(), size.height),
                        r,
                    )
                }
            },
        horizontalArrangement = Arrangement.spacedBy(ChipGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEachIndexed { i, text ->
            val colour by animateColorAsState(
                targetValue = if (i == selected) ChipTextSelected else ChipText,
                animationSpec = tween(ColourMillis, easing = Standard),
                label = "chip-text",
            )
            Text(
                text = text,
                color = colour,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                modifier = Modifier
                    .selectable(selected = i == selected, onClick = { onSelect(i) })
                    .onGloballyPositioned { c ->
                        bounds[i] = c.positionInParent().x to c.size.width.toFloat()
                    }
                    .padding(horizontal = 18.dp, vertical = 9.dp),
            )
        }
    }
}
