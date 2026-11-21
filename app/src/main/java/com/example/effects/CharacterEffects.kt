package com.example.effects

import com.example.engine.EvaluatedMeshDeform
import com.example.engine.PresetTargetType
import com.example.schema.PivotAnchorPreset
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin

private val CHARACTER_EFFECT_TARGETS = listOf(
    PresetTargetType.CHARACTER,
    PresetTargetType.SPRITE,
    PresetTargetType.IMAGE,
    PresetTargetType.SHAPE,
    PresetTargetType.LAYER,
)

private val LOW_COST_CHARACTER_METADATA = EffectPerformanceMetadata(
    level = CostLevel.LOW,
    cpuCost = CostLevel.LOW,
    gpuCost = CostLevel.LOW,
    memoryCost = CostLevel.LOW,
    supportsLowEnd = true,
    fallbackEffect = null,
    renderingTechnique = "2D Matrix Transform + Lightweight Vector Overlay",
    maxRecommendedParticles = 0,
    textureRequirements = "None (reuses existing character sprite)",
    knownLimitations = listOf("2D layered approximation without 3D bone rigging"),
    qualityReductionOptions = listOf("Zero reduction needed on Low-End devices"),
)

/**
 * Phase 2 Character Effect: Eye Blink (`fx.character.eye_blink`).
 * Periodic eyelid closure via lightweight scale/vector blink overlay.
 */
class EyeBlinkEffect : BaseMatrixEffect() {
    override val definition: EffectModuleDefinition = EffectModuleDefinition(
        id = ID,
        name = "Eye Blink",
        category = EffectCategory.CHARACTER,
        phase = 2,
        description = "Natural periodic eye blink using lightweight eyelid overlay and subtle micro-squash.",
        targetTypes = CHARACTER_EFFECT_TARGETS,
        defaultDurationSec = 3.0,
        defaultLoop = true,
        params = listOf(
            EffectParamSpec("blinksPerCycle", "Blinks / Cycle", 1.0, 1.0, 4.0, 1.0),
            EffectParamSpec("blinkWidth", "Blink Length", 0.12, 0.04, 0.30, 0.02),
            EffectParamSpec("squashEye", "Scale Blink", 0.0, 0.0, 1.0, 1.0, isAdvanced = true, enumLabels = mapOf(0 to "Eyelid", 1 to "Scale Y")),
        ),
        performance = LOW_COST_CHARACTER_METADATA,
    )

    override fun evaluate(context: EffectEvaluationContext, out: EffectTransformState) {
        val blinks = max(1.0, context.param("blinksPerCycle", 1.0))
        val width = context.param("blinkWidth", 0.12).coerceIn(0.04, 0.35)
        val scaleMode = context.param("squashEye", 0.0) >= 0.5
        val sub = (context.normalizedTime * blinks) % 1.0
        val center = 0.78
        val dist = abs(sub - center)
        val close = if (dist < width * 0.5) {
            val u = 1.0 - dist / (width * 0.5)
            (sin(u * PI * 0.5) * context.intensity).coerceIn(0.0, 1.0)
        } else 0.0

        if (scaleMode) {
            out.dScaleY = -close * 0.85
        } else {
            out.visualFx = EvaluatedVisualFx(eyeBlinkClose = close.toFloat())
            out.dScaleY = -close * 0.015
        }
    }

    companion object {
        const val ID = "fx.character.eye_blink"
    }
}

/**
 * Phase 2 Character Effect: Eye Look (`fx.character.eye_look`).
 * Lightweight directional gaze shift with optional subtle natural scanning.
 */
class EyeLookEffect : BaseMatrixEffect() {
    override val definition: EffectModuleDefinition = EffectModuleDefinition(
        id = ID,
        name = "Eye Look",
        category = EffectCategory.CHARACTER,
        phase = 2,
        description = "Directs character eye gaze horizontally and vertically with optional glance scan.",
        targetTypes = CHARACTER_EFFECT_TARGETS,
        defaultDurationSec = 3.5,
        defaultLoop = true,
        params = listOf(
            EffectParamSpec("lookX", "Look X", 0.65, -1.0, 1.0, 0.05),
            EffectParamSpec("lookY", "Look Y", 0.0, -1.0, 1.0, 0.05),
            EffectParamSpec("autoGlance", "Glance Motion", 1.0, 0.0, 1.0, 1.0, isAdvanced = true, enumLabels = mapOf(0 to "Hold", 1 to "Scan")),
        ),
        performance = LOW_COST_CHARACTER_METADATA,
    )

