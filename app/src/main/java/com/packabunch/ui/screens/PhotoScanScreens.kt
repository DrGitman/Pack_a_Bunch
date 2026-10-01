package com.packabunch.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.State
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.packabunch.R
import com.packabunch.packing.Dimensions
import com.packabunch.packing.ItemForm
import com.packabunch.ui.format.LengthUnit
import com.packabunch.packing.ShapeFamily
import com.packabunch.scan.CameraFeed
import com.packabunch.scan.CameraFrame
import com.packabunch.scan.Gravity
import com.packabunch.scan.OutlineLayer
import com.packabunch.scan.OutlineScene
import com.packabunch.scan.OutlineStroke
import com.packabunch.scan.OutlineStyle
import com.packabunch.scan.PhotoInput
import com.packabunch.scan.PhotoItem
import com.packabunch.scan.PhotoMeasure
import com.packabunch.scan.PhotoProgress
import com.packabunch.scan.PhotoSpace
import com.packabunch.scan.ViewMapping
import com.packabunch.scan.photoFromGallery
import com.packabunch.scan.toPhotoInput
import com.packabunch.ui.components.AnchorAlign
import com.packabunch.ui.components.AnchoredLabel
import com.packabunch.ui.components.AnchoredLabels
import com.packabunch.ui.components.DimensionField
import com.packabunch.ui.components.DimensionPill
import com.packabunch.ui.components.LabelledTextField
import com.packabunch.ui.components.Note
import com.packabunch.ui.components.NoteTone
import com.packabunch.ui.components.ObjectTag
import com.packabunch.ui.components.PackAppBar
import com.packabunch.ui.components.PackIcons
import com.packabunch.ui.components.PackTextButton
import com.packabunch.ui.components.PrimaryButton
import com.packabunch.ui.components.ScanBadge
import com.packabunch.ui.components.ScanGuidancePill
import com.packabunch.ui.components.ScanStatusPill
import com.packabunch.ui.components.ScreenScaffold
import com.packabunch.ui.components.SecondaryButton
import com.packabunch.ui.components.Stepper
import com.packabunch.ui.components.SwitchRow
import com.packabunch.ui.components.UnitToggle
import com.packabunch.ui.components.fd
import com.packabunch.ui.components.scanPopIn
import com.packabunch.ui.format.formatEditableLength
import com.packabunch.ui.format.formatLength
import com.packabunch.ui.format.parseLengthToMm
import com.packabunch.ui.motion.Motion
import com.packabunch.ui.theme.CameraEstimateText
import com.packabunch.ui.theme.CameraEstimateTint
import com.packabunch.ui.theme.Ground
import com.packabunch.ui.theme.NumericFamily
import com.packabunch.ui.theme.Outline
import com.packabunch.ui.theme.Primary
import com.packabunch.ui.theme.SurfaceField
import com.packabunch.ui.theme.TextPrimary
import com.packabunch.ui.theme.TextSecondary
import com.packabunch.ui.theme.TextTertiary
import com.packabunch.ui.theme.TypedInText
import com.packabunch.ui.theme.TypedInTint
import com.packabunch.ui.theme.UiFamily
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
 * Measuring by photo — the Figma frames ItemPhoto / SpacePhoto: Take → Finding sizes (with %) →
 * Done → Check each item (photo, auto-name, sizes) or Check the space. Motion as in
 * docs/PHOTO-SCAN-MOTION.md. One photo is one scan against the free plan's daily count.
 */

/** One checked item, ready for the pack. */
class CheckedItem(
    val name: String,
    val dimensions: Dimensions,
    /** Which of W, D, H the person typed over. */
    val typed: Set<Int>,
    val quantity: Int,
    val keepUpright: Boolean,
    val nothingOnTop: Boolean,
    val form: ItemForm?,
    val photo: Bitmap?,
)

private enum class PhotoKind { ITEMS, SPACE }

private val ITEM_STEPS = listOf("Finding the things in your photo", "Tracing their outlines", "Working out the sizes", "Naming them")
private val ITEM_HEADINGS = listOf("Finding the things", "Tracing their outlines", "Working out the sizes", "Naming them")
private val ITEM_ENDS = floatArrayOf(25f, 64f, 90f, 100f)
private val SPACE_STEPS = listOf("Found the floor", "Finding the walls and the opening", "Working out the inside size", "Checking for wheel arches")
private val SPACE_HEADINGS = listOf("Finding the floor", "Finding the walls", "Working out the size", "Checking for arches")
private val SPACE_ENDS = floatArrayOf(22f, 58f, 86f, 100f)
/** Shortest time each step shows, so the steps never flash past (motion doc). */
private val STAGE_MIN_MS = longArrayOf(600, 1700, 600, 600)

// ------------------------------------------------------------------------------------- flows

/** Items by photo: take, find, done, then check each item. */
@Composable
fun PhotoItemsFlow(
    unit: LengthUnit,
    onUnitChange: (LengthUnit) -> Unit,
    maxItems: Int,
    /** True on the free plan, whose piece limit the status pill shows; false on Pack Plus. */
    planLimited: Boolean,
    /** Spends one of the day's scans; false when there are none left (the caller says so). */
    takeScan: () -> Boolean,
    onSave: (List<CheckedItem>) -> Unit,
    onTypeInstead: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val measure = remember { PhotoMeasure(context) }
    var input by remember { mutableStateOf<PhotoInput?>(null) }
    var progress by remember { mutableStateOf(PhotoProgress(0, 0f)) }
    var results by remember { mutableStateOf<List<PhotoItem>?>(null) }
    var done by remember { mutableStateOf(false) }
    var checking by remember { mutableIntStateOf(-1) }
    val checked = remember { mutableStateListOf<CheckedItem>() }
    val scope = rememberCoroutineScope()

    fun retake() { input = null; results = null; done = false; checking = -1; checked.clear() }
    fun start(photo: PhotoInput) {
        if (!takeScan()) return
        input = photo; results = null; done = false; progress = PhotoProgress(0, 0f)
        scope.launch {
            results = measure.items(photo, maxItems) { p -> progress = p }
        }
    }

    val photo = input
    when {
        photo == null -> PhotoCaptureScreen(
            kind = PhotoKind.ITEMS, status = if (planLimited) "Photo · up to $maxItems" else "Photo",
            guidance = "Fit them all in, with a little gap round each",
            hint = "Arm's length, straight on, good light. One photo measures everything in it.",
            onPhoto = ::start, onTypeInstead = onTypeInstead, onBack = onBack,
        )
        !done -> PhotoFindingScreen(
            picture = photo.picture, kind = PhotoKind.ITEMS, progress = progress, finished = results != null,
            onFinished = { done = true }, onCancel = ::retake,
        )
        checking < 0 -> PhotoItemsDoneScreen(photo.picture, results.orEmpty(), progress.outlines, unit, onCheck = { checking = 0 }, onRetake = ::retake, onTypeInstead = onTypeInstead)
        else -> {
            val items = results.orEmpty()
            val item = items.getOrNull(checking)
            if (item == null) { LaunchedEffect(Unit) { onSave(checked.toList()) }; return }
            CheckItemScreen(
                key = checking, item = item, index = checking, total = items.size, unit = unit, onUnitChange = onUnitChange,
                onSave = { c -> checked += c; checking++ },
                onRemove = { checking++ },
                onRetake = ::retake,
                onBack = { if (checking == 0) checking = -1 else { checked.removeAt(checked.lastIndex); checking-- } },
            )
        }
    }
}

