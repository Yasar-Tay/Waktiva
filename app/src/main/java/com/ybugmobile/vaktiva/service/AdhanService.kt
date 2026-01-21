package com.ybugmobile.vaktiva.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.collect.ImmutableList
import com.ybugmobile.vaktiva.R
import com.ybugmobile.vaktiva.ui.adhan.AdhanActivity
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AdhanService : MediaSessionService() {

    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    
    private val handler = Handler(Looper.getMainLooper())
    private var volumeFadeRunnable: Runnable? = null

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

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, false) // Changed to false to avoid IllegalArgumentException with USAGE_ALARM
            .build()
            .apply {
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_ENDED) {
                            stopForeground(Service.STOP_FOREGROUND_REMOVE)
                            stopSelf()
                        }
                    }
                })
            }

        mediaSession = MediaSession.Builder(this, player!!)
            .build()

        // Custom notification provider to handle full-screen intent and custom actions
        setMediaNotificationProvider(object : MediaNotification.Provider {
            private val defaultProvider = DefaultMediaNotificationProvider(this@AdhanService)

            override fun createNotification(
                session: MediaSession,
                customLayout: ImmutableList<CommandButton>,
                actionFactory: MediaNotification.ActionFactory,
                onNotificationChangedCallback: MediaNotification.Provider.Callback
            ): MediaNotification {
                val mediaNotification = defaultProvider.createNotification(session, customLayout, actionFactory, onNotificationChangedCallback)
                
                val prayerName = session.player.currentMediaItem?.mediaMetadata?.title?.toString()?.replace("Adhan: ", "") ?: "Prayer"
                
                val fullScreenIntent = Intent(this@AdhanService, AdhanActivity::class.java).apply {
                    putExtra("PRAYER_NAME", prayerName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                val fullScreenPendingIntent = PendingIntent.getActivity(
                    this@AdhanService, 
                    0, 
                    fullScreenIntent, 
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val stopIntent = Intent(this@AdhanService, AdhanService::class.java).apply {
                    action = ACTION_STOP_ADHAN
                }
                val stopPendingIntent = PendingIntent.getService(
                    this@AdhanService,
                    1,
                    stopIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                // Recover the builder from the notification created by the default provider
                val builder = Notification.Builder.recoverBuilder(this@AdhanService, mediaNotification.notification)
                
                // Add the full-screen intent
                builder.setFullScreenIntent(fullScreenPendingIntent, true)
                
                // Create Stop Action
                val stopAction = Notification.Action.Builder(
                    android.graphics.drawable.Icon.createWithResource(this@AdhanService, android.R.drawable.ic_menu_close_clear_cancel),
                    "Stop",
                    stopPendingIntent
                ).build()

                // Replace existing actions (like Play/Pause) with just the Stop action
                builder.setActions(stopAction)

                // Show Stop button in compact view (index 0)
                val token = mediaNotification.notification.extras?.getParcelable(Notification.EXTRA_MEDIA_SESSION) as? android.media.session.MediaSession.Token
                if (token != null) {
                    builder.setStyle(
                        Notification.MediaStyle()
                            .setMediaSession(token)
                            .setShowActionsInCompactView(0)
                    )
                }
                
                // Rebuild the notification
                return MediaNotification(mediaNotification.notificationId, builder.build())
            }

            override fun handleCustomCommand(
                session: MediaSession,
                action: String,
                extras: Bundle
            ): Boolean {
                return defaultProvider.handleCustomCommand(session, action, extras)
            }
        })
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_ADHAN) {
            player?.stop()
            player?.clearMediaItems()
            stopForeground(Service.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val audioPath = intent?.getStringExtra("AUDIO_PATH")
        val prayerName = intent?.getStringExtra("PRAYER_NAME") ?: "Prayer"

        if (audioPath != null) {
            val fullScreenIntent = Intent(this, AdhanActivity::class.java).apply {
                putExtra("PRAYER_NAME", prayerName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            val fullScreenPendingIntent = PendingIntent.getActivity(
                this, 0, fullScreenIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val stopIntent = Intent(this, AdhanService::class.java).apply {
                action = ACTION_STOP_ADHAN
            }
            val stopPendingIntent = PendingIntent.getService(
                this, 1, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Adhan: $prayerName")
                .setContentText("It's time for prayer")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setFullScreenIntent(fullScreenPendingIntent, true)
                .addAction(R.drawable.ic_launcher_foreground, "STOP", stopPendingIntent)
                .setOngoing(true)
                .build()

            startForeground(NOTIFICATION_ID, notification)

            val mediaMetadata = MediaMetadata.Builder()
                .setTitle("Adhan: $prayerName")
                .setArtist("Vaktiva")
                .setAlbumTitle("Prayer Call")
                .setDisplayTitle("Time for $prayerName")
                .build()

            val mediaItem = MediaItem.Builder()
                .setUri(Uri.parse(audioPath))
                .setMediaId(prayerName)
                .setMediaMetadata(mediaMetadata)
                .build()
            
            player?.setMediaItem(mediaItem)
            player?.prepare()
            
            startFadeIn()
            player?.play()
        }

        return super.onStartCommand(intent, flags, startId)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
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

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

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
