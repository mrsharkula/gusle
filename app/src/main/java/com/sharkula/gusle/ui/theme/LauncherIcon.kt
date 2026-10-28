package com.sharkula.gusle.ui.theme

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.PackageManager.ComponentEnabledSetting
import android.util.Log
import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private const val Tag = "LauncherIcon"

/** Whose icon the launcher shows: [theme]'s while the icon follows the theme ([follows]), else the default's. */
fun launcherIconTheme(theme: AppTheme, follows: Boolean): AppTheme = if (follows) theme else AppTheme.Default

/**
 * Keeps the launcher icon in step with Settings → Theme. Android can't change an installed app's
 * icon, so the manifest has one launcher alias of MainActivity per [AppTheme], each with the icon
 * in that theme's colors ([AppTheme.launcherAlias]), and exactly one is enabled: the one of
 * [launcherIconTheme]. A change may be due when either choice changes ([sync]) and as Gusle starts
 * (the choices come back from a backup, the aliases' states don't); [applyIfPending] makes it, and
 * MainActivity calls that as it leaves the screen, not before: Android removes the task that was
 * started from an alias when that alias is disabled (seen on the Wear OS 6 emulator: Gusle closed
 * to the app list), so the change waits until Gusle is off the screen, where that only means the
 * next start is a fresh one. The alias names must stay forever: one that vanished while enabled
 * would take Gusle out of the app list.
 */
class LauncherIcon(
    context: Context,
    private val theme: () -> ThemeChoice,
    private val follows: () -> IconThemeChoice,
) {
    private val context = context.applicationContext

    /** Whether the enabled alias may not be the wanted one. From the start: see the class. */
    private var pending = true

    /** One switch at a time: two at once could both read the old states and leave two aliases enabled. */
    private val oneAtATime = Mutex()

    /** Notes that the icon may have to change; [applyIfPending] changes it. */
    @MainThread
    fun sync() {
        pending = true
    }

    /**
     * Enables the wanted alias and disables the others, in the background, if a change may be due.
     * Nothing until both choices are read, and nothing lasting if the switch fails: it stays due for
     * the next time.
     */
    @MainThread
    fun applyIfPending() {
        if (!pending) return
        val theme = theme().current ?: return
        val follows = follows().current ?: return
        pending = false
        val wanted = launcherIconTheme(theme, follows)
        MainScope().launch {
            val done = withContext(Dispatchers.IO) {
                oneAtATime.withLock {
                    try {
                        enableOnly(wanted)
                        true
                    } catch (e: Exception) {
                        // E.g. the package manager out of reach for a moment. Not worth more than a line.
                        Log.w(Tag, "Couldn't switch the launcher icon to ${wanted.id}", e)
                        false
                    }
                }
            }
            if (!done) pending = true
        }
    }

    @WorkerThread
    private fun enableOnly(wanted: AppTheme) {
        val pm = context.packageManager
        val changes = AppTheme.entries.mapNotNull { theme ->
            val alias = ComponentName(context, theme.launcherAlias)
            val enabled = theme == wanted
            if (pm.isEnabled(alias, inManifest = theme == AppTheme.Default) == enabled) return@mapNotNull null
            ComponentEnabledSetting(
                alias,
                if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
        }
        // One call, so the launcher hears of it once; the wanted one first, so there's never none.
        if (changes.isNotEmpty()) {
            pm.setComponentEnabledSettings(changes.sortedByDescending { it.enabledState == PackageManager.COMPONENT_ENABLED_STATE_ENABLED })
        }
    }
}

/** Whether [component] is enabled now; [inManifest] is what its manifest entry says, which "default" means. */
private fun PackageManager.isEnabled(component: ComponentName, inManifest: Boolean): Boolean =
    when (getComponentEnabledSetting(component)) {
        PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
        PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> inManifest
        else -> false
    }
