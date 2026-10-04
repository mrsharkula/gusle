package com.sharkula.gusle

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.wear.compose.material3.AppScaffold
import com.sharkula.gusle.ui.home.HomeScreen
import com.sharkula.gusle.ui.theme.GusleTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GusleTheme {
                AppScaffold {
                    HomeScreen(onAllSongs = {}, onSettings = {})
                }
            }
        }
    }
}
