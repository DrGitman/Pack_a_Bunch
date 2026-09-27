package com.packabunch.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.packabunch.packing.TierLimits
import com.packabunch.ui.components.Note
import com.packabunch.ui.components.NoteTone
import com.packabunch.ui.components.PackAppBar
import com.packabunch.ui.components.PackIcons
import com.packabunch.ui.components.PackTextButton
import com.packabunch.ui.components.PrimaryButton
import com.packabunch.ui.components.ScreenScaffold
import com.packabunch.ui.theme.BrandTint
import com.packabunch.ui.theme.NumeralLarge
import com.packabunch.ui.theme.Primary
import com.packabunch.ui.theme.Spacing
import com.packabunch.ui.theme.TextPrimary
import com.packabunch.ui.theme.TextSecondary
import com.packabunch.ui.theme.TextTertiary
import com.packabunch.ui.theme.UiFamily

/**
 * Pack a Bunch Pro — `design/artboards/Upgrade.dc.html`.
 *
 * Rules this screen follows, all of which are easy to break and expensive to break:
 *
 *  - **Shown only after a result.** Never on launch, never before a plan has been delivered.
 *  - **The price, the period and the renewal terms are together**, not split up so the
 *    period is easy to miss.
 *  - **Nothing is sold that does not exist yet.** No cloud sync, no "AI optimisation", no
 *    unlimited precision. Each line here maps to a real field on [TierLimits].
 *  - **Every price comes from Google Play**, localised, and this screen renders whatever it
 *    is given. A hardcoded price is wrong for almost everybody who sees it, so when billing
 *    has not loaded the purchase button is disabled rather than showing a guess.
 *  - **Each plan says how often it renews and what a trial turns into**, on the plan itself
 *    and again beside the button. A cheap weekly plan earns its keep only if nobody feels
 *    tricked by the second charge.
 */
