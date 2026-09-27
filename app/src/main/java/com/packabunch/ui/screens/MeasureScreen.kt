package com.packabunch.ui.screens

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.opengl.GLSurfaceView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import com.packabunch.ui.components.PackAppBar
import com.packabunch.ui.components.fd
import com.packabunch.ui.components.fs
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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
import com.packabunch.ar.findActivity
import com.packabunch.ar.ArAvailability
import com.packabunch.ar.ArMeasureController
import com.packabunch.ar.ArSupport
import com.packabunch.ar.EdgeStage
import com.packabunch.ar.TrackingStatus
import com.packabunch.packing.Dimensions
import com.packabunch.packing.MeasurementSource
import com.packabunch.ui.components.Note
import com.packabunch.ui.components.NoteTone
import com.packabunch.ui.components.PackIconButton
import com.packabunch.ui.components.PackIcons
import com.packabunch.ui.components.PackTextButton
import com.packabunch.ui.components.PrimaryButton
import com.packabunch.ui.components.ScreenScaffold
import com.packabunch.ui.components.SecondaryButton
import com.packabunch.ui.format.LengthUnit
import com.packabunch.ui.format.formatLengthWithUnit
import com.packabunch.ui.theme.NumeralLarge
import com.packabunch.ui.theme.OnCamera
import com.packabunch.ui.theme.Spacing
import com.packabunch.ui.theme.TextPrimary
import com.packabunch.ui.theme.TextSecondary
import com.packabunch.ui.theme.UiFamily

/**
 * Measure with the camera — `Measure.dc.html`, `MeasureStart.dc.html`, and the recovery
 * states `CameraDenied`, `ArUnavailable`, `ArServicesInstall`, `TrackingLost`.
 *
 * Three edges, measured one at a time, each from two real hit tests against tracked
 * geometry. Nothing here is derived from pixel distance, and nothing is stored without
 * passing through the review screen first.
 *
 * "Type it instead" is on screen at every single moment, including while tracking is lost
 * and including when AR is not available at all. That is not a courtesy — it is the reason
 * this screen is allowed to exist.
 */
