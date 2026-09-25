package com.packabunch.ui.components

import androidx.annotation.RawRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.packabunch.R

/*
 * The floating nav bar: Projects · + · Settings.
 *
 * There is one white pill for the whole bar. Moving between destinations, it slides and stretches
 * to the new one while the old label shrinks away and the new one opens out — so the text leaves
 * as the pill arrives rather than popping.
 *
 * Opening a sub-page of Settings (You, Notifications, Pack Plan, Restore, Info) does not move the
 * pill at all: pass a different [NavSlot] in the same position and its icon and label swap
 * vertically in place, which reads as the bar relabelling itself rather than as navigation.
 *
 * Tapping + clears the bar back to bare icons — the pill and both labels go, the two icons stay
 * where they are, and the + turns into a × — so the new-pack screen has the room without the bar
 * vanishing. Dismissing it brings the pill and label back.
 *
 * The page body cross-fades underneath, so nothing but the bar ever travels.
 */

private val BarColor = Color(0xFF3F2718)
private val PillColor = Color(0xFFFFFFFF)
private val FabColor = Color(0xFFE08A46)
private val OnPillText = Color(0xFF2B1D14)
private val OnPillIcon = Color(0xFFC2702F)
private val OnBarIcon = Color(0xFFE6D7BE)

private val BarHeight = 64.dp
private val PillHeight = 48.dp
private val BarPadding = 8.dp
private val IconSize = 20.dp
private val FabSize = 42.dp

private val Standard = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
private const val LabelMillis = 280
private const val SwapMillis = 260
const val PageFadeMillis = 230

private val PillSpring = spring<Dp>(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)
private val CollapseSpring = spring<Dp>(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow)
private val FloatSpring = spring<Float>(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow)

/**
 * One position in the bar. Swapping the [NavSlot] at a position — same index, different [key] —
 * is what produces the in-place relabel; changing `selected` is what moves the pill.
 */
data class NavSlot(
    val key: String,
    val label: String,
    @RawRes val animation: Int,
)

/**
 * @param collapsed true while the new-pack screen is open. The pill and both labels clear away,
 *   leaving just the two icons and the +, which turns into a ×.
 */
