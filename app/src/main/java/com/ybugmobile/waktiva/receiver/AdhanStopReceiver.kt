package com.ybugmobile.waktiva.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.WorkManager
import com.ybugmobile.waktiva.data.worker.AdhanWorker

class AdhanStopReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        WorkManager.getInstance(context).cancelUniqueWork(AdhanWorker.WORK_NAME)
    }
}
