package com.packabunch.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.packabunch.billing.PlanKind
import com.packabunch.billing.PlanOffer
import com.packabunch.packing.TierLimits
import com.packabunch.ui.components.Note
import com.packabunch.ui.components.NoteTone
import com.packabunch.ui.components.PackIcons
import com.packabunch.ui.components.PackTextButton
import com.packabunch.ui.components.PrimaryButton
import com.packabunch.ui.components.SelectableChip
import com.packabunch.ui.motion.Motion
import com.packabunch.ui.theme.BrandTint
import com.packabunch.ui.theme.Chrome
import com.packabunch.ui.theme.HeroBody
import com.packabunch.ui.theme.HeroEyebrow
import com.packabunch.ui.theme.NumeralLarge
import com.packabunch.ui.theme.Primary
import com.packabunch.ui.theme.PrimaryDark
import com.packabunch.ui.theme.Spacing
import com.packabunch.ui.theme.TextPrimary
import com.packabunch.ui.theme.TextSecondary
import com.packabunch.ui.theme.TextTertiary
import com.packabunch.ui.theme.UiFamily

/**
 * Pack Plus — the `Pack Plus` frame: a dark card that says what it is, three things it adds,
 * the price with its renewal, and one button.
 *
 * Rules this screen follows, all of which are easy to break and expensive to break:
 *
 *  - **Shown only after a result.** Never on launch, never before a plan has been delivered.
 *  - **The price, the period and the renewal terms are together**, in one card, so the period
 *    cannot be missed.
 *  - **Nothing is sold that does not exist yet.** Each line maps to a real field on [TierLimits].
 *  - **Every price comes from Google Play.** Play charges each country its own price, in its
 *    own currency, from the one base price set in the Play Console — so someone in Namibia
 *    sees N$ and someone in Germany sees €, and both are the same amount once converted. The
 *    app never works out where anyone is: the Play account already knows, and the price it
 *    returns is the price charged. A hardcoded price would be wrong for almost everybody, so
 *    when billing has not loaded the button is disabled rather than showing a guess.
 *  - **Each plan says how often it renews and what a trial turns into**, in the price card
 *    beside the button. A cheap weekly plan earns its keep only if nobody feels tricked by the
 *    second charge.
 */
@Composable
fun UpgradeScreen(
    plans: List<PlanOffer>,
    selected: Int,
    onSelect: (Int) -> Unit,
    onSubscribe: () -> Unit,
    onRestore: () -> Unit,
    /** "Have a promo code?" — for judges and testers; Pack Plus without a Play purchase. */
    onHaveCode: () -> Unit = {},
    onTerms: () -> Unit,
    onPrivacy: () -> Unit,
    onCompare: () -> Unit = {},
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** Pack Plus is already on: the page manages it instead of selling it. */
    plusOn: Boolean = false,
    /** When a promo code's Pack Plus runs out; null for a Google Play subscription. */
    plusUntil: java.time.Instant? = null,
    onManageInPlay: () -> Unit = {},
    /** Ask Google Play for the plans again, after they failed to load. */
    onRetry: () -> Unit = {},
) {
    ArtboardPage(modifier) {
        com.packabunch.ui.components.PackAppBar(
            title = "Pack Plus",
            onBack = onBack,
            modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
        )
        Column(
            Modifier
                .padding(horizontal = Spacing.gutter)
                .padding(top = 6.dp),
        ) {
            Hero()
            Spacer(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val saved = TierLimits.FREE.maxSavedPacks
                Benefit(PackIcons.Projects, "As many saved packs as you like", "Free keeps ${if (saved == 1) "one" else saved.toString()}.")
                Benefit(PackIcons.Copy, "Reuse items across packs", "Measure a thing once, use it anywhere.")
                Benefit(PackIcons.Cube, "New kinds of space as they land", "Only once they're tested and shipped.")
            }

            Spacer(Modifier.height(14.dp))
            if (plusOn) {
                PlusOnCard(plusUntil)
            } else {
                // Weekly and monthly (and yearly, if one is set up), always shown as the choice
                // they are — before Google Play has answered too, so the page never looks bare.
                val kinds = plans.map { it.kind }.ifEmpty { listOf(PlanKind.WEEKLY, PlanKind.MONTHLY) }
                var fallbackKind by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(PlanKind.MONTHLY) }
                val selectedKind = plans.getOrNull(selected)?.kind ?: fallbackKind
                PlanToggle(kinds, selectedKind) { kind ->
                    val index = plans.indexOfFirst { it.kind == kind }
                    if (index >= 0) onSelect(index) else fallbackKind = kind
                }
                Spacer(Modifier.height(10.dp))
                val chosen = plans.getOrNull(selected)
                if (chosen != null) PriceCard(chosen) else PricesUnavailable(onRetry)
            }
        }

        PushDown()

        Column(Modifier.padding(horizontal = Spacing.gutter)) {
            when {
                plusOn && plusUntil == null -> PrimaryButton(text = "Manage in Google Play", onClick = onManageInPlay)
                plusOn -> Unit
                else -> PrimaryButton(
                    text = if (chosen(plans, selected)?.trial != null) "Start free trial" else "Subscribe",
                    onClick = onSubscribe,
                    enabled = chosen(plans, selected) != null,
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = if (plusOn) "Cancel any time; your packs stay either way. Free keeps " +
                    "${TierLimits.FREE.maxSavedPacks} pack, up to ${TierLimits.FREE.maxPiecesPerPack} pieces."
                else "Free keeps ${TierLimits.FREE.maxSavedPacks} pack, up to " +
                    "${TierLimits.FREE.maxPiecesPerPack} pieces, and the whole packing guide.",
                color = TextTertiary,
                fontFamily = UiFamily,
                fontSize = 12.5f.sp,
                lineHeight = 18.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            )
            // Two short rows rather than one long one: on a narrow phone "Privacy" used to
            // break across two lines.
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PackTextButton(text = "Restore purchases", onClick = onRestore, color = Primary)
                if (!plusOn) {
                    Text("·", color = TextTertiary, fontFamily = UiFamily)
                    PackTextButton(text = "Have a promo code?", onClick = onHaveCode, color = Primary)
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PackTextButton(text = "Terms", onClick = onTerms, color = TextSecondary)
                Text("·", color = TextTertiary, fontFamily = UiFamily)
                PackTextButton(text = "Privacy", onClick = onPrivacy, color = TextSecondary)
            }
        }
    }
}

