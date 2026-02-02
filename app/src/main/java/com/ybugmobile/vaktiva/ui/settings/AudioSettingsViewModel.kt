package com.ybugmobile.vaktiva.ui.settings

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ybugmobile.vaktiva.R
import com.ybugmobile.vaktiva.data.audio.AdhanAudioManager
import com.ybugmobile.vaktiva.data.audio.AudioPlayer
import com.ybugmobile.vaktiva.data.local.preferences.SettingsManager
import com.ybugmobile.vaktiva.domain.model.PrayerDay
import com.ybugmobile.vaktiva.domain.model.PrayerType
import com.ybugmobile.vaktiva.domain.repository.PrayerRepository
import com.ybugmobile.vaktiva.domain.manager.TimeManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDateTime
import javax.inject.Inject

data class AdhanAudioItem(
    val name: String,
    val path: String,
    val isDefault: Boolean = false,
    val isSelected: Boolean = false,
    val isPlaying: Boolean = false
)

@HiltViewModel
class AudioSettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val audioManager: AdhanAudioManager,
    private val settingsManager: SettingsManager,
    private val audioPlayer: AudioPlayer,
    private val prayerRepository: PrayerRepository,
    private val timeManager: TimeManager
) : ViewModel() {

    private val _currentPlayingPath = MutableStateFlow<String?>(null)
    private val _selectedPrayerType = MutableStateFlow<PrayerType?>(null) // null means global selection
    val selectedPrayerType: StateFlow<PrayerType?> = _selectedPrayerType.asStateFlow()

    val settings = settingsManager.settingsFlow
    val currentTime = timeManager.currentTime

    val allPrayerDays: StateFlow<List<PrayerDay>> = prayerRepository.getPrayerDays()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val audioItems: StateFlow<List<AdhanAudioItem>> = combine(
        settingsManager.settingsFlow,
        _currentPlayingPath,
        _selectedPrayerType
    ) { settings, playingPath, selectedPrayer ->
        val items = mutableListOf<AdhanAudioItem>()

        // 1. Add Default Adhan
        val defaultPath = "android.resource://${context.packageName}/${R.raw.ezan}"
        val isSelected = if (selectedPrayer != null) {
            settings.prayerSpecificAdhanPaths[selectedPrayer] == defaultPath || 
            (settings.prayerSpecificAdhanPaths[selectedPrayer] == null && settings.selectedAdhanPath == defaultPath)
        } else {
            settings.selectedAdhanPath == defaultPath || settings.selectedAdhanPath == null
        }

        items.add(
            AdhanAudioItem(
                name = context.getString(R.string.audio_setting_default),
                path = defaultPath,
                isDefault = true,
                isSelected = isSelected,
                isPlaying = playingPath == defaultPath
            )
        )

        // 2. Add Custom Adhans
        audioManager.getCustomAdhans().forEach { file ->
            val path = file.absolutePath
            val isCustomSelected = if (selectedPrayer != null) {
                settings.prayerSpecificAdhanPaths[selectedPrayer] == path
            } else {
                settings.selectedAdhanPath == path
            }

            items.add(
                AdhanAudioItem(
                    name = file.name,
                    path = path,
                    isSelected = isCustomSelected,
                    isPlaying = playingPath == path
                )
            )
        }
        items
    }.asStateFlow(emptyList())

    private fun <T> kotlinx.coroutines.flow.Flow<T>.asStateFlow(initialValue: T): StateFlow<T> {
        val flow = this
        val state = MutableStateFlow(initialValue)
        viewModelScope.launch {
            flow.collect { state.value = it }
        }
        return state.asStateFlow()
    }

    fun selectPrayerType(type: PrayerType?) {
        _selectedPrayerType.value = type
    }

    fun selectAudio(path: String) {
        viewModelScope.launch {
            val currentPrayer = _selectedPrayerType.value
            if (currentPrayer != null) {
                settingsManager.updatePrayerSpecificAdhanPath(currentPrayer, path)
            } else {
                settingsManager.updateSelectedAdhanPath(path)
            }
        }
    }

    fun toggleUseSpecificAdhan(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.updateUseSpecificAdhan(enabled)
        }
    }

    fun togglePreAdhanWarning(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.updatePreAdhanWarning(enabled)
        }
    }

    fun updatePreAdhanWarningMinutes(minutes: Int) {
        viewModelScope.launch {
            settingsManager.updatePreAdhanWarningMinutes(minutes)
        }
    }

    fun toggleUseFajrAlarmBeforeSunrise(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.updateUseFajrAlarmBeforeSunrise(enabled)
        }
    }

    fun updateFajrAlarmMinutesBeforeSunrise(minutes: Int) {
        viewModelScope.launch {
            settingsManager.updateFajrAlarmMinutesBeforeSunrise(minutes)
        }
    }

    fun togglePreview(path: String) {
        if (_currentPlayingPath.value == path) {
            audioPlayer.stop()
            _currentPlayingPath.value = null
        } else {
            audioPlayer.stop()
            audioPlayer.play(Uri.parse(path))
            _currentPlayingPath.value = path
        }
    }

    fun addCustomAudio(uri: Uri) {
        val mimeType = context.contentResolver.getType(uri)
        if (mimeType == null || !mimeType.startsWith("audio/")) {
            return
        }

        viewModelScope.launch {
            val fileName = getFileName(uri) ?: "custom_adhan_${System.currentTimeMillis()}.mp3"
            val savedPath = audioManager.saveCustomAdhan(uri, fileName)
            if (savedPath != null) {
                selectAudio(savedPath)
            }
        }
    }

    fun deleteAudio(path: String) {
        viewModelScope.launch {
            val file = File(path)
            if (file.exists()) {
                audioManager.deleteAdhan(file)
            }
        }
    }

    private fun getFileName(uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index != -1) {
                        result = cursor.getString(index)
                    }
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/') ?: -1
            if (cut != -1) {
                result = result?.substring(cut + 1)
            }
        }
        return result
    }

    override fun onCleared() {
        super.onCleared()
        audioPlayer.stop()
    }
}
