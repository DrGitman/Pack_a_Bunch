package com.packabunch.ui.screens

import android.opengl.GLSurfaceView
import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.animation.togetherWith
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.ar.core.TrackingFailureReason
import com.packabunch.ar.ItemScanController
import com.packabunch.ar.ItemScanUi
import com.packabunch.ar.ScanAnchor
import com.packabunch.ar.ScanItem
import com.packabunch.ar.ScannedItemResult
import com.packabunch.ar.TrackingStatus
import com.packabunch.packing.AngleHint
import com.packabunch.packing.CannotMeasureReason
import com.packabunch.packing.FittedObject
import com.packabunch.packing.ItemScanState
import com.packabunch.packing.ShapeFamily
import com.packabunch.ui.components.AnchorAlign
import com.packabunch.ui.components.AnchoredLabel
import com.packabunch.ui.components.AnchoredLabels
import com.packabunch.ui.components.DimensionPill
import com.packabunch.ui.components.GlassLottieButton
import com.packabunch.ui.components.RollingText
import com.packabunch.ui.components.TorchButton
import com.packabunch.ui.components.Note
import com.packabunch.ui.components.ObjectTag
import com.packabunch.ui.components.PackAppBar
import com.packabunch.ui.components.PackIcons
import com.packabunch.ui.components.ProgressRing
import com.packabunch.ui.components.ScanBadge
import com.packabunch.ui.components.ScanDoneButton
import com.packabunch.ui.components.ScanGuidancePill
import com.packabunch.ui.components.ScanStatusPill
import com.packabunch.ui.components.ScreenScaffold
import com.packabunch.ui.components.SecondaryButton
import com.packabunch.ui.components.fd
import com.packabunch.ui.components.fs
import com.packabunch.ui.format.LengthUnit
import com.packabunch.ui.format.formatLengthWithUnit
import com.packabunch.ui.theme.NumericFamily
import com.packabunch.ui.theme.ScanGlass
import com.packabunch.ui.theme.ScanGlassStrong
import com.packabunch.ui.theme.UiFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.sin

/**
 * Scanning items: point the phone at things on a table and each one is outlined, named and
 * measured where it stands. Built to the Figma frames `ItemScan · New` (one or two objects),
 * `MultiScan · New` (three or more) and `ItemScan · Outline states`.
 *
 * The camera fills the screen. Controls are small glass pills that leave the picture alone;
 * the outlines themselves are drawn by the AR renderer ([com.packabunch.ar.GlowOutlineRenderer])
 * so they stay attached to the objects as the phone moves. This screen only places the words.
 *
 * A typed path sits beside the camera at equal prominence (the pencil), as every camera path
 * must. Tapping a tag that says "type it" goes the same way.
 */
@Composable
fun ItemScanScreen(
    unit: LengthUnit,
    /** Objects this pack can still take. Past it the scan stops starting new ones. */
    maxItems: Int,
    /** The plan's piece limit for the "14/20" ring, or null on a plan without one. */
    planLimit: Int?,
    /** Pieces already in the pack, so the ring counts the whole pack rather than this scan. */
    alreadyInPack: Int,
    onDone: (List<ScannedItemResult>) -> Unit,
    onManual: () -> Unit,
    onBack: () -> Unit,
) {
    DepthCaptureGate(onManual, onBack) {
        val context = LocalContext.current
        val density = LocalDensity.current.density
        val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
        val controller = remember { ItemScanController(context, density, maxItems.coerceAtLeast(0)) }
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
                PackAppBar(title = "Scan items", onBack = onBack)
                Note(text = fatal)
                SecondaryButton(text = "Type measurements instead", onClick = onManual)
            }
        } else {
            ItemScanOverlay(
                ui = ui,
                anchors = anchors,
                unit = unit,
                planLimit = planLimit,
                alreadyInPack = alreadyInPack,
                onTorch = { controller.setTorch(!ui.torchOn) },
                onRemove = controller::remove,
                onManual = onManual,
                onBack = onBack,
                onDone = {
                    if (!finishing) {
                        finishing = true
                        scope.launch {
                            val results = withContext(Dispatchers.Default) { controller.results() }
                            onDone(results)
                            finishing = false
                        }
                    }
                },
                cameraPreview = { AndroidView(factory = { view }, modifier = Modifier.fillMaxSize()) },
            )
        }
    }
}

/**
 * Everything drawn over the camera, stateless so it can be previewed without a phone.
 * Switches to the MultiScan layout — compact tags and a summary bar instead of the preview
 * card — once three or more objects are in play, exactly as the two Figma frames do.
 */
