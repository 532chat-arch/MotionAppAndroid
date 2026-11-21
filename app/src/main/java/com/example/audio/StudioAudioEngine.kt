package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import android.net.Uri
import com.example.assets.ProceduralAudioSynth
import com.example.schema.AssetRecord
import com.example.schema.AudioTrackDocument
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.max
import kotlin.math.min

class StudioAudioEngine(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pcmPlayJob: Job? = null
    private var activeTrack: AudioTrack? = null
    private val activePlayers = mutableListOf<MediaPlayer>()

    @Synchronized
    fun stopAll() {
        pcmPlayJob?.cancel()
        pcmPlayJob = null
        activeTrack?.let { tr ->
            runCatching {
                tr.pause()
                tr.flush()
                tr.stop()
                tr.release()
            }
        }
        activeTrack = null
        for (mp in activePlayers) {
            runCatching {
                if (mp.isPlaying) mp.stop()
                mp.release()
            }
        }
        activePlayers.clear()
    }

    fun startPlayback(
        tracks: List<AudioTrackDocument>,
        assets: List<AssetRecord>,
        startFrame: Int,
        durationFrames: Int,
        fps: Double,
        masterMuted: Boolean,
    ) {
        stopAll()
        if (masterMuted || tracks.isEmpty()) return

        val anySolo = tracks.any { it.solo }
        val activeTracks = tracks.filter { t -> !t.muted && (!anySolo || t.solo) }
        if (activeTracks.isEmpty()) return

        val safeFps = max(1.0, fps)
        val startSec = startFrame / safeFps
        val totalRemainingSec = max(0.25, (durationFrames - startFrame) / safeFps)

        // Launch procedural/WAV PCM mix via AudioTrack
        val pcmClips = activeTracks.flatMap { tr ->
            tr.clips.mapNotNull { clip ->
                val asset = assets.find { it.id == clip.assetId } ?: return@mapNotNull null
                if (asset.dataUrl.startsWith("procedural://")) {
                    Triple(tr, clip, ProceduralAudioSynth.getPcmSamples(asset.dataUrl))
                } else null
            }
        }

        if (pcmClips.isNotEmpty()) {
            pcmPlayJob = scope.launch {
                val sampleRate = ProceduralAudioSynth.SAMPLE_RATE
                val totalSamples = min(sampleRate * 60, (totalRemainingSec * sampleRate).toInt().coerceAtLeast(1024))
                val mix = FloatArray(totalSamples)

                for ((track, clip, samples) in pcmClips) {
                    if (samples.isEmpty()) continue
                    val clipStartSec = clip.startFrame / safeFps
                    val clipDurSec = clip.durationFrames / safeFps
                    val clipEndSec = clipStartSec + clipDurSec
                    if (clipEndSec <= startSec) continue

                    val vol = (track.volume * clip.volume).coerceIn(0.0, 2.0).toFloat()
                    val fadeInSec = clip.fadeInFrames / safeFps
                    val fadeOutSec = clip.fadeOutFrames / safeFps

                    val outStartIdx = max(0, ((clipStartSec - startSec) * sampleRate).toInt())
                    val outEndIdx = min(totalSamples, ((clipEndSec - startSec) * sampleRate).toInt())
                    val baseOffsetSamples = (clip.inFrame / safeFps * sampleRate).toInt()

                    for (i in outStartIdx until outEndIdx) {
                        val playSec = startSec + i.toDouble() / sampleRate
                        val localSec = playSec - clipStartSec
                        var srcIdx = baseOffsetSamples + (localSec * sampleRate).toInt()
                        if (clip.loop) {
                            srcIdx = ((srcIdx % samples.size) + samples.size) % samples.size
                        } else if (srcIdx !in samples.indices) {
                            continue
                        }
                        var env = 1.0f
                        if (fadeInSec > 0.0 && localSec < fadeInSec) {
                            env *= (localSec / fadeInSec).toFloat().coerceIn(0f, 1f)
                        }
                        val remain = clipDurSec - localSec
                        if (fadeOutSec > 0.0 && remain < fadeOutSec) {
                            env *= (remain / fadeOutSec).toFloat().coerceIn(0f, 1f)
                        }
                        mix[i] += (samples[srcIdx] / 32768f) * vol * env
                    }
                }

                val outPcm = ShortArray(totalSamples) { idx ->
                    (mix[idx].coerceIn(-0.98f, 0.98f) * 32767f).toInt().toShort()
                }

                val minBuf = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                ).coerceAtLeast(4096)

                val trackObj = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(minBuf)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                synchronized(this@StudioAudioEngine) {
                    activeTrack = trackObj
                }
                runCatching {
                    trackObj.play()
                    var offset = 0
                    val chunk = 2048
                    while (isActive && offset < outPcm.size) {
                        val count = min(chunk, outPcm.size - offset)
                        val written = trackObj.write(outPcm, offset, count)
                        if (written <= 0) break
                        offset += written
                    }
                }
            }
        }

        // Launch imported audio files (MP3/M4A/WAV/OGG) via MediaPlayer
        for (track in activeTracks) {
            for (clip in track.clips) {
                val asset = assets.find { it.id == clip.assetId } ?: continue
                if (asset.dataUrl.startsWith("procedural://")) continue
                val clipStartSec = clip.startFrame / safeFps
                val clipEndSec = (clip.startFrame + clip.durationFrames) / safeFps
                if (clipEndSec <= startSec) continue
                val delayMs = max(0L, ((clipStartSec - startSec) * 1000.0).toLong())
                val seekMs = max(0, ((clip.inFrame / safeFps + max(0.0, startSec - clipStartSec)) * 1000.0).toInt())
                val vol = (track.volume * clip.volume).coerceIn(0.0, 1.0).toFloat()

                scope.launch {
                    if (delayMs > 0) delay(delayMs)
                    if (!isActive) return@launch
                    runCatching {
                        val mp = MediaPlayer()
                        if (asset.dataUrl.startsWith("content://")) {
                            mp.setDataSource(context, Uri.parse(asset.dataUrl))
                        } else {
                            val filePath = asset.dataUrl.removePrefix("file://")
                            if (!File(filePath).exists()) {
                                mp.release()
                                return@launch
                            }
                            mp.setDataSource(filePath)
                        }
                        mp.isLooping = clip.loop
                        mp.setVolume(vol, vol)
                        mp.prepare()
                        if (seekMs > 0 && seekMs < mp.duration) mp.seekTo(seekMs)
                        synchronized(this@StudioAudioEngine) {
                            activePlayers += mp
                        }
                        mp.start()
                    }
                }
            }
        }
    }

    fun dispose() {
        stopAll()
        scope.cancel()
    }
}
