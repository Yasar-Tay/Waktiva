package com.ybugmobile.waktiva.data.worker

import android.content.ContentResolver
import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.annotation.OptIn
import androidx.hilt.work.HiltWorker
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.ybugmobile.waktiva.data.notification.NotificationHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * WorkManager worker that handles adhan audio playback.
 *
 * Using WorkManager's setForeground() instead of startForegroundService() is the
 * Android 15-compliant approach: it is explicitly exempt from BOOT_COMPLETED
 * foreground service restrictions.
 */
@HiltWorker
class AdhanWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val notificationHelper: NotificationHelper
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_NAME = "adhan_playback"
        const val KEY_PRAYER_NAME = "prayer_name"
        const val KEY_AUDIO_PATH = "audio_path"
        private const val TAG = "AdhanWorker"
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notification = notificationHelper.createAdhanNotification(
            inputData.getString(KEY_PRAYER_NAME) ?: ""
        )
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NotificationHelper.NOTIFICATION_ID_ADHAN,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            ForegroundInfo(NotificationHelper.NOTIFICATION_ID_ADHAN, notification)
        }
    }

    @OptIn(UnstableApi::class)
    override suspend fun doWork(): Result {
        val prayerName = inputData.getString(KEY_PRAYER_NAME) ?: return Result.failure()
        val audioPath = inputData.getString(KEY_AUDIO_PATH) ?: return Result.failure()

        Log.d(TAG, "Starting adhan for $prayerName")
        setProgress(workDataOf(KEY_PRAYER_NAME to prayerName))

        // setForeground() via WorkManager is NOT subject to BOOT_COMPLETED FGS restrictions.
        // This is the Android 15-recommended replacement for startForegroundService().
        val notification = notificationHelper.createAdhanNotification(prayerName)
        val foregroundInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NotificationHelper.NOTIFICATION_ID_ADHAN,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            ForegroundInfo(NotificationHelper.NOTIFICATION_ID_ADHAN, notification)
        }
        setForeground(foregroundInfo)

        return suspendCancellableCoroutine { continuation ->
            var player: ExoPlayer? = null

            continuation.invokeOnCancellation {
                mainHandler.post {
                    Log.d(TAG, "Adhan cancelled — stopping player")
                    player?.stop()
                    player?.release()
                    player = null
                }
            }

            mainHandler.post {
                if (!continuation.isActive) return@post

                var isFallbackPlaying = false

                val exoPlayer = ExoPlayer.Builder(appContext)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(C.USAGE_ALARM)
                            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                            .build(),
                        false
                    )
                    .setHandleAudioBecomingNoisy(true)
                    .setWakeMode(C.WAKE_MODE_LOCAL)
                    .build()
                player = exoPlayer

                exoPlayer.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_ENDED) {
                            Log.d(TAG, "Playback ended naturally")
                            exoPlayer.release()
                            player = null
                            if (continuation.isActive) continuation.resume(Result.success())
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        Log.e(TAG, "Playback error: ${error.errorCodeName}", error)
                        if (!isFallbackPlaying) {
                            isFallbackPlaying = true
                            exoPlayer.setMediaItem(MediaItem.fromUri(Settings.System.DEFAULT_ALARM_ALERT_URI))
                            exoPlayer.prepare()
                            exoPlayer.play()
                        } else {
                            exoPlayer.release()
                            player = null
                            if (continuation.isActive) continuation.resume(Result.success())
                        }
                    }
                })

                val uri = resolveAudioUri(audioPath)
                Log.d(TAG, "Playing URI: $uri")
                exoPlayer.setMediaItem(MediaItem.fromUri(uri))
                exoPlayer.prepare()
                exoPlayer.play()
            }
        }
    }

    private fun resolveAudioUri(audioPath: String): Uri {
        return when {
            audioPath.startsWith("android.resource://") -> {
                val resId = audioPath.substringAfterLast("/")
                Uri.Builder()
                    .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
                    .authority(appContext.packageName)
                    .appendPath(resId)
                    .build()
            }
            audioPath.contains("://") -> Uri.parse(audioPath)
            else -> {
                val resId = appContext.resources.getIdentifier(audioPath, "raw", appContext.packageName)
                if (resId != 0) {
                    Uri.Builder()
                        .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
                        .authority(appContext.packageName)
                        .appendPath(resId.toString())
                        .build()
                } else {
                    Uri.fromFile(File(audioPath))
                }
            }
        }
    }
}