@Composable
fun ItemScanOverlay(
    ui: ItemScanUi,
    anchors: List<ScanAnchor>,
    unit: LengthUnit,
    planLimit: Int?,
    alreadyInPack: Int,
    onTorch: () -> Unit,
    onRemove: (Int) -> Unit,
    onManual: () -> Unit,
    onBack: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    cameraPreview: @Composable () -> Unit = {},
) {
    val items = ui.items
    val multi = items.size >= 3
    val measured = ui.measuredCount
    var focused by remember { mutableStateOf<Int?>(null) }
    val glass = if (multi) ScanGlassStrong else ScanGlass
    val glassColor by androidx.compose.animation.animateColorAsState(glass, label = "glass")

    // A tap you feel each time something finishes measuring — you are usually looking at the
    // object, not the screen, when it happens.
    val view = androidx.compose.ui.platform.LocalView.current
    var lastMeasured by remember { mutableStateOf(measured) }
    androidx.compose.runtime.LaunchedEffect(measured) {
        if (measured > lastMeasured) {
            view.performHapticFeedback(
                if (android.os.Build.VERSION.SDK_INT >= 30) android.view.HapticFeedbackConstants.CONFIRM
                else android.view.HapticFeedbackConstants.VIRTUAL_KEY,
            )
        }
        lastMeasured = measured
    }

    Box(modifier.fillMaxSize().background(Color.Black)) {
        cameraPreview()

        // Tags and pills, pinned to the objects.
        val byId = items.associateBy { it.id }
        val pillsFor = focusedMeasured(items, anchors, focused)
        val labels = buildList {
            for (a in anchors) {
                val item = byId[a.id] ?: continue
                val index = items.indexOf(item)
                val name = item.name ?: "Item ${index + 1}"
                if (a.id == pillsFor) {
                    val m = item.state as ItemScanState.Measured
                    val d = m.dimensions.asDimensions()
                    val round = item.shape == ShapeFamily.CYLINDER || item.shape == ShapeFamily.TAPERED || item.shape == ShapeFamily.SPHERE
                    a.depth?.let { (x, y) ->
                        add(AnchoredLabel("d${a.id}", x, y, AnchorAlign.Centre) { DimensionPill("D", formatLengthWithUnit(d.depthMm, unit), appearDelayMillis = 90) })
                    }
                    a.height?.let { (x, y) ->
                        add(AnchoredLabel("h${a.id}", x, y, AnchorAlign.Centre) { DimensionPill("H", formatLengthWithUnit(d.heightMm, unit), highlight = true, appearDelayMillis = 180) })
                    }
                    val w = a.width
                    if (w != null) {
                        // The tag goes under the width pill, as in the frame: the name reads as a caption.
                        add(AnchoredLabel("w${a.id}", w.first, w.second, AnchorAlign.Below) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Spacer(Modifier.height(4.fd))
                                DimensionPill(if (round) "Ø" else "W", formatLengthWithUnit(d.widthMm, unit))
                                Spacer(Modifier.height(4.fd))
                                ObjectTag(name, ScanBadge.Measured, 1f, compact = multi, onClick = { focused = null }, onLongClick = { onRemove(a.id) })
                            }
                        })
                        continue
                    }
                }
                val (text, badge, progress) = tagFor(item.state, name)
                add(AnchoredLabel("t${a.id}", a.x, a.y, AnchorAlign.Above) {
                    ObjectTag(
                        text = text,
                        badge = badge,
                        progress = progress,
                        compact = multi,
                        onClick = {
                            when (item.state) {
                                is ItemScanState.CannotMeasure -> onManual()
                                is ItemScanState.Measured -> focused = a.id
                                else -> Unit
                            }
                        },
                        onLongClick = { onRemove(a.id) },
                    )
                })
            }
        }
        AnchoredLabels(labels, Modifier.fillMaxSize())

        // Top: back, counter, one instruction.
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
                ScanStatusPill(lead = null, value = "$measured of ${items.size} measured")
            }
            Spacer(Modifier.height(7.fd))
            val (guide, working) = guidance(ui)
            ScanGuidancePill(guide, working)
        }

        // Bottom: live preview (or summary), then torch · done · type it.
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(start = 12.fd, end = 12.fd, bottom = 14.fd),
        ) {
            // The preview card grows into the summary bar when the third object turns up.
            androidx.compose.animation.AnimatedContent(
                targetState = multi,
                transitionSpec = {
                    (androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(com.packabunch.ui.motion.Motion.MEDIUM_MS)) +
                        androidx.compose.animation.scaleIn(initialScale = 0.92f)) togetherWith
                        androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(com.packabunch.ui.motion.Motion.SHORT_MS))
                },
                label = "previewLayout",
            ) { isMulti ->
                if (isMulti) ScanSummaryBar(items, alreadyInPack, planLimit, glassColor)
                else CapturedCard(items, alreadyInPack, planLimit, glassColor)
            }
            Spacer(Modifier.height(if (multi) 13.fd else 9.fd))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 2.fd),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (ui.torchAvailable) {
                    TorchButton(ui.torchOn, onTorch)
                } else {
                    Spacer(Modifier.size(42.fd))
                }
                Spacer(Modifier.weight(1f))
                ScanDoneButton(
                    text = when {
                        measured == 0 -> "Done"
                        multi -> "Use $measured items"
                        else -> "Done · $measured ${if (measured == 1) "item" else "items"}"
                    },
                    enabled = measured > 0,
                    onClick = onDone,
                )
                Spacer(Modifier.weight(1f))
                GlassLottieButton(com.packabunch.R.raw.icon_edit, "Type measurements instead", onManual, iconSize = 24.fd)
            }
        }
    }
}

