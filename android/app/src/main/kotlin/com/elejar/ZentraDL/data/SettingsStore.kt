package com.elejar.ZentraDL.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.prefs by preferencesDataStore("settings")

/** Settings v1 (more in Phase 2 → documented in docs/SETTINGS.md). */
@Singleton
class SettingsStore @Inject constructor(@ApplicationContext private val ctx: Context) {
    private val connectionsKey = intPreferencesKey("connections")

    val connections: Flow<Int> = ctx.prefs.data.map { it[connectionsKey] ?: 8 }

    suspend fun setConnections(n: Int) {
        ctx.prefs.edit { it[connectionsKey] = n.coerceIn(1, 32) }
    }
}
