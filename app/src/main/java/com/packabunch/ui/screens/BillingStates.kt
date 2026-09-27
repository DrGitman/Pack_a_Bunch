package com.packabunch.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.packabunch.packing.TierLimits
import com.packabunch.ui.components.PackIcons
import com.packabunch.ui.components.PackPopup
import com.packabunch.ui.theme.Caution
import com.packabunch.ui.theme.CautionTint
import com.packabunch.ui.theme.ErrorRed
import com.packabunch.ui.theme.ErrorTint
import com.packabunch.ui.theme.NumericFamily
import com.packabunch.ui.theme.Success
import com.packabunch.ui.theme.SuccessTint
import com.packabunch.ui.theme.SurfaceSunken
import com.packabunch.ui.theme.TextSecondary

/**
 * What a purchase did, shown over the Pack Plus page — `PurchasePending`, `PurchaseSuccess`,
 * `PurchaseCancelled`, `PurchaseFailed`, and offline.
 *
 * The rule that governs all of them: **entitlement follows verified state, never a local
 * "payment clicked" boolean.** Reaching [PurchaseOutcome.Succeeded] is not what unlocks
 * anything — RevenueCat's entitlement is. These only report what already happened.
 *
 * Pending is the one most often got wrong. A pending purchase is *not* a failure and *not* a
 * success: some payment methods take days, and the honest thing is to say so and let the
 * person carry on. It turns into "Pack Plus is on" by itself when the payment clears.
 */
enum class PurchaseOutcome { Succeeded, Pending, Cancelled, Failed, Offline }

/** A purchase's outcome and, when Google Play refused it, the code support can look up. */
data class PurchaseAttempt(val outcome: PurchaseOutcome, val reference: String? = null)

@Composable
fun PurchasePopup(
    attempt: PurchaseAttempt,
    /** "month", "week" or "year": what the plan just bought renews every. */
    renewsEvery: String,
    /** Got it, Back to my pack, Carry on without it — closes the pop-up. */
    onClose: () -> Unit,
    /** Try again: opens Google Play's payment sheet once more. */
    onTryAgain: () -> Unit,
    /** Check again: asks Google Play whether a pending payment has cleared. */
    onCheckAgain: () -> Unit,
    /** Back to my pack, once Plus is on. */
    onDone: () -> Unit,
    onSeeUnlocked: () -> Unit,
    onGetHelp: (reference: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val free = TierLimits.FREE
    when (attempt.outcome) {
        PurchaseOutcome.Pending -> PackPopup(
            icon = PackIcons.Clock, iconTint = Caution, iconBackground = CautionTint,
            title = "Google Play is still checking",
            body = "Your payment went through but hasn't been confirmed yet. This can take a few minutes, or longer with some payment methods.",
            detail = buildAnnotatedString {
                append("Nothing else is needed from you. Pack Plus switches on by itself the moment it clears — you don't have to pay again.")
            },
            primary = "Got it", onPrimary = onClose,
            secondary = "Check again", onSecondary = onCheckAgain,
            footnote = "Everything you've saved still works in the meantime.",
            onDismiss = onClose,
            modifier = modifier,
        )
        PurchaseOutcome.Succeeded -> PackPopup(
            icon = PackIcons.Check, iconTint = Success, iconBackground = SuccessTint,
            title = "Pack Plus is on",
            body = "Google Play confirmed it. Your saved-pack limit is gone and the item library is open.",
            detail = buildAnnotatedString {
                append("Renews ${renewsEvery}ly until you cancel, which you do in Google Play — Settings has a shortcut. Everything you had stays exactly where it was.")
            },
            primary = "Back to my pack", onPrimary = onDone,
            secondary = "See what's unlocked", onSecondary = onSeeUnlocked,
            onDismiss = onDone,
            modifier = modifier,
        )
        PurchaseOutcome.Cancelled -> PackPopup(
            icon = PackIcons.Close, iconTint = TextSecondary, iconBackground = SurfaceSunken,
            title = "Purchase cancelled",
            body = "You backed out of Google Play's payment screen, so nothing was charged and nothing changed.",
            primary = "Try again", onPrimary = onTryAgain,
            secondary = "Carry on without it", onSecondary = onClose,
            footnote = "Free keeps ${free.maxSavedPacks} pack, ${free.maxPiecesPerPack} pieces and the full packing guide.",
            onDismiss = onClose,
            modifier = modifier,
        )
        PurchaseOutcome.Failed -> PackPopup(
            icon = PackIcons.Warning, iconTint = ErrorRed, iconBackground = ErrorTint,
            title = "That purchase didn't go through",
            body = "Google Play turned the payment down. That is usually the card or the Play account rather than anything in this app.",
            detail = buildAnnotatedString {
                append("You have ")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("not") }
                append(" been charged. If it keeps happening, check your payment method in Google Play, or send us the code below.")
                attempt.reference?.let {
                    append("\n")
                    withStyle(SpanStyle(fontFamily = NumericFamily)) { append("ref $it") }
                }
            },
            primary = "Try again", onPrimary = onTryAgain,
            secondary = "Get help", onSecondary = { onGetHelp(attempt.reference) },
            onDismiss = onClose,
            modifier = modifier,
        )
        PurchaseOutcome.Offline -> PackPopup(
            icon = PackIcons.Offline, iconTint = Caution, iconBackground = CautionTint,
            title = "You're offline",
            body = "Subscribing needs a connection. Measuring, planning and the packing guide all work without one.",
            primary = "Try again", onPrimary = onTryAgain,
            secondary = "Carry on offline", onSecondary = onClose,
            onDismiss = onClose,
            modifier = modifier,
        )
    }
}
