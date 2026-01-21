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

    fun scheduleUpcomingAlarms(prayerDays: List<PrayerDay>) {
        val now = LocalDateTime.now()
        
        // Flatten all timings from today and tomorrow to find future ones
        prayerDays
            .flatMap { day -> 
                day.timings.map { (type, time) -> 
                    Triple(type, day.date.atTime(time), day) 
                }
            }
            .filter { (_, dateTime, _) -> dateTime.isAfter(now) }
            .sortedBy { it.second }
            .take(10) // Schedule next 10 prayers (roughly 2 days)
            .forEach { (type, dateTime, _) ->
                if (type != PrayerType.SUNRISE) { // Usually we don't play Adhan for Sunrise
                    val epochMillis = dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    scheduleAlarm(epochMillis, type.name)
                }
            }
    }

    fun scheduleAlarm(timeMillis: Long, prayerName: String) {
        val intent = Intent(context, PrayerAlarmReceiver::class.java).apply {
            putExtra("PRAYER_NAME", prayerName)
            // Add a flag to identify real alarms vs test alarms if needed
            action = "com.ybugmobile.vaktiva.ACTION_PRAYER_ALARM"
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            prayerName.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        Log.d("AlarmScheduler", "Scheduling alarm for $prayerName at $timeMillis")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAlarmClock(
                    AlarmManager.AlarmClockInfo(timeMillis, pendingIntent),
                    pendingIntent
                )
            } else {
                Log.e("AlarmScheduler", "Cannot schedule exact alarms: Permission missing")
            }
        } else {
            alarmManager.setAlarmClock(
                AlarmManager.AlarmClockInfo(timeMillis, pendingIntent),
                pendingIntent
            )
        }
    }

    fun cancelAlarm(prayerName: String) {
        val intent = Intent(context, PrayerAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            prayerName.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }
}