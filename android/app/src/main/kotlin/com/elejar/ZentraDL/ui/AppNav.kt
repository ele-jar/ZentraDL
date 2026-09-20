package com.elejar.ZentraDL.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.serialization.Serializable

@Serializable
object Downloads

@Serializable
data class Details(val id: String)

/** App navigation (P2c: Downloads + Details; Browser/Activity/Settings land later). */
@Composable
fun AppNav() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Downloads) {
        composable<Downloads> {
            DownloadsScreen(onDetails = { id -> nav.navigate(Details(id)) })
        }
        composable<Details> {
            DetailsScreen(onBack = { nav.popBackStack() }, onDeleted = { nav.popBackStack() })
        }
    }
}
