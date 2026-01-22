package com.ybugmobile.vaktiva.service

import android.app.NotificationChannel
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
        const val CHANNEL_ID = "adhan_channel"
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        
        createNotificationChannel()

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_ALARM)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        val basePlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, false)
            .build()

        // Hide "seek to previous/next" buttons by stripping the commands
        player = object : ForwardingPlayer(basePlayer) {
            override fun getAvailableCommands(): Player.Commands {
                return super.getAvailableCommands().buildUpon()
                    .remove(Player.COMMAND_SEEK_TO_PREVIOUS)
                    .remove(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .remove(Player.COMMAND_SEEK_TO_NEXT)
                    .remove(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
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
                    val fallbackUri = Settings.System.DEFAULT_ALARM_ALERT_URI
                    val mediaItem = MediaItem.fromUri(fallbackUri)
                    basePlayer.setMediaItem(mediaItem)
                    basePlayer.prepare()
                    basePlayer.play()
                } else {
                    stopPlaybackAndService()
                }
            }
        })

        val stopButton = CommandButton.Builder()
            .setDisplayName("Stop Adhan")
            .setIconResId(android.R.drawable.ic_menu_close_clear_cancel)
            .setSessionCommand(SessionCommand(ACTION_STOP_ADHAN, Bundle.EMPTY))
            .build()

        mediaSession = MediaSession.Builder(this, player!!)
            .setCustomLayout(ImmutableList.of(stopButton))
            .setCallback(object : MediaSession.Callback {
                override fun onCustomCommand(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    customCommand: SessionCommand,
                    args: Bundle
                ): ListenableFuture<SessionResult> {
                    if (customCommand.customAction == ACTION_STOP_ADHAN) {
                        stopPlaybackAndService()
                    }
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
            })
            .build()

        setMediaNotificationProvider(object : MediaNotification.Provider {
            @OptIn(UnstableApi::class)
            override fun createNotification(
                session: MediaSession,
                customLayout: ImmutableList<CommandButton>,
                actionFactory: MediaNotification.ActionFactory,
                onNotificationChangedCallback: MediaNotification.Provider.Callback
            ): MediaNotification {
                val prayerName = session.player.currentMediaItem?.mediaMetadata?.title?.toString()?.replace("Adhan: ", "") ?: "Prayer"
                val notificationBuilder = createNotificationBuilder(prayerName, session)
                return MediaNotification(NOTIFICATION_ID, notificationBuilder.build())
            }

            override fun handleCustomCommand(session: MediaSession, action: String, extras: Bundle) = false
        })
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_ADHAN) {
            stopPlaybackAndService()
            return START_NOT_STICKY
        }

        val prayerName = intent?.getStringExtra("PRAYER_NAME") ?: "Prayer"
        
        val notification = createNotificationBuilder(prayerName, mediaSession).build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        val audioPath = intent?.getStringExtra("AUDIO_PATH")
        if (audioPath != null && player?.playbackState == Player.STATE_IDLE) {
            isFallbackPlaying = false
            
            val mediaMetadata = MediaMetadata.Builder()
                .setTitle("Adhan: $prayerName")
                .build()

            val mediaItem = MediaItem.Builder()
                .setUri(audioPath.toUri())
                .setMediaMetadata(mediaMetadata)
                .build()
            
            player?.setMediaItem(mediaItem)
            player?.prepare()
            startFadeIn()
            player?.play()

            val activityIntent = Intent(this, AdhanActivity::class.java).apply {
                putExtra("PRAYER_NAME", prayerName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            startActivity(activityIntent)
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
            this, 
            0, 
            fullScreenIntent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, AdhanService::class.java).apply {
            action = ACTION_STOP_ADHAN
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Adhan: $prayerName")
            .setContentText("It's time for prayer")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setOngoing(true)
            .setAutoCancel(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "STOP ADHAN", stopPendingIntent)

        session?.let {
            builder.setStyle(MediaStyleNotificationHelper.MediaStyle(it)
                .setShowActionsInCompactView(0, 1))
        }

        return builder
    }

    private fun stopPlaybackAndService() {
        player?.stop()
        player?.clearMediaItems()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Adhan Playback",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Shows notification when Adhan is playing"
                setSound(null, null)
                enableVibration(true)
                setBypassDnd(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }
            manager.createNotificationChannel(channel)
        }
    }

    private fun startFadeIn() {
        volumeFadeRunnable?.let { handler.removeCallbacks(it) }
        var currentVolume = 0.05f
        player?.volume = currentVolume
        volumeFadeRunnable = object : Runnable {
            override fun run() {
                if (player == null) return
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
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        player = null
        super.onDestroy()
    }
}
