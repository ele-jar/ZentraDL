package com.elejar.ZentraDL.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.elejar.ZentraDL.R

/**
 * Add-download sheet (P2b single-URL; batch checklist lands in P3).
 * Paste -> auto-resolve -> Download/Queue in <= 2 taps.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSheet(
    onDismiss: () -> Unit,
    onStartService: (String) -> Unit,
    vm: DownloadsViewModel = hiltViewModel(),
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val resolve by vm.resolveState.collectAsState()
    val conns by vm.connections.collectAsState()
    val ctx = LocalContext.current
    var url by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var nameEdited by remember { mutableStateOf(false) }
    var suggestion by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        vm.resetResolve()
        val clip = (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager)
            .primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString().orEmpty()
        if (looksLikeLink(clip)) suggestion = clip
    }
    LaunchedEffect(resolve) {
        val info = (resolve as? DownloadsViewModel.ResolveUi.Done)?.info
        if (info != null && !nameEdited) name = info.fileName
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(stringResource(R.string.add_download), style = MaterialTheme.typography.titleLarge)
            suggestion?.let { s ->
                FilterChip(
                    selected = false,
                    onClick = { url = s; suggestion = null; vm.resolve(s) },
                    label = { Text(s.take(48), maxLines = 1) },
                    leadingIcon = { Icon(Icons.Filled.ContentPaste, contentDescription = null) },
                )
            }
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text(stringResource(R.string.url_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { vm.resolve(url) }),
                trailingIcon = {
                    TextButton(onClick = { vm.resolve(url) }) { Text(stringResource(R.string.check)) }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            when (val r = resolve) {
                is DownloadsViewModel.ResolveUi.Resolving -> LinearProgressIndicator(Modifier.fillMaxWidth())
                is DownloadsViewModel.ResolveUi.Error -> Text(
                    r.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                is DownloadsViewModel.ResolveUi.Done -> {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it; nameEdited = true },
                        label = { Text(stringResource(R.string.name_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    val size = if (r.info.totalBytes > 0) Format.bytes(r.info.totalBytes) else "Unknown size"
                    Text(
                        "$size · " + if (r.info.resumable) "Resumable" else "Not resumable",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                    Text(
                        "Save to ${vm.defaultDirPath}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Connections", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        IconButton(onClick = { vm.setConnections(conns - 1) }) {
                            Icon(Icons.Filled.Remove, contentDescription = "Fewer connections")
                        }
                        Text("$conns", fontFamily = FontFamily.Monospace)
                        IconButton(onClick = { vm.setConnections(conns + 1) }) {
                            Icon(Icons.Filled.Add, contentDescription = "More connections")
                        }
                    }
                }
                else -> Unit
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                val ready = resolve is DownloadsViewModel.ResolveUi.Done
                Button(
                    onClick = {
                        val target = url
                        val finalName = name.ifBlank { null }
                        vm.addDownload(target, finalName) { onStartService(it); onDismiss() }
                    },
                    enabled = ready,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.download)) }
                OutlinedButton(
                    onClick = {
                        val target = url
                        val finalName = name.ifBlank { null }
                        vm.addDownload(target, finalName) { onDismiss() }
                    },
                    enabled = ready,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.queue_action)) }
            }
        }
    }
}