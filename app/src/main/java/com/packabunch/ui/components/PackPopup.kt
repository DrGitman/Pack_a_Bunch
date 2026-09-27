package com.packabunch.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.packabunch.ui.theme.BodyInk
import com.packabunch.ui.theme.Spacing
import com.packabunch.ui.theme.SurfaceMuted
import com.packabunch.ui.theme.TextPrimary
import com.packabunch.ui.theme.TextSecondary
import com.packabunch.ui.theme.TextTertiary
import com.packabunch.ui.theme.UiFamily

/**
 * The app's pop-up: a white card over the dimmed page, as in the `PurchasePending`,
 * `PurchaseSuccess`, `PurchaseCancelled`, `PurchaseFailed` and lost-tracking frames — an icon
 * on its own tint, a heading, a sentence, an optional box of detail, one filled button, one
 * outlined one and an optional line underneath.
 *
 * The page behind stays drawn and dimmed rather than replaced, so it is plain what the pop-up
 * is about and that dismissing it goes back to exactly that.
 */
@Composable
fun PackPopup(
    icon: ImageVector,
    iconTint: Color,
    iconBackground: Color,
    title: String,
    body: String,
    primary: String,
    onPrimary: () -> Unit,
    modifier: Modifier = Modifier,
    secondary: String? = null,
    onSecondary: () -> Unit = {},
    /** The box of detail under the body. */
    detail: AnnotatedString? = null,
    /** An icon at the start of [detail] — the tick beside what was kept. */
    detailIcon: ImageVector? = null,
    detailIconTint: Color = TextSecondary,
    footnote: String? = null,
    /** Back and a tap on the dimmed page; null when the pop-up must be answered. */
    onDismiss: (() -> Unit)? = null,
) {
    if (onDismiss != null) BackHandler(onBack = onDismiss)
    val shown = remember { Animatable(0f) }
    LaunchedEffect(Unit) { shown.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)) }
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = shown.value.coerceIn(0f, 1f) }
                .background(Color(0x8C2B1D14))
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onDismiss?.invoke() },
        )
        Column(
            Modifier
                .padding(horizontal = Spacing.lg)
                .widthIn(max = 420.dp)
                .graphicsLayer {
                    alpha = shown.value.coerceIn(0f, 1f)
                    val s = 0.92f + 0.08f * shown.value
                    scaleX = s; scaleY = s
                }
                .warmShadow(24.dp, RoundedCornerShape(28.dp))
                .background(Color.White, RoundedCornerShape(28.dp))
                .swallowTaps()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.size(58.dp).background(iconBackground, RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text(
                title,
                color = TextPrimary,
                fontFamily = UiFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 20.sp,
                lineHeight = 25.sp,
                letterSpacing = (-0.4).sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(body, color = TextSecondary, fontFamily = UiFamily, fontSize = 14.sp, lineHeight = 20.sp, textAlign = TextAlign.Center)
            if (detail != null) {
                Spacer(Modifier.height(14.dp))
                Row(
                    Modifier.fillMaxWidth().background(SurfaceMuted, RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (detailIcon != null) Icon(detailIcon, null, tint = detailIconTint, modifier = Modifier.size(18.dp))
                    Text(detail, color = BodyInk, fontFamily = UiFamily, fontSize = 13.sp, lineHeight = 19.sp)
                }
            }
            Spacer(Modifier.height(18.dp))
            PrimaryButton(text = primary, onClick = onPrimary, height = 50.dp)
            if (secondary != null) {
                Spacer(Modifier.height(8.dp))
                SecondaryButton(text = secondary, onClick = onSecondary, height = 48.dp, contentColor = TextPrimary)
            }
            if (footnote != null) {
                Spacer(Modifier.height(10.dp))
                Text(footnote, color = TextTertiary, fontFamily = UiFamily, fontSize = 12.sp, lineHeight = 17.sp, textAlign = TextAlign.Center)
            }
        }
    }
}
