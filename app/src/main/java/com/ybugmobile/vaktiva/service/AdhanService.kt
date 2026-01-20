package com.ybugmobile.vaktiva.service

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.ybugmobile.vaktiva.R
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AdhanService : MediaSessionService() {

    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    
    private val handler = Handler(Looper.getMainLooper())
    private var volumeFadeRunnable: Runnable? = null

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        
        // Custom notification provider to ensure compliance with Android 14+ requirements
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setNotificationId(1001) // Match your NotificationHelper ID if possible
                .setChannelId("adhan_channel")
                .build()
        )

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_ALARM)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, false) // Required false for USAGE_ALARM
            .build()
            .apply {
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_ENDED) {
                            stopSelf()
                        }
                    }
                })
            }

        mediaSession = MediaSession.Builder(this, player!!)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val audioPath = intent?.getStringExtra("AUDIO_PATH")
        val prayerName = intent?.getStringExtra("PRAYER_NAME") ?: "Prayer"

        if (audioPath != null) {
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
