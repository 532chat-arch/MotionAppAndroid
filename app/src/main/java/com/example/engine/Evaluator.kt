package com.example.engine

import com.example.effects.EffectEvaluationContext
import com.example.effects.EffectRegistry
import com.example.effects.EffectTransformState
import com.example.effects.EvaluatedAudioFx
import com.example.effects.EvaluatedParticleLayerFx
import com.example.effects.EvaluatedTextFx
import com.example.effects.EvaluatedVisualFx
import com.example.schema.BlendModeType
import com.example.schema.CameraDocument
import com.example.schema.CompositionRecord
import com.example.schema.EnvironmentDocument
import com.example.schema.InterpolationType
import com.example.schema.Keyframe
import com.example.schema.LayerDocument
import com.example.schema.LayerRole
import com.example.schema.LyricsConfig
import com.example.schema.LyricsCue
import com.example.schema.LyricsStyle
import com.example.schema.MotionModifierBlendMode
import com.example.schema.MotionModifierDirection
import com.example.schema.MotionModifierInstance
import com.example.schema.MotionProject
import com.example.schema.PivotAnchorPreset
import com.example.schema.SceneObjectDocument
import com.example.schema.TransitionType
import com.example.schema.fpsToDouble
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

data class EvaluatedMeshDeform(
    val waveAxis: String = "horizontal", // "horizontal" or "vertical"
    val waveAmplitude: Double = 0.0,
    val waveFrequency: Double = 1.5,
    val wavePhase: Double = 0.0,
    val pinEdge: String = "top", // "top", "bottom", "left", "right"
    val skewXDeg: Double = 0.0,
    val skewYDeg: Double = 0.0,
    val isStrandFlow: Boolean = false,
    val strandCount: Int = 16,
    val strandVariance: Double = 0.7,
    val windBias: Double = 0.0,
    val turbulence: Double = 0.35,
    val tipLift: Double = 0.3,
)

data class EvaluatedTransform(
    val x: Double,
    val y: Double,
    val scaleX: Double,
    val scaleY: Double,
    val rotationDeg: Double,
    val opacity: Double,
    val skewXDeg: Double = 0.0,
    val skewYDeg: Double = 0.0,
    val flipX: Boolean = false,
    val flipY: Boolean = false,
    val anchorOffsetX: Double = 0.0,
    val anchorOffsetY: Double = 0.0,
)

data class EvaluatedCamera(
    val x: Double,
    val y: Double,
    val zoom: Double,
    val rotationDeg: Double,
    val parallaxStrength: Double,
)

data class EvaluatedLyricsWord(
    val text: String,
    val progress: Double, // 0..1 highlight progress
    val revealAlpha: Double,
    val offsetY: Double,
    val scale: Double,
)

data class EvaluatedLyricsState(
    val cue: LyricsCue?,
    val visibleText: String,
    val fullText: String,
    val cueProgress: Double,
    val opacity: Double,
    val offsetY: Double,
    val scale: Double,
    val glowBoost: Double,
    val beatPulse: Double,
    val words: List<EvaluatedLyricsWord>,
)

data class EvaluatedLayerState(
    val layer: LayerDocument,
    val isActiveAtFrame: Boolean,
    val localTransform: EvaluatedTransform,
    val worldTransform: EvaluatedTransform,
    val blendMode: BlendModeType,
    val depth: Double,
    val role: LayerRole,
    val meshDeform: EvaluatedMeshDeform?,
    val characterFrameIndex: Int?,
    val lyricsState: EvaluatedLyricsState?,
    val visualFx: EvaluatedVisualFx? = null,
    val particleFxList: List<EvaluatedParticleLayerFx> = emptyList(),
    val textFx: EvaluatedTextFx? = null,
    val audioFx: EvaluatedAudioFx? = null,
)

data class EvaluatedCompositionState(
    val composition: CompositionRecord,
    val frame: Int,
    val fps: Double,
    val camera: EvaluatedCamera,
    val environment: EnvironmentDocument,
    val layers: List<EvaluatedLayerState>,
)

data class EvaluatedMovieState(
    val primary: EvaluatedCompositionState?,
    val outgoing: EvaluatedCompositionState?,
    val transitionType: TransitionType,
    val transitionProgress: Double,
)

fun applyEasing(t: Double, mode: InterpolationType): Double {
    val c = t.coerceIn(0.0, 1.0)
    return when (mode) {
        InterpolationType.LINEAR -> c
        InterpolationType.HOLD -> 0.0
        InterpolationType.EASE_IN -> c * c * c
        InterpolationType.EASE_OUT -> 1.0 - (1.0 - c) * (1.0 - c) * (1.0 - c)
        InterpolationType.EASE_IN_OUT ->
            if (c < 0.5) 4.0 * c * c * c
            else 1.0 - ((-2.0 * c + 2.0) * (-2.0 * c + 2.0) * (-2.0 * c + 2.0)) / 2.0
    }
}

fun evaluateKeyframes(
    keyframes: List<Keyframe>,
    frame: Double,
    fallback: Double,
): Double {
    if (keyframes.isEmpty()) return fallback
    val sorted = if (keyframes.size <= 1) keyframes else keyframes.sortedBy { it.frame }
    if (frame <= sorted.first().frame) return sorted.first().value
    if (frame >= sorted.last().frame) return sorted.last().value

    for (i in 0 until sorted.lastIndex) {
        val k0 = sorted[i]
        val k1 = sorted[i + 1]
        if (frame >= k0.frame && frame <= k1.frame) {
            val span = k1.frame - k0.frame
            if (span <= 0 || k0.interpolation == InterpolationType.HOLD) return k0.value
            val rawT = (frame - k0.frame) / span.toDouble()
            val eased = applyEasing(rawT, k0.interpolation)
            return k0.value + (k1.value - k0.value) * eased
        }
    }
    return sorted.last().value
}

