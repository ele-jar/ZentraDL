package com.elejar.ZentraDL.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.elejar.ZentraDL.R
import com.elejar.ZentraDL.service.DownloadService

private enum class BrowserSheet { None, Tabs, Bookmarks, History }

/** Browser shell (P5a: one live WebView, tabs reload on switch, interception → Add sheet). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(vm: BrowserViewModel = hiltViewModel()) {
    val tabs by vm.tabs.collectAsState()
    val selected by vm.selected.collectAsState()
    val bookmarks by vm.bookmarkList.collectAsState()
    val history by vm.historyList.collectAsState()
    val intercepted by vm.intercepted.collectAsState()
    val ctx = LocalContext.current
    var address by remember { mutableStateOf("") }
    var progress by remember { mutableIntStateOf(0) }
    var sheet by remember { mutableStateOf(BrowserSheet.None) }
    var showAdd by remember { mutableStateOf(false) }
    var bookmarked by remember { mutableStateOf(false) }
    val tab = tabs.firstOrNull { it.id == selected }

    LaunchedEffect(tab?.url) {
        address = tab?.url.orEmpty()
        bookmarked = tab?.url?.let { vm.isBookmarked(it) } == true
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
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                ) {
                    IconButton(onClick = { vm.newTab() }) {
                        Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.new_tab))
                    }
                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        placeholder = { Text(stringResource(R.string.address_hint)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { vm.go(address) }),
                        modifier = Modifier.weight(1f).padding(vertical = 4.dp),
                    )
                    IconButton(onClick = { sheet = BrowserSheet.Tabs }) {
                        Text("${tabs.size}", style = MaterialTheme.typography.titleMedium)
                    }
                    var menu by remember { mutableStateOf(false) }
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.more_actions_simple))
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.bookmarks)) },
                            onClick = { menu = false; sheet = BrowserSheet.Bookmarks },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.history)) },
                            onClick = { menu = false; sheet = BrowserSheet.History },
                        )
                        DropdownMenuItem(
                            text = { Text(if (bookmarked) stringResource(R.string.remove_bookmark) else stringResource(R.string.add_bookmark)) },
                            onClick = {
                                menu = false
                                tab?.let { vm.toggleBookmark(it.url, it.title) }
                            },
                            enabled = !tab?.url.isNullOrBlank(),
                        )
                    }
                }
                if (progress in 1..99) LinearProgressIndicator(progress = progress / 100f, modifier = Modifier.fillMaxWidth())
            }
        },
    ) { pads ->
        Column(Modifier.fillMaxSize().padding(pads)) {
            val url = tab?.url.orEmpty()
            if (url.isBlank()) {
                StartPage(
                    bookmarks = bookmarks,
                    onOpen = { vm.go(it) },
                )
            } else {
                var webRef by remember(tab.id) { mutableStateOf<WebView?>(null) }
                var canGoBack by remember(tab.id) { mutableStateOf(false) }
                var canGoForward by remember(tab.id) { mutableStateOf(false) }
                BackHandler(enabled = canGoBack) {
                    webRef?.goBack()
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    IconButton(onClick = { webRef?.goBack() }, enabled = canGoBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                    IconButton(onClick = { webRef?.goForward() }, enabled = canGoForward) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = stringResource(R.string.forward))
                    }
                    IconButton(onClick = { webRef?.reload() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.reload))
                    }
                    IconButton(onClick = {
                        tab?.let { vm.toggleBookmark(it.url, it.title) }
                    }) {
                        Icon(
                            if (bookmarked) Icons.Filled.Star else Icons.Filled.StarBorder,
                            contentDescription = stringResource(
                                if (bookmarked) R.string.remove_bookmark else R.string.add_bookmark,
                            ),
                        )
                    }
                }
                AndroidView(
                    factory = { c ->
                        WebView(c).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.mediaPlaybackRequiresUserGesture = false
                            settings.userAgentString = settings.userAgentString
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
                                    val u = r.url.toString()
                                    return if (u.startsWith("http://") || u.startsWith("https://")) {
                                        false
                                    } else {
                                        runCatching {
                                            c.startActivity(Intent(Intent.ACTION_VIEW, r.url))
                                        }
                                        true
                                    }
                                }

                                override fun onPageStarted(v: WebView, u: String, icon: Bitmap?) {
                                    address = u
                                }

                                override fun onPageFinished(v: WebView, u: String) {
                                    vm.onPage(u, v.title.orEmpty())
                                    webRef?.let {
                                        canGoBack = it.canGoBack()
                                        canGoForward = it.canGoForward()
                                    }
                                }
                            }
                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(v: WebView, p: Int) {
                                    progress = p
                                }

                                override fun onReceivedTitle(v: WebView, t: String?) {
                                    vm.onPage(v.url.orEmpty(), t.orEmpty())
                                }
                            }
                            setDownloadListener { u, _, _, _, _, _ -> vm.onDownload(u) }
                            loadUrl(tab.url)
                        }
                    },
                    update = { wv ->
                        webRef = wv
                        vm.consumeLoad()?.let { wv.loadUrl(it) }
                    },
                    modifier = Modifier.fillMaxSize(),
                    onRelease = { it.destroy() },
                )
                DisposableEffect(tab.id) {
                    onDispose { webRef?.destroy(); webRef = null }
                }
            }
        }
    }

    when (sheet) {
        BrowserSheet.Tabs -> TabsSheet(
            tabs = tabs,
            selected = selected,
            onSelect = { vm.select(it); sheet = BrowserSheet.None },
            onClose = { vm.closeTab(it) },
            onNew = { vm.newTab(); sheet = BrowserSheet.None },
            onDismiss = { sheet = BrowserSheet.None },
        )
        BrowserSheet.Bookmarks -> BookmarksSheet(
            bookmarks = bookmarks,
            onOpen = { vm.go(it); sheet = BrowserSheet.None },
            onDelete = { vm.toggleBookmark(it, it) },
            onDismiss = { sheet = BrowserSheet.None },
        )
        BrowserSheet.History -> HistorySheet(
            history = history,
            onOpen = { vm.go(it); sheet = BrowserSheet.None },
            onClear = { vm.clearHistory() },
            onDismiss = { sheet = BrowserSheet.None },
        )
        BrowserSheet.None -> Unit
    }
    if (showAdd || intercepted != null) {
        AddSheet(
            initialUrl = intercepted ?: "",
            onDismiss = { showAdd = false; vm.consumeIntercepted() },
            onStartService = { withNotifPerm { DownloadService.start(ctx, it) } },
        )
    }
    // Intercepted download opens the sheet (pendingUrl-style prefill).
    LaunchedEffect(intercepted) {
        if (intercepted != null) showAdd = true
    }
}

@Composable
private fun StartPage(bookmarks: List<com.elejar.ZentraDL.data.local.Bookmark>, onOpen: (String) -> Unit) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxSize().padding(16.dp),
    ) {
        item {
            Text(stringResource(R.string.browser_welcome), style = MaterialTheme.typography.titleMedium)
        }
        items(bookmarks.take(20), key = { it.id }) { b ->
            Column(
                Modifier.fillMaxWidth().clickable { onOpen(b.url) }.padding(vertical = 8.dp),
            ) {
                Text(b.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    b.url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TabsSheet(
    tabs: List<WebTab>,
    selected: Long,
    onSelect: (Long) -> Unit,
    onClose: (Long) -> Unit,
    onNew: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(R.string.tabs_n, tabs.size),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onNew) { Text(stringResource(R.string.new_tab)) }
                }
            }
            items(tabs, key = { it.id }) { t ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.weight(1f).clickable { onSelect(t.id) }.padding(vertical = 8.dp),
                    ) {
                        Text(
                            t.title.ifBlank { t.url.ifBlank { stringResource(R.string.new_tab) } },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = if (t.id == selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            t.url,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = { onClose(t.id) }) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close_tab))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookmarksSheet(
    bookmarks: List<com.elejar.ZentraDL.data.local.Bookmark>,
    onOpen: (String) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            item { Text(stringResource(R.string.bookmarks), style = MaterialTheme.typography.titleMedium) }
            if (bookmarks.isEmpty()) {
                item { Text(stringResource(R.string.bookmarks_empty), style = MaterialTheme.typography.bodyMedium) }
            }
            items(bookmarks, key = { it.id }) { b ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.weight(1f).clickable { onOpen(b.url) }.padding(vertical = 8.dp),
                    ) {
                        Text(b.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            b.url,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = { onDelete(b.url) }) {
                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistorySheet(
    history: List<com.elejar.ZentraDL.data.local.HistoryEntry>,
    onOpen: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(R.string.history),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onClear) { Text(stringResource(R.string.clear_history)) }
                }
            }
            if (history.isEmpty()) {
                item { Text(stringResource(R.string.history_empty), style = MaterialTheme.typography.bodyMedium) }
            }
            items(history, key = { it.id }) { h ->
                Column(
                    Modifier.fillMaxWidth().clickable { onOpen(h.url) }.padding(vertical = 8.dp),
                ) {
                    Text(h.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        h.url,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
