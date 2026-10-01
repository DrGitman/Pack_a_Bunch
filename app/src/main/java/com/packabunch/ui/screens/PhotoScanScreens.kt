package com.packabunch.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Animatable
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
import com.packabunch.ui.components.GlassLottieButton
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
import com.packabunch.ui.components.TorchButton
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
            kind = PhotoKind.ITEMS, status = "Photo · up to $maxItems",
            guidance = "Fit them all in, with a little gap round each",
            hint = "Arm's length, straight on, good light. One photo measures everything in it.",
            onPhoto = ::start, onTypeInstead = onTypeInstead, onBack = onBack,
        )
        !done -> PhotoFindingScreen(
            picture = photo.picture, kind = PhotoKind.ITEMS, progress = progress, finished = results != null,
            onFinished = { done = true }, onCancel = ::retake,
        )
        checking < 0 -> PhotoItemsDoneScreen(photo.picture, results.orEmpty(), unit, onCheck = { checking = 0 }, onRetake = ::retake, onTypeInstead = onTypeInstead)
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
                    GlassLottieButton(R.raw.icon_back, "Back", onBack, diameter = 34.fd, iconSize = 20.fd)
                    Spacer(Modifier.weight(1f))
                    ScanStatusPill(lead = null, value = status)
                }
                Spacer(Modifier.height(7.fd))
                ScanGuidancePill(error ?: guidance, working = true)
            }

            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 12.fd, vertical = 8.fd),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    Modifier.fillMaxWidth().background(Color(0xCC2B2622), RoundedCornerShape(12.fd)).padding(horizontal = 12.fd, vertical = 9.fd),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(PackIcons.Info, null, tint = Color.White, modifier = Modifier.size(15.fd))
                    Spacer(Modifier.width(9.fd))
                    Text(hint, color = Color.White, fontFamily = UiFamily, fontSize = 11.fd.value.sp, lineHeight = 15.fd.value.sp)
                }
                Spacer(Modifier.height(18.fd))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.width(12.fd))
                    GlassLottieButton(R.raw.btn_camera_gallery, "Choose a photo", {
                        gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }, diameter = 42.fd, iconSize = 22.fd)
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
                    TorchButton(torch, { torch = feed.setTorch(!torch) && !torch })
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
            OutlineLayer(OutlineScene(progress.outlines.map { o ->
                OutlineStroke(o.toView(mapping, picture), OutlineStyle.SCANNING)
            }), LocalDensity.current.density)
        }
        Row(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 14.fd, vertical = 6.fd),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassLottieButton(R.raw.icon_close, "Cancel", onCancel, diameter = 34.fd, iconSize = 18.fd)
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
                    Text(headings[current], color = TextPrimary, fontFamily = UiFamily, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
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
private fun PhotoItemsDoneScreen(picture: Bitmap, items: List<PhotoItem>, unit: LengthUnit, onCheck: () -> Unit, onRetake: () -> Unit, onTypeInstead: () -> Unit) {
    if (items.isEmpty()) {
        NothingFound(picture, "Couldn't find anything", "Fit the things in with a gap round each, in good light, and try again.", onRetake, onTypeInstead)
        return
    }
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(Unit) { reveal.animateTo(1f, tween(400, delayMillis = 50)) }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        PhotoWithOverlay(picture) { mapping ->
            OutlineLayer(OutlineScene(items.flatMap { it.outline }.map { OutlineStroke(it.toView(mapping, picture), OutlineStyle.MEASURED) }),
                LocalDensity.current.density, Modifier.fillMaxSize().alpha(reveal.value))
            AnchoredLabels(items.mapIndexed { i, item ->
                val (x, y) = mapping.toView(item.tag.first * picture.width.toDouble(), item.tag.second * picture.height.toDouble())
                AnchoredLabel("t$i", x / mapping.viewW, y / mapping.viewH, AnchorAlign.Above) {
                    ObjectTag(item.name ?: "Item ${i + 1}", ScanBadge.Measured, 1f, compact = items.size > 2)
                }
            }, Modifier.fillMaxSize())
        }
        GlassLottieButton(R.raw.icon_restore, "Retake photo", onRetake, diameter = 34.fd, iconSize = 18.fd,
            modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing).padding(14.fd))
        Sheet(Modifier.align(Alignment.BottomCenter)) {
            DoneHeading(if (items.size == 1) "Found 1 thing" else "Found ${items.size} things", "Sizes from one photo. Check each one next.")
            Spacer(Modifier.height(14.dp))
            Column(Modifier.fillMaxWidth().border(1.dp, Outline, RoundedCornerShape(16.dp)).padding(horizontal = 12.dp, vertical = 4.dp)) {
                items.take(5).forEachIndexed { i, item ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Thumb(item.crop, 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(item.name ?: "Item ${i + 1}", color = TextPrimary, fontFamily = UiFamily, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Spacer(Modifier.width(8.dp))
                                PhotoBadge("FROM PHOTO")
                            }
                            Text(sizeLine(item.dimensions, item.shape, unit), color = TextSecondary, fontFamily = NumericFamily, fontSize = 12.5.sp)
                        }
                    }
                }
                if (items.size > 5) Text("and ${items.size - 5} more", color = TextTertiary, fontFamily = UiFamily, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
            }
            Spacer(Modifier.height(14.dp))
            PrimaryButton("Check the sizes", onCheck)
            PackTextButton("Retake photo", onRetake, Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

@Composable
private fun PhotoSpaceDoneScreen(picture: Bitmap, space: PhotoSpace, label: String, unit: LengthUnit, onCheck: () -> Unit, onRetake: () -> Unit) {
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(Unit) { reveal.animateTo(1f, tween(400, delayMillis = 50)) }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        PhotoWithOverlay(picture) { mapping -> SpaceOverlay(space, picture, mapping, unit, reveal.value) }
        GlassLottieButton(R.raw.icon_restore, "Retake photo", onRetake, diameter = 34.fd, iconSize = 18.fd,
            modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing).padding(14.fd))
        Sheet(Modifier.align(Alignment.BottomCenter)) {
            DoneHeading("$label measured", "Inside size from one photo. Check it next.")
            Spacer(Modifier.height(14.dp))
            Column(Modifier.fillMaxWidth().background(Color(0xFFF7EFE6), RoundedCornerShape(16.dp)).padding(16.dp)) {
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
            PrimaryButton("Check the sizes", onCheck)
            PackTextButton("Retake photo", onRetake, Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

/** The space's box, opening and W / D / H pills over the photo. */
@Composable
private fun SpaceOverlay(space: PhotoSpace, picture: Bitmap, mapping: ViewMapping, unit: LengthUnit, alpha: Float) {
    OutlineLayer(OutlineScene(space.edges.map { (e, opening) ->
        OutlineStroke(e.toView(mapping, picture), if (opening) OutlineStyle.OPENING else OutlineStyle.MEASURED)
    }), LocalDensity.current.density, Modifier.fillMaxSize().alpha(alpha))
    fun at(p: Pair<Float, Float>?) = p?.let { mapping.toView(it.first * picture.width.toDouble(), it.second * picture.height.toDouble()) }
        ?.let { (x, y) -> x / mapping.viewW to y / mapping.viewH }
    AnchoredLabels(buildList {
        at(space.openingAt)?.let { (x, y) -> space.opening?.let { (w, h) ->
            add(AnchoredLabel("o", x, y, AnchorAlign.Above) { DimensionPill("OPENING", "${formatLength(w, unit)} × ${formatLength(h, unit)} ${unit.shortLabel}") })
        } }
        at(space.widthAt)?.let { (x, y) -> add(AnchoredLabel("w", x, y, AnchorAlign.Centre, movable = false) { DimensionPill("W", "${formatLength(space.dimensions.widthMm, unit)} ${unit.shortLabel}", appearDelayMillis = 100) }) }
        at(space.depthAt)?.let { (x, y) -> add(AnchoredLabel("d", x, y, AnchorAlign.Centre, movable = false) { DimensionPill("D", "${formatLength(space.dimensions.depthMm, unit)} ${unit.shortLabel}", appearDelayMillis = 190) }) }
        at(space.heightAt)?.let { (x, y) -> add(AnchoredLabel("h", x, y, AnchorAlign.Centre, movable = false) { DimensionPill("H", "${formatLength(space.dimensions.heightMm, unit)} ${unit.shortLabel}", highlight = true, appearDelayMillis = 280) }) }
    }, Modifier.fillMaxSize())
}

@Composable
private fun DoneHeading(title: String, subtitle: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        LottieOnce(R.raw.photo_done_check, Modifier.size(48.dp))
        Spacer(Modifier.width(14.dp))
        Column {
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
                    GlassLottieButton(R.raw.icon_restore, "Retake photo", onRetake, diameter = 28.dp, iconSize = 15.dp, modifier = Modifier.align(Alignment.TopEnd).padding(5.dp))
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
                GlassLottieButton(R.raw.icon_restore, "Retake photo", onRetake, diameter = 30.dp, iconSize = 16.dp, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp))
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
private fun Sheet(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val rise = remember { Animatable(1f) }
    LaunchedEffect(Unit) { rise.animateTo(0f, spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)) }
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

@Suppress("unused") private val keep = listOf(Ground, TextPrimary, mutableFloatStateOf(0f))
