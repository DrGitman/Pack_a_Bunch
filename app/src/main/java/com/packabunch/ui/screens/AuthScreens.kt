package com.packabunch.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.packabunch.R
import com.packabunch.ui.components.*
import com.packabunch.ui.theme.*

/** Native, functional forms laid out from the four authentication HTML artboards. */
@Composable
internal fun ArtboardPage(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    ScreenScaffold(modifier.imePadding()) {
        BoxWithConstraints(Modifier.weight(1f)) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight).padding(bottom = 12.dp), content = content)
        }
    }
}

@Composable
private fun AuthText(text: String, size: Float = 15f, line: Int = 23,
    color: Color = TextSecondary, weight: FontWeight = FontWeight.Normal,
    modifier: Modifier = Modifier, centered: Boolean = false) {
    Text(text, modifier, color, fontSize = size.sp, fontFamily = UiFamily,
        fontWeight = weight, lineHeight = line.sp,
        textAlign = if (centered) TextAlign.Center else TextAlign.Start)
}

@Composable
private fun GoogleButton(onClick: () -> Unit) {
    val interactions = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val view = androidx.compose.ui.platform.LocalView.current
    Row(Modifier.fillMaxWidth().height(56.dp)
        .background(Surface, RoundedCornerShape(28.dp))
        .border(1.5.dp, OutlineStrong, RoundedCornerShape(28.dp))
        .clip(RoundedCornerShape(28.dp))
        .clickable(interactionSource = interactions, indication = ripple(color = Primary),
            role = androidx.compose.ui.semantics.Role.Button) {
            view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            onClick()
        }, horizontalArrangement = Arrangement.spacedBy(11.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(R.drawable.auth_google), null, Modifier.size(21.dp))
        AuthText("Continue with Google", 15.5f, color = TextPrimary, weight = FontWeight.Bold)
    }
}

@Composable
private fun TrustCard(title: String? = null, rows: List<String>, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().background(Surface, RoundedCornerShape(24.dp)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (title != null) AuthText(title, 14.5f, color = TextPrimary, weight = FontWeight.Bold)
        rows.forEach { text ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(PackIcons.Check, null, Modifier.padding(top = 2.dp).size(19.dp), tint = Success)
                AuthText(text, 14f, 21, BodyInk, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun SignInScreen(onContinueWithGoogle: () -> Unit, onContinueWithEmail: () -> Unit,
    onLogIn: () -> Unit, modifier: Modifier = Modifier) {
    ArtboardPage(modifier) {
        Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).background(Primary, RoundedCornerShape(11.dp)), contentAlignment = Alignment.Center) {
                Icon(PackIcons.Cube, null, Modifier.size(19.dp), tint = OnPrimary)
            }
            AuthText("Pack a Bunch", 15.5f, color = TextPrimary, weight = FontWeight.Bold)
        }
        Box(Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp).fillMaxWidth()
            .clip(RoundedCornerShape(30.dp)).background(Chrome)) {
            Image(painterResource(R.drawable.auth_parcels), null,
                Modifier.align(Alignment.BottomEnd).offset(x = 14.dp, y = 16.dp).size(150.dp, 130.dp), alpha = .75f)
            Column(Modifier.padding(horizontal = 22.dp, vertical = 24.dp)) {
                Text("GET STARTED", color = HeroEyebrow, fontFamily = UiFamily,
                    fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp)
                Spacer(Modifier.height(14.dp))
                Text("Your next pack,\nstarts here", Modifier.widthIn(max = 250.dp), color = Ground,
                    fontFamily = UiFamily, fontSize = 28.sp, lineHeight = 35.sp,
                    fontWeight = FontWeight.ExtraBold, letterSpacing = (-.8).sp)
                Spacer(Modifier.height(10.dp))
                AuthText("Sign up or log in to measure a space, add your items and plan your pack.",
                    14.5f, 21, HeroBody, modifier = Modifier.widthIn(max = 236.dp))
            }
        }
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            GoogleButton(onContinueWithGoogle)
            PrimaryButton("Sign up with email", onContinueWithEmail, icon = PackIcons.Mail)
        }
        TrustCard(rows = listOf("An email address is all we ask for. No phone number.",
            "Your packs and measurements stay on this phone for now.",
            "Sign in with Google or your email and password."),
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp))
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically) {
            AuthText("Already have an account?", 14.5f)
            PackTextButton("Log in", onLogIn)
        }
    }
}

@Composable
private fun AuthField(label: String, value: String, onChange: (String) -> Unit,
    password: Boolean = false, strength: Boolean = false) {
    var focused by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().background(Surface, RoundedCornerShape(20.dp))
        .border(1.5.dp, if (focused) Primary else Outline, RoundedCornerShape(20.dp))
        .padding(horizontal = 16.dp, vertical = 14.dp)) {
        Text(label.uppercase(), color = TextTertiary, fontFamily = UiFamily,
            fontSize = 11.5.sp, fontWeight = FontWeight.Bold, letterSpacing = .4.sp)
        Spacer(Modifier.height(5.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(value, onChange, Modifier.weight(1f).onFocusChanged { focused = it.isFocused },
                textStyle = TextStyle(color = TextPrimary, fontFamily = UiFamily, fontSize = 16.sp),
                singleLine = true, cursorBrush = SolidColor(Primary),
                visualTransformation = if (password && !visible) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else KeyboardType.Email),
                decorationBox = { inner -> Box { if (value.isEmpty()) AuthText(if (password) "Your password" else "you@example.com", 16f, color = TextTertiary); inner() } })
            if (password) {
                TextButton(onClick = { visible = !visible }, contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.height(24.dp)) { Text(if (visible) "Hide" else "Show", color = Primary, fontSize = 12.sp) }
            }
        }
        if (strength) {
            Spacer(Modifier.height(12.dp))
            val checks = listOf(value.length >= 8, value.length >= 12, value.any(Char::isDigit), value.any { !it.isLetterOrDigit() })
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                checks.forEach { met -> Box(Modifier.weight(1f).height(5.dp).background(if (met) Success else Outline, RoundedCornerShape(99.dp))) }
            }
            Spacer(Modifier.height(9.dp))
            AuthText(if (value.length >= 8) "Minimum length met. Longer is better." else "Use at least 8 characters.", 12.5f, 19,
                if (value.length >= 8) Success else TextSecondary)
        }
    }
}