/** A space by photo: take, find, done, then check the space. */
@Composable
fun PhotoSpaceFlow(
    spaceName: String?,
    unit: LengthUnit,
    onUnitChange: (LengthUnit) -> Unit,
    edgeGapMm: Int,
    takeScan: () -> Boolean,
    onSave: (name: String, dimensions: Dimensions, typed: Boolean, edgeGapMm: Int) -> Unit,
    onTypeInstead: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val measure = remember { PhotoMeasure(context) }
    var input by remember { mutableStateOf<PhotoInput?>(null) }
    var progress by remember { mutableStateOf(PhotoProgress(0, 0f)) }
    var result by remember { mutableStateOf<Result<PhotoSpace?>?>(null) }
    var done by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val label = spaceName?.takeIf { it.isNotBlank() } ?: "Space"

    fun retake() { input = null; result = null; done = false; checking = false }
    fun start(photo: PhotoInput) {
        if (!takeScan()) return
        input = photo; result = null; done = false; progress = PhotoProgress(0, 0f)
        scope.launch { result = runCatching { measure.space(photo, spaceName) { p -> progress = p } } }
    }

    val photo = input
    val space = result?.getOrNull()
    when {
        photo == null -> PhotoCaptureScreen(
            kind = PhotoKind.SPACE, status = "$label · photo",
            guidance = "Get the whole opening and the floor in",
            hint = "Open it fully. Step back, phone at chest height, and point straight in.",
            onPhoto = ::start, onTypeInstead = onTypeInstead, onBack = onBack,
        )
        !done -> PhotoFindingScreen(photo.picture, PhotoKind.SPACE, progress, finished = result != null, onFinished = { done = true }, onCancel = ::retake, statusLabel = label)
        space == null -> NothingFound(photo.picture, "Couldn't see the floor", "Open it fully, step back and get the whole floor of the space in the photo.", ::retake, onTypeInstead)
        !checking -> PhotoSpaceDoneScreen(photo.picture, space, label, unit, onCheck = { checking = true }, onRetake = ::retake)
        else -> CheckSpaceScreen(photo.picture, space, label, unit, onUnitChange, edgeGapMm, onSave = onSave, onRetake = ::retake, onBack = { checking = false })
    }
}

// ------------------------------------------------------------------------------------- take

@Composable
private fun PhotoCaptureScreen(
    kind: PhotoKind,
    status: String,
    guidance: String,
    hint: String,
    onPhoto: (PhotoInput) -> Unit,
    onTypeInstead: () -> Unit,
    onBack: () -> Unit,
) {
    CameraCaptureGate(onTypeInstead, onBack) {
        val context = LocalContext.current
        val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
        val feed = remember { CameraFeed(context) }
        val gravity = remember { Gravity(context) }
        var latest by remember { mutableStateOf<CameraFrame?>(null) }
        var torch by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        var shooting by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        val preview = remember {
            PreviewView(context).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            }
        }
        DisposableEffect(owner) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> { gravity.start(); feed.start(owner, preview, { latest = it }) { error = it } }
                    Lifecycle.Event.ON_PAUSE -> { feed.stop(); gravity.stop() }
                    else -> Unit
                }
            }
            owner.lifecycle.addObserver(observer)
            onDispose { owner.lifecycle.removeObserver(observer); feed.release(); gravity.stop() }
        }
        val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) scope.launch { photoFromGallery(context, uri)?.let(onPhoto) }
        }
        // Live guidance: what to change for the best photo to measure from, read from the picture's
        // brightness and the phone's tilt and steadiness, a few times a second.
        var brightness by remember { mutableFloatStateOf(128f) }
        var pitch by remember { mutableStateOf<Double?>(null) }
        var movingUntil by remember { mutableStateOf(0L) }
        LaunchedEffect(Unit) {
            var last: DoubleArray? = null
            while (true) {
                val up = gravity.upUpright()
                if (up != null) {
                    pitch = Math.toDegrees(kotlin.math.asin((-up[2]).coerceIn(-1.0, 1.0)))
                    last?.let { l ->
                        val turn = Math.toDegrees(kotlin.math.acos((l[0] * up[0] + l[1] * up[1] + l[2] * up[2]).coerceIn(-1.0, 1.0)))
                        if (turn > 2.5) movingUntil = System.currentTimeMillis() + 600
                    }
                    last = up
                }
                latest?.picture?.let { b -> brightness = lumaOf(b) }
                delay(150)
            }
        }
        val liveGuide = photoGuidance(kind, brightness, pitch, System.currentTimeMillis() < movingUntil) ?: guidance
        val shutter = rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.photo_shutter))
        val shutterAnim = remember { Animatable(0f) }

        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(factory = { preview }, modifier = Modifier.fillMaxSize())
            Corners(Modifier.fillMaxSize().padding(horizontal = 16.fd).padding(top = 140.fd, bottom = 190.fd))

            Column(
                Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 14.fd, vertical = 6.fd),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    PhotoButton(R.raw.photo_back, "Back", 34.fd, onBack)
                    Spacer(Modifier.weight(1f))
                    ScanStatusPill(lead = null, value = status)
                }
                Spacer(Modifier.height(7.fd))
                ScanGuidancePill(error ?: liveGuide, working = true)
            }

            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 12.fd, vertical = 8.fd),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // The tip is a pop-up: it springs in as the camera opens, then gets out of the way of
                // the shot by itself after a few seconds, or at once on a tap.
                var tipShowing by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { delay(350); tipShowing = true; delay(4500); tipShowing = false }
                androidx.compose.animation.AnimatedVisibility(
                    visible = tipShowing,
                    enter = androidx.compose.animation.fadeIn(tween(220)) +
                        androidx.compose.animation.scaleIn(spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow), initialScale = 0.82f) +
                        androidx.compose.animation.slideInVertically(tween(320, easing = Motion.Enter)) { it / 4 },
                    exit = androidx.compose.animation.fadeOut(tween(200, easing = Motion.Exit)) +
                        androidx.compose.animation.slideOutVertically(tween(200, easing = Motion.Exit)) { it / 4 },
                ) {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.fd)).background(Color(0xCC2B2622))
                            .clickable { tipShowing = false }.padding(horizontal = 12.fd, vertical = 9.fd),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(PackIcons.Info, null, tint = Color.White, modifier = Modifier.size(15.fd))
                        Spacer(Modifier.width(9.fd))
                        Text(hint, color = Color.White, fontFamily = UiFamily, fontSize = 11.fd.value.sp, lineHeight = 15.fd.value.sp)
                    }
                }
                Spacer(Modifier.height(18.fd))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.width(12.fd))
                    PhotoButton(R.raw.photo_gallery, "Choose a photo", 42.fd, {
                        gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    })
                    Spacer(Modifier.weight(1f))
                    LottieAnimation(
                        shutter.value, progress = { shutterAnim.value },
                        modifier = Modifier.size(66.fd).clip(CircleShape).clickable(enabled = !shooting && latest != null) {
                            val frame = latest ?: return@clickable
                            shooting = true
                            scope.launch {
                                launch { shutterAnim.snapTo(0f); shutterAnim.animateTo(1f, tween(600)) }
                                val input = frame.toPhotoInput(gravity.upUpright())
                                delay(120)
                                shooting = false
                                onPhoto(input)
                            }
                        },
                    )
                    Spacer(Modifier.weight(1f))
                    PhotoTorch(torch) { torch = feed.setTorch(!torch) && !torch }
                    Spacer(Modifier.width(12.fd))
                }
                Spacer(Modifier.height(6.fd))
                Text(
                    "Type sizes instead", color = Color.White, fontFamily = UiFamily, fontSize = 12.fd.value.sp,
                    modifier = Modifier.clickable(onClick = onTypeInstead).padding(6.fd),
                )
            }
        }
    }
}

