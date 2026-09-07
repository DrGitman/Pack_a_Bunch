package com.packabunch.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.packabunch.ui.components.PackIcons
import com.packabunch.ui.components.PackTextButton
import com.packabunch.ui.components.PrimaryButton
import com.packabunch.ui.components.ScreenScaffold
import com.packabunch.ui.motion.Motion
import com.packabunch.ui.render.CrateDiagram
import com.packabunch.ui.theme.BrandTint
import com.packabunch.ui.theme.CautionTint
import com.packabunch.ui.theme.Outline
import com.packabunch.ui.theme.Primary
import com.packabunch.ui.theme.Spacing
import com.packabunch.ui.theme.TextPrimary
import com.packabunch.ui.theme.TextSecondary
import com.packabunch.ui.theme.TextTertiary
import com.packabunch.ui.theme.UiFamily
import kotlinx.coroutines.launch

/**
 * First run — `OnbMeasure`, `OnbPlan`, `OnbPack`, and `Onboarding` (the limits slide).
 *
 * Four slides, and the fourth is the one that matters. It ships as a *slide*, not a dialog
 * and not a link in a settings page, because what the app cannot do is as much a part of
 * knowing what it is as what it can. Somebody who finds out about soft bags on slide four
 * has lost ten seconds; somebody who finds out at the kerb has lost their afternoon.
 *
 * Skippable throughout. A person who wants to get on with it should be able to.
 */
private data class Slide(
    val icon: ImageVector,
    val title: String,
    val body: String,
    val isLimits: Boolean = false,
    val limits: List<String> = emptyList(),
)

private val SLIDES = listOf(
    Slide(
        icon = PackIcons.Ruler,
        title = "Measure the space",
        body = "Type the inside measurements, or sweep the camera round it. Typing works " +
            "on every phone and is always there.",
    ),
    Slide(
        icon = PackIcons.Cube,
        title = "Add what's going in",
        body = "Name, size at its widest points, how many. We work out an arrangement that " +
            "actually fits — and tell you plainly about anything that doesn't.",
    ),
    Slide(
        icon = PackIcons.Check,
        title = "Follow the order",
        body = "One piece at a time, bottom up, described against the container's own front, " +
            "back, left and right. Never against where you happen to be standing.",
    ),
    Slide(
        icon = PackIcons.Info,
        title = "What it can't do",
        body = "Worth knowing before you start:",
        isLimits = true,
        limits = listOf(
            "Soft things. A duvet or a bin bag squashes, and no geometry handles that.",
            "Odd shapes. Everything is treated as the box it would fit inside, so a lamp " +
                "takes more room on screen than in life.",
            "Weight. We can't tell what will take load, so nothing is stacked on anything " +
                "you've marked fragile.",
        ),
    ),
)

@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState(pageCount = { SLIDES.size })
    val scope = rememberCoroutineScope()
    val last = pagerState.currentPage == SLIDES.lastIndex

    ScreenScaffold(modifier) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter, vertical = 6.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            if (!last) PackTextButton(text = "Skip", onClick = onFinished, color = TextTertiary)
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
        ) { page ->
            SlideContent(SLIDES[page])
        }

        Row(
            Modifier.fillMaxWidth().padding(vertical = Spacing.base),
            horizontalArrangement = Arrangement.Center,
        ) {
            repeat(SLIDES.size) { index ->
                val active = index == pagerState.currentPage
                val width by animateDpAsState(
                    targetValue = if (active) 22.dp else 7.dp,
                    animationSpec = Motion.standardTween(),
                    label = "dotWidth",
                )
                val colour by animateColorAsState(
                    targetValue = if (active) Primary else Outline,
                    animationSpec = Motion.standardTween(),
                    label = "dotColour",
                )
                Box(
                    Modifier
                        .padding(horizontal = 3.dp)
                        .size(width = width, height = 7.dp)
                        .background(colour, RoundedCornerShape(999.dp)),
                )
            }
        }

        Column(Modifier.padding(horizontal = Spacing.gutter)) {
            PrimaryButton(
                text = if (last) "Get started" else "Next",
                onClick = {
                    if (last) {
                        onFinished()
                    } else {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    }
                },
            )
        }

        Spacer(Modifier.height(Spacing.base))
    }
}

@Composable
private fun SlideContent(slide: Slide) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.gutter),
        verticalArrangement = Arrangement.Center,
    ) {
        if (slide.isLimits) {
            Box(
                Modifier.size(64.dp).background(CautionTint, RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    slide.icon,
                    contentDescription = null,
                    tint = Color(0xFFB4761A),
                    modifier = Modifier.size(30.dp),
                )
            }
        } else {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(BrandTint, RoundedCornerShape(28.dp)),
                contentAlignment = Alignment.Center,
            ) {
                CrateDiagram(Modifier.size(160.dp, 140.dp))
            }
        }

        Spacer(Modifier.height(Spacing.xl))

        Text(
            text = slide.title,
            color = TextPrimary,
            fontFamily = UiFamily,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 29.sp,
            lineHeight = 36.sp,
            letterSpacing = (-0.9).sp,
        )

        Spacer(Modifier.height(10.dp))

        Text(
            text = slide.body,
            color = TextSecondary,
            fontFamily = UiFamily,
            fontSize = 15.sp,
            lineHeight = 23.sp,
        )

        if (slide.limits.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.base))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                slide.limits.forEach { limit ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(Color.White, RoundedCornerShape(18.dp))
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(11.dp),
                    ) {
                        Icon(
                            PackIcons.Close,
                            contentDescription = null,
                            tint = Color(0xFFB09A85),
                            modifier = Modifier.size(17.dp),
                        )
                        Text(
                            text = limit,
                            color = TextSecondary,
                            fontFamily = UiFamily,
                            fontSize = 13.5f.sp,
                            lineHeight = 20.sp,
                        )
                    }
                }
            }
        }
    }
}
