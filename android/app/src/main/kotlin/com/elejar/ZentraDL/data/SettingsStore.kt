package com.elejar.ZentraDL.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.elejar.ZentraDL.domain.GatePolicy
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
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
    private val wifiOnlyKey = booleanPreferencesKey("wifi_only")
    private val chargingOnlyKey = booleanPreferencesKey("charging_only")
    private val schedEnabledKey = booleanPreferencesKey("sched_enabled")
    private val schedStartKey = intPreferencesKey("sched_start_min")
    private val schedEndKey = intPreferencesKey("sched_end_min")
    private val speedLimitKbpsKey = intPreferencesKey("speed_limit_kbps")
    private val appLockKey = booleanPreferencesKey("app_lock")
    private val dhtEnabledKey = booleanPreferencesKey("dht_enabled")
    private val maxPeersKey = intPreferencesKey("max_peers_torrent")
    private val seedGoalKey = intPreferencesKey("seed_goal_ratio")
    private val adblockKey = booleanPreferencesKey("adblock_enabled")
    private val smartMasterKey = booleanPreferencesKey("smart_master")
    private val onboardedKey = booleanPreferencesKey("onboarded")
    private val quietEnabledKey = booleanPreferencesKey("quiet_enabled")
    private val quietStartKey = intPreferencesKey("quiet_start_min")
    private val quietEndKey = intPreferencesKey("quiet_end_min")
    private val failuresOnlyKey = booleanPreferencesKey("failures_only")
    private val hideTinyKey = booleanPreferencesKey("hide_tiny")

    val connections: Flow<Int> = ctx.prefs.data.map { it[connectionsKey] ?: 8 }
    val maxRunning: Flow<Int> = ctx.prefs.data.map { it[maxRunningKey] ?: 3 }
    val density: Flow<String> = ctx.prefs.data.map { it[densityKey] ?: "comfortable" }
    val sort: Flow<String> = ctx.prefs.data.map { it[sortKey] ?: "date" }
    val themeMode: Flow<String> = ctx.prefs.data.map { it[themeModeKey] ?: "System" }
    val accent: Flow<String> = ctx.prefs.data.map { it[accentKey] ?: "Blue" }
    val dynamicColor: Flow<Boolean> = ctx.prefs.data.map { it[dynamicColorKey] ?: true }
    val wifiOnly: Flow<Boolean> = ctx.prefs.data.map { it[wifiOnlyKey] ?: false }
    val chargingOnly: Flow<Boolean> = ctx.prefs.data.map { it[chargingOnlyKey] ?: false }
    val schedEnabled: Flow<Boolean> = ctx.prefs.data.map { it[schedEnabledKey] ?: false }
    val schedStartMin: Flow<Int> = ctx.prefs.data.map { it[schedStartKey] ?: 1320 }
    val schedEndMin: Flow<Int> = ctx.prefs.data.map { it[schedEndKey] ?: 360 }
    /** 0 = unlimited. */
    val speedLimitKbps: Flow<Int> = ctx.prefs.data.map { it[speedLimitKbpsKey] ?: 0 }
    val appLock: Flow<Boolean> = ctx.prefs.data.map { it[appLockKey] ?: false }
    val dhtEnabled: Flow<Boolean> = ctx.prefs.data.map { it[dhtEnabledKey] ?: true }
    val maxPeers: Flow<Int> = ctx.prefs.data.map { it[maxPeersKey] ?: 50 }
    /** Seed goal ratio ×100 (0 = seed forever). */
    val seedGoal: Flow<Int> = ctx.prefs.data.map { it[seedGoalKey] ?: 0 }
    val adblock: Flow<Boolean> = ctx.prefs.data.map { it[adblockKey] ?: true }
    val smartMaster: Flow<Boolean> = ctx.prefs.data.map { it[smartMasterKey] ?: true }
    val onboarded: Flow<Boolean> = ctx.prefs.data.map { it[onboardedKey] ?: false }
    val quietEnabled: Flow<Boolean> = ctx.prefs.data.map { it[quietEnabledKey] ?: false }
    val quietStartMin: Flow<Int> = ctx.prefs.data.map { it[quietStartKey] ?: 1320 }
    val quietEndMin: Flow<Int> = ctx.prefs.data.map { it[quietEndKey] ?: 420 }
    val failuresOnly: Flow<Boolean> = ctx.prefs.data.map { it[failuresOnlyKey] ?: false }
    /** Hide "completed" notifications for files under 1 MB. */
    val hideTiny: Flow<Boolean> = ctx.prefs.data.map { it[hideTinyKey] ?: false }

    /** Combined queue-gate policy (P3d). */
    val gatePolicy: Flow<GatePolicy> = combine(
        wifiOnly, chargingOnly, schedEnabled, schedStartMin, schedEndMin,
    ) { wifi, charging, sched, start, end ->
        GatePolicy(wifi, charging, if (sched) start else -1, if (sched) end else -1)
    }

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

    suspend fun setWifiOnly(v: Boolean) {
        ctx.prefs.edit { it[wifiOnlyKey] = v }
    }

    suspend fun setChargingOnly(v: Boolean) {
        ctx.prefs.edit { it[chargingOnlyKey] = v }
    }

    suspend fun setSchedEnabled(v: Boolean) {
        ctx.prefs.edit { it[schedEnabledKey] = v }
    }

    suspend fun setSchedStartMin(v: Int) {
        ctx.prefs.edit { it[schedStartKey] = v.coerceIn(0, 1439) }
    }

    suspend fun setSchedEndMin(v: Int) {
        ctx.prefs.edit { it[schedEndKey] = v.coerceIn(0, 1440) }
    }

    suspend fun setSpeedLimitKbps(v: Int) {
        ctx.prefs.edit { it[speedLimitKbpsKey] = v.coerceIn(0, 102_400) }
    }

    suspend fun setAppLock(v: Boolean) {
        ctx.prefs.edit { it[appLockKey] = v }
    }

    suspend fun setDhtEnabled(v: Boolean) {
        ctx.prefs.edit { it[dhtEnabledKey] = v }
    }

    suspend fun setMaxPeers(v: Int) {
        ctx.prefs.edit { it[maxPeersKey] = v.coerceIn(5, 200) }
    }

    suspend fun setSeedGoal(v: Int) {
        ctx.prefs.edit { it[seedGoalKey] = v.coerceIn(0, 1000) }
    }

    suspend fun setAdblock(v: Boolean) {
        ctx.prefs.edit { it[adblockKey] = v }
    }

    suspend fun setSmartMaster(v: Boolean) {
        ctx.prefs.edit { it[smartMasterKey] = v }
    }

    suspend fun setOnboarded() {
        ctx.prefs.edit { it[onboardedKey] = true }
    }

    suspend fun setQuietEnabled(v: Boolean) {
        ctx.prefs.edit { it[quietEnabledKey] = v }
    }

    suspend fun setQuietStartMin(v: Int) {
        ctx.prefs.edit { it[quietStartKey] = v.coerceIn(0, 1439) }
    }

    suspend fun setQuietEndMin(v: Int) {
        ctx.prefs.edit { it[quietEndKey] = v.coerceIn(0, 1440) }
    }

    suspend fun setFailuresOnly(v: Boolean) {
        ctx.prefs.edit { it[failuresOnlyKey] = v }
    }

    suspend fun setHideTiny(v: Boolean) {
        ctx.prefs.edit { it[hideTinyKey] = v }
    }
}
