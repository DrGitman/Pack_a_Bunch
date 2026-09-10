package com.packabunch.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.packabunch.ui.theme.Outline
import com.packabunch.ui.theme.Spacing

/** Exists only until Room's first emission; never delays real content. */
@Composable
fun PackListSkeleton(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "savedPacksLoading")
    val opacity by transition.animateFloat(
        initialValue = 0.35f, targetValue = 0.7f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "placeholderPulse",
    )
    Column(modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.md)
        .clearAndSetSemantics {}, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(3) {
            Box(Modifier.fillMaxWidth().height(150.dp)
                .graphicsLayer { alpha = opacity }
                .background(Outline, RoundedCornerShape(22.dp)))
        }
    }
}
