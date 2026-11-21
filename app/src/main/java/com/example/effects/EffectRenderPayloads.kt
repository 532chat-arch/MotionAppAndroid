package com.example.effects

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * Aggregated lightweight visual, transition-clip, and character-overlay parameters
 * evaluated for a layer at a single frame.
 */
data class EvaluatedVisualFx(
    val blurRadius: Float = 0f,
    val shadowOffsetX: Float = 0f,
    val shadowOffsetY: Float = 0f,
    val shadowRadius: Float = 0f,
    val shadowAlpha: Float = 0f,
    val shadowColorInt: Int = 0xFF000000.toInt(),
    val outlineWidth: Float = 0f,
    val outlineColorInt: Int = 0xFFFFFFFF.toInt(),
    val glowRadius: Float = 0f,
    val glowIntensity: Float = 0f,
    val glowColorInt: Int = 0xFF60A5FA.toInt(),
    val brightness: Float = 0f, // -1f..1f
    val contrast: Float = 1f,   // 0.2f..2.5f
    val saturation: Float = 1f, // 0f..2.5f
    val vignetteIntensity: Float = 0f,
    val vignetteRadius: Float = 0.72f,
    val pixelateBlockSize: Float = 0f,
    val filmGrainIntensity: Float = 0f,
    val filmGrainSeed: Int = 0,
    val wipeDirection: Int = -1, // -1=none, 0=L->R, 1=R->L, 2=T->B, 3=B->T
    val wipeProgress: Float = 1f,
    val irisProgress: Float = -1f, // -1=none, 0..1
    val dissolveProgress: Float = -1f, // -1=none, 0..1
    val dissolveBlockSize: Float = 16f,
    val eyeBlinkClose: Float = 0f, // 0=open, 1=closed
    val eyeLookX: Float = 0f,      // -1..1
    val eyeLookY: Float = 0f,      // -1..1
    val hasEyeLook: Boolean = false,
    val mouthShapeIndex: Int = -1, // -1=none, 0=closed, 1=Ah, 2=Oh, 3=Ee, 4=Smile
) {
    fun hasColorMatrix(): Boolean =
        abs(brightness) > 0.01f || abs(contrast - 1f) > 0.01f || abs(saturation - 1f) > 0.01f

    fun mergeWith(other: EvaluatedVisualFx): EvaluatedVisualFx = EvaluatedVisualFx(
        blurRadius = max(blurRadius, other.blurRadius),
        shadowOffsetX = if (other.shadowAlpha > 0f) other.shadowOffsetX else shadowOffsetX,
        shadowOffsetY = if (other.shadowAlpha > 0f) other.shadowOffsetY else shadowOffsetY,
        shadowRadius = max(shadowRadius, other.shadowRadius),
        shadowAlpha = max(shadowAlpha, other.shadowAlpha),
        shadowColorInt = if (other.shadowAlpha > 0f) other.shadowColorInt else shadowColorInt,
        outlineWidth = max(outlineWidth, other.outlineWidth),
        outlineColorInt = if (other.outlineWidth > 0f) other.outlineColorInt else outlineColorInt,
        glowRadius = max(glowRadius, other.glowRadius),
        glowIntensity = max(glowIntensity, other.glowIntensity),
        glowColorInt = if (other.glowIntensity > 0f) other.glowColorInt else glowColorInt,
        brightness = (brightness + other.brightness).coerceIn(-1f, 1f),
        contrast = (contrast * other.contrast).coerceIn(0.2f, 2.5f),
        saturation = (saturation * other.saturation).coerceIn(0f, 2.5f),
        vignetteIntensity = max(vignetteIntensity, other.vignetteIntensity),
        vignetteRadius = if (other.vignetteIntensity > 0f) other.vignetteRadius else vignetteRadius,
        pixelateBlockSize = max(pixelateBlockSize, other.pixelateBlockSize),
        filmGrainIntensity = max(filmGrainIntensity, other.filmGrainIntensity),
        filmGrainSeed = if (other.filmGrainIntensity > 0f) other.filmGrainSeed else filmGrainSeed,
        wipeDirection = if (other.wipeDirection >= 0) other.wipeDirection else wipeDirection,
        wipeProgress = if (other.wipeDirection >= 0) min(wipeProgress, other.wipeProgress) else wipeProgress,
        irisProgress = when {
            irisProgress < 0f -> other.irisProgress
            other.irisProgress < 0f -> irisProgress
            else -> min(irisProgress, other.irisProgress)
        },
        dissolveProgress = when {
            dissolveProgress < 0f -> other.dissolveProgress
            other.dissolveProgress < 0f -> dissolveProgress
            else -> min(dissolveProgress, other.dissolveProgress)
        },
        dissolveBlockSize = if (other.dissolveProgress >= 0f) other.dissolveBlockSize else dissolveBlockSize,
        eyeBlinkClose = max(eyeBlinkClose, other.eyeBlinkClose),
        eyeLookX = if (other.hasEyeLook) other.eyeLookX else eyeLookX,
        eyeLookY = if (other.hasEyeLook) other.eyeLookY else eyeLookY,
        hasEyeLook = hasEyeLook || other.hasEyeLook,
        mouthShapeIndex = if (other.mouthShapeIndex >= 0) other.mouthShapeIndex else mouthShapeIndex,
    )
}

