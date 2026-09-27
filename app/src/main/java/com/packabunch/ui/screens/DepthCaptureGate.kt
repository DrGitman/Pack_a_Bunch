package com.packabunch.ui.screens

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.*
import com.packabunch.ar.*
import com.packabunch.ui.components.*

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

    // The permission belongs to the install, not the account, so "have we asked before" is
    // kept per phone. Android cannot answer this itself: shouldShowRequestPermissionRationale
    // reads false both before the first ask and after a permanent no.
    if (!permission) {
        val asks = remember { context.getSharedPreferences("camera_prompt", android.content.Context.MODE_PRIVATE) }
        // Held in Compose state as well as on disk. Denying the prompt leaves `permission`
        // false, so nothing else here changes and the screen would never swap over.
        var asked by remember { mutableStateOf(asks.getBoolean("asked", false)) }
        if (asked) {
            CameraDeniedScreen(
                onTypeInstead = onManual,
                onOpenSettings = {
                    context.startActivity(
                        android.content.Intent(
                            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            android.net.Uri.fromParts("package", context.packageName, null),
                        ),
                    )
                },
                onBack = onBack,
            )
        } else {
            CameraRationaleSheet(
                onContinue = {
                    asks.edit().putBoolean("asked", true).apply()
                    asked = true
                    launcher.launch(Manifest.permission.CAMERA)
                },
                onNotNow = onManual,
            )
        }
        return
    }

    if (support == ArSupport.NeedsInstall) {
        ArServicesInstallScreen(
            onInstall = {
                context.findActivity()?.let { ArAvailability.getArCore(it) }
                refresh++
            },
            onTypeInstead = onManual,
            onBack = onBack,
        )
        return
    }

    // Still asking ARCore. Nothing to say yet, and a wrong answer here is worse than a wait.
    if (support == ArSupport.Checking || (depth == null && support == ArSupport.Ready)) {
        ScreenScaffold {
            PackAppBar(title = "Measure with the camera", onBack = onBack)
            Note(text = "Checking depth scanning support…")
        }
        return
    }

    // Asking ARCore failed rather than answering "no". Saying "this phone can't measure by
    // camera" here would be a permanent, unfixable-sounding verdict on what may be a
    // temporary fault — the exact mistake ArSupport was split into four cases to avoid.
    (support as? ArSupport.Unknown)?.let { unknown ->
        ScreenScaffold {
            PackAppBar(title = "Measure with the camera", onBack = onBack)
            Note(
                title = "Couldn't check camera measuring",
                text = "Something went wrong asking this phone whether it can measure by " +
                    "camera (${unknown.reason}). Typed measurements work either way.",
                tone = NoteTone.Caution,
            )
            PrimaryButton(text = "Try again", onClick = { refresh++ })
            SecondaryButton(text = "Type the measurements", onClick = onManual)
        }
        return
    }

    ArUnavailableScreen(onTypeInstead = onManual, onBack = onBack)
}
