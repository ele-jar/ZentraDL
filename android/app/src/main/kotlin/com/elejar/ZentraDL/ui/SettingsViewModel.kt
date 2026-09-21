package com.elejar.ZentraDL.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elejar.ZentraDL.data.SettingsStore
import com.elejar.ZentraDL.data.TaskRepository
import com.elejar.ZentraDL.data.local.Category
import com.elejar.ZentraDL.domain.rules.Rule
import com.elejar.ZentraDL.domain.rules.RuleTemplates
import com.elejar.ZentraDL.domain.rules.RuleText
import com.elejar.ZentraDL.domain.rules.RuleEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Settings state (P2c; the screen UI lands with notification/settings commit). */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsStore,
    private val repo: TaskRepository,
    private val rules: RuleEngine,
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
    val dhtEnabled: StateFlow<Boolean> = settings.dhtEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    val maxPeers: StateFlow<Int> = settings.maxPeers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 50)
    val seedGoal: StateFlow<Int> = settings.seedGoal
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val adblock: StateFlow<Boolean> = settings.adblock
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun setAppLock(v: Boolean) {
        viewModelScope.launch { settings.setAppLock(v) }
    }

    fun setDhtEnabled(v: Boolean) {
        viewModelScope.launch { settings.setDhtEnabled(v) }
    }

    fun setMaxPeers(v: Int) {
        viewModelScope.launch { settings.setMaxPeers(v) }
    }

    fun setSeedGoal(v: Int) {
        viewModelScope.launch { settings.setSeedGoal(v) }
    }

    fun setAdblock(v: Boolean) {
        viewModelScope.launch { settings.setAdblock(v) }
    }

    val quietEnabled: StateFlow<Boolean> = settings.quietEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val quietStartMin: StateFlow<Int> = settings.quietStartMin
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 1320)
    val quietEndMin: StateFlow<Int> = settings.quietEndMin
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 420)
    val failuresOnly: StateFlow<Boolean> = settings.failuresOnly
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val hideTiny: StateFlow<Boolean> = settings.hideTiny
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setQuietEnabled(v: Boolean) {
        viewModelScope.launch { settings.setQuietEnabled(v) }
    }

    fun setQuietStartMin(v: Int) {
        viewModelScope.launch { settings.setQuietStartMin(v) }
    }

    fun setQuietEndMin(v: Int) {
        viewModelScope.launch { settings.setQuietEndMin(v) }
    }

    fun setFailuresOnly(v: Boolean) {
        viewModelScope.launch { settings.setFailuresOnly(v) }
    }

    fun setHideTiny(v: Boolean) {
        viewModelScope.launch { settings.setHideTiny(v) }
    }

    val smartMaster: StateFlow<Boolean> = settings.smartMaster
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun setSmartMaster(v: Boolean) {
        viewModelScope.launch { settings.setSmartMaster(v) }
    }

    private val _ruleList = MutableStateFlow(emptyList<Rule>())
    val ruleList: StateFlow<List<Rule>> = _ruleList.asStateFlow()

    fun refreshRules() {
        viewModelScope.launch { _ruleList.value = rules.allRules() }
    }

    fun addTemplate(template: Rule) {
        viewModelScope.launch {
            rules.addRule(template.copy(id = rules.newId()))
            refreshRules()
        }
    }

    fun toggleRule(id: String, enabled: Boolean) {
        viewModelScope.launch {
            rules.setEnabled(id, enabled)
            refreshRules()
        }
    }

    fun deleteRule(id: String) {
        viewModelScope.launch {
            rules.deleteRule(id)
            refreshRules()
        }
    }

    suspend fun previewRule(id: String): List<String> {
        val rule = _ruleList.value.firstOrNull { it.id == id } ?: return emptyList()
        return rules.dryRun(rule)
    }

    fun exportText(): String = RuleText.export(_ruleList.value)

    suspend fun importText(text: String): Int {
        val parsed = RuleText.parse(text) { rules.newId() }
        parsed.forEach { rules.addRule(it) }
        _ruleList.value = rules.allRules()
        return parsed.size
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
