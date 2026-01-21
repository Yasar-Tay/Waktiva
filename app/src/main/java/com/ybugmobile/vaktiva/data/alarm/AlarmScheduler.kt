package com.ybugmobile.vaktiva.data.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.ybugmobile.vaktiva.domain.model.PrayerDay
import com.ybugmobile.vaktiva.domain.model.PrayerType
import com.ybugmobile.vaktiva.receiver.PrayerAlarmReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject

class AlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /**
     * Finds and schedules only the NEXT single prayer alarm.
     * Chained scheduling is more resilient to system restrictions than scheduling many at once.
     */
    fun scheduleNextAlarm(prayerDays: List<PrayerDay>) {
        val now = LocalDateTime.now()
        
        val nextPrayer = prayerDays
            .flatMap { day -> 
                day.timings.map { (type, time) -> 
                    Triple(type, day.date.atTime(time), day) 
                }
            }
            .filter { (type, dateTime, _) -> 
                dateTime.isAfter(now) && type != PrayerType.SUNRISE 
            }
            .minByOrNull { it.second }

        if (nextPrayer != null) {
            val (type, dateTime, _) = nextPrayer
            val epochMillis = dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            
            Log.d("AlarmScheduler", "Scheduling NEXT alarm: ${type.name} at $dateTime ($epochMillis)")
            scheduleAlarm(epochMillis, type.name)
        } else {
            Log.w("AlarmScheduler", "No upcoming prayers found to schedule!")
        }
    }

    private fun scheduleAlarm(timeMillis: Long, prayerName: String) {
        val intent = Intent(context, PrayerAlarmReceiver::class.java).apply {
            putExtra("PRAYER_NAME", prayerName)
            action = "com.ybugmobile.vaktiva.ACTION_PRAYER_ALARM"
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            1001, // Use a constant ID for the single next alarm
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAlarmClock(
                    AlarmManager.AlarmClockInfo(timeMillis, pendingIntent),
                    pendingIntent
                )
            } else {
                Log.e("AlarmScheduler", "Cannot schedule exact alarms: Permission missing. Using inexact fallback.")
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeMillis, pendingIntent)
            }
        } else {
            alarmManager.setAlarmClock(
                AlarmManager.AlarmClockInfo(timeMillis, pendingIntent),
                pendingIntent
            )
        }
    }

    fun cancelAllAlarms() {
        val intent = Intent(context, PrayerAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            1001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }
}
