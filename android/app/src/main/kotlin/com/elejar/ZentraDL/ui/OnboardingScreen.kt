package com.elejar.ZentraDL.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.elejar.ZentraDL.R
import com.elejar.ZentraDL.designsystem.components.SwitchRow
import com.elejar.ZentraDL.designsystem.theme.ThemeMode
import kotlinx.coroutines.launch

/** Onboarding ≤3 skippable screens (U10): value, theme, smart preview. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    onDone: () -> Unit,
    vm: SettingsViewModel = hiltViewModel(),
) {
    val pager = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()
    val themeMode by vm.themeMode.collectAsState()
    val smartMaster by vm.smartMaster.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                actions = {
                    TextButton(onClick = { vm.setOnboarded { onDone() } }) {
                        Text(stringResource(R.string.skip))
                    }
                },
            )
        },
    ) { pads ->
        Column(Modifier.fillMaxSize().padding(pads).padding(24.dp)) {
            HorizontalPager(state = pager, modifier = Modifier.weight(1f).fillMaxWidth()) { page ->
                when (page) {
                    0 -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.ob1_title), style = MaterialTheme.typography.headlineMedium)
                        Text(stringResource(R.string.ob1_text), style = MaterialTheme.typography.bodyLarge)
                    }
                    1 -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.ob2_title), style = MaterialTheme.typography.headlineMedium)
                        Text(stringResource(R.string.ob2_text), style = MaterialTheme.typography.bodyLarge)
                        ThemeMode.entries.forEach { m ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                androidx.compose.material3.RadioButton(
                                    selected = themeMode == m.name,
                                    onClick = { vm.setThemeMode(m.name) },
                                )
                                Text(m.name, modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                    else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.ob3_title), style = MaterialTheme.typography.headlineMedium)
                        Text(stringResource(R.string.ob3_text), style = MaterialTheme.typography.bodyLarge)
                        SwitchRow(
                            title = stringResource(R.string.smart_master),
                            description = stringResource(R.string.smart_master_desc),
                            checked = smartMaster,
                            onCheckedChange = { vm.setSmartMaster(it) },
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Text(
                    "${pager.currentPage + 1} / 3",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f).align(Alignment.CenterVertically),
                )
                if (pager.currentPage < 2) {
                    Button(onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }) {
                        Text(stringResource(R.string.next))
                    }
                } else {
                    Button(onClick = { vm.setOnboarded { onDone() } }) {
                        Text(stringResource(R.string.done))
                    }
                }
            }
        }
    }
}
