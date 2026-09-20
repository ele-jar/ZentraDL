package com.elejar.ZentraDL.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.prefs by preferencesDataStore("settings")

/** App settings (documented in docs/SETTINGS.md as they land). */
@Singleton
class SettingsStore @Inject constructor(@ApplicationContext private val ctx: Context) {
    private val connectionsKey = intPreferencesKey("connections")
    private val maxRunningKey = intPreferencesKey("max_running")
    private val densityKey = stringPreferencesKey("density")
    private val sortKey = stringPreferencesKey("sort")
    private val themeModeKey = stringPreferencesKey("theme_mode")
    private val accentKey = stringPreferencesKey("accent")
    private val dynamicColorKey = booleanPreferencesKey("dynamic_color")

    val connections: Flow<Int> = ctx.prefs.data.map { it[connectionsKey] ?: 8 }
    val maxRunning: Flow<Int> = ctx.prefs.data.map { it[maxRunningKey] ?: 3 }
    val density: Flow<String> = ctx.prefs.data.map { it[densityKey] ?: "comfortable" }
    val sort: Flow<String> = ctx.prefs.data.map { it[sortKey] ?: "date" }
    val themeMode: Flow<String> = ctx.prefs.data.map { it[themeModeKey] ?: "system" }
    val accent: Flow<String> = ctx.prefs.data.map { it[accentKey] ?: "blue" }
    val dynamicColor: Flow<Boolean> = ctx.prefs.data.map { it[dynamicColorKey] ?: true }

    suspend fun setConnections(n: Int) {
        ctx.prefs.edit { it[connectionsKey] = n.coerceIn(1, 32) }
    }

    suspend fun setMaxRunning(n: Int) {
        ctx.prefs.edit { it[maxRunningKey] = n.coerceIn(1, 30) }
    }

    suspend fun setDensity(v: String) {
        ctx.prefs.edit { it[densityKey] = v }
    }

    suspend fun setSort(v: String) {
        ctx.prefs.edit { it[sortKey] = v }
    }

    suspend fun setThemeMode(v: String) {
        ctx.prefs.edit { it[themeModeKey] = v }
    }

    suspend fun setAccent(v: String) {
        ctx.prefs.edit { it[accentKey] = v }
    }

    suspend fun setDynamicColor(v: Boolean) {
        ctx.prefs.edit { it[dynamicColorKey] = v }
    }
}
