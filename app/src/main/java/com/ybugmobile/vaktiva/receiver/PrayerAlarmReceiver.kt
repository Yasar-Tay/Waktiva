package com.ybugmobile.vaktiva.receiver

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.ybugmobile.vaktiva.MainActivity
import com.ybugmobile.vaktiva.R
import com.ybugmobile.vaktiva.data.alarm.AlarmScheduler
import com.ybugmobile.vaktiva.data.local.preferences.SettingsManager
import com.ybugmobile.vaktiva.domain.model.PrayerType
import com.ybugmobile.vaktiva.service.AdhanService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import javax.inject.Inject

@AndroidEntryPoint
class PrayerAlarmReceiver : BroadcastReceiver() {

    @Inject
    lateinit var settingsManager: SettingsManager

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    companion object {
        const val CHANNEL_ID_WARNING = "pre_adhan_warning_channel_v1"
        const val NOTIFICATION_ID_WARNING = 2001
        const val ACTION_SKIP_ADHAN = "com.ybugmobile.vaktiva.ACTION_SKIP_ADHAN"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val prayerName = intent.getStringExtra("PRAYER_NAME") ?: return

        Log.d("PrayerAlarmReceiver", "onReceive: action=$action, prayer=$prayerName")
        
        if (action == ACTION_SKIP_ADHAN) {
            runBlocking {
                val today = LocalDate.now().toString()
                settingsManager.muteNextPrayer(prayerName, today)
                Log.d("PrayerAlarmReceiver", "SKIP SUCCESS: $prayerName muted for $today")
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(NOTIFICATION_ID_WARNING)
            return
        }

        val pendingResult = goAsync()
        scope.launch {
            try {
                when (action) {
                    AlarmScheduler.ACTION_PRE_ADHAN_NOTIFICATION -> showPreAdhanNotification(context, prayerName)
                    AlarmScheduler.ACTION_PRAYER_ALARM -> handleAdhanTrigger(context, prayerName)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleAdhanTrigger(context: Context, prayerName: String) {
        val settings = settingsManager.settingsFlow.first()
        val today = LocalDate.now().toString()

        if (settings.mutedPrayerName.equals(prayerName, ignoreCase = true) && settings.mutedPrayerDate == today) {
            Log.d("PrayerAlarmReceiver", "ADHAN SKIPPED: Logic recognized skip for $prayerName. Resetting mute for tomorrow.")
            // Reset mute after it has been used once to ensure default is unmuted
            settingsManager.clearMutedPrayer()
            return
        }

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Vaktiva:AdhanWakeLock")
        wakeLock.acquire(5 * 60 * 1000L)

        try {
            val prayerType = PrayerType.fromString(prayerName)
            val audioPath = if (settings.useSpecificAdhanForEachPrayer && prayerType != null) {
                settings.prayerSpecificAdhanPaths[prayerType] ?: settings.selectedAdhanPath
            } else settings.selectedAdhanPath

            val finalPath = audioPath ?: "android.resource://${context.packageName}/${R.raw.ezan}"
            val serviceIntent = Intent(context, AdhanService::class.java).apply {
                putExtra("PRAYER_NAME", prayerName)
                putExtra("AUDIO_PATH", finalPath)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } finally {
            if (wakeLock.isHeld) wakeLock.release()
        }
    }

    private fun showPreAdhanNotification(context: Context, prayerName: String) {
        val skipIntent = Intent(context, PrayerAlarmReceiver::class.java).apply {
            action = ACTION_SKIP_ADHAN
            putExtra("PRAYER_NAME", prayerName)
            setClassName(context.packageName, "com.ybugmobile.vaktiva.receiver.PrayerAlarmReceiver")
        }
        val skipPendingIntent = PendingIntent.getBroadcast(
            context, prayerName.hashCode(), skipIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentIntent = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_WARNING)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Adhan Approaching")
            .setContentText("It will be $prayerName soon.")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(contentIntent, true) // Banner / Pop-up
            .addAction(R.drawable.ic_launcher_foreground, "SKIP ADHAN", skipPendingIntent)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID_WARNING, notification)
    }
}
