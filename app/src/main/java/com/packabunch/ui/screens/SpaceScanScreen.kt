package com.packabunch.ui.screens

import android.opengl.GLSurfaceView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.ar.core.TrackingFailureReason
import com.packabunch.ar.SpaceAnchors
import com.packabunch.ar.SpaceScanController
import com.packabunch.ar.SpaceScanUi
import com.packabunch.ar.TrackingStatus
import com.packabunch.packing.ScannedSpace
import com.packabunch.packing.SpaceBox
import com.packabunch.packing.SpaceFace
import com.packabunch.ui.components.AnchorAlign
import com.packabunch.ui.components.AnchoredLabel
import com.packabunch.ui.components.AnchoredLabels
import com.packabunch.ui.components.DimensionPill
import com.packabunch.ui.components.GlassLottieButton
import com.packabunch.ui.components.RollingText
import com.packabunch.ui.components.TorchButton
import com.packabunch.ui.components.scanPopIn
import com.packabunch.ui.components.GlassPill
import com.packabunch.ui.components.Note
import com.packabunch.ui.components.PackAppBar
import com.packabunch.ui.components.PackIcons
import com.packabunch.ui.components.ScanDoneButton
import com.packabunch.ui.components.ScanGuidancePill
import com.packabunch.ui.components.ScanStatusPill
import com.packabunch.ui.components.ScreenScaffold
import com.packabunch.ui.components.SecondaryButton
import com.packabunch.ui.components.fd
import com.packabunch.ui.components.fs
import com.packabunch.ui.format.LengthUnit
import com.packabunch.ui.format.formatLength
import com.packabunch.ui.format.formatLengthWithUnit
import com.packabunch.ui.theme.NumericFamily
import com.packabunch.ui.theme.OnCamera
import com.packabunch.ui.theme.ScanGlass
import com.packabunch.ui.theme.UiFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Sweeping a space — the Figma frame `SpaceScan · New`.
 *
 * The space is drawn on the camera as the box it is being measured as: white where its sides
 * have been seen, amber and dotted where one has not, the opening dashed. Its width, depth and
 * height sit on the edges they measure; "72 % mapped" is the share of its sides actually
 * seen, not a timer.
 *
 * "Use this space" unlocks at the same bar the space scan always had ([com.packabunch.ar.SCAN_DONE_THRESHOLD]):
 * below it too much of the space would be guesswork. A typed path (the pencil) is always there.
 */
@Composable
fun SpaceScanScreen(
    onScanned: (ScannedSpace) -> Unit,
    onTypeInstead: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** Shown in the counter — "Car boot 72 % mapped". */
    spaceName: String? = null,
    unit: LengthUnit = LengthUnit.CENTIMETRES,
) {
    DepthCaptureGate(onTypeInstead, onBack) {
        val context = LocalContext.current
        val density = LocalDensity.current.density
        val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
        val controller = remember { SpaceScanController(context, density) }
        val ui by controller.ui.collectAsStateWithLifecycle()
        val anchors by controller.anchors.collectAsStateWithLifecycle()
        val scope = rememberCoroutineScope()
        var error by remember { mutableStateOf<String?>(null) }
        var finishing by remember { mutableStateOf(false) }
        val view = remember {
            GLSurfaceView(context).apply {
                preserveEGLContextOnPause = true
                setEGLContextClientVersion(2)
                setEGLConfigChooser(8, 8, 8, 8, 16, 0)
                setRenderer(controller)
                renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
            }
        }
        DisposableEffect(owner) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> {
                        error = controller.resume(view.display?.rotation ?: 0, view.width.coerceAtLeast(1), view.height.coerceAtLeast(1))
                        if (error == null) view.onResume()
                    }
                    Lifecycle.Event.ON_PAUSE -> { view.onPause(); controller.pause() }
                    else -> Unit
                }
            }
            owner.lifecycle.addObserver(observer)
            onDispose { owner.lifecycle.removeObserver(observer); view.onPause(); controller.release() }
        }

        val fatal = error ?: ui.fatalError
        if (fatal != null) {
            ScreenScaffold {
                PackAppBar(title = "Scan the space", onBack = onBack)
                Note(text = fatal)
                SecondaryButton(text = "Type the measurements instead", onClick = onTypeInstead)
            }
        } else {
            SpaceScanOverlay(
                ui = ui,
                anchors = anchors,
                unit = unit,
                spaceName = spaceName,
                onTorch = { controller.setTorch(!ui.torchOn) },
                onTypeInstead = onTypeInstead,
                onBack = onBack,
                onUse = {
                    if (!finishing) {
                        finishing = true
                        scope.launch {
                            val space = withContext(Dispatchers.Default) { controller.buildScannedSpace() }
                            finishing = false
                            if (space != null) onScanned(space)
                        }
                    }
                },
                modifier = modifier,
                cameraPreview = { AndroidView(factory = { view }, modifier = Modifier.fillMaxSize()) },
            )
        }
    }
}

