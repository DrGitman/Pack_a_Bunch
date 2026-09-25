package com.packabunch.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.packabunch.ui.motion.pressScale
import com.packabunch.ui.theme.OutlineStrong
import com.packabunch.ui.theme.Primary
import com.packabunch.ui.theme.PrimaryDark
import com.packabunch.ui.theme.UiFamily

/**
 * Buttons, at the sizes the tokens fix: 56 dp primary, 54 dp outlined, 48 dp text, and
 * nothing tappable under 44 dp anywhere.
 *
 * All three take the same press treatment — a small spring scale plus the ripple. The
 * scale is what makes a full-width button feel like it has any give; the ripple alone on a
 * 56 dp slab reads as nothing happening.
 */

private val ButtonShadow = Color(0xFFA65C34)

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    height: Dp = 56.dp,
    /** A quiet pill inside the button, for a count that belongs to the action. */
    trailing: String? = null,
) {
    val interaction = remembered()
    val view = LocalView.current
    val shape = RoundedCornerShape(height / 2)
    val background = if (enabled) Primary else Color(0xFFDFD3C6)
    val content = if (enabled) Color.White else Color(0xFFA2907F)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .pressScale(pressedScale = 0.95f, enabled = enabled, interactionSource = interaction)
            .then(if (enabled) Modifier.warmShadow(10.dp, shape) else Modifier)
            .background(background, shape)
            .clip(shape)
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                role = Role.Button,
                indication = ripple(color = Color.White),
                onClick = {
                    view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                    onClick()
                },
            ),
        horizontalArrangement = Arrangement.spacedBy(9.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
        }
        Text(
            text = text,
            color = content,
            fontFamily = UiFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
        )
        if (trailing != null) {
            Text(
                text = trailing,
                color = content,
                fontFamily = UiFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                modifier = Modifier
                    .background(Color(0x33FFFFFF), RoundedCornerShape(999.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}

/**
 * The outlined button. Used for "Measure with the camera", which is deliberately *not*
 * quieter than the typed path — the copy rules require both routes at equal prominence,
 * so this sits directly under the typed fields rather than being tucked away.
 */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    height: Dp = 54.dp,
    contentColor: Color = PrimaryDark,
    iconTint: Color = Primary,
    backgroundColor: Color = Color.White,
) {
    val interaction = remembered()
    val view = LocalView.current
    val shape = RoundedCornerShape(height / 2)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .pressScale(pressedScale = 0.95f, enabled = enabled, interactionSource = interaction)
            .background(backgroundColor, shape)
            .border(BorderStroke(1.5.dp, OutlineStrong), shape)
            .clip(shape)
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                role = Role.Button,
                indication = ripple(color = Primary),
                onClick = {
                    view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                    onClick()
                },
            ),
        horizontalArrangement = Arrangement.spacedBy(9.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
        }
        Text(
            text = text,
            color = contentColor,
            fontFamily = UiFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 15.5f.sp,
        )
    }
}

@Composable
fun PackTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = PrimaryDark,
    enabled: Boolean = true,
) {
    val interaction = remembered()
    val view = LocalView.current
    Box(
        modifier = modifier
            .height(48.dp)
            .pressScale(pressedScale = 0.95f, enabled = enabled, interactionSource = interaction)
            .clip(RoundedCornerShape(24.dp))
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                role = Role.Button,
                indication = ripple(color = color),
                onClick = {
                    view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                    onClick()
                },
            )
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = color,
            fontFamily = UiFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
        )
    }
}

/** The 44 dp circular icon button in app bars. Bounded ripple, minimum touch target met. */
@Composable
fun PackIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color(0xFF2B1D14),
    size: Dp = 44.dp,
    iconSize: Dp = 22.dp,
    background: Color = Color.Transparent,
    enabled: Boolean = true,
) {
    val interaction = remembered()
    val view = LocalView.current
    Box(
        modifier = modifier
            .size(size)
            .pressScale(pressedScale = 0.95f, enabled = enabled, interactionSource = interaction)
            .background(background, RoundedCornerShape(size / 2))
            .clip(RoundedCornerShape(size / 2))
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                role = Role.Button,
                indication = ripple(bounded = true, color = tint),
                onClick = {
                    view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                    onClick()
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(iconSize))
    }
}

@Composable
private fun remembered(): MutableInteractionSource = remember { MutableInteractionSource() }

