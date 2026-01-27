package com.ybugmobile.vaktiva.data.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.ybugmobile.vaktiva.MainActivity
import com.ybugmobile.vaktiva.R
import com.ybugmobile.vaktiva.receiver.PrayerAlarmReceiver
import com.ybugmobile.vaktiva.ui.adhan.AdhanActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        const val CHANNEL_ID_ADHAN = "adhan_playback_channel"
        const val CHANNEL_ID_WARNING = "pre_adhan_warning_channel_v1"
        
        const val NOTIFICATION_ID_ADHAN = 1001
        const val NOTIFICATION_ID_WARNING = 2001
        
        const val ACTION_SKIP_ADHAN = "com.ybugmobile.vaktiva.ACTION_SKIP_ADHAN"
        const val EXTRA_PRAYER_NAME = "PRAYER_NAME"
    }

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val adhanChannel = NotificationChannel(
                CHANNEL_ID_ADHAN,
                "Adhan Playback",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notification for active Adhan playback"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setSound(null, null) 
                enableVibration(true)
            }

            val warningChannel = NotificationChannel(
                CHANNEL_ID_WARNING,
                "Prayer Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for upcoming prayers"
                setShowBadge(true)
            }

            notificationManager.createNotificationChannels(listOf(adhanChannel, warningChannel))
        }
    }

    fun showPreAdhanWarning(prayerName: String, minutes: Int, isMuted: Boolean = false) {
        val contentIntent = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java), 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_WARNING)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        if (isMuted) {
            builder.setContentTitle("Adhan Muted")
                .setContentText("Adhan for $prayerName is skipped.")
                .setSmallIcon(R.drawable.ic_launcher_foreground) // Use a different icon if available
        } else {
            val skipIntent = Intent(context, PrayerAlarmReceiver::class.java).apply {
                action = ACTION_SKIP_ADHAN
                putExtra(EXTRA_PRAYER_NAME, prayerName)
            }
            val skipPendingIntent = PendingIntent.getBroadcast(
                context, prayerName.hashCode(), skipIntent, 
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            builder.setContentTitle("Upcoming Adhan: $prayerName")
                .setContentText("Adhan will be played in $minutes minutes.")
                .addAction(R.drawable.ic_launcher_foreground, "SKIP FOR THIS PRAYER", skipPendingIntent)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
        }

        notificationManager.notify(NOTIFICATION_ID_WARNING, builder.build())
    }

    fun cancelWarningNotification() {
        notificationManager.cancel(NOTIFICATION_ID_WARNING)
    }

    fun cancelAdhanNotification() {
        notificationManager.cancel(NOTIFICATION_ID_ADHAN)
    }
}
