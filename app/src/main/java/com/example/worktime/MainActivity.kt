package com.example.worktime

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.worktime.data.Store
import com.example.worktime.timer.Notifs
import com.example.worktime.ui.ProjectsScreen
import com.example.worktime.ui.SettingsScreen
import com.example.worktime.ui.StatsScreen
import com.example.worktime.ui.TimerScreen
import com.example.worktime.ui.Segmented
import com.example.worktime.ui.theme.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Store.init(this)
        Notifs.ensureChannels(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        setContent {
            WorktimeTheme(darkTheme = Store.settings.darkTheme) {
                KeepScreenOn(Store.timer.running)
                Root()
            }
        }
    }
}

@Composable
private fun KeepScreenOn(on: Boolean) {
    val ctx = LocalContext.current
    DisposableEffect(on) {
        val window = (ctx as? android.app.Activity)?.window
        if (on) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}

private val tabs = listOf("Timer", "Stats", "Projects", "More")

@Composable
private fun Root() {
    var tab by remember { mutableStateOf(0) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Bg,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Bg)
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Segmented(tabs, tab, { tab = it })
            }
        }
    ) { inner ->
        Box(Modifier.padding(inner).statusBarsPadding()) {
            when (tab) {
                0 -> TimerScreen()
                1 -> StatsScreen()
                2 -> ProjectsScreen()
                else -> SettingsScreen()
            }
        }
    }
}
