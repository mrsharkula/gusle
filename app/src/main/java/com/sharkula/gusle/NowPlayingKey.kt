package com.sharkula.gusle

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.WorkerThread
import androidx.core.content.edit
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

private const val KeyPrefs = "now_playing_key"
private const val KeyKey = "key"

/** The key's length in random bytes: far too many to guess. */
private const val KeyBytes = 32

/**
 * A secret only Gusle knows, made once and kept on disk: the tile sends it with
 * [OpenNowPlayingExtra], and MainActivity opens Now playing only when it comes along. So other apps
 * can open Gusle, but still can't make it connect to the player, as with the alias the media
 * controls use. The app keeps one. It isn't backed up or moved to another watch (see
 * data_extraction_rules.xml): a new watch makes its own.
 */
class NowPlayingKey internal constructor(openPrefs: () -> SharedPreferences) {
    constructor(context: Context) : this(
        { context.applicationContext.getSharedPreferences(KeyPrefs, Context.MODE_PRIVATE) },
    )

    private val prefs by lazy(openPrefs)

    /** The key once it's been read or made in this process. */
    @Volatile
    private var known: String? = null

    /** The key, made the first time. Reads the disk the first time in a process. */
    @WorkerThread
    fun value(): String =
        known ?: synchronized(this) {
            known ?: (prefs.getString(KeyKey, null) ?: make()).also { known = it }
        }

    /**
     * Whether [sent] is the key. Reads the disk if the key isn't known in this process yet; that's
     * only for an intent that comes with one, i.e. a tap on the tile, never for a plain launch. It
     * doesn't make a key: a key sent before there was one is wrong.
     */
    fun matches(sent: String): Boolean {
        val key = known ?: synchronized(this) {
            known ?: prefs.getString(KeyKey, null)?.also { known = it }
        } ?: return false
        // In the same time however much of it is right.
        return MessageDigest.isEqual(sent.toByteArray(), key.toByteArray())
    }

    private fun make(): String {
        val bytes = ByteArray(KeyBytes).also(SecureRandom()::nextBytes)
        val key = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        // Written before it's used: a key lost with the process would make the tile's taps wrong.
        prefs.edit(commit = true) { putString(KeyKey, key) }
        return key
    }
}