/** Everything drawn over the camera, stateless so it can be previewed without a phone. */
@Composable
fun SpaceScanOverlay(
    ui: SpaceScanUi,
    anchors: SpaceAnchors,
    unit: LengthUnit,
    spaceName: String?,
    onTorch: () -> Unit,
    onTypeInstead: () -> Unit,
    onBack: () -> Unit,
    onUse: () -> Unit,
    modifier: Modifier = Modifier,
    cameraPreview: @Composable () -> Unit = {},
) {
    val box = ui.box
    // Felt, not just seen: the moment the space is mapped well enough to use.
    val hapticView = androidx.compose.ui.platform.LocalView.current
    var wasReady by remember { mutableStateOf(ui.canFinish) }
    androidx.compose.runtime.LaunchedEffect(ui.canFinish) {
        if (ui.canFinish && !wasReady) hapticView.performHapticFeedback(
            if (android.os.Build.VERSION.SDK_INT >= 30) android.view.HapticFeedbackConstants.CONFIRM
            else android.view.HapticFeedbackConstants.VIRTUAL_KEY,
        )
        wasReady = ui.canFinish
    }
    Box(modifier.fillMaxSize().background(Color.Black)) {
        cameraPreview()

        if (box != null) {
            val d = box.dimensions
            val labels = buildList {
                anchors.opening?.let { (x, y) ->
                    val opening = box.opening
                    if (opening != null) add(AnchoredLabel("opening", x, y, AnchorAlign.Centre) {
                        GlassPill(Modifier.scanPopIn(260), horizontal = 8.fd, vertical = 4.fd, gap = 5.fd) {
                            Text("OPENING", color = Color.White.copy(alpha = 0.75f), fontFamily = UiFamily, fontWeight = FontWeight.Bold, fontSize = 8.5f.fs, letterSpacing = 0.5f.fs)
                            RollingText(
                                "${formatLength(opening.widthMm, unit)} × ${formatLengthWithUnit(opening.heightMm, unit)}",
                                Color.White, NumericFamily, FontWeight.Medium, 10.5f.fs,
                            )
                        }
                    })
                }
                anchors.height?.let { (x, y) -> add(AnchoredLabel("h", x, y, AnchorAlign.Centre) { DimensionPill("H", formatLengthWithUnit(d.heightMm, unit), highlight = true, appearDelayMillis = 180) }) }
                anchors.depth?.let { (x, y) -> add(AnchoredLabel("d", x, y, AnchorAlign.Centre) { DimensionPill("D", formatLengthWithUnit(d.depthMm, unit), appearDelayMillis = 90) }) }
                anchors.width?.let { (x, y) -> add(AnchoredLabel("w", x, y, AnchorAlign.Centre) { DimensionPill("W", formatLengthWithUnit(d.widthMm, unit)) }) }
            }
            AnchoredLabels(labels, Modifier.fillMaxSize())
        }

        Column(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 14.fd, vertical = 6.fd),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                GlassLottieButton(com.packabunch.R.raw.icon_back, "Back", onBack, diameter = 34.fd, iconSize = 20.fd)
                Spacer(Modifier.weight(1f))
                ScanStatusPill(lead = spaceName ?: "Space", value = "${ui.mappedPercent}% mapped")
            }
            Spacer(Modifier.height(7.fd))
            val (guide, working) = spaceGuidance(ui)
            ScanGuidancePill(guide, working)
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(start = 12.fd, end = 12.fd, bottom = 14.fd),
        ) {
            MappedCard(ui)
            Spacer(Modifier.height(9.fd))
            Row(Modifier.fillMaxWidth().padding(horizontal = 2.fd), verticalAlignment = Alignment.CenterVertically) {
                TorchButton(ui.torchOn, onTorch)
                Spacer(Modifier.weight(1f))
                ScanDoneButton("Use this space", enabled = ui.canFinish, onClick = onUse)
                Spacer(Modifier.weight(1f))
                GlassLottieButton(com.packabunch.R.raw.icon_edit, "Type the measurements instead", onTypeInstead, iconSize = 24.fd)
            }
        }
    }
}

