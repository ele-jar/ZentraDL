package com.elejar.ZentraDL.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.elejar.ZentraDL.R

/** Activity: insights + auto-action log with Undo (U8). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityScreen(vm: ActivityViewModel = hiltViewModel()) {
    val records by vm.records.collectAsState()
    val categories by vm.categories.collectAsState()
    val log by vm.log.collectAsState()
    val catNames = rememberCatNames(categories)

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.activity_tab)) }) },
    ) { pads ->
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize().padding(pads).padding(16.dp),
        ) {
            item {
                val day = 86_400_000L
                val now = System.currentTimeMillis()
                val today = records.count { it.createdAt >= (now / day) * day }
                val week = records.count { it.createdAt >= (now / day) * day - 6 * day }
                val totalMb = records.filter { it.status == "completed" }.sumOf { it.totalBytes.coerceAtLeast(0) }
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.insights_title), style = MaterialTheme.typography.titleSmall)
                        Text(
                            stringResource(R.string.insights_line, today, week, Format.bytes(totalMb)),
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
            item {
                Text(stringResource(R.string.last_7_days), style = MaterialTheme.typography.titleSmall)
                val weeks = ActivityUi.weeklyBytes(records)
                val max = weeks.maxOfOrNull { it.bytes }?.coerceAtLeast(1) ?: 1
                weeks.forEach { d ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            d.label,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(end = 0.dp),
                        )
                        LinearProgressIndicator(
                            progress = (d.bytes.toFloat() / max).coerceIn(0f, 1f),
                            modifier = Modifier.weight(1f).height(6.dp).clip(MaterialTheme.shapes.small),
                        )
                        Text(
                            Format.bytes(d.bytes),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
            item {
                Text(stringResource(R.string.storage_by_category), style = MaterialTheme.typography.titleSmall)
                val byCat = ActivityUi.bytesByCategory(records)
                if (byCat.isEmpty()) {
                    Text(stringResource(R.string.nothing_yet), style = MaterialTheme.typography.bodyMedium)
                }
                byCat.toList().sortedByDescending { it.second }.forEach { (id, bytes) ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(catNames[id] ?: id, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            Format.bytes(bytes),
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(R.string.auto_log),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { vm.clearLog() }) { Text(stringResource(R.string.clear_history)) }
                }
            }
            if (log.isEmpty()) {
                item { Text(stringResource(R.string.auto_log_empty), style = MaterialTheme.typography.bodyMedium) }
            }
            items(log, key = { it.id }) { e ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text(e.text, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                        Text(
                            Format.dayTime(e.timeMs),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (e.undoJson != null) {
                        OutlinedButton(onClick = { vm.undo(e.id) }) {
                            Text(stringResource(R.string.undo))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberCatNames(categories: List<com.elejar.ZentraDL.data.local.Category>): Map<String, String> {
    return remember(categories) { categories.associate { it.id to it.name } }
}
