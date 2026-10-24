package com.sharkula.gusle.ui.songs

import android.app.Activity
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit
import com.sharkula.gusle.R
import com.sharkula.gusle.data.Favorites
import com.sharkula.gusle.data.MusicLibrary
import com.sharkula.gusle.data.Playlists
import com.sharkula.gusle.data.Songs
import com.sharkula.gusle.playback.PlayingSongHolder
import com.sharkula.gusle.songDeleter
import com.sharkula.gusle.ui.components.Announcer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val Tag = "SongDeletion"

/** The song Android was asked to delete, until its answer arrives (see [SongDeleter.finishLeftover]). */
private const val DeletionPrefs = "song_deletion"
private const val AskedSongIdKey = "asked_song_id"

/**
 * A screen restored with Android's answer gets it as it's composed, just after the songs are read:
 * the leftover check waits this long, so it doesn't do that screen's work too.
 */
private const val AnswerGraceMs = 2_000L

/**
 * Does what follows once a song is deleted from the watch, or couldn't be. The app keeps one: its
 * work outlives the song's actions screen, which closes as the song goes, and the navigation, which
 * closes too when the last song on the watch is gone ("No songs yet"). Use it from the main thread.
 *
 * Android's answer can be lost: its screen is see-through, so Gusle stays visible under it, without
 * saved state, and if Gusle's process dies meanwhile (low memory), Android drops Gusle's screen and
 * the answer with it. So the song asked about is noted on disk until the answer arrives, and the
 * next time Gusle starts it finishes the work for a song that's gone ([finishLeftover]).
 */
class SongDeleter(
    private val context: Context,
    private val library: MusicLibrary,
    private val playing: PlayingSongHolder,
    private val favorites: () -> Favorites,
    private val playlists: () -> Playlists,
    private val announcer: Announcer,
) {
    private val scope = MainScope()

    private val prefs by lazy { context.getSharedPreferences(DeletionPrefs, Context.MODE_PRIVATE) }

    /** The song whose deletion [deleted] is finishing, so [finishLeftover] leaves it alone. */
    private var finishing: Long? = null

    /** Android is about to ask about the song with [songId]: noted until its answer arrives. */
    internal fun asking(songId: Long) {
        // In the background, within milliseconds: Gusle's process would have to die sooner for the
        // note to be lost. Nothing waits for it, as Gusle only pauses under Android's screen.
        prefs.edit { putLong(AskedSongIdKey, songId) }
    }

    /** Android's answer about the song with [songId] arrived, and what follows is done. */
    private fun answered(songId: Long) {
        if (prefs.getLong(AskedSongIdKey, -1) == songId) prefs.edit { remove(AskedSongIdKey) }
    }

    /**
     * Finishes what a lost answer left undone, once the songs are read: if the song Android was last
     * asked about is gone, it leaves the saved queue, the favorites and the playlists, as after
     * Allow, and is no longer noted. Run once as the app starts. An answer that does arrive, to a
     * screen Gusle restored, is left to that screen ([AnswerGraceMs]). A song that's still there stays noted:
     * Android's screen may still be waiting for the user, and a later Allow could again go unheard.
     * The next song asked about replaces it.
     */
    suspend fun finishLeftover() {
        val songId = withContext(Dispatchers.IO) { prefs.getLong(AskedSongIdKey, -1) }.takeIf { it >= 0 } ?: return
        library.songs.first { it is Songs.Loaded }
        delay(AnswerGraceMs)
        if (finishing == songId || prefs.getLong(AskedSongIdKey, -1) != songId) return
        val songs = library.songs.value as? Songs.Loaded ?: return
        if (songId in songs.byId || stillThere(songId)) return
        Log.i(Tag, "Song $songId was deleted, but Android's answer never arrived; forgetting it now")
        playing.forgetDeleted(songId)
        favorites().forget(songId)
        playlists().forget(songId)
        answered(songId)
    }

    /**
     * Android said the song with [songId] is deleted, which its actions screen asked for
     * ([deletion]). It leaves the player's queue first, at once (or the saved one), so the player
     * has moved on before the songs are read again: then every screen shows it gone, and its
     * actions screen closes, back where it was held. Once its file is surely gone, it's no longer a
     * favorite nor in any playlist, and screen readers hear it's done. If the file is still there, the screen says it
     * couldn't be deleted; it has left the queue, though, as Android said it was gone.
     */
    fun deleted(songId: Long, deletion: SongDeletion) {
        finishing = songId
        playing.forgetDeleted(songId)
        scope.launch {
            library.refresh().join()
            // Asked of the file itself: a read that failed keeps the songs from before.
            if (stillThere(songId)) {
                Log.w(Tag, "Song $songId is still there after Android deleted it")
                couldntDelete(deletion, rescan = false)
            } else {
                favorites().forget(songId)
                playlists().forget(songId)
                announcer.say(context.getString(R.string.deleted))
            }
            answered(songId)
            if (finishing == songId) finishing = null
        }
    }

    /** Android's answer was no, or its screen couldn't be shown: nothing follows. */
    internal fun notDeleted(songId: Long) {
        answered(songId)
    }

    /**
     * Android couldn't be asked, or [deletion]'s song is still there: its screen says so, screen
     * readers hear it, and the songs are read again ([rescan]), so every screen shows what's on the
     * watch.
     */
    fun couldntDelete(deletion: SongDeletion, rescan: Boolean = true) {
        deletion.failed()
        announcer.say(context.getString(R.string.couldnt_delete))
        if (rescan) library.refresh()
    }

    /** Whether MediaStore still has the song; when it can't say, Android's word that it's gone counts. */
    private suspend fun stillThere(songId: Long): Boolean = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.query(uriOf(songId), arrayOf(MediaStore.Audio.Media._ID), null, null, null)
                ?.use { it.count > 0 } ?: false
        } catch (e: RuntimeException) {
            Log.w(Tag, "Couldn't check whether song $songId is gone", e)
            false
        }
    }
}

