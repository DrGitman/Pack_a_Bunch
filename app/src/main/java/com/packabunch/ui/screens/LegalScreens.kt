package com.packabunch.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.packabunch.ui.components.PackAppBar
import com.packabunch.ui.theme.Spacing
import com.packabunch.ui.theme.TextPrimary
import com.packabunch.ui.theme.TextSecondary
import com.packabunch.ui.theme.TextTertiary
import com.packabunch.ui.theme.UiFamily

/*
 * The terms and the privacy policy, written to be read rather than skipped.
 *
 * They live in the app so they work with no website and no connection. When the published
 * pages exist, set TERMS_URL and PRIVACY_URL in local.properties: the links then open those
 * instead, and Play gets the public URL it asks for.
 *
 * TODO before the first Play release, these three must be true, not placeholders:
 */
private const val PUBLISHER = "Pack a Bunch"
const val SUPPORT_EMAIL = "support@packabunch.site"
private const val CONTACT = SUPPORT_EMAIL
private const val COUNTRY = "Namibia"
private const val LAST_UPDATED = "30 September 2026"

private data class Section(val heading: String, val body: List<String>)

@Composable
fun PrivacyPolicyScreen(onBack: () -> Unit, modifier: Modifier = Modifier) =
    LegalPage("Privacy policy", privacySections, onBack, modifier)

@Composable
fun TermsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) =
    LegalPage("Terms of use", termsSections, onBack, modifier)