enum class ParticleEffectKind {
    RAIN,
    SNOW,
    DUST,
    FIREFLIES,
    SMOKE,
    FALLING_LEAVES,
}

/**
 * Lightweight descriptor emitted by Phase 4 particle effects.
 * Particles are evaluated deterministically into a reused [ParticlePool] at draw time.
 */
data class EvaluatedParticleLayerFx(
    val kind: ParticleEffectKind,
    val count: Int,
    val speed: Float,
    val size: Float,
    val wind: Float,
    val spreadWidth: Float,
    val spreadHeight: Float,
    val seed: Int,
    val colorInt: Int,
    val opacity: Float,
    val timeSec: Float,
)

/**
 * Evaluated Phase 5 kinetic typography parameters applied to TextObject layers.
 */
data class EvaluatedTextFx(
    val typewriterReveal01: Float = 1f,
    val showCursor: Boolean = false,
    val charReveal01: Float = 1f,
    val wordReveal01: Float = 1f,
    val waveAmplitude: Float = 0f,
    val waveFrequency: Float = 2f,
    val wavePhase: Float = 0f,
    val trackingPx: Float = 0f,
    val charBounceAmplitude: Float = 0f,
    val charBouncePhase: Float = 0f,
    val charRotationDeg: Float = 0f,
    val charScaleBoost: Float = 0f,
) {
    fun mergeWith(other: EvaluatedTextFx): EvaluatedTextFx = EvaluatedTextFx(
        typewriterReveal01 = min(typewriterReveal01, other.typewriterReveal01),
        showCursor = showCursor || other.showCursor,
        charReveal01 = min(charReveal01, other.charReveal01),
        wordReveal01 = min(wordReveal01, other.wordReveal01),
        waveAmplitude = waveAmplitude + other.waveAmplitude,
        waveFrequency = if (other.waveAmplitude > 0f) other.waveFrequency else waveFrequency,
        wavePhase = if (other.waveAmplitude > 0f) other.wavePhase else wavePhase,
        trackingPx = trackingPx + other.trackingPx,
        charBounceAmplitude = charBounceAmplitude + other.charBounceAmplitude,
        charBouncePhase = if (other.charBounceAmplitude > 0f) other.charBouncePhase else charBouncePhase,
        charRotationDeg = charRotationDeg + other.charRotationDeg,
        charScaleBoost = charScaleBoost + other.charScaleBoost,
    )
}

/**
 * Evaluated Phase 5 audio-reactive visualization & beat marker parameters.
 */
