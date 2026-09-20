package com.elejar.ZentraDL.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elejar.ZentraDL.data.SettingsStore
import com.elejar.ZentraDL.data.TaskRepository
import com.elejar.ZentraDL.data.local.Category
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Settings state (P2c; the screen UI lands with notification/settings commit). */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsStore,
    private val repo: TaskRepository,
) : ViewModel() {
    val themeMode: StateFlow<String> = settings.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "System")
    val accent: StateFlow<String> = settings.accent
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "Blue")
    val dynamicColor: StateFlow<Boolean> = settings.dynamicColor
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    val connections: StateFlow<Int> = settings.connections
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 8)
    val maxRunning: StateFlow<Int> = settings.maxRunning
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 3)
    val categories: StateFlow<List<Category>> = repo.categories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val wifiOnly: StateFlow<Boolean> = settings.wifiOnly
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val chargingOnly: StateFlow<Boolean> = settings.chargingOnly
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val schedEnabled: StateFlow<Boolean> = settings.schedEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val schedStartMin: StateFlow<Int> = settings.schedStartMin
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 1320)
    val schedEndMin: StateFlow<Int> = settings.schedEndMin
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 360)
    val speedLimitKbps: StateFlow<Int> = settings.speedLimitKbps
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val appLock: StateFlow<Boolean> = settings.appLock
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setAppLock(v: Boolean) {
        viewModelScope.launch { settings.setAppLock(v) }
    }

    fun setWifiOnly(v: Boolean) {
        viewModelScope.launch { settings.setWifiOnly(v) }
    }

    fun setChargingOnly(v: Boolean) {
        viewModelScope.launch { settings.setChargingOnly(v) }
    }

    fun setSchedEnabled(v: Boolean) {
        viewModelScope.launch { settings.setSchedEnabled(v) }
    }

    fun setSchedStartMin(v: Int) {
        viewModelScope.launch { settings.setSchedStartMin(v) }
    }

    fun setSchedEndMin(v: Int) {
        viewModelScope.launch { settings.setSchedEndMin(v) }
    }

    fun setSpeedLimitKbps(v: Int) {
        viewModelScope.launch { settings.setSpeedLimitKbps(v) }
    }

    fun addCategory(name: String) {
        viewModelScope.launch { repo.addCategory(name) }
    }

    fun deleteCategory(id: String) {
        viewModelScope.launch { repo.deleteCategory(id) }
    }

    fun setThemeMode(v: String) {
        viewModelScope.launch { settings.setThemeMode(v) }
    }

    fun setAccent(v: String) {
        viewModelScope.launch { settings.setAccent(v) }
    }

    fun setDynamicColor(v: Boolean) {
        viewModelScope.launch { settings.setDynamicColor(v) }
    }

    fun setConnections(n: Int) {
        viewModelScope.launch { settings.setConnections(n) }
    }

    fun setMaxRunning(n: Int) {
        viewModelScope.launch { settings.setMaxRunning(n) }
    }
}