/**
 * One instruction. When a side is missing it says which, in the direction the person has to
 * move — "Sweep right", not "right side 34 %".
 */
private fun spaceGuidance(ui: SpaceScanUi): Pair<String, Boolean> {
    if (ui.status != TrackingStatus.TRACKING) {
        return when (ui.failureReason) {
            TrackingFailureReason.INSUFFICIENT_LIGHT -> "Too dark to see inside — try the torch" to true
            TrackingFailureReason.EXCESSIVE_MOTION -> "A little slower — the camera lost its place" to true
            TrackingFailureReason.INSUFFICIENT_FEATURES -> "Point at something with more detail" to true
            else -> "Move the phone slowly to get started" to true
        }
    }
    if (!ui.floorFound) return "Point at the floor of the space and hold for a moment" to true
    val box = ui.box ?: return "Now sweep slowly round the sides" to true
    if (ui.canFinish && box.weakestFace == null) return "Mapped — check the sizes, then use this space" to false
    return when (box.weakestFace) {
        SpaceFace.LEFT -> "Sweep left — the left side isn't mapped yet"
        SpaceFace.RIGHT -> "Sweep right — the right side isn't mapped yet"
        SpaceFace.BACK -> "Point at the back — it's still thin"
        SpaceFace.FLOOR -> "Tilt down — the floor isn't mapped yet"
        SpaceFace.FRONT -> "Turn round — the wall behind you isn't mapped"
        SpaceFace.TOP, null -> if (ui.canFinish) "Mapped — check the sizes, then use this space" else "Keep sweeping slowly"
    } to !ui.canFinish
}

