package com.ybugmobile.vaktiva.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ybugmobile.vaktiva.data.local.preferences.SettingsManager
import com.ybugmobile.vaktiva.domain.model.PrayerDay
import com.ybugmobile.vaktiva.domain.repository.PrayerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsManager: SettingsManager,
    private val prayerRepository: PrayerRepository
) : ViewModel() {

    val settings = settingsManager.settingsFlow

    val allPrayerDays: StateFlow<List<PrayerDay>> = prayerRepository.getPrayerDays()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setMadhab(madhab: Int) {
        viewModelScope.launch {
            settingsManager.updateMadhab(madhab)
        }
    }

    fun setCalculationMethod(method: Int) {
        viewModelScope.launch {
            settingsManager.updateCalculationMethod(method)
        }
    }

    fun updateLanguage(language: String) {
        viewModelScope.launch {
            settingsManager.updateLanguage(language)
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
}
