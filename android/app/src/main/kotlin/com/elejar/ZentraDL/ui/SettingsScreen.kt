package com.elejar.ZentraDL.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.elejar.ZentraDL.BuildConfig
import com.elejar.ZentraDL.R
import com.elejar.ZentraDL.designsystem.components.SliderRow
import com.elejar.ZentraDL.designsystem.components.SwitchRow
import com.elejar.ZentraDL.designsystem.theme.Accent
import com.elejar.ZentraDL.designsystem.theme.ThemeMode

/** Settings v1 (P2c; more groups land in later phases). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: SettingsViewModel = hiltViewModel()) {
    val themeMode by vm.themeMode.collectAsState()
    val accent by vm.accent.collectAsState()
    val dynamic by vm.dynamicColor.collectAsState()
    val connections by vm.connections.collectAsState()
    val maxRunning by vm.maxRunning.collectAsState()
    var query by remember { mutableStateOf("") }
    var choice by remember { mutableStateOf<ChoiceState?>(null) }

    fun matches(vararg texts: String): Boolean {
        val q = query.trim().lowercase()
        return q.isEmpty() || texts.any { it.lowercase().contains(q) }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.settings)) }) },
    ) { pads ->
        LazyColumn(Modifier.fillMaxSize().padding(pads)) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(R.string.search_settings)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            if (matches("appearance", "theme", "accent", "dynamic", "color")) {
                item { SectionHeader(stringResource(R.string.appearance)) }
                item {
                    ChoiceRow(
                        title = stringResource(R.string.theme),
                        description = stringResource(R.string.theme_desc),
                        value = themeMode,
                        onClick = {
                            choice = ChoiceState(
                                "theme", stringResource(R.string.theme),
                                ThemeMode.entries.map { it.name }, themeMode,
                            ) { vm.setThemeMode(it) }
                        },
                    )
                }
                item {
                    ChoiceRow(
                        title = stringResource(R.string.accent),
                        description = stringResource(R.string.accent_desc),
                        value = accent,
                        enabled = !dynamic,
                        onClick = {
                            choice = ChoiceState(
                                "accent", stringResource(R.string.accent),
                                Accent.entries.map { it.name }, accent,
                            ) { vm.setAccent(it) }
                        },
                    )
                }
                item {
                    SwitchRow(
                        title = stringResource(R.string.dynamic_color),
                        description = stringResource(R.string.dynamic_color_desc),
                        checked = dynamic,
                        onCheckedChange = { vm.setDynamicColor(it) },
                    )
                }
            }
            if (matches("downloads", "connections", "simultaneous", "running")) {
                item { SectionHeader(stringResource(R.string.downloads_section)) }
                item {
                    SliderRow(
                        title = stringResource(R.string.connections_title),
                        description = stringResource(R.string.connections_desc),
                        value = connections.toFloat(),
                        valueLabel = "$connections",
                        range = 1f..32f,
                        onValueChange = { vm.setConnections(it.toInt()) },
                    )
                }
                item {
                    SliderRow(
                        title = stringResource(R.string.max_running_title),
                        description = stringResource(R.string.max_running_desc),
                        value = maxRunning.toFloat(),
                        valueLabel = "$maxRunning",
                        range = 1f..30f,
                        onValueChange = { vm.setMaxRunning(it.toInt()) },
                    )
                }
            }
            if (matches("about", "version", "license")) {
                item { SectionHeader(stringResource(R.string.about)) }
                item {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text("ZentraDL ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleSmall)
                        Text(
                            stringResource(R.string.about_text),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
    choice?.let { c ->
        AlertDialog(
            onDismissRequest = { choice = null },
            title = { Text(c.title) },
            text = {
                Column {
                    c.options.forEach { o ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth().clickable { c.onSelect(o); choice = null }.padding(vertical = 8.dp),
                        ) {
                            RadioButton(selected = o == c.selected, onClick = null)
                            Text(o)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { choice = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

private data class ChoiceState(
    val key: String,
    val title: String,
    val options: List<String>,
    val selected: String,
    val onSelect: (String) -> Unit,
)

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun ChoiceRow(
    title: String,
    description: String,
    value: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
