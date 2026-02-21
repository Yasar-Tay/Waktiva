package com.ybugmobile.vaktiva.data.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.ybugmobile.vaktiva.data.local.preferences.SettingsManager
import com.ybugmobile.vaktiva.data.notification.NotificationHelper
import com.ybugmobile.vaktiva.domain.model.PrayerDay
import com.ybugmobile.vaktiva.domain.model.PrayerType
import com.ybugmobile.vaktiva.receiver.PrayerAlarmReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject

/**
 * Handles the scheduling and cancellation of high-precision alarms for Adhan (Call to Prayer)
 * and pre-Adhan notifications.
 */
class AlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsManager: SettingsManager
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    companion object {
        const val ACTION_PRAYER_ALARM = "com.ybugmobile.vaktiva.ACTION_PRAYER_ALARM"
        const val ACTION_PRE_ADHAN_NOTIFICATION = "com.ybugmobile.vaktiva.ACTION_PRE_ADHAN_NOTIFICATION"
        
        const val REQUEST_CODE_ADHAN = 1001
        const val REQUEST_CODE_PRE_ADHAN = 1002
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
        
        val nextPrayer = prayerDays
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
            .filter { (type, dateTime, _) -> 
                dateTime.isAfter(now) && type != PrayerType.SUNRISE 
            }
            .minByOrNull { it.second }

        if (nextPrayer != null) {
            val (type, dateTime, prayerDay) = nextPrayer
            val epochMillis = dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val prayerDateStr = prayerDay.date.toString()
            
            Log.d("AlarmScheduler", "Scheduling ADHAN alarm for: ${type.name} at $dateTime")
            scheduleAlarm(epochMillis, type.name, prayerDateStr, ACTION_PRAYER_ALARM, REQUEST_CODE_ADHAN)

            if (enablePreAdhan && preAdhanMinutes > 0) {
                val preTime = dateTime.minusMinutes(preAdhanMinutes.toLong())
                
                // FIX: Only schedule pre-adhan if it's in the future.
                // If it's already past the warning time but before the prayer, 
                // we don't schedule it here to avoid duplicate triggers during refreshes.
                if (preTime.isAfter(now)) {
                    val preEpochMillis = preTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    Log.d("AlarmScheduler", "Scheduling PRE-ADHAN alarm for: ${type.name} at $preTime")
                    scheduleAlarm(preEpochMillis, type.name, prayerDateStr, ACTION_PRE_ADHAN_NOTIFICATION, REQUEST_CODE_PRE_ADHAN)
                } else {
                    Log.d("AlarmScheduler", "Already inside warning window, skipping PRE-ADHAN to avoid duplicates")
                }
            }
        }
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
