package com.ybugmobile.vaktiva.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.ybugmobile.vaktiva.data.worker.PrayerUpdateWorker
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Trigger a prayer update work to reschedule all alarms
            val workRequest = OneTimeWorkRequestBuilder<PrayerUpdateWorker>().build()
            WorkManager.getInstance(context).enqueue(workRequest)
        }
    }
}
