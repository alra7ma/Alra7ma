package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.alerts.CounterAlertManager
import com.example.data.local.AppDatabase
import com.example.data.local.CounterReadingEntity
import com.example.data.local.ReadingEventType
import com.example.data.preferences.MonitorSettings
import com.example.data.preferences.SettingsRepository
import com.example.data.preferences.VodafonePageDefaults
import com.example.data.repository.CounterRepository
import com.example.webview.CounterBridgeCallbacks
import com.example.webview.CounterCandidate
import com.example.webview.DomScanPayload
import com.example.webview.PickedElementPayload
import com.example.worker.BackgroundMonitorScheduler
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class PageStatus {
    LOADING,
    MONITORING,
    PAUSED,
    ERROR
}

enum class HistoryFilter {
    ALL,
    CHANGES,
    ALERTS
}

data class AlertBanner(
    val title: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class MonitorUiState(
    val currentUrl: String = VodafonePageDefaults.DEFAULT_URL,
    val pageTitle: String = "",
    val pageProgress: Int = 0,
    val pageStatus: PageStatus = PageStatus.LOADING,
    val errorMessage: String? = null,
    val isMonitoring: Boolean = true,
    val isPickerActive: Boolean = false,
    val currentValue: String = "--",
    val previousValue: String? = null,
    val currentNumeric: Long? = null,
    val deltaNumeric: Long? = null,
    val currentLabel: String = "",
    val activeSelector: String = "",
    val candidates: List<CounterCandidate> = emptyList(),
    val lastUpdatedMs: Long? = null,
    val changesCount: Int = 0,
    val nextReloadInSeconds: Int? = null,
    val activeAlert: AlertBanner? = null,
    val settings: MonitorSettings = MonitorSettings(),
    val history: List<CounterReadingEntity> = emptyList(),
    val historyFilter: HistoryFilter = HistoryFilter.ALL
)

class CounterViewModel(application: Application) : AndroidViewModel(application),
    CounterBridgeCallbacks {

    private val repository: CounterRepository = CounterRepository(
        counterDao = AppDatabase.getInstance(application).counterDao(),
        settingsRepository = SettingsRepository(application)
    )
    private val alertManager = CounterAlertManager(application)
    private val backgroundScheduler = BackgroundMonitorScheduler(application)

    private val _liveState = MutableStateFlow(MonitorUiState())
    private val _historyFilter = MutableStateFlow(HistoryFilter.ALL)

    val uiState: StateFlow<MonitorUiState> = combine(
        _liveState,
        repository.settings,
        repository.recentReadings,
        _historyFilter
    ) { live, settings, readings, filter ->
        live.copy(
            settings = settings,
            history = readings,
            historyFilter = filter
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MonitorUiState()
    )

    private var reloadTriggerCallback: (() -> Unit)? = null
    private var pinnedSelectorSyncCallback: ((String) -> Unit)? = null
    private var pickerModeSyncCallback: ((Boolean) -> Unit)? = null
    private var urlLoadCallback: ((String) -> Unit)? = null

    private var autoReloadJob: Job? = null
    private var lastThresholdAlertedValue: Long? = null
    private var lastZeroAlerted: Boolean = false

    init {
        viewModelScope.launch {
            repository.settings.collect { settings ->
                pinnedSelectorSyncCallback?.invoke(settings.pinnedSelector)
                restartAutoReloadTimer(settings.autoReloadSeconds)
                backgroundScheduler.syncPeriodicCheck(settings.backgroundCheckEnabled)
            }
        }
    }

    fun attachWebCallbacks(
        onReload: () -> Unit,
        onLoadUrl: (String) -> Unit,
        onSetPinnedSelector: (String) -> Unit,
        onSetPickerMode: (Boolean) -> Unit
    ) {
        reloadTriggerCallback = onReload
        urlLoadCallback = onLoadUrl
        pinnedSelectorSyncCallback = onSetPinnedSelector
        pickerModeSyncCallback = onSetPickerMode
        val settings = uiState.value.settings
        onSetPinnedSelector(settings.pinnedSelector)
        onLoadUrl(settings.targetUrl)
    }

    private fun restartAutoReloadTimer(intervalSeconds: Int) {
        autoReloadJob?.cancel()
        if (intervalSeconds <= 0) {
            _liveState.update { it.copy(nextReloadInSeconds = null) }
            return
        }
        autoReloadJob = viewModelScope.launch {
            var remaining = intervalSeconds
            while (true) {
                _liveState.update {
                    it.copy(nextReloadInSeconds = if (_liveState.value.isMonitoring) remaining else null)
                }
                delay(1000L)
                if (!_liveState.value.isMonitoring) continue
                remaining--
                if (remaining <= 0) {
                    reloadTriggerCallback?.invoke()
                    remaining = intervalSeconds
                }
            }
        }
    }

    fun toggleMonitoring() {
        _liveState.update { state ->
            val next = !state.isMonitoring
            state.copy(
                isMonitoring = next,
                pageStatus = if (next) PageStatus.MONITORING else PageStatus.PAUSED
            )
        }
    }

    fun requestReload() {
        _liveState.update { it.copy(pageStatus = PageStatus.LOADING, errorMessage = null) }
        reloadTriggerCallback?.invoke()
    }

    fun runBackgroundCheckNow() {
        backgroundScheduler.runImmediateBackgroundCheck()
    }

    fun togglePickerMode() {
        val next = !_liveState.value.isPickerActive
        _liveState.update { it.copy(isPickerActive = next) }
        pickerModeSyncCallback?.invoke(next)
    }

    fun disablePickerMode() {
        _liveState.update { it.copy(isPickerActive = false) }
        pickerModeSyncCallback?.invoke(false)
    }

    fun selectCandidate(candidate: CounterCandidate) {
        viewModelScope.launch {
            repository.setPinnedSelector(candidate.selector)
            pinnedSelectorSyncCallback?.invoke(candidate.selector)
        }
    }

    fun setCustomSelector(selector: String) {
        viewModelScope.launch {
            repository.setPinnedSelector(selector)
            pinnedSelectorSyncCallback?.invoke(selector.trim())
        }
    }

    fun clearPinnedSelector() {
        viewModelScope.launch {
            repository.setPinnedSelector("")
            pinnedSelectorSyncCallback?.invoke("")
        }
    }

    fun updateUrl(url: String) {
        val normalized = if (url.startsWith("http://") || url.startsWith("https://")) {
            url.trim()
        } else {
            "https://${url.trim()}"
        }
        viewModelScope.launch {
            repository.setTargetUrl(normalized)
            _liveState.update { it.copy(currentUrl = normalized, pageStatus = PageStatus.LOADING) }
            urlLoadCallback?.invoke(normalized)
        }
    }

    fun resetDefaultUrl() {
        updateUrl(VodafonePageDefaults.DEFAULT_URL)
    }

    fun captureManualSnapshot() {
        val state = _liveState.value
        if (state.currentValue == "--") return
        viewModelScope.launch {
            repository.recordReading(
                CounterReadingEntity(
                    url = state.currentUrl,
                    selector = state.activeSelector,
                    label = state.currentLabel.ifBlank { "عداد الصفحة" },
                    rawValue = state.currentValue,
                    numericValue = state.currentNumeric,
                    previousValue = state.previousValue,
                    eventType = ReadingEventType.SNAPSHOT.name
                )
            )
        }
    }

    fun dismissAlertBanner() {
        _liveState.update { it.copy(activeAlert = null) }
    }

    fun setHistoryFilter(filter: HistoryFilter) {
        _historyFilter.value = filter
    }

    fun clearHistory() {
        viewModelScope.launch { repository.clearHistory() }
    }

    fun setAutoReloadSeconds(seconds: Int) {
        viewModelScope.launch { repository.setAutoReloadSeconds(seconds) }
    }

    fun setBackgroundCheckEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setBackgroundCheckEnabled(enabled)
            backgroundScheduler.syncPeriodicCheck(enabled)
        }
    }

    fun setAlertOnIncrease(enabled: Boolean) {
        viewModelScope.launch { repository.setAlertOnIncrease(enabled) }
    }

    fun setAlertOnChange(enabled: Boolean) {
        viewModelScope.launch { repository.setAlertOnChange(enabled) }
    }

    fun setAlertOnZero(enabled: Boolean) {
        viewModelScope.launch { repository.setAlertOnZero(enabled) }
    }

    fun setThresholdEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setThresholdEnabled(enabled) }
    }

    fun setThresholdValue(value: Int) {
        viewModelScope.launch { repository.setThresholdValue(value) }
    }

    fun setSoundEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setSoundEnabled(enabled) }
    }

    fun setVibrationEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setVibrationEnabled(enabled) }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setNotificationsEnabled(enabled) }
    }

    fun hasNotificationPermission(): Boolean = alertManager.hasNotificationPermission()

    // --- CounterBridgeCallbacks ---

    override fun onPageProgress(progress: Int) {
        _liveState.update { it.copy(pageProgress = progress) }
    }

    override fun onPageStarted(url: String) {
        _liveState.update {
            it.copy(
                currentUrl = url,
                pageStatus = if (it.isMonitoring) PageStatus.LOADING else PageStatus.PAUSED,
                errorMessage = null
            )
        }
    }

    override fun onPageFinished(url: String) {
        _liveState.update {
            it.copy(
                currentUrl = url,
                pageProgress = 100,
                pageStatus = if (it.isMonitoring) PageStatus.MONITORING else PageStatus.PAUSED,
                errorMessage = null
            )
        }
    }

    override fun onPageError(description: String) {
        _liveState.update {
            it.copy(
                pageStatus = PageStatus.ERROR,
                errorMessage = description
            )
        }
    }

    override fun onElementPicked(picked: PickedElementPayload) {
        _liveState.update { it.copy(isPickerActive = false) }
        viewModelScope.launch {
            repository.setPinnedSelector(picked.selector)
        }
    }

    override fun onCounterPayload(payload: DomScanPayload) {
        val currentState = _liveState.value
        if (!currentState.isMonitoring) return

        val primary = payload.primary
        if (primary == null) {
            _liveState.update {
                it.copy(
                    currentUrl = payload.url.ifBlank { it.currentUrl },
                    pageTitle = payload.title,
                    candidates = payload.candidates,
                    lastUpdatedMs = System.currentTimeMillis()
                )
            }
            return
        }

        // Determine the Total Counter specifically for increase alerts:
        // If user pinned a selector, treat primary as their chosen target; otherwise strictly use totalCounter (never a timer or secondary value!)
        val settings = uiState.value.settings
        val totalCandidate = if (settings.pinnedSelector.isNotBlank()) {
            primary.takeIf { it.kind != "TIMER" }
        } else {
            payload.totalCounter ?: primary.takeIf { it.kind != "TIMER" }
        }

        val oldVal = currentState.currentValue
        val newVal = primary.value
        val hasInitial = oldVal == "--"
        val valueChanged = !hasInitial && oldVal != newVal
        val prevNumeric = currentState.currentNumeric
        val numeric = primary.numericValue
        val delta = if (numeric != null && prevNumeric != null) numeric - prevNumeric else null

        // Strictly evaluate increase against the total counter value, NOT secondary/other values
        val prevTotalNumeric = settings.lastKnownNumeric
        val newTotalNumeric = totalCandidate?.numericValue
        val totalIncreased = prevTotalNumeric != null &&
            newTotalNumeric != null &&
            newTotalNumeric > prevTotalNumeric

        val now = System.currentTimeMillis()

        // Evaluate alert rules (prioritizing total counter increase!)
        var alertReason: String? = null
        if (settings.alertOnIncrease && totalIncreased && totalCandidate != null) {
            val prevLabelVal = settings.lastKnownRaw.ifBlank { prevTotalNumeric.toString() }
            alertReason = "ارتفعت قيمة العداد الإجمالية من $prevLabelVal إلى ${totalCandidate.value}"
        } else if (settings.alertOnZero && numeric != null && numeric == 0L) {
            if (!lastZeroAlerted) {
                lastZeroAlerted = true
                alertReason = "وصل العداد إلى الصفر"
            }
        } else {
            lastZeroAlerted = false
        }

        if (alertReason == null && settings.thresholdEnabled && numeric != null && numeric > 0L &&
            numeric <= settings.thresholdValue
        ) {
            if (lastThresholdAlertedValue == null || numeric > (lastThresholdAlertedValue ?: 0L)) {
                lastThresholdAlertedValue = numeric
                alertReason = "اقترب العداد من الحد (${settings.thresholdValue})"
            }
        } else if (numeric != null && numeric > settings.thresholdValue) {
            lastThresholdAlertedValue = null
        }

        if (alertReason == null && valueChanged && settings.alertOnChange) {
            alertReason = "تغيّر من $oldVal إلى $newVal"
        }

        if (alertReason != null) {
            alertManager.triggerAlert(
                label = (totalCandidate ?: primary).label.ifBlank { "العداد الإجمالي" },
                value = (totalCandidate ?: primary).value,
                reason = alertReason,
                playSound = settings.soundEnabled,
                vibrate = settings.vibrationEnabled,
                postNotification = settings.notificationsEnabled
            )
        }

        // Persist total baseline for WorkManager + meaningful events in Room
        val totalChanged = totalCandidate != null &&
            (settings.lastKnownRaw.isBlank() || settings.lastKnownRaw != totalCandidate.value)
        if (hasInitial || valueChanged || totalChanged || alertReason != null) {
            val eventType = when {
                alertReason != null -> ReadingEventType.ALERT
                hasInitial -> ReadingEventType.INITIAL
                else -> ReadingEventType.CHANGED
            }
            viewModelScope.launch {
                if (totalCandidate != null) {
                    repository.updateLastKnownReading(
                        raw = totalCandidate.value,
                        numeric = totalCandidate.numericValue
                    )
                }
                repository.recordReading(
                    CounterReadingEntity(
                        url = payload.url.ifBlank { currentState.currentUrl },
                        selector = primary.selector,
                        label = primary.label.ifBlank { "العداد الإجمالي" },
                        rawValue = newVal,
                        numericValue = numeric,
                        previousValue = if (hasInitial) null else oldVal,
                        eventType = eventType.name,
                        timestamp = now
                    )
                )
            }
        }

        _liveState.update { state ->
            state.copy(
                currentUrl = payload.url.ifBlank { state.currentUrl },
                pageTitle = payload.title,
                pageStatus = PageStatus.MONITORING,
                currentValue = newVal,
                previousValue = if (valueChanged) oldVal else state.previousValue,
                currentNumeric = numeric,
                deltaNumeric = if (valueChanged) delta else state.deltaNumeric,
                currentLabel = primary.label,
                activeSelector = primary.selector,
                candidates = payload.candidates,
                lastUpdatedMs = now,
                changesCount = if (valueChanged) state.changesCount + 1 else state.changesCount,
                activeAlert = if (alertReason != null) {
                    AlertBanner(
                        title = primary.label.ifBlank { "تنبيه العداد" },
                        message = "$newVal — $alertReason",
                        timestamp = now
                    )
                } else state.activeAlert
            )
        }
    }
}
