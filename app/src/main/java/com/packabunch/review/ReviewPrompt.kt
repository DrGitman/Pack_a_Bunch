package com.packabunch.review

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.google.android.play.core.review.ReviewManagerFactory

/**
 * Asking for a Play review, and taking feedback privately.
 *
 * ### When
 * After a person has finished packing their second pack — the moment the app has just done
 * its job twice — and never more than once in [QUIET_DAYS]. Play throttles the prompt further
 * on its own, so it may not appear even then, and nothing here depends on whether it did.
 *
 * ### What it deliberately does not do
 * It does not ask "Do you like the app?" first and send only the happy answers to the store.
 * Google Play's In-App Review policy forbids any question before the prompt that predicts the
 * rating, and apps that gate reviews that way are removed. Unhappy people are not hidden;
 * they have "Get in touch" in Settings, always, which goes straight to [feedback].
 */
object ReviewPrompt {

    fun packFinished(activity: Activity) {
        val prefs = activity.getSharedPreferences("review_prompt", Context.MODE_PRIVATE)
        val finished = prefs.getInt("packs_finished", 0) + 1
        prefs.edit().putInt("packs_finished", finished).apply()
        val now = System.currentTimeMillis()
        if (finished < 2 || now - prefs.getLong("asked_at", 0) < QUIET_DAYS * 24 * 60 * 60 * 1000L) return
        prefs.edit().putLong("asked_at", now).apply()
        val manager = ReviewManagerFactory.create(activity)
        manager.requestReviewFlow().addOnCompleteListener { request ->
            if (request.isSuccessful) manager.launchReviewFlow(activity, request.result)
        }
    }

    /**
     * Opens the person's email app addressed to support, with the version filled in so a bug
     * report says what it is about. False when the phone has no email app, so the caller can
     * show the address instead.
     */
    fun feedback(context: Context, address: String, version: String): Boolean {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
            putExtra(Intent.EXTRA_EMAIL, arrayOf(address))
            putExtra(Intent.EXTRA_SUBJECT, "Pack a Bunch feedback ($version)")
        }
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    private const val QUIET_DAYS = 90
}
