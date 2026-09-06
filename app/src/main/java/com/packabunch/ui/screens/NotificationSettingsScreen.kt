package com.packabunch.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.packabunch.ui.components.PackAppBar
import com.packabunch.ui.components.PackIcons
import com.packabunch.ui.components.SecondaryButton
import com.packabunch.ui.components.SectionHeading
import com.packabunch.ui.components.ScreenScaffold
import com.packabunch.ui.components.SwitchRow
import com.packabunch.ui.theme.CautionTint
import com.packabunch.ui.theme.Spacing
import com.packabunch.ui.theme.Success
import com.packabunch.ui.theme.SuccessTint
import com.packabunch.ui.theme.TextPrimary
import com.packabunch.ui.theme.TextTertiary
import com.packabunch.ui.theme.UiFamily

/** Every notification the app can send. Five of them, and this is all of them. */
data class NotificationPreferences(
    val halfFinishedPack: Boolean = true,
    val backupState: Boolean = true,
    val billing: Boolean = true,
    val newKindsOfSpace: Boolean = true,
    val askHowItWent: Boolean = true,
) {
    val allOff: Boolean
        get() = !halfFinishedPack && !backupState && !billing && !newKindsOfSpace && !askHowItWent
}

/**
 * Notification settings — `design/artboards/NotificationSettings.dc.html`.
 *
 * The line at the bottom is a promise the code has to keep: there is no marketing list and
 * no streak, and turning all five off really does mean the app never contacts you again.
 * Adding a sixth kind of notification means adding a row here — a notification that cannot
 * be switched off from this screen must not exist.
 */
@Composable
fun NotificationSettingsScreen(
    preferences: NotificationPreferences,
    systemNotificationsAllowed: Boolean,
    onPreferencesChange: (NotificationPreferences) -> Unit,
    onOpenSystemSettings: () -> Unit,
    onTurnEverythingOff: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ScreenScaffold(modifier) {
        PackAppBar(title = "Notification settings", onBack = onBack)

        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.gutter),
        ) {
            Spacer(Modifier.height(Spacing.md))

            // Android's own switch overrides everything here, so its state is shown rather
            // than left to be discovered when nothing arrives.
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(
                        if (systemNotificationsAllowed) SuccessTint else CautionTint,
                        RoundedCornerShape(18.dp),
                    )
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    if (systemNotificationsAllowed) PackIcons.Check else PackIcons.Warning,
                    contentDescription = null,
                    tint = if (systemNotificationsAllowed) Success else Color(0xFFB4761A),
                    modifier = Modifier.size(20.dp),
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        text = if (systemNotificationsAllowed) "Allowed by Android"
                        else "Blocked by Android",
                        color = TextPrimary,
                        fontFamily = UiFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.5f.sp,
                    )
                    Text(
                        text = if (systemNotificationsAllowed) {
                            "Turn everything off in system settings"
                        } else {
                            "Nothing below will arrive until you allow them"
                        },
                        color = TextTertiary,
                        fontFamily = UiFamily,
                        fontSize = 12.5f.sp,
                    )
                }
                com.packabunch.ui.components.PackTextButton(
                    text = "Open",
                    onClick = onOpenSystemSettings,
                )
            }

            Spacer(Modifier.height(Spacing.lg))
            SectionHeading("About your packs")
            Spacer(Modifier.height(10.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SwitchRow(
                    title = "Half-finished pack",
                    subtitle = "Once, the day after you stop",
                    checked = preferences.halfFinishedPack,
                    onCheckedChange = {
                        onPreferencesChange(preferences.copy(halfFinishedPack = it))
                    },
                )
                SwitchRow(
                    title = "Backup finished or failed",
                    subtitle = "Only when something needs your attention",
                    checked = preferences.backupState,
                    onCheckedChange = { onPreferencesChange(preferences.copy(backupState = it)) },
                )
                SwitchRow(
                    title = "Subscription and billing",
                    subtitle = "Renewals, failed payments, expiry",
                    checked = preferences.billing,
                    onCheckedChange = { onPreferencesChange(preferences.copy(billing = it)) },
                )
            }

            Spacer(Modifier.height(Spacing.lg))
            SectionHeading("About the app")
            Spacer(Modifier.height(10.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SwitchRow(
                    title = "New kinds of space",
                    subtitle = "A few times a year at most",
                    checked = preferences.newKindsOfSpace,
                    onCheckedChange = {
                        onPreferencesChange(preferences.copy(newKindsOfSpace = it))
                    },
                )
                SwitchRow(
                    title = "Ask me how a pack went",
                    subtitle = "Helps us check the model against real crates",
                    checked = preferences.askHowItWent,
                    onCheckedChange = { onPreferencesChange(preferences.copy(askHowItWent = it)) },
                )
            }

            Spacer(Modifier.height(Spacing.base))

            Text(
                text = "There is no marketing list and no daily streak. If you turn all of " +
                    "these off, you'll never hear from the app again.",
                color = TextTertiary,
                fontFamily = UiFamily,
                fontSize = 12.5f.sp,
                lineHeight = 19.sp,
            )

            Spacer(Modifier.height(Spacing.base))

            if (!preferences.allOff) {
                SecondaryButton(text = "Turn everything off", onClick = onTurnEverythingOff)
            }

            Spacer(Modifier.height(Spacing.xxl))
        }
    }
}
