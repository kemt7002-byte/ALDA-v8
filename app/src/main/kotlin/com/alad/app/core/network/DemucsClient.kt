package com.alad.app.core.network

import android.util.Base64
import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit

/** Calls the temporary free Google Colab + Cloudflare Tunnel endpoint. Input/output PCM is mono signed 16-bit, 16 kHz. */
class DemucsClient(private val spaceUrl: String = SPACE_URL) {
    companion object {
        // Replace this with your deployed Hugging Face Space URL (ending in .hf.space).
        const val SPACE_URL = "" // Endpoint is entered in Settings; retained only as a fallback.
    }
    data class Stems(val vocalsPcm: ByteArray, val instrumentalPcm: ByteArray)

    private val client = OkHttpClient.Builder()
        .connectTimeout(45, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    fun separate(pcm16kMono: ByteArray): Stems {
        require(spaceUrl.startsWith("https://")) { "Hugging Face Space URL is not configured" }
        val wav = pcmToWav(pcm16kMono, 16000)
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("file", "audio.wav", wav.toRequestBody("audio/wav".toMediaType()))
            .build()
        val base = spaceUrl.trimEnd('/')
        val request = Request.Builder().url("$base/separate").post(body).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("Demucs Space HTTP ${response.code}: ${response.body?.string()?.take(300)}")
            val json = JSONObject(response.body?.string() ?: error("Empty Demucs response"))
            val vocalsWav = Base64.decode(json.getString("vocals_wav"), Base64.DEFAULT)
            val instrumentalWav = Base64.decode(json.getString("instrumental_wav"), Base64.DEFAULT)
            return Stems(wavToPcm(vocalsWav), wavToPcm(instrumentalWav))
        }
    }

    private fun pcmToWav(pcm: ByteArray, sampleRate: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val dataSize = pcm.size
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray()); header.putInt(36 + dataSize); header.put("WAVE".toByteArray())
        header.put("fmt ".toByteArray()); header.putInt(16); header.putShort(1.toShort()); header.putShort(1.toShort())
        header.putInt(sampleRate); header.putInt(sampleRate * 2); header.putShort(2.toShort()); header.putShort(16.toShort())
        header.put("data".toByteArray()); header.putInt(dataSize)
        out.write(header.array()); out.write(pcm); return out.toByteArray()
    }

    private fun wavToPcm(wav: ByteArray): ByteArray {
        require(wav.size > 44 && String(wav, 0, 4) == "RIFF") { "Invalid WAV from Demucs Space" }
        val buffer = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)
        var offset = 12
        while (offset + 8 <= wav.size) {
            val id = String(wav, offset, 4)
            val size = buffer.getInt(offset + 4).coerceAtLeast(0)
            if (id == "data") {
                val start = offset + 8
                return wav.copyOfRange(start, (start + size).coerceAtMost(wav.size))
            }
            offset += 8 + size + (size and 1)
        }
        throw IllegalStateException("WAV data chunk not found")
    }
}
