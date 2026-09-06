package com.packabunch.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.packabunch.ui.motion.Motion
import com.packabunch.ui.motion.pressScale
import com.packabunch.ui.theme.Accent
import com.packabunch.ui.theme.Chrome
import com.packabunch.ui.theme.Ground
import com.packabunch.ui.theme.NumeralChip
import com.packabunch.ui.theme.Primary
import com.packabunch.ui.theme.Spacing
import com.packabunch.ui.theme.TextPrimary
import com.packabunch.ui.theme.TextTertiary
import com.packabunch.ui.theme.UiFamily

/**
 * Every screen's outer frame.
 *
 * The artboards leave the top 30 dp and the bottom strip empty on purpose — those are the
 * real Android system bars, not something to paint. Here that means `safeDrawing` insets
 * rather than fixed 30 dp padding, so the layout is right on a phone with a different
 * status bar height, and edge-to-edge stays honest.
 */
@Composable
fun ScreenScaffold(
    modifier: Modifier = Modifier,
    background: Color = Ground,
    applyInsets: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(background)
            .then(if (applyInsets) Modifier.windowInsetsPadding(WindowInsets.safeDrawing) else Modifier),
        content = content,
    )
}

/** Full-bleed variant for the camera screens, which draw under the system bars. */
@Composable
fun FullBleedScaffold(
    modifier: Modifier = Modifier,
    background: Color = Color.Black,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier = modifier.fillMaxSize().background(background), content = content)
}

/** Back chevron, title, and whatever the screen needs on the right. */
@Composable
fun PackAppBar(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (onBack != null) {
            PackIconButton(PackIcons.Back, contentDescription = "Back", onClick = onBack)
        } else {
            Spacer(Modifier.size(4.dp))
        }

        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = TextPrimary,
                fontFamily = UiFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
            )
            if (subtitle != null) {
                Text(text = subtitle, style = NumeralChip, color = Color(0xFF8A7565))
            }
        }

        actions()
    }
}

/**
 * The three-segment progress bar on the create flow. Segments fill rather than switch, so
 * moving between steps reads as travel and going back reads as going back.
 */
@Composable
fun StepProgress(
    step: Int,
    totalSteps: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(totalSteps) { index ->
            val filled = index < step
            val color by animateColorAsState(
                targetValue = if (filled) Primary else Color(0xFFE7DACA),
                animationSpec = Motion.standardTween(),
                label = "stepSegment",
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(5.dp)
                    .background(color, RoundedCornerShape(999.dp)),
            )
        }
    }
}

/** "STEP 1 OF 3 · THE SPACE" */
@Composable
fun StepLabel(step: Int, totalSteps: Int, name: String, modifier: Modifier = Modifier) {
    Text(
        text = "STEP $step OF $totalSteps · ${name.uppercase()}",
        color = TextTertiary,
        fontFamily = UiFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.5f.sp,
        letterSpacing = 0.3.sp,
        modifier = modifier,
    )
}

/** The big screen heading under the step label. */
@Composable
fun ScreenHeading(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = TextPrimary,
        fontFamily = UiFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.6).sp,
        modifier = modifier,
    )
}

enum class NavDestination { Projects, Settings }

/**
 * The floating dark nav pill with the accent FAB in the middle.
 *
 * It sits *over* the content rather than in a bottom bar slot, which is why screens that
 * use it scroll behind it instead of being squashed above it. The current destination
 * expands to show its label; the other collapses to its icon.
 */
@Composable
fun NavPill(
    current: NavDestination,
    onNavigate: (NavDestination) -> Unit,
    onNewPack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .warmShadow(14.dp, RoundedCornerShape(34.dp))
            .background(Chrome, RoundedCornerShape(34.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        NavPillItem(
            icon = PackIcons.Projects,
            label = "Projects",
            selected = current == NavDestination.Projects,
            onClick = { onNavigate(NavDestination.Projects) },
        )

        Box(
            modifier = Modifier
                .size(56.dp)
                .pressScale(pressedScale = 0.9f)
                .background(Accent, RoundedCornerShape(28.dp))
                .clip(RoundedCornerShape(28.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(color = Chrome),
                    onClick = onNewPack,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(PackIcons.Plus, contentDescription = "New pack", tint = Chrome, modifier = Modifier.size(24.dp))
        }

        NavPillItem(
            icon = PackIcons.Settings,
            label = "Settings",
            selected = current == NavDestination.Settings,
            onClick = { onNavigate(NavDestination.Settings) },
        )
    }
}

@Composable
private fun NavPillItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val expansion by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = Motion.standardTween(),
        label = "navExpansion",
    )
    val background by animateColorAsState(
        targetValue = if (selected) Color.White else Color.Transparent,
        animationSpec = Motion.standardTween(),
        label = "navBackground",
    )
    val tint by animateColorAsState(
        targetValue = if (selected) Primary else Color(0xFFC9B29E),
        animationSpec = Motion.standardTween(),
        label = "navTint",
    )

    Row(
        modifier = Modifier
            .height(52.dp)
            .pressScale(pressedScale = 0.94f)
            .background(background, RoundedCornerShape(26.dp))
            .clip(RoundedCornerShape(26.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = Primary),
                onClick = onClick,
            )
            .padding(horizontal = (14 + 6 * expansion).dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(if (selected) 20.dp else 21.dp))
        if (expansion > 0.4f) {
            Text(
                text = label,
                color = TextPrimary.copy(alpha = expansion),
                fontFamily = UiFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
            )
        }
    }
}

/** Standard bottom padding so content clears the floating nav pill. */
val NavPillClearance = 96.dp

@Composable
fun ScreenBottomBar(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.gutter),
        content = content,
    )
}
