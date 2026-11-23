package com.example.data.repository

import com.example.data.local.CounterDao
import com.example.data.local.CounterReadingEntity
import com.example.data.preferences.MonitorSettings
import com.example.data.preferences.SettingsRepository
import kotlinx.coroutines.flow.Flow

class CounterRepository(
    private val counterDao: CounterDao,
    private val settingsRepository: SettingsRepository
) {
    val recentReadings: Flow<List<CounterReadingEntity>> = counterDao.observeRecentReadings()
    val settings: Flow<MonitorSettings> = settingsRepository.settingsFlow

    suspend fun getSettingsSnapshot(): MonitorSettings = settingsRepository.getSnapshot()

    suspend fun recordReading(reading: CounterReadingEntity) {
        counterDao.insertReading(reading)
    }

    suspend fun clearHistory() {
        counterDao.clearAll()
    }

    suspend fun setTargetUrl(url: String) = settingsRepository.updateTargetUrl(url)
    suspend fun setPinnedSelector(selector: String) = settingsRepository.updatePinnedSelector(selector)
    suspend fun setAutoReloadSeconds(seconds: Int) = settingsRepository.updateAutoReloadSeconds(seconds)
    suspend fun setBackgroundCheckEnabled(enabled: Boolean) =
        settingsRepository.updateBackgroundCheckEnabled(enabled)
    suspend fun setAlertOnIncrease(enabled: Boolean) = settingsRepository.updateAlertOnIncrease(enabled)
    suspend fun setAlertOnChange(enabled: Boolean) = settingsRepository.updateAlertOnChange(enabled)
    suspend fun setAlertOnZero(enabled: Boolean) = settingsRepository.updateAlertOnZero(enabled)
    suspend fun setThresholdEnabled(enabled: Boolean) = settingsRepository.updateThresholdEnabled(enabled)
    suspend fun setThresholdValue(value: Int) = settingsRepository.updateThresholdValue(value)
    suspend fun setSoundEnabled(enabled: Boolean) = settingsRepository.updateSoundEnabled(enabled)
    suspend fun setVibrationEnabled(enabled: Boolean) = settingsRepository.updateVibrationEnabled(enabled)
    suspend fun setNotificationsEnabled(enabled: Boolean) = settingsRepository.updateNotificationsEnabled(enabled)

    suspend fun updateLastKnownReading(raw: String, numeric: Long?) =
        settingsRepository.updateLastKnownReading(raw, numeric)

    suspend fun updateBackgroundStatus(timestampMs: Long, status: String) =
        settingsRepository.updateBackgroundStatus(timestampMs, status)
}