    override fun evaluate(context: EffectEvaluationContext, out: EffectTransformState) {
        val baseX = context.param("lookX", 0.65)
        val baseY = context.param("lookY", 0.0)
        val scan = context.param("autoGlance", 1.0) >= 0.5
        val phase = context.normalizedTime * 2.0 * PI
        val gx = (if (scan) sin(phase) * baseX else baseX) * context.intensity
        val gy = (if (scan) sin(phase * 0.5 + 0.6) * baseY else baseY) * context.intensity
        out.visualFx = EvaluatedVisualFx(
            eyeLookX = gx.toFloat().coerceIn(-1f, 1f),
            eyeLookY = gy.toFloat().coerceIn(-1f, 1f),
            hasEyeLook = true,
        )
    }

    companion object {
        const val ID = "fx.character.eye_look"
    }
}

/**
 * Phase 2 Character Effect: Head Turn (`fx.character.head_turn`).
 * Lightweight 2.5D head turn approximation via horizontal foreshortening, arc offset, and subtle tilt.
 */
class HeadTurnEffect : BaseMatrixEffect() {
    override val definition: EffectModuleDefinition = EffectModuleDefinition(
        id = ID,
        name = "Head Turn",
        category = EffectCategory.CHARACTER,
        phase = 2,
        description = "Lightweight 2.5D head turn using matrix foreshortening, skew, and neck arc.",
        targetTypes = CHARACTER_EFFECT_TARGETS,
        defaultDurationSec = 2.8,
        defaultLoop = true,
        defaultPivot = PivotAnchorPreset.BOTTOM,
        params = listOf(
            EffectParamSpec("turnAmount", "Turn Angle", 0.45, -1.0, 1.0, 0.05),
            EffectParamSpec("oscillate", "Motion Mode", 1.0, 0.0, 1.0, 1.0, enumLabels = mapOf(0 to "Pose", 1 to "Look L/R")),
            EffectParamSpec("tiltDeg", "Head Tilt", 4.0, 0.0, 18.0, 0.5, "°", isAdvanced = true),
            EffectParamSpec("shiftX", "Arc Shift", 10.0, 0.0, 40.0, 1.0, "px", isAdvanced = true),
        ),
        performance = LOW_COST_CHARACTER_METADATA,
    )

    override fun evaluate(context: EffectEvaluationContext, out: EffectTransformState) {
        val amt = context.param("turnAmount", 0.45) * context.intensity
        val oscillate = context.param("oscillate", 1.0) >= 0.5
        val tilt = context.param("tiltDeg", 4.0) * context.intensity
        val shift = context.param("shiftX", 10.0) * context.intensity

        val turn = if (oscillate) sin(context.normalizedTime * 2.0 * PI) * amt else amt
        val absTurn = abs(turn).coerceIn(0.0, 0.9)
        // Perspective foreshortening: slightly narrows width and leans along turn direction
        out.dScaleX = -absTurn * 0.18
        out.dSkewYDeg = turn * 6.0
        out.dRotDeg = turn * tilt
        out.dx = turn * shift
        out.dy = absTurn * 3.0
    }

    companion object {
        const val ID = "fx.character.head_turn"
    }
}

/**
 * Phase 2 Character Effect: Breathing (`fx.character.breathing`).
 * Organic chest/body expansion and subtle vertical lift anchored at the feet/base.
 */
class BreathingEffect : BaseMatrixEffect() {
    override val definition: EffectModuleDefinition = EffectModuleDefinition(
        id = ID,
        name = "Breathing",
        category = EffectCategory.CHARACTER,
        phase = 2,
        description = "Subtle idle breathing combining chest scale expansion and gentle vertical lift.",
        targetTypes = CHARACTER_EFFECT_TARGETS,
        defaultDurationSec = 3.2,
        defaultLoop = true,
        defaultPivot = PivotAnchorPreset.BOTTOM,
        params = listOf(
            EffectParamSpec("chestScale", "Chest Expand", 0.032, 0.005, 0.14, 0.005),
            EffectParamSpec("liftPx", "Shoulder Lift", 4.5, 0.0, 24.0, 0.5, "px"),
            EffectParamSpec("counterScaleX", "Volume Balance", 0.35, 0.0, 0.8, 0.05, isAdvanced = true),
        ),
        performance = LOW_COST_CHARACTER_METADATA,
    )

