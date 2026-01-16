package com.ybugmobile.vaktiva.ui.settings

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ybugmobile.vaktiva.data.audio.AdhanAudioManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class AudioSettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val audioManager: AdhanAudioManager
) : ViewModel() {

    private val _audioFiles = MutableStateFlow<List<File>>(emptyList())
    val audioFiles: StateFlow<List<File>> = _audioFiles

    init {
        loadAudioFiles()
    }

    private fun loadAudioFiles() {
        _audioFiles.value = audioManager.getCustomAdhans()
    }

    fun addCustomAudio(uri: Uri) {
        viewModelScope.launch {
            val fileName = getFileName(uri) ?: "custom_adhan_${System.currentTimeMillis()}.mp3"
            audioManager.saveCustomAdhan(uri, fileName)
            loadAudioFiles()
        }
    }

    fun deleteAudio(file: File) {
        viewModelScope.launch {
            audioManager.deleteAdhan(file)
            loadAudioFiles()
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
}