data class EvaluatedAudioFx(
    val bpm: Float = 120f,
    val beatPulse01: Float = 0f,
    val audioLevel01: Float = 0f,
    val showWaveform: Boolean = false,
    val waveformBars: Int = 20,
    val waveformHeight: Float = 56f,
    val waveformColorInt: Int = 0xFF38BDF8.toInt(),
    val showBeatMarkers: Boolean = false,
    val timeSec: Float = 0f,
) {
    fun mergeWith(other: EvaluatedAudioFx): EvaluatedAudioFx = EvaluatedAudioFx(
        bpm = other.bpm,
        beatPulse01 = max(beatPulse01, other.beatPulse01),
        audioLevel01 = max(audioLevel01, other.audioLevel01),
        showWaveform = showWaveform || other.showWaveform,
        waveformBars = if (other.showWaveform) other.waveformBars else waveformBars,
        waveformHeight = if (other.showWaveform) other.waveformHeight else waveformHeight,
        waveformColorInt = if (other.showWaveform) other.waveformColorInt else waveformColorInt,
        showBeatMarkers = showBeatMarkers || other.showBeatMarkers,
        timeSec = other.timeSec,
    )
}

/**
 * Reusable pooled particle element — zero per-frame object allocation.
 */
class PooledParticle {
    var x: Float = 0f
    var y: Float = 0f
    var vx: Float = 0f
    var vy: Float = 0f
    var size: Float = 4f
    var alpha: Float = 1f
    var rotationDeg: Float = 0f
    var life01: Float = 0f
}

/**
 * Fixed-capacity particle pool with deterministic seeded evaluation.
 * Reuses pre-allocated [PooledParticle] objects across every frame and respects
 * [DevicePerformanceProfile] and adaptive preview limits.
 */
class ParticlePool(val capacity: Int = MAX_POOL_CAPACITY) {
    private val pool: Array<PooledParticle> = Array(capacity) { PooledParticle() }
    var activeCount: Int = 0
        private set

    fun get(index: Int): PooledParticle = pool[index]

    private fun hash01(seed: Int): Float {
        val v = sin(seed.toDouble() * 127.1 + 311.7) * 43758.5453123
        return (v - floor(v)).toFloat()
    }

