package com.ybugmobile.waktiva.data.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.ybugmobile.waktiva.MainActivity
import com.ybugmobile.waktiva.R
import com.ybugmobile.waktiva.domain.model.PrayerType
import com.ybugmobile.waktiva.receiver.AdhanStopReceiver
import com.ybugmobile.waktiva.receiver.PrayerAlarmReceiver
import com.ybugmobile.waktiva.ui.adhan.AdhanActivity
import dagger.hilt.android.qualifiers.ApplicationContext
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
        const val CHANNEL_ID_PRAYER_TIME = "prayer_time_notification_channel"
        const val CHANNEL_ID_PRAYER_LOG = "prayer_log_reminder_channel"

        const val NOTIFICATION_ID_ADHAN = 1001
        const val NOTIFICATION_ID_WARNING = 2001
        const val NOTIFICATION_ID_MISSED = 3001
        const val NOTIFICATION_ID_PRAYER_TIME = 4001
        const val NOTIFICATION_ID_PRAYER_LOG = 5001

        const val ACTION_SKIP_ADHAN = "com.ybugmobile.waktiva.ACTION_SKIP_ADHAN"
        const val ACTION_STOP_ADHAN = "com.ybugmobile.waktiva.ACTION_STOP_ADHAN"
        const val ACTION_MARK_ALL_PRAYED = "com.ybugmobile.waktiva.ACTION_MARK_ALL_PRAYED"

        /** Asks MainActivity to open a screen; see [SCREEN_PRAYER_LOG]. */
        const val EXTRA_OPEN_SCREEN = "OPEN_SCREEN"
        const val SCREEN_PRAYER_LOG = "prayer_log"
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

            val prayerTimeChannel = NotificationChannel(
                CHANNEL_ID_PRAYER_TIME,
                context.getString(R.string.silent_prayer_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.silent_prayer_channel_description)
                setSound(null, null)
                enableVibration(false)
                setShowBadge(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            val prayerLogChannel = NotificationChannel(
                CHANNEL_ID_PRAYER_LOG,
                context.getString(R.string.prayer_log_reminder_channel),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.prayer_log_reminder_channel_desc)
                setShowBadge(true)
            }

            notificationManager.createNotificationChannels(
                listOf(adhanChannel, warningChannel, prayerTimeChannel, prayerLogChannel)
            )
        }
    }

    fun showPreAdhanWarning(prayerName: String, prayerDate: String, minutes: Int, isMuted: Boolean = false) {
        val prayerType = PrayerType.fromString(prayerName)
        val displayedPrayerName = prayerType?.getPrayerName(context) ?: prayerName

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

    /**
     * Builds the notification used by AdhanWorker's setForeground() call.
     * The stop action broadcasts to AdhanStopReceiver which cancels the WorkManager job.
     */
    fun createAdhanNotification(prayerName: String): Notification {
        val prayerType = PrayerType.fromString(prayerName)
        val displayedPrayerName = prayerType?.getPrayerName(context) ?: prayerName

        val fullScreenIntent = Intent(context, AdhanActivity::class.java).apply {
            putExtra(EXTRA_PRAYER_NAME, prayerName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            context, 0, fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(context, AdhanStopReceiver::class.java).apply {
            action = ACTION_STOP_ADHAN
        }
        val stopPendingIntent = PendingIntent.getBroadcast(
            context, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ID_ADHAN)
            .setContentTitle(context.getString(R.string.notification_adhan_title, displayedPrayerName))
            .setContentText(context.getString(R.string.notification_adhan_content))
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, context.getString(R.string.adhan_stop), stopPendingIntent)
            .build()
    }

    fun showSilentPrayerTimeNotification(prayerName: String) {
        val prayerType = PrayerType.fromString(prayerName)
        val displayedPrayerName = prayerType?.getPrayerName(context) ?: prayerName

        val contentIntent = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_PRAYER_TIME)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_prayer_time_title, displayedPrayerName))
            .setContentText(context.getString(R.string.notification_adhan_content))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        notificationManager.notify(NOTIFICATION_ID_PRAYER_TIME, notification)
    }

    /**
     * Reminds the user to mark [prayerDate]'s prayers in the prayer log, naming those still
     * [unmarked]. Tapping it opens the log; its action marks them all as prayed.
     */
    fun showPrayerLogReminder(prayerDate: String, unmarked: List<PrayerType>) {
        val openLog = Intent(context, MainActivity::class.java).apply {
            putExtra(EXTRA_OPEN_SCREEN, SCREEN_PRAYER_LOG)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        // Its own request code: sharing the others' would hand them this intent's extra.
        val contentIntent = PendingIntent.getActivity(
            context, NOTIFICATION_ID_PRAYER_LOG, openLog,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val markAll = Intent(context, PrayerAlarmReceiver::class.java).apply {
            action = ACTION_MARK_ALL_PRAYED
            putExtra(EXTRA_PRAYER_NAME, PrayerType.ISHA.name)
            putExtra(EXTRA_PRAYER_DATE, prayerDate)
        }
        val markAllIntent = PendingIntent.getBroadcast(
            context, NOTIFICATION_ID_PRAYER_LOG, markAll,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val names = unmarked.joinToString(", ") { it.getPrayerName(context) }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_PRAYER_LOG)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.prayer_log_reminder_title))
            .setContentText(context.getString(R.string.prayer_log_reminder_text, names))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(contentIntent)
            .addAction(R.drawable.ic_notification, context.getString(R.string.prayer_log_reminder_mark_all), markAllIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID_PRAYER_LOG, notification)
    }

    fun cancelPrayerLogReminder() {
        notificationManager.cancel(NOTIFICATION_ID_PRAYER_LOG)
    }

    fun showMissedAdhanNotification(prayerName: String) {
        val prayerType = PrayerType.fromString(prayerName)
        val displayedPrayerName = prayerType?.getPrayerName(context) ?: prayerName

        val contentIntent = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_WARNING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_adhan_title, displayedPrayerName))
            .setContentText(context.getString(R.string.notification_adhan_content))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        notificationManager.notify(NOTIFICATION_ID_MISSED, notification)
    }
}
