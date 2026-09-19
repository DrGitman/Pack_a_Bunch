package com.packabunch.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.packabunch.R

/** The official mark, from `logos/`. Tile brown sampled from logo_theme_brown_white_inside.png. */
val LogoBrown = Color(0xFF955532)

/** The parcel mark on the brand tile — splash, about row, empty states that want the brand. */
@Composable
fun BrandTile(
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
    cornerRadius: Dp = 20.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .background(LogoBrown, RoundedCornerShape(cornerRadius)),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.logo_mark_white),
            contentDescription = "Pack a Bunch",
            modifier = Modifier.size(size * 0.6f),
            contentScale = ContentScale.Fit,
        )
    }
}

/** The parcel mark alone, in brand brown, for light surfaces. */
@Composable
fun BrandMark(modifier: Modifier = Modifier, size: Dp = 40.dp) {
    Image(
        painter = painterResource(R.drawable.logo_mark_brown),
        contentDescription = "Pack a Bunch",
        modifier = modifier.size(size),
        contentScale = ContentScale.Fit,
    )
}

/** The wordmark. [onDark] picks the white cut rather than tinting the black one. */
@Composable
fun Wordmark(
    modifier: Modifier = Modifier,
    height: Dp = 26.dp,
    onDark: Boolean = false,
) {
    Image(
        painter = painterResource(
            if (onDark) R.drawable.logo_wordmark_white else R.drawable.logo_wordmark_black,
        ),
        contentDescription = "Pack a Bunch",
        modifier = modifier.height(height),
        contentScale = ContentScale.Fit,
    )
}