    /**
     * Populates active particles deterministically for [fx] with zero heap allocations.
     * Returns the exact number of active particles capped by [maxAllowed] and [capacity].
     */
    fun populateDeterministic(
        fx: EvaluatedParticleLayerFx,
        particleScale: Float = 1.0f,
        maxAllowed: Int = capacity,
    ): Int {
        val desired = (fx.count * particleScale).toInt().coerceAtLeast(1)
        val count = desired.coerceIn(0, min(capacity, maxAllowed))
        activeCount = count
        val halfW = fx.spreadWidth * 0.5f
        val halfH = fx.spreadHeight * 0.5f
        val t = fx.timeSec
        val seedBase = fx.seed * 97

        for (i in 0 until count) {
            val p = pool[i]
            val r0 = hash01(seedBase + i * 11 + 1)
            val r1 = hash01(seedBase + i * 17 + 3)
            val r2 = hash01(seedBase + i * 23 + 7)
            val r3 = hash01(seedBase + i * 31 + 13)

            when (fx.kind) {
                ParticleEffectKind.RAIN -> {
                    val fallSpeed = (180f + r2 * 220f) * fx.speed
                    val cycleY = ((r1 * fx.spreadHeight + t * fallSpeed) % fx.spreadHeight + fx.spreadHeight) % fx.spreadHeight
                    val driftX = fx.wind * cycleY * 0.35f
                    val rawX = ((r0 * fx.spreadWidth + driftX) % fx.spreadWidth + fx.spreadWidth) % fx.spreadWidth
                    p.x = rawX - halfW
                    p.y = cycleY - halfH
                    p.vx = fx.wind * 18f
                    p.vy = (14f + r2 * 16f) * fx.size
                    p.size = (1.2f + r3 * 1.1f) * fx.size
                    p.alpha = (0.35f + r2 * 0.55f) * fx.opacity
                    p.rotationDeg = 0f
                    p.life01 = cycleY / max(1f, fx.spreadHeight)
                }
                ParticleEffectKind.SNOW -> {
                    val fallSpeed = (28f + r2 * 45f) * fx.speed
                    val cycleY = ((r1 * fx.spreadHeight + t * fallSpeed) % fx.spreadHeight + fx.spreadHeight) % fx.spreadHeight
                    val sway = sin((t * (1.2f + r3 * 1.5f) + r0 * 6.28f).toDouble()).toFloat() * 18f
                    val rawX = ((r0 * fx.spreadWidth + t * fx.wind * 25f + sway) % fx.spreadWidth + fx.spreadWidth) % fx.spreadWidth
                    p.x = rawX - halfW
                    p.y = cycleY - halfH
                    p.vx = sway
                    p.vy = fallSpeed
                    p.size = (2.2f + r2 * 4.5f) * fx.size
                    p.alpha = (0.45f + r3 * 0.5f) * fx.opacity
                    p.rotationDeg = t * 25f * (r3 - 0.5f)
                    p.life01 = cycleY / max(1f, fx.spreadHeight)
                }
                ParticleEffectKind.DUST -> {
                    val angle = t * (0.4f + r2 * 0.6f) * fx.speed + r0 * 6.283f
                    val driftX = cos(angle.toDouble()).toFloat() * (14f + r3 * 22f) + fx.wind * t * 12f
                    val driftY = sin((angle * 0.85f).toDouble()).toFloat() * (10f + r1 * 18f)
                    val rawX = ((r0 * fx.spreadWidth + driftX) % fx.spreadWidth + fx.spreadWidth) % fx.spreadWidth
                    val rawY = ((r1 * fx.spreadHeight + driftY) % fx.spreadHeight + fx.spreadHeight) % fx.spreadHeight
                    p.x = rawX - halfW
                    p.y = rawY - halfH
                    p.vx = 0f
                    p.vy = 0f
                    p.size = (1.5f + r2 * 3.0f) * fx.size
                    val twinkle = 0.45f + 0.55f * sin((t * (1.5f + r3 * 2f) + r0 * 9f).toDouble()).toFloat()
                    p.alpha = twinkle.coerceIn(0.15f, 1f) * fx.opacity
                    p.rotationDeg = 0f
                    p.life01 = twinkle
                }
                ParticleEffectKind.FIREFLIES -> {
                    val w = t * (0.8f + r2 * 0.9f) * fx.speed + r0 * 6.283f
                    p.x = (r0 - 0.5f) * fx.spreadWidth + sin(w.toDouble()).toFloat() * (22f + r3 * 36f)
                    p.y = (r1 - 0.5f) * fx.spreadHeight + cos((w * 0.77f).toDouble()).toFloat() * (16f + r2 * 26f)
                    p.vx = 0f
                    p.vy = 0f
                    p.size = (2.8f + r2 * 3.8f) * fx.size
                    val pulse = 0.25f + 0.75f * (0.5f + 0.5f * sin((t * (2.2f + r3 * 2.5f) + r1 * 12f).toDouble()).toFloat())
                    p.alpha = pulse * fx.opacity
                    p.rotationDeg = 0f
                    p.life01 = pulse
                }
                ParticleEffectKind.SMOKE -> {
                    val lifeSpan = 2.4f + r2 * 1.8f
                    val rawAge = ((t * fx.speed + r1 * lifeSpan) % lifeSpan + lifeSpan) % lifeSpan
                    val life = rawAge / lifeSpan
                    val rise = life * fx.spreadHeight * 0.85f
                    val curl = sin((life * 5.0f + r0 * 6.28f).toDouble()).toFloat() * (10f + life * 24f) + fx.wind * life * 45f
                    p.x = (r0 - 0.5f) * fx.spreadWidth * 0.35f + curl
                    p.y = halfH * 0.5f - rise
                    p.vx = fx.wind
                    p.vy = -rise
                    p.size = (10f + r3 * 12f + life * 26f) * fx.size
                    // Smooth fade-in then fade-out envelope
                    val env = sin((life * PI).toDouble()).toFloat().coerceIn(0f, 1f)
                    p.alpha = env * 0.52f * fx.opacity
                    p.rotationDeg = life * 45f * (if (r3 > 0.5f) 1f else -1f)
                    p.life01 = life
                }
                ParticleEffectKind.FALLING_LEAVES -> {
                    val fallSpeed = (34f + r2 * 40f) * fx.speed
                    val cycleY = ((r1 * fx.spreadHeight + t * fallSpeed) % fx.spreadHeight + fx.spreadHeight) % fx.spreadHeight
                    val swing = sin((t * (1.8f + r3 * 1.4f) + r0 * 6.28f).toDouble()).toFloat() * 34f
                    val rawX = ((r0 * fx.spreadWidth + t * fx.wind * 42f + swing) % fx.spreadWidth + fx.spreadWidth) % fx.spreadWidth
                    p.x = rawX - halfW
                    p.y = cycleY - halfH
                    p.vx = swing
                    p.vy = fallSpeed
                    p.size = (6f + r2 * 6f) * fx.size
                    p.alpha = (0.72f + r3 * 0.28f) * fx.opacity
                    p.rotationDeg = (r0 * 360f + t * (55f + r3 * 65f) * (if (r2 > 0.5f) 1f else -1f)) % 360f
                    p.life01 = cycleY / max(1f, fx.spreadHeight)
                }
            }
        }
        return count
    }

