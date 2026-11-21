package com.example.effects

import com.example.engine.applyEasing
import com.example.schema.InterpolationType
import com.example.schema.PivotAnchorPreset
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

/**
 * Phase 1 Motion Effect: Bounce (`fx.motion.bounce`).
 * Elastic vertical bounce with volume-preserving squash & stretch using pure math curves.
 */
class BounceEffect : BaseMatrixEffect() {
    override val definition: EffectModuleDefinition = EffectModuleDefinition(
        id = ID,
        name = "Bounce",
        category = EffectCategory.MOTION,
        phase = 1,
        description = "Elastic vertical bounce with optional squash-and-stretch volume preservation.",
        targetTypes = ALL_LAYER_EFFECT_TARGETS,
        defaultDurationSec = 1.4,
        defaultLoop = true,
        defaultPivot = PivotAnchorPreset.BOTTOM,
        params = listOf(
            EffectParamSpec("height", "Bounce Height", 55.0, 4.0, 220.0, 2.0, "px"),
            EffectParamSpec("bounces", "Bounces", 2.0, 1.0, 6.0, 1.0),
            EffectParamSpec("squash", "Squash & Stretch", 0.14, 0.0, 0.45, 0.02),
            EffectParamSpec("damping", "Decay Damping", 0.45, 0.0, 0.95, 0.05, isAdvanced = true),
        ),
        performance = LOW_COST_MOTION_METADATA,
    )

    override fun evaluate(context: EffectEvaluationContext, out: EffectTransformState) {
        val t = context.normalizedTime
        val height = context.param("height", 55.0) * context.intensity
        val bounces = max(1.0, context.param("bounces", 2.0))
        val squash = context.param("squash", 0.14) * context.intensity
        val damping = context.param("damping", 0.45).coerceIn(0.0, 0.95)

        val decay = exp(-damping * t * 3.2)
        val wave = abs(sin(t * bounces * PI))
        out.dy = -wave * height * decay

        val impact = (1.0 - wave).pow(3.0) * decay
        out.dScaleX = impact * squash
        out.dScaleY = -impact * squash * 0.85
    }

    companion object {
        const val ID = "fx.motion.bounce"
    }
}

/**
 * Phase 1 Motion Effect: Shake (`fx.motion.shake`).
 * Multi-frequency impact or tremor shake with smooth exponential decay.
 */
class ShakeEffect : BaseMatrixEffect() {
    override val definition: EffectModuleDefinition = EffectModuleDefinition(
        id = ID,
        name = "Shake",
        category = EffectCategory.MOTION,
        phase = 1,
        description = "Multi-frequency impact or tremor shake with smooth exponential decay.",
        targetTypes = ALL_LAYER_EFFECT_TARGETS,
        defaultDurationSec = 1.0,
        defaultLoop = true,
        params = listOf(
            EffectParamSpec("amplitudeX", "Shake X", 14.0, 0.0, 60.0, 1.0, "px"),
            EffectParamSpec("amplitudeY", "Shake Y", 10.0, 0.0, 60.0, 1.0, "px"),
            EffectParamSpec("frequency", "Speed / Freq", 7.0, 1.0, 18.0, 0.5),
            EffectParamSpec("rotationDeg", "Rotational Jolt", 2.5, 0.0, 18.0, 0.5, "°", isAdvanced = true),
            EffectParamSpec("decay", "Impact Decay", 0.35, 0.0, 1.0, 0.05, isAdvanced = true),
        ),
        performance = LOW_COST_MOTION_METADATA,
    )

    override fun evaluate(context: EffectEvaluationContext, out: EffectTransformState) {
        val t = context.normalizedTime
        val phase = t * 2.0 * PI
        val ampX = context.param("amplitudeX", 14.0) * context.intensity
        val ampY = context.param("amplitudeY", 10.0) * context.intensity
        val freq = max(1.0, context.param("frequency", 7.0))
        val rotAmp = context.param("rotationDeg", 2.5) * context.intensity
        val decayRate = context.param("decay", 0.35).coerceIn(0.0, 1.0)

        val envelope = if (decayRate > 0.01) exp(-decayRate * t * 4.5) else 1.0
        val w = phase * freq
        out.dx = (sin(w) * 0.62 + sin(w * 2.13 + 1.1) * 0.38) * ampX * envelope
        out.dy = (cos(w * 1.17 + 0.4) * 0.62 + cos(w * 2.41 + 2.2) * 0.38) * ampY * envelope
        out.dRotDeg = sin(w * 1.43 + 0.7) * rotAmp * envelope
    }

