package com.elejar.ZentraDL.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elejar.ZentraDL.data.TaskRepository
import com.elejar.ZentraDL.data.local.Category
import com.elejar.ZentraDL.data.local.RuleLogEntry
import com.elejar.ZentraDL.data.local.TaskRecord
import com.elejar.ZentraDL.domain.rules.RuleEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Activity: insights + auto-action log with Undo (U8, S13-lite). */
@HiltViewModel
class ActivityViewModel @Inject constructor(
    private val repo: TaskRepository,
    private val rules: RuleEngine,
) : ViewModel() {

    val records: StateFlow<List<TaskRecord>> = repo.records
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val categories: StateFlow<List<Category>> = repo.categories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _log = MutableStateFlow(emptyList<RuleLogEntry>())
    val log: StateFlow<List<RuleLogEntry>> = _log

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch { _log.value = rules.recentLog() }
    }

    fun undo(id: Long) {
        viewModelScope.launch {
            rules.undo(id)
            refresh()
        }
    }

    fun clearLog() {
        viewModelScope.launch {
            rules.clearLog()
            refresh()
        }
    }
}

/** Pure insights math (unit-tested). */
object ActivityUi {
    data class DayBucket(val label: String, val bytes: Long)

    /** Last 7 days of completed bytes, oldest first. */
    fun weeklyBytes(records: List<TaskRecord>, nowMs: Long = System.currentTimeMillis()): List<DayBucket> {
        val day = 86_400_000L
        val startOfToday = (nowMs / day) * day
        val names = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
        return (6 downTo 0).map { back ->
            val from = startOfToday - back * day
            val cal = java.util.Calendar.getInstance().apply { timeInMillis = from }
            val bytes = records
                .filter { it.status == "completed" && it.createdAt in from..<from + day }
                .sumOf { it.totalBytes.coerceAtLeast(0) }
            DayBucket(names[cal.get(java.util.Calendar.DAY_OF_WEEK) - 1], bytes)
        }
    }

    /** Completed bytes per category id. */
    fun bytesByCategory(records: List<TaskRecord>): Map<String, Long> =
        records.filter { it.status == "completed" }
            .groupBy { it.categoryId }
            .mapValues { (_, rs) -> rs.sumOf { it.totalBytes.coerceAtLeast(0) } }
}