/** The four L-shaped corners framing the shot. */
@Composable
private fun Corners(modifier: Modifier) {
    Canvas(modifier) {
        val l = 22.dp.toPx(); val sw = 2.dp.toPx(); val c = Color.White
        fun corner(x: Float, y: Float, dx: Float, dy: Float) {
            drawLine(c, Offset(x, y), Offset(x + dx * l, y), sw, StrokeCap.Round)
            drawLine(c, Offset(x, y), Offset(x, y + dy * l), sw, StrokeCap.Round)
        }
        corner(0f, 0f, 1f, 1f); corner(size.width, 0f, -1f, 1f)
        corner(0f, size.height, 1f, -1f); corner(size.width, size.height, -1f, -1f)
    }
}

// ------------------------------------------------------------------------------------- finding sizes

@Composable
private fun PhotoFindingScreen(
    picture: Bitmap,
    kind: PhotoKind,
    progress: PhotoProgress,
    finished: Boolean,
    onFinished: () -> Unit,
    onCancel: () -> Unit,
    statusLabel: String? = null,
) {
    val ends = if (kind == PhotoKind.ITEMS) ITEM_ENDS else SPACE_ENDS
    val steps = if (kind == PhotoKind.ITEMS) ITEM_STEPS else SPACE_STEPS
    val headings = if (kind == PhotoKind.ITEMS) ITEM_HEADINGS else SPACE_HEADINGS
    val start = remember { System.currentTimeMillis() }
    var now by remember { mutableStateOf(start) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(50) } }

    // The real progress, never shown ahead of itself, and each step held long enough to read.
    val real = run {
        val s = progress.stage.coerceIn(0, 3)
        val from = if (s == 0) 0f else ends[s - 1]
        if (finished) 100f else from + (ends[s] - from) * progress.fraction.coerceIn(0f, 1f)
    }
    val paced = run {
        var t = (now - start).toFloat(); var p = 0f
        for (s in 0..3) {
            val from = if (s == 0) 0f else ends[s - 1]
            val f = (t / STAGE_MIN_MS[s]).coerceIn(0f, 1f)
            p = from + (ends[s] - from) * f
            t -= STAGE_MIN_MS[s]
            if (f < 1f) break
        }
        p
    }
    val target = minOf(real, paced)
    val shown by animateFloatAsState(target, tween(300, easing = Motion.Enter), label = "percent")
    LaunchedEffect(finished, shown >= 99.5f) { if (finished && shown >= 99.5f) { delay(250); onFinished() } }
    val current = ends.indexOfFirst { shown < it }.let { if (it < 0) 3 else it }

    val scrim by animateFloatAsState(0.28f, tween(300, easing = Motion.Standard), label = "scrim")
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        PhotoWithOverlay(picture) { mapping ->
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = scrim)))
            // Each outline arrives piece by piece: its edges fade in one after another over about a second.
            val seen = remember { mutableStateListOf<Long>() }
            while (seen.size < progress.outlines.size) seen += now
            OutlineLayer(OutlineScene(progress.outlines.flatMapIndexed { i, o ->
                val v = o.toView(mapping, picture)
                val n = v.size / 2 - 1
                (0 until n).map { j ->
                    val a = ((now - seen[i] - j * 1000f / n) / 250f).coerceIn(0f, 1f)
                    OutlineStroke(floatArrayOf(v[2 * j], v[2 * j + 1], v[2 * j + 2], v[2 * j + 3]), OutlineStyle.SCANNING.faded(a))
                }
            }), LocalDensity.current.density)
        }
        Row(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 14.fd, vertical = 6.fd),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PhotoButton(R.raw.photo_close, "Cancel", 34.fd, onCancel)
            Spacer(Modifier.weight(1f))
            if (kind == PhotoKind.ITEMS && progress.found > 0) ScanStatusPill(lead = null, value = "${progress.found} found", modifier = Modifier.scanPopIn())
            else if (statusLabel != null) ScanStatusPill(lead = null, value = statusLabel)
        }
        Sheet(Modifier.align(Alignment.BottomCenter)) {
            Box(Modifier.align(Alignment.CenterHorizontally).size(36.dp, 4.dp).background(Color(0xFFE6D9CC), RoundedCornerShape(2.dp)))
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text("FINDING SIZES", color = TextTertiary, fontFamily = UiFamily, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 0.8.sp)
                    Spacer(Modifier.height(4.dp))
                    Crossfade(headings[current], animationSpec = tween(180), label = "heading") { text ->
                        Text(text, color = TextPrimary, fontFamily = UiFamily, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                    }
                }
                Text("${shown.toInt()}%", color = Primary, fontFamily = UiFamily, fontWeight = FontWeight.ExtraBold, fontSize = 32.sp)
            }
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth().height(6.dp).background(Color(0xFFF0E4D8), RoundedCornerShape(3.dp))) {
                Box(Modifier.fillMaxWidth(shown / 100f).height(6.dp).background(Primary, RoundedCornerShape(3.dp)))
            }
            Spacer(Modifier.height(16.dp))
            for (i in 0..3) {
                val state = when { shown >= ends[i] -> 2; i == current -> 1; else -> 0 }
                val detail = when {
                    kind == PhotoKind.ITEMS && i == 0 && state == 2 -> "${progress.found}"
                    kind == PhotoKind.ITEMS && i == 1 && state == 1 && progress.found > 0 -> "${progress.traced} of ${progress.found}"
                    else -> null
                }
                StepRow(steps[i], state, detail)
                if (i < 3) Spacer(Modifier.height(12.dp))
            }
            Spacer(Modifier.height(14.dp))
            Text("About ten seconds. The photo stays on your phone.", color = TextTertiary, fontFamily = UiFamily, fontSize = 12.sp)
        }
    }
}

