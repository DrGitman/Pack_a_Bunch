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

    /**
     * Counts a finished pack and asks Google Play for its review sheet when due. True when it
     * asked, so the app's own feedback card waits for another pack rather than stacking on it.
     */
    fun packFinished(activity: Activity): Boolean {
        val prefs = activity.getSharedPreferences("review_prompt", Context.MODE_PRIVATE)
        val finished = prefs.getInt("packs_finished", 0) + 1
        prefs.edit().putInt("packs_finished", finished).apply()
        val now = System.currentTimeMillis()
        if (finished < 2 || now - prefs.getLong("asked_at", 0) < QUIET_DAYS * DAY_MS) return false
        prefs.edit().putLong("asked_at", now).apply()
        val manager = ReviewManagerFactory.create(activity)
        manager.requestReviewFlow().addOnCompleteListener { request ->
            if (request.isSuccessful) manager.launchReviewFlow(activity, request.result)
        }
        return true
    }

    /** How many packs this phone has seen finished. */
    fun packsFinished(context: Context): Int =
        context.getSharedPreferences("review_prompt", Context.MODE_PRIVATE).getInt("packs_finished", 0)

    /**
     * The app's own "How are we doing?" card: right after a finished pack, once or twice a week
     * for someone using the app — never sooner than three and a half days after the last one,
     * so a busy week of packing sees it twice at most and a quiet one once. It is only feedback
     * to us; it has nothing to do with the Play review above, which is asked for on its own and
     * whatever the stars were.
     */
    fun feedbackDue(context: Context): Boolean {
        val prefs = context.getSharedPreferences("review_prompt", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - prefs.getLong("feedback_at", 0) < FEEDBACK_GAP_MS) return false
        prefs.edit().putLong("feedback_at", now).apply()
        return true
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
    /** Half a week: twice a week at most. */
    private const val FEEDBACK_GAP_MS = 84 * 60 * 60 * 1000L
    private const val DAY_MS = 24 * 60 * 60 * 1000L
}
