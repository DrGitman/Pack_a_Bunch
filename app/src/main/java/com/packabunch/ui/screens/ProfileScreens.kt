package com.packabunch.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
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
import com.packabunch.packing.Tier
import com.packabunch.ui.components.IconTile
import com.packabunch.ui.components.LabelledTextField
import com.packabunch.ui.components.Note
import com.packabunch.ui.components.NoteTone
import com.packabunch.ui.components.PackAppBar
import com.packabunch.ui.components.PackCard
import com.packabunch.ui.components.PackIcons
import com.packabunch.ui.components.PackTextButton
import com.packabunch.ui.components.PrimaryButton
import com.packabunch.ui.components.ScreenHeading
import com.packabunch.ui.components.ScreenScaffold
import com.packabunch.ui.components.SecondaryButton
import com.packabunch.ui.components.SectionHeading
import com.packabunch.ui.components.StatTile
import com.packabunch.ui.theme.BrandTint
import com.packabunch.ui.theme.ErrorRed
import com.packabunch.ui.theme.ErrorTint
import com.packabunch.ui.theme.Primary
import com.packabunch.ui.theme.Spacing
import com.packabunch.ui.theme.SurfaceField
import com.packabunch.ui.theme.TextPrimary
import com.packabunch.ui.theme.TextSecondary
import com.packabunch.ui.theme.TextTertiary
import com.packabunch.ui.theme.UiFamily

/**
 * Account — `design/artboards/Profile.dc.html`.
 *
 * Shows the plan, what is stored where, and the routes out: change email, change password,
 * sign out, delete. The backup line says what is actually true today rather than what the
 * design assumes, because "backed up" is a promise a person will rely on.
 */
@Composable
fun ProfileScreen(
    email: String?,
    tier: Tier,
    savedPackCount: Int,
    itemsMeasured: Int,
    backupEnabled: Boolean,
    onChangeEmail: () -> Unit,
    onChangePassword: () -> Unit,
    onManageSubscription: () -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    completedPacks: Int = 0,
    syncMessage: String? = null,
    syncRunning: Boolean = false,
    onSync: (() -> Unit)? = null,
) {
    ScreenScaffold(modifier) {
        PackAppBar(title = "Account", onBack = onBack)

        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.gutter),
        ) {
            Spacer(Modifier.height(Spacing.md))

            PackCard(shape = RoundedCornerShape(28.dp), elevation = 10.dp, contentPadding = 20.dp) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(15.dp)) {
                    Box(Modifier.size(68.dp).background(Primary, RoundedCornerShape(24.dp)), contentAlignment = Alignment.Center) {
                        Text(email?.firstOrNull()?.uppercase() ?: "P", color = com.packabunch.ui.theme.OnPrimary,
                            fontFamily = UiFamily, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-.5).sp)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(email?.substringBefore('@') ?: "Your account", color = TextPrimary, fontFamily = UiFamily,
                            fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, letterSpacing = (-.4).sp)
                        Text(email.orEmpty(), Modifier.padding(top = 3.dp), color = TextSecondary,
                            fontFamily = UiFamily, fontSize = 13.5.sp)
                        Spacer(Modifier.height(9.dp))
                        Row(Modifier.background(BrandTint, RoundedCornerShape(99.dp)).padding(horizontal = 11.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(PackIcons.Cube, null, Modifier.size(13.dp), tint = com.packabunch.ui.theme.PrimaryDark)
                            Text(if (tier == Tier.PLUS) "PACK PLUS" else "FREE PLAN", color = com.packabunch.ui.theme.PrimaryDark,
                                fontFamily = UiFamily, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = .5.sp)
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
                androidx.compose.material3.HorizontalDivider(color = com.packabunch.ui.theme.Divider)
                Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(savedPackCount to "packs", itemsMeasured to "things measured", completedPacks to "packs checked").forEach { (value, label) ->
                        Column(Modifier.weight(1f)) {
                            Text(value.toString(), color = TextPrimary, fontFamily = com.packabunch.ui.theme.NumericFamily, fontSize = 21.sp)
                            Text(label, Modifier.padding(top = 3.dp), color = TextSecondary, fontFamily = UiFamily,
                                fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, lineHeight = 16.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            // What is actually true, not what the design assumed. Somebody who believes
            // their packs are backed up and finds out otherwise has lost real work.
            PackCard(shape = RoundedCornerShape(22.dp), contentPadding = 16.dp) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                    IconTile(PackIcons.Info, tint = if (backupEnabled) com.packabunch.ui.theme.Success else TextSecondary,
                        background = if (backupEnabled) com.packabunch.ui.theme.SuccessTint else com.packabunch.ui.theme.SurfaceMuted,
                        size = 42.dp, iconSize = 21.dp, cornerRadius = 14.dp)
                    Column(Modifier.weight(1f)) {
                        Text(if (backupEnabled) "Backed up" else "On this phone only", color = TextPrimary,
                            fontFamily = UiFamily, fontSize = 15.sp, fontWeight = FontWeight.Bold, lineHeight = 20.sp)
                        Text(syncMessage ?: if (backupEnabled) "Your packs are copied to your account." else "Cloud sync is not connected yet.",
                            Modifier.padding(top = 2.dp), color = TextSecondary, fontFamily = UiFamily, fontSize = 12.5.sp, lineHeight = 18.sp)
                    }
                    Text(if (backupEnabled) "ON" else "OFF",
                        Modifier.background(com.packabunch.ui.theme.SurfaceMuted, RoundedCornerShape(99.dp)).padding(horizontal = 11.dp, vertical = 6.dp),
                        color = TextSecondary, fontFamily = UiFamily, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                }
            }
            if(onSync != null) PackTextButton(if(syncRunning) "Syncing…" else "Sync now", { if(!syncRunning) onSync() })
            Spacer(Modifier.height(Spacing.lg))
            SectionHeading("Account")
            Spacer(Modifier.height(10.dp))

            Column(Modifier.fillMaxWidth().background(com.packabunch.ui.theme.Surface, RoundedCornerShape(22.dp))
                .padding(horizontal = 16.dp, vertical = 6.dp)) {
                AccountRow(PackIcons.Mail, "Change email", onChangeEmail)
                androidx.compose.material3.HorizontalDivider(color = com.packabunch.ui.theme.Divider)
                AccountRow(PackIcons.Lock, "Change password", onChangePassword)
                androidx.compose.material3.HorizontalDivider(color = com.packabunch.ui.theme.Divider)
                AccountRow(PackIcons.Cube, "Manage Pack Plus", onManageSubscription)
            }

            Spacer(Modifier.height(Spacing.lg))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton(text = "Sign out", onClick = onSignOut)
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    PackTextButton(
                        text = "Delete my account",
                        onClick = onDeleteAccount,
                        color = ErrorRed,
                    )
                }
            }

            Spacer(Modifier.height(Spacing.xxl))
        }
    }
}