@Composable
fun LogInScreen(localPackCount: Int, onLogIn: (String, String) -> Unit, onGoogle: () -> Unit,
    onForgot: () -> Unit, onCreateAccount: () -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    ArtboardPage(modifier) {
        PackAppBar("Log in", onBack)
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp)) {
            Text("Welcome back", color = TextPrimary, fontFamily = UiFamily, fontWeight = FontWeight.ExtraBold,
                fontSize = 29.sp, lineHeight = 36.sp, letterSpacing = (-.9).sp)
            Spacer(Modifier.height(10.dp))
            AuthText("Log in to continue with your packs on this phone.")
            Spacer(Modifier.height(24.dp))
            GoogleButton(onGoogle)
            Row(Modifier.padding(top = 22.dp, bottom = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically) {
                HorizontalDivider(Modifier.weight(1f), color = OutlineStrong)
                AuthText("or with email", 12.5f, color = TextTertiary, weight = FontWeight.SemiBold)
                HorizontalDivider(Modifier.weight(1f), color = OutlineStrong)
            }
            AuthField("Email", email, { email = it })
            Spacer(Modifier.height(12.dp))
            AuthField("Password", password, { password = it }, password = true)
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(PackIcons.Lock, null, Modifier.size(19.dp), tint = Primary)
                AuthText("  Secure sign-in", 13f, color = BodyInk, modifier = Modifier.weight(1f))
                PackTextButton("Forgot password?", onForgot)
            }
            Spacer(Modifier.height(12.dp))
            Note(text = "Your session is saved securely on this phone. Cloud backup and merging are not connected yet.", icon = PackIcons.Lock)
        }
        Spacer(Modifier.weight(1f))
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp)) {
            PrimaryButton("Log in", { onLogIn(email.trim(), password) }, enabled = email.contains('@') && password.isNotEmpty())
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically) {
                AuthText("New here?", 14.5f)
                PackTextButton("Create an account", onCreateAccount)
            }
        }
    }
}

@Composable
fun CreateAccountScreen(onCreate: (String, String, Boolean, Boolean) -> Unit, onLogIn: () -> Unit,
    onBack: () -> Unit, modifier: Modifier = Modifier) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    ArtboardPage(modifier) {
        PackAppBar("Create an account", onBack)
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp)) {
            AuthText("Only an email and a password. No phone number, no name required.")
            Spacer(Modifier.height(24.dp))
            AuthField("Email", email, { email = it })
            Spacer(Modifier.height(12.dp))
            AuthField("Password", password, { password = it }, password = true, strength = true)
            Spacer(Modifier.height(20.dp))
            TrustCard("What the account is for", listOf("Sign in securely with your email and password",
                "Keep packs separate for each account on this phone", "Cloud backup is not connected yet"))
            Spacer(Modifier.height(16.dp))
            AuthText("We may ask you to confirm your email before logging in.", 13.5f, 20)
        }
        Spacer(Modifier.weight(1f))
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp)) {
            PrimaryButton("Create account", { onCreate(email.trim(), password, false, false) },
                enabled = email.contains('@') && email.length > 3 && password.length >= 8)
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { PackTextButton("I already have an account", onLogIn) }
        }
    }
}

@Composable
fun ForgotPasswordScreen(sent: Boolean = false, onSend: (String) -> Unit, onBack: () -> Unit,
    modifier: Modifier = Modifier) {
    var email by remember { mutableStateOf("") }
    ArtboardPage(modifier) {
        PackAppBar("Reset password", onBack)
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(88.dp).background(ItemTints.first(), RoundedCornerShape(30.dp)), contentAlignment = Alignment.Center) {
                Icon(PackIcons.Mail, null, Modifier.size(42.dp), tint = Primary)
            }
            Spacer(Modifier.height(22.dp))
            AuthText(if (sent) "Check your email" else "We'll email you a link", 26f, 33, TextPrimary,
                FontWeight.ExtraBold, centered = true)
            Spacer(Modifier.height(11.dp))
            AuthText(if (sent) "If this address has an account, a reset link is on its way." else "Type the address you signed up with. We'll send you a reset link.", centered = true)
            Spacer(Modifier.height(26.dp))
            if (!sent) AuthField("Email", email, { email = it })
            Spacer(Modifier.height(20.dp))
            TrustCard("If nothing arrives", listOf("Check your spam folder",
                "If you signed up with Google, go back and use the Google button",
                "We show the same message whether or not the address has an account"))
        }
        Spacer(Modifier.weight(1f))
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp)) {
            if (!sent) PrimaryButton("Send the link", { onSend(email.trim()) }, enabled = email.contains('@'))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { PackTextButton("Back to log in", onBack) }
        }
    }
}

