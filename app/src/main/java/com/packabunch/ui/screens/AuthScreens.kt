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
import com.packabunch.ui.components.BrandTile
import com.packabunch.ui.components.LabelledTextField
import com.packabunch.ui.components.Note
import com.packabunch.ui.components.NoteTone
import com.packabunch.ui.components.PackAppBar
import com.packabunch.ui.components.PackIcons
import com.packabunch.ui.components.PackSwitch
import com.packabunch.ui.components.PackTextButton
import com.packabunch.ui.components.PrimaryButton
import com.packabunch.ui.components.ScreenScaffold
import com.packabunch.ui.components.SecondaryButton
import com.packabunch.ui.theme.Spacing
import com.packabunch.ui.theme.SurfaceField
import com.packabunch.ui.theme.TextPrimary
import com.packabunch.ui.theme.TextSecondary
import com.packabunch.ui.theme.TextTertiary
import com.packabunch.ui.theme.UiFamily

/**
 * Sign in, create account, reset password — `SignIn`, `LogIn`, `CreateAccount`,
 * `ForgotPassword`.
 *
 * **These are backed by a local-only stub.** Nothing leaves the device, no account exists
 * on any server, and no password is verified against anything. That is a deliberate,
 * temporary state so the screens can be built and reviewed — and it must be either finished
 * or removed before any Play upload, because a sign-in screen that stores a password
 * locally and calls it an account is worse than no sign-in screen at all.
 *
 * `docs/UX.md` and `docs/PLAN.md` still disagree on whether accounts should exist. Until
 * that is settled these screens are reachable but not required: nothing in the app blocks
 * on being signed in.
 */
@Composable
fun SignInScreen(
    onContinueWithGoogle: () -> Unit,
    onContinueWithEmail: () -> Unit,
    onLogIn: () -> Unit,
    onSkip: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    ScreenScaffold(modifier) {
        Spacer(Modifier.weight(1f))

        Column(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BrandTile(size = 72.dp, cornerRadius = 22.dp)
            Spacer(Modifier.height(Spacing.lg))
            Text(
                text = "Keep your packs",
                color = TextPrimary,
                fontFamily = UiFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 29.sp,
                letterSpacing = (-0.9).sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "So the crate you measured today is still there next time.",
                color = TextSecondary,
                fontFamily = UiFamily,
                fontSize = 15.sp,
                lineHeight = 23.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }

        Spacer(Modifier.weight(1f))

        Column(
            Modifier.padding(horizontal = Spacing.gutter),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // The local-only state is stated on the screen, not just in a comment. Nobody
            // should believe they have an account when they do not.
            Note(
                text = "Accounts aren't switched on yet. Everything stays on this phone " +
                    "for now.",
                tone = NoteTone.Caution,
                icon = PackIcons.Info,
            )

            SecondaryButton(
                text = "Continue with Google",
                onClick = onContinueWithGoogle,
                icon = PackIcons.Google,
            )
            SecondaryButton(
                text = "Continue with email",
                onClick = onContinueWithEmail,
                icon = PackIcons.Mail,
            )

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Already have an account?",
                    color = TextTertiary,
                    fontFamily = UiFamily,
                    fontSize = 13.sp,
                )
                PackTextButton(text = "Log in", onClick = onLogIn)
            }

            if (onSkip != null) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    PackTextButton(text = "Not now", onClick = onSkip, color = TextTertiary)
                }
            }
        }

        Spacer(Modifier.height(Spacing.base))
    }
}

@Composable
fun CreateAccountScreen(
    onCreate: (email: String, password: String, backup: Boolean, marketing: Boolean) -> Unit,
    onLogIn: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var backup by remember { mutableStateOf(true) }
    var marketing by remember { mutableStateOf(false) }

    val valid = email.contains('@') && email.length > 3 && password.length >= 8

    ScreenScaffold(modifier) {
        PackAppBar(title = "Create account", onBack = onBack)

        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.gutter),
        ) {
            Spacer(Modifier.height(Spacing.md))

            LabelledTextField(
                label = "Email",
                value = email,
                onValueChange = { email = it },
                placeholder = "you@example.com",
                focused = true,
            )
            Spacer(Modifier.height(10.dp))
            LabelledTextField(
                label = "Password",
                value = password,
                onValueChange = { password = it },
                placeholder = "At least 8 characters",
            )

            Spacer(Modifier.height(Spacing.base))

            CheckRow(
                title = "Back my packs up",
                detail = "Keeps them if you lose the phone.",
                checked = backup,
                onCheckedChange = { backup = it },
            )
            Spacer(Modifier.height(8.dp))
            // Off by default, and it stays off unless somebody actually chooses it.
            CheckRow(
                title = "Occasional emails about the app",
                detail = "A few times a year. Off unless you turn it on.",
                checked = marketing,
                onCheckedChange = { marketing = it },
            )

            Spacer(Modifier.height(Spacing.base))

            Note(
                text = "Accounts aren't switched on yet, so this is stored on this phone " +
                    "only. Nothing is sent anywhere.",
                tone = NoteTone.Caution,
                icon = PackIcons.Info,
            )

            Spacer(Modifier.height(Spacing.xl))
        }

        Column(Modifier.padding(horizontal = Spacing.gutter)) {
            PrimaryButton(
                text = "Create account",
                enabled = valid,
                onClick = { onCreate(email.trim(), password, backup, marketing) },
            )
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                PackTextButton(text = "I already have one", onClick = onLogIn)
            }
        }

        Spacer(Modifier.height(Spacing.sm))
    }
}

