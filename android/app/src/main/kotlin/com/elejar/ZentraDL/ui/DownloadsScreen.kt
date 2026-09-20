package com.elejar.ZentraDL.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.elejar.ZentraDL.R
import com.elejar.ZentraDL.data.local.TaskRecord
import com.elejar.ZentraDL.designsystem.components.CardDensity
import com.elejar.ZentraDL.designsystem.components.DownloadCard
import com.elejar.ZentraDL.designsystem.components.EmptyState
import com.elejar.ZentraDL.designsystem.components.TaskStatus
import com.elejar.ZentraDL.service.DownloadService

/** Downloads home (P2b: list + queue; details land in P2c). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    onDetails: (String) -> Unit,
    vm: DownloadsViewModel = hiltViewModel(),
) {
    val items by vm.items.collectAsState()
    val header by vm.header.collectAsState()
    val density by vm.density.collectAsState()
    val sort by vm.sort.collectAsState()
    val ctx = LocalContext.current
    val snacks = remember { SnackbarHostState() }
    var showAdd by remember { mutableStateOf(false) }
    var sortOpen by remember { mutableStateOf(false) }
    var confirmDeleteFile by remember { mutableStateOf<TaskRecord?>(null) }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(DownloadsUi.StatusFilter.All) }

    LaunchedEffect(vm) {
        vm.events.collect { e ->
            when (e) {
                is DownloadsViewModel.Event.Message -> snacks.showSnackbar(e.text)
                is DownloadsViewModel.Event.Deleted -> {
                    val r = snacks.showSnackbar(
                        message = "Deleted ${e.record.fileName}",
                        actionLabel = "Undo",
                        duration = SnackbarDuration.Long,
                    )
                    if (r == SnackbarResult.ActionPerformed) vm.undoDelete(e.record)
                }
            }
        }
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
    fun startService(id: String) = withNotifPerm { DownloadService.start(ctx, id) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    Box {
                    IconButton(onClick = { sortOpen = true }) {
                        Icon(Icons.Filled.SwapVert, contentDescription = stringResource(R.string.sort))
                    }
                        DropdownMenu(expanded = sortOpen, onDismissRequest = { sortOpen = false }) {
                            SortOption("date", "Date", sort) { vm.setSort(DownloadsUi.SortMode.Date); sortOpen = false }
                            SortOption("name", "Name", sort) { vm.setSort(DownloadsUi.SortMode.Name); sortOpen = false }
                            SortOption("size", "Size", sort) { vm.setSort(DownloadsUi.SortMode.Size); sortOpen = false }
                            SortOption("progress", "Progress", sort) { vm.setSort(DownloadsUi.SortMode.Progress); sortOpen = false }
                            SortOption("speed", "Speed", sort) { vm.setSort(DownloadsUi.SortMode.Speed); sortOpen = false }
                        }
                    }
                    IconButton(onClick = { vm.setDensity(if (density == "compact") "comfortable" else "compact") }) {
                        Icon(Icons.Filled.ViewList, contentDescription = stringResource(R.string.toggle_density, density))
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAdd = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.add_download)) },
            )
        },
        snackbarHost = { SnackbarHost(snacks) },
    ) { pads ->
        Column(Modifier.fillMaxSize().padding(pads)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it; vm.setQuery(it) },
                label = { Text(stringResource(R.string.search_downloads)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                DownloadsUi.StatusFilter.entries.forEach { f ->
                    FilterChip(
                        selected = filter == f,
                        onClick = { filter = f; vm.setFilter(f) },
                        label = { Text(f.name) },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
            }
            SpeedHeader(header, onPauseAll = { vm.pauseAll() }, onResumeAll = { vm.resumeAll() })
            if (items.isEmpty()) {
                EmptyState(
                    stringResource(R.string.empty_title),
                    stringResource(R.string.empty_hint),
                    stringResource(R.string.add_download),
                    onAction = { showAdd = true },
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(items, key = { keyOf(it) }) { item ->
                        when (item) {
                            is DownloadsUi.ListItem.Header -> Text(
                                item.title,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                            is DownloadsUi.ListItem.Row -> {
                                val row = item.row
                                var menu by remember(row.record.id) { mutableStateOf(false) }
                                Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                                    DownloadCard(
                                        data = row.toCardData(),
                                        density = if (density == "compact") CardDensity.Compact else CardDensity.Comfortable,
                                        onAction = {when (row.status) {
                                                TaskStatus.Downloading, TaskStatus.Queued -> vm.pause(row.record.id)
                                                TaskStatus.Paused, TaskStatus.Failed -> vm.retry(row.record.id, ::startService)
                                                TaskStatus.Completed ->
                                                    openOrComplain(ctx, row.record) { vm.message(it) }
                                                else -> Unit
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        onClick = { onDetails(row.record.id) },
                                    )
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                        Box {
                                            IconButton(onClick = { menu = true }) {
                                                Icon(
                                                    Icons.Filled.MoreVert,
                                                    contentDescription = stringResource(R.string.more_actions, row.record.fileName),
                                                )
                                            }
                                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.copy_link)) },
                                                onClick = { FileActions.copyLink(ctx, row.record.url); menu = false },
                                            )
                                            if (row.status == TaskStatus.Failed) {
                                                DropdownMenuItem(
                                                    text = { Text(stringResource(R.string.retry)) },
                                                    onClick = { menu = false; vm.retry(row.record.id, ::startService) },
                                                )
                                            }
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.delete)) },
                                                onClick = { menu = false; vm.delete(row.record.id, deleteFile = false) },
                                            )
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.delete_with_file)) },
                                                onClick = { menu = false; confirmDeleteFile = row.record },
                                            )
                                        }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        AddSheet(onDismiss = { showAdd = false }, onStartService = ::startService, vm = vm)
    }
    confirmDeleteFile?.let { rec ->
        AlertDialog(
            onDismissRequest = { confirmDeleteFile = null },
            title = { Text(stringResource(R.string.delete_file_title)) },
            text = { Text(stringResource(R.string.delete_file_text, rec.fileName)) },
            confirmButton = {
                TextButton(onClick = { vm.delete(rec.id, deleteFile = true); confirmDeleteFile = null }) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteFile = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

private fun keyOf(item: DownloadsUi.ListItem): String = when (item) {
    is DownloadsUi.ListItem.Header -> "h:${item.title}"
    is DownloadsUi.ListItem.Row -> "r:${item.row.record.id}"
}

@Composable
private fun SortOption(value: String, label: String, current: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = current == value, onClick = null)
                Text(label)
            }
        },
        onClick = onClick,
    )
}

@Composable
private fun SpeedHeader(h: DownloadsViewModel.HeaderUi, onPauseAll: () -> Unit, onResumeAll: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "${Format.speed(h.downSpeed, h.downSpeed > 0)} ↓",
                style = MaterialTheme.typography.titleSmall,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                "${h.active} active · ${h.queued} queued",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onPauseAll) { Text(stringResource(R.string.pause_all)) }
        TextButton(onClick = onResumeAll) { Text(stringResource(R.string.resume_all)) }
    }
}

fun looksLikeLink(s: String): Boolean {
    val t = s.trim()
    return t.startsWith("http://") || t.startsWith("https://") || t.startsWith("magnet:?") ||
        (t.contains("://") && t.contains('.'))
}