private fun resolveModifierProgress(
    modifier: MotionModifierInstance,
    frame: Double,
): Double? {
    if (!modifier.enabled) return null
    val effectiveStart = modifier.startFrame + modifier.delayFrames
    val rel = (frame - effectiveStart) * max(0.05, modifier.speed)
    if (rel < 0.0) return null

    val dur = max(1, modifier.durationFrames).toDouble()
    val rawCycle = rel / dur + modifier.phaseOffset
    if (!modifier.loop && rel > dur) return 1.0

    val norm = if (modifier.loop) {
        val mod = rawCycle - floor(rawCycle)
        if (mod < 0.0) mod + 1.0 else mod
    } else {
        rawCycle.coerceIn(0.0, 1.0)
    }

    return when (modifier.direction) {
        MotionModifierDirection.FORWARD -> norm
        MotionModifierDirection.REVERSE -> 1.0 - norm
        MotionModifierDirection.PINGPONG -> {
            val cycleIdx = floor(rawCycle).toInt()
            val p = rawCycle - floor(rawCycle)
            if (cycleIdx % 2 == 0) p else 1.0 - p
        }
    }
}

private fun pivotAnchorNormalized(preset: PivotAnchorPreset): Pair<Double, Double> = when (preset) {
    PivotAnchorPreset.CENTER -> 0.5 to 0.5
    PivotAnchorPreset.TOP -> 0.5 to 0.0
    PivotAnchorPreset.BOTTOM -> 0.5 to 1.0
    PivotAnchorPreset.LEFT -> 0.0 to 0.5
    PivotAnchorPreset.RIGHT -> 1.0 to 0.5
    PivotAnchorPreset.TOP_LEFT -> 0.0 to 0.0
    PivotAnchorPreset.TOP_RIGHT -> 1.0 to 0.0
    PivotAnchorPreset.BOTTOM_LEFT -> 0.0 to 1.0
    PivotAnchorPreset.BOTTOM_RIGHT -> 1.0 to 1.0
}

private fun deterministicHash(seed: Int): Double {
    var x = sin(seed.toDouble() * 12.9898 + 78.233) * 43758.5453
    x -= floor(x)
    return x
}

data class LayerModifierResult(
    val transform: EvaluatedTransform,
    val meshDeform: EvaluatedMeshDeform?,
    val visualFx: EvaluatedVisualFx? = null,
    val particleFxList: List<EvaluatedParticleLayerFx> = emptyList(),
    val textFx: EvaluatedTextFx? = null,
    val audioFx: EvaluatedAudioFx? = null,
)

