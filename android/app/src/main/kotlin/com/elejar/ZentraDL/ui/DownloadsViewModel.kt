package com.elejar.ZentraDL.ui

import com.elejar.ZentraDL.data.TaskRepository
import com.elejar.ZentraDL.data.local.TaskRecord
import com.elejar.ZentraDL.engine.model.DownloadProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val repo: TaskRepository,
) : ViewModel() {

    val records: StateFlow<List<TaskRecord>> = repo.records
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val progress: StateFlow<Map<String, DownloadProgress>> = repo.progress

    private val _events = Channel<String>(Channel.BUFFERED)
    val events: Flow<String> = _events.receiveAsFlow()

    fun addDownload(url: String, onEnqueued: (String) -> Unit) {
        viewModelScope.launch {
            val clean = url.trim()
            if (clean.isBlank()) {
                _events.send("Enter a link first")
                return@launch
            }
            try {
                onEnqueued(repo.enqueue(clean))
            } catch (e: Exception) {
                _events.send("Couldn't add download: ${e.message}")
            }
        }
    }

    fun stopAll() {
        viewModelScope.launch { repo.cancelAll() }
    }

    fun pauseAll() {
        viewModelScope.launch { repo.pauseAll() }
    }

    fun resumeAll() {
        viewModelScope.launch { repo.resumeAll() }
    }
}