/** The measured object whose W / D / H pills are showing: the tapped one, else the biggest in view. */
private fun focusedMeasured(items: List<ScanItem>, anchors: List<ScanAnchor>, tapped: Int?): Int? {
    val measured = items.filter { it.state is ItemScanState.Measured }.map { it.id }.toSet()
    if (tapped != null && tapped in measured && anchors.any { it.id == tapped }) return tapped
    // Pills only fit on something that fills a decent share of the view.
    return anchors.filter { it.id in measured && it.span >= 0.22f }.maxByOrNull { it.span }?.id
}

private data class TagText(val text: String, val badge: ScanBadge, val progress: Float)

private fun tagFor(state: ItemScanState, name: String): TagText = when (state) {
    is ItemScanState.Measured -> TagText(name, ScanBadge.Measured, 1f)
    is ItemScanState.Scanning -> TagText("$name · scanning", ScanBadge.Working, state.progress)
    is ItemScanState.NeedsAngle -> TagText(
        when (state.hint) {
            AngleHint.TILT_DOWN -> "$name · tilt down"
            AngleHint.STEP_AROUND -> "$name · step around"
        },
        ScanBadge.Working, state.progress,
    )
    is ItemScanState.CannotMeasure -> TagText(
        when (state.reason) {
            CannotMeasureReason.NO_DEPTH -> "No depth here — type it"
            CannotMeasureReason.TOO_SMALL -> "Too small — type it"
            CannotMeasureReason.TOO_FLAT -> "Too flat — type it"
        },
        ScanBadge.NeedsYou, 0f,
    )
}

/**
 * The one instruction at the top. Always about the single most useful next move, never a
 * list — and when an object needs another angle, it names the object and the move, which is
 * what the old screen's "0 measured, 1 still going" could never tell anyone.
 */
private fun guidance(ui: ItemScanUi): Pair<String, Boolean> {
    if (ui.status != TrackingStatus.TRACKING) {
        return when (ui.failureReason) {
            TrackingFailureReason.INSUFFICIENT_LIGHT -> "Too dark to see — try the torch" to true
            TrackingFailureReason.EXCESSIVE_MOTION -> "A little slower — the camera lost its place" to true
            TrackingFailureReason.INSUFFICIENT_FEATURES -> "Point at the table, not a blank wall" to true
            else -> "Move the phone slowly to get started" to true
        }
    }
    if (!ui.surfaceFound) return "Point at the table or floor they're standing on" to true
    val items = ui.items
    if (ui.capReached) return "That's ${ui.maxItems} — the free plan stops there" to true
    if (items.isEmpty()) return "Point at your things — leave a little gap between them" to true
    val scanning = items.count { it.state is ItemScanState.Scanning || it.state is ItemScanState.NeedsAngle }
    if (scanning == 0) {
        return if (ui.measuredCount > 0) "All measured — tap Done, or add more" to false
        else "Nothing measurable here — type sizes with the pencil" to true
    }
    fun nameOf(item: ScanItem) = item.name?.lowercase() ?: "item ${items.indexOf(item) + 1}"
    if (items.size >= 3) return "Move slowly over the table — $scanning still scanning" to true
    items.firstOrNull { it.state is ItemScanState.NeedsAngle }?.let { item ->
        val hint = (item.state as ItemScanState.NeedsAngle).hint
        return when (hint) {
            AngleHint.TILT_DOWN -> "Tilt down so the top of the ${nameOf(item)} is in view"
            AngleHint.STEP_AROUND -> "Step to the side — I can't see behind the ${nameOf(item)}"
        } to true
    }
    val next = items.filter { it.state is ItemScanState.Scanning }.maxByOrNull { (it.state as ItemScanState.Scanning).progress }
        ?: return "Keep going" to true
    val progress = (next.state as ItemScanState.Scanning).progress
    return (if (progress >= 0.6f) "Hold steady — nearly got the ${nameOf(next)}" else "Move slowly around the ${nameOf(next)}") to true
}

