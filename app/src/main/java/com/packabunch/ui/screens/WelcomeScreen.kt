package com.packabunch.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import com.packabunch.R
import com.packabunch.ui.theme.Surface
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.packabunch.ui.components.BrandTile
import com.packabunch.ui.components.PackIcons
import com.packabunch.ui.components.PackTextButton
import com.packabunch.ui.components.PrimaryButton
import com.packabunch.ui.components.ScreenScaffold
import com.packabunch.ui.components.SecondaryButton
import com.packabunch.ui.motion.entrance
import com.packabunch.ui.motion.rememberStaggeredEntrance
import com.packabunch.ui.theme.BrandTint
import com.packabunch.ui.theme.PackABunchTheme
import com.packabunch.ui.theme.Primary
import com.packabunch.ui.theme.Spacing
import com.packabunch.ui.theme.TextPrimary
import com.packabunch.ui.theme.TextSecondary
import com.packabunch.ui.theme.TextTertiary
import com.packabunch.ui.theme.UiFamily

/**
 * Welcome — `design/artboards/Main.dc.html`.
 *
 * Two things this screen must not do, both of which it would be easy to add: it does not
 * ask for the camera, and it does not put a sign-in wall in front of the sample. Somebody
 * should be able to see what the app does before it asks them for anything.
 */
@Composable
fun WelcomeScreen(
    onPlanAPack: () -> Unit,
    onTrySample: () -> Unit,
    onSeeProjects: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ArtboardPage(modifier) {
        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.gutter),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Image(painterResource(R.drawable.logo_theme_brown_white_inside), "Pack a Bunch", Modifier.size(38.dp))
            Text(
                text = "Pack a Bunch",
                color = TextPrimary,
                fontFamily = UiFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
            )
        }

        Box(Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp).fillMaxWidth()
            .background(Surface, RoundedCornerShape(28.dp)).padding(18.dp)) {
            Box(Modifier.fillMaxWidth().height(262.dp).background(BrandTint, RoundedCornerShape(20.dp))) {
                Image(painterResource(R.drawable.main), null, Modifier.fillMaxWidth().height(262.dp))
                Text("Example pack", Modifier.align(Alignment.TopStart).padding(14.dp)
                    .background(Surface, RoundedCornerShape(99.dp)).padding(horizontal = 12.dp, vertical = 7.dp),
                    fontFamily = UiFamily, fontSize = 12.sp, color = TextPrimary)
                Text("3 of 3 placed", Modifier.align(Alignment.BottomEnd).padding(14.dp)
                    .background(com.packabunch.ui.theme.ChromeAlt, RoundedCornerShape(99.dp)).padding(horizontal = 12.dp, vertical = 7.dp),
                    fontFamily = UiFamily, fontSize = 12.sp, color = com.packabunch.ui.theme.Ground)
            }
        }
        Spacer(Modifier.height(26.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.gutter),
        ) {
            val heading = rememberStaggeredEntrance(index = 0)
            Column(Modifier.entrance(heading)) {
                Text(
                    text = "Know what fits.",
                    color = TextPrimary,
                    fontFamily = UiFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 33.sp,
                    lineHeight = 39.sp,
                    letterSpacing = (-1).sp,
                )
                Text(
                    text = "See where it goes.",
                    color = TextPrimary,
                    fontFamily = UiFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 33.sp,
                    lineHeight = 39.sp,
                    letterSpacing = (-1).sp,
                )
            }

            Spacer(Modifier.height(12.dp))

            val blurb = rememberStaggeredEntrance(index = 1)
            Text(
                text = "Measure a space, add your things, and get a packing order you " +
                    "can actually follow.",
                color = TextSecondary,
                fontFamily = UiFamily,
                fontSize = 15.sp,
                lineHeight = 23.sp,
                modifier = Modifier.entrance(blurb),
            )

            Spacer(Modifier.height(20.dp))

            val steps = rememberStaggeredEntrance(index = 2)
            Row(
                modifier = Modifier.fillMaxWidth().entrance(steps),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StepChip(PackIcons.Ruler, "Measure", Modifier.weight(1f))
                StepChip(PackIcons.Cube, "Plan", Modifier.weight(1f))
                StepChip(PackIcons.Check, "Pack", Modifier.weight(1f))
            }
        }

        Spacer(Modifier.weight(1f))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.gutter),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PrimaryButton(text = "Plan a pack", onClick = onPlanAPack)
            SecondaryButton(text = "Try a sample pack", onClick = onTrySample)
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                PackTextButton(text = "Your saved packs", onClick = onSeeProjects)
            }
        }

        Spacer(Modifier.height(Spacing.sm))
    }
}

@Composable
private fun StepChip(icon: ImageVector, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(Surface, RoundedCornerShape(18.dp))
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, contentDescription = null, tint = Primary, modifier = Modifier.size(22.dp))
        Text(
            text = label,
            color = TextPrimary,
            fontFamily = UiFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 12.5f.sp,
        )
    }
}

@Preview(widthDp = 412, heightDp = 916)
@Composable
private fun WelcomePreview() {
    PackABunchTheme {
        Box(Modifier.background(Color(0xFFF7EFE6))) {
            WelcomeScreen(onPlanAPack = {}, onTrySample = {}, onSeeProjects = {})
        }
    }
}