    companion object {
        const val MAX_POOL_CAPACITY = 180
    }
}

/**
 * Lightweight precomputed audio-beat & envelope evaluator.
 * Avoids continuous real-time FFT processing on low-end tablets while giving deterministic,
 * scrubbable beat pulses, bass hits, and multi-band bar heights synchronized to project BPM.
 */
object AudioEnvelopeAnalyzer {
    data class AudioSample(
        val beatPhase01: Double,
        val beatPulse01: Double,
        val bassImpact01: Double,
        val envelope01: Double,
        val isBeatFrame: Boolean,
    )

    fun sampleAt(
        frame: Double,
        fps: Double = 24.0,
        bpm: Double = 120.0,
        phaseOffsetBeats: Double = 0.0,
        sharpness: Double = 3.2,
    ): AudioSample {
        val safeFps = max(1.0, fps)
        val safeBpm = bpm.coerceIn(40.0, 240.0)
        val timeSec = frame / safeFps
        val beats = timeSec * (safeBpm / 60.0) + phaseOffsetBeats
        val beatFrac = beats - floor(beats)
        val pulse = (1.0 - beatFrac).pow(sharpness).coerceIn(0.0, 1.0)
        val bass = exp(-beatFrac * 7.5).coerceIn(0.0, 1.0)
        // Smooth multi-harmonic synthetic envelope matching musical bar dynamics
        val barWave = 0.55 + 0.25 * sin(beats * PI * 0.5) + 0.20 * cos(beats * PI * 1.5)
        val env = (0.30 * barWave + 0.70 * pulse).coerceIn(0.0, 1.0)
        val isBeat = beatFrac < (safeBpm / 60.0) / safeFps * 1.35
        return AudioSample(
            beatPhase01 = beatFrac,
            beatPulse01 = pulse,
            bassImpact01 = bass,
            envelope01 = env,
            isBeatFrame = isBeat,
        )
    }

    /** Computes deterministic normalized [0..1] bar magnitude for equalizer bar [barIndex]. */
    fun barMagnitude(barIndex: Int, totalBars: Int, timeSec: Float, bpm: Float): Float {
        val norm = (barIndex + 0.5f) / max(1, totalBars).toFloat()
        val beats = timeSec * (bpm / 60f)
        val beatFrac = beats - floor(beats.toDouble()).toFloat()
        val kick = exp((-beatFrac * 5.5f).toDouble()).toFloat()
        // Lower bars react more to bass kick; mid/high bars shimmer rhythmically
        val bassWeight = (1f - norm).pow(1.4f)
        val midWave = 0.5f + 0.5f * sin((beats * 3.14159f * (1f + norm * 2.5f) + barIndex * 0.75f).toDouble()).toFloat()
        val hiWave = 0.5f + 0.5f * cos((beats * 6.28318f * (0.5f + norm) - barIndex * 1.1f).toDouble()).toFloat()
        return (kick * bassWeight * 0.85f + midWave * 0.42f + hiWave * 0.22f).coerceIn(0.08f, 1.0f)
    }
}
