package com.elejar.ZentraDL.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.elejar.ZentraDL.R
import com.elejar.ZentraDL.data.local.TaskRecord
import com.elejar.ZentraDL.designsystem.components.PieceCell
import com.elejar.ZentraDL.designsystem.components.PiecesLegend
import com.elejar.ZentraDL.designsystem.components.PiecesMap
import com.elejar.ZentraDL.designsystem.components.StatusChip
import com.elejar.ZentraDL.engine.torrent.TorrentLive
import com.elejar.ZentraDL.engine.torrent.TorrentMeta
import com.elejar.ZentraDL.service.DownloadService

private enum class TorrentTab { Info, Files, Pieces, Peers, Trackers }

/** Torrent details with tabs (P4c: Info/Files/Pieces/Peers/Trackers; settings in P4d). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TorrentDetailsScreen(
    onBack: () -> Unit,
    onDeleted: () -> Unit,
    vm: TorrentDetailsViewModel = hiltViewModel(),
) {
    val rec by vm.record.collectAsState()
    val progress by vm.progress.collectAsState()
    val live by vm.live.collectAsState()
    val meta by vm.meta.collectAsState()
    val trow by vm.trow.collectAsState()
    val ctx = LocalContext.current
    val snacks = remember { SnackbarHostState() }
    var menu by remember { mutableStateOf(false) }
    var confirmDeleteFile by remember { mutableStateOf(false) }
    var tab by remember { mutableIntStateOf(0) }
    val tabs = TorrentTab.entries

    LaunchedEffect(vm) {
        vm.events.collect { snacks.showSnackbar(it) }
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun withNotifPerm(action: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        action()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(rec?.fileName ?: "", maxLines = 1, overflow = TextOverflow.MiddleEllipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.more_actions_simple))
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.delete)) },
                            onClick = { menu = false; vm.delete(deleteFile = false) { onDeleted() } },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.delete_with_file)) },
                            onClick = { menu = false; confirmDeleteFile = true },
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snacks) },
    ) { pads ->
        val r = rec
        if (r == null) {
            Text(stringResource(R.string.loading), modifier = Modifier.padding(pads).padding(16.dp))
            return@Scaffold
        }
        Column(Modifier.fillMaxSize().padding(pads)) {
            ScrollableTabRow(selectedTabIndex = tab, edgePadding = 16.dp) {
                tabs.forEachIndexed { i, t ->
                    Tab(
                        selected = tab == i,
                        onClick = { tab = i },
                        text = { Text(stringResource(torrentTabTitle(t))) },
                    )
                }
            }
            when (tabs[tab]) {
                TorrentTab.Info -> InfoTab(r, live, meta, vm, ::withNotifPerm, ctx)
                TorrentTab.Files -> FilesTab(meta, trow?.selectedPaths)
                TorrentTab.Pieces -> PiecesTab(live)
                TorrentTab.Peers -> PeersTab(live)
                TorrentTab.Trackers -> TrackersTab(meta)
            }
        }
    }
    if (confirmDeleteFile) {
        AlertDialog(
            onDismissRequest = { confirmDeleteFile = false },
            title = { Text(stringResource(R.string.delete_file_title)) },
            text = { Text(stringResource(R.string.delete_file_text, rec?.fileName ?: "")) },
            confirmButton = {
                TextButton(onClick = { vm.delete(deleteFile = true) { onDeleted() } }) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteFile = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

private fun torrentTabTitle(t: TorrentTab): Int = when (t) {
    TorrentTab.Info -> R.string.tab_info
    TorrentTab.Files -> R.string.tab_files
    TorrentTab.Pieces -> R.string.tab_pieces
    TorrentTab.Peers -> R.string.tab_peers
    TorrentTab.Trackers -> R.string.tab_trackers
}

@Composable
private fun InfoTab(
    r: TaskRecord,
    live: TorrentLive?,
    meta: TorrentMeta?,
    vm: TorrentDetailsViewModel,
    withNotifPerm: (() -> Unit) -> Unit,
    ctx: android.content.Context,
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize().padding(16.dp),
    ) {
        item {
            val pct = live?.stats?.let { s ->
                if (s.piecesTotal > 0) (s.piecesComplete * 100 / s.piecesTotal) else -1
            }
            Text(
                if (pct != null && pct >= 0) "$pct%" else "—",
                style = MaterialTheme.typography.displaySmall,
                fontFamily = FontFamily.Monospace,
            )
            if (pct != null && pct >= 0) {
                LinearProgressIndicator(
                    progress = (pct / 100f).coerceIn(0f, 1f),
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(MaterialTheme.shapes.small),
                )
            } else {
                LinearProgressIndicator(Modifier.fillMaxWidth().height(6.dp).clip(MaterialTheme.shapes.small))
            }
        }
        item {
            val s = live?.stats
            Text(
                if (s != null) {
                    "${Format.bytes(s.downloadedBytes)} · ↓${Format.speed(s.downRate, s.downRate > 0)} · ↑${Format.speed(s.upRate, s.upRate > 0)} · ${s.peers} peers"
                } else {
                    r.status
                },
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
            )
            StatusChip(DownloadsUi.mapStatus(r.status))
        }
        item { HealthCard(r, live) }
        if (r.status == "failed") {
            item {
                val fix = ErrorTexts.classify(r.error)
                Text(fix.title, style = MaterialTheme.typography.titleSmall)
                Text(fix.message, style = MaterialTheme.typography.bodyMedium)
                Button(onClick = { withNotifPerm { vm.retry { DownloadService.startTorrent(ctx, it) } } }) {
                    Text(fix.action)
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (r.status) {
                    "downloading", "queued", "seeding" -> OutlinedButton(onClick = { vm.pause() }) {
                        Text(stringResource(R.string.pause))
                    }
                    "paused", "failed" -> Button(onClick = { withNotifPerm { vm.retry { DownloadService.startTorrent(ctx, it) } } }) {
                        Text(stringResource(R.string.retry))
                    }
                }
            }
        }
        item {
            Fact(stringResource(R.string.label_size), if (r.totalBytes > 0) Format.bytes(r.totalBytes) else stringResource(R.string.unknown_size))
            Fact(stringResource(R.string.label_added), Format.dayTime(r.createdAt))
            Fact(stringResource(R.string.label_save_path), r.destPath)
            meta?.let { m ->
                Fact(stringResource(R.string.trackers_n), "${m.trackers.size}")
                if (m.isPrivate) Fact(stringResource(R.string.private_flag), stringResource(R.string.yes))
                m.createdBy?.let { Fact(stringResource(R.string.created_by), it) }
            }
            if ((meta?.trackers.orEmpty()).isEmpty()) {
                Text(
                    stringResource(R.string.trackerless_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Health without seed counts (engine exposes peers/rate only — see D012). */
