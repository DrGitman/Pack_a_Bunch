package com.packabunch.ar

import android.app.Activity
import android.content.Context
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Session

/**
 * Whether AR can run here, answered honestly.
 *
 * Four different "no"s, because they need four different screens. Lumping them into one
 * "AR unavailable" would tell somebody with an out-of-date ARCore that their phone cannot
 * do it, which is untrue and unfixable-looking.
 */
sealed interface ArSupport {
    /** Ready to go. */
    data object Ready : ArSupport

    /** Supported, but ARCore needs installing or updating first — recoverable. */
    data object NeedsInstall : ArSupport

    /** The hardware cannot do it, and never will. Do not offer to retry. */
    data object NotSupported : ArSupport

    /** Still being determined; ARCore answers asynchronously on first call. */
    data object Checking : ArSupport

    /** Something went wrong asking. Treated as unavailable, never as available. */
    data class Unknown(val reason: String) : ArSupport
}

object ArAvailability {

    /**
     * Non-blocking check.
     *
     * ARCore's availability query can be transiently "unknown" while it works things out,
     * so this returns [ArSupport.Checking] and expects to be called again rather than
     * blocking or guessing. Guessing here would mean showing a camera button that leads
     * nowhere.
     */
    fun check(context: Context): ArSupport = try {
        when (val availability = ArCoreApk.getInstance().checkAvailability(context)) {
            ArCoreApk.Availability.SUPPORTED_INSTALLED -> ArSupport.Ready

            ArCoreApk.Availability.SUPPORTED_APK_TOO_OLD,
            ArCoreApk.Availability.SUPPORTED_NOT_INSTALLED,
            -> ArSupport.NeedsInstall

            ArCoreApk.Availability.UNSUPPORTED_DEVICE_NOT_CAPABLE -> ArSupport.NotSupported

            ArCoreApk.Availability.UNKNOWN_CHECKING -> ArSupport.Checking

            ArCoreApk.Availability.UNKNOWN_ERROR,
            ArCoreApk.Availability.UNKNOWN_TIMED_OUT,
            -> ArSupport.Unknown(availability.name)

            else -> ArSupport.Unknown(availability.name)
        }
    } catch (t: Throwable) {
        // ARCore not present on the classpath at runtime, or a vendor-specific failure.
        // Anything unexpected means no AR, never "assume it works".
        ArSupport.Unknown(t.message ?: t::class.java.simpleName)
    }

    /**
     * Whether this phone can produce depth, which mapping an irregular space needs.
     *
     * Answering it costs a short-lived [Session], because depth support is a property of a
     * configured session rather than something the availability check reports. The session
     * is never resumed, so the camera is not touched and no permission is required — and it
     * is closed immediately, because holding one open would block the real one later.
     *
     * Returns false on any failure. A phone wrongly told it can map would sweep for a
     * minute and get nothing, which is worse than not being offered it.
     */
    fun supportsDepth(context: Context): Boolean = try {
        if (check(context) !is ArSupport.Ready) {
            false
        } else {
            Session(context).use { session ->
                session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)
            }
        }
    } catch (t: Throwable) {
        false
    }

    /** `Session` is `Closeable` only on newer SDKs, so the close is explicit here. */
    private inline fun <T> Session.use(block: (Session) -> T): T = try {
        block(this)
    } finally {
        close()
    }

    /**
     * Asks ARCore to install itself. Returns true when the flow was started, meaning this
     * activity will be paused and should re-check when it resumes.
     */
    fun requestInstall(activity: Activity, userRequestedInstall: Boolean): Boolean = try {
        ArCoreApk.getInstance().requestInstall(activity, userRequestedInstall) ==
            ArCoreApk.InstallStatus.INSTALL_REQUESTED
    } catch (t: Throwable) {
        false
    }
}
