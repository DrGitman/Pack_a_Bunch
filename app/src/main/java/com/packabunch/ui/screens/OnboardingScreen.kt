package com.packabunch.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.packabunch.R
import com.packabunch.ui.components.*
import com.packabunch.ui.theme.*
import kotlinx.coroutines.launch

private data class IntroSlide(val art: Int, val step: String, val title: String, val body: String, val foot: String)
private val introSlides = listOf(
    IntroSlide(R.drawable.onbmeasure, "STEP ONE", "Measure the space",
        "Point the camera at the inside of a crate, box or car boot — or just type the numbers off a tape measure. Both give you the same plan.",
        "Camera measuring works on some phones. Typing always works."),
    IntroSlide(R.drawable.onbplan, "STEP TWO", "See what actually fits",
        "Add your things with their width, depth and height. You get an arrangement that respects turning, stacking and what mustn’t be squashed.", "Up to 20 pieces per pack."),
    IntroSlide(R.drawable.onbpack, "STEP THREE", "Follow it, one piece at a time",
        "Numbered steps in an order that keeps everything supported: which item, which way round, which corner it goes in.",
        "Mark each piece as it goes in. Your place is saved."),
)

@Composable
fun OnboardingScreen(onFinished: () -> Unit, modifier: Modifier = Modifier) {
    val pager = rememberPagerState(pageCount = { 4 })
    val scope = rememberCoroutineScope()
    ScreenScaffold(modifier) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(R.drawable.logo_theme_brown_white_inside), "Pack a Bunch", Modifier.size(34.dp))
            Text("Pack a Bunch", Modifier.weight(1f), color = TextPrimary, fontFamily = UiFamily, fontWeight = FontWeight.Bold, fontSize = 15.5.sp)
            PackTextButton("Skip", onFinished, color = TextTertiary)
        }
        HorizontalPager(pager, Modifier.weight(1f)) { page ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
                Spacer(Modifier.height(26.dp))
                if (page < 3) {
                    val slide = introSlides[page]
                    Box(Modifier.fillMaxWidth().background(Surface, RoundedCornerShape(30.dp)).padding(18.dp)) {
                        Box(Modifier.fillMaxWidth().height(300.dp).background(BrandTint, RoundedCornerShape(22.dp)), contentAlignment = Alignment.Center) {
                            Image(painterResource(slide.art), null, Modifier.fillMaxWidth().height(290.dp))
                        }
                    }
                    Spacer(Modifier.height(30.dp))
                    Text(slide.step, color = Primary, fontFamily = UiFamily, fontSize = 12.5.sp,
                        fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(slide.title, color = TextPrimary, fontFamily = UiFamily, fontSize = 31.sp,
                        lineHeight = 38.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp)
                    Spacer(Modifier.height(12.dp))
                    Text(slide.body, color = TextSecondary, fontFamily = UiFamily, fontSize = 15.sp, lineHeight = 23.sp)
                } else {
                    Text("What this does,\nand what it doesn't", color = TextPrimary, fontFamily = UiFamily,
                        fontWeight = FontWeight.ExtraBold, fontSize = 29.sp, lineHeight = 36.sp, letterSpacing = (-.9).sp)
                    Spacer(Modifier.height(11.dp))
                    Text("Thirty seconds now saves a wrong assumption later.", color = TextSecondary, fontFamily = UiFamily, fontSize = 15.sp, lineHeight = 23.sp)
                    Spacer(Modifier.height(24.dp))
                    LimitCard("Boxy spaces, open at the top", "Crates, storage boxes, drawers, a car boot. You measure the space inside and keep the opening clear.")
                    Spacer(Modifier.height(11.dp))
                    LimitCard("Firm things, up to 20 pieces", "Every item gets a width, depth and height — measured with the camera or typed in. Then you get an order to pack them in.")
                    Spacer(Modifier.height(11.dp))
                    LimitCard("Not yet: soft or heavy", "Backpacks, duvets, and whether a stack will take the weight. Scanned shapes are approximate. Where we can't tell, we say so instead of guessing.", true)
                }
                Spacer(Modifier.height(20.dp))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
            repeat(4) { index -> Box(Modifier.size(if (index == pager.currentPage) 26.dp else 8.dp, 8.dp)
                .background(if (index == pager.currentPage) Primary else OutlineStrong, RoundedCornerShape(99.dp))) }
        }
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text(if (pager.currentPage < 3) introSlides[pager.currentPage].foot else "Better to know now than half way through a move.",
                Modifier.fillMaxWidth().padding(bottom = 14.dp), color = TextTertiary, fontFamily = UiFamily,
                fontSize = 12.5.sp, textAlign = TextAlign.Center)
            PrimaryButton(if (pager.currentPage == 3) "Got it" else "Next", {
                if (pager.currentPage == 3) onFinished() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
            })
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun LimitCard(title: String, detail: String, caution: Boolean = false) {
    Row(Modifier.fillMaxWidth().background(Surface, RoundedCornerShape(24.dp)).padding(18.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(44.dp).background(if (caution) ErrorTint else SuccessTint, RoundedCornerShape(15.dp)), contentAlignment = Alignment.Center) {
            Icon(if (caution) PackIcons.Close else PackIcons.Check, null, Modifier.size(22.dp), tint = if (caution) ErrorRed else Success)
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontFamily = UiFamily, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(5.dp))
            Text(detail, color = TextSecondary, fontFamily = UiFamily, fontSize = 14.sp, lineHeight = 21.sp)
        }
    }
}
