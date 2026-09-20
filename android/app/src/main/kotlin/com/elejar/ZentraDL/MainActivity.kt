package com.elejar.ZentraDL

import android.content.Intent
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
import com.elejar.ZentraDL.ui.looksLikeLink
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val pending = extractShareUrl(intent)
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
                AppNav(pendingUrl = pending)
            }
        }
    }

    /** Validated share/view URL (http/https/magnet only); null when absent/invalid. */
    private fun extractShareUrl(intent: Intent?): String? {
        if (intent == null) return null
        val raw: String = when (intent.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_VIEW -> intent.data?.toString()
            Intent.ACTION_PROCESS_TEXT -> intent.getStringExtra(Intent.EXTRA_PROCESS_TEXT)
            else -> null
        } ?: return null
        // SEND text may surround the link with words — take the first link token.
        val candidate = raw.split(Regex("\\s+")).firstOrNull { looksLikeLink(it) } ?: raw.trim()
        return candidate.takeIf { looksLikeLink(it) }
    }
}
