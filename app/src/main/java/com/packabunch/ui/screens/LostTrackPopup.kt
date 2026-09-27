package com.packabunch.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.google.ar.core.TrackingFailureReason
import com.packabunch.ui.components.PackIcons
import com.packabunch.ui.components.PackPopup
import com.packabunch.ui.theme.BrandTint
import com.packabunch.ui.theme.NumericFamily
import com.packabunch.ui.theme.Primary
import com.packabunch.ui.theme.Success

/**
 * "Lost track of the room" — shown over a camera screen when ARCore has lost its place for
 * long enough that the one-line advice is not going to be read. It names what was being
 * measured, says what is already kept, and offers the retry and the typed route side by side.
 *
 * [what] is the thing itself with its article — "the room", "the car boot", "the cake box".
 */
@Composable
fun LostTrackPopup(
    what: String,
    reason: TrackingFailureReason?,
    /** What is kept — "Width 58.4 cm and depth 39.6 cm are saved." — or null when nothing is yet. */
    saved: AnnotatedString?,
    retry: String,
    onRetry: () -> Unit,
    typeInstead: String,
    onTypeInstead: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val why = when (reason) {
        TrackingFailureReason.INSUFFICIENT_LIGHT -> "It got too dark to hold on to. Turn the torch on, point it back at $what and move slowly."
        TrackingFailureReason.INSUFFICIENT_FEATURES -> "There wasn't enough detail to hold on to. Point it back at $what, somewhere with some texture, and move slowly."
        TrackingFailureReason.CAMERA_UNAVAILABLE -> "Another app took the camera for a moment. Point it back at $what and move slowly."
        else -> "The camera moved too fast, or there wasn't enough detail to hold on to. Point it back at $what and move slowly."
    }
    PackPopup(
        icon = PackIcons.Rotate,
        iconTint = Primary,
        iconBackground = BrandTint,
        title = "Lost track of $what",
        body = why,
        detail = saved,
        detailIcon = saved?.let { PackIcons.Check },
        detailIconTint = Success,
        primary = retry,
        onPrimary = onRetry,
        secondary = typeInstead,
        onSecondary = onTypeInstead,
        onDismiss = onDismiss,
        modifier = modifier,
    )
}

/**
 * Whether the lost-tracking pop-up should be up: once tracking has been lost for a second and
 * a bit — a blink is covered by the advice line — and until tracking comes back or it is
 * dismissed. Dismissed stays dismissed until the next time tracking is lost.
 */
@Composable
fun rememberLostTrack(lost: Boolean): Pair<Boolean, () -> Unit> {
    var showing by remember { mutableStateOf(false) }
    var dismissed by remember { mutableStateOf(false) }
    LaunchedEffect(lost) {
        if (lost) {
            kotlinx.coroutines.delay(LOST_TRACK_DELAY_MS)
            if (!dismissed) showing = true
        } else {
            showing = false
            dismissed = false
        }
    }
    return showing to { showing = false; dismissed = true }
}

/** The thing being measured, as the pop-up names it: its own name if it has one. */
fun spaceNoun(name: String?, standingInside: Boolean): String {
    val n = name?.trim().orEmpty()
    return when {
        n.isNotEmpty() -> "the ${n.lowercase()}"
        standingInside -> "the room"
        else -> "the space"
    }
}

/** "Width 58.4 cm and depth 39.6 cm are saved.", figures in the numeric face. */
fun savedLengths(lengths: List<Pair<String, String>>): AnnotatedString? {
    if (lengths.isEmpty()) return null
    return buildAnnotatedString {
        lengths.forEachIndexed { i, (label, value) ->
            if (i > 0) append(if (i == lengths.lastIndex) " and " else ", ")
            append(if (i == 0) label.replaceFirstChar { it.uppercase() } else label.lowercase())
            append(" ")
            withStyle(SpanStyle(fontFamily = NumericFamily)) { append(value) }
        }
        append(if (lengths.size == 1) " is saved." else " are saved.")
    }
}

private const val LOST_TRACK_DELAY_MS = 1_200L