    override fun evaluate(context: EffectEvaluationContext, out: EffectTransformState) {
        val phase = context.normalizedTime * 2.0 * PI
        val scale = context.param("chestScale", 0.032) * context.intensity
        val lift = context.param("liftPx", 4.5) * context.intensity
        val balance = context.param("counterScaleX", 0.35)
        val breath = 0.5 - 0.5 * cos(phase)
        out.dScaleY = breath * scale
        out.dScaleX = -breath * scale * balance
        out.dy = -breath * lift
    }

    companion object {
        const val ID = "fx.character.breathing"
    }
}

/**
 * Phase 2 Character Effect: Hair Sway (`fx.character.hair_sway`).
 * Segmented lightweight strand oscillation with adjustable amplitude, frequency, and wind bias.
 * Avoids full hair physics; scales strand count down cleanly on Low-End devices.
 */
class HairSwayEffect : BaseMatrixEffect() {
    override val definition: EffectModuleDefinition = EffectModuleDefinition(
        id = ID,
        name = "Hair Sway",
        category = EffectCategory.CHARACTER,
        phase = 2,
        description = "Lightweight segmented hair strand oscillation with breeze bias — zero physics sim.",
        targetTypes = CHARACTER_EFFECT_TARGETS,
        defaultDurationSec = 2.8,
        defaultLoop = true,
        defaultPivot = PivotAnchorPreset.TOP,
        params = listOf(
            EffectParamSpec("amplitude", "Sway Amplitude", 14.0, 2.0, 42.0, 1.0, "px"),
            EffectParamSpec("frequency", "Wave Frequency", 1.7, 0.5, 4.0, 0.1),
            EffectParamSpec("windBias", "Wind Push", 6.0, -30.0, 30.0, 1.0, "px"),
            EffectParamSpec("strandCount", "Segments", 10.0, 4.0, 18.0, 2.0, isAdvanced = true),
            EffectParamSpec("turbulence", "Turbulence", 0.35, 0.0, 1.0, 0.05, isAdvanced = true),
        ),
        performance = EffectPerformanceMetadata(
            level = CostLevel.MEDIUM,
            cpuCost = CostLevel.LOW,
            gpuCost = CostLevel.MEDIUM,
            memoryCost = CostLevel.LOW,
            supportsLowEnd = true,
            fallbackEffect = SwingEffect.ID,
            renderingTechnique = "Segmented 2D Strand Strip Mesh (Sinusoidal)",
            maxRecommendedParticles = 0,
            textureRequirements = "Reuses single layer sprite bitmap",
            lightweightAlternatives = listOf(SwingEffect.ID, SkewEffect.ID),
            knownLimitations = listOf("Segmented strip approximation; not a collision-aware hair sim"),
            qualityReductionOptions = listOf("Reduces strand strips to 6 on Low-End / Performance preview"),
        ),
    )

    override fun evaluate(context: EffectEvaluationContext, out: EffectTransformState) {
        val phase = context.normalizedTime * 2.0 * PI
        val amp = context.param("amplitude", 14.0) * context.intensity
        val freq = context.param("frequency", 1.7)
        val wind = context.param("windBias", 6.0) * context.intensity
        val rawStrands = context.param("strandCount", 10.0).toInt().coerceIn(4, 18)
        val strands = if (context.deviceProfile == DevicePerformanceProfile.LOW_END || context.reducedQuality) {
            minOf(rawStrands, 6)
        } else rawStrands
        val turb = context.param("turbulence", 0.35).coerceIn(0.0, 1.0)

        out.dRotDeg = sin(phase) * (amp * 0.12)
        out.meshDeform = EvaluatedMeshDeform(
            waveAxis = "horizontal",
            waveAmplitude = amp,
            waveFrequency = freq,
            wavePhase = phase,
            pinEdge = "top",
            skewXDeg = sin(phase - 0.3) * (amp * 0.18),
            skewYDeg = 0.0,
            isStrandFlow = true,
            strandCount = strands,
            strandVariance = 0.68,
            windBias = wind * (0.7 + 0.3 * sin(phase * 0.5)),
            turbulence = turb,
            tipLift = 0.32,
        )
    }

