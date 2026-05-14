package com.ybugmobile.waktiva.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingWorkPolicy
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
            // Use enqueueUniqueWork so a boot-triggered run never overlaps with the
            // already-scheduled periodic PrayerUpdateWorker. KEEP means if one is
            // already queued/running, this boot trigger is silently dropped.
            val workRequest = OneTimeWorkRequestBuilder<PrayerUpdateWorker>().build()
            // Use a separate unique name so this one-time boot run never collides
            // with the periodic "PrayerUpdateWork" that WorkManager restores on reboot.
            // If KEEP were used with the same name, WorkManager would silently drop
            // this request because the periodic work already occupies that slot.
            WorkManager.getInstance(context).enqueueUniqueWork(
                "PrayerUpdateWork_Boot",
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
        }
    }
}
