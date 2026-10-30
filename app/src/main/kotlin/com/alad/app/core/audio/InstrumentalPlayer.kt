package com.alad.app.core.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/** Plays the separated instrumental on the assistant stream so muting STREAM_MUSIC does not mute this stem. */
class InstrumentalPlayer {
    private var track: AudioTrack? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val queue = Channel<ByteArray>(capacity = Channel.UNLIMITED)
    fun start() {
        val min = AudioTrack.getMinBufferSize(16000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(16000).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
            .setBufferSizeInBytes(maxOf(min, 8192))
            .setTransferMode(AudioTrack.MODE_STREAM).build().also { it.setVolume(1.0f); it.play() }
        scope.launch { for (pcm in queue) try { track?.write(pcm, 0, pcm.size) } catch (e: Exception) { Log.e("InstrumentalPlayer", "Playback failed", e) } }
    }
    fun play(pcm: ByteArray) { queue.trySend(pcm) }
    fun stop() {
        queue.close(); scope.cancel()
        try { track?.stop(); track?.release() } catch (_: Exception) {}
        track = null
    }
}
