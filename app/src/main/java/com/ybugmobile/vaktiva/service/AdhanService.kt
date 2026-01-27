package com.ybugmobile.vaktiva.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.MediaStyleNotificationHelper
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.ybugmobile.vaktiva.R
import com.ybugmobile.vaktiva.ui.adhan.AdhanActivity
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AdhanService : MediaSessionService() {

    private var player: Player? = null
    private var mediaSession: MediaSession? = null
    
    private val handler = Handler(Looper.getMainLooper())
    private var volumeFadeRunnable: Runnable? = null
    private var isFallbackPlaying = false

    companion object {
        const val ACTION_STOP_ADHAN = "com.ybugmobile.vaktiva.ACTION_STOP_ADHAN"
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "adhan_playback_channel"
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_ALARM)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        val basePlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, false)
            .build()

        player = object : ForwardingPlayer(basePlayer) {
            override fun getAvailableCommands(): Player.Commands {
                return super.getAvailableCommands().buildUpon()
                    .remove(Player.COMMAND_SEEK_TO_PREVIOUS)
                    .remove(Player.COMMAND_SEEK_TO_NEXT)
                    .build()
            }
        }

        basePlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    stopPlaybackAndService()
                }
            }
            override fun onPlayerError(error: PlaybackException) {
                if (!isFallbackPlaying) {
                    isFallbackPlaying = true
                    basePlayer.setMediaItem(MediaItem.fromUri(Settings.System.DEFAULT_ALARM_ALERT_URI))
                    basePlayer.prepare()
                    basePlayer.play()
                } else {
                    stopPlaybackAndService()
                }
            }
        })

        mediaSession = MediaSession.Builder(this, player!!)
            .setCallback(object : MediaSession.Callback {
                override fun onCustomCommand(s: MediaSession, c: MediaSession.ControllerInfo, cmd: SessionCommand, a: Bundle): ListenableFuture<SessionResult> {
                    if (cmd.customAction == ACTION_STOP_ADHAN) stopPlaybackAndService()
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
            })
            .build()

        setMediaNotificationProvider(object : MediaNotification.Provider {
            override fun createNotification(s: MediaSession, cl: ImmutableList<CommandButton>, af: MediaNotification.ActionFactory, cb: MediaNotification.Provider.Callback): MediaNotification {
                val prayerName = s.player.currentMediaItem?.mediaMetadata?.title?.toString()?.replace("Adhan: ", "") ?: "Prayer"
                return MediaNotification(NOTIFICATION_ID, createNotificationBuilder(prayerName, s).build())
            }
            override fun handleCustomCommand(s: MediaSession, a: String, e: Bundle) = false
        })
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_ADHAN) {
            stopPlaybackAndService()
            return START_NOT_STICKY
        }

        val prayerName = intent?.getStringExtra("PRAYER_NAME") ?: "Prayer"
        val audioPath = intent?.getStringExtra("AUDIO_PATH")

        // 1. Create and Start Foreground immediately
        val notification = createNotificationBuilder(prayerName, mediaSession).build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        // 2. Prepare Player
        if (audioPath != null && player?.playbackState == Player.STATE_IDLE) {
            isFallbackPlaying = false
            val metadata = MediaMetadata.Builder().setTitle("Adhan: $prayerName").build()
            val mediaItem = MediaItem.Builder().setUri(audioPath.toUri()).setMediaMetadata(metadata).build()
            
            player?.setMediaItem(mediaItem)
            player?.prepare()
            startFadeIn()
            player?.play()
        }

        return super.onStartCommand(intent, flags, startId)
    }

    @OptIn(UnstableApi::class)
    private fun createNotificationBuilder(prayerName: String, session: MediaSession?): NotificationCompat.Builder {
        val fullScreenIntent = Intent(this, AdhanActivity::class.java).apply {
            putExtra("PRAYER_NAME", prayerName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this, 0, fullScreenIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, AdhanService::class.java).apply { action = ACTION_STOP_ADHAN }
        val stopPendingIntent = PendingIntent.getService(this, 1, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Adhan: $prayerName")
            .setContentText("It's time for prayer")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(fullScreenPendingIntent, true) // This launches the UI on lockscreen
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "STOP ADHAN", stopPendingIntent)
            .setStyle(session?.let { MediaStyleNotificationHelper.MediaStyle(it).setShowActionsInCompactView(0) })
    }

    private fun stopPlaybackAndService() {
        player?.stop()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startFadeIn() {
        volumeFadeRunnable?.let { handler.removeCallbacks(it) }
        var currentVolume = 0.05f
        player?.volume = currentVolume
        volumeFadeRunnable = object : Runnable {
            override fun run() {
                currentVolume += 0.05f
                if (currentVolume <= 1.0f) {
                    player?.volume = currentVolume
                    handler.postDelayed(this, 1000)
                }
            }
        }
        handler.post(volumeFadeRunnable!!)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = mediaSession

    override fun onDestroy() {
        volumeFadeRunnable?.let { handler.removeCallbacks(it) }
        mediaSession?.run { player.release(); release() }
        super.onDestroy()
    }
}
