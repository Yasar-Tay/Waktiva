package com.ybugmobile.waktiva.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ybugmobile.waktiva.data.local.preferences.SettingsManager
import com.ybugmobile.waktiva.domain.model.PrayerDay
import com.ybugmobile.waktiva.domain.repository.PrayerRepository
import com.ybugmobile.waktiva.domain.manager.TimeManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsManager: SettingsManager,
    private val prayerRepository: PrayerRepository,
    private val timeManager: TimeManager
) : ViewModel() {

    sealed interface UiEvent {
        data object PrayerHistoryDeleted : UiEvent
        data object PrayerHistoryDeleteFailed : UiEvent
    }

    private val _uiEvents = MutableSharedFlow<UiEvent>(extraBufferCapacity = 1)
    val uiEvents = _uiEvents.asSharedFlow()

    val settings = settingsManager.settingsFlow
    val currentTime = timeManager.currentTime

    val allPrayerDays: StateFlow<List<PrayerDay>> = prayerRepository.getPrayerDays()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setMadhab(madhab: Int) {
        viewModelScope.launch {
            settingsManager.updateMadhab(madhab)
            val s = settingsManager.settingsFlow.first()
            val lat = s.latitude ?: return@launch
            val lng = s.longitude ?: return@launch
            prayerRepository.recalculatePrayerTimesLocally(s.calculationMethod, madhab, lat, lng)
        }
    }

    fun setCalculationMethod(method: Int) {
        viewModelScope.launch {
            settingsManager.updateCalculationMethod(method)
            val s = settingsManager.settingsFlow.first()
            val lat = s.latitude ?: return@launch
            val lng = s.longitude ?: return@launch
            prayerRepository.recalculatePrayerTimesLocally(method, s.madhab, lat, lng)
        }
    }

    fun updateLanguage(language: String, onUpdated: () -> Unit = {}) {
        viewModelScope.launch {
            settingsManager.updateLanguage(language)
            onUpdated()
        }
    }

    fun setPlayAdhanAudio(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.updatePlayAdhanAudio(enabled)
        }
    }

    fun setSetupComplete(complete: Boolean) {
        viewModelScope.launch {
            settingsManager.setSetupComplete(complete)
        }
    }

    fun deletePastData() {
        viewModelScope.launch {
            try {
                val today = LocalDate.now().toString()
                prayerRepository.deletePastData(today)
                _uiEvents.emit(UiEvent.PrayerHistoryDeleted)
            } catch (_: Exception) {
                _uiEvents.emit(UiEvent.PrayerHistoryDeleteFailed)
            }
        }
    }

    fun setShowWeatherEffects(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.updateShowWeatherEffects(enabled)
        }
    }

    fun setSilentPrayerNotification(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.updateShowSilentPrayerNotification(enabled)
        }
    }
}
