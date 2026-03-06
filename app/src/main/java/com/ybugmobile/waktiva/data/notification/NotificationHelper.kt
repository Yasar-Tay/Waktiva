package com.ybugmobile.waktiva.data.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.ybugmobile.waktiva.MainActivity
import com.ybugmobile.waktiva.R
import com.ybugmobile.waktiva.domain.model.NextPrayer
import com.ybugmobile.waktiva.domain.model.PrayerType
import com.ybugmobile.waktiva.receiver.PrayerAlarmReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.US)

    companion object {
        const val CHANNEL_ID_ADHAN = "adhan_playback_channel"
        const val CHANNEL_ID_WARNING = "pre_adhan_warning_channel_v1"
        const val CHANNEL_ID_PERSISTENT = "persistent_countdown_channel"
        
        const val NOTIFICATION_ID_ADHAN = 1001
        const val NOTIFICATION_ID_WARNING = 2001
        const val NOTIFICATION_ID_PERSISTENT = 3001
        
        const val ACTION_SKIP_ADHAN = "com.ybugmobile.waktiva.ACTION_SKIP_ADHAN"
        const val EXTRA_PRAYER_NAME = "PRAYER_NAME"
        const val EXTRA_PRAYER_DATE = "PRAYER_DATE"
    }

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val adhanChannel = NotificationChannel(
                CHANNEL_ID_ADHAN,
                context.getString(R.string.adhan_playing),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.adhan_sounding)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setSound(null, null) 
                enableVibration(true)
            }

            val warningChannel = NotificationChannel(
                CHANNEL_ID_WARNING,
                context.getString(R.string.pre_adhan_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.pre_adhan_channel_description)
                setShowBadge(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            // Using DEFAULT importance but ensuring it's silent so it shows on Lock Screen reliably
            val persistentChannel = NotificationChannel(
                CHANNEL_ID_PERSISTENT,
                context.getString(R.string.settings_persistent_notification),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.settings_persistent_notification_desc)
                setShowBadge(false)
                setSound(null, null)
                enableLights(false)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            notificationManager.createNotificationChannels(listOf(adhanChannel, warningChannel, persistentChannel))
        }
    }

    fun showPersistentNotification(nextPrayer: NextPrayer) {
        val prayerType = nextPrayer.type
        val prayerName = prayerType.getDisplayName(context)
        val prayerTime = nextPrayer.time.format(timeFormatter)
        
        val remainingMillis = nextPrayer.remainingDuration.toMillis()
        val baseTime = SystemClock.elapsedRealtime() + remainingMillis

        val remoteViews = RemoteViews(context.packageName, R.layout.notification_persistent).apply {
            setTextViewText(R.id.notif_prayer_name, prayerName.uppercase())
            setTextViewText(R.id.notif_prayer_time, prayerTime)
            
            val hours = nextPrayer.remainingDuration.toHours()
            val format = when {
                hours == 0L -> "00:%s"
                hours < 10L -> "0%s"
                else -> "%s"
            }
            setChronometer(R.id.notif_chronometer, baseTime, format, true)
            setChronometerCountDown(R.id.notif_chronometer, true)
        }

        val contentIntent = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java), 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_PERSISTENT)
            .setSmallIcon(R.drawable.ic_notification)
            .setCustomContentView(remoteViews)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        notificationManager.notify(NOTIFICATION_ID_PERSISTENT, notification)
    }

    fun hidePersistentNotification() {
        notificationManager.cancel(NOTIFICATION_ID_PERSISTENT)
    }

    fun showPreAdhanWarning(prayerName: String, prayerDate: String, minutes: Int, isMuted: Boolean = false) {
        val prayerType = PrayerType.fromString(prayerName)
        val displayedPrayerName = prayerType?.getDisplayName(context) ?: prayerName

        val contentIntent = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java), 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_WARNING)
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        if (isMuted) {
            builder.setContentTitle(context.getString(R.string.notification_adhan_muted))
                .setContentText(context.getString(R.string.notification_adhan_skipped_text, displayedPrayerName))
                .setSmallIcon(R.drawable.ic_notification)
        } else {
            val skipIntent = Intent(context, PrayerAlarmReceiver::class.java).apply {
                action = ACTION_SKIP_ADHAN
                putExtra(EXTRA_PRAYER_NAME, prayerName)
                putExtra(EXTRA_PRAYER_DATE, prayerDate)
            }
            val skipPendingIntent = PendingIntent.getBroadcast(
                context, prayerName.hashCode() + prayerDate.hashCode(), skipIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            builder.setContentTitle(context.getString(R.string.notification_upcoming_adhan_title, displayedPrayerName))
                .setContentText(context.getString(R.string.notification_upcoming_adhan_text, minutes))
                .addAction(R.drawable.ic_notification, context.getString(R.string.notification_skip_action), skipPendingIntent)
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
