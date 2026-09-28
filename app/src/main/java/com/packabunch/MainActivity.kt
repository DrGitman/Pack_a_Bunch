package com.packabunch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.packabunch.ui.AppViewModel
import com.packabunch.ui.nav.PackNavHost
import com.packabunch.ui.theme.Ground
import com.packabunch.ui.theme.PackABunchTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Opened from a password-reset email.
        if (savedInstanceState == null) com.packabunch.auth.RecoveryLink.offer(intent?.data)
        com.packabunch.notify.NotificationLink.offer(intent)
        askForNotifications()
        setContent {
            PackABunchTheme {
                // Once per app launch, not per screen rotation.
                var splashShown by rememberSaveable { mutableStateOf(false) }
                androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
                com.packabunch.auth.RequiredAccount { account, signOut ->
                    val appViewModel: AppViewModel = viewModel(
                        key = "packs-" + account.userId,
                        factory = AppViewModel.Factory(applicationContext, account.userId!!, account),
                    )
                    // Sync and its live connection only while the app is on screen.
                    androidx.lifecycle.compose.LifecycleStartEffect(appViewModel) {
                        appViewModel.onForeground()
                        onStopOrDispose { appViewModel.onBackground() }
                    }
                    PackNavHost(
                        viewModel = appViewModel,
                        account = account,
                        onSignOut = signOut,
                        modifier = Modifier.fillMaxSize().background(Ground),
                    )
                }
                // On top, so restoring the session and loading packs happen while it plays.
                if (!splashShown) com.packabunch.ui.screens.SplashScreen(onFinished = { splashShown = true })
                }
            }
        }
    }

    /** Already open when the reset link is tapped: the link arrives here instead. */
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        com.packabunch.auth.RecoveryLink.offer(intent.data)
        com.packabunch.notify.NotificationLink.offer(intent)
    }

    /**
     * Android 13 and later need a yes before any notice reaches the phone. Asked once; after a
     * no the app never nags, and the Notifications page keeps working either way.
     */
    private val notificationPermission =
        registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) {}

    private fun askForNotifications() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) return
        val prefs = getSharedPreferences("app_launch", MODE_PRIVATE)
        if (prefs.getBoolean("askedNotifications", false)) return
        if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED) return
        prefs.edit().putBoolean("askedNotifications", true).apply()
        notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }
}