/** One step: a ring before it starts, the spinner while it runs, the tick when it is done. */
@Composable
private fun StepRow(label: String, state: Int, detail: String?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
            when (state) {
                0 -> Canvas(Modifier.size(18.dp)) { drawCircle(Color(0xFFDCC6AF), radius = size.minDimension / 2 - 1.dp.toPx(), style = Stroke(1.6.dp.toPx())) }
                1 -> LottieOnce(R.raw.photo_step_working, Modifier.size(20.dp), loop = true)
                else -> LottieOnce(R.raw.photo_step_done, Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            label, color = if (state == 0) Color(0xFFA2907F) else Color(0xFF2B1D14), fontFamily = UiFamily,
            fontWeight = if (state == 1) FontWeight.Bold else FontWeight.SemiBold, fontSize = 14.5.sp, modifier = Modifier.weight(1f),
        )
        if (detail != null) Text(detail, color = if (state == 2) com.packabunch.ui.theme.Success else TextTertiary, fontFamily = NumericFamily, fontSize = 12.sp)
    }
}

@Composable
private fun LottieOnce(res: Int, modifier: Modifier, loop: Boolean = false) {
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(res))
    val progress by animateLottieCompositionAsState(composition, iterations = if (loop) LottieConstants.IterateForever else 1)
    LottieAnimation(composition, progress = { progress }, modifier = modifier)
}

// ------------------------------------------------------------------------------------- done

