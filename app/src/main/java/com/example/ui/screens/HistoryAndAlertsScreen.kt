package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.HistoryFilter
import com.example.ui.MonitorUiState
import com.example.ui.components.CounterSparklineCard
import com.example.ui.components.ReadingHistoryRow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryAndAlertsScreen(
    uiState: MonitorUiState,
    hasNotificationPermission: () -> Boolean,
    onSetBackgroundCheckEnabled: (Boolean) -> Unit,
    onRunBackgroundCheckNow: () -> Unit,
    onSetAutoReloadSeconds: (Int) -> Unit,
    onSetAlertOnIncrease: (Boolean) -> Unit,
    onSetAlertOnChange: (Boolean) -> Unit,
    onSetAlertOnZero: (Boolean) -> Unit,
    onSetThresholdEnabled: (Boolean) -> Unit,
    onSetThresholdValue: (Int) -> Unit,
    onSetSoundEnabled: (Boolean) -> Unit,
    onSetVibrationEnabled: (Boolean) -> Unit,
    onSetNotificationsEnabled: (Boolean) -> Unit,
    onUpdateUrl: (String) -> Unit,
    onResetDefaultUrl: () -> Unit,
    onApplyCustomSelector: (String) -> Unit,
    onSelectHistoryFilter: (HistoryFilter) -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    var permissionGranted by remember { mutableStateOf(hasNotificationPermission()) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionGranted = granted
    }

    var thresholdInput by remember { mutableStateOf(uiState.settings.thresholdValue.toString()) }
    var selectorInput by remember { mutableStateOf(uiState.settings.pinnedSelector) }
    var urlInput by remember { mutableStateOf(uiState.currentUrl) }

    LaunchedEffect(uiState.settings.thresholdValue) {
        thresholdInput = uiState.settings.thresholdValue.toString()
    }
    LaunchedEffect(uiState.settings.pinnedSelector) {
        selectorInput = uiState.settings.pinnedSelector
    }
    LaunchedEffect(uiState.currentUrl) {
        urlInput = uiState.currentUrl
    }

    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    val filteredHistory = remember(uiState.history, uiState.historyFilter) {
        when (uiState.historyFilter) {
            HistoryFilter.ALL -> uiState.history
            HistoryFilter.CHANGES -> uiState.history.filter { it.eventType == "CHANGED" }
            HistoryFilter.ALERTS -> uiState.history.filter { it.eventType == "ALERT" }
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Notification Permission Banner on Android 13+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                !permissionGranted &&
                uiState.settings.notificationsEnabled
            ) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Notifications,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary
                            )
                            Text(
                                text = stringResource(R.string.permission_notifications_needed),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = {
                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("grant_notifications_button")
                            ) {
                                Text(stringResource(R.string.permission_grant_button))
                            }
                        }
                    }
                }
            }

            // Background Worker Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.background_section_title),
                            style = MaterialTheme.typography.titleMedium
                        )

                        SettingToggleRow(
                            label = stringResource(R.string.background_enable_toggle),
                            checked = uiState.settings.backgroundCheckEnabled,
                            onCheckedChange = onSetBackgroundCheckEnabled,
                            testTag = "switch_background_check"
                        )

                        val bgCheckInfo = uiState.settings.lastBackgroundCheckMs?.let { ts ->
                            val timeStr = timeFormat.format(Date(ts))
                            val status = uiState.settings.lastBackgroundStatus
                            stringResource(R.string.background_last_check, "$timeStr • $status")
                        } ?: stringResource(R.string.background_status_never)

                        Text(
                            text = bgCheckInfo,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        FilledTonalButton(
                            onClick = onRunBackgroundCheckNow,
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("run_background_check_now_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.size(6.dp))
                            Text(stringResource(R.string.background_run_now))
                        }
                    }
                }
            }

            // Alert Rules Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.alerts_section_title),
                            style = MaterialTheme.typography.titleMedium
                        )

                        SettingToggleRow(
                            label = stringResource(R.string.alert_on_increase),
                            checked = uiState.settings.alertOnIncrease,
                            onCheckedChange = onSetAlertOnIncrease,
                            testTag = "switch_alert_on_increase"
                        )
                        SettingToggleRow(
                            label = stringResource(R.string.alert_on_change),
                            checked = uiState.settings.alertOnChange,
                            onCheckedChange = onSetAlertOnChange,
                            testTag = "switch_alert_on_change"
                        )
                        SettingToggleRow(
                            label = stringResource(R.string.alert_on_zero),
                            checked = uiState.settings.alertOnZero,
                            onCheckedChange = onSetAlertOnZero,
                            testTag = "switch_alert_on_zero"
                        )
                        SettingToggleRow(
                            label = stringResource(R.string.alert_threshold_toggle),
                            checked = uiState.settings.thresholdEnabled,
                            onCheckedChange = onSetThresholdEnabled,
                            testTag = "switch_alert_threshold"
                        )

                        if (uiState.settings.thresholdEnabled) {
                            OutlinedTextField(
                                value = thresholdInput,
                                onValueChange = { next ->
                                    thresholdInput = next.filter { it.isDigit() }
                                    thresholdInput.toIntOrNull()?.let { onSetThresholdValue(it) }
                                },
                                label = { Text(stringResource(R.string.alert_threshold_label)) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("threshold_input")
                            )
                        }

                        SettingToggleRow(
                            label = stringResource(R.string.alert_sound),
                            checked = uiState.settings.soundEnabled,
                            onCheckedChange = onSetSoundEnabled,
                            testTag = "switch_sound"
                        )
                        SettingToggleRow(
                            label = stringResource(R.string.alert_vibration),
                            checked = uiState.settings.vibrationEnabled,
                            onCheckedChange = onSetVibrationEnabled,
                            testTag = "switch_vibration"
                        )
                        SettingToggleRow(
                            label = stringResource(R.string.alert_notifications),
                            checked = uiState.settings.notificationsEnabled,
                            onCheckedChange = { enabled ->
                                onSetNotificationsEnabled(enabled)
                                if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                    !permissionGranted
                                ) {
                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            },
                            testTag = "switch_notifications"
                        )
                    }
                }
            }

            // Auto-Reload Interval Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = stringResource(R.string.auto_reload_title),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        val options = listOf(0, 15, 30, 60, 300)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            options.forEach { sec ->
                                FilterChip(
                                    selected = uiState.settings.autoReloadSeconds == sec,
                                    onClick = { onSetAutoReloadSeconds(sec) },
                                    label = {
                                        Text(
                                            text = if (sec == 0) {
                                                stringResource(R.string.auto_reload_off)
                                            } else {
                                                stringResource(R.string.auto_reload_every_seconds, sec)
                                            }
                                        )
                                    },
                                    modifier = Modifier.testTag("reload_interval_$sec")
                                )
                            }
                        }
                    }
                }
            }

            // Sparkline Trend Card
            item {
                CounterSparklineCard(readings = uiState.history)
            }

            // Target URL & Custom Selector Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.url_section_title),
                            style = MaterialTheme.typography.titleMedium
                        )
                        OutlinedTextField(
                            value = urlInput,
                            onValueChange = { urlInput = it },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("url_input_field")
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { onUpdateUrl(urlInput) },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("apply_url_button")
                            ) {
                                Text(stringResource(R.string.selector_apply))
                            }
                            OutlinedButton(
                                onClick = onResetDefaultUrl,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("webview_reset_url_button")
                            ) {
                                Text(stringResource(R.string.action_reset_url))
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = stringResource(R.string.selector_section_title),
                            style = MaterialTheme.typography.titleMedium
                        )
                        OutlinedTextField(
                            value = selectorInput,
                            onValueChange = { selectorInput = it },
                            placeholder = { Text(stringResource(R.string.selector_hint)) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("custom_selector_input")
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { onApplyCustomSelector(selectorInput) },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("apply_selector_button")
                            ) {
                                Text(stringResource(R.string.selector_apply))
                            }
                            if (selectorInput.isNotBlank()) {
                                OutlinedButton(
                                    onClick = {
                                        selectorInput = ""
                                        onApplyCustomSelector("")
                                    },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("reset_selector_button")
                                ) {
                                    Text(stringResource(R.string.action_clear_pin))
                                }
                            }
                        }
                    }
                }
            }

            // History Header & Filters
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.history_section_title),
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (uiState.history.isNotEmpty()) {
                            TextButton(
                                onClick = onClearHistory,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("clear_history_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteOutline,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.size(4.dp))
                                Text(stringResource(R.string.action_clear_history))
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = uiState.historyFilter == HistoryFilter.ALL,
                            onClick = { onSelectHistoryFilter(HistoryFilter.ALL) },
                            label = { Text(stringResource(R.string.filter_all)) },
                            modifier = Modifier.testTag("filter_all_chip")
                        )
                        FilterChip(
                            selected = uiState.historyFilter == HistoryFilter.CHANGES,
                            onClick = { onSelectHistoryFilter(HistoryFilter.CHANGES) },
                            label = { Text(stringResource(R.string.filter_changes)) },
                            modifier = Modifier.testTag("filter_changes_chip")
                        )
                        FilterChip(
                            selected = uiState.historyFilter == HistoryFilter.ALERTS,
                            onClick = { onSelectHistoryFilter(HistoryFilter.ALERTS) },
                            label = { Text(stringResource(R.string.filter_alerts)) },
                            modifier = Modifier.testTag("filter_alerts_chip")
                        )
                    }
                }
            }

            if (filteredHistory.isEmpty()) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Text(
                            text = stringResource(R.string.history_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(24.dp)
                        )
                    }
                }
            } else {
                items(
                    items = filteredHistory,
                    key = { it.id }
                ) { reading ->
                    ReadingHistoryRow(reading = reading, timeFormat = timeFormat)
                }
            }
        }
    }
}

@Composable
private fun SettingToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}
