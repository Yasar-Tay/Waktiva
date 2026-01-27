package com.ybugmobile.vaktiva.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioAttributes as AndroidAudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
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
import com.ybugmobile.vaktiva.data.notification.NotificationHelper
import com.ybugmobile.vaktiva.ui.adhan.AdhanActivity
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AdhanService : MediaSessionService(), AudioManager.OnAudioFocusChangeListener {

    private var player: Player? = null
    private var mediaSession: MediaSession? = null
    private lateinit var audioManager: AudioManager
    private var focusRequest: AudioFocusRequest? = null
    
    private val handler = Handler(Looper.getMainLooper())
    private var volumeFadeRunnable: Runnable? = null
    private var isFallbackPlaying = false

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                player?.pause()
            }
        }
    }

    companion object {
        const val ACTION_STOP_ADHAN = "com.ybugmobile.vaktiva.ACTION_STOP_ADHAN"
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_ALARM)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        val basePlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, false)
            .setWakeMode(C.WAKE_MODE_NETWORK) // Enable wake lock support
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
                return MediaNotification(NotificationHelper.NOTIFICATION_ID_ADHAN, createNotificationBuilder(prayerName, s).build())
            }
            override fun handleCustomCommand(s: MediaSession, a: String, e: Bundle) = false
        })

        registerReceiver(noisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_ADHAN) {
            stopPlaybackAndService()
            return START_NOT_STICKY
        }

        val prayerName = intent?.getStringExtra(NotificationHelper.EXTRA_PRAYER_NAME) ?: "Prayer"
        val audioPath = intent?.getStringExtra("AUDIO_PATH")

        // 1. Create and Start Foreground immediately
        val notification = createNotificationBuilder(prayerName, mediaSession).build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NotificationHelper.NOTIFICATION_ID_ADHAN, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NotificationHelper.NOTIFICATION_ID_ADHAN, notification)
        }

        // 2. Request Audio Focus and Prepare Player
        if (requestAudioFocus()) {
            if (audioPath != null && player?.playbackState == Player.STATE_IDLE) {
                isFallbackPlaying = false
                val metadata = MediaMetadata.Builder().setTitle("Adhan: $prayerName").build()
                val mediaItem = MediaItem.Builder().setUri(audioPath.toUri()).setMediaMetadata(metadata).build()
                
                player?.setMediaItem(mediaItem)
                player?.prepare()
                startFadeIn()
                player?.play()
            }
        }

        return super.onStartCommand(intent, flags, startId)
    }

    private fun requestAudioFocus(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val playbackAttributes = AndroidAudioAttributes.Builder()
                .setUsage(AndroidAudioAttributes.USAGE_ALARM)
                .setContentType(AndroidAudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(playbackAttributes)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(this)
                .build()
            audioManager.requestAudioFocus(focusRequest!!) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(this, AudioManager.STREAM_ALARM, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    override fun onAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> player?.volume = 1.0f
            AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> player?.pause()
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> player?.volume = 0.2f
        }
    }

    @OptIn(UnstableApi::class)
    private fun createNotificationBuilder(prayerName: String, session: MediaSession?): NotificationCompat.Builder {
        val fullScreenIntent = Intent(this, AdhanActivity::class.java).apply {
            putExtra(NotificationHelper.EXTRA_PRAYER_NAME, prayerName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this, 0, fullScreenIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, AdhanService::class.java).apply { action = ACTION_STOP_ADHAN }
        val stopPendingIntent = PendingIntent.getService(this, 1, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, NotificationHelper.CHANNEL_ID_ADHAN)
            .setContentTitle("Adhan: $prayerName")
            .setContentText("It's time for prayer")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(fullScreenPendingIntent, true) 
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "STOP ADHAN", stopPendingIntent)
            .setStyle(session?.let { MediaStyleNotificationHelper.MediaStyle(it).setShowActionsInCompactView(0) })
    }

    private fun stopPlaybackAndService() {
        player?.stop()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && focusRequest != null) {
            audioManager.abandonAudioFocusRequest(focusRequest!!)
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(this)
        }
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
        unregisterReceiver(noisyReceiver)
        volumeFadeRunnable?.let { handler.removeCallbacks(it) }
        mediaSession?.run { player.release(); release() }
        super.onDestroy()
    }
}
