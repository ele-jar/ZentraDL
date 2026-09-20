package com.elejar.ZentraDL.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elejar.ZentraDL.data.local.Bookmark
import com.elejar.ZentraDL.data.local.BookmarkDao
import com.elejar.ZentraDL.data.local.HistoryDao
import com.elejar.ZentraDL.data.local.HistoryEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Browser state (P5a shell; tabs keep URL+title, web state reloads on switch). */
@HiltViewModel
class BrowserViewModel @Inject constructor(
    private val bookmarks: BookmarkDao,
    private val history: HistoryDao,
) : ViewModel() {

    private val _tabs = MutableStateFlow(listOf(WebTab(1, "")))
    val tabs: StateFlow<List<WebTab>> = _tabs

    private val _selected = MutableStateFlow(1L)
    val selected: StateFlow<Long> = _selected

    private var nextId = 2L

    /** URL the WebView should load (consumed by the screen). */
    private val _pendingLoad = MutableStateFlow<String?>(null)
    val pendingLoad: StateFlow<String?> = _pendingLoad

    /** Intercepted download URL (opens the Add sheet). */
    private val _intercepted = MutableStateFlow<String?>(null)
    val intercepted: StateFlow<String?> = _intercepted

    val bookmarkList: StateFlow<List<Bookmark>> = bookmarks.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val historyList: StateFlow<List<HistoryEntry>> = history.observeRecent()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun currentTab(): WebTab? = _tabs.value.firstOrNull { it.id == _selected.value }

    fun go(input: String) {
        val url = normalize(input)
        val tab = currentTab()
        if (tab == null) {
            val (tabs, sel) = BrowserTabs.open(_tabs.value, url, nextId++)
            _tabs.value = tabs
            _selected.value = sel
        } else {
            _tabs.value = BrowserTabs.navigate(_tabs.value, tab.id, url)
        }
        _pendingLoad.value = url
    }

    fun consumeLoad(): String? {
        val u = _pendingLoad.value
        _pendingLoad.value = null
        return u
    }

    fun onPage(url: String, title: String) {
        val tab = currentTab() ?: return
        _tabs.value = BrowserTabs.navigate(_tabs.value, tab.id, url, title)
        if (url.startsWith("http")) {
            viewModelScope.launch {
                history.insert(HistoryEntry(title = title.ifBlank { url }, url = url, visitedAt = System.currentTimeMillis()))
                history.trimOlderThan(System.currentTimeMillis() - 90L * 86_400_000L)
            }
        }
    }

    fun newTab() {
        val (tabs, sel) = BrowserTabs.open(_tabs.value, "", nextId++)
        _tabs.value = tabs
        _selected.value = sel
        _pendingLoad.value = null
    }

    fun select(id: Long) {
        if (_tabs.value.any { it.id == id }) {
            _selected.value = id
            _pendingLoad.value = null
        }
    }

    fun closeTab(id: Long) {
        val (tabs, sel) = BrowserTabs.close(_tabs.value, id)
        _tabs.value = tabs.ifEmpty { listOf(WebTab(nextId++, "")) }
        _selected.value = if (sel == -1L) _tabs.value.first().id else sel
        _pendingLoad.value = null
    }

    fun onDownload(url: String) {
        _intercepted.value = url
    }

    fun consumeIntercepted(): String? {
        val u = _intercepted.value
        _intercepted.value = null
        return u
    }

    suspend fun isBookmarked(url: String): Boolean = bookmarks.findByUrl(url) != null

    fun toggleBookmark(url: String, title: String) {
        viewModelScope.launch {
            if (bookmarks.findByUrl(url) != null) bookmarks.deleteByUrl(url)
            else bookmarks.insert(Bookmark(title = title.ifBlank { url }, url = url, createdAt = System.currentTimeMillis()))
        }
    }

    fun clearHistory() {
        viewModelScope.launch { history.clear() }
    }

    companion object {
        /** Bare domains/terms → https or search; anything else passes through. */
        fun normalize(input: String): String {
            val t = input.trim()
            if (t.isEmpty()) return ""
            if (t.startsWith("http://") || t.startsWith("https://")) return t
            if (t.startsWith("about:")) return t
            if (!t.contains(' ') && t.contains('.')) return "https://$t"
            return "https://duckduckgo.com/?q=" + java.net.URLEncoder.encode(t, "UTF-8")
        }
    }
}
