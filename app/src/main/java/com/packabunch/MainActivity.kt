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
}
