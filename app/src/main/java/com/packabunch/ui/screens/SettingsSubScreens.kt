package com.packabunch.ui.screens

import androidx.annotation.RawRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.packabunch.R
import com.packabunch.packing.Tier
import com.packabunch.ui.components.LottieTapIcon
import com.packabunch.ui.components.PackAppBar
import com.packabunch.ui.components.PackCard
import com.packabunch.ui.components.PrimaryButton
import com.packabunch.ui.components.SecondaryButton
import com.packabunch.ui.motion.pressScale
import com.packabunch.ui.theme.BodyInk
import com.packabunch.ui.theme.Spacing
import com.packabunch.ui.theme.Surface
import com.packabunch.ui.theme.TextPrimary
import com.packabunch.ui.theme.TextSecondary
import com.packabunch.ui.theme.TextTertiary
import com.packabunch.ui.theme.UiFamily

/*
 * The Settings sub-pages.
 *
 * These are pages rather than dialogs for a reason beyond taste: the nav bar names where you are,
 * and a dialog floats over Settings, so the bar would still say "Settings" while something else
 * fills the screen. A page lets the bar rename itself to Info, Restore or Pack Plan, which is
 * both the animation you designed and an honest answer to "where am I?".
 */

/** One row in a settings group: an animated icon, a label, an optional value, and a chevron. */
@Composable
fun SettingsSubRow(
    @RawRes icon: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    value: String? = null,
) {
    // The row owns the gesture and lends it to both icons, so a press anywhere plays them.
    val press = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .pressScale(pressedScale = 0.98f, interactionSource = press)
            .clip(RoundedCornerShape(16.dp))
            .clickable(interactionSource = press, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        LottieTapIcon(icon, null, onClick = null, size = 20.dp, interactionSource = press)
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            color = TextPrimary,
            fontFamily = UiFamily,
            fontSize = 15.sp,
        )
        if (value != null) {
            Text(value, color = TextTertiary, fontFamily = UiFamily, fontSize = 13.sp)
        }
        LottieTapIcon(R.raw.icon_chevron, null, onClick = null, size = 16.dp, interactionSource = press)
    }
}

@Composable
private fun SubScreen(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    ArtboardPage(modifier) {
        PackAppBar(title = title, onBack = onBack)
        Column(Modifier.padding(horizontal = Spacing.gutter, vertical = 14.dp), content = content)
    }
}

@Composable
private fun GroupHeading(text: String) {
    Text(
        text = text.uppercase(),
        color = TextTertiary,
        fontFamily = UiFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 11.5.sp,
        letterSpacing = 0.8.sp,
        modifier = Modifier.padding(start = 4.dp, top = 18.dp, bottom = 8.dp),
    )
}

/** Privacy, terms and help. Was a dialog with three text buttons in it. */
@Composable
fun InfoScreen(
    onPrivacy: () -> Unit,
    onTerms: () -> Unit,
    onSupport: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    version: String = "",
) {
    SubScreen("Privacy, terms and help", onBack, modifier) {
        GroupHeading("What you agreed to")
        PackCard(contentPadding = 0.dp, shape = RoundedCornerShape(18.dp)) {
            Column {
                SettingsSubRow(R.raw.icon_info, "Privacy policy", onPrivacy)
                SettingsSubRow(R.raw.icon_info, "Terms of use", onTerms)
            }
        }

        GroupHeading("Help")
        PackCard(contentPadding = 0.dp, shape = RoundedCornerShape(18.dp)) {
            SettingsSubRow(R.raw.icon_info, "Get in touch", onSupport)
        }

        Spacer(Modifier.height(20.dp))
        Text(
            text = version,
            modifier = Modifier.fillMaxWidth(),
            color = TextTertiary,
            fontFamily = UiFamily,
            fontSize = 12.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

/** Restore purchases. The one screen in Settings that makes a network call, so it says so. */
@Composable
fun RestorePurchasesScreen(
    onRestore: ((Boolean) -> Unit) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var working by remember { mutableStateOf(false) }
    var outcome by remember { mutableStateOf<String?>(null) }

    SubScreen("Restore purchases", onBack, modifier) {
        PackCard(shape = RoundedCornerShape(18.dp)) {
            Column {
                Text(
                    text = "If you have bought Pack a Bunch Pro before, restoring brings it back " +
                        "on this phone. It uses the Google account signed in on this device.",
                    color = TextSecondary,
                    fontFamily = UiFamily,
                    fontSize = 14.5.sp,
                    lineHeight = 21.sp,
                )
                Spacer(Modifier.height(16.dp))
                PrimaryButton(
                    text = if (working) "Checking with Google Play…" else "Restore",
                    enabled = !working,
                    onClick = {
                        working = true
                        outcome = null
                        onRestore { restored ->
                            working = false
                            outcome = if (restored) "Pack a Bunch Pro is back on this phone."
                            else "There is nothing to restore on this Google account."
                        }
                    },
                )
            }
        }

        outcome?.let { message ->
            Spacer(Modifier.height(14.dp))
            PackCard(shape = RoundedCornerShape(18.dp)) {
                Text(message, color = BodyInk, fontFamily = UiFamily, fontSize = 14.5.sp, lineHeight = 21.sp)
            }
        }

        Spacer(Modifier.height(14.dp))
        Text(
            text = "Restoring brings back a subscription. It does not bring back packs you deleted.",
            color = TextTertiary,
            fontFamily = UiFamily,
            fontSize = 12.5.sp,
            lineHeight = 18.sp,
        )
    }
}

/** Pack Plan: what this account is on today, and where to change it. */
@Composable
fun PackPlanScreen(
    tier: Tier,
    price: String?,
    onUpgrade: () -> Unit,
    onManageInPlay: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pro = tier == Tier.PLUS
    SubScreen("Pack Plan", onBack, modifier) {
        PackCard(shape = RoundedCornerShape(18.dp)) {
            Column {
                Text(
                    text = if (pro) "Pack a Bunch Pro" else "Free plan",
                    color = TextPrimary,
                    fontFamily = UiFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (pro) "Billed through Google Play until you cancel."
                    else "One pack at a time, up to 20 pieces.",
                    color = TextSecondary,
                    fontFamily = UiFamily,
                    fontSize = 13.5.sp,
                    lineHeight = 20.sp,
                )
            }
        }

        GroupHeading("What Pro lifts")
        PackCard(contentPadding = 0.dp, shape = RoundedCornerShape(18.dp)) {
            Column {
                PlanLine("As many saved packs as you like", pro)
                PlanLine("Bigger packs, past twenty pieces", pro)
                PlanLine("Every item you measure, kept in your library", pro)
            }
        }

        Spacer(Modifier.height(18.dp))
        if (pro) {
            SecondaryButton(text = "Manage or cancel in Google Play", onClick = onManageInPlay)
        } else {
            PrimaryButton(
                text = price?.let { "Get Pack a Bunch Pro, $it" } ?: "See Pack a Bunch Pro",
                onClick = onUpgrade,
            )
        }
    }
}

@Composable
private fun PlanLine(text: String, included: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        androidx.compose.material3.Icon(
            imageVector = if (included) com.packabunch.ui.components.PackIcons.Check
            else com.packabunch.ui.components.PackIcons.Plus,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = if (included) com.packabunch.ui.theme.Success else TextTertiary,
        )
        Text(text, color = BodyInk, fontFamily = UiFamily, fontSize = 14.5.sp, lineHeight = 20.sp)
    }
}
