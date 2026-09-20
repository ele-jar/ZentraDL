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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
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
    val categories by vm.categories.collectAsState()
    var query by remember { mutableStateOf("") }
    var choice by remember { mutableStateOf<ChoiceState?>(null) }
    var showAddCat by remember { mutableStateOf(false) }
    var timePick by remember { mutableStateOf<String?>(null) }
    val wifiOnly by vm.wifiOnly.collectAsState()
    val chargingOnly by vm.chargingOnly.collectAsState()
    val schedEnabled by vm.schedEnabled.collectAsState()
    val schedStart by vm.schedStartMin.collectAsState()
    val schedEnd by vm.schedEndMin.collectAsState()
    val speedKbps by vm.speedLimitKbps.collectAsState()
    val themeTitle = stringResource(R.string.theme)
    val accentTitle = stringResource(R.string.accent)

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
                                "theme", themeTitle,
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
                                "accent", accentTitle,
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
            if (matches("storage", "organization", "categor", "folder")) {
                item { SectionHeader(stringResource(R.string.storage_section)) }
                item {
                    Text(
                        stringResource(R.string.categories_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
                categories.forEach { c ->
                    item(key = "cat:${c.id}") {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(c.name, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    c.folder,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (c.id != "other") {
                                IconButton(onClick = { vm.deleteCategory(c.id) }) {
                                    Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = stringResource(R.string.delete_category, c.name),
                                    )
                                }
                            }
                        }
                    }
                }
                item {
                    TextButton(onClick = { showAddCat = true }) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Text(stringResource(R.string.add_category))
                    }
                }
            }
            if (matches("network", "wifi", "charging", "schedule", "speed", "limit")) {
                item { SectionHeader(stringResource(R.string.network_section)) }
                item {
                    SwitchRow(
                        title = stringResource(R.string.wifi_only),
                        description = stringResource(R.string.wifi_only_desc),
                        checked = wifiOnly,
                        onCheckedChange = { vm.setWifiOnly(it) },
                    )
                }
                item {
                    SwitchRow(
                        title = stringResource(R.string.charging_only),
                        description = stringResource(R.string.charging_only_desc),
                        checked = chargingOnly,
                        onCheckedChange = { vm.setChargingOnly(it) },
                    )
                }
                item {
                    SwitchRow(
                        title = stringResource(R.string.schedule),
                        description = stringResource(R.string.schedule_desc),
                        checked = schedEnabled,
                        onCheckedChange = { vm.setSchedEnabled(it) },
                    )
                }
                if (schedEnabled) {
                    item {
                        ChoiceRow(
                            title = stringResource(R.string.schedule_start),
                            description = stringResource(R.string.schedule_start_desc),
                            value = fmtMin(schedStart),
                            onClick = { timePick = "start" },
                        )
                    }
                    item {
                        ChoiceRow(
                            title = stringResource(R.string.schedule_end),
                            description = stringResource(R.string.schedule_end_desc),
                            value = fmtMin(schedEnd),
                            onClick = { timePick = "end" },
                        )
                    }
                }
                item {
                    val mb = speedKbps / 1024
                    SliderRow(
                        title = stringResource(R.string.speed_limit_title),
                        description = stringResource(R.string.speed_limit_desc),
                        value = mb.toFloat(),
                        valueLabel = if (mb == 0) stringResource(R.string.unlimited) else "$mb MB/s",
                        range = 0f..50f,
                        onValueChange = { vm.setSpeedLimitKbps(it.toInt() * 1024) },
                    )
                }
            }
            if (matches("about", "version", "license")) {                item { SectionHeader(stringResource(R.string.about)) }
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
    if (showAddCat) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddCat = false },
            title = { Text(stringResource(R.string.add_category)) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.category_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { vm.addCategory(name); showAddCat = false },
                    enabled = name.isNotBlank(),
                ) { Text(stringResource(R.string.add_category)) }
            },
            dismissButton = {
                TextButton(onClick = { showAddCat = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
    timePick?.let { which ->
        val initial = if (which == "start") schedStart else schedEnd
        val picker = rememberTimePickerState(initialHour = initial / 60, initialMinute = initial % 60, is24Hour = true)
        AlertDialog(
            onDismissRequest = { timePick = null },
            title = {
                Text(stringResource(if (which == "start") R.string.schedule_start else R.string.schedule_end))
            },
            text = { TimePicker(picker) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val m = picker.hour * 60 + picker.minute
                        if (which == "start") vm.setSchedStartMin(m) else vm.setSchedEndMin(m)
                        timePick = null
                    },
                ) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { timePick = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

/** Minutes-of-day → "22:00". */
fun fmtMin(m: Int): String = "%02d:%02d".format(m / 60, m % 60)

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
