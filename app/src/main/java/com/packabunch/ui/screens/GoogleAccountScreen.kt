package com.packabunch.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.packabunch.auth.SupabaseAccount
import com.packabunch.ui.components.*
import com.packabunch.ui.theme.Spacing

@Composable
fun GoogleAccountScreen(account: SupabaseAccount, onSignOut: () -> Unit, onBack: () -> Unit) {
    ScreenScaffold {
        PackAppBar(title = "Your account", onBack = onBack)
        Column(Modifier.padding(Spacing.gutter), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(account.email ?: "Signed in")
            Note(text = "Your packs are saved on this device. Cloud sync is not connected yet.", icon = PackIcons.Info)
            SecondaryButton(text = "Sign out", onClick = onSignOut)
        }
    }
}