    companion object {
        const val ID = "fx.character.hair_sway"
    }
}

/**
 * Phase 2 Character Effect: Cloth Sway (`fx.character.cloth_sway`).
 * Layered sprite strip deformation for capes, dresses, scarves, and flags without cloth physics.
 */
class ClothSwayEffect : BaseMatrixEffect() {
    override val definition: EffectModuleDefinition = EffectModuleDefinition(
        id = ID,
        name = "Cloth Sway",
        category = EffectCategory.CHARACTER,
        phase = 2,
        description = "Lightweight sine-wave strip deformation for capes, dresses, scarves, and banners.",
        targetTypes = CHARACTER_EFFECT_TARGETS,
        defaultDurationSec = 2.4,
        defaultLoop = true,
        defaultPivot = PivotAnchorPreset.TOP,
        params = listOf(
            EffectParamSpec("amplitude", "Billow Strength", 16.0, 2.0, 44.0, 1.0, "px"),
            EffectParamSpec("frequency", "Ripple Speed", 2.0, 0.5, 4.5, 0.1),
            EffectParamSpec("pinEdge", "Pinned Edge", 0.0, 0.0, 1.0, 1.0, enumLabels = mapOf(0 to "Top", 1 to "Left")),
            EffectParamSpec("leanDeg", "Wind Lean", 4.5, 0.0, 18.0, 0.5, "°", isAdvanced = true),
        ),
        performance = EffectPerformanceMetadata(
            level = CostLevel.MEDIUM,
            cpuCost = CostLevel.LOW,
            gpuCost = CostLevel.MEDIUM,
            memoryCost = CostLevel.LOW,
            supportsLowEnd = true,
            fallbackEffect = SkewEffect.ID,
            renderingTechnique = "Segmented 2D Sine Strip Mesh",
            maxRecommendedParticles = 0,
            textureRequirements = "Reuses single layer sprite bitmap",
            lightweightAlternatives = listOf(SkewEffect.ID, SwingEffect.ID),
            knownLimitations = listOf("2D pinned strip deformation; avoids costly vertex-spring cloth simulation"),
            qualityReductionOptions = listOf("Automatically reduces strip subdivisions in Performance mode"),
        ),
    )

    override fun evaluate(context: EffectEvaluationContext, out: EffectTransformState) {
        val phase = context.normalizedTime * 2.0 * PI
        val amp = context.param("amplitude", 16.0) * context.intensity
        val freq = context.param("frequency", 2.0)
        val pinLeft = context.param("pinEdge", 0.0) >= 0.5
        val lean = context.param("leanDeg", 4.5) * context.intensity

        out.dRotDeg = sin(phase) * lean
        out.meshDeform = EvaluatedMeshDeform(
            waveAxis = if (pinLeft) "vertical" else "horizontal",
            waveAmplitude = amp,
            waveFrequency = freq,
            wavePhase = phase,
            pinEdge = if (pinLeft) "left" else "top",
            skewXDeg = if (pinLeft) 0.0 else out.dRotDeg * 0.35,
            skewYDeg = if (pinLeft) out.dRotDeg * 0.35 else 0.0,
        )
    }

    companion object {
        const val ID = "fx.character.cloth_sway"
    }
}

/**
 * Phase 2 Character Effect: Mouth Shapes (`fx.character.mouth_shapes`).
 * Lightweight viseme/mouth shape cycling (Closed, Ah, Oh, Ee, Smile) with subtle jaw bounce.
 */
class MouthShapesEffect : BaseMatrixEffect() {
    override val definition: EffectModuleDefinition = EffectModuleDefinition(
        id = ID,
        name = "Mouth Shapes",
        category = EffectCategory.CHARACTER,
        phase = 2,
        description = "Lightweight lip-sync viseme shapes (Closed, Ah, Oh, Ee, Smile) with subtle talk bounce.",
        targetTypes = CHARACTER_EFFECT_TARGETS,
        defaultDurationSec = 1.6,
        defaultLoop = true,
        params = listOf(
            EffectParamSpec("shapeMode", "Mouth Pose", 5.0, 0.0, 5.0, 1.0, enumLabels = mapOf(0 to "Closed", 1 to "Ah", 2 to "Oh", 3 to "Ee", 4 to "Smile", 5 to "Auto Talk")),
            EffectParamSpec("talkRate", "Syllable Rate", 4.0, 1.0, 10.0, 0.5),
            EffectParamSpec("jawBounce", "Jaw Micro-Pulse", 0.025, 0.0, 0.1, 0.005, isAdvanced = true),
        ),
        performance = LOW_COST_CHARACTER_METADATA,
    )

