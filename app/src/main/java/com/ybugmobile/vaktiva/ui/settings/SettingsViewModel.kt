package com.ybugmobile.vaktiva.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ybugmobile.vaktiva.data.local.preferences.SettingsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsManager: SettingsManager
) : ViewModel() {

    val settings = settingsManager.settingsFlow

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
}