// -- the live 3D preview ---------------------------------------------------------------------

/** `Live preview` in ItemScan · New: what has been captured so far, in 3D, with the count. */
@Composable
private fun CapturedCard(items: List<ScanItem>, alreadyInPack: Int, planLimit: Int?, glass: Color) {
    Column(
        Modifier
            .size(width = 96.fd, height = 98.fd)
            .background(glass, RoundedCornerShape(16.fd))
            .padding(horizontal = 10.fd, vertical = 9.fd),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "CAPTURED",
                color = Color.White.copy(alpha = 0.75f),
                fontFamily = UiFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 8.fs,
                letterSpacing = 0.6f.fs,
            )
            Spacer(Modifier.weight(1f))
            RollingText(countLabel(items.size + alreadyInPack, planLimit), Color.White.copy(alpha = 0.75f), NumericFamily, FontWeight.Normal, 9.fs)
        }
        ScanPreview(items, Modifier.fillMaxWidth().weight(1f), showWorktop = true)
    }
}

/** `Summary` in MultiScan · New: preview tile, counts, and the plan's piece ring. */
@Composable
private fun ScanSummaryBar(items: List<ScanItem>, alreadyInPack: Int, planLimit: Int?, glass: Color) {
    val measured = items.count { it.state is ItemScanState.Measured }
    val scanning = items.count { it.state is ItemScanState.Scanning }
    val needYou = items.count { it.state is ItemScanState.NeedsAngle || it.state is ItemScanState.CannotMeasure }
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.fd)
            .background(glass, RoundedCornerShape(18.fd))
            .padding(horizontal = 6.fd),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.fd),
    ) {
        Box(Modifier.size(44.fd).background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.fd)).padding(4.fd)) {
            ScanPreview(items, Modifier.fillMaxSize(), showWorktop = false)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.fd)) {
            RollingText("$measured measured", Color.White, UiFamily, FontWeight.Bold, 12.fs)
            RollingText(
                listOfNotNull(
                    "$scanning scanning".takeIf { scanning > 0 },
                    "$needYou need you".takeIf { needYou > 0 },
                ).joinToString(" · ").ifEmpty { "All in view are done" },
                Color.White.copy(alpha = 0.72f),
                UiFamily,
                FontWeight.Medium,
                10.5f.fs,
            )
        }
        val total = items.size + alreadyInPack
        Box(Modifier.size(40.fd), contentAlignment = Alignment.Center) {
            val ring by androidx.compose.animation.core.animateFloatAsState(
                if (planLimit != null) total / planLimit.toFloat() else 1f,
                androidx.compose.animation.core.spring(dampingRatio = 1f, stiffness = androidx.compose.animation.core.Spring.StiffnessLow),
                label = "planRing",
            )
            ProgressRing(
                progress = ring,
                modifier = Modifier.fillMaxSize(),
                track = Color.White.copy(alpha = 0.20f),
                strokeFraction = 0.1f,
            )
            RollingText(countLabel(total, planLimit), Color.White, NumericFamily, FontWeight.Medium, 9.5f.fs)
        }
    }
}

private fun countLabel(count: Int, limit: Int?) = if (limit != null) "$count/$limit" else "$count"

/**
 * A small isometric drawing of every fitted object on its surface: solid faces once measured,
 * dashed while still scanning. It is drawn from the same fits the outlines use, so it grows in
 * step with the real scan — the preview is progress, not decoration.
 */
