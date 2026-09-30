package com.ybugmobile.waktiva.data.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ybugmobile.waktiva.data.local.preferences.SettingsManager
import com.ybugmobile.waktiva.domain.repository.PrayerRepository
import com.ybugmobile.waktiva.ui.widget.WaktivaWidget
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/**
 * Fetches the weather for the day circle widgets when the one they have is getting old, and
 * redraws them with it. The repository keeps what it fetches in the weather cache the widgets
 * read, so a widget never waits on the network while it's drawn.
 */
@HiltWorker
class WidgetWeatherWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: PrayerRepository,
    private val settingsManager: SettingsManager
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val settings = settingsManager.settingsFlow.first()
        val lat = settings.latitude ?: return Result.success()
        val lng = settings.longitude ?: return Result.success()
        // A failure isn't retried here: the next redraw asks again once the pause is over.
        repository.getWeatherData(lat, lng, settings.locationName)
            .onSuccess { WaktivaWidget.updateAll(applicationContext) }
            .onFailure { Log.w(TAG, "Widget weather refresh failed", it) }
        return Result.success()
    }

    companion object {
        private const val TAG = "WidgetWeatherWorker"
        private const val WORK_NAME = "widget_weather_refresh"

        /** Weather older than this is fetched again. */
        private const val STALE_MILLIS = 90 * 60 * 1000L

        /** The least time between two tries, so a failing fetch doesn't repeat with every redraw. */
        private const val RETRY_PAUSE_MILLIS = 30 * 60 * 1000L

        private const val PREFS = "widget_weather_worker"
        private const val KEY_LAST_TRY = "last_try"

        /** Asks for fresh weather when the widgets' is older than [STALE_MILLIS] or missing. */
        fun requestIfStale(context: Context, fetchedAtMillis: Long?) {
            val now = System.currentTimeMillis()
            if (fetchedAtMillis != null && now - fetchedAtMillis in 0 until STALE_MILLIS) return

            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val lastTry = prefs.getLong(KEY_LAST_TRY, 0L)
            if (now - lastTry in 0 until RETRY_PAUSE_MILLIS) return
            prefs.edit().putLong(KEY_LAST_TRY, now).apply()

            val request = OneTimeWorkRequestBuilder<WidgetWeatherWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
        }
    }
}
