package com.dresskit.wardrobe.ui.screens

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaDataSource
import android.media.MediaPlayer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.dresskit.wardrobe.data.DirectMessageService
import com.dresskit.wardrobe.model.DmMessage
import kotlinx.coroutines.CancellationException

internal class DmVoicePlayback(private val context: Context) {
    var activeId by mutableStateOf<String?>(null)
        private set
    var playing by mutableStateOf(false)
        private set
    var loading by mutableStateOf(false)
        private set
    var failed by mutableStateOf(false)
        private set
    var positionMs by mutableIntStateOf(0)
        private set
    var durationMs by mutableIntStateOf(0)
        private set
    private var speed = 1f

    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(attributes)
        .setWillPauseWhenDucked(true)
        .setOnAudioFocusChangeListener { change ->
            if (change < 0) pause()
        }
        .build()
    private var hasFocus = false
    private var player: MediaPlayer? = null
    private var generation = 0

    suspend fun toggle(convId: String, message: DmMessage, rate: Float) {
        if (activeId == message.msgId) {
            if (loading) return
            if (playing) {
                pause()
                return
            }
            player?.let {
                start(it)
                return
            }
        }
        close()
        val request = generation
        activeId = message.msgId
        speed = rate
        loading = true
        durationMs = message.mediaDurationSec * 1000
        try {
            val bytes = DirectMessageService.loadDmAudioBytes(context, convId, message)
            if (generation != request) return
            val mp = MediaPlayer()
            player = mp
            mp.setAudioAttributes(attributes)
            mp.setDataSource(object : MediaDataSource() {
                override fun getSize(): Long = bytes.size.toLong()
                override fun close() = Unit
                override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
                    if (size == 0) return 0
                    if (position < 0 || position >= bytes.size.toLong()) return -1
                    val start = position.toInt()
                    val count = minOf(size, bytes.size - start)
                    bytes.copyInto(buffer, offset, start, start + count)
                    return count
                }
            })
            mp.setOnPreparedListener {
                if (player !== mp) return@setOnPreparedListener
                loading = false
                durationMs = mp.duration.takeIf { it > 0 } ?: durationMs
                start(mp)
            }
            mp.setOnCompletionListener {
                if (player !== mp) return@setOnCompletionListener
                playing = false
                positionMs = 0
                runCatching { mp.seekTo(0) }
                abandonFocus()
            }
            mp.setOnErrorListener { _, _, _ ->
                if (player === mp) fail()
                true
            }
            mp.prepareAsync()
        } catch (cancelled: CancellationException) {
            if (generation == request) close()
            throw cancelled
        } catch (_: Exception) {
            if (generation == request) fail()
        }
    }

    fun changeSpeed(rate: Float) {
        speed = rate
        if (playing) player?.let { mp -> runCatching { mp.playbackParams = mp.playbackParams.setSpeed(rate) } }
    }

    fun seek(position: Int) {
        if (loading) return
        player?.let { mp ->
            runCatching { mp.seekTo(position.coerceIn(0, durationMs)) }
                .onSuccess { positionMs = position }
        }
    }

    fun updatePosition() {
        if (playing) player?.let { mp -> runCatching { positionMs = mp.currentPosition } }
    }

    fun pause() {
        if (playing) runCatching { player?.pause() }
        playing = false
        abandonFocus()
    }

    private fun start(mp: MediaPlayer) {
        runCatching {
            check(audioManager.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
            hasFocus = true
            mp.start()
            playing = true
            changeSpeed(speed)
        }.onFailure { fail() }
    }

    private fun abandonFocus() {
        if (!hasFocus) return
        hasFocus = false
        audioManager.abandonAudioFocusRequest(focus)
    }

    private fun fail() {
        val messageId = activeId
        close()
        activeId = messageId
        failed = true
    }

    fun suspendPlayback() {
        if (loading) close() else pause()
    }

    fun close() {
        generation++
        val previous = player
        player = null
        runCatching { previous?.release() }
        abandonFocus()
        activeId = null
        loading = false
        failed = false
        playing = false
        positionMs = 0
        durationMs = 0
    }
}
