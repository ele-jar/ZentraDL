package com.elejar.ZentraDL.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.elejar.ZentraDL.data.TaskRepository
import com.elejar.ZentraDL.data.TorrentRepository
import com.elejar.ZentraDL.data.local.TaskRecord
import com.elejar.ZentraDL.data.local.TorrentTask
import com.elejar.ZentraDL.engine.model.DownloadProgress
import com.elejar.ZentraDL.engine.torrent.TorrentLive
import com.elejar.ZentraDL.engine.torrent.TorrentMeta
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Torrent details backing (P4b overview; tabs + pieces land in P4c). */
@HiltViewModel
class TorrentDetailsViewModel @Inject constructor(
    private val repo: TaskRepository,
    private val trepo: TorrentRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val args = savedStateHandle.toRoute<TorrentDetails>()

    val record: StateFlow<TaskRecord?> = repo.observe(args.id)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val trow: StateFlow<TorrentTask?> = trepo.observeTorrent(args.id)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val progress: StateFlow<DownloadProgress?> = trepo.tprogress
        .map { it[args.id] }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** 1 Hz live snapshot (stats/peers/pieces) while a session exists. */
    val live: StateFlow<TorrentLive?> = trepo.torrentLive(args.id)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _meta = MutableStateFlow<TorrentMeta?>(null)
    val meta: StateFlow<TorrentMeta?> = _meta

    init {
        viewModelScope.launch { _meta.value = trepo.meta(args.id) }
    }

    private val _events = Channel<String>(Channel.BUFFERED)
    val events: Flow<String> = _events.receiveAsFlow()

    fun pause() {
        viewModelScope.launch { trepo.cancelTorrent(args.id) }
    }

    fun retry(onStart: (String) -> Unit) {
        onStart(args.id)
    }

    fun delete(deleteFile: Boolean, onDone: () -> Unit) {
        viewModelScope.launch {
            trepo.deleteTorrent(args.id, deleteFile)
            onDone()
        }
    }

    fun message(text: String) {
        viewModelScope.launch { _events.send(text) }
    }
}