/** `Live preview` in SpaceScan · New: the space in 3D, seen sides filled, the unseen one in amber. */
@Composable
private fun MappedCard(ui: SpaceScanUi) {
    Column(
        Modifier
            .size(width = 104.fd, height = 98.fd)
            .background(ScanGlass, RoundedCornerShape(16.fd))
            .padding(horizontal = 10.fd, vertical = 9.fd),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("MAPPED", color = Color.White.copy(alpha = 0.75f), fontFamily = UiFamily, fontWeight = FontWeight.Bold, fontSize = 8.fs, letterSpacing = 0.6f.fs)
            Spacer(Modifier.weight(1f))
            RollingText("${ui.mappedPercent}%", Color.White.copy(alpha = 0.8f), NumericFamily, FontWeight.Normal, 9.fs)
        }
        val box = ui.box
        // Sides fade in as they are mapped rather than popping.
        val seenLeft by androidx.compose.animation.core.animateFloatAsState(if ((box?.coverage?.get(SpaceFace.LEFT) ?: 0f) >= SpaceBox.WELL_SEEN) 1f else 0f, androidx.compose.animation.core.tween(com.packabunch.ui.motion.Motion.MEDIUM_MS), label = "left")
        val seenRight by androidx.compose.animation.core.animateFloatAsState(if ((box?.coverage?.get(SpaceFace.RIGHT) ?: 0f) >= SpaceBox.WELL_SEEN) 1f else 0f, androidx.compose.animation.core.tween(com.packabunch.ui.motion.Motion.MEDIUM_MS), label = "right")
        val seenBack by androidx.compose.animation.core.animateFloatAsState(if ((box?.coverage?.get(SpaceFace.BACK) ?: 0f) >= SpaceBox.WELL_SEEN) 1f else 0f, androidx.compose.animation.core.tween(com.packabunch.ui.motion.Motion.MEDIUM_MS), label = "back")
        val floorFill by androidx.compose.animation.core.animateFloatAsState(box?.coverage?.get(SpaceFace.FLOOR) ?: 0f, androidx.compose.animation.core.tween(com.packabunch.ui.motion.Motion.MEDIUM_MS), label = "floor")
        Canvas(Modifier.fillMaxWidth().weight(1f)) {
            if (box == null) return@Canvas
            // A fixed three-quarter view from front-left, scaled to fit whatever proportions
            // the space has.
            val w = box.widthMm; val d = box.depthMm; val h = box.heightMm
            val c30 = 0.866f; val s30 = 0.5f
            fun iso(x: Float, y: Float, z: Float) = Offset((x + y * 0.55f) * c30, (-y * 0.55f) * s30 - z)
            val pts = listOf(iso(0f, 0f, 0f), iso(w, 0f, 0f), iso(w, d, 0f), iso(0f, d, 0f), iso(0f, 0f, h), iso(w, 0f, h), iso(w, d, h), iso(0f, d, h))
            val minX = pts.minOf { it.x }; val maxX = pts.maxOf { it.x }; val minY = pts.minOf { it.y }; val maxY = pts.maxOf { it.y }
            val scale = minOf(size.width / (maxX - minX), size.height / (maxY - minY)) * 0.9f
            val ox = size.width / 2 - (minX + maxX) / 2 * scale; val oy = size.height / 2 - (minY + maxY) / 2 * scale
            fun p(x: Float, y: Float, z: Float) = iso(x, y, z).let { Offset(it.x * scale + ox, it.y * scale + oy) }
            fun quad(a: Offset, b: Offset, c: Offset, e: Offset) = Path().apply { moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c.x, c.y); lineTo(e.x, e.y); close() }
            val thin = Stroke(0.6f.fd.toPx()); val line = Stroke(0.8f.fd.toPx())
            fun seen(f: SpaceFace) = (box.coverage[f] ?: 0f) >= SpaceBox.WELL_SEEN
            // Floor, in strips that fill in with coverage, as the frame shows it.
            val floor = quad(p(0f, 0f, 0f), p(w, 0f, 0f), p(w, d, 0f), p(0f, d, 0f))
            drawPath(floor, Color.White.copy(alpha = 0.22f * (floorFill + 0.2f).coerceAtMost(1f)))
            drawPath(floor, Color.White.copy(alpha = 0.35f), style = thin)
            if (seenBack > 0f) {
                val back = quad(p(0f, d, 0f), p(w, d, 0f), p(w, d, h), p(0f, d, h))
                drawPath(back, Color.White.copy(alpha = 0.30f * seenBack)); drawPath(back, Color.White.copy(alpha = 0.8f * seenBack), style = line)
            }
            if (seenLeft > 0f) {
                val left = quad(p(0f, 0f, 0f), p(0f, d, 0f), p(0f, d, h), p(0f, 0f, h))
                drawPath(left, Color.White.copy(alpha = 0.18f * seenLeft)); drawPath(left, Color.White.copy(alpha = 0.8f * seenLeft), style = line)
            }
            if (seenRight > 0f) {
                val right = quad(p(w, 0f, 0f), p(w, d, 0f), p(w, d, h), p(w, 0f, h))
                drawPath(right, Color.White.copy(alpha = 0.12f * seenRight)); drawPath(right, Color.White.copy(alpha = 0.8f * seenRight), style = line)
            }
            // The whole volume, dashed; then the unseen side in amber on top.
            val dash = PathEffect.dashPathEffect(floatArrayOf(2f.fd.toPx(), 2f.fd.toPx()))
            val volume = Path().apply {
                val top = listOf(p(0f, 0f, h), p(w, 0f, h), p(w, d, h), p(0f, d, h))
                moveTo(top[0].x, top[0].y); top.drop(1).forEach { lineTo(it.x, it.y) }; close()
                listOf(0f to 0f, w to 0f, w to d, 0f to d).forEach { (x, y) -> val a = p(x, y, 0f); val b = p(x, y, h); moveTo(a.x, a.y); lineTo(b.x, b.y) }
            }
            drawPath(volume, Color.White.copy(alpha = 0.5f), style = Stroke(0.7f.fd.toPx(), pathEffect = dash))
            val weak = box.weakestFace
            val unseen = when (weak) {
                SpaceFace.LEFT -> quad(p(0f, 0f, 0f), p(0f, d, 0f), p(0f, d, h), p(0f, 0f, h))
                SpaceFace.RIGHT -> quad(p(w, 0f, 0f), p(w, d, 0f), p(w, d, h), p(w, 0f, h))
                SpaceFace.BACK -> quad(p(0f, d, 0f), p(w, d, 0f), p(w, d, h), p(0f, d, h))
                SpaceFace.FLOOR -> floor
                SpaceFace.FRONT -> quad(p(0f, 0f, 0f), p(w, 0f, 0f), p(w, 0f, h), p(0f, 0f, h))
                else -> null
            }
            if (unseen != null) drawPath(unseen, OnCamera, style = Stroke(1f.fd.toPx(), pathEffect = dash))
        }
    }
}