@Composable
private fun PhotoItemsDoneScreen(picture: Bitmap, items: List<PhotoItem>, traced: List<FloatArray>, unit: LengthUnit, onCheck: () -> Unit, onRetake: () -> Unit, onTypeInstead: () -> Unit) {
    if (items.isEmpty()) {
        NothingFound(picture, "Couldn't find anything", "Fit the things in with a gap round each, in good light, and try again.", onRetake, onTypeInstead)
        return
    }
    val t = rememberElapsedMs().value
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        PhotoWithOverlay(picture) { mapping ->
            val density = LocalDensity.current.density
            // The faint footprint glow under each measured thing, fading in from 0.25 s (Enter).
            OutlineLayer(OutlineScene(fills = items.mapNotNull { it.footprint }.map {
                com.packabunch.scan.OutlineFill(it.toView(mapping, picture), OutlineStyle.MEASURED_FILL_ARGB)
            }), density, Modifier.fillMaxSize().alpha(phase(t, 250, 400, Motion.Enter)))
            // Dashed outlines fade out as the solid measured ones draw in.
            OutlineLayer(OutlineScene(traced.map { OutlineStroke(it.toView(mapping, picture), OutlineStyle.SCANNING) }), density,
                Modifier.fillMaxSize().alpha(1f - phase(t, 0, 350, Motion.Standard)))
            OutlineLayer(OutlineScene(items.flatMap { it.outline }.map { OutlineStroke(it.toView(mapping, picture), OutlineStyle.MEASURED) }),
                density, Modifier.fillMaxSize().alpha(phase(t, 50, 400, androidx.compose.animation.core.FastOutSlowInEasing)))
            AnchoredLabels(items.mapIndexed { i, item ->
                val (x, y) = mapping.toView(item.tag.first * picture.width.toDouble(), item.tag.second * picture.height.toDouble())
                AnchoredLabel("t$i", x / mapping.viewW, y / mapping.viewH, AnchorAlign.Above) {
                    Box(Modifier.popAt(t, 350L + 100L * i)) { ObjectTag(item.name ?: "Item ${i + 1}", ScanBadge.Measured, 1f, compact = items.size > 2) }
                }
            }, Modifier.fillMaxSize())
        }
        PhotoButton(R.raw.photo_retake, "Retake photo", 34.fd, onRetake,
            Modifier.windowInsetsPadding(WindowInsets.safeDrawing).padding(14.fd).alpha(phase(t, 600, 240, LinearEasing)))
        Sheet(Modifier.align(Alignment.BottomCenter), delayMs = 150) {
            DoneHeading(if (items.size == 1) "Found 1 thing" else "Found ${items.size} things", "Sizes from one photo. Check each one next.", t)
            Spacer(Modifier.height(14.dp))
            // A fixed card: however many things were found, only the list inside it scrolls.
            Column(
                Modifier.fillMaxWidth().height(ROW_HEIGHT * 2 + 10.dp).border(1.dp, Outline, RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp))
                    .verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 5.dp),
            ) {
                items.forEachIndexed { i, item ->
                    Row(Modifier.fillMaxWidth().height(ROW_HEIGHT).appear(t, 650L + 100L * i, 8f), verticalAlignment = Alignment.CenterVertically) {
                        Thumb(item.crop, 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(item.name ?: "Item ${i + 1}", color = TextPrimary, fontFamily = UiFamily, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
                                Spacer(Modifier.width(8.dp))
                                PhotoBadge("FROM PHOTO")
                            }
                            Text(sizeLine(item.dimensions, item.shape, unit), color = TextSecondary, fontFamily = NumericFamily, fontSize = 12.5.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Box(Modifier.appear(t, 900, 8f)) { PrimaryButton("Check the sizes", onCheck) }
            PackTextButton("Retake photo", onRetake, Modifier.align(Alignment.CenterHorizontally).alpha(phase(t, 950, 240, LinearEasing)))
        }
    }
}

private val ROW_HEIGHT = 58.dp

/** The space's floor glow: 12 % white, quieter than an object's 16 %. */
private const val FLOOR_FILL_ARGB = 0x1FFFFFFFL

@Composable
private fun PhotoSpaceDoneScreen(picture: Bitmap, space: PhotoSpace, label: String, unit: LengthUnit, onCheck: () -> Unit, onRetake: () -> Unit) {
    val t = rememberElapsedMs().value
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        PhotoWithOverlay(picture) { mapping -> SpaceOverlay(space, picture, mapping, unit, phase(t, 50, 400, androidx.compose.animation.core.FastOutSlowInEasing), labelsFrom = 500, glow = phase(t, 250, 400, Motion.Enter)) }
        PhotoButton(R.raw.photo_retake, "Retake photo", 34.fd, onRetake,
            Modifier.windowInsetsPadding(WindowInsets.safeDrawing).padding(14.fd).alpha(phase(t, 600, 240, LinearEasing)))
        Sheet(Modifier.align(Alignment.BottomCenter), delayMs = 150) {
            DoneHeading("$label measured", "Inside size from one photo. Check it next.", t)
            Spacer(Modifier.height(14.dp))
            Column(Modifier.fillMaxWidth().appear(t, 650, 8f).background(Color(0xFFF7EFE6), RoundedCornerShape(16.dp)).padding(16.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    for ((name, mm) in listOf("WIDTH" to space.dimensions.widthMm, "DEPTH" to space.dimensions.depthMm, "HEIGHT" to space.dimensions.heightMm)) {
                        Column(Modifier.weight(1f)) {
                            Text(name, color = TextTertiary, fontFamily = UiFamily, fontWeight = FontWeight.Bold, fontSize = 10.5.sp, letterSpacing = 0.6.sp)
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(formatLength(mm, unit), color = TextPrimary, fontFamily = NumericFamily, fontSize = 24.sp)
                                Text(" ${unit.shortLabel}", color = TextTertiary, fontFamily = NumericFamily, fontSize = 12.sp)
                            }
                        }
                    }
                }
                space.opening?.let { (w, h) ->
                    Spacer(Modifier.height(10.dp))
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Outline))
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth()) {
                        Text("Opening", color = TextSecondary, fontFamily = UiFamily, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Text("${formatLength(w, unit)} × ${formatLength(h, unit)} ${unit.shortLabel}", color = TextPrimary, fontFamily = NumericFamily, fontSize = 13.sp)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Box(Modifier.appear(t, 850, 8f)) { PrimaryButton("Check the sizes", onCheck) }
            PackTextButton("Retake photo", onRetake, Modifier.align(Alignment.CenterHorizontally).alpha(phase(t, 900, 240, LinearEasing)))
        }
    }
}

/** The space's box, opening and W / D / H pills over the photo. */
@Composable
private fun SpaceOverlay(space: PhotoSpace, picture: Bitmap, mapping: ViewMapping, unit: LengthUnit, alpha: Float, labelsFrom: Int = 0, glow: Float = 1f) {
    // The faint floor glow, under the lines.
    space.floor?.let { f ->
        OutlineLayer(OutlineScene(fills = listOf(com.packabunch.scan.OutlineFill(f.toView(mapping, picture), FLOOR_FILL_ARGB))),
            LocalDensity.current.density, Modifier.fillMaxSize().alpha(glow))
    }
    OutlineLayer(OutlineScene(space.edges.map { (e, opening) ->
        OutlineStroke(e.toView(mapping, picture), if (opening) OutlineStyle.OPENING else OutlineStyle.MEASURED)
    }), LocalDensity.current.density, Modifier.fillMaxSize().alpha(alpha))
    fun at(p: Pair<Float, Float>?) = p?.let { mapping.toView(it.first * picture.width.toDouble(), it.second * picture.height.toDouble()) }
        ?.let { (x, y) -> x / mapping.viewW to y / mapping.viewH }
    AnchoredLabels(buildList {
        at(space.openingAt)?.let { (x, y) -> space.opening?.let { (w, h) ->
            add(AnchoredLabel("o", x, y, AnchorAlign.Above) { DimensionPill("OPENING", "${formatLength(w, unit)} × ${formatLength(h, unit)} ${unit.shortLabel}", appearDelayMillis = labelsFrom) })
        } }
        at(space.widthAt)?.let { (x, y) -> add(AnchoredLabel("w", x, y, AnchorAlign.Centre, movable = false) { DimensionPill("W", "${formatLength(space.dimensions.widthMm, unit)} ${unit.shortLabel}", appearDelayMillis = labelsFrom + 100) }) }
        at(space.depthAt)?.let { (x, y) -> add(AnchoredLabel("d", x, y, AnchorAlign.Centre, movable = false) { DimensionPill("D", "${formatLength(space.dimensions.depthMm, unit)} ${unit.shortLabel}", appearDelayMillis = labelsFrom + 190) }) }
        at(space.heightAt)?.let { (x, y) -> add(AnchoredLabel("h", x, y, AnchorAlign.Centre, movable = false) { DimensionPill("H", "${formatLength(space.dimensions.heightMm, unit)} ${unit.shortLabel}", highlight = true, appearDelayMillis = labelsFrom + 280) }) }
    }, Modifier.fillMaxSize())
}

@Composable
private fun DoneHeading(title: String, subtitle: String, t: Long = Long.MAX_VALUE) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        // The big tick plays at 0.45 s; until then its place is kept.
        Box(Modifier.size(48.dp)) { if (t >= 450) LottieOnce(R.raw.photo_done_check, Modifier.size(48.dp)) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.graphicsLayer {
            alpha = phase(t, 500, 240, LinearEasing)
            translationY = (1f - phase(t, 500, 320, Motion.Enter)) * 10.dp.toPx()
        }) {
            Text(title, color = TextPrimary, fontFamily = UiFamily, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp)
            Text(subtitle, color = TextSecondary, fontFamily = UiFamily, fontSize = 13.sp)
        }
    }
}

