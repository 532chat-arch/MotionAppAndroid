package com.example.effects

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val LOW_COST_AUDIO_METADATA = EffectPerformanceMetadata(
    level = CostLevel.LOW,
    cpuCost = CostLevel.LOW,
    gpuCost = CostLevel.LOW,
    memoryCost = CostLevel.LOW,
    supportsLowEnd = true,
    fallbackEffect = null,
    renderingTechnique = "Precomputed Beat/Envelope Math (Zero Live Audio FFT Overhead)",
    maxRecommendedParticles = 0,
    textureRequirements = "None (pure 2D matrix + lightweight bar primitives)",
    knownLimitations = listOf("Uses BPM/phase envelope so scrubbing and export stay 100% deterministic"),
    qualityReductionOptions = listOf("Reduces equalizer bar count automatically on Low-End devices"),
)

/** 1. Beat Pulse (`fx.audio.beat_pulse`) */
class BeatPulseEffect : BaseMatrixEffect() {
    override val definition = EffectModuleDefinition(
        id = ID,
        name = "Beat Pulse",
        category = EffectCategory.AUDIO,
        phase = 5,
        description = "BPM-synced rhythmic scale and glow punch ideal for lyric videos and music promos.",
        targetTypes = ALL_LAYER_EFFECT_TARGETS,
        defaultDurationSec = 4.0,
        defaultLoop = true,
        params = listOf(
            EffectParamSpec("bpm", "Tempo (BPM)", 120.0, 60.0, 200.0, 2.0, " bpm"),
            EffectParamSpec("scalePunch", "Scale Punch", 0.14, 0.02, 0.45, 0.02),
            EffectParamSpec("sharpness", "Hit Sharpness", 3.2, 1.2, 7.0, 0.2, isAdvanced = true),
        ),
        performance = LOW_COST_AUDIO_METADATA,
    )

    override fun evaluate(context: EffectEvaluationContext, out: EffectTransformState) {
        val bpm = context.param("bpm", 120.0)
        val punch = context.param("scalePunch", 0.14) * context.intensity
        val sharp = context.param("sharpness", 3.2)
        val sample = AudioEnvelopeAnalyzer.sampleAt(context.frame, fps = 24.0, bpm = bpm, sharpness = sharp)
        out.dScaleX = sample.beatPulse01 * punch
        out.dScaleY = sample.beatPulse01 * punch
        out.audioFx = EvaluatedAudioFx(
            bpm = bpm.toFloat(),
            beatPulse01 = sample.beatPulse01.toFloat(),
            audioLevel01 = sample.envelope01.toFloat(),
            timeSec = (context.frame / 24.0).toFloat(),
        )
    }

    companion object {
        const val ID = "fx.audio.beat_pulse"
    }
}

/** 2. Audio Scale (`fx.audio.scale`) */
class AudioScaleEffect : BaseMatrixEffect() {
    override val definition = EffectModuleDefinition(
        id = ID,
        name = "Audio Scale",
        category = EffectCategory.AUDIO,
        phase = 5,
        description = "Smooth audio-envelope driven scale response with configurable sensitivity.",
        targetTypes = ALL_LAYER_EFFECT_TARGETS,
        defaultDurationSec = 4.0,
        defaultLoop = true,
        params = listOf(
            EffectParamSpec("bpm", "Tempo (BPM)", 120.0, 60.0, 200.0, 2.0, " bpm"),
            EffectParamSpec("sensitivity", "Sensitivity", 0.22, 0.02, 0.65, 0.02),
            EffectParamSpec("axis", "Axis", 0.0, 0.0, 2.0, 1.0, isAdvanced = true, enumLabels = mapOf(0 to "Both", 1 to "X", 2 to "Y")),
        ),
        performance = LOW_COST_AUDIO_METADATA,
    )

    override fun evaluate(context: EffectEvaluationContext, out: EffectTransformState) {
        val bpm = context.param("bpm", 120.0)
        val sens = context.param("sensitivity", 0.22) * context.intensity
        val axis = context.param("axis", 0.0).toInt()
        val sample = AudioEnvelopeAnalyzer.sampleAt(context.frame, fps = 24.0, bpm = bpm)
        val delta = sample.envelope01 * sens
        if (axis == 0 || axis == 1) out.dScaleX = delta
        if (axis == 0 || axis == 2) out.dScaleY = delta
        out.audioFx = EvaluatedAudioFx(
            bpm = bpm.toFloat(),
            beatPulse01 = sample.beatPulse01.toFloat(),
            audioLevel01 = sample.envelope01.toFloat(),
            timeSec = (context.frame / 24.0).toFloat(),
        )
    }

    companion object {
        const val ID = "fx.audio.scale"
    }
}

/** 3. Bass Shake (`fx.audio.bass_shake`) */
class BassShakeEffect : BaseMatrixEffect() {
    override val definition = EffectModuleDefinition(
        id = ID,
        name = "Bass Shake",
        category = EffectCategory.AUDIO,
        phase = 5,
        description = "Kick-drum triggered impact shake that settles cleanly between beats.",
        targetTypes = ALL_LAYER_EFFECT_TARGETS,
        defaultDurationSec = 4.0,
        defaultLoop = true,
        params = listOf(
            EffectParamSpec("bpm", "Tempo (BPM)", 120.0, 60.0, 200.0, 2.0, " bpm"),
            EffectParamSpec("strength", "Shake Force", 14.0, 2.0, 48.0, 1.0, "px"),
            EffectParamSpec("rotKick", "Rotational Kick", 2.0, 0.0, 12.0, 0.5, "°", isAdvanced = true),
        ),
        performance = LOW_COST_AUDIO_METADATA,
    )