@Composable
fun UpgradeScreen(
    plans: List<com.packabunch.billing.PlanOffer>,
    selected: Int,
    onSelect: (Int) -> Unit,
    onSubscribe: () -> Unit,
    onRestore: () -> Unit,
    onTerms: () -> Unit,
    onPrivacy: () -> Unit,
    onCompare: () -> Unit = {},
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ArtboardPage(modifier) {
        PackAppBar(title = "", onBack = onBack)

        Column(Modifier.padding(horizontal = Spacing.gutter)) {
            Text(
                text = "PACK-A-BUNCH PRO",
                color = Primary,
                fontFamily = UiFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 12.5f.sp,
                letterSpacing = 1.2.sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Keep every pack you plan",
                color = TextPrimary,
                fontFamily = UiFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 29.sp,
                lineHeight = 36.sp,
                letterSpacing = (-0.9).sp,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "For people who pack more than once.",
                color = TextSecondary,
                fontFamily = UiFamily,
                fontSize = 15.sp,
            )
        }

        Spacer(Modifier.height(Spacing.xl))

        Column(
            Modifier.padding(horizontal = Spacing.gutter),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Benefit(
                icon = PackIcons.Projects,
                title = "As many saved packs as you like",
                detail = "Free keeps ${TierLimits.FREE.maxSavedPacks}.",
            )
            Benefit(
                icon = PackIcons.Cube,
                title = "As many pieces as the planner can search",
                detail = "Free stops at ${TierLimits.FREE.maxPiecesPerPack} pieces a pack.",
            )
            Benefit(
                icon = PackIcons.Camera,
                title = "Scan a space of any size",
                detail = "Free scans up to ${TierLimits.FREE.maxScannedSpaceLitres} litres " +
                    "a crate, but not a car boot.",
            )
            Benefit(
                icon = PackIcons.Rotate,
                title = "Scan as often as you need",
                detail = "Free allows ${TierLimits.FREE.maxScansPerDay} scans a day.",
            )
            Benefit(
                icon = PackIcons.Library,
                title = "Reuse items across packs",
                detail = "Measure a thing once, use it anywhere.",
            )
        }

        PushDown()

        Column(Modifier.padding(horizontal = Spacing.gutter)) {
            val chosen = plans.getOrNull(selected)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                plans.forEachIndexed { index, plan ->
                    PlanCard(plan = plan, selected = index == selected, onClick = { onSelect(index) })
                }
            }
            Spacer(Modifier.height(12.dp))

            val purchaseEnabled = chosen != null
            if (!purchaseEnabled) {
                // Products unavailable means no purchase, not a fake success. A beta that
                // cannot sell is fine; one that pretends to have sold is not.
                Note(
                    text = "Subscriptions aren't available right now. Nothing has been charged.",
                    tone = NoteTone.Caution,
                    icon = PackIcons.Warning,
                )
                Spacer(Modifier.height(10.dp))
            }

            PrimaryButton(
                text = if (chosen?.trial != null) "Start free trial" else "Subscribe",
                onClick = onSubscribe,
                enabled = purchaseEnabled,
            )

            Spacer(Modifier.height(8.dp))

            // The selected plan's whole deal, next to the button that commits to it.
            if (chosen != null) {
                Text(
                    text = listOfNotNull(
                        chosen.trial?.let { "$it." },
                        chosen.renewal,
                        "Billed through Google Play; cancel there any time" +
                            if (chosen.trial != null) ", and before the trial ends to pay nothing." else ".",
                    ).joinToString(" "),
                    color = Color(0xFF7C4223),
                    fontFamily = UiFamily,
                    fontSize = 12.5f.sp,
                    lineHeight = 18.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
            }

            Text(
                text = "Free keeps ${TierLimits.FREE.maxSavedPacks} pack, up to " +
                    "${TierLimits.FREE.maxPiecesPerPack} pieces, and the whole packing guide.",
                color = TextTertiary,
                fontFamily = UiFamily,
                fontSize = 12.5f.sp,
                lineHeight = 18.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                PackTextButton(text = "Compare the two plans", onClick = onCompare)
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PackTextButton(text = "Restore purchases", onClick = onRestore)
                Text("·", color = TextTertiary, fontFamily = UiFamily)
                PackTextButton(text = "Terms", onClick = onTerms)
                Text("·", color = TextTertiary, fontFamily = UiFamily)
                PackTextButton(text = "Privacy", onClick = onPrivacy)
            }
        }

        Spacer(Modifier.height(Spacing.sm))
    }
}

/**
 * One plan to choose. The price is the plan's own; the per-month line is only there so a
 * weekly and a monthly plan can be compared, never in place of the price actually charged.
 */
@Composable
private fun PlanCard(plan: com.packabunch.billing.PlanOffer, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(if (selected) BrandTint else Color.White, RoundedCornerShape(18.dp))
            .border(
                if (selected) 2.dp else 1.dp,
                if (selected) Primary else Color(0xFFE6D9CB),
                RoundedCornerShape(18.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = plan.kind.title,
                color = TextPrimary,
                fontFamily = UiFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                modifier = Modifier.weight(1f),
            )
            Text(text = plan.price, style = NumeralLarge.copy(fontSize = 19.sp), color = TextPrimary)
            Spacer(Modifier.size(5.dp))
            Text(text = plan.kind.per, color = TextSecondary, fontFamily = UiFamily, fontSize = 13.sp)
        }
        val details = listOfNotNull(plan.trial, plan.perMonth, plan.renewal)
        Text(
            text = details.joinToString(" · "),
            color = TextTertiary,
            fontFamily = UiFamily,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}

@Composable
private fun Benefit(icon: ImageVector, title: String, detail: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(13.dp)) {
        Box(
            Modifier
                .size(40.dp)
                .background(BrandTint, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Primary, modifier = Modifier.size(20.dp))
        }
        Column {
            Text(
                text = title,
                color = TextPrimary,
                fontFamily = UiFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
            )
            Text(
                text = detail,
                color = TextTertiary,
                fontFamily = UiFamily,
                fontSize = 12.5f.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