fun applyLayerModifiers(
    base: EvaluatedTransform,
    modifiers: List<MotionModifierInstance>,
    frame: Double,
    objWidth: Double,
    objHeight: Double,
): LayerModifierResult {
    if (modifiers.isEmpty()) return LayerModifierResult(base, null)

    var x = base.x
    var y = base.y
    var scaleX = base.scaleX
    var scaleY = base.scaleY
    var rot = base.rotationDeg
    var opacity = base.opacity
    var skewXDeg = base.skewXDeg
    var skewYDeg = base.skewYDeg
    var flipX = base.flipX
    var flipY = base.flipY
    var anchorOffsetX = base.anchorOffsetX
    var anchorOffsetY = base.anchorOffsetY
    var mesh: EvaluatedMeshDeform? = null
    var visualFx: EvaluatedVisualFx? = null
    var particleFxList: MutableList<EvaluatedParticleLayerFx>? = null
    var textFx: EvaluatedTextFx? = null
    var audioFx: EvaluatedAudioFx? = null
    val effectContext = EffectEvaluationContext()
    val effectState = EffectTransformState()

    for (mod in modifiers) {
        val t = resolveModifierProgress(mod, frame) ?: continue
        val intensity = mod.intensity
        val kernelId = mod.basePresetId ?: mod.presetId
        val p = mod.params
        val phase = t * 2.0 * PI

        var dx = 0.0
        var dy = 0.0
        var dScaleX = 0.0
        var dScaleY = 0.0
        var dRot = 0.0
        var dOpacity = 0.0
        var modMesh: EvaluatedMeshDeform? = null

        effectContext.set(
            modifier = mod,
            normalizedTime = t,
            frame = frame,
            objWidth = objWidth,
            objHeight = objHeight,
        )
        val handledByRegistry = EffectRegistry.default.evaluate(kernelId, effectContext, effectState)
        if (handledByRegistry) {
            dx = effectState.dx
            dy = effectState.dy
            dScaleX = effectState.dScaleX
            dScaleY = effectState.dScaleY
            dRot = effectState.dRotDeg
            dOpacity = effectState.dOpacity
            skewXDeg += effectState.dSkewXDeg
            skewYDeg += effectState.dSkewYDeg
            if (effectState.flipX) flipX = !flipX
            if (effectState.flipY) flipY = !flipY
            anchorOffsetX += effectState.anchorOffsetX
            anchorOffsetY += effectState.anchorOffsetY
            effectState.overrideOpacity?.let { ov ->
                opacity = (opacity * ov).coerceIn(0.0, 1.0)
            }
            effectState.meshDeform?.let { modMesh = it }
            effectState.visualFx?.let { v ->
                visualFx = visualFx?.mergeWith(v) ?: v
            }
            effectState.particleFx?.let { pf ->
                if (particleFxList == null) particleFxList = mutableListOf()
                particleFxList!!.add(pf)
            }
            effectState.textFx?.let { tf ->
                textFx = textFx?.mergeWith(tf) ?: tf
            }
            effectState.audioFx?.let { af ->
                audioFx = audioFx?.mergeWith(af) ?: af
            }
        } else when (kernelId) {
            "hair.soft-wind" -> {
                val amp = (p["waveAmplitude"] ?: 11.0) * intensity
                val freq = p["waveFrequency"] ?: 1.65
                val rotStr = (p["rotationStrength"] ?: 2.0) * intensity
                val separation = (p["strandSeparation"] ?: 0.72).coerceIn(0.0, 1.5)
                val turb = (p["turbulence"] ?: 0.36).coerceIn(0.0, 1.5)
                // Keep scalp anchored; use subtle root give and let multi-strand 2D mesh drive organic flow
                dRot = (sin(phase) * 0.3 + sin(phase * 2.0 + 0.9) * 0.12) * rotStr
                val windGust = 0.55 + 0.32 * sin(phase - 0.35) + 0.13 * sin(phase * 2.0 + 1.1)
                modMesh = EvaluatedMeshDeform(
                    waveAxis = "horizontal",
                    waveAmplitude = amp,
                    waveFrequency = freq,
                    wavePhase = phase,
                    pinEdge = "top",
                    skewXDeg = rotStr * 0.35 * sin(phase - 0.25),
                    skewYDeg = 0.0,
                    isStrandFlow = true,
                    strandCount = 16,
                    strandVariance = separation,
                    windBias = amp * 0.58 * windGust,
                    turbulence = turb,
                    tipLift = 0.30,
                )
            }
            "hair.strong-wind" -> {
                val amp = (p["waveAmplitude"] ?: 24.0) * intensity
                val freq = p["waveFrequency"] ?: 2.35
                val rotStr = (p["rotationStrength"] ?: 6.0) * intensity
                val dir = if ((p["windDir"] ?: 1.0) >= 0.0) 1.0 else -1.0
                val separation = (p["strandSeparation"] ?: 0.90).coerceIn(0.0, 1.5)
                val turb = (p["turbulence"] ?: 0.68).coerceIn(0.0, 1.5)
                val gust = 0.68 + 0.24 * sin(phase) + 0.16 * sin(phase * 3.0 + 0.55)
                // Minimal root tilt so hair stays attached to head while tips whip dramatically downwind
                dRot = dir * rotStr * 0.18 * gust
                modMesh = EvaluatedMeshDeform(
                    waveAxis = "horizontal",
                    waveAmplitude = amp * dir,
                    waveFrequency = freq,
                    wavePhase = phase * 1.6,
                    pinEdge = "top",
                    skewXDeg = dir * rotStr * 0.55 * gust,
                    skewYDeg = 0.0,
                    isStrandFlow = true,
                    strandCount = 18,
                    strandVariance = separation,
                    windBias = dir * amp * 1.15 * gust,
                    turbulence = turb,
                    tipLift = 0.44,
                )
            }
            "hair.gentle-sway" -> {
                val rotStr = (p["rotationStrength"] ?: 3.5) * intensity
                val amp = (p["waveAmplitude"] ?: 7.5) * intensity
                val separation = (p["strandSeparation"] ?: 0.58).coerceIn(0.0, 1.5)
                dRot = sin(phase) * rotStr * 0.25
                modMesh = EvaluatedMeshDeform(
                    waveAxis = "horizontal",
                    waveAmplitude = amp,
                    waveFrequency = 1.25,
                    wavePhase = phase,
                    pinEdge = "top",
                    skewXDeg = sin(phase - 0.3) * rotStr * 0.4,
                    skewYDeg = 0.0,
                    isStrandFlow = true,
                    strandCount = 14,
                    strandVariance = separation,
                    windBias = sin(phase) * amp * 0.28,
                    turbulence = 0.20,
                    tipLift = 0.22,
                )
            }
            "hair.floating-strand" -> {
                val amp = (p["waveAmplitude"] ?: 16.0) * intensity
                val freq = p["waveFrequency"] ?: 1.35
                val lift = (p["movementStrength"] ?: 8.0) * intensity
                val separation = (p["strandSeparation"] ?: 1.0).coerceIn(0.0, 1.5)
                dy = -abs(sin(phase * 0.5)) * lift * 0.35
                dRot = cos(phase) * 1.0 * intensity
                modMesh = EvaluatedMeshDeform(
                    waveAxis = "horizontal",
                    waveAmplitude = amp,
                    waveFrequency = freq,
                    wavePhase = phase,
                    pinEdge = "top",
                    skewXDeg = 0.0,
                    skewYDeg = 0.0,
                    isStrandFlow = true,
                    strandCount = 16,
                    strandVariance = separation,
                    windBias = sin(phase * 0.5) * amp * 0.35,
                    turbulence = 0.48,
                    tipLift = 0.58,
                )
            }
            "hair.bounce" -> {
                val bounce = (p["movementStrength"] ?: 10.0) * intensity
                val sc = (p["scaleStrength"] ?: 0.04) * intensity
                val wave = abs(sin(phase))
                dy = -wave * bounce * 0.55
                dScaleY = (wave - 0.5) * sc
                dScaleX = -(wave - 0.5) * sc * 0.45
                modMesh = EvaluatedMeshDeform(
                    waveAxis = "horizontal",
                    waveAmplitude = bounce * 0.65,
                    waveFrequency = 1.5,
                    wavePhase = phase * 2.0 - 0.45,
                    pinEdge = "top",
                    skewXDeg = 0.0,
                    skewYDeg = 0.0,
                    isStrandFlow = true,
                    strandCount = 14,
                    strandVariance = 0.75,
                    windBias = 0.0,
                    turbulence = 0.32,
                    tipLift = 0.45,
                )
            }
            "cloth.cape-flutter" -> {
                val amp = (p["waveAmplitude"] ?: 18.0) * intensity
                val freq = p["waveFrequency"] ?: 2.4
                val rotStr = (p["rotationStrength"] ?: 6.0) * intensity
                dRot = (0.5 + 0.5 * sin(phase)) * rotStr
                modMesh = EvaluatedMeshDeform("horizontal", amp, freq, phase, "top", dRot * 0.4, 0.0)
            }
            "cloth.dress-sway" -> {
                val amp = (p["waveAmplitude"] ?: 12.0) * intensity
                val freq = p["waveFrequency"] ?: 1.5
                val rotStr = (p["rotationStrength"] ?: 5.0) * intensity
                dRot = sin(phase) * rotStr
                modMesh = EvaluatedMeshDeform("horizontal", amp, freq, phase, "top", dRot * 0.3, 0.0)
            }
            "cloth.scarf-trail" -> {
                val amp = (p["waveAmplitude"] ?: 16.0) * intensity
                val freq = p["waveFrequency"] ?: 2.6
                val rotStr = (p["rotationStrength"] ?: 4.0) * intensity
                dRot = sin(phase) * rotStr
                modMesh = EvaluatedMeshDeform("vertical", amp, freq, phase, "left", 0.0, dRot * 0.3)
            }
            "cloth.sleeve-drift" -> {
                val amp = (p["waveAmplitude"] ?: 7.0) * intensity
                val rotStr = (p["rotationStrength"] ?: 3.0) * intensity
                dRot = sin(phase) * rotStr
                modMesh = EvaluatedMeshDeform("horizontal", amp, 1.2, phase, "top", 0.0, 0.0)
            }
            "cloth.flag-wave" -> {
                val amp = (p["waveAmplitude"] ?: 24.0) * intensity
                val freq = p["waveFrequency"] ?: 3.0
                modMesh = EvaluatedMeshDeform("vertical", amp, freq, phase, "left", 0.0, 0.0)
            }
            "character.idle-breathing" -> {
                val sc = (p["scaleStrength"] ?: 0.028) * intensity
                val lift = (p["movementStrength"] ?: 4.0) * intensity
                val breath = 0.5 - 0.5 * cos(phase)
                dScaleY = breath * sc
                dScaleX = -breath * sc * 0.35
                dy = -breath * lift
            }
            "character.head-bob" -> {
                val depth = (p["movementStrength"] ?: 9.0) * intensity
                val nod = (p["rotationStrength"] ?: 4.0) * intensity
                val beat = abs(sin(phase))
                dy = beat * depth
                dRot = sin(phase) * nod
            }
            "character.subtle-sway" -> {
                val shift = (p["movementStrength"] ?: 8.0) * intensity
                val lean = (p["rotationStrength"] ?: 2.5) * intensity
                dx = sin(phase) * shift
                dRot = sin(phase) * lean
            }
            "character.walk-bob" -> {
                val lift = (p["movementStrength"] ?: 12.0) * intensity
                val rock = (p["rotationStrength"] ?: 3.5) * intensity
                dy = -abs(sin(phase)) * lift
                dRot = sin(phase) * rock
            }
            "character.talk-pulse" -> {
                val sc = (p["scaleStrength"] ?: 0.045) * intensity
                val lift = (p["movementStrength"] ?: 5.0) * intensity
                val pulse = 0.5 + 0.5 * sin(phase * 2.0)
                dScaleY = pulse * sc
                dScaleX = -pulse * sc * 0.4
                dy = -pulse * lift
            }
            "fx.glow-pulse" -> {
                val sc = (p["scaleStrength"] ?: 0.06) * intensity
                val opDip = (p["opacityPulse"] ?: 0.25) * intensity
                val pulse = 0.5 - 0.5 * cos(phase)
                dScaleX = pulse * sc
                dScaleY = pulse * sc
                dOpacity = -pulse * opDip
            }
            "fx.float-hover" -> {
                val h = (p["movementStrength"] ?: 12.0) * intensity
                val tilt = (p["rotationStrength"] ?: 2.0) * intensity
                dy = sin(phase) * h
                dRot = cos(phase) * tilt
            }
            "fx.glitch-jitter" -> {
                val amp = (p["movementStrength"] ?: 6.0) * intensity
                val snap = (p["rotationStrength"] ?: 1.5) * intensity
                val stepSeed = floor(frame * max(0.5, mod.speed)).toInt()
                val trigger = deterministicHash(stepSeed * 7 + 3)
                if (trigger > 0.62) {
                    dx = (deterministicHash(stepSeed * 13 + 1) * 2.0 - 1.0) * amp
                    dy = (deterministicHash(stepSeed * 19 + 5) * 2.0 - 1.0) * amp * 0.6
                    dRot = (deterministicHash(stepSeed * 29 + 9) * 2.0 - 1.0) * snap
                }
            }
        }

        // Pivot anchor offset compensation so rotation/scale orbits around the requested anchor
        if (mod.pivotAnchor != PivotAnchorPreset.CENTER && (abs(dRot) > 1e-4 || abs(dScaleX) > 1e-4 || abs(dScaleY) > 1e-4)) {
            val (px, py) = pivotAnchorNormalized(mod.pivotAnchor)
            val offX = (px - 0.5) * objWidth * scaleX
            val offY = (py - 0.5) * objHeight * scaleY
            val rad = dRot * PI / 180.0
            val c = cos(rad)
            val s = sin(rad)
            val rotOffX = offX * (1.0 + dScaleX) * c - offY * (1.0 + dScaleY) * s
            val rotOffY = offX * (1.0 + dScaleX) * s + offY * (1.0 + dScaleY) * c
            dx += (offX - rotOffX)
            dy += (offY - rotOffY)
        }

        when (mod.blendMode) {
            MotionModifierBlendMode.ADD -> {
                x += dx
                y += dy
                rot += dRot
                scaleX += dScaleX
                scaleY += dScaleY
                opacity = (opacity + dOpacity).coerceIn(0.0, 1.0)
            }
            MotionModifierBlendMode.MULTIPLY -> {
                x += dx
                y += dy
                rot += dRot
                scaleX *= (1.0 + dScaleX)
                scaleY *= (1.0 + dScaleY)
                opacity = (opacity * (1.0 + dOpacity)).coerceIn(0.0, 1.0)
            }
            MotionModifierBlendMode.OVERRIDE -> {
                if (dx != 0.0) x = base.x + dx
                if (dy != 0.0) y = base.y + dy
                if (dRot != 0.0) rot = dRot
                if (dScaleX != 0.0) scaleX = 1.0 + dScaleX
                if (dScaleY != 0.0) scaleY = 1.0 + dScaleY
                if (dOpacity != 0.0) opacity = (1.0 + dOpacity).coerceIn(0.0, 1.0)
            }
        }

        if (modMesh != null && (abs(modMesh.waveAmplitude) > 0.05 || abs(modMesh.windBias) > 0.05)) {
            mesh = if (mesh == null) {
                modMesh
            } else {
                mesh.copy(
                    waveAmplitude = mesh.waveAmplitude + modMesh.waveAmplitude,
                    skewXDeg = mesh.skewXDeg + modMesh.skewXDeg,
                    skewYDeg = mesh.skewYDeg + modMesh.skewYDeg,
                    isStrandFlow = mesh.isStrandFlow || modMesh.isStrandFlow,
                    strandCount = max(mesh.strandCount, modMesh.strandCount),
                    strandVariance = max(mesh.strandVariance, modMesh.strandVariance),
                    windBias = mesh.windBias + modMesh.windBias,
                    turbulence = max(mesh.turbulence, modMesh.turbulence),
                    tipLift = max(mesh.tipLift, modMesh.tipLift),
                )
            }
        }
    }

    return LayerModifierResult(
        transform = EvaluatedTransform(
            x = x,
            y = y,
            scaleX = max(0.05, scaleX),
            scaleY = max(0.05, scaleY),
            rotationDeg = rot,
            opacity = opacity.coerceIn(0.0, 1.0),
            skewXDeg = skewXDeg.coerceIn(-65.0, 65.0),
            skewYDeg = skewYDeg.coerceIn(-65.0, 65.0),
            flipX = flipX,
            flipY = flipY,
            anchorOffsetX = anchorOffsetX,
            anchorOffsetY = anchorOffsetY,
        ),
        meshDeform = mesh,
        visualFx = visualFx,
        particleFxList = particleFxList ?: emptyList(),
        textFx = textFx,
        audioFx = audioFx,
    )
}

