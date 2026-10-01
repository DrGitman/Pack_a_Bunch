package com.packabunch.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.packabunch.ui.components.Note
import com.packabunch.ui.components.PackAppBar
import com.packabunch.ui.components.ScreenScaffold
import com.packabunch.ui.components.SecondaryButton

/**
 * Before any camera measuring: the camera permission, asked the friendly way.
 *
 * Measuring needs nothing but the camera — the depth model and the detector ship inside the app —
 * so the only things that can stop it are a refused permission or a phone with no camera at all.
 */
@Composable
fun CameraCaptureGate(onManual: () -> Unit, onBack: () -> Unit, content: @Composable () -> Unit) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    var permission by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh++ }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(refresh) {
        permission = context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
        ScreenScaffold {
            PackAppBar(title = "Measure with the camera", onBack = onBack)
            Note(text = "This device has no camera. Typed measurements give you the same plan.")
            SecondaryButton(text = "Type the measurements", onClick = onManual)
        }
        return
    }
    if (permission) { content(); return }

    // The permission belongs to the install, not the account, so "have we asked before" is
    // kept per phone. Android cannot answer this itself: shouldShowRequestPermissionRationale
    // reads false both before the first ask and after a permanent no.
    val asks = remember { context.getSharedPreferences("camera_prompt", android.content.Context.MODE_PRIVATE) }
    // Held in Compose state as well as on disk. Denying the prompt leaves `permission` false, so
    // nothing else here changes and the screen would never swap over.
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
}