    companion object {
        const val ID = "fx.motion.shake"
    }
}

/**
 * Phase 1 Motion Effect: Float (`fx.motion.float`).
 * Weightless levitation drift with subtle horizontal sway and rotational tilt.
 */
class FloatEffect : BaseMatrixEffect() {
    override val definition: EffectModuleDefinition = EffectModuleDefinition(
        id = ID,
        name = "Float",
        category = EffectCategory.MOTION,
        phase = 1,
        description = "Weightless levitation drift with subtle horizontal sway and tilt.",
        targetTypes = ALL_LAYER_EFFECT_TARGETS,
        defaultDurationSec = 3.2,
        defaultLoop = true,
        params = listOf(
            EffectParamSpec("amplitudeY", "Float Height", 16.0, 2.0, 80.0, 1.0, "px"),
            EffectParamSpec("tiltDeg", "Tilt", 2.5, 0.0, 20.0, 0.5, "°"),
            EffectParamSpec("driftX", "Side Drift", 6.0, 0.0, 60.0, 1.0, "px", isAdvanced = true),
            EffectParamSpec("phaseRatio", "Drift Ratio", 0.5, 0.25, 2.0, 0.25, isAdvanced = true),
        ),
        performance = LOW_COST_MOTION_METADATA,
    )

    override fun evaluate(context: EffectEvaluationContext, out: EffectTransformState) {
        val phase = context.normalizedTime * 2.0 * PI
        val ampY = context.param("amplitudeY", 16.0) * context.intensity
        val tilt = context.param("tiltDeg", 2.5) * context.intensity
        val driftX = context.param("driftX", 6.0) * context.intensity
        val ratio = context.param("phaseRatio", 0.5).coerceIn(0.25, 2.0)

        out.dy = sin(phase) * ampY
        out.dx = sin(phase * ratio + 0.8) * driftX
        out.dRotDeg = cos(phase) * tilt
    }

    companion object {
        const val ID = "fx.motion.float"
    }
}

/**
 * Phase 1 Motion Effect: Wiggle (`fx.motion.wiggle`).
 * Deterministic multi-harmonic wiggle — smooth, scrubbable, zero per-frame randomness jitter.
 */
class WiggleEffect : BaseMatrixEffect() {
    override val definition: EffectModuleDefinition = EffectModuleDefinition(
        id = ID,
        name = "Wiggle",
        category = EffectCategory.MOTION,
        phase = 1,
        description = "Organic deterministic harmonic wiggle for handheld props, doodles, and lively icons.",
        targetTypes = ALL_LAYER_EFFECT_TARGETS,
        defaultDurationSec = 3.0,
        defaultLoop = true,
        params = listOf(
            EffectParamSpec("posAmp", "Position Wiggle", 14.0, 0.0, 60.0, 1.0, "px"),
            EffectParamSpec("rotAmp", "Rotation Wiggle", 5.0, 0.0, 30.0, 0.5, "°"),
            EffectParamSpec("frequency", "Wiggle Rate", 2.2, 0.4, 8.0, 0.2),
            EffectParamSpec("scaleAmp", "Scale Wiggle", 0.04, 0.0, 0.25, 0.01, isAdvanced = true),
            EffectParamSpec("seed", "Random Seed", 1.0, 0.0, 50.0, 1.0, isAdvanced = true),
        ),
        performance = LOW_COST_MOTION_METADATA,
    )

    override fun evaluate(context: EffectEvaluationContext, out: EffectTransformState) {
        val phase = context.normalizedTime * 2.0 * PI
        val posAmp = context.param("posAmp", 14.0) * context.intensity
        val rotAmp = context.param("rotAmp", 5.0) * context.intensity
        val freq = max(0.3, context.param("frequency", 2.2))
        val scaleAmp = context.param("scaleAmp", 0.04) * context.intensity
        val seed = context.param("seed", 1.0)

        val w = phase * freq + seed * 3.17
        val nx = sin(w) * 0.55 + sin(w * 2.17 + 1.4) * 0.30 + sin(w * 3.63 + 2.8) * 0.15
        val ny = cos(w * 1.13 + 0.6) * 0.55 + sin(w * 2.47 + 0.2) * 0.30 + cos(w * 3.91 + 1.9) * 0.15
        val nr = sin(w * 0.89 + 2.1) * 0.65 + cos(w * 2.03 + 0.9) * 0.35
        val ns = sin(w * 1.37 + 1.2) * 0.7 + cos(w * 2.71) * 0.3

        out.dx = nx * posAmp
        out.dy = ny * posAmp
        out.dRotDeg = nr * rotAmp
        out.dScaleX = ns * scaleAmp
        out.dScaleY = -ns * scaleAmp * 0.5
    }

