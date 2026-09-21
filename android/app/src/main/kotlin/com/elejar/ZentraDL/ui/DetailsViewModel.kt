package com.elejar.ZentraDL.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.elejar.ZentraDL.data.TaskRepository
import com.elejar.ZentraDL.data.local.Category
import com.elejar.ZentraDL.data.local.TaskRecord
import com.elejar.ZentraDL.engine.model.DownloadProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class DetailsViewModel @Inject constructor(
    private val repo: TaskRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val args = savedStateHandle.toRoute<Details>()

    val record: StateFlow<TaskRecord?> = repo.observe(args.id)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val progress: StateFlow<DownloadProgress?> = repo.progress
        .map { it[args.id] }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _events = Channel<String>(Channel.BUFFERED)
    val events: Flow<String> = _events.receiveAsFlow()

    val categories: StateFlow<List<Category>> = repo.categories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _hash = MutableStateFlow<String?>(null)
    val hash: StateFlow<String?> = _hash.asStateFlow()

    fun loadHash() {
        viewModelScope.launch { _hash.value = repo.sha256Of(args.id) }
    }

    fun saveExpected(hex: String) {
        viewModelScope.launch { repo.setExpectedSha(args.id, hex) }
    }

    fun moveToCategory(categoryId: String) {
        viewModelScope.launch { repo.setCategory(args.id, categoryId) }
    }

    fun rename(newName: String) {
        viewModelScope.launch {
            _events.send(if (repo.rename(args.id, newName)) "Renamed" else "Invalid name")
        }
    }

    fun moveToVault(vaulted: Boolean) {
        viewModelScope.launch {
            _events.send(
                if (repo.setVaulted(args.id, vaulted)) {
                    if (vaulted) "Moved to vault" else "Removed from vault"
                } else {
                    "Couldn't move right now"
                },
            )
        }
    }

    fun pause() {
        viewModelScope.launch { repo.pause(args.id) }
    }

    fun retry(onStart: (String) -> Unit) {
        onStart(args.id)
    }

    fun delete(deleteFile: Boolean, onDone: () -> Unit) {
        viewModelScope.launch {
            repo.delete(args.id, deleteFile)
            onDone()
        }
    }

    fun refreshLink(url: String) {
        viewModelScope.launch {
            _events.send(if (repo.refreshUrl(args.id, url)) "Link updated — queued" else "Couldn't update now")
        }
    }

    fun message(text: String) {
        viewModelScope.launch { _events.send(text) }
    }
}
