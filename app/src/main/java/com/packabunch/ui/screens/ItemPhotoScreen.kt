package com.packabunch.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.packabunch.ui.components.Note
import com.packabunch.ui.components.NoteTone
import com.packabunch.ui.components.PackIconButton
import com.packabunch.ui.components.PackIcons
import com.packabunch.ui.components.PackTextButton
import com.packabunch.ui.components.PrimaryButton
import com.packabunch.ui.components.SecondaryButton
import com.packabunch.ui.theme.OnCamera
import com.packabunch.ui.theme.Spacing
import com.packabunch.ui.theme.TextPrimary
import com.packabunch.ui.theme.TextSecondary
import com.packabunch.ui.theme.UiFamily
import java.io.File
import java.util.concurrent.Executors

/**
 * Item photo — `design/artboards/ItemPhoto.dc.html`.
 *
 * A photo is for recognising the thing later, and that is all. **It does not set the size**,
 * which the screen states outright rather than leaving to be assumed: a single unscaled
 * photograph carries no metric information whatsoever, and any product that implies
 * otherwise is guessing.
 *
 * ### Camera ownership
 *
 * This uses CameraX, and the AR screens use ARCore. Both want exclusive access to the
 * camera, and holding one open while the other starts is a reliable way to get a black
 * preview or a hard failure on some devices. Two rules keep them apart:
 *
 *  - This screen binds on entry and unbinds in `onDispose`, so leaving it releases the
 *    camera before anything else can ask for it.
 *  - It is never composed at the same time as a measure or scan screen; navigation between
 *    them is a real destination change, not an overlay.
 *
 * Photos are written to app-private storage and never leave the device.
 */
@Composable
fun ItemPhotoScreen(
    itemName: String,
    onPhotoTaken: (path: String) -> Unit,
    onSkip: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var capturedPath by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasPermission = granted }

    if (!hasPermission) {
        CameraRationale(
            onAllow = { permissionLauncher.launch(Manifest.permission.CAMERA) },
            onSkip = onSkip,
            onBack = onBack,
            modifier = modifier,
        )
        return
    }

    val previewView = remember { PreviewView(context) }
    val imageCapture = remember { ImageCapture.Builder().build() }
    val executor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(lifecycleOwner) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null

        providerFuture.addListener({
            try {
                provider = providerFuture.get()
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                provider?.unbindAll()
                provider?.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageCapture,
                )
            } catch (t: Throwable) {
                error = "The camera couldn't start. You can add the photo later."
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            // Released here, not on a later screen. ARCore cannot start while CameraX holds
            // the camera, and the measure screen is one back-press away.
            provider?.unbindAll()
            executor.shutdown()
        }
    }

    Box(modifier.fillMaxSize().background(Color.Black)) {
        val path = capturedPath
        if (path == null) {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

            // The framing guide. Deliberately a plain rectangle: it says "fill this with one
            // item", and implies nothing about measurement.
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(280.dp)
                    .background(Color.Transparent, RoundedCornerShape(24.dp)),
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .border(2.dp, OnCamera, RoundedCornerShape(24.dp)),
                )
            }
        } else {
            val bitmap = remember(path) { android.graphics.BitmapFactory.decodeFile(path) }
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Photo of $itemName",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            PackIconButton(
                icon = PackIcons.Back,
                contentDescription = "Back",
                onClick = onBack,
                tint = Color.White,
                background = Color(0x55000000),
            )
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.White, RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = Spacing.gutter, vertical = 18.dp),
        ) {
            error?.let {
                Note(text = it, tone = NoteTone.Caution, icon = PackIcons.Warning)
                Spacer(Modifier.height(12.dp))
            }

            Text(
                text = if (capturedPath == null) "Fill the frame with one item" else "Keep this one?",
                color = TextPrimary,
                fontFamily = UiFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
            )
            Spacer(Modifier.height(6.dp))

            // The sentence this screen exists for.
            Text(
                text = "A photo helps you recognise the item later. It doesn't set the size — " +
                    "you still measure or type that yourself.",
                color = TextSecondary,
                fontFamily = UiFamily,
                fontSize = 14.sp,
                lineHeight = 21.sp,
            )

            Spacer(Modifier.height(14.dp))

            if (capturedPath == null) {
                PrimaryButton(
                    text = "Take the photo",
                    icon = PackIcons.Camera,
                    onClick = {
                        val file = File(
                            context.itemPhotoDirectory(),
                            "item-${System.currentTimeMillis()}.jpg",
                        )
                        val options = ImageCapture.OutputFileOptions.Builder(file).build()
                        imageCapture.takePicture(
                            options,
                            executor,
                            object : ImageCapture.OnImageSavedCallback {
                                override fun onImageSaved(
                                    result: ImageCapture.OutputFileResults,
                                ) {
                                    capturedPath = file.absolutePath
                                }

                                override fun onError(exception: ImageCaptureException) {
                                    error = "That photo didn't save. Try again, or skip it."
                                }
                            },
                        )
                    },
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryButton(
                        text = "Retake",
                        onClick = { capturedPath = null },
                        modifier = Modifier.weight(1f),
                    )
                    PrimaryButton(
                        text = "Use it",
                        onClick = { onPhotoTaken(path!!) },
                        modifier = Modifier.weight(1f),
                        height = 54.dp,
                    )
                }
            }

            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                PackTextButton(text = "Skip the photo", onClick = onSkip)
            }
        }
    }
}

/**
 * Camera rationale — `design/artboards/CameraRationale.dc.html`.
 *
 * Shown before Android's own prompt, so the request is explained rather than sprung. The
 * important part is the last line: declining leaves everything working.
 */
@Composable
fun CameraRationale(
    onAllow: () -> Unit,
    onSkip: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    com.packabunch.ui.components.ScreenScaffold(modifier) {
        com.packabunch.ui.components.PackAppBar(title = "Photo", onBack = onBack)

        Spacer(Modifier.height(Spacing.xl))

        Column(Modifier.padding(horizontal = Spacing.gutter)) {
            com.packabunch.ui.components.IconTile(
                icon = PackIcons.Camera,
                tint = com.packabunch.ui.theme.Primary,
                background = com.packabunch.ui.theme.BrandTint,
                size = 56.dp,
                iconSize = 26.dp,
            )
            Spacer(Modifier.height(Spacing.base))
            com.packabunch.ui.components.ScreenHeading("A photo needs the camera")
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Only for taking the picture, and it stays on this phone. If you say " +
                    "no, everything else carries on working exactly as it does now — you " +
                    "just won't have a picture on the item.",
                color = TextSecondary,
                fontFamily = UiFamily,
                fontSize = 15.sp,
                lineHeight = 23.sp,
            )
        }

        Spacer(Modifier.weight(1f))

        Column(
            Modifier.padding(horizontal = Spacing.gutter),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PrimaryButton(text = "Allow the camera", onClick = onAllow)
            SecondaryButton(text = "Skip the photo", onClick = onSkip)
        }

        Spacer(Modifier.height(Spacing.base))
    }
}

/**
 * App-private storage. Not the shared gallery: an item photo is part of somebody's pack,
 * not something to drop into their camera roll, and keeping it private is what lets the
 * app promise photos never leave the device.
 */
fun Context.itemPhotoDirectory(): File =
    File(filesDir, "item-photos").apply { if (!exists()) mkdirs() }