@Composable
private fun HealthCard(r: TaskRecord, live: TorrentLive?) {
    val s = live?.stats ?: return
    if (r.status != "downloading" && r.status != "seeding" && r.status != "queued") return
    val (label, hint) = when {
        s.piecesTotal > 0 && s.piecesComplete >= s.piecesTotal ->
            stringResource(R.string.health_seeding) to stringResource(R.string.health_seeding_hint)
        s.peers == 0 -> stringResource(R.string.health_no_peers) to stringResource(R.string.health_no_peers_hint)
        s.downRate == 0L -> stringResource(R.string.health_stalled) to stringResource(R.string.health_stalled_hint)
        else -> stringResource(R.string.health_active, s.peers) to stringResource(R.string.health_active_hint)
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            Text(hint, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun Fact(label: String, value: String) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun FilesTab(meta: TorrentMeta?, selectedCsv: String?) {
    val m = meta
    if (m == null) {
        Text(stringResource(R.string.loading), modifier = Modifier.padding(16.dp))
        return
    }
    val selected = selectedCsv?.split(",")?.filter { it.isNotEmpty() }?.toSet()
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxSize().padding(16.dp),
    ) {
        item {
            Text(
                stringResource(R.string.files_n, m.files.size, Format.bytes(m.sizeBytes)),
                style = MaterialTheme.typography.titleSmall,
            )
        }
        items(m.files, key = { it.path }) { f ->
            val included = selected == null || selected.isEmpty() || f.path in selected
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(f.path, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (included) Format.bytes(f.size)
                        else stringResource(R.string.skipped_prefix, Format.bytes(f.size)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
    }
}

@Composable
private fun PiecesTab(live: TorrentLive?) {
    var inspected by remember { mutableStateOf<Int?>(null) }
    val map = live?.pieces
    val cells: List<PieceCell> = remember(map) {
        if (map == null || map.total <= 0) emptyList()
        else PieceCells.aggregate(map.total, map.runs, 1500)
    }
    val per = if (map != null && map.total > 0 && cells.isNotEmpty()) {
        (map.total + cells.size - 1) / cells.size
    } else {
        0
    }
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize().padding(16.dp),
    ) {
        if (map == null || map.total <= 0) {
            item { Text(stringResource(R.string.pieces_waiting), style = MaterialTheme.typography.bodyMedium) }
            return@LazyColumn
        }
        item {
            Text(
                stringResource(
                    R.string.pieces_header, map.complete, map.total,
                    if (map.total > 0) (map.complete * 100 / map.total) else 0,
                    Format.bytes(map.pieceLength),
                ),
                style = MaterialTheme.typography.titleSmall,
                fontFamily = FontFamily.Monospace,
            )
        }
        item {
            PiecesMap(
                cells = cells,
                summary = stringResource(
                    R.string.pieces_summary, map.complete, map.total,
                    if (map.total > 0) (map.complete * 100 / map.total) else 0,
                ),
                onTapCell = { inspected = it },
            )
        }
        item {
            PiecesLegend(
                stringResource(R.string.legend_complete),
                stringResource(R.string.legend_missing),
                stringResource(R.string.legend_skipped),
            )
        }
        item {
            Text(
                inspected?.let { stringResource(R.string.cell_inspect, it * per, ((it + 1) * per - 1).coerceAtMost(map.total - 1)) }
                    ?: stringResource(R.string.cell_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PeersTab(live: TorrentLive?) {
    val peers = live?.peers.orEmpty()
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxSize().padding(16.dp),
    ) {
        item {
            Text(
                stringResource(R.string.peers_n, peers.size),
                style = MaterialTheme.typography.titleSmall,
            )
        }
        if (peers.isEmpty()) {
            item { Text(stringResource(R.string.peers_empty), style = MaterialTheme.typography.bodyMedium) }
        }
        items(peers, key = { it.address + ":" + it.port }) { p ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    "${p.address}:${if (p.port >= 0) p.port else "?"}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f),
                )
                p.peerIdHex?.let {
                    Text(
                        it.take(12),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun TrackersTab(meta: TorrentMeta?) {
    val m = meta
    if (m == null) {
        Text(stringResource(R.string.loading), modifier = Modifier.padding(16.dp))
        return
    }
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxSize().padding(16.dp),
    ) {
        item {
            Text(stringResource(R.string.trackers_n, m.trackers.size), style = MaterialTheme.typography.titleSmall)
        }
        if (m.trackers.isEmpty()) {
            item { Text(stringResource(R.string.trackerless_hint), style = MaterialTheme.typography.bodyMedium) }
        }
        items(m.trackers, key = { it }) { t ->
            Text(
                t,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