@Composable
fun MeasureScreen(
    unit: LengthUnit,
    onMeasured: (Dimensions, MeasurementSource) -> Unit,
    onTypeInstead: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** The space's own name, for "Lost track of the cake box". */
    spaceName: String? = null,
) {
    val context = LocalContext.current
    val activity = context.findActivity()

    var hasCameraPermission by remember {
        mutableStateOf(
            context.checkSelfPermission(Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var support by remember { mutableStateOf<ArSupport>(ArSupport.Checking) }
    var installRequested by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasCameraPermission = granted }

    // ARCore can answer "still checking" for a moment, so this re-asks rather than
    // treating an indeterminate answer as a no.
    LaunchedEffect(support, hasCameraPermission) {
        if (support is ArSupport.Checking) {
            kotlinx.coroutines.delay(200)
            support = ArAvailability.check(context)
        }
    }

    when {
        !hasCameraPermission -> CameraDenied(
            onAllow = { permissionLauncher.launch(Manifest.permission.CAMERA) },
            onTypeInstead = onTypeInstead,
            onBack = onBack,
            modifier = modifier,
        )

        support is ArSupport.NotSupported || support is ArSupport.Unknown -> ArUnavailable(
            detail = (support as? ArSupport.Unknown)?.reason,
            onTypeInstead = onTypeInstead,
            onBack = onBack,
            modifier = modifier,
        )

        support is ArSupport.NeedsInstall -> ArNeedsInstall(
            onInstall = {
                activity?.let {
                    ArAvailability.getArCore(it)
                    installRequested = true
                    support = ArSupport.Checking
                }
            },
            onTypeInstead = onTypeInstead,
            onBack = onBack,
            modifier = modifier,
        )

        support is ArSupport.Ready -> ArMeasureSurface(
            unit = unit,
            spaceName = spaceName,
            onMeasured = onMeasured,
            onTypeInstead = onTypeInstead,
            onBack = onBack,
            modifier = modifier,
        )

        else -> Box(modifier.fillMaxSize().background(Color.Black))
    }
}

@Composable
private fun ArMeasureSurface(
    unit: LengthUnit,
    spaceName: String?,
    onMeasured: (Dimensions, MeasurementSource) -> Unit,
    onTypeInstead: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val controller = remember { ArMeasureController(context) }
    val state by controller.state.collectAsStateWithLifecycle()
    var startError by remember { mutableStateOf<String?>(null) }

    val glView = remember {
        GLSurfaceView(context).apply {
            preserveEGLContextOnPause = true
            setEGLContextClientVersion(2)
            setEGLConfigChooser(8, 8, 8, 8, 16, 0)
            setRenderer(controller)
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
            setWillNotDraw(false)
        }
    }

    // The session owns the camera, so it must follow the lifecycle exactly. Holding it open
    // in the background would keep the camera from anything else on the phone.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    val rotation = glView.display?.rotation ?: 0
                    startError = controller.resume(rotation, glView.width.coerceAtLeast(1), glView.height.coerceAtLeast(1))
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

    LaunchedEffect(state.measured) {
        val w = state.measured[EdgeStage.WIDTH]
        val d = state.measured[EdgeStage.DEPTH]
        val h = state.measured[EdgeStage.HEIGHT]
        if (w != null && d != null && h != null) {
            onMeasured(Dimensions(w, d, h), MeasurementSource.CAMERA_ESTIMATE)
        }
    }

    val fatal = state.fatalError ?: startError
    if (fatal != null) {
        ScreenScaffold(modifier) {
            PackAppBar(title = "Measure the space", onBack = onBack)
            Column(Modifier.padding(horizontal = Spacing.gutter)) {
                Note(text = fatal, tone = NoteTone.Problem, icon = PackIcons.Warning)
                Spacer(Modifier.height(12.dp))
                SecondaryButton(text = "Type the measurements instead", onClick = onTypeInstead)
            }
        }
        return
    }

    // The same camera screen as the any-shape scan (`SpaceScan · New`): the picture fills the
    // screen, and every control is a small glass pill that leaves it alone.
    Box(modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { glView }, modifier = Modifier.fillMaxSize())

        MeasurementOverlay(state = state)

        // The length being measured, on the line itself, as the scan puts its W / D / H.
        var viewSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
        val a = state.capturedScreen.getOrNull(0)
        val b = state.capturedScreen.getOrNull(1) ?: state.reticleScreen?.takeIf { state.reticleHasSurface }
        val live = state.liveLengthMm
        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().onSizeChanged { viewSize = it }) {
            if (a != null && b != null && live != null && viewSize.width > 0) {
                com.packabunch.ui.components.AnchoredLabels(
                    listOf(
                        com.packabunch.ui.components.AnchoredLabel(
                            "live", (a.first + b.first) / 2 / viewSize.width, (a.second + b.second) / 2 / viewSize.height,
                            com.packabunch.ui.components.AnchorAlign.Centre,
                        ) {
                            com.packabunch.ui.components.DimensionPill(
                                axis = state.stage.label.first().toString(),
                                value = formatLengthWithUnit(live, unit),
                                highlight = state.stage == EdgeStage.HEIGHT,
                            )
                        },
                    ),
                    Modifier.fillMaxSize(),
                )
            }
        }

        // Top: back, which edge of three, one instruction.
        Column(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 14.fd, vertical = 6.fd),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                com.packabunch.ui.components.GlassLottieButton(com.packabunch.R.raw.icon_back, "Back", onBack, diameter = 34.fd, iconSize = 20.fd)
                Spacer(Modifier.weight(1f))
                com.packabunch.ui.components.ScanStatusPill(
                    lead = spaceName?.takeIf { it.isNotBlank() } ?: "Crate",
                    value = if (state.allEdgesMeasured) "3 of 3 measured"
                    else "${state.stage.label} · ${EdgeStage.entries.indexOf(state.stage) + 1} of 3",
                )
            }
            Spacer(Modifier.height(7.fd))
            com.packabunch.ui.components.ScanGuidancePill(
                text = when {
                    state.status == TrackingStatus.INITIALISING -> "Move the phone slowly so it can find the surface"
                    state.status == TrackingStatus.LOST -> trackingLostAdvice(state.failureReason)
                    !state.reticleHasSurface && state.capturedScreen.size < 2 -> "Point at a surface — there's nothing to measure from here"
                    state.capturedScreen.isEmpty() -> state.stage.instruction
                    state.capturedScreen.size == 1 -> "Now tap the other end"
                    else -> "Happy with that? Confirm it, or undo and measure again"
                },
                working = !state.allEdgesMeasured,
            )
        }

        // Bottom: what is measured so far, then torch · the one action · type it.
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(start = 12.fd, end = 12.fd, bottom = 14.fd),
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                MeasuredCard(state, unit)
                Spacer(Modifier.weight(1f))
                if (state.capturedScreen.isNotEmpty()) {
                    com.packabunch.ui.components.GlassPill(
                        Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .clickable(onClick = controller::undoLastPoint),
                        horizontal = 12.fd, vertical = 8.fd, gap = 6.fd,
                    ) {
                        Icon(PackIcons.Undo, null, tint = Color.White, modifier = Modifier.size(14.fd))
                        Text("Undo point", color = Color.White, fontFamily = UiFamily, fontWeight = FontWeight.SemiBold, fontSize = 11.fs)
                    }
                }
            }
            Spacer(Modifier.height(9.fd))
            Row(Modifier.fillMaxWidth().padding(horizontal = 2.fd), verticalAlignment = Alignment.CenterVertically) {
                com.packabunch.ui.components.TorchButton(state.torchOn, { controller.setTorch(!state.torchOn) })
                Spacer(Modifier.weight(1f))
                com.packabunch.ui.components.ScanDoneButton(
                    text = if (state.capturedScreen.size >= 2) "Confirm ${state.stage.label.lowercase()}" else "Add point",
                    enabled = state.status == TrackingStatus.TRACKING &&
                        (state.reticleHasSurface || state.capturedScreen.size >= 2),
                    onClick = {
                        if (state.capturedScreen.size >= 2) controller.confirmCurrentEdge()
                        else controller.capturePoint()
                    },
                )
                Spacer(Modifier.weight(1f))
                com.packabunch.ui.components.GlassLottieButton(com.packabunch.R.raw.icon_edit, "Type the measurements instead", onTypeInstead, iconSize = 24.fd)
            }
        }

        // Lost for more than a moment: say so plainly, keep what is measured, offer both routes.
        val (lostShowing, dismissLost) = rememberLostTrack(state.status == TrackingStatus.LOST)
        if (lostShowing) {
            val edge = state.stage.label.lowercase()
            LostTrackPopup(
                // Measured edge by edge, so it is a box-shaped thing: a crate unless it has a name.
                what = if (spaceName.isNullOrBlank()) "the crate" else spaceNoun(spaceName, standingInside = false),
                reason = state.failureReason,
                saved = savedLengths(
                    com.packabunch.ar.EdgeStage.entries.mapNotNull { e ->
                        state.measured[e]?.let { e.label to formatLengthWithUnit(it, unit) }
                    },
                ),
                retry = "Try the $edge again",
                onRetry = { controller.restartCurrentEdge(); dismissLost() },
                typeInstead = "Type the $edge",
                onTypeInstead = { dismissLost(); onTypeInstead() },
                onDismiss = dismissLost,
            )
        }
    }
}