@Composable
private fun NothingFound(picture: Bitmap, title: String, body: String, onRetake: () -> Unit, onTypeInstead: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        PhotoWithOverlay(picture) { Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.28f))) }
        Sheet(Modifier.align(Alignment.BottomCenter)) {
            Text(title, color = TextPrimary, fontFamily = UiFamily, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp)
            Spacer(Modifier.height(6.dp))
            Text(body, color = TextSecondary, fontFamily = UiFamily, fontSize = 13.5.sp)
            Spacer(Modifier.height(16.dp))
            PrimaryButton("Retake photo", onRetake)
            PackTextButton("Type sizes instead", onTypeInstead, Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

// ------------------------------------------------------------------------------------- check

@Composable
private fun CheckItemScreen(
    key: Int,
    item: PhotoItem,
    index: Int,
    total: Int,
    unit: LengthUnit,
    onUnitChange: (LengthUnit) -> Unit,
    onSave: (CheckedItem) -> Unit,
    onRemove: () -> Unit,
    onRetake: () -> Unit,
    onBack: () -> Unit,
) {
    // Keyed by position, so each item starts from its own photo's values.
    var name by remember(key) { mutableStateOf(item.name ?: "Item ${index + 1}") }
    var w by remember(key, unit) { mutableStateOf(formatEditableLength(item.dimensions.widthMm, unit)) }
    var d by remember(key, unit) { mutableStateOf(formatEditableLength(item.dimensions.depthMm, unit)) }
    var h by remember(key, unit) { mutableStateOf(formatEditableLength(item.dimensions.heightMm, unit)) }
    val typed = remember(key) { mutableStateListOf<Int>() }
    var quantity by remember(key) { mutableIntStateOf(1) }
    var upright by remember(key) { mutableStateOf(item.shape == ShapeFamily.CYLINDER || item.shape == ShapeFamily.TAPERED) }
    var nothingOnTop by remember(key) { mutableStateOf(false) }
    val wMm = parseLengthToMm(w, unit); val dMm = parseLengthToMm(d, unit); val hMm = parseLengthToMm(h, unit)

    ScreenScaffold {
        PackAppBar("Check item", onBack, actions = {
            Text("${index + 1} of $total", color = TextTertiary, fontFamily = NumericFamily, fontSize = 13.sp, modifier = Modifier.padding(end = 16.dp))
        })
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (i in 0 until total) Box(Modifier.weight(1f).height(4.dp).background(if (i <= index) Primary else Color(0xFFEADFD3), RoundedCornerShape(2.dp)))
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp)) {
            Row {
                Box {
                    Thumb(item.crop, 104.dp)
                    PhotoButton(R.raw.photo_retake, "Retake photo", 28.dp, onRetake, modifier = Modifier.align(Alignment.TopEnd).padding(5.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    LabelledTextField("Name", name, { name = it })
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(PackIcons.Sparkle, null, tint = Primary, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Named from the photo. Tap to change.", color = TextTertiary, fontFamily = UiFamily, fontSize = 12.sp)
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Size, at its widest points", color = TextPrimary, fontFamily = UiFamily, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                UnitToggle(unit, onUnitChange, compact = true)
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((i, pair) in listOf(Triple("Width", w, { v: String -> w = v }), Triple("Depth", d, { v: String -> d = v }), Triple("Height", h, { v: String -> h = v })).withIndex()) {
                    Column(Modifier.weight(1f)) {
                        DimensionField(pair.first, pair.second, { v -> pair.third(v); if (i !in typed) typed += i }, unit,
                            error = parseLengthToMm(pair.second, unit) == null)
                        Spacer(Modifier.height(6.dp))
                        PhotoBadge(if (i in typed) "TYPED" else "PHOTO", typed = i in typed)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Note("Photo sizes are estimates. If the fit will be tight, check with a tape and type the number.", tone = NoteTone.Caution, icon = PackIcons.Warning)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth().background(SurfaceField, RoundedCornerShape(18.dp)).padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("How many", color = TextPrimary, fontFamily = UiFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.5.sp, modifier = Modifier.weight(1f))
                Stepper(quantity, { quantity = it })
            }
            Spacer(Modifier.height(10.dp))
            SwitchRow("Keep it upright", "Stops it being laid on its side", upright, { upright = it })
            Spacer(Modifier.height(10.dp))
            SwitchRow("Nothing on top", "Fragile, or shouldn't be squashed", nothingOnTop, { nothingOnTop = it })
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryButton("Remove", onRemove, Modifier.weight(1f))
            PrimaryButton(
                if (index + 1 < total) "Save · next item" else "Save · add to pack",
                onClick = {
                    onSave(CheckedItem(
                        name = name.trim().ifEmpty { "Item ${index + 1}" },
                        dimensions = Dimensions(wMm!!, dMm!!, hMm!!),
                        typed = typed.toSet(), quantity = quantity, keepUpright = upright, nothingOnTop = nothingOnTop,
                        form = item.form ?: ItemForm.guess(name), photo = item.crop,
                    ))
                },
                modifier = Modifier.weight(1.6f),
                enabled = wMm != null && dMm != null && hMm != null && wMm > 0 && dMm > 0 && hMm > 0,
            )
        }
    }
}

@Composable
private fun CheckSpaceScreen(
    picture: Bitmap,
    space: PhotoSpace,
    label: String,
    unit: LengthUnit,
    onUnitChange: (LengthUnit) -> Unit,
    edgeGapMm: Int,
    onSave: (name: String, dimensions: Dimensions, typed: Boolean, edgeGapMm: Int) -> Unit,
    onRetake: () -> Unit,
    onBack: () -> Unit,
) {
    var name by remember { mutableStateOf(label) }
    var w by remember(unit) { mutableStateOf(formatEditableLength(space.dimensions.widthMm, unit)) }
    var d by remember(unit) { mutableStateOf(formatEditableLength(space.dimensions.depthMm, unit)) }
    var h by remember(unit) { mutableStateOf(formatEditableLength(space.dimensions.heightMm, unit)) }
    var editing by remember { mutableIntStateOf(-1) }
    val typed = remember { mutableStateListOf<Int>() }
    var gap by remember { mutableIntStateOf(edgeGapMm) }
    val wMm = parseLengthToMm(w, unit); val dMm = parseLengthToMm(d, unit); val hMm = parseLengthToMm(h, unit)

    ScreenScaffold {
        PackAppBar("Check the space", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp)) {
            Box(Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(18.dp))) {
                PhotoWithOverlay(picture) { mapping -> SpaceOverlay(space, picture, mapping, unit, 1f) }
                PhotoButton(R.raw.photo_retake, "Retake photo", 30.dp, onRetake, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp))
                Text("Your photo · stays on this phone", color = Color.White, fontFamily = UiFamily, fontSize = 11.sp,
                    modifier = Modifier.align(Alignment.BottomStart).padding(10.dp).background(Color(0x992B2622), RoundedCornerShape(999.dp)).padding(horizontal = 10.dp, vertical = 4.dp))
            }
            Spacer(Modifier.height(14.dp))
            LabelledTextField("Name", name, { name = it })
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Inside measurements", color = TextPrimary, fontFamily = UiFamily, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                UnitToggle(unit, onUnitChange, compact = true)
            }
            Spacer(Modifier.height(10.dp))
            Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(18.dp)).border(1.dp, Outline, RoundedCornerShape(18.dp)).padding(horizontal = 14.dp, vertical = 4.dp)) {
                val rows = listOf(Triple("Width", w, { v: String -> w = v }), Triple("Depth", d, { v: String -> d = v }), Triple("Height", h, { v: String -> h = v }))
                for ((i, row) in rows.withIndex()) {
                    if (editing == i) {
                        DimensionField(row.first, row.second, { v -> row.third(v); if (i !in typed) typed += i }, unit,
                            Modifier.padding(vertical = 8.dp), focused = true, error = parseLengthToMm(row.second, unit) == null)
                    } else {
                        SizeRow(row.first, "${row.second} ${unit.shortLabel}", typed = i in typed, onEdit = { editing = i })
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFF1E8DF)))
                }
                space.opening?.let { (ow, oh) -> SizeRow("Opening", "${formatLength(ow, unit)} × ${formatLength(oh, unit)} ${unit.shortLabel}", typed = false, onEdit = null) }
            }
            Spacer(Modifier.height(14.dp))
            Note("Photo sizes are estimates. If the fit will be tight, check with a tape and type the number.", tone = NoteTone.Caution, icon = PackIcons.Warning)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth().background(SurfaceField, RoundedCornerShape(18.dp)).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Leave a gap around the edges", color = TextPrimary, fontFamily = UiFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.5.sp)
                    Text("Currently $gap mm on every side", color = TextTertiary, fontFamily = UiFamily, fontSize = 12.5.sp)
                }
                Stepper(gap, { gap = it }, range = 0..50)
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryButton("Retake", onRetake, Modifier.weight(1f), icon = PackIcons.Rotate)
            PrimaryButton(
                "Save this space",
                onClick = { onSave(name.trim().ifEmpty { label }, Dimensions(wMm!!, dMm!!, hMm!!), typed.isNotEmpty(), gap) },
                modifier = Modifier.weight(1.6f),
                enabled = wMm != null && dMm != null && hMm != null && wMm > 0 && dMm > 0 && hMm > 0,
            )
        }
    }
}

