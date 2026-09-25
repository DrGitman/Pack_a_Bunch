package com.packabunch.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.packabunch.ui.theme.Caution
import com.packabunch.ui.theme.CautionText
import com.packabunch.ui.theme.CautionTint
import com.packabunch.ui.theme.ErrorRed
import com.packabunch.ui.theme.ErrorText
import com.packabunch.ui.theme.ErrorTint
import com.packabunch.ui.theme.NumeralLarge
import com.packabunch.ui.theme.Radius
import com.packabunch.ui.theme.SectionLabel
import com.packabunch.ui.theme.Spacing
import com.packabunch.ui.theme.Success
import com.packabunch.ui.theme.SuccessTint
import com.packabunch.ui.theme.SurfaceMuted
import com.packabunch.ui.theme.TextPrimary
import com.packabunch.ui.theme.TextSecondary
import com.packabunch.ui.theme.TextTertiary
import com.packabunch.ui.theme.UiFamily

/**
 * No shadow. The designs are flat: cards, pills and the nav bar separate by colour and shape,
 * not by lift, and Compose's drop shadow reads as grey haze against the cream ground.
 *
 * Kept as a modifier rather than deleted from every call site, so one line brings shadows back
 * if the design ever wants them.
 */
fun Modifier.warmShadow(elevation: Dp, shape: Shape): Modifier = this

/** The standard white card: 22 dp corners, warm low shadow. */
@Composable
fun PackCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(Radius.card),
    elevation: Dp = 6.dp,
    background: Color = Color.White,
    contentPadding: Dp = Spacing.base,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .warmShadow(elevation, shape)
            .background(background, shape)
            .padding(contentPadding),
        content = content,
    )
}

/** A rounded tinted tile holding an icon — the 44 dp one beside a heading. */
@Composable
fun IconTile(
    icon: ImageVector,
    tint: Color,
    background: Color,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconSize: Dp = 22.dp,
    cornerRadius: Dp = Radius.iconTile,
) {
    Box(
        modifier = modifier
            .size(size)
            .background(background, RoundedCornerShape(cornerRadius)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize))
    }
}

/**
 * The square number tile that identifies an item. Always drawn next to the item's name —
 * the number and the name are what carry the identity, the colour only reinforces it.
 */
@Composable
fun ItemNumberTile(
    number: Int,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 30.dp,
    cornerRadius: Dp = 10.dp,
    fontSize: Int = 13,
) {
    Box(
        modifier = modifier.size(size).background(color, RoundedCornerShape(cornerRadius)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = number.toString(),
            color = Color.White,
            fontFamily = UiFamily,
            fontWeight = FontWeight.ExtraBold,
            fontSize = fontSize.sp,
        )
    }
}

enum class NoteTone { Neutral, Caution, Problem, Confirmed }

/**
 * The tinted explanation block used all over the app — "Measure the space inside the
 * crate", "This doesn't prove it can never fit". Tone maps to the status palette.
 */
@Composable
fun Note(
    text: String,
    modifier: Modifier = Modifier,
    tone: NoteTone = NoteTone.Neutral,
    icon: ImageVector? = PackIcons.Info,
    title: String? = null,
) {
    val background = when (tone) {
        NoteTone.Neutral -> SurfaceMuted
        NoteTone.Caution -> CautionTint
        NoteTone.Problem -> ErrorTint
        NoteTone.Confirmed -> SuccessTint
    }
    val bodyColor = when (tone) {
        NoteTone.Neutral -> TextSecondary
        NoteTone.Caution -> CautionText
        NoteTone.Problem -> ErrorText
        NoteTone.Confirmed -> Success
    }
    val iconColor = when (tone) {
        NoteTone.Neutral -> Color(0xFF8A7565)
        NoteTone.Caution -> Caution
        NoteTone.Problem -> ErrorRed
        NoteTone.Confirmed -> Success
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(background, RoundedCornerShape(14.dp))
            .padding(Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(18.dp).padding(top = 1.dp),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (title != null) {
                Text(
                    text = title,
                    color = TextPrimary,
                    fontFamily = UiFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                )
            }
            Text(
                text = text,
                color = bodyColor,
                fontFamily = UiFamily,
                fontSize = 13.sp,
                lineHeight = 19.sp,
            )
        }
    }
}

/**
 * One of the three result tiles: a big mono numeral over a quiet caption.
 *
 * The caption is the honest half. "modelled fill" is not "efficiency", and the tile is
 * built so the caption cannot be dropped while keeping the number.
 */
@Composable
fun StatTile(
    value: String,
    caption: String,
    modifier: Modifier = Modifier,
    valueColor: Color = TextPrimary,
) {
    PackCard(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        contentPadding = 0.dp,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 13.dp)) {
            Text(text = value, style = NumeralLarge, color = valueColor)
            Text(
                text = caption,
                color = Color(0xFF8A7565),
                fontFamily = UiFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.5f.sp,
                lineHeight = 15.sp,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
    }
}

/** The uppercase divider label: "WHAT YOU CAN DO INSTEAD". */
@Composable
fun SectionHeading(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = SectionLabel,
        color = TextTertiary,
        modifier = modifier,
    )
}

/** A row that reads as tappable: content, then a quiet chevron. */
@Composable
fun ChevronRow(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        content()
        Icon(
            PackIcons.Forward,
            contentDescription = null,
            tint = Color(0xFFC3B0A0),
            modifier = Modifier.size(19.dp),
        )
    }
}

/** Dashed outline placeholder — the disabled "Add an item" on the piece-limit screen. */
@Composable
fun DashedPlaceholder(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = PackIcons.Plus,
) {
    val shape = RoundedCornerShape(22.dp)
    val stroke = androidx.compose.ui.graphics.drawscope.Stroke(
        width = with(androidx.compose.ui.platform.LocalDensity.current) { 1.6.dp.toPx() },
        // A drawn dash, as the artboard has it. A plain border reads as a disabled field.
        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
            with(androidx.compose.ui.platform.LocalDensity.current) {
                floatArrayOf(7.dp.toPx(), 6.dp.toPx())
            },
        ),
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                drawRoundRect(
                    color = Color(0xFFD9C8B4),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(22.dp.toPx()),
                    style = stroke,
                )
            }
            .clip(shape)
            .padding(horizontal = Spacing.base, vertical = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = com.packabunch.ui.theme.Primary,
                modifier = Modifier.size(20.dp),
            )
        }
        Text(
            text = text,
            color = com.packabunch.ui.theme.PrimaryDark,
            fontFamily = UiFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
        )
    }
}
