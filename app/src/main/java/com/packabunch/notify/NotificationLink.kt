package com.packabunch.notify

import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow

/** A tap on one of the app's phone notifications: the nav host opens the Notifications page. */
object NotificationLink {
    val pending = MutableStateFlow(false)

    fun offer(intent: Intent?) {
        if (intent?.getStringExtra(SystemNotifier.EXTRA_OPEN) == SystemNotifier.OPEN_NOTIFICATIONS) {
            intent.removeExtra(SystemNotifier.EXTRA_OPEN)
            pending.value = true
        }
    }
}