@Composable
private fun SizeRow(label: String, value: String, typed: Boolean, onEdit: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = TextPrimary, fontFamily = UiFamily, fontSize = 14.5.sp, modifier = Modifier.weight(1f))
        PhotoBadge(if (typed) "TYPED" else "PHOTO", typed)
        Spacer(Modifier.width(10.dp))
        Text(value, color = TextPrimary, fontFamily = NumericFamily, fontSize = 15.sp)
        if (onEdit != null) {
            Spacer(Modifier.width(10.dp))
            Icon(PackIcons.Pencil, "Edit $label", tint = Primary, modifier = Modifier.size(18.dp).clickable(onClick = onEdit))
        }
    }
}

// ------------------------------------------------------------------------------------- pieces

@Composable
private fun PhotoBadge(text: String, typed: Boolean = false) {
    Text(
        text, color = if (typed) TypedInText else CameraEstimateText, fontFamily = UiFamily, fontWeight = FontWeight.Bold, fontSize = 10.sp,
        modifier = Modifier.background(if (typed) TypedInTint else CameraEstimateTint, RoundedCornerShape(6.dp)).padding(horizontal = 7.dp, vertical = 3.dp),
    )
}

@Composable
private fun Thumb(bitmap: Bitmap?, size: androidx.compose.ui.unit.Dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(12.dp)).background(Color(0xFFEADFD3))) {
        if (bitmap != null) Image(bitmap.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun Sheet(modifier: Modifier = Modifier, delayMs: Long = 0, content: @Composable ColumnScope.() -> Unit) {
    val reduce = reduceMotion()
    val rise = remember { Animatable(if (reduce) 0f else 1f) }
    LaunchedEffect(Unit) { if (!reduce) { delay(delayMs); rise.animateTo(0f, spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)) } }
    Column(
        modifier.fillMaxWidth()
            .offset { androidx.compose.ui.unit.IntOffset(0, (rise.value * 600).toInt()) }
            .background(Color.White, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        content = content,
    )
}

/** The photo filling its box, centre-cropped, with [overlay] drawn in the same coordinates. */
@Composable
private fun PhotoWithOverlay(picture: Bitmap, overlay: @Composable (ViewMapping) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val vw = with(density) { maxWidth.roundToPx() }.coerceAtLeast(1)
        val vh = with(density) { maxHeight.roundToPx() }.coerceAtLeast(1)
        val image = remember(picture) { picture.asImageBitmap() }
        Image(image, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        overlay(remember(picture, vw, vh) { ViewMapping(0, picture.width, picture.height, vw, vh) })
    }
}

/** 0..1 picture polyline to view pixels. */
private fun FloatArray.toView(mapping: ViewMapping, picture: Bitmap): FloatArray {
    val out = FloatArray(size)
    for (i in 0 until size / 2) {
        val (x, y) = mapping.toView(this[2 * i] * picture.width.toDouble(), this[2 * i + 1] * picture.height.toDouble())
        out[2 * i] = x; out[2 * i + 1] = y
    }
    return out
}

private fun sizeLine(d: Dimensions, shape: ShapeFamily, unit: LengthUnit): String {
    val round = shape == ShapeFamily.CYLINDER || shape == ShapeFamily.TAPERED || shape == ShapeFamily.SPHERE
    return if (round) "Ø ${formatLength(d.widthMm, unit)} × ${formatLength(d.heightMm, unit)} ${unit.shortLabel}"
    else "${formatLength(d.widthMm, unit)} × ${formatLength(d.depthMm, unit)} × ${formatLength(d.heightMm, unit)} ${unit.shortLabel}"
}

// ------------------------------------------------------------------------------------- motion helpers

/** A glass button that is one of the photo Lotties: resting on its first frame, played once on a tap. */
@Composable
private fun PhotoButton(res: Int, description: String, size: Dp, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(res))
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    LottieAnimation(
        composition, { progress.value },
        modifier = modifier.size(size).clip(CircleShape)
            .semantics { contentDescription = description; role = Role.Button }
            .clickable {
                scope.launch { progress.snapTo(0f); progress.animateTo(1f, tween((composition?.duration ?: 500f).toInt(), easing = LinearEasing)) }
                onClick()
            },
    )
}

/**
 * The torch: the on and off files, chosen by the state it is going to, each holding its last frame.
 * The files are twice the button so the amber ring can flash out past it.
 */
@Composable
private fun PhotoTorch(on: Boolean, onToggle: () -> Unit) {
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(if (on) R.raw.photo_torch_on else R.raw.photo_torch_off))
    val progress = remember { Animatable(1f) }
    var tapped by remember { mutableStateOf(false) }
    LaunchedEffect(on) { if (tapped) { progress.snapTo(0f); progress.animateTo(1f, tween(600, easing = LinearEasing)) } }
    Box(
        Modifier.size(42.fd).semantics { contentDescription = if (on) "Torch on" else "Torch off"; role = Role.Button }
            .clickable { tapped = true; onToggle() },
        contentAlignment = Alignment.Center,
    ) {
        LottieAnimation(composition, { progress.value }, modifier = Modifier.requiredSize(84.fd), clipToCompositionBounds = false)
    }
}

