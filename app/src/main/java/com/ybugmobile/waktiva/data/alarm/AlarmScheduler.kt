package com.ybugmobile.waktiva.data.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.ybugmobile.waktiva.data.local.preferences.SettingsManager
import com.ybugmobile.waktiva.data.local.preferences.UserSettings
import com.ybugmobile.waktiva.data.notification.NotificationHelper
import com.ybugmobile.waktiva.domain.model.PrayerDay
import com.ybugmobile.waktiva.domain.model.PrayerLog
import com.ybugmobile.waktiva.domain.model.PrayerType
import com.ybugmobile.waktiva.receiver.PrayerAlarmReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject

/**
 * Handles the scheduling and cancellation of high-precision alarms for Adhan (Call to Prayer),
 * pre-Adhan notifications, and widget refresh milestones (like Sunrise).
 */
class AlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsManager: SettingsManager
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    companion object {
        const val ACTION_PRAYER_ALARM = "com.ybugmobile.waktiva.ACTION_PRAYER_ALARM"
        const val ACTION_PRE_ADHAN_NOTIFICATION = "com.ybugmobile.waktiva.ACTION_PRE_ADHAN_NOTIFICATION"
        const val ACTION_WIDGET_REFRESH = "com.ybugmobile.waktiva.ACTION_WIDGET_REFRESH"
        const val ACTION_PRAYER_LOG_REMINDER = "com.ybugmobile.waktiva.ACTION_PRAYER_LOG_REMINDER"
        const val ACTION_PRAYER_LOG_NUDGE = "com.ybugmobile.waktiva.ACTION_PRAYER_LOG_NUDGE"

        /** How long before a prayer's time ends the prayer log's game nudges to mark it. */
        const val PRAYER_LOG_NUDGE_MINUTES = 30L
        
        const val REQUEST_CODE_ADHAN = 1001
        const val REQUEST_CODE_PRE_ADHAN = 1002
        const val REQUEST_CODE_WIDGET_REFRESH = 1003
        const val REQUEST_CODE_WIDGET_REFRESH_BACKUP = 1004
        const val REQUEST_CODE_PRAYER_LOG_REMINDER = 1005
        const val REQUEST_CODE_PRAYER_LOG_NUDGE = 1006
        const val REQUEST_CODE_TEST = 9999
        const val REQUEST_CODE_TEST_PRE = 9998
    }

    fun scheduleTestAlarm(secondsFromNow: Int) {
        val adhanDelay = if (secondsFromNow < 5) 5 else secondsFromNow
        val adhanTriggerAt = System.currentTimeMillis() + (adhanDelay * 1000)
        val today = LocalDate.now().toString()
        
        Log.d("AlarmScheduler", "Scheduling TEST adhan in $adhanDelay seconds")
        scheduleAlarm(adhanTriggerAt, PrayerType.FAJR.name, today, ACTION_PRAYER_ALARM, REQUEST_CODE_TEST)

        runBlocking {
            val settings = settingsManager.settingsFlow.first()
            if (settings.enablePreAdhanWarning) {
                val warningSeconds = settings.preAdhanWarningMinutes * 60
                val notificationDelaySeconds = (adhanDelay - warningSeconds).coerceAtLeast(1)
                val preTriggerAt = System.currentTimeMillis() + (notificationDelaySeconds * 1000)
                Log.d("AlarmScheduler", "Scheduling TEST pre-adhan in $notificationDelaySeconds seconds")
                scheduleAlarm(preTriggerAt, PrayerType.FAJR.name, today, ACTION_PRE_ADHAN_NOTIFICATION, REQUEST_CODE_TEST_PRE)
            }
        }
    }

    fun scheduleNextAlarm(prayerDays: List<PrayerDay>, enablePreAdhan: Boolean, preAdhanMinutes: Int) {
        val now = LocalDateTime.now()
        val settings = runBlocking { settingsManager.settingsFlow.first() }

        // Clear any stale sunrise fallback refresh before we decide whether to schedule a new one.
        cancelAlarm(REQUEST_CODE_WIDGET_REFRESH_BACKUP)
        
        // 1. Identify all upcoming "milestones" (all prayer times + sunrise)
        val allMilestones = prayerDays
            .flatMap { day -> 
                day.timings.map { (type, time) -> 
                    var triggerDateTime = day.date.atTime(time)
                    
                    if (type == PrayerType.FAJR) {
                        val isRamadan = day.hijriDate?.monthNumber == 9
                        if (!isRamadan && settings.useFajrAlarmBeforeSunrise) {
                            val sunriseTime = day.timings[PrayerType.SUNRISE] ?: time
                            triggerDateTime = day.date.atTime(sunriseTime).minusMinutes(settings.fajrAlarmMinutesBeforeSunrise.toLong())
                        }
                    }

                    Triple(type, triggerDateTime, day) 
                }
            }
            .filter { (_, dateTime, _) -> dateTime.isAfter(now) }
            .sortedBy { it.second }

        // 2. Schedule the next Adhan Alarm (Skips Sunrise as it's not a prayer)
        val nextAdhan = allMilestones.find { it.first != PrayerType.SUNRISE }
        
        if (nextAdhan != null) {
            val (type, dateTime, prayerDay) = nextAdhan
            val epochMillis = dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val prayerDateStr = prayerDay.date.toString()
            
            Log.d("AlarmScheduler", "Scheduling ADHAN alarm for: ${type.name} at $dateTime")
            scheduleAlarm(epochMillis, type.name, prayerDateStr, ACTION_PRAYER_ALARM, REQUEST_CODE_ADHAN)

            if (enablePreAdhan && preAdhanMinutes > 0) {
                val preTime = dateTime.minusMinutes(preAdhanMinutes.toLong())
                if (preTime.isAfter(now)) {
                    val preEpochMillis = preTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    Log.d("AlarmScheduler", "Scheduling PRE-ADHAN alarm for: ${type.name} at $preTime")
                    scheduleAlarm(preEpochMillis, type.name, prayerDateStr, ACTION_PRE_ADHAN_NOTIFICATION, REQUEST_CODE_PRE_ADHAN)
                }
            }
        }

        schedulePrayerLogReminder(prayerDays, now, settings)
        schedulePrayerLogNudge(prayerDays, now, settings)

        // 3. Schedule the absolute next milestone (could be Sunrise) to ensure Widget updates
        val absoluteNext = allMilestones.firstOrNull()
        if (absoluteNext != null) {
            val (type, dateTime, prayerDay) = absoluteNext
            val epochMillis = dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            
            // Only schedule if this is different from the Adhan alarm or if it's Sunrise
            // This prevents "Double-waking" but ensures we wake up for Sunrise specifically.
            Log.d("AlarmScheduler", "Scheduling WIDGET REFRESH milestone: ${type.name} at $dateTime")
            scheduleAlarm(epochMillis, type.name, prayerDay.date.toString(), ACTION_WIDGET_REFRESH, REQUEST_CODE_WIDGET_REFRESH)

            if (type == PrayerType.SUNRISE) {
                val backupTime = dateTime.plusMinutes(1)
                val backupEpochMillis = backupTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                Log.d("AlarmScheduler", "Scheduling SUNRISE fallback refresh at $backupTime")
                scheduleAlarm(
                    backupEpochMillis,
                    type.name,
                    prayerDay.date.toString(),
                    ACTION_WIDGET_REFRESH,
                    REQUEST_CODE_WIDGET_REFRESH_BACKUP
                )
            }
        }
    }

    /**
     * Sets the reminder to mark the day's prayers in the prayer log, a while after the next Isha,
     * or clears it while the log or its reminder is off. The receiver decides when it fires
     * whether any prayer is still unmarked.
     */
    private fun schedulePrayerLogReminder(prayerDays: List<PrayerDay>, now: LocalDateTime, settings: UserSettings) {
        val next = if (settings.prayerLogEnabled && settings.prayerLogReminderEnabled) {
            PrayerLog.nextReminder(prayerDays, now, settings.prayerLogReminderMinutes)
        } else {
            null
        }
        val intent = reminderIntent(next?.first?.toString() ?: LocalDate.now().toString())
        if (next == null) {
            PendingIntent.getBroadcast(
                context, REQUEST_CODE_PRAYER_LOG_REMINDER, intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )?.let { alarmManager.cancel(it) }
            return
        }

        val (date, at) = next
        val pendingIntent = PendingIntent.getBroadcast(
            context, REQUEST_CODE_PRAYER_LOG_REMINDER, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        Log.d("AlarmScheduler", "Scheduling PRAYER LOG reminder for $date at $at")
        setReminderAlarm(at, pendingIntent)
    }

    /**
     * Sets the prayer log game's next nudge, [PRAYER_LOG_NUDGE_MINUTES] before a prayer's time
     * ends, or clears it while the log or its game notifications are off. The receiver decides
     * when it fires whether the prayer is still unmarked.
     */
    private fun schedulePrayerLogNudge(prayerDays: List<PrayerDay>, now: LocalDateTime, settings: UserSettings) {
        val next = if (settings.prayerLogEnabled && settings.prayerLogGameNotifications) {
            PrayerLog.nextNudge(prayerDays, now, PRAYER_LOG_NUDGE_MINUTES)
        } else {
            null
        }
        val intent = Intent(context, PrayerAlarmReceiver::class.java).apply {
            action = ACTION_PRAYER_LOG_NUDGE
            putExtra(NotificationHelper.EXTRA_PRAYER_NAME, (next?.type ?: PrayerType.DHUHR).name)
            putExtra(NotificationHelper.EXTRA_PRAYER_DATE, (next?.date ?: LocalDate.now()).toString())
            component = ComponentName(context, PrayerAlarmReceiver::class.java)
        }
        if (next == null) {
            PendingIntent.getBroadcast(
                context, REQUEST_CODE_PRAYER_LOG_NUDGE, intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )?.let { alarmManager.cancel(it) }
            return
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context, REQUEST_CODE_PRAYER_LOG_NUDGE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        Log.d("AlarmScheduler", "Scheduling PRAYER LOG nudge for ${next.type} on ${next.date} at ${next.at}")
        setReminderAlarm(next.at, pendingIntent)
    }

    /**
     * Sets a reminder for [at], exactly when allowed. Not an alarm clock: a reminder mustn't show
     * up as the phone's next alarm.
     */
    private fun setReminderAlarm(at: LocalDateTime, pendingIntent: PendingIntent) {
        val triggerAt = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            }
        } catch (e: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
    }

    private fun reminderIntent(prayerDate: String) = Intent(context, PrayerAlarmReceiver::class.java).apply {
        action = ACTION_PRAYER_LOG_REMINDER
        putExtra(NotificationHelper.EXTRA_PRAYER_NAME, PrayerType.ISHA.name)
        putExtra(NotificationHelper.EXTRA_PRAYER_DATE, prayerDate)
        component = ComponentName(context, PrayerAlarmReceiver::class.java)
    }

    private fun scheduleAlarm(timeMillis: Long, prayerName: String, prayerDate: String, action: String, requestCode: Int) {
        val intent = Intent(context, PrayerAlarmReceiver::class.java).apply {
            this.action = action
            putExtra(NotificationHelper.EXTRA_PRAYER_NAME, prayerName)
            putExtra(NotificationHelper.EXTRA_PRAYER_DATE, prayerDate)
            component = ComponentName(context, PrayerAlarmReceiver::class.java)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmClockInfo = AlarmManager.AlarmClockInfo(timeMillis, pendingIntent)
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeMillis, pendingIntent)
                }
            } else {
                alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
            }
        } catch (e: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeMillis, pendingIntent)
        }
    }

    fun cancelAllAlarms() {
        cancelAlarm(REQUEST_CODE_ADHAN)
        cancelAlarm(REQUEST_CODE_PRE_ADHAN)
        cancelAlarm(REQUEST_CODE_WIDGET_REFRESH)
        cancelAlarm(REQUEST_CODE_WIDGET_REFRESH_BACKUP)
        cancelAlarm(REQUEST_CODE_TEST)
        cancelAlarm(REQUEST_CODE_TEST_PRE)
    }

    private fun cancelAlarm(requestCode: Int) {
        val intent = Intent(context, PrayerAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
        }
    }
}
