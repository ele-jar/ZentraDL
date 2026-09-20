package com.elejar.ZentraDL.ui

import com.elejar.ZentraDL.data.DuplicateTask
import com.elejar.ZentraDL.data.SettingsStore
import com.elejar.ZentraDL.data.TaskRepository
import com.elejar.ZentraDL.data.TorrentRepository
import com.elejar.ZentraDL.data.local.Category
import com.elejar.ZentraDL.data.local.TaskRecord
import com.elejar.ZentraDL.data.local.TorrentTask
import com.elejar.ZentraDL.domain.GateBlock
import com.elejar.ZentraDL.engine.model.DownloadProgress
import com.elejar.ZentraDL.engine.model.ResourceInfo
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
    private val trepo: TorrentRepository,
    private val settings: SettingsStore,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(DownloadsUi.StatusFilter.All)
    private val category = MutableStateFlow<String?>(null)

    val categories: StateFlow<List<Category>> = repo.categories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val categoryFilter: StateFlow<String?> = category

    val density: StateFlow<String> = settings.density
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "comfortable")
    val sort: StateFlow<String> = settings.sort
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "date")
    val connections: StateFlow<Int> = settings.connections
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 8)
    val defaultDirPath: String get() = repo.defaultDir.absolutePath

    private val _resolve = MutableStateFlow<ResolveUi>(ResolveUi.Idle)
    val resolveState: StateFlow<ResolveUi> = _resolve

    /** HTTP + torrent live progress in one map (torrent stats converted). */
    private val allProgress = combine(repo.progress, trepo.tprogress) { h, t -> h + t }

    val items: StateFlow<List<DownloadsUi.ListItem>> = combine(
        combine(repo.records, repo.progress, query, filter, sort, ::ListInputs),
        category,
        allProgress,
    ) { i, c, tp ->
        DownloadsUi.buildList(i.records, i.progress + tp, i.query, i.filter, sortOf(i.sort), categoryId = c)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val header: StateFlow<HeaderUi> = combine(repo.records, allProgress, repo.gateBlock) { records, progress, block ->
        val down = progress.values.sumOf { it.bytesPerSecond }
        HeaderUi(
            downSpeed = down,
            active = records.count { it.status == "downloading" },
            queued = records.count { it.status == "queued" },
            block = block,
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

    fun setCategory(id: String?) {
        category.value = id
    }

    fun moveToCategory(id: String, categoryId: String) {
        viewModelScope.launch { repo.setCategory(id, categoryId) }
    }

    fun setSort(s: DownloadsUi.SortMode) {
        viewModelScope.launch { settings.setSort(s.name.lowercase()) }
    }

    fun setDensity(d: String) {
        viewModelScope.launch { settings.setDensity(d) }
    }

    fun setConnections(n: Int) {
        viewModelScope.launch { settings.setConnections(n) }
    }

    fun resolve(url: String) {
        viewModelScope.launch {
            val clean = url.trim()
            if (clean.isBlank()) return@launch
            _resolve.value = ResolveUi.Resolving
            _resolve.value = try {
                ResolveUi.Done(repo.probe(clean))
            } catch (e: Exception) {
                ResolveUi.Error(e.message ?: "Couldn't check this link")
            }
        }
    }

    fun resetResolve() {
        _resolve.value = ResolveUi.Idle
    }

    fun addDownload(
        url: String,
        name: String? = null,
        categoryId: String? = null,
        allowDuplicate: Boolean = false,
        onEnqueued: (String) -> Unit,
    ) {
        viewModelScope.launch {
            val clean = url.trim()
            if (clean.isBlank()) {
                _events.send(Event.Message("Enter a link first"))
                return@launch
            }
            try {
                onEnqueued(repo.enqueue(clean, name, categoryId, allowDuplicate))
            } catch (d: DuplicateTask) {
                _events.send(Event.Duplicate(clean, name, categoryId, d.record.id))
            } catch (e: Exception) {
                _events.send(Event.Message("Couldn't add download: ${e.message}"))
            }
        }
    }

    /** Paste-a-list: one URL per line, no probing; dupes skipped with a count. */
    fun addBatch(raw: String, categoryId: String?, startFirst: Boolean, onStart: (String) -> Unit) {
        viewModelScope.launch {
            val urls = raw.lines().map { it.trim() }.filter { looksLikeLink(it) }.distinct()
            if (urls.isEmpty()) {
                _events.send(Event.Message("No links found"))
                return@launch
            }
            var skipped = 0
            val ids = mutableListOf<String>()
            urls.forEach { u ->
                try {
                    ids += repo.enqueue(u, null, categoryId)
                } catch (d: DuplicateTask) {
                    skipped++
                }
            }
            _events.send(
                Event.Message(
                    if (skipped == 0) "Queued ${ids.size} downloads"
                    else "Queued ${ids.size} downloads ($skipped already in list)",
                ),
            )
            if (startFirst && ids.isNotEmpty()) onStart(ids.first())
        }
    }

    fun exportUrls(onShare: (String) -> Unit) {
        viewModelScope.launch {
            val urls = repo.allUrls()
            if (urls.isEmpty()) _events.send(Event.Message("Nothing to export"))
            else onShare(urls.joinToString("\n"))
        }
    }

    fun startTask(id: String, onStart: (String) -> Unit) {
        onStart(id)
    }

    fun pause(id: String) {
        viewModelScope.launch { repo.pause(id) }
    }

    fun pauseTorrent(id: String) {
        viewModelScope.launch { trepo.cancelTorrent(id) }
    }

    fun deleteTorrent(id: String, deleteFile: Boolean) {
        viewModelScope.launch {
            val rec = repo.get(id) ?: return@launch
            val row = trepo.torrentRow(id)
            trepo.deleteTorrent(id, deleteFile)
            if (!deleteFile && row != null) _events.send(Event.TorrentDeleted(rec, row))
        }
    }

    fun undoTorrentDelete(rec: TaskRecord, row: TorrentTask) {
        viewModelScope.launch { trepo.restoreTorrent(rec, row) }
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
        trepo.pauseAllTorrents()
    }

    fun pauseIds(ids: Set<String>) {
        viewModelScope.launch {
            ids.forEach { if (repo.get(it)?.kind == "torrent") trepo.cancelTorrent(it) else repo.pause(it) }
        }
    }

    fun deleteIds(ids: Set<String>, deleteFile: Boolean) {
        viewModelScope.launch {
            val recs = ids.mapNotNull { repo.get(it) }
            val (tor, http) = recs.partition { it.kind == "torrent" }
            http.forEach { repo.delete(it.id, deleteFile) }
            val trows = tor.mapNotNull { r ->
                val row = trepo.torrentRow(r.id)
                trepo.deleteTorrent(r.id, deleteFile)
                row
            }
            if (!deleteFile && (http.isNotEmpty() || trows.isNotEmpty())) {
                _events.send(Event.DeletedBatch(http, tor, trows))
            }
        }
    }

    fun undoDeleteBatch(recs: List<TaskRecord>, torRecs: List<TaskRecord>, rows: List<TorrentTask>) {
        viewModelScope.launch {
            recs.forEach { repo.restore(it) }
            torRecs.zip(rows).forEach { (rec, row) -> trepo.restoreTorrent(rec, row) }
        }
    }

    fun moveIdsToCategory(ids: Set<String>, categoryId: String) {
        // Torrents stay put: moving live session files breaks seeding (P4d move-storage).
        viewModelScope.launch {
            ids.forEach {
                val r = repo.get(it)
                if (r != null && r.kind != "torrent") repo.setCategory(it, categoryId)
            }
        }
    }

    fun rename(id: String, name: String) {
        viewModelScope.launch {
            if (!repo.rename(id, name)) _events.send(Event.Message("Invalid name"))
        }
    }

    fun message(text: String) {
        viewModelScope.launch { _events.send(Event.Message(text)) }
    }

    fun resumeAll() {
        viewModelScope.launch { repo.resumeAll() }
        viewModelScope.launch { trepo.resumeAllTorrents() }
    }

    fun stopAll() {
        viewModelScope.launch { repo.cancelAll() }
        trepo.pauseAllTorrents()
    }

    private fun sortOf(s: String): DownloadsUi.SortMode = when (s) {
        "name" -> DownloadsUi.SortMode.Name
        "size" -> DownloadsUi.SortMode.Size
        "progress" -> DownloadsUi.SortMode.Progress
        "speed" -> DownloadsUi.SortMode.Speed
        else -> DownloadsUi.SortMode.Date
    }

    /** Holder so the 5-input combine stays on the unambiguous overload. */
    private data class ListInputs(
        val records: List<TaskRecord>,
        val progress: Map<String, DownloadProgress>,
        val query: String,
        val filter: DownloadsUi.StatusFilter,
        val sort: String,
    )

    data class HeaderUi(val downSpeed: Long, val active: Int, val queued: Int, val block: GateBlock? = null)

    sealed interface ResolveUi {
        data object Idle : ResolveUi
        data object Resolving : ResolveUi
        data class Done(val info: ResourceInfo) : ResolveUi
        data class Error(val message: String) : ResolveUi
    }

    sealed interface Event {
        data class Message(val text: String) : Event
        data class Deleted(val record: TaskRecord) : Event
        data class DeletedBatch(
            val records: List<TaskRecord>,
            val torrentRecords: List<TaskRecord> = emptyList(),
            val torrentRows: List<TorrentTask> = emptyList(),
        ) : Event
        data class TorrentDeleted(val record: TaskRecord, val row: TorrentTask) : Event
        data class Duplicate(val url: String, val name: String?, val categoryId: String?, val recordId: String) : Event
    }
}
