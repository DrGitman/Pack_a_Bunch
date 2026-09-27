package com.packabunch.auth

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The password-reset link from the email, arriving back in the app.
 *
 * The reset email's link goes to Supabase, which checks it and sends the phone to
 * [REDIRECT] with a short-lived session in the fragment — or with an error, when the link was
 * used already or is over an hour old. [MainActivity][com.packabunch.MainActivity] hands every
 * such link here; the account screens pick it up and ask for the new password.
 */
object RecoveryLink {

    /** Where the reset email sends the phone. Must be listed in Supabase → Authentication → URL Configuration. */
    const val REDIRECT = "packabunch://reset-password"

    sealed interface Link {
        /** A good link: the refresh token of the session it opened. */
        data class Opened(val refreshToken: String) : Link

        /** A spent or expired link, in words for the person. */
        data class Refused(val message: String) : Link
    }

    val pending = MutableStateFlow<Link?>(null)

    /** Takes [uri] if it is a reset link. True when it was one. */
    fun offer(uri: Uri?): Boolean {
        if (uri == null || uri.scheme != "packabunch" || uri.host != "reset-password") return false
        // Supabase puts the session in the fragment, and errors in either the fragment or the query.
        val values = HashMap<String, String>()
        for (part in listOfNotNull(uri.encodedQuery, uri.encodedFragment)) {
            for (pair in part.split('&')) {
                val k = pair.substringBefore('=')
                val v = pair.substringAfter('=', "")
                if (k.isNotEmpty()) values[Uri.decode(k)] = Uri.decode(v.replace('+', ' '))
            }
        }
        val refresh = values["refresh_token"]
        pending.value = when {
            refresh != null && values["error"] == null -> Link.Opened(refresh)
            values["error_code"] == "otp_expired" ->
                Link.Refused("That reset link has expired or was used already. Links work once, for an hour. Ask for a new one.")
            else -> Link.Refused("That reset link didn't work. Ask for a new one.")
        }
        return true
    }
}