fun evaluateCameraAtFrame(
    comp: CompositionRecord,
    frame: Int,
    followTargetPos: Pair<Double, Double>? = null,
): EvaluatedCamera {
    val cam: CameraDocument = comp.camera
    val f = frame.toDouble()
    var x = evaluateKeyframes(cam.keyframes.x, f, cam.x)
    var y = evaluateKeyframes(cam.keyframes.y, f, cam.y)
    var zoom = evaluateKeyframes(cam.keyframes.zoom, f, cam.zoom)
    var rot = evaluateKeyframes(cam.keyframes.rotation, f, cam.rotationDeg)

    for (mod in cam.modifiers) {
        val t = resolveModifierProgress(mod, f) ?: continue
        val intensity = mod.intensity
        val kernelId = mod.basePresetId ?: mod.presetId
        val p = mod.params
        val phase = t * 2.0 * PI

        when (kernelId) {
            "camera.slow-zoom-in" -> {
                val amt = (p["zoomAmount"] ?: 0.18) * intensity
                zoom += applyEasing(t, InterpolationType.EASE_IN_OUT) * amt
            }
            "camera.slow-zoom-out" -> {
                val amt = (p["zoomAmount"] ?: 0.18) * intensity
                zoom += (1.0 - applyEasing(t, InterpolationType.EASE_IN_OUT)) * amt
            }
            "camera.horizontal-pan" -> {
                val dist = (p["panDistance"] ?: 180.0) * intensity
                x += (applyEasing(t, InterpolationType.EASE_IN_OUT) - 0.5) * dist
            }
            "camera.vertical-pan" -> {
                val dist = (p["panDistance"] ?: -120.0) * intensity
                y += (applyEasing(t, InterpolationType.EASE_IN_OUT) - 0.5) * dist
            }
            "camera.shake" -> {
                val amp = (p["amplitude"] ?: 10.0) * intensity
                val roll = (p["rotationStrength"] ?: 0.6) * intensity
                x += (sin(phase * 3.7) * 0.6 + sin(phase * 7.3 + 1.1) * 0.4) * amp
                y += (cos(phase * 4.3 + 0.5) * 0.6 + cos(phase * 8.9 + 2.3) * 0.4) * amp
                rot += sin(phase * 5.1 + 0.7) * roll
            }
            "camera.parallax-drift" -> {
                val ax = (p["amplitudeX"] ?: 42.0) * intensity
                val ay = (p["amplitudeY"] ?: 18.0) * intensity
                val zp = (p["zoomPulse"] ?: 0.025) * intensity
                x += sin(phase) * ax
                y += sin(phase * 2.0) * ay * 0.5
                zoom += (0.5 - 0.5 * cos(phase)) * zp
            }
            "camera.follow-object" -> {
                if (followTargetPos != null) {
                    val weight = ((p["followStrength"] ?: 0.65) * intensity).coerceIn(0.0, 1.0)
                    val offX = p["offsetX"] ?: 0.0
                    val offY = p["offsetY"] ?: 0.0
                    val targetCamX = followTargetPos.first - comp.settings.width * 0.5 + offX
                    val targetCamY = followTargetPos.second - comp.settings.height * 0.5 + offY
                    x += (targetCamX - x) * weight
                    y += (targetCamY - y) * weight
                }
            }
        }
    }

    return EvaluatedCamera(
        x = x,
        y = y,
        zoom = zoom.coerceIn(0.15, 6.0),
        rotationDeg = rot,
        parallaxStrength = cam.parallaxStrength,
    )
}