private fun chosen(plans: List<PlanOffer>, selected: Int) = plans.getOrNull(selected)

/** The dark card at the top: the name, the promise, and the crate with one thing packed. */
@Composable
private fun Hero() {
    Box(
        Modifier
            .fillMaxWidth()
            .background(Brush.linearGradient(listOf(Color(0xFF4A2E1F), Chrome)), RoundedCornerShape(24.dp))
            .padding(start = 18.dp, end = 12.dp, top = 16.dp, bottom = 18.dp),
    ) {
        Column(Modifier.padding(end = 92.dp)) {
            Row(
                Modifier
                    .background(Color(0x33E8A76B), RoundedCornerShape(999.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Canvas(Modifier.size(11.dp)) {
                    // The little hexagon mark beside the name.
                    val r = size.minDimension / 2; val c = Offset(size.width / 2, size.height / 2)
                    val hex = Path().apply {
                        for (i in 0..5) {
                            val a = Math.toRadians(60.0 * i - 90).toFloat()
                            val p = Offset(c.x + r * kotlin.math.cos(a), c.y + r * kotlin.math.sin(a))
                            if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
                        }
                        close()
                    }
                    drawPath(hex, HeroEyebrow, style = Stroke(1.4.dp.toPx()))
                }
                Text("PACK PLUS", color = HeroEyebrow, fontFamily = UiFamily, fontWeight = FontWeight.ExtraBold, fontSize = 10.5f.sp, letterSpacing = 0.8.sp)
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Keep every pack you plan",
                color = Color.White,
                fontFamily = UiFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 27.sp,
                lineHeight = 31.sp,
                letterSpacing = (-0.8).sp,
            )
            Spacer(Modifier.height(8.dp))
            Text("For people who pack more than once.", color = HeroBody, fontFamily = UiFamily, fontSize = 14.sp, lineHeight = 19.sp)
        }
        HeroCrate(Modifier.align(Alignment.BottomEnd).size(width = 88.dp, height = 64.dp))
    }
}

/** An open crate seen from above at an angle, with one orange box in it. */
@Composable
private fun HeroCrate(modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width; val h = size.height
        fun p(x: Float, y: Float) = Offset(w * x, h * y)
        fun quad(a: Offset, b: Offset, c: Offset, d: Offset) = Path().apply { moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c.x, c.y); lineTo(d.x, d.y); close() }
        // Crate: rim, then the two outer walls.
        val rimBack = p(0.5f, 0.12f); val rimRight = p(0.98f, 0.36f); val rimFront = p(0.5f, 0.6f); val rimLeft = p(0.02f, 0.36f)
        drawPath(quad(rimBack, rimRight, rimFront, rimLeft), Color(0xFF5A3825))
        drawPath(quad(rimLeft, rimFront, p(0.5f, 0.98f), p(0.02f, 0.74f)), Color(0xFF3A2317))
        drawPath(quad(rimFront, rimRight, p(0.98f, 0.74f), p(0.5f, 0.98f)), Color(0xFF301D12))
        drawPath(quad(rimBack, rimRight, rimFront, rimLeft), Color(0x33FFFFFF), style = Stroke(1.dp.toPx()))
        // The packed box, standing in the crate.
        val t0 = p(0.34f, 0.16f); val t1 = p(0.58f, 0.28f); val t2 = p(0.42f, 0.4f); val t3 = p(0.18f, 0.28f)
        drawPath(quad(t0, t1, t2, t3), Color(0xFFF0A160))
        drawPath(quad(t3, t2, p(0.42f, 0.58f), p(0.18f, 0.46f)), Color(0xFFD9803E))
        drawPath(quad(t2, t1, p(0.58f, 0.46f), p(0.42f, 0.58f)), Color(0xFFB9652C))
    }
}

