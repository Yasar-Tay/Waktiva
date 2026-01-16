package com.ybugmobile.vaktiva.data.audio

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdhanAudioManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val audioDir = File(context.filesDir, "audio").apply {
        if (!exists()) mkdirs()
    }

    fun saveCustomAdhan(uri: Uri, fileName: String): String? {
        return try {
            val destinationFile = File(audioDir, fileName)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destinationFile).use { output ->
                    input.copyTo(output)
                }
            }
            destinationFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun getCustomAdhans(): List<File> {
        return audioDir.listFiles()?.toList() ?: emptyList()
    }

    fun deleteAdhan(file: File): Boolean {
        return if (file.exists()) {
            file.delete()
        } else false
    }
}