fun evaluateLyricsAtFrame(
    lyrics: LyricsConfig,
    frame: Int,
    fps: Double,
): EvaluatedLyricsState {
    val activeCue = lyrics.cues.find { frame >= it.startFrame && frame <= it.endFrame }
    if (activeCue == null) {
        return EvaluatedLyricsState(
            cue = null,
            visibleText = "",
            fullText = "",
            cueProgress = 0.0,
            opacity = 0.0,
            offsetY = 0.0,
            scale = 1.0,
            glowBoost = 0.0,
            beatPulse = 0.0,
            words = emptyList(),
        )
    }

    val span = max(1, activeCue.endFrame - activeCue.startFrame).toDouble()
    val cueProgress = ((frame - activeCue.startFrame).toDouble() / span).coerceIn(0.0, 1.0)
    val trans = max(1, lyrics.transitionFrames).toDouble()
    val enterT = ((frame - activeCue.startFrame).toDouble() / trans).coerceIn(0.0, 1.0)
    val exitT = ((activeCue.endFrame - frame).toDouble() / trans).coerceIn(0.0, 1.0)
    val edgeAlpha = min(applyEasing(enterT, InterpolationType.EASE_OUT), applyEasing(exitT, InterpolationType.EASE_OUT))

    val sec = frame.toDouble() / max(1.0, fps)
    val beatsPerSec = max(20.0, lyrics.bpm) / 60.0
    val beatFrac = (sec * beatsPerSec) % 1.0
    val beatPulse = (1.0 - beatFrac) * (1.0 - beatFrac)

    val rawWords = if (activeCue.words.isNotEmpty()) activeCue.words
    else splitWordsWithTiming(activeCue.text, activeCue.startFrame, activeCue.endFrame)

    var opacity = edgeAlpha
    var offsetY = 0.0
    var scale = 1.0 + if (lyrics.style == LyricsStyle.BEAT_PULSE) beatPulse * lyrics.beatPulseStrength * 2.0 else beatPulse * lyrics.beatPulseStrength * 0.5
    var glowBoost = beatPulse * 0.45
    var visibleText = activeCue.text

    when (lyrics.style) {
        LyricsStyle.STATIC -> {
            opacity = 1.0
            scale = 1.0
        }
        LyricsStyle.FADE -> {
            opacity = edgeAlpha
        }
        LyricsStyle.SLIDE_UP -> {
            offsetY = (1.0 - applyEasing(enterT, InterpolationType.EASE_OUT)) * 28.0
        }
        LyricsStyle.SCALE_REVEAL -> {
            val e = applyEasing(enterT, InterpolationType.EASE_OUT)
            scale *= (0.78 + 0.22 * e)
        }
        LyricsStyle.TYPEWRITER -> {
            val chars = (activeCue.text.length * min(1.0, cueProgress * 1.15)).toInt().coerceIn(0, activeCue.text.length)
            visibleText = activeCue.text.take(chars)
        }
        LyricsStyle.CHAR_REVEAL -> {
            val chars = (activeCue.text.length * min(1.0, cueProgress * 1.25)).toInt().coerceIn(0, activeCue.text.length)
            visibleText = activeCue.text.take(chars)
        }
        LyricsStyle.BEAT_PULSE -> {
            glowBoost = beatPulse
        }
        else -> Unit
    }

    val evalWords = rawWords.mapIndexed { idx, w ->
        val wSpan = max(1, w.endFrame - w.startFrame).toDouble()
        val wProgress = ((frame - w.startFrame).toDouble() / wSpan).coerceIn(0.0, 1.0)
        val staggerDelay = idx * max(1, lyrics.staggerFrames)
        val staggerT = ((frame - activeCue.startFrame - staggerDelay).toDouble() / max(1.0, trans)).coerceIn(0.0, 1.0)
        val wAlpha = when (lyrics.style) {
            LyricsStyle.WORD_REVEAL -> if (frame >= w.startFrame) applyEasing(min(1.0, (frame - w.startFrame) / 4.0), InterpolationType.EASE_OUT) else 0.15
            LyricsStyle.STAGGER -> applyEasing(staggerT, InterpolationType.EASE_OUT)
            else -> 1.0
        }
        val wOffsetY = when (lyrics.style) {
            LyricsStyle.STAGGER -> (1.0 - applyEasing(staggerT, InterpolationType.EASE_OUT)) * 18.0
            LyricsStyle.KARAOKE -> if (frame in w.startFrame..w.endFrame) -3.0 * sin(wProgress * PI) else 0.0
            else -> 0.0
        }
        val wScale = when (lyrics.style) {
            LyricsStyle.KARAOKE -> if (frame in w.startFrame..w.endFrame) 1.0 + 0.06 * sin(wProgress * PI) else 1.0
            LyricsStyle.WORD_REVEAL -> if (frame in w.startFrame..w.endFrame) 1.08 else 1.0
            else -> 1.0
        }
        EvaluatedLyricsWord(
            text = w.text,
            progress = if (lyrics.style == LyricsStyle.KARAOKE || lyrics.style == LyricsStyle.WORD_REVEAL) wProgress else if (frame >= w.startFrame) 1.0 else 0.0,
            revealAlpha = wAlpha,
            offsetY = wOffsetY,
            scale = wScale,
        )
    }

    return EvaluatedLyricsState(
        cue = activeCue,
        visibleText = visibleText,
        fullText = activeCue.text,
        cueProgress = cueProgress,
        opacity = opacity.coerceIn(0.0, 1.0),
        offsetY = offsetY,
        scale = scale,
        glowBoost = glowBoost,
        beatPulse = beatPulse,
        words = evalWords,
    )
}