/** True when the system has animations switched off: every motion jumps to its end. */
@Composable
private fun reduceMotion(): Boolean {
    val context = LocalContext.current
    return remember { android.provider.Settings.Global.getFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
}

/** Milliseconds since this screen appeared, every frame — the clock the Done timeline runs on. */
@Composable
private fun rememberElapsedMs(): State<Long> {
    val reduce = reduceMotion()
    val clock = remember { mutableStateOf(if (reduce) 100_000L else 0L) }
    if (!reduce) LaunchedEffect(Unit) {
        val start = withFrameMillis { it }
        while (clock.value < 4000) clock.value = withFrameMillis { it } - start
    }
    return clock
}

/** 0..1 through a step that starts at [at] ms and lasts [dur] ms, eased. */
private fun phase(t: Long, at: Long, dur: Int, easing: androidx.compose.animation.core.Easing): Float =
    easing.transform(((t - at) / dur.toFloat()).coerceIn(0f, 1f))

/** Fades in and rises [riseDp] at [at] ms (Enter easing, 320 ms). */
private fun Modifier.appear(t: Long, at: Long, riseDp: Float): Modifier = graphicsLayer {
    alpha = phase(t, at, 240, LinearEasing)
    translationY = (1f - phase(t, at, 320, Motion.Enter)) * riseDp * density
}

/** scanPopIn on the timeline: opacity 0→1, scale 0.82→1 with the spring's overshoot, rising 6 dp. */
private fun Modifier.popAt(t: Long, at: Long): Modifier = graphicsLayer {
    val f = ((t - at) / 450f).coerceIn(0f, 1f)
    val spring = 1f - kotlin.math.exp(-6f * f) * kotlin.math.cos(9f * f)
    alpha = (f * 4f).coerceAtMost(1f)
    val scale = 0.82f + 0.18f * spring
    scaleX = scale; scaleY = scale
    translationY = (1f - spring) * 6f * density
}

/** The same outline style, [a] as opaque. */
private fun OutlineStyle.faded(a: Float): OutlineStyle {
    val alpha = (((argb shr 24) and 0xFF) * a).toLong()
    return copy(argb = (alpha shl 24) or (argb and 0xFFFFFF))
}

/** Mean brightness 0..255 of a picture, from a coarse grid of its pixels. */
private fun lumaOf(b: Bitmap): Float {
    var sum = 0f; var n = 0
    for (gy in 1..12) for (gx in 1..16) {
        val p = b.getPixel(gx * (b.width - 1) / 17, gy * (b.height - 1) / 13)
        sum += ((p shr 16) and 0xFF) * 0.299f + ((p shr 8) and 0xFF) * 0.587f + (p and 0xFF) * 0.114f; n++
    }
    return sum / n
}

/**
 * What to change for a better photo to measure from, or null when the shot is good. Light first,
 * then steadiness, then the angle: things on a table need it in view; a space needs its floor.
 */
private fun photoGuidance(kind: PhotoKind, brightness: Float, pitchDeg: Double?, moving: Boolean): String? = when {
    brightness < 40f -> if (kind == PhotoKind.SPACE) "Too dark inside — turn the torch on" else "Too dark — turn the torch on"
    moving -> "Hold still for the photo"
    pitchDeg == null -> null
    kind == PhotoKind.ITEMS && pitchDeg < 15 -> "Tilt down a little, so the table shows"
    kind == PhotoKind.ITEMS && pitchDeg > 80 -> "Tilt up a little, so their sides show too"
    kind == PhotoKind.SPACE && pitchDeg < 5 -> "Point a little down, so the floor is in"
    kind == PhotoKind.SPACE && pitchDeg > 65 -> "Step back and point straight in"
    else -> null
}

@Suppress("unused") private val keep = listOf(Ground, TextPrimary)
