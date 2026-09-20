package com.elejar.ZentraDL.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.elejar.ZentraDL.data.TaskRepository
import com.elejar.ZentraDL.data.local.TaskRecord
import com.elejar.ZentraDL.engine.model.DownloadProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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

    fun message(text: String) {
        viewModelScope.launch { _events.send(text) }
    }
}