fun evaluateCompositionAtFrame(
    project: MotionProject,
    compositionId: String,
    frame: Int,
): EvaluatedCompositionState? {
    val comp = project.compositions.find { it.id == compositionId } ?: return null
    val fps = fpsToDouble(comp.settings.fpsOverride ?: project.fps)
    val f = frame.toDouble()
    val anySolo = comp.layers.any { it.solo }

    val localById = mutableMapOf<String, LayerModifierResult>()
    for (layer in comp.layers) {
        val kf = layer.keyframes
        val base = EvaluatedTransform(
            x = evaluateKeyframes(kf.x, f, layer.transform.x),
            y = evaluateKeyframes(kf.y, f, layer.transform.y),
            scaleX = evaluateKeyframes(kf.scaleX, f, layer.transform.scaleX),
            scaleY = evaluateKeyframes(kf.scaleY, f, layer.transform.scaleY),
            rotationDeg = evaluateKeyframes(kf.rotation, f, layer.transform.rotationDeg),
            opacity = evaluateKeyframes(kf.opacity, f, layer.opacity).coerceIn(0.0, 1.0),
        )
        val primaryObj = layer.objects.firstOrNull()
        val res = applyLayerModifiers(
            base = base,
            modifiers = layer.modifiers,
            frame = f,
            objWidth = primaryObj?.width ?: 200.0,
            objHeight = primaryObj?.height ?: 200.0,
        )
        localById[layer.id] = res
    }

    val byId = comp.layers.associateBy { it.id }
    val worldById = mutableMapOf<String, EvaluatedTransform>()

    fun resolveWorld(layerId: String, visited: MutableSet<String>): EvaluatedTransform {
        worldById[layerId]?.let { return it }
        val layer = byId[layerId]
            ?: return EvaluatedTransform(0.0, 0.0, 1.0, 1.0, 0.0, 1.0)
        val local = localById[layerId]!!.transform
        val pid = layer.parentId
        if (pid == null || !byId.containsKey(pid) || !visited.add(layerId)) {
            worldById[layerId] = local
            return local
        }
        val parent = resolveWorld(pid, visited)
        val rad = parent.rotationDeg * PI / 180.0
        val c = cos(rad)
        val s = sin(rad)
        val sx = local.x * parent.scaleX
        val sy = local.y * parent.scaleY
        val wx = parent.x + sx * c - sy * s
        val wy = parent.y + sx * s + sy * c
        val world = EvaluatedTransform(
            x = wx,
            y = wy,
            scaleX = parent.scaleX * local.scaleX,
            scaleY = parent.scaleY * local.scaleY,
            rotationDeg = parent.rotationDeg + local.rotationDeg,
            opacity = (parent.opacity * local.opacity).coerceIn(0.0, 1.0),
            skewXDeg = parent.skewXDeg + local.skewXDeg,
            skewYDeg = parent.skewYDeg + local.skewYDeg,
            flipX = parent.flipX xor local.flipX,
            flipY = parent.flipY xor local.flipY,
            anchorOffsetX = local.anchorOffsetX,
            anchorOffsetY = local.anchorOffsetY,
        )
        worldById[layerId] = world
        return world
    }

    for (layer in comp.layers) {
        resolveWorld(layer.id, mutableSetOf())
    }

    val followPos = comp.camera.followLayerId?.let { fid ->
        worldById[fid]?.let { it.x to it.y }
    }
    val evaluatedCamera = evaluateCameraAtFrame(comp, frame, followPos)

    val evalLayers = comp.layers.map { layer ->
        val localRes = localById[layer.id]!!
        val world = worldById[layer.id]!!
        val inRange = frame >= layer.inFrame && frame <= layer.outFrame
        val visibleBySolo = !anySolo || layer.solo
        val active = layer.visible && visibleBySolo && inRange

        val primaryObj = layer.objects.firstOrNull()
        val charFrameIdx = if (primaryObj is SceneObjectDocument.CharacterObject) {
            val charDef = project.characters.find { it.id == primaryObj.characterId }
            val clip = charDef?.clips?.find { it.id == primaryObj.activeClipId } ?: charDef?.clips?.firstOrNull()
            if (clip != null && clip.frameIndices.isNotEmpty()) {
                val elapsedSec = max(0.0, (frame - primaryObj.clipStartFrame).toDouble() / max(1.0, fps))
                val rawIdx = floor(elapsedSec * max(0.5, clip.fps) * max(0.1, primaryObj.playbackRate)).toInt()
                val idx = if (clip.loop) rawIdx % clip.frameIndices.size else min(rawIdx, clip.frameIndices.lastIndex)
                clip.frameIndices[idx.coerceIn(0, clip.frameIndices.lastIndex)]
            } else {
                0
            }
        } else null

        val lyricsState = if (primaryObj is SceneObjectDocument.TextObject && primaryObj.lyrics?.enabled == true) {
            evaluateLyricsAtFrame(primaryObj.lyrics, frame, fps)
        } else null

        EvaluatedLayerState(
            layer = layer,
            isActiveAtFrame = active,
            localTransform = localRes.transform,
            worldTransform = world,
            blendMode = layer.blendMode,
            depth = layer.depth,
            role = layer.role,
            meshDeform = localRes.meshDeform,
            characterFrameIndex = charFrameIdx,
            lyricsState = lyricsState,
            visualFx = localRes.visualFx,
            particleFxList = localRes.particleFxList,
            textFx = localRes.textFx,
            audioFx = localRes.audioFx,
        )
    }

    return EvaluatedCompositionState(
        composition = comp,
        frame = frame,
        fps = fps,
        camera = evaluatedCamera,
        environment = comp.environment,
        layers = evalLayers,
    )
}

