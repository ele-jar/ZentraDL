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
