package com.ybugmobile.waktiva.service

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
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
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.ybugmobile.waktiva.R
import com.ybugmobile.waktiva.data.notification.NotificationHelper
import com.ybugmobile.waktiva.domain.model.PrayerType
import com.ybugmobile.waktiva.ui.adhan.AdhanActivity
import dagger.hilt.android.AndroidEntryPoint
import java.io.File

/**
 * A foreground [MediaSessionService] responsible for high-reliability Adhan (Call to Prayer) audio playback.
 */
@AndroidEntryPoint
class AdhanService : MediaSessionService() {

    private var player: Player? = null
    private var mediaSession: MediaSession? = null
    private lateinit var audioManager: AudioManager
    
    private var isFallbackPlaying = false

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                stopPlaybackAndService()
            }
        }
    }

    companion object {
        private const val TAG = "AdhanService"
        const val ACTION_STOP_ADHAN = "com.ybugmobile.waktiva.ACTION_STOP_ADHAN"
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
            .setAudioAttributes(audioAttributes, false) // Automatic handling of audio focus is only available for USAGE_MEDIA and USAGE_GAME.
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        player = object : ForwardingPlayer(basePlayer) {
            override fun getAvailableCommands(): Player.Commands {
                return super.getAvailableCommands().buildUpon()
                    .remove(Player.COMMAND_PLAY_PAUSE)
                    .remove(Player.COMMAND_SEEK_TO_PREVIOUS)
                    .remove(Player.COMMAND_SEEK_TO_NEXT)
                    .remove(Player.COMMAND_SEEK_BACK)
                    .remove(Player.COMMAND_SEEK_FORWARD)
                    .build()
            }
        }

        basePlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                Log.d(TAG, "Playback state: $playbackState")
                if (playbackState == Player.STATE_ENDED) {
                    stopPlaybackAndService()
                }
            }
            override fun onPlayerError(error: PlaybackException) {
                Log.e(TAG, "Playback error: ${error.errorCodeName} (${error.errorCode})", error)
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
                val mediaMetadata = s.player.currentMediaItem?.mediaMetadata
                val prayerName = mediaMetadata?.extras?.getString(NotificationHelper.EXTRA_PRAYER_NAME) ?: 
                                 mediaMetadata?.title?.toString() ?: this@AdhanService.getString(R.string.adhan_default_prayer)
                return MediaNotification(NotificationHelper.NOTIFICATION_ID_ADHAN, createNotificationBuilder(prayerName).build())
            }
            override fun handleCustomCommand(s: MediaSession, a: String, e: Bundle) = false
        })

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(noisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(noisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_ADHAN) {
            stopPlaybackAndService()
            return START_NOT_STICKY
        }

        val prayerName = intent?.getStringExtra(NotificationHelper.EXTRA_PRAYER_NAME) ?: getString(R.string.adhan_default_prayer)
        val audioPath = intent?.getStringExtra("AUDIO_PATH")
        Log.d(TAG, "onStartCommand: prayer=$prayerName, audioPath=$audioPath")

        val notification = createNotificationBuilder(prayerName).build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NotificationHelper.NOTIFICATION_ID_ADHAN, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NotificationHelper.NOTIFICATION_ID_ADHAN, notification)
        }

        if (audioPath != null) {
            isFallbackPlaying = false
            
            val uri = when {
                audioPath.startsWith("android.resource://") -> {
                    val resId = audioPath.substringAfterLast("/")
                    Uri.parse("rawresource:///$resId")
                }
                audioPath.contains("://") -> Uri.parse(audioPath)
                else -> {
                    val resId = resources.getIdentifier(audioPath, "raw", packageName)
                    if (resId != 0) {
                        Uri.parse("rawresource:///$resId")
                    } else {
                        Uri.fromFile(File(audioPath))
                    }
                }
            }
            Log.d(TAG, "Playing URI: $uri")

            val prayerType = PrayerType.fromString(prayerName)
            val displayedPrayerName = prayerType?.getDisplayName(this) ?: prayerName
            val metadataTitle = getString(R.string.adhan_metadata_title_format, displayedPrayerName)
            
            val retriever = MediaMetadataRetriever()
            var adhanArtist: String? = null
            try {
                // MediaMetadataRetriever might not support rawresource:/// directly, 
                // so we use the android.resource:// format for metadata if possible
                val metadataUri = if (uri.scheme == "rawresource") {
                    Uri.parse("android.resource://$packageName/${uri.lastPathSegment}")
                } else uri
                
                retriever.setDataSource(this, metadataUri)
                adhanArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            } catch (e: Exception) {
                Log.w(TAG, "Metadata retrieval failed for $uri: ${e.message}")
            } finally {
                try { retriever.release() } catch (e: Exception) {}
            }

            val extras = Bundle().apply {
                putString(NotificationHelper.EXTRA_PRAYER_NAME, prayerName)
            }
            
            val metadata = MediaMetadata.Builder()
                .setTitle(metadataTitle)
                .setArtist(adhanArtist)
                .setExtras(extras)
                .build()
                
            val mediaItem = MediaItem.Builder()
                .setUri(uri)
                .setMediaMetadata(metadata)
                .build()
            
            player?.setMediaItem(mediaItem)
            player?.prepare()
            player?.volume = 1.0f 
            player?.play()
        }

        return super.onStartCommand(intent, flags, startId)
    }

    @OptIn(UnstableApi::class)
    private fun createNotificationBuilder(prayerName: String): NotificationCompat.Builder {
        val prayerType = PrayerType.fromString(prayerName)
        val displayedPrayerName = prayerType?.getDisplayName(this) ?: prayerName

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
            .setContentTitle(getString(R.string.notification_adhan_title, displayedPrayerName))
            .setContentText(getString(R.string.notification_adhan_content))
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(fullScreenPendingIntent, true) 
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.adhan_stop), stopPendingIntent)
    }

    private fun stopPlaybackAndService() {
        player?.stop()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = mediaSession

    override fun onDestroy() {
        try { unregisterReceiver(noisyReceiver) } catch (e: Exception) {}
        mediaSession?.run { player.release(); release() }
        super.onDestroy()
    }
}
