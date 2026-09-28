package com.packabunch.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.packabunch.MainActivity
import com.packabunch.R

/**
 * The app's notices on the phone itself, in the shade and as heads-up banners, not only on the
 * Notifications page. Each carries the app's look as far as Android lets it: the brand
 * terracotta on the icon and app name, the Pack a Bunch box in the status bar, the logo tile
 * beside the text, and the whole message rather than one clipped line. Tapping one opens the
 * Notifications page.
 */
class SystemNotifier(private val context: Context) {

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "Packs and account", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "A pack left part packed, a pack finished, backups and Pack Plus."
                    lightColor = BRAND
                    enableLights(true)
                },
            )
        }
    }

    /** False until the person allows notifications (Android 13 and later ask for it). */
    fun allowed(): Boolean =
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun post(id: String, title: String, body: String, whenMillis: Long) {
        if (!allowed()) return
        val open = PendingIntent.getActivity(
            context, id.hashCode(),
            Intent(context, MainActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_OPEN, OPEN_NOTIFICATIONS),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(BRAND)
            .setLargeIcon(BitmapFactory.decodeResource(context.resources, R.drawable.logo_tile))
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body).setBigContentTitle(title))
            .setWhen(if (whenMillis > 0) whenMillis else System.currentTimeMillis())
            .setShowWhen(true)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id.hashCode(), notification)
        } catch (_: SecurityException) {
            // Permission taken away between the check and the post: the page still has it.
        }
    }

    companion object {
        private const val CHANNEL = "packs"
        /** The brand terracotta, #A65C34. */
        private const val BRAND = 0xFFA65C34.toInt()
        const val EXTRA_OPEN = "open"
        const val OPEN_NOTIFICATIONS = "notifications"
    }
}