@Composable
private fun Benefit(icon: ImageVector, title: String, detail: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(18.dp))
            .padding(horizontal = 12.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(34.dp).background(BrandTint, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = Primary, modifier = Modifier.size(17.dp))
        }
        Column {
            Text(title, color = TextPrimary, fontFamily = UiFamily, fontWeight = FontWeight.Bold, fontSize = 14.5f.sp)
            Text(detail, color = TextTertiary, fontFamily = UiFamily, fontSize = 12.5f.sp, lineHeight = 17.sp)
        }
    }
}

/**
 * The chosen plan's whole deal in one place: Google Play's price as given, how often it is
 * charged, what a trial turns into, and where to cancel.
 */
@Composable
private fun PriceCard(plan: PlanOffer) {
    AnimatedContent(
        targetState = plan,
        transitionSpec = { fadeIn(tween(Motion.SHORT_MS)) togetherWith fadeOut(tween(Motion.SHORT_MS)) },
        label = "planPrice",
    ) { p ->
        Column(
            Modifier
                .fillMaxWidth()
                .background(Color.White, RoundedCornerShape(20.dp))
                .border(1.5.dp, Primary, RoundedCornerShape(20.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(p.price, style = NumeralLarge.copy(fontSize = 26.sp, fontWeight = FontWeight.ExtraBold), color = TextPrimary)
                Spacer(Modifier.size(6.dp))
                Text(p.kind.per, color = TextSecondary, fontFamily = UiFamily, fontSize = 13.sp, modifier = Modifier.padding(bottom = 4.dp))
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = listOfNotNull(
                    p.trial?.let { "$it." },
                    p.renewal,
                    "Billed through Google Play; cancel there any time" + if (p.trial != null) ", and before the trial ends to pay nothing." else ".",
                    "Price shown in your local currency at checkout.",
                ).joinToString(" "),
                color = TextSecondary,
                fontFamily = UiFamily,
                fontSize = 12.5f.sp,
                lineHeight = 18.sp,
            )
            p.perMonth?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, color = PrimaryDark, fontFamily = UiFamily, fontWeight = FontWeight.SemiBold, fontSize = 12.5f.sp)
            }
        }
    }
}

/** Weekly | Monthly (| Yearly): one pill, the chosen half filled. */
@Composable
private fun PlanToggle(kinds: List<PlanKind>, selected: PlanKind, onPick: (PlanKind) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color(0xFFF1E4D6), RoundedCornerShape(999.dp))
            .padding(4.dp),
    ) {
        kinds.forEach { kind ->
            val on = kind == selected
            Box(
                Modifier
                    .weight(1f)
                    .height(40.dp)
                    .background(if (on) Primary else Color.Transparent, RoundedCornerShape(999.dp))
                    .clip(RoundedCornerShape(999.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Tab,
                    ) { onPick(kind) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    kind.title,
                    color = if (on) Color.White else TextSecondary,
                    fontFamily = UiFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                )
            }
        }
    }
}

/** Google Play hasn't answered: say where prices come from and offer to ask again. */
@Composable
private fun PricesUnavailable(onRetry: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(PackIcons.Warning, contentDescription = null, tint = com.packabunch.ui.theme.Caution, modifier = Modifier.size(16.dp))
            Text("Prices couldn't load", color = TextPrimary, fontFamily = UiFamily, fontWeight = FontWeight.Bold, fontSize = 14.5f.sp)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Google Play sets the price for your country, in your currency. It couldn't be reached just now. Nothing has been charged.",
            color = TextSecondary, fontFamily = UiFamily, fontSize = 12.5f.sp, lineHeight = 18.sp,
        )
        PackTextButton(text = "Try again", onClick = onRetry, color = Primary)
    }
}

/** Pack Plus is on: where it comes from, and until when if it runs out by itself. */
@Composable
private fun PlusOnCard(until: java.time.Instant?) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(20.dp))
            .border(1.5.dp, Primary, RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(34.dp).background(com.packabunch.ui.theme.SuccessTint, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
            Icon(PackIcons.Check, contentDescription = null, tint = com.packabunch.ui.theme.Success, modifier = Modifier.size(17.dp))
        }
        Column {
            Text("Pack Plus is on", color = TextPrimary, fontFamily = UiFamily, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            Text(
                if (until != null) "From a promo code, until " +
                    java.time.format.DateTimeFormatter.ofPattern("d MMMM yyyy").format(until.atZone(java.time.ZoneId.systemDefault())) + "."
                else "Billed through Google Play. Change or cancel it there.",
                color = TextSecondary, fontFamily = UiFamily, fontSize = 12.5f.sp, lineHeight = 18.sp,
            )
        }
    }
}
