package com.packabunch.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import kotlinx.coroutines.delay

/*
 * The line that sits under the "Projects" heading.
 *
 * Arriving straight from sign-in it fades up reading "Welcome back", holds for about a second,
 * then cross-fades to the pack count and stays there. Every other arrival shows the count
 * immediately with no motion. Same timing as "Greeting · Motion" in Figma.
 *
 * There is deliberately no welcome screen: the greeting rides along with the packs list instead
 * of standing between the person and it.
 */

private val SubtitleColor = Color(0xFF7C6857)
private val Standard = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)   // Material "standard" easing
private val SubtitleHeight = 22.dp

private const val GreetingText = "Welcome back"
private const val EnterMillis = 340
private const val ExitMillis = 280
private const val HoldMillis = 1_150L

/**
 * @param packCount how many packs the person has saved.
 * @param greet true only when this screen was reached straight from a successful sign-in.
 *   Pass false on every other arrival (tab switches, back navigation, cold start with a live
 *   session). The greeting is shown at most once per screen instance — a rotation or a
 *   recomposition will not replay it.
 */
@Composable
fun ProjectsSubtitle(
    packCount: Int,
    greet: Boolean,
    modifier: Modifier = Modifier,
    color: Color = SubtitleColor,
) {
    val count = if (packCount == 1) "1 pack saved" else "$packCount packs saved"

    // survives rotation, so the greeting does not start over
    var greeted by rememberSaveable { mutableStateOf(!greet) }
    var line by rememberSaveable { mutableStateOf(if (greet) null else count) }

    LaunchedEffect(greet) {
        if (!greeted) {
            line = GreetingText
            delay(HoldMillis)
            greeted = true
        }
    }
    // keep the settled line in step with the real count
    LaunchedEffect(count, greeted) {
        if (greeted) line = count
    }

    AnimatedContent(
        targetState = line,
        modifier = modifier.height(SubtitleHeight),
        transitionSpec = {
            (
                fadeIn(tween(EnterMillis, easing = Standard)) +
                    slideInVertically(tween(EnterMillis + 60, easing = Standard)) { it / 2 }
                ) togetherWith (
                fadeOut(tween(ExitMillis, easing = Standard)) +
                    slideOutVertically(tween(ExitMillis, easing = Standard)) { -it / 2 }
                )
        },
        label = "projects-subtitle",
    ) { value ->
        if (value != null) {
            Text(
                text = value,
                color = color,
                fontSize = 15.sp,
                lineHeight = 20.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