    companion object {
        const val ID = "fx.motion.wiggle"
    }
}

/**
 * Phase 1 Motion Effect: Spring (`fx.motion.spring`).
 * Analytical damped harmonic oscillator — zero physics simulation, 100% scrubbable.
 */
class SpringEffect : BaseMatrixEffect() {
    override val definition: EffectModuleDefinition = EffectModuleDefinition(
        id = ID,
        name = "Spring",
        category = EffectCategory.MOTION,
        phase = 1,
        description = "Analytical damped harmonic spring overshoot — zero physics simulation, 100% scrubbable.",
        targetTypes = ALL_LAYER_EFFECT_TARGETS,
        defaultDurationSec = 1.6,
        defaultLoop = false,
        defaultPivot = PivotAnchorPreset.BOTTOM,
        params = listOf(
            EffectParamSpec("displacementY", "Drop / Pull Y", -90.0, -300.0, 300.0, 5.0, "px"),
            EffectParamSpec("stiffness", "Stiffness", 14.0, 4.0, 35.0, 1.0),
            EffectParamSpec("damping", "Damping", 4.2, 0.8, 14.0, 0.2),
            EffectParamSpec("displacementX", "Pull X", 0.0, -300.0, 300.0, 5.0, "px", isAdvanced = true),
            EffectParamSpec("scaleOvershoot", "Elastic Scale", 0.22, 0.0, 0.6, 0.02, isAdvanced = true),
            EffectParamSpec("rotOvershoot", "Tilt Overshoot", 0.0, -45.0, 45.0, 1.0, "°", isAdvanced = true),
        ),
        performance = LOW_COST_MOTION_METADATA,
    )

    override fun evaluate(context: EffectEvaluationContext, out: EffectTransformState) {
        val t = context.normalizedTime
        val dispY = context.param("displacementY", -90.0) * context.intensity
        val dispX = context.param("displacementX", 0.0) * context.intensity
        val stiffness = context.param("stiffness", 14.0).coerceIn(4.0, 35.0)
        val damping = context.param("damping", 4.2).coerceIn(0.8, 14.0)
        val scaleOver = context.param("scaleOvershoot", 0.22) * context.intensity
        val rotOver = context.param("rotOvershoot", 0.0) * context.intensity

        val osc = exp(-damping * t) * cos(stiffness * t)
        out.dx = dispX * osc
        out.dy = dispY * osc
        out.dScaleX = -scaleOver * osc
        out.dScaleY = scaleOver * osc
        out.dRotDeg = rotOver * osc
    }

    companion object {
        const val ID = "fx.motion.spring"
    }
}

/**
 * Phase 1 Motion Effect: Pulse (`fx.motion.pulse`).
 * Rhythmic scale and opacity throb.
 */
class PulseEffect : BaseMatrixEffect() {
    override val definition: EffectModuleDefinition = EffectModuleDefinition(
        id = ID,
        name = "Pulse",
        category = EffectCategory.MOTION,
        phase = 1,
        description = "Rhythmic scale and opacity throb for hearts, icons, badges, and music visuals.",
        targetTypes = ALL_LAYER_EFFECT_TARGETS,
        defaultDurationSec = 1.0,
        defaultLoop = true,
        params = listOf(
            EffectParamSpec("scaleAmp", "Pulse Scale", 0.12, 0.01, 0.45, 0.01),
            EffectParamSpec("opacityDip", "Opacity Dip", 0.15, 0.0, 0.8, 0.05),
            EffectParamSpec("sharpness", "Beat Sharpness", 2.0, 1.0, 6.0, 0.5, isAdvanced = true),
        ),
        performance = LOW_COST_MOTION_METADATA,
    )

    override fun evaluate(context: EffectEvaluationContext, out: EffectTransformState) {
        val phase = context.normalizedTime * 2.0 * PI
        val scaleAmp = context.param("scaleAmp", 0.12) * context.intensity
        val opacityDip = context.param("opacityDip", 0.15) * context.intensity
        val sharpness = context.param("sharpness", 2.0).coerceIn(1.0, 6.0)

        val raw = (0.5 - 0.5 * cos(phase)).pow(sharpness)
        out.dScaleX = raw * scaleAmp
        out.dScaleY = raw * scaleAmp
        out.dOpacity = -raw * opacityDip
    }

    companion object {
        const val ID = "fx.motion.pulse"
    }
}

