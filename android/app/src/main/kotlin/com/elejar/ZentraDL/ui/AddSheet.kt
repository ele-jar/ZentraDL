package com.elejar.ZentraDL.ui

import android.content.Context
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material3.TextButton
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
import com.elejar.ZentraDL.domain.Categorizer

/**
 * Add-download sheet (P2b single-URL; P3b batch + prefill from share intents).
 * Paste -> auto-resolve -> Download/Queue in <= 2 taps.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSheet(
    onDismiss: () -> Unit,
    onStartService: (String) -> Unit,
    vm: DownloadsViewModel = hiltViewModel(),
    initialUrl: String = "",
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val resolve by vm.resolveState.collectAsState()
    val conns by vm.connections.collectAsState()
    val categories by vm.categories.collectAsState()
    val ctx = LocalContext.current
    var url by remember(initialUrl) { mutableStateOf(initialUrl) }
    var name by remember { mutableStateOf("") }
    var nameEdited by remember { mutableStateOf(false) }
    var suggestion by remember { mutableStateOf<String?>(null) }
    var cat by remember { mutableStateOf<String?>(null) }
    var catPicked by remember { mutableStateOf(false) }
    var batch by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.resetResolve()
        if (looksLikeLink(initialUrl)) {
            vm.resolve(initialUrl)
        } else {
            val clip = (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager)
                .primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString().orEmpty()
            if (looksLikeLink(clip)) suggestion = clip
        }
    }
    LaunchedEffect(resolve) {
        val info = (resolve as? DownloadsViewModel.ResolveUi.Done)?.info
        if (info != null && !nameEdited) name = info.fileName
        if (info != null && !catPicked) {
            cat = Categorizer.categorize(info.fileName, info.mimeType, url).categoryId
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.add_download),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { batch = !batch }) {
                    Text(stringResource(if (batch) R.string.single_link else R.string.batch))
                }
            }
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
                label = { Text(stringResource(if (batch) R.string.batch_hint else R.string.url_label)) },
                singleLine = !batch,
                minLines = if (batch) 5 else 1,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (!batch) vm.resolve(url) }),
                trailingIcon = {
                    if (!batch) {
                        TextButton(onClick = { vm.resolve(url) }) { Text(stringResource(R.string.check)) }
                    }
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
                    if (categories.isNotEmpty()) {
                        // Category chip (auto from name/MIME/host); tap to override.
                        val auto = Categorizer.categorize(
                            name.ifBlank { r.info.fileName }, r.info.mimeType, url,
                        ).categoryId
                        val autoLabel = stringResource(R.string.auto)
                        Row(Modifier.horizontalScroll(rememberScrollState())) {
                            categories.forEach { c ->
                                FilterChip(
                                    selected = (cat ?: auto) == c.id,
                                    onClick = { cat = c.id; catPicked = true },
                                    label = { Text(if (c.id == auto) "${c.name} · $autoLabel" else c.name) },
                                    modifier = Modifier.padding(end = 8.dp),
                                )
                            }
                        }
                    }
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
                if (batch) {
                    // Batch: no probing — one link per line, auto-categorized each.
                    val count = url.lines().count { looksLikeLink(it) }
                    Button(
                        onClick = { vm.addBatch(url, cat) { onStartService(it); onDismiss() } },
                        enabled = count > 0,
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.download_n, count)) }
                    OutlinedButton(
                        onClick = { vm.addBatch(url, cat) {}; onDismiss() },
                        enabled = count > 0,
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.queue_action)) }
                } else {
                    val ready = resolve is DownloadsViewModel.ResolveUi.Done
                    Button(
                        onClick = {
                            val target = url
                            val finalName = name.ifBlank { null }
                            vm.addDownload(target, finalName, cat) { onStartService(it); onDismiss() }
                        },
                        enabled = ready,
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.download)) }
                    OutlinedButton(
                        onClick = {
                            val target = url
                            val finalName = name.ifBlank { null }
                            vm.addDownload(target, finalName, cat) { onDismiss() }
                        },
                        enabled = ready,
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.queue_action)) }
                }
            }
        }
    }
}