@Composable
private fun ScanPreview(items: List<ScanItem>, modifier: Modifier, showWorktop: Boolean) {
    // Each object rises into the preview when it is found and fills in solid when measured.
    val appear = remember { androidx.compose.runtime.mutableStateMapOf<Int, androidx.compose.animation.core.Animatable<Float, androidx.compose.animation.core.AnimationVector1D>>() }
    val solid = remember { androidx.compose.runtime.mutableStateMapOf<Int, androidx.compose.animation.core.Animatable<Float, androidx.compose.animation.core.AnimationVector1D>>() }
    for (item in items) androidx.compose.runtime.key(item.id) {
        val a = appear.getOrPut(item.id) { androidx.compose.animation.core.Animatable(0f) }
        val f = solid.getOrPut(item.id) { androidx.compose.animation.core.Animatable(0f) }
        val measured = item.state is ItemScanState.Measured
        androidx.compose.runtime.LaunchedEffect(item.id) {
            a.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.7f, stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow))
        }
        androidx.compose.runtime.LaunchedEffect(item.id, measured) {
            f.animateTo(if (measured) 1f else 0f, androidx.compose.animation.core.tween(420, easing = com.packabunch.ui.motion.Motion.Enter))
        }
    }
    Canvas(modifier) {
        val fits = items.mapNotNull { item ->
            val fit = item.fit ?: return@mapNotNull null
            if (item.state is ItemScanState.CannotMeasure) null
            else Triple(fit, solid[item.id]?.value ?: 0f, appear[item.id]?.value ?: 1f)
        }
        if (fits.isEmpty()) return@Canvas
        // Isometric: x runs down-right, y runs down-left, height runs up.
        val c30 = cos(Math.PI / 6).toFloat(); val s30 = sin(Math.PI / 6).toFloat()
        fun iso(x: Float, y: Float, h: Float) = Offset((x - y) * c30, (x + y) * s30 - h)
        val corners = fits.flatMap { (f, _, _) -> boxCorners(f).flatMap { (x, y) -> listOf(iso(x, y, 0f), iso(x, y, f.heightMm)) } }
        val pad = if (showWorktop) 0.18f else 0.06f
        val minX = corners.minOf { it.x }; val maxX = corners.maxOf { it.x }
        val minY = corners.minOf { it.y }; val maxY = corners.maxOf { it.y }
        val scale = minOf(size.width / ((maxX - minX) * (1 + 2 * pad)), size.height / ((maxY - minY) * (1 + 2 * pad)))
        val ox = size.width / 2 - (minX + maxX) / 2 * scale
        val oy = size.height / 2 - (minY + maxY) / 2 * scale
        fun p(x: Float, y: Float, h: Float) = iso(x, y, h).let { Offset(it.x * scale + ox, it.y * scale + oy) }

        if (showWorktop) {
            val xs = fits.flatMap { (f, _, _) -> boxCorners(f).map { it.first } }
            val ys = fits.flatMap { (f, _, _) -> boxCorners(f).map { it.second } }
            val mx = (xs.max() - xs.min()) * 0.25f + 20f; val my = (ys.max() - ys.min()) * 0.25f + 20f
            val top = Path().apply {
                moveTo(p(xs.min() - mx, ys.min() - my, 0f)); lineTo(p(xs.max() + mx, ys.min() - my, 0f))
                lineTo(p(xs.max() + mx, ys.max() + my, 0f)); lineTo(p(xs.min() - mx, ys.max() + my, 0f)); close()
            }
            drawPath(top, Color.White.copy(alpha = 0.07f))
            drawPath(top, Color.White.copy(alpha = 0.28f), style = Stroke(0.8f.fd.toPx()))
        }
        // Back to front, so nearer objects cover farther ones.
        for ((fit, done, shown) in fits.sortedBy { (f, _, _) -> f.centreXMm + f.centreYMm }) {
            // Rises from the worktop as it appears.
            val lift = (1f - shown) * fit.heightMm * 0.6f
            drawPreviewObject(fit, done, shown.coerceIn(0f, 1f)) { x, y, z -> p(x, y, z + lift) }
        }
    }
}

private fun boxCorners(f: FittedObject): List<Pair<Float, Float>> {
    val r = Math.toRadians(f.yawDegrees.toDouble())
    val ux = cos(r).toFloat(); val uy = sin(r).toFloat()
    val hw = f.widthMm / 2; val hd = f.depthMm / 2
    return listOf(-1f to -1f, 1f to -1f, 1f to 1f, -1f to 1f).map { (a, b) ->
        (f.centreXMm + a * hw * ux - b * hd * uy) to (f.centreYMm + a * hw * uy + b * hd * ux)
    }
}

private fun Path.moveTo(o: Offset) = moveTo(o.x, o.y)
private fun Path.lineTo(o: Offset) = lineTo(o.x, o.y)