@Composable
fun LogInScreen(
    localPackCount: Int,
    onLogIn: (email: String, password: String) -> Unit,
    onGoogle: () -> Unit,
    onForgot: () -> Unit,
    onCreateAccount: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val valid = email.contains('@') && password.isNotEmpty()

    ScreenScaffold(modifier) {
        PackAppBar(title = "Log in", onBack = onBack)

        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.gutter),
        ) {
            Spacer(Modifier.height(Spacing.md))

            SecondaryButton(text = "Continue with Google", onClick = onGoogle, icon = PackIcons.Google)

            Spacer(Modifier.height(Spacing.base))

            LabelledTextField(
                label = "Email",
                value = email,
                onValueChange = { email = it },
                placeholder = "you@example.com",
                focused = true,
            )
            Spacer(Modifier.height(10.dp))
            LabelledTextField(
                label = "Password",
                value = password,
                onValueChange = { password = it },
            )

            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                PackTextButton(text = "Forgot password?", onClick = onForgot)
            }

            // The open question in the design handoff, surfaced rather than answered
            // silently. Merging somebody's local packs into an account without asking is
            // exactly the kind of quiet decision that loses data.
            if (localPackCount > 0) {
                Spacer(Modifier.height(Spacing.sm))
                Note(
                    title = "You have $localPackCount pack${if (localPackCount == 1) "" else "s"} on this phone",
                    text = "We'll ask what to do with them once you're in — nothing is " +
                        "merged or replaced without you choosing.",
                    icon = PackIcons.Info,
                )
            }

            Spacer(Modifier.height(Spacing.xl))
        }

        Column(Modifier.padding(horizontal = Spacing.gutter)) {
            PrimaryButton(
                text = "Log in",
                enabled = valid,
                onClick = { onLogIn(email.trim(), password) },
            )
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                PackTextButton(text = "Create an account", onClick = onCreateAccount)
            }
        }

        Spacer(Modifier.height(Spacing.sm))
    }
}

/**
 * Reset password — `ForgotPassword.dc.html`.
 *
 * The response is identical whether or not the address is registered. That is not a UX
 * nicety: a form that says "no account with that email" is an account-enumeration oracle,
 * and anybody can walk a list of addresses through it.
 */
@Composable
fun ForgotPasswordScreen(
    onSend: (email: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var email by remember { mutableStateOf("") }
    var sent by remember { mutableStateOf(false) }

    ScreenScaffold(modifier) {
        PackAppBar(title = "Reset password", onBack = onBack)

        Column(Modifier.padding(horizontal = Spacing.gutter)) {
            Spacer(Modifier.height(Spacing.md))

            if (sent) {
                Note(
                    title = "Check your email",
                    text = "If there's an account for ${email.trim()}, a reset link is on " +
                        "its way. It expires in an hour.",
                    tone = NoteTone.Confirmed,
                    icon = PackIcons.Mail,
                )
            } else {
                Text(
                    text = "Type the email you signed up with and we'll send a reset link.",
                    color = TextSecondary,
                    fontFamily = UiFamily,
                    fontSize = 15.sp,
                    lineHeight = 23.sp,
                )
                Spacer(Modifier.height(Spacing.base))
                LabelledTextField(
                    label = "Email",
                    value = email,
                    onValueChange = { email = it },
                    placeholder = "you@example.com",
                    focused = true,
                )
            }
        }

        Spacer(Modifier.weight(1f))

        Column(Modifier.padding(horizontal = Spacing.gutter)) {
            if (sent) {
                SecondaryButton(text = "Back to log in", onClick = onBack)
            } else {
                PrimaryButton(
                    text = "Send the link",
                    enabled = email.contains('@'),
                    onClick = { onSend(email.trim()); sent = true },
                )
            }
        }

        Spacer(Modifier.height(Spacing.base))
    }
}

@Composable
private fun CheckRow(
    title: String,
    detail: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(SurfaceField, RoundedCornerShape(18.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = TextPrimary,
                fontFamily = UiFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.5f.sp,
            )
            Text(
                text = detail,
                color = TextTertiary,
                fontFamily = UiFamily,
                fontSize = 12.5f.sp,
                lineHeight = 18.sp,
            )
        }
        PackSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
