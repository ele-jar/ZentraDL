package com.elejar.ZentraDL.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elejar.ZentraDL.data.DuplicateTask
import com.elejar.ZentraDL.data.TorrentRepository
import com.elejar.ZentraDL.engine.torrent.MetadataTimeoutException
import com.elejar.ZentraDL.engine.torrent.TorrentMeta
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import java.net.URL
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Add-torrent sheet state (P4b; tabs + pieces land in P4c). */
@HiltViewModel
class TorrentAddViewModel @Inject constructor(
    private val trepo: TorrentRepository,
) : ViewModel() {

    sealed interface State {
        data object Input : State
        data object Fetching : State
        data class Ready(val meta: TorrentMeta) : State
        data class Error(val message: String) : State
    }

    sealed interface Source {
        data class Magnet(val magnet: String, val rawBytes: ByteArray?) : Source
        data class File(val bytes: ByteArray) : Source
    }

    private val _state = MutableStateFlow<State>(State.Input)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _selected = MutableStateFlow(setOf<String>())
    val selected: StateFlow<Set<String>> = _selected.asStateFlow()

    private val _sequential = MutableStateFlow(false)
    val sequential: StateFlow<Boolean> = _sequential.asStateFlow()

    private val _saveDir = MutableStateFlow<File?>(null)
    val saveDir: StateFlow<File?> = _saveDir.asStateFlow()

    private var source: Source? = null
    private var lastMagnetInput = ""

    private val _events = Channel<Event>(Channel.BUFFERED)
    val events: Flow<Event> = _events.receiveAsFlow()

    fun fetchMagnet(magnet: String) {
        val clean = magnet.trim()
        if (clean.isBlank()) return
        lastMagnetInput = clean
        viewModelScope.launch {
            _state.value = State.Fetching
            _state.value = try {
                trepo.parseMagnetRef(clean)
                val fetched = trepo.fetchMeta(clean)
                arrive(Source.Magnet(clean, fetched.rawBytes), fetched.meta)
            } catch (e: MetadataTimeoutException) {
                State.Error("Couldn't fetch torrent info in 60s — no peers responded")
            } catch (e: IllegalArgumentException) {
                State.Error("That doesn't look like a magnet link")
            } catch (e: Exception) {
                State.Error("Couldn't fetch it: ${e.message}")
            }
        }
        }
    }

    fun fetchFileUrl(url: String) {
        viewModelScope.launch {
            _state.value = State.Fetching
            _state.value = try {
                val bytes = withContext(Dispatchers.IO) { readCapped(URL(url.trim())) }
                loadBytes(bytes)
            } catch (e: Exception) {
                State.Error("Couldn't download that .torrent file")
            }
        }
    }

    fun loadFile(bytes: ByteArray) {
        viewModelScope.launch {
            _state.value = try {
                loadBytes(bytes)
            } catch (e: Exception) {
                State.Error("Couldn't read that .torrent file")
            }
        }
    }

    private suspend fun loadBytes(bytes: ByteArray): State {
        val meta = trepo.parseFile(bytes)
        return arrive(Source.File(bytes), meta)
    }

    private suspend fun arrive(source: Source, meta: TorrentMeta): State {
        this.source = source
        _selected.value = meta.files.map { it.path }.toSet()
        _sequential.value = false
        _saveDir.value = trepo.previewDir(null, meta.name)
        return State.Ready(meta)
    }

    fun backToInput() {
        source = null
        _state.value = State.Input
    }

    fun toggle(path: String) {
        _selected.update { if (path in it) it - path else it + path }
    }

    fun toggleGroup(paths: Set<String>, select: Boolean) {
        _selected.update { if (select) it + paths else it - paths }
    }

    fun setSequential(v: Boolean) {
        _sequential.value = v
    }

    fun add(allowDuplicate: Boolean = false, onDone: (String) -> Unit) {
        val src = source ?: return
        val meta = (_state.value as? State.Ready)?.meta ?: return
        val wanted = _selected.value.ifEmpty { return }
        viewModelScope.launch {
            _events.send(Event.Working(true))
            try {
                val id = when (src) {
                    is Source.Magnet -> trepo.addKnownMeta(
                        meta, src.magnet, src.rawBytes, null, allowDuplicate, _sequential.value,
                    )
                    is Source.File -> trepo.addKnownMeta(
                        meta, null, src.bytes, null, allowDuplicate, _sequential.value,
                    )
                }
                // Selection applies on next start; store the subset now (empty = all).
                if (wanted.size != meta.files.size) trepo.updateSelection(id, wanted)
                onDone(id)
            } catch (d: DuplicateTask) {
                _events.send(Event.TorrentDuplicate(d.record.id))
            } catch (e: Exception) {
                _events.send(Event.Message("Couldn't add it: ${e.message}"))
            } finally {
                _events.send(Event.Working(false))
            }
        }
    }

    fun retryLast() {
        if (lastMagnetInput.isNotBlank()) fetchMagnet(lastMagnetInput)
    }

    /** Capped download for .torrent URLs (they're KBs; refuse anything huge). */
    private fun readCapped(url: URL, cap: Int = 16 * 1024 * 1024): ByteArray {
        url.openStream().use { ins ->
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(8192)
            var total = 0
            while (true) {
                val n = ins.read(buf)
                if (n < 0) break
                total += n
                if (total > cap) throw IllegalArgumentException("file too large")
                out.write(buf, 0, n)
            }
            return out.toByteArray()
        }
    }

    sealed interface Event {
        data class Message(val text: String) : Event
        data class Working(val busy: Boolean) : Event
        data class TorrentDuplicate(val recordId: String) : Event
    }
}
