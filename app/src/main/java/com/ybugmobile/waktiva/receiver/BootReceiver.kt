package com.ybugmobile.waktiva.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.ybugmobile.waktiva.data.worker.PrayerUpdateWorker
import dagger.hilt.android.AndroidEntryPoint

/**
 * A [BroadcastReceiver] that listens for the [Intent.ACTION_BOOT_COMPLETED] system broadcast.
 *
 * Its primary responsibility is to ensure that prayer alarms are rescheduled immediately after
 * the device is powered on or restarted. Since the Android [AlarmManager] clears all alarms on
 * reboot, this receiver is critical for the app's reliability.
 *
 * Instead of performing the scheduling logic directly in the receiver (which has a short timeout),
 * it delegates the work to [PrayerUpdateWorker] via [WorkManager].
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Trigger a prayer update work to reschedule all alarms safely in the background
            val workRequest = OneTimeWorkRequestBuilder<PrayerUpdateWorker>().build()
            WorkManager.getInstance(context).enqueue(workRequest)
        }
    }
}
