package com.packabunch.ui.screens

import android.opengl.GLSurfaceView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.packabunch.ar.ArScanController
import com.packabunch.ar.SCAN_DONE_THRESHOLD
import com.packabunch.ar.ScanRegion
import com.packabunch.ar.TrackingStatus
import com.packabunch.packing.ScannedSpace
import com.packabunch.ui.components.Note
import com.packabunch.ui.components.NoteTone
import com.packabunch.ui.components.PackIconButton
import com.packabunch.ui.components.PackIcons
import com.packabunch.ui.components.PackTextButton
import com.packabunch.ui.components.PrimaryButton
import com.packabunch.ui.components.SecondaryButton
import com.packabunch.ui.motion.Motion
import com.packabunch.ui.theme.NumeralLarge
import com.packabunch.ui.theme.OnCamera
import com.packabunch.ui.theme.Spacing
import com.packabunch.ui.theme.Success
import com.packabunch.ui.theme.TextPrimary
import com.packabunch.ui.theme.TextSecondary
import com.packabunch.ui.theme.TextTertiary
import com.packabunch.ui.theme.UiFamily

/**
 * Sweeping a space — `design/artboards/SpaceScan.dc.html`.
 *
 * The progress figure is real coverage of the occupancy grid, not a timer dressed up as
 * one. Per-region readouts exist so the instruction can be specific — "right side still
 * thin, sweep there" is something you can act on; a bare percentage is not.
 *
 * Done is locked until 80% because a plan built on a mostly-unmapped space would be mostly
 * guesswork, and the screen says why rather than just greying the button out.
 */
@Composable
fun SpaceScanScreen(
    onScanned: (ScannedSpace) -> Unit,
    onTypeInstead: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val controller = remember { ArScanController(context) }
    val state by controller.state.collectAsStateWithLifecycle()
    var startError by remember { mutableStateOf<String?>(null) }

    val glView = remember {
        GLSurfaceView(context).apply {
            preserveEGLContextOnPause = true
            setEGLContextClientVersion(2)
            setEGLConfigChooser(8, 8, 8, 8, 16, 0)
            setRenderer(controller)
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    startError = controller.resume(
                        glView.display?.rotation ?: 0,
                        glView.width.coerceAtLeast(1),
                        glView.height.coerceAtLeast(1),
                    )
                    if (startError == null) glView.onResume()
                }
                Lifecycle.Event.ON_PAUSE -> {
                    glView.onPause()
                    controller.pause()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            controller.release()
        }
    }

    Box(modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { glView }, modifier = Modifier.fillMaxSize())

        Column(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PackIconButton(
                    icon = PackIcons.Back,
                    contentDescription = "Back",
                    onClick = onBack,
                    tint = Color.White,
                    background = Color(0x55000000),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = formatElapsed(state.elapsedSeconds),
                    style = NumeralLarge.copy(fontSize = 15.sp),
                    color = Color.White,
                    modifier = Modifier
                        .background(Color(0x55000000), RoundedCornerShape(999.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }

            Spacer(Modifier.height(12.dp))

            CoverageBanner(state.coveragePercent, state.thinRegions)
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.White, RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = Spacing.gutter, vertical = 18.dp),
        ) {
            val fatal = state.fatalError ?: startError
            if (fatal != null) {
                Note(text = fatal, tone = NoteTone.Problem, icon = PackIcons.Warning)
                Spacer(Modifier.height(12.dp))
                SecondaryButton(text = "Type the measurements instead", onClick = onTypeInstead)
                return@Column
            }

            Text(
                text = "Sweep the whole space",
                color = TextPrimary,
                fontFamily = UiFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = when {
                    state.status == TrackingStatus.INITIALISING ->
                        "Hold still for a moment while it finds the room."
                    state.status == TrackingStatus.LOST ->
                        "Lost track — move the phone slowly until it picks the room up again."
                    else ->
                        "Move slowly along one side, across the back, then the other side. " +
                            "Green means we've got it."
                },
                color = TextSecondary,
                fontFamily = UiFamily,
                fontSize = 15.sp,
                lineHeight = 23.sp,
            )

            Spacer(Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ScanRegion.entries.forEach { region ->
                    RegionPill(
                        region = region,
                        coverage = state.regionCoverage[region] ?: 0f,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            PrimaryButton(
                text = "Done",
                enabled = state.canFinish,
                onClick = { controller.buildScannedSpace()?.let(onScanned) },
            )

            if (!state.canFinish) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Done unlocks at ${(SCAN_DONE_THRESHOLD * 100).toInt()}%. " +
                        "Below that, too much of the space would be guesswork.",
                    color = TextTertiary,
                    fontFamily = UiFamily,
                    fontSize = 12.5f.sp,
                    lineHeight = 18.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                PackTextButton(text = "Type it instead", onClick = onTypeInstead)
            }
        }
    }
}

@Composable
private fun CoverageBanner(percent: Int, thin: List<ScanRegion>) {
    Column(
        Modifier
            .background(Color(0x99000000), RoundedCornerShape(20.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "$percent% mapped",
                style = NumeralLarge.copy(fontSize = 18.sp),
                color = Color.White,
            )
        }
        if (thin.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${thin.first().label} still thin — sweep there",
                color = OnCamera,
                fontFamily = UiFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.5f.sp,
            )
        }
    }
}

@Composable
private fun RegionPill(region: ScanRegion, coverage: Float, modifier: Modifier = Modifier) {
    val mapped = coverage >= 0.55f
    val progress by animateFloatAsState(
        targetValue = coverage.coerceIn(0f, 1f),
        animationSpec = Motion.standardTween(),
        label = "regionCoverage",
    )

    Column(
        modifier = modifier
            .background(
                if (mapped) Color(0xFFE1EEE7) else Color(0xFFFAEEDA),
                RoundedCornerShape(14.dp),
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (mapped) PackIcons.Check else PackIcons.Warning,
                contentDescription = null,
                tint = if (mapped) Success else Color(0xFFB4761A),
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.size(5.dp))
            Text(
                text = region.label,
                color = TextPrimary,
                fontFamily = UiFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 11.5f.sp,
            )
        }
        Spacer(Modifier.height(5.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(Color(0x22000000), RoundedCornerShape(999.dp)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(progress)
                    .height(4.dp)
                    .background(
                        if (mapped) Success else Color(0xFFB4761A),
                        RoundedCornerShape(999.dp),
                    ),
            )
        }
    }
}

private fun formatElapsed(seconds: Int): String =
    "%d:%02d".format(seconds / 60, seconds % 60)
