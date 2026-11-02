package com.vrgames.clockshop.render

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log

/**
 * Looping background music from assets/audio/bgm.ogg. It needs no spatialization, so it uses the
 * plain Android player (the SDK's guidance for non-positional audio: lowest overhead). OGG loops
 * gaplessly. Without the asset the game simply has no music. Preparing is asynchronous: a
 * synchronous prepare() on the main thread at startup was long enough to trigger an ANR.
 */
class Music(context: Context) {
    private var player: MediaPlayer? = null
    private var resumed = false
    private var prepared = false

    var muted = false
        set(v) {
            field = v
            apply()
        }

    init {
        runCatching {
            context.assets.openFd(ASSET).use { fd ->
                player = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build(),
                    )
                    setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
                    isLooping = true
                    setVolume(VOLUME, VOLUME)
                    setOnPreparedListener {
                        prepared = true
                        apply()
                    }
                    prepareAsync()
                }
            }
        }.onFailure { Log.i(TAG, "no background music: ${it.message}") }
    }

    fun resume() {
        resumed = true
        apply()
    }

    fun pause() {
        resumed = false
        apply()
    }

    fun release() {
        player?.release()
        player = null
        prepared = false
    }

    private fun apply() {
        val p = player?.takeIf { prepared } ?: return
        runCatching { if (resumed && !muted) p.start() else if (p.isPlaying) p.pause() }
    }

    companion object {
        private const val TAG = "CaseMusic"
        private const val ASSET = "audio/bgm.ogg"
        /** Quiet, under the wood sounds; check on the device. */
        private const val VOLUME = 0.3f
    }
}
