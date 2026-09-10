package com.packabunch.ui.screens

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.opengl.GLSurfaceView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.packabunch.ar.*
import com.packabunch.packing.SweptObject
import com.packabunch.ui.components.*
import com.packabunch.ui.format.LengthUnit

@Composable
fun DepthCaptureGate(onManual: () -> Unit, onBack: () -> Unit, content: @Composable () -> Unit) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    var permission by remember { mutableStateOf(false) }
    var support by remember { mutableStateOf<ArSupport>(ArSupport.Checking) }
    var depth by remember { mutableStateOf<Boolean?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh++ }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(refresh) {
        permission = context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        do { support = ArAvailability.check(context); if (support == ArSupport.Checking) kotlinx.coroutines.delay(300) }
        while (support == ArSupport.Checking)
        if (permission && support == ArSupport.Ready) depth = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            ArAvailability.supportsDepth(context)
        }
    }
    if (permission && support == ArSupport.Ready && depth == true) { content(); return }
    ScreenScaffold {
        PackAppBar(title = "Camera scan", onBack = onBack)
        Note(text = when {
            !permission -> "Allow the camera to scan your space or items. Scans stay on this phone."
            support == ArSupport.NeedsInstall -> "Install or update Google Play Services for AR to scan."
            support == ArSupport.Checking || depth == null && support == ArSupport.Ready -> "Checking depth scanning support…"
            else -> "Depth scanning is unavailable on this phone. You can still enter measurements."
        })
        if (!permission) PrimaryButton(text = "Allow camera", onClick = { launcher.launch(Manifest.permission.CAMERA) })
        else if (support == ArSupport.NeedsInstall) PrimaryButton(text = "Install AR services", onClick = {
            (context as? Activity)?.let { ArAvailability.requestInstall(it, true) }; refresh++
        })
        SecondaryButton(text = "Type measurements instead", onClick = onManual)
    }
}

@Composable
fun LiveSweepScreen(unit: LengthUnit, onDone: (List<SweptObject>) -> Unit, onManual: () -> Unit, onBack: () -> Unit) {
    DepthCaptureGate(onManual, onBack) {
        val context = LocalContext.current
        val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
        val controller = remember { ArScanController(context, trackItems = true) }
        val state by controller.state.collectAsStateWithLifecycle()
        val objects by controller.objects.collectAsStateWithLifecycle()
        var error by remember { mutableStateOf<String?>(null) }
        val view = remember { GLSurfaceView(context).apply {
            preserveEGLContextOnPause = true
            setEGLContextClientVersion(2)
            setEGLConfigChooser(8, 8, 8, 8, 16, 0)
            setRenderer(controller)
        } }
        DisposableEffect(owner) {
            val observer = LifecycleEventObserver { _, event -> when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    error = controller.resume(view.display?.rotation ?: 0, view.width.coerceAtLeast(1), view.height.coerceAtLeast(1))
                    if (error == null) view.onResume()
                }
                Lifecycle.Event.ON_PAUSE -> { view.onPause(); controller.pause() }
                else -> Unit
            } }
            owner.lifecycle.addObserver(observer)
            onDispose { owner.lifecycle.removeObserver(observer); view.onPause(); controller.release() }
        }
        if (error != null || state.fatalError != null) ScreenScaffold {
            PackAppBar(title = "Item scan", onBack = onBack)
            Note(text = error ?: state.fatalError.orEmpty())
            SecondaryButton(text = "Type measurements instead", onClick = onManual)
        } else SweepItemsScreen(objects, unit, state.status == TrackingStatus.TRACKING,
            fallbackFor = { null }, onDone = onDone, onAddManually = onManual, onBack = onBack,
            cameraPreview = { AndroidView(factory = { view }, modifier = Modifier.fillMaxSize()) })
    }
}
