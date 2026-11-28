package io.github.robin19666.zapretmanager.core

import android.content.Context
import android.media.AudioManager
import android.media.AudioTrack
import io.github.robin19666.zapretmanager.core.ZmScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Короткое «чирик-чирик» воробушка.
 *
 * Звук синтезируется на месте (два свиста с подъёмом частоты), поэтому в APK не
 * добавляется ни одного файла. Играем только если звук мультимедиа не выключен:
 * на беззвучном телефоне тычок по птице остаётся просто анимацией.
 */
object Chirp {
    private const val SAMPLE_RATE = 22_050

    fun play(context: Context) {
        val audio = context.getSystemService(AudioManager::class.java) ?: return
        val silent = audio.ringerMode == AudioManager.RINGER_MODE_SILENT
        val volume = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        if (volume <= 0 || silent) return
        ZmScope.scope.launch(Dispatchers.Default) {
            runCatching { playChirps() }
        }
    }

    private fun playChirps() {
        val pcm = buildChirps()
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                android.media.AudioFormat.Builder()
                    .setEncoding(android.media.AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(android.media.AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(pcm.size * 2)
            .build()
        try {
            track.write(pcm, 0, pcm.size)
            track.play()
            // Даём звуку доиграть, чтобы трек не закрылся на середине.
            Thread.sleep(420)
        } finally {
            runCatching { track.stop() }
            track.release()
        }
    }

    /** Два коротких свиста: 2.3→4.3 кГц, затем 2.6→4.8 кГц. */
    private fun buildChirps(): ShortArray {
        val samples = ArrayList<Short>(SAMPLE_RATE / 2)
        listOf(2_300f to 4_300f, 2_600f to 4_800f).forEach { (from, to) ->
            val duration = 0.085f
            val count = (SAMPLE_RATE * duration).toInt()
            var phase = 0.0
            for (i in 0 until count) {
                val progress = i.toFloat() / count
                val frequency = from + (to - from) * progress
                phase += 2.0 * PI * frequency / SAMPLE_RATE
                val envelope = sin(PI * progress).let { it * it } * exp(-2.6f * progress)
                val value = (sin(phase) * envelope * 0.32 * Short.MAX_VALUE).toInt()
                samples += value.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            // Пауза между свистами.
            repeat(SAMPLE_RATE / 40) { samples += 0 }
        }
        return samples.toShortArray()
    }
}