@Composable
fun PackNavBar(
    slots: List<NavSlot>,
    selected: Int,
    onSelect: (Int) -> Unit,
    @RawRes fabAnimation: Int,
    onFabClick: () -> Unit,
    modifier: Modifier = Modifier,
    collapsed: Boolean = false,
    fabContentDescription: String = "New pack",
) {
    val density = LocalDensity.current
    val bounds = remember { mutableStateMapOf<Int, Pair<Float, Float>>() }
    var barX by remember { mutableFloatStateOf(0f) }
    var previous by remember { mutableIntStateOf(selected) }
    val movingRight = selected >= previous
    LaunchedEffect(selected) { previous = selected }

    val pillAlpha by animateFloatAsState(
        targetValue = if (collapsed) 0f else 1f,
        animationSpec = tween(if (collapsed) 160 else 240, easing = Standard),
        label = "pill-alpha",
    )
    val fabSource = remember { MutableInteractionSource() }
    val fabPressed by fabSource.collectIsPressedAsState()
    val fabScale by animateFloatAsState(
        targetValue = if (fabPressed) 0.86f else 1f,
        animationSpec = FloatSpring,
        label = "fab-press",
    )
    // Half a turn per press, taken slowly. A + has four-fold symmetry, so a quick quarter turn
    // is literally unreadable — it ends on a shape identical to the one it started from. Half a
    // turn over ~0.9 s gives the eye something to follow, and still lands square.
    var fabTurns by remember { mutableIntStateOf(0) }
    val fabTurn by animateFloatAsState(
        targetValue = fabTurns * -180f,
        animationSpec = spring(dampingRatio = 0.92f, stiffness = Spring.StiffnessVeryLow),
        label = "fab-turn",
    )

    val here = bounds[selected]
    val pillX by animateDpAsState(
        targetValue = with(density) { (here?.first ?: 0f).toDp() },
        animationSpec = PillSpring,
        label = "pill-x",
    )
    val pillW by animateDpAsState(
        targetValue = with(density) { (here?.second ?: 0f).toDp() },
        animationSpec = PillSpring,
        label = "pill-w",
    )

    // no fillMaxWidth: the bar wraps its contents, so it grows and shrinks with the pill and
    // re-centres itself. Put it in a Box with Alignment.BottomCenter and it stays centred.
    Box(
        modifier = modifier
            .height(BarHeight)
            .clip(RoundedCornerShape(BarHeight / 2))
            .background(BarColor)
            .onGloballyPositioned { barX = it.positionInRoot().x },
        contentAlignment = Alignment.CenterStart,
    ) {
        run {
            if (here != null) {
                Box(
                    Modifier
                        .alpha(pillAlpha)
                        .offset(x = pillX)
                        .width(pillW)
                        .height(PillHeight)
                        .clip(RoundedCornerShape(PillHeight / 2))
                        .background(PillColor),
                )
            }

            Row(
                modifier = Modifier.padding(horizontal = BarPadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                slots.forEachIndexed { i, slot ->
                    NavSlotItem(
                        slot = slot,
                        selected = i == selected,
                        collapsed = collapsed,
                        movingRight = movingRight,
                        onClick = { onSelect(i) },
                        modifier = Modifier.onGloballyPositioned { c ->
                            bounds[i] = (c.positionInRoot().x - barX) to c.size.width.toFloat()
                        },
                    )
                    if (i == 0) {
                        Box(
                            Modifier
                                .size(FabSize)
                                .graphicsLayer {
                                    rotationZ = fabTurn
                                    scaleX = fabScale
                                    scaleY = fabScale
                                }
                                .clip(CircleShape)
                                .background(FabColor),
                            contentAlignment = Alignment.Center,
                        ) {
                            LottieTapIcon(
                                animation = fabAnimation,
                                contentDescription = fabContentDescription,
                                onClick = { fabTurns++; onFabClick() },
                                size = FabSize,
                                interactionSource = fabSource,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NavSlotItem(
    slot: NavSlot,
    selected: Boolean,
    collapsed: Boolean,
    movingRight: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val firstKey = remember { slot.key }
    val open = selected && !collapsed     // "wearing the pill"
    val iconTint by animateColorAsState(
        targetValue = if (open) OnPillIcon else OnBarIcon,
        animationSpec = tween(LabelMillis, easing = Standard),
        label = "nav-tint",
    )

    Row(
        modifier = modifier
            .height(PillHeight)
            .widthIn(min = PillHeight)
            .padding(horizontal = if (open) 13.dp else 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        // identity swap: same position, different destination — slides vertically in place
        AnimatedContent(
            targetState = slot.key,
            transitionSpec = { verticalSwap() },
            label = "nav-icon",
        ) { key ->
            LottieTapIcon(
                animation = slot.animation,
                contentDescription = slot.label,
                onClick = onClick,
                size = IconSize,
                tint = iconTint,
                playOnAppear = key != firstKey,   // still on first show, animates on a swap
            )
        }

        // the label opens out as the pill arrives and shrinks away as it leaves
        AnimatedVisibility(
            visible = open,
            enter = expandHorizontally(tween(LabelMillis, easing = Standard), clip = false) +
                fadeIn(tween(LabelMillis, delayMillis = 70, easing = Standard)) +
                slideInHorizontally(tween(LabelMillis, easing = Standard)) {
                    if (movingRight) it / 2 else -it / 2
                },
            exit = shrinkHorizontally(tween(LabelMillis, easing = Standard), clip = false) +
                fadeOut(tween(130, easing = Standard)) +
                slideOutHorizontally(tween(LabelMillis, easing = Standard)) {
                    if (movingRight) -it / 2 else it / 2
                },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(8.dp))
                AnimatedContent(
                    targetState = slot.label,
                    transitionSpec = { verticalSwap() },
                    label = "nav-label",
                ) { text ->
                    Text(
                        text = text,
                        color = OnPillText,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** New content rises from below, old content leaves upward. Used for the in-place relabel. */
private fun verticalSwap(): ContentTransform =
    (
        slideInVertically(tween(SwapMillis, easing = Standard)) { it / 2 } +
            fadeIn(tween(SwapMillis, easing = Standard))
        ) togetherWith (
        slideOutVertically(tween(SwapMillis, easing = Standard)) { -it / 2 } +
            fadeOut(tween(SwapMillis - 80, easing = Standard))
        )

/**
 * The page body — cross-fades while the pill travels, so the two movements read as one gesture
 * and the page itself never slides.
 *
 *     Box {
 *         PackPages(page) { p -> when (p) { Page.Projects -> ProjectsScreen(); … } }
 *         PackNavBar(
 *             slots = listOf(NavSlots.Projects, rightSlot),
 *             selected = if (page == Page.Projects) 0 else 1,
 *             onSelect = { … },
 *             fabAnimation = R.raw.icon_plus,
 *             onFabClick = { newPackOpen = true },
 *             collapsed = newPackOpen,
 *             modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
 *         )
 *     }
 */
@Composable
fun <T> PackPages(
    page: T,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit,
) {
    Crossfade(
        targetState = page,
        animationSpec = tween(PageFadeMillis, easing = Standard),
        modifier = modifier,
        label = "pack-pages",
    ) { p -> content(p) }
}

/*
 * The slots, ready to use. The right-hand position takes whichever of these matches the page the
 * person is on, so opening a sub-page of Settings relabels the bar in place:
 *
 *     val rightSlot = when (page) {
 *         Page.Account       -> NavSlots.Account
 *         Page.Notifications -> NavSlots.Notifications
 *         Page.PackPlan      -> NavSlots.PackPlan
 *         Page.Restore       -> NavSlots.Restore
 *         Page.Info          -> NavSlots.Info
 *         else               -> NavSlots.Settings
 *     }
 */
object NavSlots {
    val Projects = NavSlot("projects", "Projects", R.raw.icon_projects)
    val Settings = NavSlot("settings", "Settings", R.raw.icon_gear)
    val Account = NavSlot("account", "You", R.raw.icon_account)
    val Notifications = NavSlot("notifications", "Notifications", R.raw.icon_bell)
    val PackPlan = NavSlot("packplan", "Pack Plan", R.raw.icon_package)
    val Restore = NavSlot("restore", "Restore", R.raw.icon_restore)
    val Info = NavSlot("info", "Info", R.raw.icon_info)
}

/**
 * The bar as every screen uses it: Projects on the left, and on the right whichever slot matches
 * the page you are on, so a sub-page of Settings relabels the bar instead of hiding it.
 *
 * Each screen passes its own [here]; everything else is the same everywhere, which is what keeps
 * the motion identical from page to page.
 */
@Composable
fun PackBar(
    here: NavSlot,
    onProjects: () -> Unit,
    onSettings: () -> Unit,
    onNewPack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Left is always Projects. Right is wherever you are, or Settings when you are on Projects
    // itself — otherwise the bar shows the same icon twice.
    val right = if (here.key == NavSlots.Projects.key) NavSlots.Settings else here
    PackNavBar(
        modifier = modifier.navigationBarsPadding(),
        slots = listOf(NavSlots.Projects, right),
        selected = if (here.key == NavSlots.Projects.key) 0 else 1,
        onSelect = { index -> if (index == 0) onProjects() else onSettings() },
        fabAnimation = R.raw.icon_plus,
        onFabClick = onNewPack,
    )
}
