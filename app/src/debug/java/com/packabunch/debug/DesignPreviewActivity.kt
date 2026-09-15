package com.packabunch.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import com.packabunch.ui.screens.*
import com.packabunch.ui.format.LengthUnit
import com.packabunch.ui.theme.PackABunchTheme
import com.packabunch.data.ProjectRepository

/** UI-only fixtures. Does not construct an account, open a database, or bypass the production auth gate. */
class DesignPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val screen = intent.getStringExtra("screen") ?: "SignIn"
        setContent { PackABunchTheme {
            var unit by remember { mutableStateOf(LengthUnit.CENTIMETRES) }
            var habit by remember { mutableStateOf<PackingHabit?>(PackingHabit.MOVING_HOUSE) }
            when (screen) {
                "Onboarding" -> OnboardingScreen({ finish() })
                "OnbSetup" -> OnbSetupScreen(unit, habit, { unit = it }, { habit = it }, {}, {}, onBack = { finish() })
                "LogIn" -> LogInScreen(0, { _, _ -> }, {}, {}, {}, {})
                "CreateAccount" -> CreateAccountScreen({ _, _, _, _ -> }, {}, {})
                "ForgotPassword" -> ForgotPasswordScreen(onSend = {}, onBack = {})
                "Main" -> WelcomeScreen({}, {}, {})
                "Projects" -> ProjectsScreen(listOf(ProjectRepository.sampleProject()), unit, {}, {}, {})
                "ProjectsEmpty" -> ProjectsScreen(emptyList(), unit, {}, {}, {})
                else -> SignInScreen({}, {}, {})
            }
        } }
    }
}
