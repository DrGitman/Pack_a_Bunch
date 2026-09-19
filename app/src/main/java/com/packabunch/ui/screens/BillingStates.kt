package com.packabunch.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.packabunch.ui.components.IconTile
import com.packabunch.ui.components.Note
import com.packabunch.ui.components.NoteTone
import com.packabunch.ui.components.PackIcons
import com.packabunch.ui.components.PackTextButton
import com.packabunch.ui.components.PrimaryButton
import com.packabunch.ui.components.ScreenScaffold
import com.packabunch.ui.components.SecondaryButton
import com.packabunch.ui.theme.Caution
import com.packabunch.ui.theme.CautionTint
import com.packabunch.ui.theme.ErrorRed
import com.packabunch.ui.theme.ErrorTint
import com.packabunch.ui.theme.NumeralChip
import com.packabunch.ui.theme.Spacing
import com.packabunch.ui.theme.Success
import com.packabunch.ui.theme.SuccessTint
import com.packabunch.ui.theme.SurfaceMuted
import com.packabunch.ui.theme.TextPrimary
import com.packabunch.ui.theme.TextSecondary
import com.packabunch.ui.theme.TextTertiary
import com.packabunch.ui.theme.UiFamily

/**
 * The five things a purchase can do — `PurchaseSuccess`, `PurchasePending`,
 * `PurchaseCancelled`, `PurchaseFailed`, `OfflineBilling`.
 *
 * The rule that governs all five: **entitlement follows verified state, never a local
 * "payment clicked" boolean.** Reaching [PurchaseOutcome.Succeeded] is not what unlocks
 * anything — the entitlement check does. This screen only reports what already happened.
 *
 * Pending is the one most often got wrong. A pending purchase is *not* a failure and *not*
 * a success: some payment methods take days, and the honest thing is to say so and let the
 * person carry on using the free tier meanwhile.
 */
enum class PurchaseOutcome { Succeeded, Pending, Cancelled, Failed, Offline }

@Composable
fun PurchaseOutcomeScreen(
    outcome: PurchaseOutcome,
    /** Shown only on failure, so support can be given something to search for. */
    referenceCode: String? = null,
    onContinue: () -> Unit,
    onTryAgain: () -> Unit,
    onContactSupport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spec = when (outcome) {
        PurchaseOutcome.Succeeded -> OutcomeSpec(
            icon = PackIcons.Check,
            tint = Success,
            background = SuccessTint,
            title = "You're on Pack-a-Bunch Pro",
            body = "Every cap is lifted. Your saved packs are already there — nothing to " +
                "restore or move.",
            primary = "Get back to it",
            secondary = null,
        )

        PurchaseOutcome.Pending -> OutcomeSpec(
            icon = PackIcons.Clock,
            tint = Caution,
            background = CautionTint,
            title = "Waiting on your payment",
            body = "Some payment methods take a little while to clear — occasionally a few " +
                "days. Pack-a-Bunch Pro switches on by itself the moment it does. You haven't been " +
                "charged twice, and there's nothing to do.",
            primary = "Carry on for now",
            secondary = null,
        )

        PurchaseOutcome.Cancelled -> OutcomeSpec(
            icon = PackIcons.Close,
            tint = TextTertiary,
            background = SurfaceMuted,
            title = "Nothing was charged",
            body = "You backed out before paying, which is completely fine. Everything you " +
                "had is still exactly where it was.",
            primary = "Back to the app",
            secondary = "Have another look at Pro",
        )

        PurchaseOutcome.Failed -> OutcomeSpec(
            icon = PackIcons.Warning,
            tint = ErrorRed,
            background = ErrorTint,
            title = "The payment didn't go through",
            body = "Google Play couldn't complete it. Nothing has been charged. This is " +
                "usually the card or the Play account rather than anything in this app.",
            primary = "Try again",
            secondary = "Get help",
        )

        PurchaseOutcome.Offline -> OutcomeSpec(
            icon = PackIcons.Offline,
            tint = Caution,
            background = CautionTint,
            title = "You're offline",
            body = "Subscribing needs a connection. Everything else — measuring, planning, " +
                "the packing guide — works perfectly well without one.",
            primary = "Carry on offline",
            secondary = "Try again",
        )
    }

    ScreenScaffold(modifier) {
        Spacer(Modifier.weight(1f))

        Column(Modifier.padding(horizontal = Spacing.gutter)) {
            IconTile(
                icon = spec.icon,
                tint = spec.tint,
                background = spec.background,
                size = 64.dp,
                iconSize = 30.dp,
            )

            Spacer(Modifier.height(Spacing.lg))

            Text(
                text = spec.title,
                color = TextPrimary,
                fontFamily = UiFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 29.sp,
                lineHeight = 36.sp,
                letterSpacing = (-0.9).sp,
            )

            Spacer(Modifier.height(10.dp))

            Text(
                text = spec.body,
                color = TextSecondary,
                fontFamily = UiFamily,
                fontSize = 15.sp,
                lineHeight = 23.sp,
            )

            if (outcome == PurchaseOutcome.Pending) {
                Spacer(Modifier.height(Spacing.base))
                Note(
                    text = "Until it clears you're on the free plan, with everything that " +
                        "includes.",
                    tone = NoteTone.Neutral,
                    icon = PackIcons.Info,
                )
            }

            if (referenceCode != null && outcome == PurchaseOutcome.Failed) {
                Spacer(Modifier.height(Spacing.base))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(SurfaceMuted, RoundedCornerShape(16.dp))
                        .padding(14.dp),
                ) {
                    Column {
                        Text(
                            "REFERENCE",
                            color = TextTertiary,
                            fontFamily = UiFamily,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 10.5f.sp,
                            letterSpacing = 0.7.sp,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(text = referenceCode, style = NumeralChip.copy(fontSize = 14.sp), color = TextPrimary)
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))

        Column(
            Modifier.padding(horizontal = Spacing.gutter),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PrimaryButton(text = spec.primary, onClick = onContinue)
            spec.secondary?.let { label ->
                SecondaryButton(
                    text = label,
                    onClick = if (outcome == PurchaseOutcome.Failed) onContactSupport else onTryAgain,
                )
            }
        }

        Spacer(Modifier.height(Spacing.base))
    }
}

private data class OutcomeSpec(
    val icon: ImageVector,
    val tint: Color,
    val background: Color,
    val title: String,
    val body: String,
    val primary: String,
    val secondary: String?,
)
