package com.elejar.ZentraDL

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.elejar.ZentraDL.designsystem.theme.Accent
import com.elejar.ZentraDL.designsystem.theme.ThemeMode
import com.elejar.ZentraDL.designsystem.theme.ZentraDLTheme
import com.elejar.ZentraDL.ui.AppNav
import com.elejar.ZentraDL.ui.SettingsViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // Theme comes from Settings (v1 in P2c); defaults keep first-run calm.
            val settingsVm: SettingsViewModel = hiltViewModel()
            val mode by settingsVm.themeMode.collectAsState()
            val accent by settingsVm.accent.collectAsState()
            val dynamic by settingsVm.dynamicColor.collectAsState()
            ZentraDLTheme(
                mode = runCatching { ThemeMode.valueOf(mode) }.getOrDefault(ThemeMode.System),
                accent = runCatching { Accent.valueOf(accent) }.getOrDefault(Accent.Blue),
                dynamic = dynamic,
            ) {
                AppNav()
            }
        }
    }
}
