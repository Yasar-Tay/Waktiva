package com.ybugmobile.vaktiva.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.ybugmobile.vaktiva.R
import com.ybugmobile.vaktiva.data.audio.AudioPlayer
import com.ybugmobile.vaktiva.data.local.preferences.SettingsManager
import com.ybugmobile.vaktiva.domain.model.PrayerType
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class PrayerAlarmReceiver : BroadcastReceiver() {

    @Inject
    lateinit var settingsManager: SettingsManager

    @Inject
    lateinit var audioPlayer: AudioPlayer

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    override fun onReceive(context: Context, intent: Intent) {
        val prayerName = intent.getStringExtra("PRAYER_NAME") ?: return
        val pendingResult = goAsync()

        scope.launch {
            try {
                val settings = settingsManager.settingsFlow.first()

                if (!settings.playAdhanAudio) return@launch

                val prayerType = PrayerType.fromString(prayerName)

                val audioPath = if (settings.useSpecificAdhanForEachPrayer && prayerType != null && prayerType != PrayerType.SUNRISE) {
                    // Use specific sound if available, otherwise fallback to global
                    settings.prayerSpecificAdhanPaths[prayerType] ?: settings.selectedAdhanPath
                } else {
                    // Use global sound
                    settings.selectedAdhanPath
                }

                // Fallback to default built-in adhan if no path is selected
                val finalAudioPath = audioPath ?: "android.resource://${context.packageName}/${R.raw.ezan}"

                audioPlayer.play(Uri.parse(finalAudioPath))
            } finally {
                pendingResult.finish()
            }
        }
    }
}