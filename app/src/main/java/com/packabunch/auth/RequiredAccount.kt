package com.packabunch.auth

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.*
import androidx.compose.ui.platform.LocalContext
import com.packabunch.ui.screens.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import androidx.credentials.exceptions.GetCredentialCancellationException

/** Protected UI is not composed until authentication succeeds; navigation state cannot bypass it. */
@Composable
fun RequiredAccount(content: @Composable (SupabaseAccount, () -> Unit) -> Unit) {
    val context = LocalContext.current
    val onboarding = remember { context.getSharedPreferences("onboarding", android.content.Context.MODE_PRIVATE) }
    val account = remember { SupabaseAccount(context.applicationContext) }
    var authenticated by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var page by rememberSaveable { mutableStateOf(if (onboarding.getBoolean("complete", false)) "signIn" else "intro") }
    var unit by remember { mutableStateOf(com.packabunch.ui.format.LengthUnit.entries.firstOrNull {
        it.name == onboarding.getString("unit", null)
    } ?: com.packabunch.ui.format.LengthUnit.CENTIMETRES) }
    var habit by remember { mutableStateOf(PackingHabit.entries.firstOrNull { it.name == onboarding.getString("habit", null) }) }
    var message by remember { mutableStateOf<String?>(null) }
    var recoverySent by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        authenticated = account.restore()
        checking = false
    }
    fun runAuth(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try { action() }
            catch (_: GetCredentialCancellationException) { }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { message = if (e is java.io.IOException)
                "Couldn't connect. Check your connection and try again." else e.message ?: "Couldn't sign in." }
            finally { busy = false }
        }
    }
    fun google() = runAuth { account.signIn(context); authenticated = account.hasSession }
    if (checking) {
        AlertDialog(onDismissRequest = {}, title = { Text("Checking your session…") },
            confirmButton = {}, text = { CircularProgressIndicator() })
        return
    }
    if (authenticated) {
        key(account.userId) {
            // Copy the pre-login choices only once; never overwrite an existing account's settings.
            val prefs = context.getSharedPreferences("app_preferences_${account.userId}", android.content.Context.MODE_PRIVATE)
            if (!prefs.contains("unit")) prefs.edit().putString("unit", unit.name)
                .putString("habit", habit?.name).apply()
            content(account) {
                runAuth {
                    // Remove protected navigation immediately, then clear the persisted session.
                    authenticated = false
                    page = "signIn"
                    account.signOut(context)
                }
            }
        }
    } else {
        BackHandler(enabled = page != "signIn" && page != "intro") {
            if (!busy) page = if (page == "setup") "intro" else "signIn"
        }
        when (page) {
            "intro" -> OnboardingScreen(onFinished = { page = "setup" })
            "setup" -> OnbSetupScreen(unit = unit, habit = habit, onBack = { page = "intro" },
                onUnitChange = { unit = it }, onHabitChange = { habit = it },
                onContinue = {
                    onboarding.edit().putBoolean("complete", true).putString("unit", unit.name).putString("habit", habit?.name).apply()
                    page = "signIn"
                }, onSkip = {
                    onboarding.edit().putBoolean("complete", true).putString("unit", unit.name).putString("habit", habit?.name).apply()
                    page = "signIn"
                })
            "login" -> LogInScreen(localPackCount = 0,
                onLogIn = { email, password -> runAuth {
                    account.signInWithPassword(email, password); authenticated = account.hasSession
                } },
                onGoogle = ::google, onForgot = { recoverySent = false; page = "forgot" },
                onCreateAccount = { page = "signup" }, onBack = { page = "signIn" })
            "signup" -> CreateAccountScreen(
                onCreate = { email, password, _, _ -> runAuth {
                    authenticated = account.signUp(email, password)
                    if (!authenticated) {
                        page = "login"
                        message = "Check your email to confirm your account, then log in. If you already have an account, log in or reset your password."
                    }
                } }, onLogIn = { page = "login" }, onBack = { page = "signIn" })
            "forgot" -> ForgotPasswordScreen(
                sent = recoverySent,
                onSend = { email -> runAuth { account.sendRecovery(email); recoverySent = true } },
                onBack = { page = "login" })
            else -> SignInScreen(onContinueWithGoogle = ::google,
                onContinueWithEmail = { page = "signup" }, onLogIn = { page = "login" })
        }
    }
    if (busy) AlertDialog(onDismissRequest = {}, title = { Text("Connecting…") },
        confirmButton = {}, text = { CircularProgressIndicator() })
    message?.let { text -> AlertDialog(onDismissRequest = { message = null },
        title = { Text("Your account") }, text = { Text(text) },
        confirmButton = { TextButton(onClick = { message = null }) { Text("OK") } }) }
}

