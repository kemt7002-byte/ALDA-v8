package com.alad.app.core.audio

import android.util.Log
import com.alad.app.core.network.DemucsClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.launch

/** Buffers six-second windows, keeps at most one pending window, and never sends mixed audio to Gemini. */
class DemucsStreamingProcessor(
    private val client: DemucsClient,
    private val onVocals: (ByteArray) -> Unit,
    private val onInstrumental: (ByteArray) -> Unit,
    private val onStatus: (String) -> Unit
) {
    companion object {
        private const val TAG = "DemucsProcessor"
        private const val WINDOW_BYTES = 16000 * 2 * 6 // 6 seconds, mono PCM16 @ 16kHz
    }
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val queue = Channel<ByteArray>(capacity = 1)
    private val buffer = ByteArrayOutputStream()
    @Volatile private var stopped = false

    init {
        scope.launch {
            for (window in queue) {
                if (stopped) break
                try {
                    onStatus("Đang tách giọng nói và nhạc nền bằng Demucs...")
                    val stems = client.separate(window)
                    onVocals(stems.vocalsPcm)
                    onInstrumental(stems.instrumentalPcm)
                    onStatus("Demucs hoạt động bình thường")
                } catch (e: Exception) {
                    Log.e(TAG, "Demucs request failed", e)
                    onStatus("LỖI DEMUCS: ${e.message ?: "Không kết nối được máy chủ"}. Kiểm tra Colab, Cloudflare Tunnel và URL Demucs.")
                }
            }
        }
    }

    @Synchronized fun add(pcm: ByteArray) {
        if (stopped) return
        buffer.write(pcm)
        var bytes = buffer.toByteArray()
        while (bytes.size >= WINDOW_BYTES) {
            val window = bytes.copyOfRange(0, WINDOW_BYTES)
            val remainder = bytes.copyOfRange(WINDOW_BYTES, bytes.size)
            buffer.reset()
            buffer.write(remainder)
            bytes = remainder
            if (queue.trySend(window).isFailure) onStatus("Demucs đang quá tải: đã bỏ qua một đoạn âm thanh. Chờ máy chủ xử lý.")
        }
    }

    fun stop() { stopped = true; queue.close() }
}