/**
 * The three edges so far, in the glass card the space scan uses for "Mapped": each length once
 * confirmed, the one being measured in amber, the rest waiting.
 */
@Composable
private fun MeasuredCard(state: com.packabunch.ar.ArMeasureState, unit: LengthUnit) {
    Column(
        Modifier
            .background(com.packabunch.ui.theme.ScanGlass, RoundedCornerShape(16.fd))
            .padding(horizontal = 11.fd, vertical = 9.fd),
        verticalArrangement = Arrangement.spacedBy(4.fd),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("MEASURED", color = Color.White.copy(alpha = 0.75f), fontFamily = UiFamily, fontWeight = FontWeight.Bold, fontSize = 8.fs, letterSpacing = 0.6f.fs)
            Spacer(Modifier.width(10.fd))
            com.packabunch.ui.components.RollingText("${state.measured.size}/3", Color.White.copy(alpha = 0.8f), com.packabunch.ui.theme.NumericFamily, FontWeight.Normal, 9.fs)
        }
        EdgeStage.entries.forEach { edge ->
            val value = state.measured[edge]
            val current = edge == state.stage && !state.allEdgesMeasured
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.fd)) {
                Text(
                    edge.label.first().toString(),
                    color = if (current) OnCamera else Color.White.copy(alpha = 0.6f),
                    fontFamily = com.packabunch.ui.theme.NumericFamily,
                    fontSize = 9.5f.fs,
                )
                Text(
                    value?.let { formatLengthWithUnit(it, unit) } ?: if (current) "measuring" else "—",
                    color = if (value != null) Color.White else if (current) OnCamera else Color.White.copy(alpha = 0.5f),
                    fontFamily = if (value != null) com.packabunch.ui.theme.NumericFamily else UiFamily,
                    fontSize = 10.5f.fs,
                )
            }
        }
    }
}