fun getProjectMovieDurationFrames(project: MotionProject): Int {
    if (project.projectTimeline.isEmpty()) {
        return project.compositions.firstOrNull()?.settings?.durationFrames ?: 240
    }
    return max(1, project.projectTimeline.maxOf { it.startFrame + it.durationFrames })
}

fun evaluateProjectTimelineAtFrame(
    project: MotionProject,
    movieFrame: Int,
): EvaluatedMovieState {
    if (project.projectTimeline.isEmpty()) {
        val firstComp = project.compositions.firstOrNull()
        val evaluated = firstComp?.let {
            val dur = max(1, it.settings.durationFrames)
            evaluateCompositionAtFrame(project, it.id, ((movieFrame % dur) + dur) % dur)
        }
        return EvaluatedMovieState(evaluated, null, TransitionType.CUT, 1.0)
    }

    val activeClips = project.projectTimeline.filter { clip ->
        movieFrame >= clip.startFrame && movieFrame < clip.startFrame + clip.durationFrames
    }
    if (activeClips.isEmpty()) {
        return EvaluatedMovieState(null, null, TransitionType.CUT, 1.0)
    }

    val topClip = activeClips.last()
    val topComp = project.compositions.find { it.id == topClip.compositionId }
    val compDur = max(1, topComp?.settings?.durationFrames ?: 240)
    val localFrame = (((movieFrame - topClip.startFrame + topClip.inOffsetFrames) % compDur) + compDur) % compDur
    val primary = evaluateCompositionAtFrame(project, topClip.compositionId, localFrame)

    val framesIntoClip = movieFrame - topClip.startFrame
    val transitionProgress = if (
        topClip.transitionIn != TransitionType.CUT &&
        topClip.transitionDurationFrames > 0 &&
        framesIntoClip < topClip.transitionDurationFrames
    ) {
        (framesIntoClip.toDouble() / topClip.transitionDurationFrames.toDouble()).coerceIn(0.0, 1.0)
    } else {
        1.0
    }

    var outgoing: EvaluatedCompositionState? = null
    if (activeClips.size > 1) {
        val prevClip = activeClips[activeClips.size - 2]
        val prevComp = project.compositions.find { it.id == prevClip.compositionId }
        val prevDur = max(1, prevComp?.settings?.durationFrames ?: 240)
        val prevLocal = (((movieFrame - prevClip.startFrame + prevClip.inOffsetFrames) % prevDur) + prevDur) % prevDur
        outgoing = evaluateCompositionAtFrame(project, prevClip.compositionId, prevLocal)
    }

    return EvaluatedMovieState(
        primary = primary,
        outgoing = outgoing,
        transitionType = topClip.transitionIn,
        transitionProgress = transitionProgress,
    )
}
