package com.ybugmobile.waktiva.ui.settings

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ybugmobile.waktiva.R
import com.ybugmobile.waktiva.data.audio.AdhanAudioManager
import com.ybugmobile.waktiva.data.audio.AudioPlayer
import com.ybugmobile.waktiva.data.local.preferences.SettingsManager
import com.ybugmobile.waktiva.domain.model.PrayerDay
import com.ybugmobile.waktiva.domain.model.PrayerType
import com.ybugmobile.waktiva.domain.repository.PrayerRepository
import com.ybugmobile.waktiva.domain.manager.TimeManager
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
import javax.inject.Inject

data class AdhanAudioItem(
    val name: String,
    val artist: String? = null,
    val path: String,
    val isDefault: Boolean = false,
    val isBuiltIn: Boolean = false,
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
    private val _selectedPrayerType = MutableStateFlow<PrayerType?>(null)
    val selectedPrayerType: StateFlow<PrayerType?> = _selectedPrayerType.asStateFlow()

    // Trigger to force refresh the audio list when files are added or deleted
    private val _refreshTrigger = MutableStateFlow(0)

    val settings = settingsManager.settingsFlow
    val currentTime = timeManager.currentTime

    val allPrayerDays: StateFlow<List<PrayerDay>> = prayerRepository.getPrayerDays()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val audioItems: StateFlow<List<AdhanAudioItem>> = combine(
        settingsManager.settingsFlow,
        _currentPlayingPath,
        _selectedPrayerType,
        _refreshTrigger
    ) { settings, playingPath, selectedPrayer, _ ->
        val items = mutableListOf<AdhanAudioItem>()

        // Built-in Adhans
        val rawAdhans = listOf(
            R.raw.muhsinkara_muhayyerkurdi_ezan to true,
            R.raw.muhsinkara_fajr to false,
            R.raw.muhsinkara_asr to false,
            R.raw.muhsinkara_maghrib to false,
            R.raw.muhsinkara_isha to false,
            R.raw.medina to false
        )

        rawAdhans.forEach { (resId, isDefaultFile) ->
            val path = "android.resource://${context.packageName}/$resId"
            val metadata = getAudioMetadata(path)
            
            val isSelected = if (selectedPrayer != null) {
                settings.prayerSpecificAdhanPaths[selectedPrayer] == path || 
                (settings.prayerSpecificAdhanPaths[selectedPrayer] == null && settings.selectedAdhanPath == path) ||
                (settings.prayerSpecificAdhanPaths[selectedPrayer] == null && settings.selectedAdhanPath == null && isDefaultFile)
            } else {
                settings.selectedAdhanPath == path || (settings.selectedAdhanPath == null && isDefaultFile)
            }

            // Provide a better default name if metadata fails
            val displayName = metadata.first ?: when (resId) {
                R.raw.muhsinkara_muhayyerkurdi_ezan -> "Muhayyer Kürdi Ezan"
                R.raw.muhsinkara_fajr -> "Fajr Ezan"
                R.raw.muhsinkara_asr -> "Asr Ezan"
                R.raw.muhsinkara_maghrib -> "Maghrib Ezan"
                R.raw.muhsinkara_isha -> "Isha Ezan"
                R.raw.medina -> "Madinah Ezan"
                else -> context.getString(R.string.audio_setting_default)
            }
            
            val artistName = metadata.second ?: when (resId) {
                R.raw.muhsinkara_muhayyerkurdi_ezan,
                R.raw.muhsinkara_fajr,
                R.raw.muhsinkara_asr,
                R.raw.muhsinkara_maghrib,
                R.raw.muhsinkara_isha -> "Muhsin Kara"
                R.raw.medina -> "Madinah"
                else -> null
            }

            items.add(
                AdhanAudioItem(
                    name = displayName,
                    artist = artistName,
                    path = path,
                    isDefault = isDefaultFile,
                    isBuiltIn = true,
                    isSelected = isSelected,
                    isPlaying = playingPath == path
                )
            )
        }

        // Custom Adhans
        audioManager.getCustomAdhans().forEach { file ->
            val path = file.absolutePath
            val metadata = getAudioMetadata(path)
            val isCustomSelected = if (selectedPrayer != null) {
                settings.prayerSpecificAdhanPaths[selectedPrayer] == path
            } else {
                settings.selectedAdhanPath == path
            }

            items.add(
                AdhanAudioItem(
                    name = metadata.first ?: file.name,
                    artist = metadata.second,
                    path = path,
                    isBuiltIn = false,
                    isSelected = isCustomSelected,
                    isPlaying = playingPath == path
                )
            )
        }
        items
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private fun getAudioMetadata(path: String): Pair<String?, String?> {
        val retriever = MediaMetadataRetriever()
        return try {
            if (path.startsWith("android.resource")) {
                retriever.setDataSource(context, Uri.parse(path))
            } else {
                retriever.setDataSource(path)
            }
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            Pair(title, artist)
        } catch (e: Exception) {
            Pair(null, null)
        } finally {
            try { retriever.release() } catch (e: Exception) {}
        }
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
            audioPlayer.playFromPath(path)
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
                _refreshTrigger.value += 1
            }
        }
    }

    fun deleteAudio(path: String) {
        viewModelScope.launch {
            val file = File(path)
            if (file.exists()) {
                audioManager.deleteAdhan(file)
                _refreshTrigger.value += 1
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