    override fun evaluate(context: EffectEvaluationContext, out: EffectTransformState) {
        val bpm = context.param("bpm", 120.0)
        val force = context.param("strength", 14.0) * context.intensity
        val rotKick = context.param("rotKick", 2.0) * context.intensity
        val sample = AudioEnvelopeAnalyzer.sampleAt(context.frame, fps = 24.0, bpm = bpm)
        val impact = sample.bassImpact01
        val w = context.frame * 1.9
        out.dx = sin(w * PI) * force * impact
        out.dy = cos(w * 1.37 * PI) * force * 0.75 * impact
        out.dRotDeg = sin(w * 0.83 * PI) * rotKick * impact
    }

    companion object {
        const val ID = "fx.audio.bass_shake"
    }
}

/** 4. Audio Waveform (`fx.audio.waveform`) */
class AudioWaveformEffect : BaseMatrixEffect() {
    override val definition = EffectModuleDefinition(
        id = ID,
        name = "Audio Waveform",
        category = EffectCategory.AUDIO,
        phase = 5,
        description = "Lightweight 2D music visualizer bar spectrum rendered directly around the layer.",
        targetTypes = ALL_LAYER_EFFECT_TARGETS,
        defaultDurationSec = 6.0,
        defaultLoop = true,
        params = listOf(
            EffectParamSpec("bpm", "Tempo (BPM)", 120.0, 60.0, 200.0, 2.0, " bpm"),
            EffectParamSpec("bars", "Bar Count", 18.0, 6.0, 36.0, 2.0),
            EffectParamSpec("barHeight", "Max Height", 64.0, 16.0, 160.0, 4.0, "px"),
        ),
        performance = LOW_COST_AUDIO_METADATA,
    )

    override fun evaluate(context: EffectEvaluationContext, out: EffectTransformState) {
        val bpm = context.param("bpm", 120.0)
        val rawBars = context.param("bars", 18.0).toInt().coerceIn(6, 36)
        val bars = if (context.deviceProfile == DevicePerformanceProfile.LOW_END || context.reducedQuality) {
            minOf(rawBars, 12)
        } else rawBars
        val height = (context.param("barHeight", 64.0) * context.intensity).toFloat()
        val sample = AudioEnvelopeAnalyzer.sampleAt(context.frame, fps = 24.0, bpm = bpm)
        out.audioFx = EvaluatedAudioFx(
            bpm = bpm.toFloat(),
            beatPulse01 = sample.beatPulse01.toFloat(),
            audioLevel01 = sample.envelope01.toFloat(),
            showWaveform = true,
            waveformBars = bars,
            waveformHeight = height,
            timeSec = (context.frame / 24.0).toFloat(),
        )
    }

    companion object {
        const val ID = "fx.audio.waveform"
    }
}

/** 5. Audio Beat Markers (`fx.audio.beat_markers`) */
class AudioBeatMarkersEffect : BaseMatrixEffect() {
    override val definition = EffectModuleDefinition(
        id = ID,
        name = "Audio Beat Markers",
        category = EffectCategory.AUDIO,
        phase = 5,
        description = "Visual beat-synced pulse ring and rhythm flash indicators aligned to project BPM.",
        targetTypes = ALL_LAYER_EFFECT_TARGETS,
        defaultDurationSec = 6.0,
        defaultLoop = true,
        params = listOf(
            EffectParamSpec("bpm", "Tempo (BPM)", 120.0, 60.0, 200.0, 2.0, " bpm"),
            EffectParamSpec("ringScale", "Pulse Boost", 0.08, 0.0, 0.25, 0.01, isAdvanced = true),
        ),
        performance = LOW_COST_AUDIO_METADATA,
    )

    override fun evaluate(context: EffectEvaluationContext, out: EffectTransformState) {
        val bpm = context.param("bpm", 120.0)
        val boost = context.param("ringScale", 0.08) * context.intensity
        val sample = AudioEnvelopeAnalyzer.sampleAt(context.frame, fps = 24.0, bpm = bpm, sharpness = 4.0)
        out.dScaleX = sample.beatPulse01 * boost
        out.dScaleY = sample.beatPulse01 * boost
        out.audioFx = EvaluatedAudioFx(
            bpm = bpm.toFloat(),
            beatPulse01 = sample.beatPulse01.toFloat(),
            audioLevel01 = sample.envelope01.toFloat(),
            showBeatMarkers = true,
            timeSec = (context.frame / 24.0).toFloat(),
        )
    }

    companion object {
        const val ID = "fx.audio.beat_markers"
    }
}

val PHASE5_AUDIO_EFFECTS: List<Effect> = listOf(
    BeatPulseEffect(),
    AudioScaleEffect(),
    BassShakeEffect(),
    AudioWaveformEffect(),
    AudioBeatMarkersEffect(),
)