/** The song with [songId], as MediaStore knows it (and [MusicLibrary] reads it). */
private fun uriOf(songId: Long): Uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, songId)

/** Where deleting a song is, on its actions screen. */
enum class DeletionStep {
    /** Nothing asked, or it was cancelled. */
    None,

    /** Gusle asks whether to delete it. */
    Asking,

    /** Android asks, over Gusle. */
    InAndroid,

    /** Android deleted it; the screen closes once the songs are read again. */
    Deleted,

    /** It couldn't be deleted: the screen says so. */
    Failed,
}

/**
 * Kept with the screen when Gusle is recreated: Gusle's question, or that it couldn't be deleted.
 * Not the steps in between: Android's answer arrives anyway, and a screen restored as
 * [DeletionStep.Deleted] would stay held if what follows went to the screen from before.
 */
private val StepSaver = Saver<MutableState<DeletionStep>, String>(
    save = { state ->
        state.value.takeIf { it == DeletionStep.Asking || it == DeletionStep.Failed }?.name ?: DeletionStep.None.name
    },
    restore = { mutableStateOf(DeletionStep.valueOf(it)) },
)

/**
 * Deleting the song with [songId] from its actions screen: Gusle asks first ([ask]), then Android,
 * whose own screen deletes the file on Allow ([MediaStore.createDeleteRequest]): Gusle can only read
 * the songs, and Wear Files saved them, so only the user may let it delete one. What follows is
 * [deleter]'s. By the song's ID, not the song: once Gusle is restored, a deleted one is no longer
 * among the songs, but Android's answer still has to arrive.
 */
@Stable
class SongDeletion internal constructor(
    private val songId: Long,
    private val context: Context,
    private val deleter: SongDeleter,
    private val stepState: MutableState<DeletionStep>,
) {
    var step: DeletionStep
        get() = stepState.value
        private set(value) {
            stepState.value = value
        }

    /** Starts Android's screen; set as the screen is composed. */
    internal var launcher: ManagedActivityResultLauncher<IntentSenderRequest, ActivityResult>? = null

    /** A tap on Delete: Gusle's question. */
    fun ask() {
        if (step == DeletionStep.None || step == DeletionStep.Failed) step = DeletionStep.Asking
    }

    /** Gusle's question was turned down, swiped away, or its screen left. */
    fun dismiss() {
        if (step == DeletionStep.Asking) step = DeletionStep.None
    }

    /** Yes to Gusle's question: now Android asks. */
    fun confirm() {
        if (step != DeletionStep.Asking) return
        val launcher = launcher ?: return
        step = DeletionStep.InAndroid
        val request = try {
            MediaStore.createDeleteRequest(context.contentResolver, listOf(uriOf(songId)))
        } catch (e: RuntimeException) {
            Log.w(Tag, "Couldn't ask Android to delete song $songId", e)
            deleter.couldntDelete(this)
            return
        }
        deleter.asking(songId)
        launcher.launch(IntentSenderRequest.Builder(request).build())
    }

    /** Android's answer: Allow deleted it; Deny changes nothing. */
    internal fun answered(result: ActivityResult) {
        when {
            result.resultCode == Activity.RESULT_OK -> {
                step = DeletionStep.Deleted
                deleter.deleted(songId, this)
            }
            // Android's screen couldn't be shown.
            result.data?.hasExtra(StartIntentSenderForResult.EXTRA_SEND_INTENT_EXCEPTION) == true -> {
                Log.w(Tag, "Couldn't show Android's screen to delete song $songId")
                deleter.notDeleted(songId)
                deleter.couldntDelete(this)
            }
            else -> {
                deleter.notDeleted(songId)
                step = DeletionStep.None
            }
        }
    }

    internal fun failed() {
        step = DeletionStep.Failed
    }
}

/**
 * Deleting the song with [songId] from its actions screen. Call it before anything that may leave
 * the screen empty, e.g. its song no longer read: Android's answer arrives where this is.
 */
@Composable
fun rememberSongDeletion(songId: Long): SongDeletion {
    val context = LocalContext.current
    val deleter = context.songDeleter
    val step = rememberSaveable(saver = StepSaver) { mutableStateOf(DeletionStep.None) }
    val deletion = remember(songId, deleter, step) { SongDeletion(songId, context.applicationContext, deleter, step) }
    // Kept with the screen's saved state, so Android's answer still arrives after Gusle is recreated.
    val launcher = rememberLauncherForActivityResult(StartIntentSenderForResult()) { deletion.answered(it) }
    SideEffect { deletion.launcher = launcher }
    return deletion
}
