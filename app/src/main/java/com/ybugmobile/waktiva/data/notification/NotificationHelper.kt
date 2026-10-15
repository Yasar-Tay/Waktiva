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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardDoubleArrowUp
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.ui.graphics.vector.ImageVector
import com.ybugmobile.waktiva.domain.model.LoggedPrayers
import com.ybugmobile.waktiva.domain.model.PrayerLogCelebration
import com.ybugmobile.waktiva.domain.model.PrayerLogProgress
import com.ybugmobile.waktiva.domain.model.PrayerType
import com.ybugmobile.waktiva.receiver.AdhanStopReceiver
import com.ybugmobile.waktiva.receiver.PrayerAlarmReceiver
import com.ybugmobile.waktiva.ui.adhan.AdhanActivity
import com.ybugmobile.waktiva.ui.prayerlog.descRes
import com.ybugmobile.waktiva.ui.prayerlog.icon
import com.ybugmobile.waktiva.ui.prayerlog.nameRes
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
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
        /** The prayer log game's nudges before a prayer's time ends: with the phone's sound. */
        const val CHANNEL_ID_PRAYER_LOG_GAME = "prayer_log_game_channel"

        /** The prayer log game's cheers, which follow the user's own tap: silent. */
        const val CHANNEL_ID_PRAYER_LOG_CELEBRATION = "prayer_log_celebration_channel"

        const val NOTIFICATION_ID_ADHAN = 1001
        const val NOTIFICATION_ID_WARNING = 2001
        const val NOTIFICATION_ID_PRAYER_TIME = 4001
        const val NOTIFICATION_ID_PRAYER_LOG = 5001
        const val NOTIFICATION_ID_PRAYER_LOG_NUDGE = 5002
        const val NOTIFICATION_ID_PRAYER_LOG_CELEBRATION = 5003

        const val ACTION_SKIP_ADHAN = "com.ybugmobile.waktiva.ACTION_SKIP_ADHAN"
        const val ACTION_STOP_ADHAN = "com.ybugmobile.waktiva.ACTION_STOP_ADHAN"
        const val ACTION_MARK_ALL_PRAYED = "com.ybugmobile.waktiva.ACTION_MARK_ALL_PRAYED"
        const val ACTION_MARK_PRAYED = "com.ybugmobile.waktiva.ACTION_MARK_PRAYED"

        /** Asks MainActivity to open a screen; see [SCREEN_PRAYER_LOG]. */
        const val EXTRA_OPEN_SCREEN = "OPEN_SCREEN"
        const val SCREEN_PRAYER_LOG = "prayer_log"
        const val EXTRA_PRAYER_NAME = "PRAYER_NAME"
        const val EXTRA_PRAYER_DATE = "PRAYER_DATE"

        /** When the nudged prayer's time began and ends, as ISO local date-times. */
        const val EXTRA_STARTS_AT = "STARTS_AT"
        const val EXTRA_ENDS_AT = "ENDS_AT"
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

            val prayerLogGameChannel = NotificationChannel(
                CHANNEL_ID_PRAYER_LOG_GAME,
                context.getString(R.string.prayer_log_game_channel),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.prayer_log_game_channel_desc)
                setShowBadge(true)
            }

            val prayerLogCelebrationChannel = NotificationChannel(
                CHANNEL_ID_PRAYER_LOG_CELEBRATION,
                context.getString(R.string.prayer_log_celebration_channel),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.prayer_log_celebration_channel_desc)
                setSound(null, null)
                enableVibration(false)
                setShowBadge(true)
            }

            notificationManager.createNotificationChannels(
                listOf(
                    adhanChannel, warningChannel, prayerTimeChannel, prayerLogChannel,
                    prayerLogGameChannel, prayerLogCelebrationChannel
                )
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
     * [unmarked]. Tapping it opens the log; its action marks them all as prayed. With [streak]
     * (the game's notifications on), it speaks of the streak at stake, or the XP left to earn.
     */
    fun showPrayerLogReminder(prayerDate: String, unmarked: List<PrayerType>, streak: Int? = null) {
        val contentIntent = openPrayerLogIntent(NOTIFICATION_ID_PRAYER_LOG)
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

        val title = when {
            streak == null -> context.getString(R.string.prayer_log_reminder_title)
            streak > 0 -> context.getString(R.string.prayer_log_game_risk_title, streak)
            // What the rest of the day is worth: its prayers and the full day's bonus.
            else -> context.getString(
                R.string.prayer_log_game_finish_title,
                unmarked.size * PrayerLogProgress.XP_PER_PRAYER + PrayerLogProgress.FULL_DAY_BONUS
            )
        }

        val text = context.getString(R.string.prayer_log_reminder_text, names)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_PRAYER_LOG)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(contentIntent)
            .addAction(R.drawable.ic_notification, context.getString(R.string.prayer_log_reminder_mark_all), markAllIntent)
            .setAutoCancel(true)
        if (streak != null) {
            // With the game on, the character sheet: the streak at stake by its flame, and the
            // day's five prayers, those left in dashes.
            val sheetTitle = if (streak > 0) context.getString(R.string.prayer_log_notify_streak_risk, streak) else title
            val xpLeft = unmarked.size * PrayerLogProgress.XP_PER_PRAYER + PrayerLogProgress.FULL_DAY_BONUS
            builder
                .setSmallIcon(R.drawable.ic_notification_cetele)
                .setColor(PrayerLogNotificationViews.GOLD)
                .setStyle(NotificationCompat.DecoratedCustomViewStyle())
                .setCustomContentView(
                    PrayerLogNotificationViews.collapsed(
                        context,
                        label = null,
                        title = sheetTitle,
                        reward = context.getString(R.string.prayer_log_xp_gain, xpLeft),
                        day = (LoggedPrayers - unmarked.toSet()).toSet()
                    )
                )
                .setCustomBigContentView(PrayerLogNotificationViews.reminder(context, sheetTitle, text, unmarked))
        }

        notificationManager.notify(NOTIFICATION_ID_PRAYER_LOG, builder.build())
    }

    fun cancelPrayerLogReminder() {
        notificationManager.cancel(NOTIFICATION_ID_PRAYER_LOG)
    }

    /**
     * A nudge before [type]'s time on [date] ends while it's unmarked, on the prayer log's
     * character sheet: its XP to earn, the [streak] to keep, the [level] beside the app's name,
     * and, with [startsAt] and [endsAt], how much of its time is left, counting down; collapsed,
     * the day's prayers [prayedToday] as diamonds. Its action marks it; it goes when its time does.
     */
    fun showPrayerLogNudge(
        date: LocalDate,
        type: PrayerType,
        streak: Int,
        level: Int,
        prayedToday: Set<PrayerType> = emptySet(),
        startsAt: LocalDateTime? = null,
        endsAt: LocalDateTime? = null
    ) {
        val markIntent = PendingIntent.getBroadcast(
            context,
            NOTIFICATION_ID_PRAYER_LOG_NUDGE,
            Intent(context, PrayerAlarmReceiver::class.java).apply {
                action = ACTION_MARK_PRAYED
                putExtra(EXTRA_PRAYER_NAME, type.name)
                putExtra(EXTRA_PRAYER_DATE, date.toString())
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val xp = PrayerLogProgress.XP_PER_PRAYER
        val text = if (streak > 0) {
            context.getString(R.string.prayer_log_nudge_text_streak, xp, streak)
        } else {
            context.getString(R.string.prayer_log_nudge_text, xp)
        }

        val name = type.getPrayerName(context)
        val questEnding = context.getString(R.string.prayer_log_notify_quest_ending)
        val millisLeft = endsAt?.let { Duration.between(LocalDateTime.now(), it).toMillis() }?.takeIf { it > 0 }
        val leftFraction = if (millisLeft != null && startsAt != null && endsAt != null) {
            millisLeft.toFloat() / Duration.between(startsAt, endsAt).toMillis().coerceAtLeast(1)
        } else {
            null
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_PRAYER_LOG_GAME)
            .setSmallIcon(R.drawable.ic_notification_cetele)
            .setColor(PrayerLogNotificationViews.GOLD)
            .setSubText(context.getString(R.string.prayer_log_level, level))
            .setContentTitle(context.getString(R.string.prayer_log_nudge_title, name))
            .setContentText(text)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(
                PrayerLogNotificationViews.collapsed(
                    context,
                    label = questEnding,
                    title = name,
                    reward = context.getString(R.string.prayer_log_xp_gain, xp),
                    day = prayedToday,
                    active = type,
                    millisLeft = millisLeft
                )
            )
            .setCustomBigContentView(
                PrayerLogNotificationViews.nudge(
                    context,
                    type = type,
                    eyebrow = questEnding.uppercase(locale()),
                    name = name,
                    text = text,
                    millisLeft = millisLeft,
                    leftFraction = leftFraction
                )
            )
            .apply { if (millisLeft != null) setTimeoutAfter(millisLeft) }
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(openPrayerLogIntent(NOTIFICATION_ID_PRAYER_LOG_NUDGE))
            .addAction(R.drawable.ic_notification, context.getString(R.string.prayer_log_nudge_mark, xp), markIntent)
            // Before Android 8 there are no channels to give it its sound.
            .setDefaults(NotificationCompat.DEFAULT_SOUND)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID_PRAYER_LOG_NUDGE, notification)
    }

    fun cancelPrayerLogNudge() {
        notificationManager.cancel(NOTIFICATION_ID_PRAYER_LOG_NUDGE)
    }

    /**
     * Cheers for what a mark reached: the badges first, then a level, then the day made full,
     * each a line, and the streak; the first line is the title.
     */
    fun showPrayerLogCelebration(celebration: PrayerLogCelebration) {
        val lines = buildList {
            celebration.badges.forEach { badge ->
                add(context.getString(R.string.prayer_log_celebrate_badge, context.getString(badge.nameRes)))
                add(context.getString(badge.descRes))
            }
            celebration.level?.let { add(context.getString(R.string.prayer_log_celebrate_level, it)) }
            if (celebration.fullDay) {
                add(context.getString(R.string.prayer_log_celebrate_full_day, PrayerLogProgress.FULL_DAY_BONUS))
            }
            if (celebration.streak > 0) add(context.getString(R.string.prayer_log_celebrate_streak, celebration.streak))
        }
        val rest = lines.drop(1)
        val sheet = celebrationSheet(celebration)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_PRAYER_LOG_CELEBRATION)
            .setSmallIcon(R.drawable.ic_notification_cetele)
            .setColor(PrayerLogNotificationViews.GOLD)
            .setContentTitle(lines.first())
            .setContentText(rest.firstOrNull())
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(
                PrayerLogNotificationViews.collapsed(context, label = sheet.eyebrow, title = sheet.title, reward = sheet.chip)
            )
            .setCustomBigContentView(
                PrayerLogNotificationViews.celebration(
                    context,
                    medal = PrayerLogNotificationViews.medal(context, sheet.icon),
                    eyebrow = sheet.eyebrow.uppercase(locale()),
                    title = sheet.title,
                    text = sheet.text,
                    chip = sheet.chip
                )
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setSilent(true)
            .setContentIntent(openPrayerLogIntent(NOTIFICATION_ID_PRAYER_LOG_CELEBRATION))
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID_PRAYER_LOG_CELEBRATION, notification)
    }

    /** A cheer as the sheet shows it: its medal's [icon], an [eyebrow] over its [title], and the rest. */
    private data class CelebrationSheet(
        val icon: ImageVector,
        val eyebrow: String,
        val title: String,
        val text: String?,
        val chip: String?
    )

    /**
     * What a cheer's sheet says: the first badge won, with its medal, and what else came with it
     * on the tag; else the level reached; else the full day, with the streak.
     */
    private fun celebrationSheet(celebration: PrayerLogCelebration): CelebrationSheet {
        val streak = celebration.streak.takeIf { it > 0 }
            ?.let { "$it ${context.getString(R.string.prayer_log_streak)}" }
        val fullDay = context.getString(R.string.prayer_log_notify_full_day, PrayerLogProgress.FULL_DAY_BONUS)
            .takeIf { celebration.fullDay }
        val badge = celebration.badges.firstOrNull()
        val level = celebration.level
        return when {
            badge != null -> CelebrationSheet(
                icon = badge.icon,
                eyebrow = context.getString(R.string.prayer_log_badge_unlocked),
                title = context.getString(badge.nameRes),
                text = context.getString(badge.descRes),
                chip = listOfNotNull(level?.let { context.getString(R.string.prayer_log_level, it) }, fullDay)
                    .plus(celebration.badges.drop(1).map { context.getString(it.nameRes) })
                    .joinToString(" · ")
                    .ifEmpty { null }
            )
            level != null -> CelebrationSheet(
                icon = Icons.Rounded.KeyboardDoubleArrowUp,
                eyebrow = context.getString(R.string.prayer_log_level_up),
                title = context.getString(R.string.prayer_log_level, level),
                text = streak,
                chip = fullDay
            )
            else -> CelebrationSheet(
                icon = Icons.Rounded.Verified,
                eyebrow = context.getString(R.string.prayer_log_chest),
                title = context.getString(R.string.prayer_log_chest_open),
                text = streak,
                chip = fullDay
            )
        }
    }

    private fun locale(): Locale = context.resources.configuration.locales[0]

    /**
     * Opens the prayer log. [requestCode] is the notification's own: sharing another's would
     * hand that one this intent's extra.
     */
    private fun openPrayerLogIntent(requestCode: Int): PendingIntent {
        val openLog = Intent(context, MainActivity::class.java).apply {
            putExtra(EXTRA_OPEN_SCREEN, SCREEN_PRAYER_LOG)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        return PendingIntent.getActivity(
            context, requestCode, openLog,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
