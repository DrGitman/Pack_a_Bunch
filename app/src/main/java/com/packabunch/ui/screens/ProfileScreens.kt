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
) {
    ScreenScaffold(modifier) {
        PackAppBar(title = "Account", onBack = onBack)

        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.gutter),
        ) {
            Spacer(Modifier.height(Spacing.md))

            PackCard(elevation = 8.dp, contentPadding = Spacing.base) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconTile(
                        icon = PackIcons.Person,
                        tint = Primary,
                        background = BrandTint,
                        size = 48.dp,
                        iconSize = 24.dp,
                    )
                    Spacer(Modifier.size(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = email ?: "Not signed in",
                            color = TextPrimary,
                            fontFamily = UiFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                        )
                        Text(
                            text = if (tier == Tier.PLUS) "Pack Plus" else "Free plan",
                            color = TextTertiary,
                            fontFamily = UiFamily,
                            fontSize = 12.5f.sp,
                        )
                    }
                }
            }

            Spacer(Modifier.height(Spacing.base))

            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                StatTile(
                    value = savedPackCount.toString(),
                    caption = if (savedPackCount == 1) "pack saved" else "packs saved",
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    value = itemsMeasured.toString(),
                    caption = "things measured",
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(Spacing.base))

            // What is actually true, not what the design assumed. Somebody who believes
            // their packs are backed up and finds out otherwise has lost real work.
            Note(
                title = if (backupEnabled) "Backed up" else "On this phone only",
                text = if (backupEnabled) {
                    "Your packs are copied to your account."
                } else {
                    "Backup isn't switched on yet. Everything lives on this phone, and " +
                        "uninstalling the app takes it with you."
                },
                tone = if (backupEnabled) NoteTone.Confirmed else NoteTone.Caution,
                icon = PackIcons.Info,
            )

            Spacer(Modifier.height(Spacing.lg))
            SectionHeading("Sign-in")
            Spacer(Modifier.height(10.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AccountRow(PackIcons.Mail, "Change email", onChangeEmail)
                AccountRow(PackIcons.Lock, "Change password", onChangePassword)
                AccountRow(PackIcons.Cube, "Manage subscription", onManageSubscription)
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
            .background(Color.White, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = Primary, modifier = Modifier.size(20.dp))
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