/**
 * Phase 1 Motion Effect: Swing (`fx.motion.swing`).
 * Pendulum rotation sway anchored at top or bottom.
 */
class SwingEffect : BaseMatrixEffect() {
    override val definition: EffectModuleDefinition = EffectModuleDefinition(
        id = ID,
        name = "Swing",
        category = EffectCategory.MOTION,
        phase = 1,
        description = "Pendulum sway anchored at top or bottom — ideal for hanging signs, lanterns, and hair.",
        targetTypes = ALL_LAYER_EFFECT_TARGETS,
        defaultDurationSec = 2.6,
        defaultLoop = true,
        defaultPivot = PivotAnchorPreset.TOP,
        params = listOf(
            EffectParamSpec("angleDeg", "Swing Angle", 16.0, 1.0, 65.0, 1.0, "°"),
            EffectParamSpec("damping", "Decay", 0.0, 0.0, 1.0, 0.05),
            EffectParamSpec("swayX", "Horizontal Drift", 0.0, 0.0, 50.0, 1.0, "px", isAdvanced = true),
        ),
        performance = LOW_COST_MOTION_METADATA,
    )

    override fun evaluate(context: EffectEvaluationContext, out: EffectTransformState) {
        val t = context.normalizedTime
        val phase = t * 2.0 * PI
        val angle = context.param("angleDeg", 16.0) * context.intensity
        val damping = context.param("damping", 0.0).coerceIn(0.0, 1.0)
        val swayX = context.param("swayX", 0.0) * context.intensity

        val decay = if (damping > 0.01) exp(-damping * t * 3.5) else 1.0
        val s = sin(phase) * decay
        out.dRotDeg = s * angle
        out.dx = s * swayX
    }

    companion object {
        const val ID = "fx.motion.swing"
    }
}

/**
 * Phase 1 Motion Effect: Stagger Entrance (`fx.motion.stagger`).
 * Frame-offset staggered slide, scale-in, and alpha reveal.
 */
class StaggerEffect : BaseMatrixEffect() {
    override val definition: EffectModuleDefinition = EffectModuleDefinition(
        id = ID,
        name = "Stagger Entrance",
        category = EffectCategory.MOTION,
        phase = 1,
        description = "Frame-offset staggered entrance combining slide, subtle scale-in, and fade.",
        targetTypes = ALL_LAYER_EFFECT_TARGETS,
        defaultDurationSec = 2.0,
        defaultLoop = false,
        params = listOf(
            EffectParamSpec("delayStepFrames", "Delay / Step", 4.0, 0.0, 24.0, 1.0, "f"),
            EffectParamSpec("layerIndex", "Order Index", 0.0, 0.0, 12.0, 1.0),
            EffectParamSpec("slideY", "Slide Distance", 36.0, -150.0, 150.0, 2.0, "px"),
            EffectParamSpec("fadeInFrames", "Fade Duration", 12.0, 2.0, 48.0, 1.0, "f", isAdvanced = true),
            EffectParamSpec("scaleFrom", "Start Scale", 0.88, 0.2, 1.4, 0.02, "×", isAdvanced = true),
        ),
        performance = LOW_COST_MOTION_METADATA,
    )

    override fun evaluate(context: EffectEvaluationContext, out: EffectTransformState) {
        val mod = context.modifier ?: return
        val delayStep = max(0.0, context.param("delayStepFrames", 4.0))
        val layerIdx = max(0.0, context.param("layerIndex", 0.0))
        val slideY = context.param("slideY", 36.0) * context.intensity
        val fadeDur = max(1.0, context.param("fadeInFrames", 12.0))
        val scaleFrom = context.param("scaleFrom", 0.88).coerceIn(0.2, 1.5)

        val localStart = mod.startFrame + mod.delayFrames + delayStep * layerIdx
        val progress = ((context.frame - localStart) / fadeDur).coerceIn(0.0, 1.0)
        val eased = applyEasing(progress, InterpolationType.EASE_OUT)
        out.dy = (1.0 - eased) * slideY
        out.dScaleX = (scaleFrom - 1.0) * (1.0 - eased)
        out.dScaleY = (scaleFrom - 1.0) * (1.0 - eased)
        out.overrideOpacity = eased
    }

    companion object {
        const val ID = "fx.motion.stagger"
    }
}

val PHASE1_MOTION_EFFECTS: List<Effect> = listOf(
    BounceEffect(),
    ShakeEffect(),
    FloatEffect(),
    WiggleEffect(),
    SpringEffect(),
    PulseEffect(),
    SwingEffect(),
    StaggerEffect(),
)