    override fun evaluate(context: EffectEvaluationContext, out: EffectTransformState) {
        val mode = context.param("shapeMode", 5.0).toInt()
        val rate = max(1.0, context.param("talkRate", 4.0))
        val jaw = context.param("jawBounce", 0.025) * context.intensity

        val viseme = if (mode in 0..4) {
            mode
        } else {
            val step = floor(context.normalizedTime * rate * 4.0).toInt()
            val pattern = intArrayOf(1, 2, 3, 1, 0, 4, 2, 1)
            pattern[((step % pattern.size) + pattern.size) % pattern.size]
        }

        val openness = when (viseme) {
            1 -> 1.0
            2 -> 0.8
            3 -> 0.55
            4 -> 0.35
            else -> 0.0
        }
        out.visualFx = EvaluatedVisualFx(mouthShapeIndex = viseme)
        out.dScaleY = openness * jaw
        out.dy = -openness * jaw * 40.0
    }

    companion object {
        const val ID = "fx.character.mouth_shapes"
    }
}

/**
 * Phase 2 Character Effect: Secondary Motion (`fx.character.secondary_motion`).
 * Analytical delayed follow-through and spring lag for accessories, ponytails, ears, and props.
 */
class SecondaryMotionEffect : BaseMatrixEffect() {
    override val definition: EffectModuleDefinition = EffectModuleDefinition(
        id = ID,
        name = "Secondary Motion",
        category = EffectCategory.CHARACTER,
        phase = 2,
        description = "Lightweight delayed follow-through and spring lag for hair accessories, ears, and props.",
        targetTypes = ALL_LAYER_EFFECT_TARGETS,
        defaultDurationSec = 2.0,
        defaultLoop = true,
        defaultPivot = PivotAnchorPreset.TOP,
        params = listOf(
            EffectParamSpec("lagPhase", "Follow-Through Lag", 0.22, 0.05, 0.65, 0.02),
            EffectParamSpec("swayDeg", "Trailing Tilt", 10.0, 1.0, 35.0, 1.0, "°"),
            EffectParamSpec("driftPx", "Trailing Offset", 12.0, 0.0, 45.0, 1.0, "px"),
            EffectParamSpec("springDamping", "Spring Settle", 0.25, 0.0, 0.9, 0.05, isAdvanced = true),
        ),
        performance = LOW_COST_CHARACTER_METADATA,
    )

    override fun evaluate(context: EffectEvaluationContext, out: EffectTransformState) {
        val lag = context.param("lagPhase", 0.22)
        val sway = context.param("swayDeg", 10.0) * context.intensity
        val drift = context.param("driftPx", 12.0) * context.intensity
        val damp = context.param("springDamping", 0.25).coerceIn(0.0, 0.95)

        val delayedT = (context.normalizedTime - lag + 1.0) % 1.0
        val phase = delayedT * 2.0 * PI
        val env = if (damp > 0.01) exp(-damp * context.normalizedTime * 2.5) else 1.0
        val wave = (sin(phase) * 0.75 + sin(phase * 2.0 - 0.6) * 0.25) * env

        out.dRotDeg = wave * sway
        out.dx = wave * drift
        out.dy = abs(wave) * drift * 0.22
        out.dSkewXDeg = wave * sway * 0.35
    }

    companion object {
        const val ID = "fx.character.secondary_motion"
    }
}

val PHASE2_CHARACTER_EFFECTS: List<Effect> = listOf(
    EyeBlinkEffect(),
    EyeLookEffect(),
    HeadTurnEffect(),
    BreathingEffect(),
    HairSwayEffect(),
    ClothSwayEffect(),
    MouthShapesEffect(),
    SecondaryMotionEffect(),
)
