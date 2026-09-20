package com.elejar.ZentraDL.ui

import com.elejar.ZentraDL.data.SettingsStore
import com.elejar.ZentraDL.data.TaskRepository
import com.elejar.ZentraDL.data.local.TaskRecord
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val repo: TaskRepository,
    private val settings: SettingsStore,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(DownloadsUi.StatusFilter.All)

    val density: StateFlow<String> = settings.density
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "comfortable")
    val sort: StateFlow<String> = settings.sort
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "date")

    val items: StateFlow<List<DownloadsUi.ListItem>> = combine(
        repo.records, repo.progress, query, filter, sort,
    ) { records, progress, q, f, s ->
        DownloadsUi.buildList(records, progress, q, f, sortOf(s))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val header: StateFlow<HeaderUi> = combine(repo.records, repo.progress) { records, progress ->
        val down = progress.values.sumOf { it.bytesPerSecond }
        HeaderUi(
            downSpeed = down,
            active = records.count { it.status == "downloading" },
            queued = records.count { it.status == "queued" },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HeaderUi(0, 0, 0))

    private val _events = Channel<Event>(Channel.BUFFERED)
    val events: Flow<Event> = _events.receiveAsFlow()

    fun setQuery(q: String) {
        query.value = q
    }

    fun setFilter(f: DownloadsUi.StatusFilter) {
        filter.value = f
    }

    fun setSort(s: DownloadsUi.SortMode) {
        viewModelScope.launch { settings.setSort(s.name.lowercase()) }
    }

    fun setDensity(d: String) {
        viewModelScope.launch { settings.setDensity(d) }
    }

    fun addDownload(url: String, name: String? = null, onEnqueued: (String) -> Unit) {
        viewModelScope.launch {
            val clean = url.trim()
            if (clean.isBlank()) {
                _events.send(Event.Message("Enter a link first"))
                return@launch
            }
            try {
                onEnqueued(repo.enqueue(clean, name))
            } catch (e: Exception) {
                _events.send(Event.Message("Couldn't add download: ${e.message}"))
            }
        }
    }

    fun startTask(id: String, onStart: (String) -> Unit) {
        onStart(id)
    }

    fun pause(id: String) {
        viewModelScope.launch { repo.pause(id) }
    }

    fun retry(id: String, onStart: (String) -> Unit) {
        onStart(id)
    }

    fun delete(id: String, deleteFile: Boolean) {
        viewModelScope.launch {
            val rec = repo.get(id) ?: return@launch
            repo.delete(id, deleteFile)
            if (!deleteFile) _events.send(Event.Deleted(rec))
        }
    }

    fun undoDelete(rec: TaskRecord) {
        viewModelScope.launch { repo.restore(rec) }
    }

    fun pauseAll() {
        viewModelScope.launch { repo.pauseAll() }
    }

    fun resumeAll() {
        viewModelScope.launch { repo.resumeAll() }
    }

    fun stopAll() {
        viewModelScope.launch { repo.cancelAll() }
    }

    private fun sortOf(s: String): DownloadsUi.SortMode = when (s) {
        "name" -> DownloadsUi.SortMode.Name
        "size" -> DownloadsUi.SortMode.Size
        "progress" -> DownloadsUi.SortMode.Progress
        "speed" -> DownloadsUi.SortMode.Speed
        else -> DownloadsUi.SortMode.Date
    }

    data class HeaderUi(val downSpeed: Long, val active: Int, val queued: Int)

    sealed interface Event {
        data class Message(val text: String) : Event
        data class Deleted(val record: TaskRecord) : Event
    }
}
