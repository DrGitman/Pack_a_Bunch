package com.packabunch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.packabunch.ui.theme.Ground
import com.packabunch.ui.theme.PackABunchTheme
import com.packabunch.ui.theme.Spacing
import com.packabunch.ui.theme.TextPrimary
import com.packabunch.ui.theme.TextSecondary

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            PackABunchTheme {
                Placeholder()
            }
        }
    }
}

/**
 * Stands in until the NavHost lands. Kept honest on purpose: it does not pretend to be
 * a welcome screen, because the real one (artboards/Main.dc.html) has not been built yet.
 */
@Composable
private fun Placeholder(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Ground)
            .safeDrawingPadding()
            .padding(horizontal = Spacing.gutter),
        verticalArrangement = Arrangement.spacedBy(Spacing.md, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Pack a Bunch",
            style = androidx.compose.material3.MaterialTheme.typography.headlineMedium,
            color = TextPrimary,
        )
        Text(
            text = "Scaffold only. The packing engine is in :packing; screens come next.",
            style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
            color = TextSecondary,
        )
    }
}

@Preview(widthDp = 412, heightDp = 916)
@Composable
private fun PlaceholderPreview() {
    PackABunchTheme { Placeholder() }
}
