package com.elejar.ZentraDL

import android.app.KeyguardManager
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.elejar.ZentraDL.designsystem.theme.Accent
import com.elejar.ZentraDL.designsystem.theme.ThemeMode
import com.elejar.ZentraDL.designsystem.theme.ZentraDLTheme
import com.elejar.ZentraDL.ui.AppNav
import com.elejar.ZentraDL.ui.SettingsViewModel
import com.elejar.ZentraDL.ui.looksLikeLink
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    companion object {
        const val ACTION_ADD = "com.elejar.ZentraDL.action.ADD"
        const val EXTRA_OPEN_ADD = "open_add"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val pending = extractShareUrl(intent)
        val openAdd = intent?.action == ACTION_ADD || intent?.getBooleanExtra(EXTRA_OPEN_ADD, false) == true
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
                GatedNav(pendingUrl = pending, openAdd = openAdd)
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

/**
 * Device-credential gate (P3e app lock). Prompts on start and every resume
 * while enabled; a failed/cancelled check closes the app. No credential
 * enrolled → treat as unlocked (nothing to check against).
 */
@Composable
private fun GatedNav(pendingUrl: String?, openAdd: Boolean, settingsVm: SettingsViewModel = hiltViewModel()) {
    val ctx = LocalContext.current
    val appLock by settingsVm.appLock.collectAsState()
    var unlocked by remember { mutableStateOf(false) }
    val activity = ctx as? ComponentActivity
    val lockTitle = stringResource(R.string.app_lock_title)
    val lockDesc = stringResource(R.string.app_lock_desc)
    val credLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) unlocked = true else activity?.finish()
    }
    @Suppress("DEPRECATION") // Keyguard credential avoids a Biometric dep; still functional.
    fun prompt() {
        val km = ctx.getSystemService(KeyguardManager::class.java)
        val intent = km?.createConfirmDeviceCredentialIntent(lockTitle, lockDesc)
        if (intent != null) credLauncher.launch(intent) else unlocked = true
    }
    LaunchedEffect(appLock) {
        if (appLock && !unlocked) prompt() else if (!appLock) unlocked = true
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, appLock) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME && appLock) unlocked = false
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }
    // Re-prompt after a resume re-lock.
    LaunchedEffect(unlocked, appLock) {
        if (appLock && !unlocked) prompt()
    }
    if (unlocked || !appLock) {
        AppNav(pendingUrl = pendingUrl, openAdd = openAdd)
    } else {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}
