package com.ybugmobile.vaktiva.data.audio

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioPlayer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var exoPlayer: ExoPlayer? = null
    private val TAG = "AudioPlayer"

    @OptIn(UnstableApi::class)
    private fun initializePlayer() {
        if (exoPlayer == null) {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build()

            exoPlayer = ExoPlayer.Builder(context)
                .setAudioAttributes(audioAttributes, true)
                .setHandleAudioBecomingNoisy(true)
                .build()
                .apply {
                    repeatMode = Player.REPEAT_MODE_OFF
                    addListener(object : Player.Listener {
                        override fun onPlayerError(error: PlaybackException) {
                            Log.e(TAG, "Player error: ${error.errorCodeName} (${error.errorCode})", error)
                        }
                        override fun onPlaybackStateChanged(state: Int) {
                            val stateName = when(state) {
                                Player.STATE_IDLE -> "IDLE"
                                Player.STATE_BUFFERING -> "BUFFERING"
                                Player.STATE_READY -> "READY"
                                Player.STATE_ENDED -> "ENDED"
                                else -> "UNKNOWN"
                            }
                            Log.d(TAG, "Playback state changed to: $stateName")
                        }
                    })
                }
        }
    }

    fun play(uri: Uri) {
        Log.d(TAG, "Attempting to play URI: $uri")
        initializePlayer()
        exoPlayer?.apply {
            stop()
            clearMediaItems()
            setMediaItem(MediaItem.fromUri(uri))
            volume = 1.0f
            prepare()
            play()
        }
    }

    fun playFromPath(path: String) {
        val uri = when {
            path.startsWith("android.resource://") -> {
                // Media3 recommendation for raw resources: rawresource:///resId
                val resId = path.substringAfterLast("/")
                Uri.parse("rawresource:///$resId")
            }
            path.contains("://") -> Uri.parse(path)
            else -> Uri.fromFile(File(path))
        }
        play(uri)
    }

    fun stop() {
        exoPlayer?.stop()
    }

    fun release() {
        exoPlayer?.release()
        exoPlayer = null
    }

    fun isPlaying(): Boolean = exoPlayer?.isPlaying ?: false
}