/**
 * [solid] runs 0 → 1 as the object is measured: dashed outline while scanning, faces filling
 * in as it finishes. [shown] fades the whole object in when it is first found.
 */
private fun DrawScope.drawPreviewObject(fit: FittedObject, solid: Float, shown: Float, p: (Float, Float, Float) -> Offset) {
    val measured = solid >= 0.5f
    val stroke = if (measured) Stroke(0.8f.fd.toPx()) else Stroke(
        1f.fd.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.5f.fd.toPx(), 2f.fd.toPx())),
    )
    val line = Color.White.copy(alpha = (if (measured) 0.9f else 0.85f) * shown)
    fun fill(a: Float) = Color.White.copy(alpha = a * solid * shown)
    val h = fit.heightMm
    when (fit.shape) {
        ShapeFamily.CYLINDER, ShapeFamily.TAPERED, ShapeFamily.SPHERE -> {
            val rb = if (fit.shape == ShapeFamily.SPHERE) fit.widthMm / 2 else fit.bottomRadiusMm ?: fit.widthMm / 2
            val rt = if (fit.shape == ShapeFamily.SPHERE) fit.widthMm / 2 else fit.topRadiusMm ?: fit.widthMm / 2
            val zb = if (fit.shape == ShapeFamily.SPHERE) h / 2 else 0f
            val zt = if (fit.shape == ShapeFamily.SPHERE) h / 2 else h
            fun ring(r: Float, z: Float) = Path().apply {
                for (i in 0..32) {
                    val a = i / 32f * 2 * Math.PI.toFloat()
                    val o = p(fit.centreXMm + r * cos(a), fit.centreYMm + r * sin(a), z)
                    if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y)
                }
                close()
            }
            if (fit.shape == ShapeFamily.SPHERE) {
                val c = p(fit.centreXMm, fit.centreYMm, h / 2)
                val rad = (p(fit.centreXMm + rb, fit.centreYMm - rb, h / 2) - c).getDistance()
                drawCircle(fill(0.55f), rad, c)
                drawCircle(line, rad, c, style = stroke)
                drawPath(ring(rb, zb), line.copy(alpha = 0.5f), style = stroke)
                return
            }
            // Silhouette sides at the ring's leftmost and rightmost points on screen.
            val k = (1f / Math.sqrt(2.0)).toFloat()
            val bl = p(fit.centreXMm - rb * k, fit.centreYMm + rb * k, zb); val br = p(fit.centreXMm + rb * k, fit.centreYMm - rb * k, zb)
            val tl = p(fit.centreXMm - rt * k, fit.centreYMm + rt * k, zt); val tr = p(fit.centreXMm + rt * k, fit.centreYMm - rt * k, zt)
            val body = Path().apply { moveTo(tl); lineTo(bl); lineTo(br); lineTo(tr); close() }
            drawPath(body, fill(0.5f))
            drawPath(ring(rb, zb), fill(0.5f))
            drawPath(ring(rt, zt), fill(0.85f))
            drawLine(line, tl, bl, stroke.width, pathEffect = stroke.pathEffect)
            drawLine(line, tr, br, stroke.width, pathEffect = stroke.pathEffect)
            drawPath(ring(rt, zt), line, style = stroke)
            drawPath(ring(rb, zb), line.copy(alpha = 0.6f), style = stroke)
        }
        else -> {
            val b = boxCorners(fit)
            // The three faces the isometric eye sees: top, and the two sides facing +x+y.
            val base = b.map { (x, y) -> p(x, y, 0f) }
            val top = b.map { (x, y) -> p(x, y, h) }
            val order = b.indices.sortedByDescending { b[it].first + b[it].second }
            val near = order.first()
            val sides = listOf((near + 3) % 4, near, (near + 1) % 4)
            fun quad(a: Offset, c: Offset, d: Offset, e: Offset) = Path().apply { moveTo(a); lineTo(c); lineTo(d); lineTo(e); close() }
            val leftFace = quad(top[sides[0]], top[sides[1]], base[sides[1]], base[sides[0]])
            val rightFace = quad(top[sides[1]], top[sides[2]], base[sides[2]], base[sides[1]])
            val topFace = quad(top[0], top[1], top[2], top[3])
            drawPath(leftFace, fill(0.55f))
            drawPath(rightFace, fill(0.38f))
            drawPath(topFace, fill(0.85f))
            drawPath(leftFace, line, style = stroke)
            drawPath(rightFace, line, style = stroke)
            drawPath(topFace, line, style = stroke)
        }
    }
}
