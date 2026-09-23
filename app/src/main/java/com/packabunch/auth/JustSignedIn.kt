package com.packabunch.auth

/**
 * Set when somebody signs in or signs up, cleared the first time it is read.
 *
 * The packs screen greets on arrival from a sign in and stays quiet every other time, and
 * only the sign in flow knows the difference. Restoring a saved session is not a sign in.
 */
object JustSignedIn {
    private var pending = false
    fun mark() { pending = true }
    fun consume(): Boolean = pending.also { pending = false }
}
