package com.packabunch.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.packabunch.packing.TierLimits
import com.packabunch.ui.PackEditorState
import com.packabunch.ui.components.PackCard
import com.packabunch.ui.components.PackIcons
import com.packabunch.ui.components.PackTextButton
import com.packabunch.ui.components.PrimaryButton
import com.packabunch.ui.components.ScreenScaffold
import com.packabunch.ui.components.SecondaryButton
import com.packabunch.ui.components.SelectableChip
import com.packabunch.ui.components.StatTile
import com.packabunch.ui.components.IconTile
import com.packabunch.ui.theme.ErrorRed
import com.packabunch.ui.theme.Spacing
import com.packabunch.ui.theme.Success
import com.packabunch.ui.theme.SuccessTint
import com.packabunch.ui.theme.TextPrimary
import com.packabunch.ui.theme.TextSecondary
import com.packabunch.ui.theme.UiFamily

/**
 * Packed — `design/artboards/PackingDone.dc.html`.
 *
 * The question in the middle of this screen is the most valuable thing in the app: **did it
 * actually go in?** It is the only feedback loop that connects the geometric model to real
 * crates, and the plan is explicit that a disagreement between them means fixing the model
 * or documenting a limitation — never working around it in the marketing.
 *
 * The upgrade prompt sits below that question, not above it, and only appears after a
 * finished pack. Asking for money before delivering the result is how a utility loses the
 * benefit of the doubt.
 */
@Composable
fun PackingDoneScreen(
    state: PackEditorState,
    limits: TierLimits,
    savedPackCount: Int,
    onDone: () -> Unit,
    onSeePlan: () -> Unit,
    onUpgrade: () -> Unit,
    onFitAnswer: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val plan = state.plan
    var answered by remember { mutableStateOf<Boolean?>(null) }

    ScreenScaffold(modifier) {
        Spacer(Modifier.height(Spacing.xl))

        Column(Modifier.padding(horizontal = Spacing.gutter)) {
            IconTile(
                icon = PackIcons.Check,
                tint = Success,
                background = SuccessTint,
                size = 56.dp,
                iconSize = 28.dp,
            )
            Spacer(Modifier.height(Spacing.base))
            Text(
                text = "${state.name.ifEmpty { "The pack" }} is packed",
                color = TextPrimary,
                fontFamily = UiFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 29.sp,
                lineHeight = 36.sp,
                letterSpacing = (-0.9).sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "${plan?.placements?.size ?: 0} steps, in the order that keeps " +
                    "everything supported.",
                color = TextSecondary,
                fontFamily = UiFamily,
                fontSize = 15.sp,
                lineHeight = 23.sp,
            )
        }

        Spacer(Modifier.height(Spacing.lg))

        if (plan != null) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                StatTile(
                    value = "${plan.metrics.placedInstanceCount}/${plan.metrics.requestedInstanceCount}",
                    caption = "pieces in",
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    value = "${plan.metrics.modelledFillPercent}%",
                    caption = "modelled fill",
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    value = plan.metrics.unplacedInstanceCount.toString(),
                    caption = "left over",
                    valueColor = if (plan.metrics.unplacedInstanceCount > 0) ErrorRed else TextPrimary,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Spacer(Modifier.height(Spacing.base))

        Column(Modifier.padding(horizontal = Spacing.gutter)) {
            PackCard(elevation = 6.dp, contentPadding = Spacing.base) {
                Text(
                    text = "Did it actually go in?",
                    color = TextPrimary,
                    fontFamily = UiFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Honest answers here are what tell us whether the model matches " +
                        "real crates.",
                    color = TextSecondary,
                    fontFamily = UiFamily,
                    fontSize = 13.5f.sp,
                    lineHeight = 20.sp,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SelectableChip(
                        text = "Yes, it fitted",
                        selected = answered == true,
                        onClick = { answered = true; onFitAnswer(true) },
                    )
                    SelectableChip(
                        text = "No, it didn't",
                        selected = answered == false,
                        onClick = { answered = false; onFitAnswer(false) },
                    )
                }
            }
        }

        Spacer(Modifier.weight(1f))

        // Only shown when the free tier genuinely cannot hold another pack — not on every
        // completed pack, and never before the result has been delivered.
        if (!limits.allowsAnotherPack(savedPackCount)) {
            Column(Modifier.padding(horizontal = Spacing.gutter, vertical = 8.dp)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF5E4D6), RoundedCornerShape(20.dp))
                        .padding(14.dp),
                ) {
                    Column {
                        Text(
                            "Keep this one and start another?",
                            color = TextPrimary,
                            fontFamily = UiFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.5f.sp,
                        )
                        Text(
                            "Free holds ${limits.maxSavedPacks} saved pack at a time.",
                            color = Color(0xFF7C4223),
                            fontFamily = UiFamily,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                        Spacer(Modifier.height(10.dp))
                        SecondaryButton(text = "See Pack Plus", onClick = onUpgrade)
                    }
                }
            }
        }

        Column(Modifier.padding(horizontal = Spacing.gutter)) {
            PrimaryButton(text = "Done", onClick = onDone)
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                PackTextButton(text = "Look at the plan again", onClick = onSeePlan)
            }
        }

        Spacer(Modifier.height(Spacing.sm))
    }
}
