package com.elejar.ZentraDL.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.elejar.ZentraDL.R
import com.elejar.ZentraDL.designsystem.components.StatusChip
import com.elejar.ZentraDL.designsystem.components.TaskStatus
import com.elejar.ZentraDL.engine.model.SegmentState
import com.elejar.ZentraDL.service.DownloadService

/** HTTP task details (P2c; torrent tabs land in Phase 4). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailsScreen(
    onBack: () -> Unit,
    onDeleted: () -> Unit,
    vm: DetailsViewModel = hiltViewModel(),
) {
    val rec by vm.record.collectAsState()
    val progress by vm.progress.collectAsState()
    val categories by vm.categories.collectAsState()
    val hash by vm.hash.collectAsState()
    val ctx = LocalContext.current
    val snacks = remember { SnackbarHostState() }
    var menu by remember { mutableStateOf(false) }
    var confirmDeleteFile by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf(false) }
    var move by remember { mutableStateOf(false) }
    val noFileText = stringResource(R.string.no_file)

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
                            text = { Text(stringResource(R.string.rename)) },
                            onClick = { menu = false; rename = true },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.move_to)) },
                            onClick = { menu = false; move = true },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.share_file)) },
                            onClick = {
                                menu = false
                                val r = rec
                                val f = r?.let { java.io.File(it.destPath, it.fileName) }
                                if (r == null || f == null || !FileActions.shareFile(ctx, f)) {
                                    vm.message(noFileText)
                                }
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.copy_link)) },
                            onClick = {
                                rec?.let { FileActions.copyLink(ctx, it.url) }
                                menu = false
                            },
                        )
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
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize().padding(pads).verticalScroll(rememberScrollState()).padding(16.dp),
        ) {
            val pct = progress?.percent
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
            val p = progress
            Text(
                if (p != null) "${Format.bytes(p.downloadedBytes)} of ${if (p.totalBytes > 0) Format.bytes(p.totalBytes) else stringResource(R.string.unknown_size)} · ${Format.speed(p.bytesPerSecond, p.bytesPerSecond > 0)}" else r.status,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
            )
            StatusChip(DownloadsUi.mapStatus(r.status))

            if (r.status == "failed") {
                val fix = ErrorTexts.classify(r.error)
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(fix.title, style = MaterialTheme.typography.titleSmall)
                        Text(fix.message, style = MaterialTheme.typography.bodyMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { withNotifPerm { vm.retry { DownloadService.start(ctx, it) } } }) {
                                Text(fix.action)
                            }
                            OutlinedButton(onClick = { rec?.let { FileActions.copyLink(ctx, it.url) } }) {
                                Text(stringResource(R.string.copy_link))
                            }
                        }
                    }
                }
            }

            FactsGrid(r)

            // Checksum (H9-lite): computed hash + optional expected value to compare.
            if (r.status == "completed") {
                LaunchedEffect(r.id) { vm.loadHash() }
                var expected by remember(r.id, r.expectedSha256) { mutableStateOf(r.expectedSha256.orEmpty()) }
                Text(stringResource(R.string.checksum_title), style = MaterialTheme.typography.titleSmall)
                hash?.let { h ->
                    Text(
                        "SHA-256 $h",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                OutlinedTextField(
                    value = expected,
                    onValueChange = { expected = it },
                    label = { Text(stringResource(R.string.expected_sha)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { vm.saveExpected(expected) }) {
                        Text(stringResource(R.string.save))
                    }
                }
                val saved = r.expectedSha256
                if (hash != null && !saved.isNullOrBlank()) {
                    val ok = saved.equals(hash, ignoreCase = true)
                    Text(
                        stringResource(if (ok) R.string.checksum_match else R.string.checksum_mismatch),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    )
                }
            }

            // Actions.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (r.status) {
                    "downloading", "queued" -> OutlinedButton(onClick = { vm.pause() }) { Text(stringResource(R.string.pause)) }
                    "paused", "failed" -> Button(onClick = { withNotifPerm { vm.retry { DownloadService.start(ctx, it) } } }) {
                        Text(stringResource(R.string.retry))
                    }
                    "completed" -> Button(onClick = { openOrComplain(ctx, r) { vm.message(it) } }) {
                        Text(stringResource(R.string.open_file))
                    }
                }
            }

            // Segments map (HTTP equivalent of the Pieces map).
            val segs = progress?.segments.orEmpty()
            if (segs.isNotEmpty()) {
                Text(stringResource(R.string.segments_title), style = MaterialTheme.typography.titleSmall)
                segs.forEach { SegmentRow(it) }
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
    if (rename) {
        var name by remember(rec?.id) { mutableStateOf(rec?.fileName.orEmpty()) }
        AlertDialog(
            onDismissRequest = { rename = false },
            title = { Text(stringResource(R.string.rename)) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.new_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.rename(name); rename = false }) {
                    Text(stringResource(R.string.rename))
                }
            },
            dismissButton = {
                TextButton(onClick = { rename = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
    if (move) {
        CategoryPickerDialog(
            categories = categories,
            currentId = rec?.categoryId,
            onDismiss = { move = false },
            onPick = { vm.moveToCategory(it) },
        )
    }
}

@Composable
private fun FactsGrid(r: com.elejar.ZentraDL.data.local.TaskRecord) {
    @Composable
    fun RowScope.Fact(label: String, value: String) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Fact(stringResource(R.string.label_size), if (r.totalBytes > 0) Format.bytes(r.totalBytes) else stringResource(R.string.unknown_size))
            Fact(stringResource(R.string.label_status), r.status)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Fact(stringResource(R.string.label_added), Format.dayTime(r.createdAt))
            Fact(stringResource(R.string.label_save_path), r.destPath)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.label_link), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(r.url, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun SegmentRow(s: SegmentState) {
    val pct = if (s.endByte >= s.beginByte) {
        ((s.downloadedBytes * 100) / (s.endByte - s.beginByte + 1)).toInt().coerceIn(0, 100)
    } else {
        -1
    }
    val endStr = if (s.endByte >= 0) Format.bytes(s.endByte) else "?"
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            stringResource(R.string.segments_row, s.index, Format.bytes(s.beginByte), endStr, pct, s.retries),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
        )
        if (pct >= 0) {
            LinearProgressIndicator(
                progress = (pct / 100f).coerceIn(0f, 1f),
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(MaterialTheme.shapes.small),
            )
        }
    }
}
