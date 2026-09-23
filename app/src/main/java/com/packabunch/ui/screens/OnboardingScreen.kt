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

/**
 * [motion] is the designer's Lottie for that slide, or null until one exists — the still
 * artwork stays as the fallback so a slide is never blank while the set is half finished.
 */
private data class IntroSlide(val art: Int, val motion: Int?, val step: String, val title: String, val body: String, val foot: String)
private val introSlides = listOf(
    IntroSlide(R.drawable.onbmeasure, R.raw.onb_measure, "STEP ONE", "Measure the space",
        "Point the camera at the inside of a crate, box or car boot, or just type the numbers off a tape measure. Both give you the same plan.",
        "Camera measuring works on some phones. Typing always works."),
    IntroSlide(R.drawable.onbplan, R.raw.onb_plan, "STEP TWO", "See what actually fits",
        "Add your things with their width, depth and height. You get an arrangement that respects turning, stacking and what mustn’t be squashed.", "Up to 20 pieces per pack."),
    IntroSlide(R.drawable.onbpack, R.raw.onb_pack, "STEP THREE", "Follow it, one piece at a time",
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
        BoxWithConstraints(Modifier.weight(1f)) {
        // The artboards are 916dp tall and real phones are shorter, so the illustration gives
        // up height first. The words below it must not need scrolling.
        val artHeight = (maxHeight * 0.42f).coerceIn(180.dp, 300.dp)
        HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
                Spacer(Modifier.height(18.dp))
                if (page < 3) {
                    val slide = introSlides[page]
                    Box(Modifier.fillMaxWidth().background(Surface, RoundedCornerShape(30.dp)).padding(14.dp)) {
                        Box(Modifier.fillMaxWidth().height(artHeight).background(BrandTint, RoundedCornerShape(22.dp)), contentAlignment = Alignment.Center) {
                            if (slide.motion != null) {
                                SlideMotion(slide.motion, playing = pager.currentPage == page,
                                    modifier = Modifier.fillMaxWidth().height(artHeight - 10.dp))
                            } else {
                                Image(painterResource(slide.art), null, Modifier.fillMaxWidth().height(artHeight - 10.dp))
                            }
                        }
                    }
                    Spacer(Modifier.height(22.dp))
                    Text(slide.step, color = Primary, fontFamily = UiFamily, fontSize = 12.5.sp,
                        fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(slide.title, color = TextPrimary, fontFamily = UiFamily, fontSize = 31.sp,
                        lineHeight = 38.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp)
                    Spacer(Modifier.height(12.dp))
                    Text(slide.body, color = TextSecondary, fontFamily = UiFamily, fontSize = 15.sp, lineHeight = 23.sp)
                } else {
                    Text("What it packs", color = TextPrimary, fontFamily = UiFamily,
                        fontWeight = FontWeight.ExtraBold, fontSize = 29.sp, lineHeight = 36.sp, letterSpacing = (-.9).sp)
                    Spacer(Modifier.height(18.dp))
                    // The cards, badges and their wording all live inside this animation.
                    SlideMotion(R.raw.onb_what, playing = pager.currentPage == page,
                        modifier = Modifier.fillMaxWidth().aspectRatio(279f / 392f))
                }
                Spacer(Modifier.height(20.dp))
            }
        }
        }
        // Drawn here, not from the dots Lottie: that export lost the active pill's width,
        // leaving four identical circles. Same shapes as the artboard, animated on the tokens.
        Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
            repeat(4) { index ->
                val active = index == pager.currentPage
                val width by androidx.compose.animation.core.animateDpAsState(
                    if (active) 26.dp else 8.dp, com.packabunch.ui.motion.Motion.standardTween(), label = "dotWidth",
                )
                val colour by androidx.compose.animation.animateColorAsState(
                    if (active) Primary else OutlineStrong, com.packabunch.ui.motion.Motion.standardTween(), label = "dotColour",
                )
                Box(Modifier.size(width, 8.dp).background(colour, RoundedCornerShape(99.dp)))
            }
        }
        Column(Modifier.padding(horizontal = 20.dp)) {
            if (pager.currentPage < 3) {
                Text(introSlides[pager.currentPage].foot,
                    Modifier.fillMaxWidth().padding(bottom = 14.dp), color = TextTertiary, fontFamily = UiFamily,
                    fontSize = 12.5.sp, textAlign = TextAlign.Center)
            }
            PrimaryButton(if (pager.currentPage == 3) "Got it" else "Next", {
                if (pager.currentPage == 3) onFinished() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
            })
        }
        Spacer(Modifier.height(12.dp))
    }
}

/**
 * The designer's slide animation: the "intro" marker plays once, then "loop" repeats forever.
 *
 * Driven by one animatable rather than two pieces of state — swapping a clip mid-flight left
 * the first slide frozen on its last intro frame instead of looping.
 */
@Composable
private fun SlideMotion(raw: Int, playing: Boolean, modifier: Modifier = Modifier.fillMaxWidth().height(290.dp)) {
    val composition by com.airbnb.lottie.compose.rememberLottieComposition(
        com.airbnb.lottie.compose.LottieCompositionSpec.RawRes(raw),
    )
    val animation = com.airbnb.lottie.compose.rememberLottieAnimatable()
    LaunchedEffect(composition, playing) {
        val loaded = composition ?: return@LaunchedEffect
        if (!playing) return@LaunchedEffect
        val markers = loaded.markers.map { it.name }
        if ("intro" in markers) {
            animation.animate(loaded, clipSpec = com.airbnb.lottie.compose.LottieClipSpec.Marker("intro"))
        }
        // An intro with no loop is a one-off: it holds its last frame rather than restarting.
        if ("loop" in markers || "intro" !in markers) {
            animation.animate(
                composition = loaded,
                clipSpec = if ("loop" in markers) com.airbnb.lottie.compose.LottieClipSpec.Marker("loop") else null,
                iterations = com.airbnb.lottie.compose.LottieConstants.IterateForever,
            )
        }
    }
    com.airbnb.lottie.compose.LottieAnimation(
        composition = composition,
        progress = { animation.progress },
        modifier = modifier,
        // These are layered vector comps; software rendering drops frames on older phones.
        renderMode = com.airbnb.lottie.RenderMode.HARDWARE,
    )
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
