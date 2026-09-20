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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.elejar.ZentraDL.service.DownloadService

/** Torrent overview (P4b; full tabs + Pieces map land in P4c). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TorrentDetailsScreen(
    onBack: () -> Unit,
    onDeleted: () -> Unit,
    vm: TorrentDetailsViewModel = hiltViewModel(),
) {
    val rec by vm.record.collectAsState()
    val progress by vm.progress.collectAsState()
    val meta by vm.meta.collectAsState()
    val ctx = LocalContext.current
    val snacks = remember { SnackbarHostState() }
    var menu by remember { mutableStateOf(false) }
    var confirmDeleteFile by remember { mutableStateOf(false) }

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
                if (p != null) {
                    "${Format.bytes(p.downloadedBytes)} of ${if (p.totalBytes > 0) Format.bytes(p.totalBytes) else stringResource(R.string.unknown_size)} · ${Format.speed(p.bytesPerSecond, p.bytesPerSecond > 0)}"
                } else {
                    r.status
                },
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
            )
            StatusChip(DownloadsUi.mapStatus(r.status))

            if (r.status == "failed") {
                val fix = ErrorTexts.classify(r.error)
                Text(fix.title, style = MaterialTheme.typography.titleSmall)
                Text(fix.message, style = MaterialTheme.typography.bodyMedium)
                Button(onClick = { withNotifPerm { vm.retry { DownloadService.startTorrent(ctx, it) } } }) {
                    Text(fix.action)
                }
            }

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

            meta?.let { m ->
                Text(
                    stringResource(R.string.files_n, m.files.size, Format.bytes(m.sizeBytes)),
                    style = MaterialTheme.typography.titleSmall,
                )
                m.files.take(50).forEach { f ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            f.path,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            Format.bytes(f.size),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
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
