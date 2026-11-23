package com.example

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.preferences.VodafonePageDefaults
import com.example.ui.CounterViewModel
import com.example.ui.screens.HistoryAndAlertsScreen
import com.example.ui.screens.LiveCounterWebHeader
import com.example.ui.theme.RedCounterTheme
import com.example.webview.CounterWebViewController

enum class AppTab(
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    COUNTER_AND_PAGE(
        labelRes = R.string.tab_counter_page,
        selectedIcon = Icons.Rounded.Speed,
        unselectedIcon = Icons.Outlined.Speed,
        testTag = "nav_tab_counter_page"
    ),
    ALERTS_AND_HISTORY(
        labelRes = R.string.tab_alerts_history,
        selectedIcon = Icons.Rounded.History,
        unselectedIcon = Icons.Outlined.History,
        testTag = "nav_tab_history"
    )
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RedCounterTheme {
                RedCounterMonitorApp()
            }
        }
    }
}

@Composable
fun RedCounterMonitorApp(
    viewModel: CounterViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTab by rememberSaveable { mutableStateOf(AppTab.COUNTER_AND_PAGE) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !viewModel.hasNotificationPermission()
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val webController = remember {
        CounterWebViewController(context = context, callbacks = viewModel)
    }
    val activeWebView by webController.webViewFlow.collectAsStateWithLifecycle()

    LaunchedEffect(webController) {
        viewModel.attachWebCallbacks(
            onReload = { webController.reload() },
            onLoadUrl = { url -> webController.loadUrlIfNeeded(url) },
            onSetPinnedSelector = { selector -> webController.updatePinnedSelector(selector) },
            onSetPickerMode = { enabled -> webController.setPickerMode(enabled) }
        )
    }

    DisposableEffect(webController) {
        onDispose {
            webController.destroy()
        }
    }

    BackHandler(enabled = selectedTab != AppTab.COUNTER_AND_PAGE || webController.webView.canGoBack()) {
        if (selectedTab == AppTab.COUNTER_AND_PAGE && webController.webView.canGoBack()) {
            webController.webView.goBack()
        } else {
            selectedTab = AppTab.COUNTER_AND_PAGE
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                if (!useRail) {
                    NavigationBar {
                        AppTab.entries.forEach { tab ->
                            val selected = selectedTab == tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = { selectedTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = stringResource(tab.labelRes)
                                    )
                                },
                                label = { Text(stringResource(tab.labelRes)) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail(modifier = Modifier.fillMaxHeight()) {
                        AppTab.entries.forEach { tab ->
                            val selected = selectedTab == tab
                            NavigationRailItem(
                                selected = selected,
                                onClick = { selectedTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = stringResource(tab.labelRes)
                                    )
                                },
                                label = { Text(stringResource(tab.labelRes)) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    // Unified Counter Header + Live WebView visible immediately on launch
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (selectedTab == AppTab.COUNTER_AND_PAGE) {
                            LiveCounterWebHeader(
                                uiState = uiState,
                                onReload = { viewModel.requestReload() },
                                onToggleMonitoring = { viewModel.toggleMonitoring() },
                                onTogglePickerMode = { viewModel.togglePickerMode() },
                                onCaptureSnapshot = { viewModel.captureManualSnapshot() },
                                onSelectCandidate = { viewModel.selectCandidate(it) },
                                onClearPin = { viewModel.clearPinnedSelector() },
                                onDismissAlert = { viewModel.dismissAlertBanner() }
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            key(activeWebView) {
                                AndroidView(
                                    factory = { activeWebView },
                                    update = { wv ->
                                        val isLiveTab = selectedTab == AppTab.COUNTER_AND_PAGE
                                        wv.visibility = if (isLiveTab) View.VISIBLE else View.INVISIBLE
                                        wv.importantForAccessibility = if (isLiveTab) {
                                            View.IMPORTANT_FOR_ACCESSIBILITY_YES
                                        } else {
                                            View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .testTag("live_webview")
                                )
                            }

                            WebViewCheckingPill(
                                visible = selectedTab == AppTab.COUNTER_AND_PAGE &&
                                    (uiState.isCheckingCounter || uiState.pageStatus == com.example.ui.PageStatus.LOADING),
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = 12.dp)
                            )
                        }
                    }

                    if (selectedTab == AppTab.ALERTS_AND_HISTORY) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.background
                        ) {
                            HistoryAndAlertsScreen(
                                uiState = uiState,
                                hasNotificationPermission = { viewModel.hasNotificationPermission() },
                                onSetBackgroundCheckEnabled = { viewModel.setBackgroundCheckEnabled(it) },
                                onRunBackgroundCheckNow = { viewModel.runBackgroundCheckNow() },
                                onSetAutoReloadSeconds = { viewModel.setAutoReloadSeconds(it) },
                                onSetAlertOnIncrease = { viewModel.setAlertOnIncrease(it) },
                                onSetAlertOnChange = { viewModel.setAlertOnChange(it) },
                                onSetAlertOnZero = { viewModel.setAlertOnZero(it) },
                                onSetThresholdEnabled = { viewModel.setThresholdEnabled(it) },
                                onSetThresholdValue = { viewModel.setThresholdValue(it) },
                                onSetSoundEnabled = { viewModel.setSoundEnabled(it) },
                                onSetVibrationEnabled = { viewModel.setVibrationEnabled(it) },
                                onSetNotificationsEnabled = { viewModel.setNotificationsEnabled(it) },
                                onUpdateUrl = { url ->
                                    viewModel.updateUrl(url)
                                    val normalized = if (url.startsWith("http://") || url.startsWith("https://")) {
                                        url.trim()
                                    } else {
                                        "https://${url.trim()}"
                                    }
                                    webController.forceLoadUrl(normalized)
                                },
                                onResetDefaultUrl = {
                                    viewModel.resetDefaultUrl()
                                    webController.forceLoadUrl(VodafonePageDefaults.DEFAULT_URL)
                                },
                                onApplyCustomSelector = { viewModel.setCustomSelector(it) },
                                onSelectHistoryFilter = { viewModel.setHistoryFilter(it) },
                                onClearHistory = { viewModel.clearHistory() }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WebViewCheckingPill(
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            tonalElevation = 6.dp,
            shadowElevation = 6.dp,
            border = BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
            ),
            modifier = Modifier.testTag("webview_checking_indicator")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.5.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.checking_counter_in_progress),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
