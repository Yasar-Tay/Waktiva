package com.ybugmobile.vaktiva.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.util.Log
import com.ybugmobile.vaktiva.R
import com.ybugmobile.vaktiva.data.local.preferences.SettingsManager
import com.ybugmobile.vaktiva.domain.model.PrayerType
import com.ybugmobile.vaktiva.service.AdhanService
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

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    override fun onReceive(context: Context, intent: Intent) {
        val prayerName = intent.getStringExtra("PRAYER_NAME")
        if (prayerName == null) {
            Log.e("PrayerAlarmReceiver", "PRAYER_NAME extra is missing or key mismatch! Check your AlarmScheduler.")
            return
        }

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "Vaktiva:AdhanWakeLock"
        )
        wakeLock.acquire(10 * 60 * 1000L /*10 minutes max*/)

        val pendingResult = goAsync()
        Log.d("PrayerAlarmReceiver", "Alarm received for $prayerName. Processing...")

        scope.launch {
            try {
                val settings = settingsManager.settingsFlow.first()

                if (!settings.playAdhanAudio) {
                    Log.d("PrayerAlarmReceiver", "Adhan audio is disabled in settings.")
                    return@launch
                }

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

                Log.d("PrayerAlarmReceiver", "Starting AdhanService for $prayerName. URI: $finalAudioPath")

                val serviceIntent = Intent(context, AdhanService::class.java).apply {
                    putExtra("PRAYER_NAME", prayerName)
                    putExtra("AUDIO_PATH", finalAudioPath)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            } catch (e: Exception) {
                Log.e("PrayerAlarmReceiver", "Error starting service", e)
            } finally {
                if (wakeLock.isHeld) wakeLock.release()
                pendingResult.finish()
            }
        }
    }
}
