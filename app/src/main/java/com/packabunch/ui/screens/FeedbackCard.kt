package com.packabunch.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.packabunch.ui.components.PackIcons
import com.packabunch.ui.components.PrimaryButton
import com.packabunch.ui.components.swallowTaps
import com.packabunch.ui.components.warmShadow
import com.packabunch.ui.theme.Accent
import com.packabunch.ui.theme.OutlineStrong
import com.packabunch.ui.theme.Primary
import com.packabunch.ui.theme.Spacing
import com.packabunch.ui.theme.SurfaceField
import com.packabunch.ui.theme.SurfaceMuted
import com.packabunch.ui.theme.Outline
import com.packabunch.ui.theme.TextPrimary
import com.packabunch.ui.theme.TextSecondary
import com.packabunch.ui.theme.TextTertiary
import com.packabunch.ui.theme.UiFamily
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * "How are we doing?" — the app's own feedback card, shown now and then after a pack is
 * finished (see [com.packabunch.review.ReviewPrompt.feedbackDue]). The layout of the reference
 * the owner picked (close in a soft circle, title, a line under it, five stars, a roomy box
 * for words, one wide button and "Skip"), drawn in the app's own cream, terracotta and brown.
 *
 * It is feedback to us only. It never decides whether Google Play's review sheet appears:
 * that is asked for separately, whatever the stars were, as Play's policy requires.
 */
@Composable
fun FeedbackCard(
    onSend: (stars: Int, words: String) -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
    /** True while it is being sent; the button waits. */
    sending: Boolean = false,
    /** Said under the button when sending failed. */
    problem: String? = null,
) {
    BackHandler(onBack = onSkip)
    var stars by remember { mutableIntStateOf(0) }
    var words by remember { mutableStateOf("") }
    val shown = remember { Animatable(0f) }
    LaunchedEffect(Unit) { shown.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)) }

    Box(modifier.fillMaxSize().imePadding(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = shown.value.coerceIn(0f, 1f) }
                .background(Color(0x8C2B1D14))
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onSkip() },
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
                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // The close button, in its own soft circle at the top right.
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                Box(
                    Modifier
                        .size(34.dp)
                        .background(SurfaceMuted, CircleShape)
                        .clickable(onClick = onSkip)
                        .semantics { contentDescription = "Close" },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(PackIcons.Close, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                }
            }
            Text(
                "How are we doing?",
                color = TextPrimary,
                fontFamily = UiFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 22.sp,
                letterSpacing = (-0.4).sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Your opinion means the world.\nPlease share it with us.",
                color = TextSecondary,
                fontFamily = UiFamily,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                for (i in 1..5) Star(filled = i <= stars, onClick = { stars = i }, label = "$i of 5 stars")
            }
            Spacer(Modifier.height(18.dp))
            BasicTextField(
                value = words,
                onValueChange = { if (it.length <= 1000) words = it },
                textStyle = TextStyle(color = TextPrimary, fontFamily = UiFamily, fontSize = 15.sp, lineHeight = 21.sp),
                cursorBrush = SolidColor(Primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .background(SurfaceField, RoundedCornerShape(18.dp))
                    .border(1.dp, Outline, RoundedCornerShape(18.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                decorationBox = { inner ->
                    Box {
                        if (words.isEmpty()) Text("Share your thoughts with us…", color = TextTertiary, fontFamily = UiFamily, fontSize = 15.sp)
                        inner()
                    }
                },
            )
            Spacer(Modifier.height(16.dp))
            PrimaryButton(
                text = if (sending) "Sending…" else "Rate now",
                onClick = { onSend(stars, words) },
                enabled = stars > 0 && !sending,
            )
            problem?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = com.packabunch.ui.theme.ErrorRed, fontFamily = UiFamily, fontSize = 13.sp, textAlign = TextAlign.Center)
            }
            Text(
                "Skip",
                color = TextSecondary,
                fontFamily = UiFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                modifier = Modifier
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onSkip)
                    .padding(horizontal = 24.dp, vertical = 14.dp),
            )
        }
    }
}

/** One star: terracotta when chosen, a soft outline-brown when not; it pops a little when tapped. */
@Composable
private fun Star(filled: Boolean, onClick: () -> Unit, label: String) {
    val scale by animateFloatAsState(if (filled) 1.08f else 1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium), label = "star")
    Canvas(
        Modifier
            .size(38.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onClick)
            .semantics { contentDescription = label },
    ) {
        val c = Offset(size.width / 2, size.height / 2 + size.height * 0.03f)
        val outer = size.minDimension / 2 * 0.9f
        val inner = outer * 0.48f
        val path = Path()
        for (k in 0 until 10) {
            val r = if (k % 2 == 0) outer else inner
            val a = -PI / 2 + k * PI / 5
            val p = Offset(c.x + (r * cos(a)).toFloat(), c.y + (r * sin(a)).toFloat())
            if (k == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
        }
        path.close()
        val colour = if (filled) Accent else OutlineStrong
        drawPath(path, colour)
        // Rounded points, as in the reference: the same colour stroked with round joins.
        drawPath(path, colour, style = androidx.compose.ui.graphics.drawscope.Stroke(
            width = outer * 0.16f, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    }
}