/** Points, the line between them, and the reticle — drawn over the camera feed. */
@Composable
private fun MeasurementOverlay(state: com.packabunch.ar.ArMeasureState) {
    Canvas(Modifier.fillMaxSize()) {
        val points = state.capturedScreen.map { Offset(it.first, it.second) }

        if (points.size >= 2) {
            drawLine(
                color = OnCamera,
                start = points[0],
                end = points[1],
                strokeWidth = 6f,
            )
        } else if (points.size == 1 && state.reticleScreen != null && state.reticleHasSurface) {
            drawLine(
                color = OnCamera.copy(alpha = 0.7f),
                start = points[0],
                end = Offset(state.reticleScreen.first, state.reticleScreen.second),
                strokeWidth = 4f,
            )
        }

        points.forEach { point ->
            drawCircle(Color.White, radius = 16f, center = point)
            drawCircle(OnCamera, radius = 11f, center = point)
        }

        // The reticle turns solid only when there is genuinely something under it. That is
        // the signal that a tap will produce a real point rather than nothing.
        state.reticleScreen?.let { (x, y) ->
            val centre = Offset(x, y)
            val colour = if (state.reticleHasSurface) OnCamera else Color.White.copy(alpha = 0.5f)
            drawCircle(
                color = colour,
                radius = 26f,
                center = centre,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f),
            )
            if (state.reticleHasSurface) {
                drawCircle(colour, radius = 5f, center = centre)
            }
        }
    }
}

private fun trackingLostAdvice(reason: com.google.ar.core.TrackingFailureReason?): String =
    when (reason) {
        com.google.ar.core.TrackingFailureReason.EXCESSIVE_MOTION ->
            "Slow down, it lost track of the room."
        com.google.ar.core.TrackingFailureReason.INSUFFICIENT_LIGHT ->
            "Too dark to see the surface. More light, or type it instead."
        com.google.ar.core.TrackingFailureReason.INSUFFICIENT_FEATURES ->
            "This surface is too plain to track. Aim at an edge or something with texture."
        com.google.ar.core.TrackingFailureReason.CAMERA_UNAVAILABLE ->
            "Something else is using the camera."
        else -> "Lost track. Move the phone slowly until it picks the room up again."
    }

// -- recovery states -------------------------------------------------------------------------

@Composable
private fun CameraDenied(
    onAllow: () -> Unit,
    onTypeInstead: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) = RecoveryState(
    modifier = modifier,
    title = "Camera access is off",
    body = "Measuring with the camera needs it. Everything else in the app works without it, " +
        "and typing measurements always works.",
    primary = "Allow the camera" to onAllow,
    secondary = "Type the measurements" to onTypeInstead,
    onBack = onBack,
)

@Composable
private fun ArUnavailable(
    detail: String?,
    onTypeInstead: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) = RecoveryState(
    modifier = modifier,
    title = "This phone can't measure with the camera",
    body = "Camera measuring needs AR support this phone doesn't have. Nothing else changes " +
        "typed measurements work exactly the same, and so does the rest of the app." +
        (detail?.let { "\n\n($it)" } ?: ""),
    primary = "Type the measurements" to onTypeInstead,
    secondary = null,
    onBack = onBack,
)

@Composable
private fun ArNeedsInstall(
    onInstall: () -> Unit,
    onTypeInstead: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) = RecoveryState(
    modifier = modifier,
    title = "One thing to install first",
    body = "Camera measuring uses Google Play Services for AR. Your phone supports it, it " +
        "just isn't installed or is out of date.",
    primary = "Install it" to onInstall,
    secondary = "Type the measurements" to onTypeInstead,
    onBack = onBack,
)

@Composable
private fun RecoveryState(
    title: String,
    body: String,
    primary: Pair<String, () -> Unit>,
    secondary: Pair<String, () -> Unit>?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ScreenScaffold(modifier) {
        com.packabunch.ui.components.PackAppBar(title = "Measure", onBack = onBack)

        Spacer(Modifier.height(Spacing.xl))

        Column(Modifier.padding(horizontal = Spacing.gutter)) {
            com.packabunch.ui.components.IconTile(
                icon = PackIcons.Camera,
                tint = Color(0xFF7C4223),
                background = Color(0xFFF5E4D6),
                size = 56.dp,
                iconSize = 26.dp,
            )
            Spacer(Modifier.height(Spacing.base))
            Text(
                text = title,
                color = TextPrimary,
                fontFamily = UiFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 25.sp,
                lineHeight = 32.sp,
                letterSpacing = (-0.6).sp,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = body,
                color = TextSecondary,
                fontFamily = UiFamily,
                fontSize = 15.sp,
                lineHeight = 23.sp,
            )
        }

        Spacer(Modifier.size(0.dp))
        Column(
            Modifier
                .padding(horizontal = Spacing.gutter)
                .padding(top = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PrimaryButton(text = primary.first, onClick = primary.second)
            secondary?.let { SecondaryButton(text = it.first, onClick = it.second) }
        }
    }
}
