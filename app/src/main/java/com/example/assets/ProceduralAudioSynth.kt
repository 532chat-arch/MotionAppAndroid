package com.example.assets

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

object ProceduralAudioSynth {
    const val SAMPLE_RATE = 22050

    private val pcmCache = mutableMapOf<String, ShortArray>()

    fun getPcmSamples(proceduralUri: String): ShortArray {
        pcmCache[proceduralUri]?.let { return it }
        val samples = when (proceduralUri) {
            "procedural://audio_twilight_chimes" -> synthesizeChimes(
                durationSec = 10.0,
                notes = doubleArrayOf(261.63, 329.63, 392.0, 493.88, 523.25, 392.0, 329.63, 293.66),
                padFreq = 130.81,
            )
            "procedural://audio_midnight_pad" -> synthesizeChimes(
                durationSec = 12.0,
                notes = doubleArrayOf(220.0, 261.63, 329.63, 392.0, 440.0, 329.63, 293.66, 246.94),
                padFreq = 110.0,
            )
            else -> ShortArray(SAMPLE_RATE * 2)
        }
        pcmCache[proceduralUri] = samples
        return samples
    }

    fun getWaveformPeaks(proceduralUri: String, bars: Int = 64): List<Float> =
        extractPeaks(getPcmSamples(proceduralUri), bars)

    fun extractPeaks(samples: ShortArray, bars: Int = 64): List<Float> {
        if (samples.isEmpty()) return List(bars) { 0.1f }
        val step = max(1, samples.size / bars)
        return List(bars) { i ->
            val start = i * step
            val end = min(samples.size, start + step)
            var peak = 0
            for (j in start until end) {
                val a = abs(samples[j].toInt())
                if (a > peak) peak = a
            }
            (peak / 32768f).coerceIn(0.05f, 1f)
        }
    }

    fun encodeWavBytes(samples: ShortArray, sampleRate: Int = SAMPLE_RATE): ByteArray {
        val dataSize = samples.size * 2
        val buf = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
        buf.put("RIFF".toByteArray(Charsets.US_ASCII))
        buf.putInt(36 + dataSize)
        buf.put("WAVE".toByteArray(Charsets.US_ASCII))
        buf.put("fmt ".toByteArray(Charsets.US_ASCII))
        buf.putInt(16)
        buf.putShort(1) // PCM
        buf.putShort(1) // Mono
        buf.putInt(sampleRate)
        buf.putInt(sampleRate * 2)
        buf.putShort(2)
        buf.putShort(16)
        buf.put("data".toByteArray(Charsets.US_ASCII))
        buf.putInt(dataSize)
        for (s in samples) buf.putShort(s)
        return buf.array()
    }

    private fun synthesizeChimes(
        durationSec: Double,
        notes: DoubleArray,
        padFreq: Double,
    ): ShortArray {
        val total = (durationSec * SAMPLE_RATE).toInt()
        val out = ShortArray(total)
        val stepSec = durationSec / notes.size
        for (i in 0 until total) {
            val t = i.toDouble() / SAMPLE_RATE
            val noteIdx = min(notes.lastIndex, (t / stepSec).toInt())
            val localT = t - noteIdx * stepSec
            val env = exp(-localT * 2.6) * min(1.0, localT * 35.0)
            val freq = notes[noteIdx]
            val chime = (sin(2.0 * PI * freq * t) * 0.55 + sin(2.0 * PI * freq * 2.0 * t) * 0.22) * env
            val padEnv = 0.5 + 0.5 * sin(2.0 * PI * 0.18 * t)
            val pad = (sin(2.0 * PI * padFreq * t) * 0.24 + sin(2.0 * PI * padFreq * 1.5 * t) * 0.14) * padEnv
            val fadeOut = if (t > durationSec - 0.8) ((durationSec - t) / 0.8).coerceIn(0.0, 1.0) else 1.0
            val sample = ((chime + pad) * 0.52 * fadeOut).coerceIn(-0.98, 0.98)
            out[i] = (sample * 32767.0).toInt().toShort()
        }
        return out
    }
}
