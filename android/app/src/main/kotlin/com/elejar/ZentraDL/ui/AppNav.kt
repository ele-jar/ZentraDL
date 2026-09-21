package com.elejar.ZentraDL.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.elejar.ZentraDL.R
import kotlinx.serialization.Serializable

@Serializable
object Downloads

@Serializable
data class Details(val id: String)

@Serializable
data class TorrentDetails(val id: String)

@Serializable
object Browser

@Serializable
object Activity

@Serializable
object Onboarding

@Serializable
object Settings

/** App navigation (P7a: + Onboarding first-run). */
@Composable
fun AppNav(pendingUrl: String? = null, openAdd: Boolean = false) {
    val nav = rememberNavController()
    val settingsVm: SettingsViewModel = hiltViewModel()
    val onboarded by settingsVm.onboarded.collectAsState()
    val entry by nav.currentBackStackEntryAsState()
    // Bottom bar on top-level destinations only.
    val topRoute = entry?.destination?.route
    val showBar = topRoute == Downloads::class.qualifiedName || topRoute == Settings::class.qualifiedName ||
        topRoute == Browser::class.qualifiedName || topRoute == Activity::class.qualifiedName
    Scaffold(
        bottomBar = {
            if (showBar) {
                NavigationBar {
                    NavigationBarItem(
                        selected = topRoute == Downloads::class.qualifiedName,
                        onClick = { nav.navigate(Downloads) { launchSingleTop = true } },
                        icon = { Icon(Icons.Filled.Download, contentDescription = null) },
                        label = { Text(stringResource(R.string.downloads_tab)) },
                    )
                    NavigationBarItem(
                        selected = topRoute == Browser::class.qualifiedName,
                        onClick = { nav.navigate(Browser) { launchSingleTop = true } },
                        icon = { Icon(Icons.Filled.Language, contentDescription = null) },
                        label = { Text(stringResource(R.string.browser_tab)) },
                    )
                    NavigationBarItem(
                        selected = topRoute == Activity::class.qualifiedName,
                        onClick = { nav.navigate(Activity) { launchSingleTop = true } },
                        icon = { Icon(Icons.Filled.BarChart, contentDescription = null) },
                        label = { Text(stringResource(R.string.activity_tab)) },
                    )
                    NavigationBarItem(
                        selected = topRoute == Settings::class.qualifiedName,
                        onClick = { nav.navigate(Settings) { launchSingleTop = true } },
                        icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                        label = { Text(stringResource(R.string.settings)) },
                    )
                }
            }
        },
    ) { pads ->
        NavHost(
            navController = nav,
            startDestination = if (onboarded) Downloads else Onboarding,
            modifier = Modifier.padding(pads),
        ) {
            composable<Onboarding> {
                OnboardingScreen(onDone = {
                    nav.navigate(Downloads) {
                        popUpTo(Onboarding) { inclusive = true }
                    }
                })
            }
            composable<Downloads> {
                DownloadsScreen(
                    onDetails = { id -> nav.navigate(Details(id)) },
                    onTorrentDetails = { id -> nav.navigate(TorrentDetails(id)) },
                    pendingUrl = pendingUrl,
                    forceAdd = openAdd,
                )
            }
            composable<Browser> {
                // Playlist links skip Downloads and land in the browser for sniffing.
                val isPlaylist = pendingUrl?.substringBefore('?')
                    ?.endsWith(".m3u8", ignoreCase = true) == true ||
                    pendingUrl?.substringBefore('?')
                        ?.endsWith(".mpd", ignoreCase = true) == true
                BrowserScreen(initialUrl = if (isPlaylist) pendingUrl ?: "" else "")
            }
            composable<Activity> {
                ActivityScreen()
            }
            composable<Details> {
                DetailsScreen(onBack = { nav.popBackStack() }, onDeleted = { nav.popBackStack() })
            }
            composable<TorrentDetails> {
                TorrentDetailsScreen(onBack = { nav.popBackStack() }, onDeleted = { nav.popBackStack() })
            }
            composable<Settings> {
                SettingsScreen()
            }
        }
    }
}