@Composable
private fun LegalPage(title: String, sections: List<Section>, onBack: () -> Unit, modifier: Modifier) {
    ArtboardPage(modifier) {
        PackAppBar(title = title, onBack = onBack)
        Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter)) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Last updated $LAST_UPDATED",
                color = TextTertiary,
                fontFamily = UiFamily,
                fontSize = 12.5.sp,
            )
            sections.forEach { section ->
                Spacer(Modifier.height(24.dp))
                Text(
                    text = section.heading,
                    color = TextPrimary,
                    fontFamily = UiFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    lineHeight = 24.sp,
                )
                section.body.forEach { paragraph ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = paragraph,
                        color = TextSecondary,
                        fontFamily = UiFamily,
                        fontSize = 15.sp,
                        lineHeight = 23.sp,
                    )
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

private val privacySections = listOf(
    Section(
        "The short version",
        listOf(
            "We ask for your email address so you can sign in. We store the packs you make so " +
                "you can open them on another phone. That is all.",
            "We do not show adverts. We do not track you. We do not sell anything about you to " +
                "anyone.",
        ),
    ),
    Section(
        "What we keep",
        listOf(
            "Your email address, so you can sign in and reset your password.",
            "Your packs, so they are on every phone you sign in on: the spaces you measure, the " +
                "items you add with their names, sizes and the shapes the scan measured, and the " +
                "plans the app works out for you. Item photos are not part of this; they stay on " +
                "the phone they were taken on.",
            "Your profile picture, if you add one.",
            "Your settings, such as whether you measure in centimetres or inches.",
        ),
    ),
    Section(
        "What never leaves your phone",
        listOf(
            "Your item and scan photos. A photo you take or pick to measure is measured on your " +
                "phone, and the item photos are saved there only. They are not backed up with " +
                "your packs, and the whole photo is never uploaded to us or to anyone else.",
            "Photos of spaces. When you measure a space, the app works out its size on your " +
                "phone and keeps only the measurements. No picture of the space is sent.",
            "Item recognition on the phone. The app's own suggestions for what something is are " +
                "worked out on your phone. The one exception is the online lookup below.",
            "\"It doesn't fit\" reports. When you tell us why a planned item didn't fit, we keep " +
                "the reason you picked and the sizes of the item and the space, so we can make plans " +
                "better. Never the item's name or anything you typed.",
        ),
    ),
    Section(
        "Looking items up online",
        listOf(
            "When you scan items, the app can look each one up to find its real size. It is on " +
                "unless you switch off \"Look up items online\" in Settings, and it only works " +
                "when you are signed in.",
            "For each item it found, the app sends a small cut-out of that item, never the whole " +
                "photo, with the name it guessed and the size it measured. Our server passes them " +
                "to Google's Gemini service, which says what the item is and how big it usually " +
                "is. We do not keep the cut-outs.",
            "Google handles them under the Gemini API terms, which may allow Google to use them " +
                "to improve its services.",
        ),
    ),
    Section(
        "Who else is involved",
        listOf(
            "Supabase stores your account and your packs for us. They hold the data on their " +
                "servers so that your packs are still there if you lose your phone.",
            "Google Play and RevenueCat handle payment if you buy Pack Plus. They tell us " +
                "only whether your subscription is active. We never see your card details.",
            "If you choose to sign in with Google, Google confirms who you are. We receive your " +
                "email address and nothing more.",
            "Google's Gemini service identifies items for the online lookup, as described above.",
        ),
    ),
    Section(
        "How long we keep it",
        listOf(
            "Your packs stay until you delete them or delete your account.",
            "You can delete every saved pack from Settings. You can delete your whole account " +
                "and everything in it from your profile. It is deleted 30 days after you ask, " +
                "with your packs on our side; signing in again before then stops it. After that " +
                "it cannot be undone.",
        ),
    ),
    Section(
        "Children",
        listOf(
            "This app is not aimed at children under 13, and we do not knowingly keep any " +
                "information about them. If you believe a child has made an account, write to " +
                "us and we will remove it.",
        ),
    ),
    Section(
        "Changes",
        listOf(
            "If we change what we collect, we will update this page and change the date at the " +
                "top. If the change is a big one, the app will tell you before it takes effect.",
        ),
    ),
    Section(
        "Contact",
        listOf(
            "Write to $CONTACT with any question about your information, or to ask for a copy " +
                "of it. $PUBLISHER publishes this app.",
        ),
    ),
)

private val termsSections = listOf(
    Section(
        "The short version",
        listOf(
            "Use the app to plan your packing. Measure carefully, and check the plan against the " +
                "real thing before you rely on it. Be fair to other people who use it.",
        ),
    ),
    Section(
        "Your account",
        listOf(
            "You need an account so your packs can follow you to another phone. Keep your " +
                "password to yourself, and tell us if somebody else gets into your account.",
            "You must be old enough to agree to these terms where you live. If you are under 18, " +
                "ask a parent first.",
        ),
    ),
    Section(
        "What the app can and cannot do",
        listOf(
            "The app works out an arrangement from the sizes you give it. Those sizes come from " +
                "a tape measure or from the camera, and the camera is an estimate.",
            "A plan is a good starting point, not a promise. Boxes bend, corners get in the way " +
                "and a fridge door is not where you think it is. Check before you commit, and " +
                "never use a plan as proof that something will fit in a van, a boot or a flight.",
            "We are not responsible for damage caused by packing something the way a plan " +
                "suggested. You are the one looking at the real objects.",
        ),
    ),
    Section(
        "Pack Plus",
        listOf(
            "The free plan keeps up to five saved packs, with up to 20 pieces each. Pack a Bunch " +
                "Pack Plus lifts those caps.",
            "If you subscribe, Google Play takes the payment and renews it each month until you " +
                "cancel. You can cancel at any time in Google Play, and you keep Pack Plus until the " +
                "month you paid for runs out.",
            "Refunds are handled by Google Play under their rules, not by us.",
            "If the price changes, Google Play will tell you before you are charged the new one.",
        ),
    ),
    Section(
        "Fair use",
        listOf(
            "Do not try to break the app, get into other people's accounts, or use it to do " +
                "anything against the law. We can close an account that does.",
        ),
    ),
    Section(
        "If something goes wrong",
        listOf(
            "We do our best to keep the app working and your packs safe, but we cannot promise " +
                "it will never be unavailable or never lose data. Keep your own note of anything " +
                "you cannot afford to lose.",
            "Where the law allows us to limit what we owe you, our responsibility is limited to " +
                "what you have paid us in the last twelve months.",
        ),
    ),
    Section(
        "Ending it",
        listOf(
            "You can stop using the app at any time and delete your account from your profile.",
            "We can close an account that breaks these terms. If we do, we will tell you why " +
                "where we are allowed to.",
        ),
    ),
    Section(
        "Changes and law",
        listOf(
            "If we change these terms, the date at the top changes, and the app tells you when " +
                "the change matters.",
            "These terms follow the law of $COUNTRY.",
            "Questions go to $CONTACT.",
        ),
    ),
)
