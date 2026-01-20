package com.ybugmobile.vaktiva.data.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
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
        const val CHANNEL_ID = "adhan_channel"
        const val WARNING_CHANNEL_ID = "warning_channel"
        const val NOTIFICATION_ID = 1001
        const val WARNING_NOTIFICATION_ID = 1002
        
        const val ACTION_SKIP_PRAYER = "com.ybugmobile.vaktiva.ACTION_SKIP_PRAYER"
        const val EXTRA_PRAYER_NAME = "PRAYER_NAME"
    }

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val adhanChannel = NotificationChannel(
                CHANNEL_ID,
                "Adhan Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for prayer times"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setSound(null, null) // Audio is handled by AdhanService
                enableVibration(true)
                setShowBadge(true)
            }

            val warningChannel = NotificationChannel(
                WARNING_CHANNEL_ID,
                "Prayer Warnings",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for pre-adhan warnings"
            }

            notificationManager.createNotificationChannels(listOf(adhanChannel, warningChannel))
        }
    }

    fun showAdhanNotification(prayerName: String) {
        val intent = Intent(context, AdhanActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("PRAYER_NAME", prayerName)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val fullScreenIntent = PendingIntent.getActivity(
            context,
            1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("It's time for $prayerName")
            .setContentText("Tap to open Vaktiva")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setFullScreenIntent(fullScreenIntent, true)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    fun showPreAdhanWarning(prayerName: String) {
        val skipIntent = Intent(context, PrayerAlarmReceiver::class.java).apply {
            action = ACTION_SKIP_PRAYER
            putExtra(EXTRA_PRAYER_NAME, prayerName)
        }
        
        val skipPendingIntent = PendingIntent.getBroadcast(
            context,
            prayerName.hashCode(),
            skipIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, WARNING_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Upcoming: $prayerName")
            .setContentText("$prayerName will start in a few minutes.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Skip Audio",
                skipPendingIntent
            )
            .build()

        notificationManager.notify(WARNING_NOTIFICATION_ID, notification)
    }

    fun cancelNotification(id: Int = NOTIFICATION_ID) {
        notificationManager.cancel(id)
    }
}
