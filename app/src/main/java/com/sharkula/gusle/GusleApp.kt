package com.sharkula.gusle

import android.app.Application
import com.sharkula.gusle.data.MusicLibrary

class GusleApp : Application() {
    /** The songs on the watch, shared by the screens and, later, the playback service. */
    val library by lazy { MusicLibrary(this) }
}
