package com.elejar.ZentraDL.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.elejar.ZentraDL.R
import com.elejar.ZentraDL.designsystem.components.SwitchRow
import com.elejar.ZentraDL.engine.torrent.TorrentMeta

/**
 * Add-torrent sheet (P4b: magnet/.torrent URL/file → metadata → file tree → start).
 * .torrent mime-handler from other apps lands in P4d.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TorrentSheet(
    onDismiss: () -> Unit,
    onStartService: (String) -> Unit,
    onTorrentDetails: (String) -> Unit,
    initialUrl: String = "",
    vm: TorrentAddViewModel = hiltViewModel(),
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val state by vm.state.collectAsState()
    val ctx = LocalContext.current

    LaunchedEffect(initialUrl) {
        if (initialUrl.startsWith("magnet:")) vm.fetchMagnet(initialUrl)
        else if (looksLikeLink(initialUrl)) vm.fetchFileUrl(initialUrl)
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        when (val s = state) {
            is TorrentAddViewModel.State.Input -> InputPane(
                onMagnet = { vm.fetchMagnet(it) },
                onFile = { vm.loadFile(it) },
                ctx = ctx,
            )
            is TorrentAddViewModel.State.Fetching -> Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            ) {
                Text(stringResource(R.string.add_torrent), style = MaterialTheme.typography.titleLarge)
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(stringResource(R.string.fetching_meta), style = MaterialTheme.typography.bodyMedium)
            }
            is TorrentAddViewModel.State.Error -> Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            ) {
                Text(stringResource(R.string.add_torrent), style = MaterialTheme.typography.titleLarge)
                Text(s.message, color = MaterialTheme.colorScheme.error)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { vm.retryLast() }) { Text(stringResource(R.string.retry)) }
                    TextButton(onClick = { vm.backToInput() }) { Text(stringResource(R.string.back)) }
                }
            }
            is TorrentAddViewModel.State.Ready -> ReadyPane(
                meta = s.meta,
                vm = vm,
                onDismiss = onDismiss,
                onStartService = onStartService,
                onTorrentDetails = onTorrentDetails,
            )
        }
    }
}

@Composable
private fun InputPane(onMagnet: (String) -> Unit, onFile: (ByteArray) -> Unit, ctx: Context) {
    var magnet by remember { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        try {
            ctx.contentResolver.openInputStream(uri)?.use { ins ->
                val out = java.io.ByteArrayOutputStream()
                val buf = ByteArray(8192)
                var total = 0
                while (true) {
                    val n = ins.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > 16 * 1024 * 1024) return@rememberLauncherForActivityResult
                    out.write(buf, 0, n)
                }
                onFile(out.toByteArray())
            }
        } catch (e: Exception) {
            // parse error surfaces in the sheet state.
        }
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(stringResource(R.string.add_torrent), style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(
            value = magnet,
            onValueChange = { magnet = it },
            label = { Text(stringResource(R.string.magnet_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = { onMagnet(magnet) },
                enabled = magnet.isNotBlank(),
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(R.string.fetch)) }
            OutlinedButton(
                onClick = { picker.launch("*/*") },
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(R.string.pick_torrent)) }
        }
    }
}

@Composable
private fun ReadyPane(
    meta: TorrentMeta,
    vm: TorrentAddViewModel,
    onDismiss: () -> Unit,
    onStartService: (String) -> Unit,
    onTorrentDetails: (String) -> Unit,
) {
    val selected by vm.selected.collectAsState()
    val sequential by vm.sequential.collectAsState()
    val saveDir by vm.saveDir.collectAsState()
    var busy by remember { mutableStateOf(false) }
    var dupe by remember { mutableStateOf<String?>(null) }
    var lastStart by remember { mutableStateOf(true) }
    var freeBytes by remember { mutableStateOf(-1L) }
    val snacksHost = remember { SnackbarHostState() }

    LaunchedEffect(vm) {
        vm.events.collect { e ->
            when (e) {
                is TorrentAddViewModel.Event.Working -> busy = e.busy
                is TorrentAddViewModel.Event.Message -> snacksHost.showSnackbar(e.text)
                is TorrentAddViewModel.Event.TorrentDuplicate -> dupe = e.recordId
            }
        }
    }
    LaunchedEffect(saveDir) {
        freeBytes = saveDir?.usableSpace ?: -1L
    }

    val groups = remember(meta) {
        meta.files.groupBy { it.path.substringBefore('/', "(root)") }.toSortedMap()
    }
    val wantedBytes = meta.files.filter { it.path in selected }.sumOf { it.size }
    val fits = freeBytes < 0 || wantedBytes <= freeBytes

    LazyColumn(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        item {
            Text(stringResource(R.string.add_torrent), style = MaterialTheme.typography.titleLarge)
        }
        item {
            Text(meta.name, style = MaterialTheme.typography.titleMedium)
            Text(
                "${Format.bytes(meta.sizeBytes)} · ${meta.files.size} files" +
                    (if (meta.isPrivate) " · private" else ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace,
            )
        }
        item {
            SwitchRow(
                title = stringResource(R.string.sequential_title),
                description = stringResource(R.string.sequential_desc),
                checked = sequential,
                onCheckedChange = { vm.setSequential(it) },
            )
        }
        groups.forEach { (dir, files) ->
            val paths = files.map { it.path }.toSet()
            val checked = paths.intersect(selected)
            val tristate = when {
                checked.isEmpty() -> ToggleableState.Off
                checked.size == paths.size -> ToggleableState.On
                else -> ToggleableState.Indeterminate
            }
            item(key = "g:$dir") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TriStateCheckbox(
                        state = tristate,
                        onClick = { vm.toggleGroup(paths, checked.size != paths.size) },
                    )
                    Text(dir, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Text(
                        Format.bytes(files.sumOf { it.size }),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
            items(files, key = { "f:${it.path}" }) { f ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = f.path in selected,
                        onCheckedChange = { vm.toggle(f.path) },
                    )
                    Column(Modifier.weight(1f)) {
                        Text(f.path, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                        Text(
                            Format.bytes(f.size),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
        }
        item {
            val dir = saveDir
            if (dir != null) {
                Text(
                    if (fits) stringResource(R.string.free_space_in, Format.bytes(freeBytes), dir.name)
                    else stringResource(R.string.not_enough_space, Format.bytes(wantedBytes), Format.bytes(freeBytes)),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (fits) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        lastStart = true
                        vm.add { onStartService(it); onDismiss() }
                    },
                    enabled = selected.isNotEmpty() && fits && !busy,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.download)) }
                OutlinedButton(
                    onClick = {
                        lastStart = false
                        vm.add { onDismiss() }
                    },
                    enabled = selected.isNotEmpty() && fits && !busy,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.queue_action)) }
            }
            if (busy) CircularProgressIndicator(modifier = Modifier.padding(vertical = 8.dp))
            SnackbarHost(snacksHost)
        }
    }
    dupe?.let { id ->
        AlertDialog(
            onDismissRequest = { dupe = null },
            title = { Text(stringResource(R.string.already_in_list)) },
            text = { Text(stringResource(R.string.already_in_list_text, meta.name)) },
            confirmButton = {
                TextButton(onClick = { dupe = null; onTorrentDetails(id) }) {
                    Text(stringResource(R.string.details))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        dupe = null
                        vm.add(allowDuplicate = true) {
                            if (lastStart) onStartService(it)
                            onDismiss()
                        }
                    },
                ) { Text(stringResource(R.string.download_anyway)) }
            },
        )
    }
}