@Composable
private fun AccountRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = title,
            color = TextPrimary,
            fontFamily = UiFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            modifier = Modifier.weight(1f),
        )
        Icon(
            PackIcons.Forward,
            contentDescription = null,
            tint = Color(0xFFC3B0A0),
            modifier = Modifier.size(18.dp),
        )
    }
}

/**
 * Delete account — `design/artboards/AccountDelete.dc.html`.
 *
 * Two things this screen has to get right, both of which Google Play cares about and both
 * of which matter more than that:
 *
 *  - **It says what does *not* go.** A Play subscription is not cancelled by deleting an
 *    account, and somebody who assumes otherwise keeps being charged. It is named, with the
 *    route to actually cancel it.
 *  - **Typed confirmation, not a checkbox.** This is irreversible and takes every pack and
 *    every photo with it, so it asks for the email to be typed out.
 *
 * Note Play also requires a *web-reachable* deletion route. This screen does not satisfy
 * that on its own — that is still outstanding and belongs with the accounts decision.
 */
@Composable
fun AccountDeleteScreen(
    email: String,
    hasActiveSubscription: Boolean,
    onConfirmDelete: () -> Unit,
    onManageSubscription: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var typed by remember { mutableStateOf("") }
    val matches = typed.trim().equals(email.trim(), ignoreCase = true)

    ScreenScaffold(modifier) {
        PackAppBar(title = "Delete account", onBack = onBack)

        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.gutter),
        ) {
            Spacer(Modifier.height(Spacing.sm))

            IconTile(
                icon = PackIcons.Trash,
                tint = ErrorRed,
                background = ErrorTint,
                size = 56.dp,
                iconSize = 26.dp,
            )

            Spacer(Modifier.height(Spacing.base))
            ScreenHeading("This can't be undone")
            Spacer(Modifier.height(10.dp))

            Text(
                text = "Deleting your account removes it and everything in it.",
                color = TextSecondary,
                fontFamily = UiFamily,
                fontSize = 15.sp,
                lineHeight = 23.sp,
            )

            Spacer(Modifier.height(Spacing.base))

            WhatGoes(
                title = "What goes",
                items = listOf(
                    "Your account and sign-in",
                    "Every saved pack, on this phone and in your account",
                    "Every item photo",
                    "Everything you've measured",
                ),
                tone = ErrorTint,
                textColor = Color(0xFF8E3322),
            )

            Spacer(Modifier.height(10.dp))

            WhatGoes(
                title = "What doesn't",
                items = buildList {
                    if (hasActiveSubscription) {
                        add("Your Pack Plus subscription — cancel that in Google Play, or it keeps billing")
                    }
                    add("Anything you've already packed. Obviously.")
                },
                tone = SurfaceField,
                textColor = TextSecondary,
            )

            if (hasActiveSubscription) {
                Spacer(Modifier.height(10.dp))
                SecondaryButton(
                    text = "Cancel my subscription first",
                    onClick = onManageSubscription,
                )
            }

            Spacer(Modifier.height(Spacing.lg))

            Text(
                text = "Type $email to confirm",
                color = TextPrimary,
                fontFamily = UiFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(8.dp))
            LabelledTextField(
                label = "Email",
                value = typed,
                onValueChange = { typed = it },
                placeholder = email,
                focused = true,
            )

            Spacer(Modifier.height(Spacing.xl))
        }

        Column(
            Modifier.padding(horizontal = Spacing.gutter),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PrimaryButton(
                text = "Delete everything",
                enabled = matches,
                onClick = onConfirmDelete,
            )
            SecondaryButton(text = "Keep my account", onClick = onBack)
        }

        Spacer(Modifier.height(Spacing.sm))
    }
}

@Composable
private fun WhatGoes(
    title: String,
    items: List<String>,
    tone: Color,
    textColor: Color,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(tone, RoundedCornerShape(20.dp))
            .padding(14.dp),
    ) {
        Text(
            text = title.uppercase(),
            color = textColor,
            fontFamily = UiFamily,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 11.sp,
            letterSpacing = 0.7.sp,
        )
        Spacer(Modifier.height(8.dp))
        items.forEach { item ->
            Row(
                Modifier.padding(vertical = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("·", color = textColor, fontFamily = UiFamily, fontSize = 14.sp)
                Text(
                    text = item,
                    color = textColor,
                    fontFamily = UiFamily,
                    fontSize = 13.5f.sp,
                    lineHeight = 20.sp,
                )
            }
        }
    }
}
