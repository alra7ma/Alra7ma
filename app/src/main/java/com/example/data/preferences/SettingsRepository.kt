package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.monitorDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "monitor_settings"
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val TARGET_URL = stringPreferencesKey("target_url")
        val PINNED_SELECTOR = stringPreferencesKey("pinned_selector")
        val AUTO_RELOAD_SECONDS = intPreferencesKey("auto_reload_seconds")
        val BACKGROUND_CHECK_ENABLED = booleanPreferencesKey("background_check_enabled")
        val ALERT_ON_INCREASE = booleanPreferencesKey("alert_on_increase")
        val ALERT_ON_CHANGE = booleanPreferencesKey("alert_on_change")
        val ALERT_ON_ZERO = booleanPreferencesKey("alert_on_zero")
        val THRESHOLD_ENABLED = booleanPreferencesKey("threshold_enabled")
        val THRESHOLD_VALUE = intPreferencesKey("threshold_value")
        val SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
        val VIBRATION_ENABLED = booleanPreferencesKey("vibration_enabled")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val LAST_KNOWN_NUMERIC = longPreferencesKey("last_known_numeric")
        val HAS_LAST_KNOWN_NUMERIC = booleanPreferencesKey("has_last_known_numeric")
        val LAST_KNOWN_RAW = stringPreferencesKey("last_known_raw")
        val LAST_BG_CHECK_MS = longPreferencesKey("last_bg_check_ms")
        val LAST_BG_STATUS = stringPreferencesKey("last_bg_status")
    }

    val settingsFlow: Flow<MonitorSettings> = context.monitorDataStore.data.map { prefs ->
        val hasNumeric = prefs[Keys.HAS_LAST_KNOWN_NUMERIC] ?: false
        MonitorSettings(
            targetUrl = prefs[Keys.TARGET_URL] ?: VodafonePageDefaults.DEFAULT_URL,
            pinnedSelector = prefs[Keys.PINNED_SELECTOR] ?: "",
            autoReloadSeconds = prefs[Keys.AUTO_RELOAD_SECONDS] ?: 0,
            backgroundCheckEnabled = prefs[Keys.BACKGROUND_CHECK_ENABLED] ?: true,
            alertOnIncrease = prefs[Keys.ALERT_ON_INCREASE] ?: true,
            alertOnChange = prefs[Keys.ALERT_ON_CHANGE] ?: false,
            alertOnZero = prefs[Keys.ALERT_ON_ZERO] ?: false,
            thresholdEnabled = prefs[Keys.THRESHOLD_ENABLED] ?: false,
            thresholdValue = prefs[Keys.THRESHOLD_VALUE] ?: 60,
            soundEnabled = prefs[Keys.SOUND_ENABLED] ?: true,
            vibrationEnabled = prefs[Keys.VIBRATION_ENABLED] ?: true,
            notificationsEnabled = prefs[Keys.NOTIFICATIONS_ENABLED] ?: true,
            lastKnownNumeric = if (hasNumeric) prefs[Keys.LAST_KNOWN_NUMERIC] else null,
            lastKnownRaw = prefs[Keys.LAST_KNOWN_RAW] ?: "",
            lastBackgroundCheckMs = prefs[Keys.LAST_BG_CHECK_MS],
            lastBackgroundStatus = prefs[Keys.LAST_BG_STATUS] ?: ""
        )
    }

    suspend fun getSnapshot(): MonitorSettings = settingsFlow.first()

    suspend fun updateTargetUrl(url: String) {
        context.monitorDataStore.edit { it[Keys.TARGET_URL] = url.trim() }
    }

    suspend fun updatePinnedSelector(selector: String) {
        context.monitorDataStore.edit { it[Keys.PINNED_SELECTOR] = selector.trim() }
    }

    suspend fun updateAutoReloadSeconds(seconds: Int) {
        context.monitorDataStore.edit { it[Keys.AUTO_RELOAD_SECONDS] = seconds }
    }

    suspend fun updateBackgroundCheckEnabled(enabled: Boolean) {
        context.monitorDataStore.edit { it[Keys.BACKGROUND_CHECK_ENABLED] = enabled }
    }

    suspend fun updateAlertOnIncrease(enabled: Boolean) {
        context.monitorDataStore.edit { it[Keys.ALERT_ON_INCREASE] = enabled }
    }

    suspend fun updateAlertOnChange(enabled: Boolean) {
        context.monitorDataStore.edit { it[Keys.ALERT_ON_CHANGE] = enabled }
    }

    suspend fun updateAlertOnZero(enabled: Boolean) {
        context.monitorDataStore.edit { it[Keys.ALERT_ON_ZERO] = enabled }
    }

    suspend fun updateThresholdEnabled(enabled: Boolean) {
        context.monitorDataStore.edit { it[Keys.THRESHOLD_ENABLED] = enabled }
    }

    suspend fun updateThresholdValue(value: Int) {
        context.monitorDataStore.edit { it[Keys.THRESHOLD_VALUE] = value.coerceAtLeast(0) }
    }

    suspend fun updateSoundEnabled(enabled: Boolean) {
        context.monitorDataStore.edit { it[Keys.SOUND_ENABLED] = enabled }
    }

    suspend fun updateVibrationEnabled(enabled: Boolean) {
        context.monitorDataStore.edit { it[Keys.VIBRATION_ENABLED] = enabled }
    }

    suspend fun updateNotificationsEnabled(enabled: Boolean) {
        context.monitorDataStore.edit { it[Keys.NOTIFICATIONS_ENABLED] = enabled }
    }

    suspend fun updateLastKnownReading(raw: String, numeric: Long?) {
        context.monitorDataStore.edit { prefs ->
            prefs[Keys.LAST_KNOWN_RAW] = raw
            if (numeric != null) {
                prefs[Keys.HAS_LAST_KNOWN_NUMERIC] = true
                prefs[Keys.LAST_KNOWN_NUMERIC] = numeric
            } else {
                prefs[Keys.HAS_LAST_KNOWN_NUMERIC] = false
                prefs.remove(Keys.LAST_KNOWN_NUMERIC)
            }
        }
    }

    suspend fun updateBackgroundStatus(timestampMs: Long, status: String) {
        context.monitorDataStore.edit { prefs ->
            prefs[Keys.LAST_BG_CHECK_MS] = timestampMs
            prefs[Keys.LAST_BG_STATUS] = status
        }
    }
}
