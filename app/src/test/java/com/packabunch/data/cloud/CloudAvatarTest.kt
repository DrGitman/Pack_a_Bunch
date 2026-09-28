package com.packabunch.data.cloud

import android.graphics.Bitmap
import android.net.Uri
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [28], manifest = Config.NONE)
class CloudAvatarTest {
    @Test fun validPictureSurvivesBoundsOnlyDecode() {
        val context = RuntimeEnvironment.getApplication()
        val file = java.io.File(context.cacheDir, "avatar-input.png")
        val bitmap = Bitmap.createBitmap(32, 24, Bitmap.Config.ARGB_8888)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val bytes = encodeAvatar(context, Uri.fromFile(file))
        assertNotNull("A valid picture must reach the upload step", bytes)
        assertTrue(bytes!!.isNotEmpty())
    }

    @Test fun invalidPictureIsRejected() {
        val context = RuntimeEnvironment.getApplication()
        val file = java.io.File(context.cacheDir, "invalid.png").apply { writeText("not a picture") }
        assertNull(encodeAvatar(context, Uri.fromFile(file)))
    }
}
