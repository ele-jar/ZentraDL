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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.elejar.ZentraDL.R
import com.elejar.ZentraDL.data.local.TaskRecord
import com.elejar.ZentraDL.service.DownloadService
import androidx.compose.ui.res.stringResource

/** P1 walking-skeleton screen (full Downloads home lands in Phase 2). */
@Composable
fun DownloadsScreen(vm: DownloadsViewModel = hiltViewModel()) {
    val records by vm.records.collectAsState()
    val progress by vm.progress.collectAsState()
    val ctx = LocalContext.current
    val snacks = remember { SnackbarHostState() }
    var url by remember { mutableStateOf("") }

    LaunchedEffect(vm) {
        vm.events.collect { snacks.showSnackbar(it) }
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun submit() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        vm.addDownload(url) { id ->
            DownloadService.start(ctx, id)
            url = ""
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snacks) }) { pads ->
        Column(Modifier.fillMaxSize().padding(pads).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TextField(
                value = url,
                onValueChange = { url = it },
                label = { Text(stringResource(R.string.url_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = ::submit, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.download))
                }
                OutlinedButton(onClick = { vm.stopAll() }) {
                    Text(stringResource(R.string.stop_all))
                }
            }
            if (records.isEmpty()) {
                Text(stringResource(R.string.empty_hint), style = MaterialTheme.typography.bodyMedium)
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(records, key = { it.id }) { rec ->
                    DownloadRow(rec, progress[rec.id]?.percent)
                }
            }
        }
    }
}

@Composable
private fun DownloadRow(rec: TaskRecord, percent: Int?) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(rec.fileName, maxLines = 2, overflow = TextOverflow.MiddleEllipsis)
            Text(
                text = rec.status + (if (percent != null) " · $percent%" else ""),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace, // tabular figures so numbers don't jitter (P2: tnum)
            )
            if (percent != null) {
                LinearProgressIndicator(progress = (percent / 100f).coerceIn(0f, 1f), modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
