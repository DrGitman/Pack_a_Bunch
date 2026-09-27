package com.packabunch.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.packabunch.ui.theme.Spacing
import kotlinx.coroutines.launch

/**
 * The app's bottom sheet: the item editor and the pack menu both sit in it.
 *
 * - **Swipe it away.** The handle and the heading follow the finger down; let go past a third
 *   of a thumb's travel, or flick, and it goes; otherwise it springs back. The handle used to
 *   be only a picture of one.
 * - **Clear of the phone's own buttons.** The sheet's white runs under the navigation bar, but
 *   its contents stop above it — the last button used to sit right on top of the system bar,
 *   where aiming for it hit Back or Home instead. The keyboard pushes it up the same way.
 * - **Never taller than the screen.** Past that the contents scroll; the heading stays put.
 * - Back closes it, and so does a tap on the dimmed page.
 */
@Composable
fun PackSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    /** What stays at the top and can be dragged by: usually the title and a close button. */
    header: @Composable ColumnScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    BackHandler(onBack = onDismiss)
    val scope = rememberCoroutineScope()
    val drag = remember { Animatable(0f) }
    var height by remember { mutableIntStateOf(0) }
    val dismissAt = with(LocalDensity.current) { 110.dp.toPx() }
    val dragState = rememberDraggableState { delta ->
        scope.launch { drag.snapTo((drag.value + delta).coerceAtLeast(0f)) }
    }

    Box(modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = 1f - (drag.value / height.coerceAtLeast(1)).coerceIn(0f, 1f) }
                .background(Color(0x7A2B1D14))
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onDismiss() },
        )

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.statusBars)
                .offset { IntOffset(0, drag.value.toInt()) }
                .onSizeChanged { height = it.height }
                .fillMaxWidth()
                .background(Color.White, RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                .swallowTaps()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .imePadding(),
        ) {
            Column(
                Modifier
                    .draggable(
                        state = dragState,
                        orientation = Orientation.Vertical,
                        onDragStopped = { velocity ->
                            if (drag.value > dismissAt || velocity > 1800f) {
                                drag.animateTo(height.toFloat().coerceAtLeast(dismissAt), tween(180))
                                onDismiss()
                            } else {
                                drag.animateTo(0f, spring(dampingRatio = 0.8f))
                            }
                        },
                    )
                    .padding(horizontal = Spacing.gutter)
                    .padding(top = 8.dp),
            ) {
                // A real target for the thumb, not just the 4 dp bar it shows.
                Box(Modifier.fillMaxWidth().height(22.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(width = 40.dp, height = 4.dp).background(Color(0xFFE2D5C6), RoundedCornerShape(999.dp)))
                }
                header()
            }
            Column(
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.gutter)
                    .padding(top = 4.dp, bottom = 20.dp),
                content = content,
            )
        }
    }
}
