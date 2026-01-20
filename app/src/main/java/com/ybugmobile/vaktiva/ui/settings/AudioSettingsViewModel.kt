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
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.io.File
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
    private val audioPlayer: AudioPlayer
) : ViewModel() {

    private val _audioItems = MutableStateFlow<List<AdhanAudioItem>>(emptyList())
    val audioItems: StateFlow<List<AdhanAudioItem>> = _audioItems.asStateFlow()

    private var currentPlayingPath: String? = null

    init {
        viewModelScope.launch {
            settingsManager.settingsFlow.collect { settings ->
                loadAudioFiles(settings.selectedAdhanPath)
            }
        }
    }

    private fun loadAudioFiles(selectedPath: String?) {
        val items = mutableListOf<AdhanAudioItem>()

        // 1. Add Default Adhan
        val defaultPath = "android.resource://${context.packageName}/${R.raw.ezan}"
        items.add(
            AdhanAudioItem(
                name = "Default Adhan",
                path = defaultPath,
                isDefault = true,
                isSelected = selectedPath == defaultPath || selectedPath == null,
                isPlaying = currentPlayingPath == defaultPath
            )
        )

        // 2. Add Custom Adhans
        audioManager.getCustomAdhans().forEach { file ->
            val path = file.absolutePath
            items.add(
                AdhanAudioItem(
                    name = file.name,
                    path = path,
                    isSelected = selectedPath == path,
                    isPlaying = currentPlayingPath == path
                )
            )
        }

        _audioItems.value = items
        
        // Ensure settings are updated if nothing was selected (set default)
        if (selectedPath == null) {
            selectAudio(defaultPath)
        }
    }

    fun selectAudio(path: String) {
        viewModelScope.launch {
            settingsManager.updateSelectedAdhanPath(path)
        }
    }

    fun togglePreview(path: String) {
        if (currentPlayingPath == path) {
            audioPlayer.stop()
            currentPlayingPath = null
        } else {
            audioPlayer.stop()
            audioPlayer.play(Uri.parse(path))
            currentPlayingPath = path
        }
        loadAudioFiles(_audioItems.value.find { it.isSelected }?.path)
    }

    fun addCustomAudio(uri: Uri) {
        val mimeType = context.contentResolver.getType(uri)
        if (mimeType == null || !mimeType.startsWith("audio/")) {
            // Log or show error in a real app. For now, we just skip invalid files.
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
                // If deleted item was selected, fallback to default
                val settings = settingsManager.settingsFlow.firstOrNull()
                if (settings?.selectedAdhanPath == path) {
                    val defaultPath = "android.resource://${context.packageName}/${R.raw.ezan}"
                    selectAudio(defaultPath)
                }
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
