package com.sharkula.gusle

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.wear.compose.material3.AppScaffold
import com.sharkula.gusle.ui.GusleNavHost
import com.sharkula.gusle.ui.access.MusicAccessGate
import com.sharkula.gusle.ui.songs.SongsGate
import com.sharkula.gusle.ui.theme.GusleTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val library = (application as GusleApp).library
        setContent {
            GusleTheme {
                AppScaffold {
                    MusicAccessGate {
                        SongsGate(library) { songs ->
                            GusleNavHost(songs)
                        }
                    }
                }
            }
        }
    }
}
