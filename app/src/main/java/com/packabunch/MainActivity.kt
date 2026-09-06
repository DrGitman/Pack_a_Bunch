package com.packabunch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
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
                val appViewModel: AppViewModel = viewModel()
                PackNavHost(
                    viewModel = appViewModel,
                    modifier = Modifier.fillMaxSize().background(Ground),
                )
            }
        }
    }
}
