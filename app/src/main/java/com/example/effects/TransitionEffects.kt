package com.example.effects

import com.example.engine.applyEasing
import com.example.schema.InterpolationType

private val LOW_COST_TRANSITION_METADATA = EffectPerformanceMetadata(
    level = CostLevel.LOW,
    cpuCost = CostLevel.LOW,
    gpuCost = CostLevel.LOW,
    memoryCost = CostLevel.LOW,
    supportsLowEnd = true,
    fallbackEffect = null,
    renderingTechnique = "2D Matrix Transform / Hardware Clip Mask (Zero Shaders)",
    maxRecommendedParticles = 0,
    textureRequirements = "None",
    knownLimitations = listOf("Uses Canvas clip/alpha rather than full-frame fragment shaders"),
    qualityReductionOptions = listOf("Zero reduction needed; runs smoothly on Low-End tablets"),
)

private fun resolveTransitionProgress(context: EffectEvaluationContext): Double {
    val isOut = context.param("mode", 0.0) >= 0.5
    val eased = applyEasing(context.normalizedTime, InterpolationType.EASE_IN_OUT)
    return if (isOut) (1.0 - eased) else eased
}

/** 1. Fade Transition (`fx.transition.fade`) */
class FadeTransitionEffect : BaseMatrixEffect() {
    override val definition = EffectModuleDefinition(
        id = ID,
        name = "Fade Transition",
        category = EffectCategory.TRANSITION,
        phase = 5,
        description = "Clean alpha fade-in or fade-out transition.",
        targetTypes = ALL_LAYER_EFFECT_TARGETS,
        defaultDurationSec = 0.8,
        defaultLoop = false,
        params = listOf(
            EffectParamSpec("mode", "Transition", 0.0, 0.0, 1.0, 1.0, enumLabels = mapOf(0 to "In", 1 to "Out")),
        ),
        performance = LOW_COST_TRANSITION_METADATA,
    )

    override fun evaluate(context: EffectEvaluationContext, out: EffectTransformState) {
        val p = resolveTransitionProgress(context)
        out.overrideOpacity = (1.0 + (p - 1.0) * context.intensity).coerceIn(0.0, 1.0)
    }

    companion object {
        const val ID = "fx.transition.fade"
    }
}

/** 2. Slide Transition (`fx.transition.slide`) */
class SlideTransitionEffect : BaseMatrixEffect() {
    override val definition = EffectModuleDefinition(
        id = ID,
        name = "Slide Transition",
        category = EffectCategory.TRANSITION,
        phase = 5,
        description = "Fast directional matrix slide transition from off-screen or into view.",
        targetTypes = ALL_LAYER_EFFECT_TARGETS,
        defaultDurationSec = 0.8,
        defaultLoop = false,
        params = listOf(
            EffectParamSpec("mode", "Transition", 0.0, 0.0, 1.0, 1.0, enumLabels = mapOf(0 to "In", 1 to "Out")),
            EffectParamSpec("direction", "Direction", 0.0, 0.0, 3.0, 1.0, enumLabels = mapOf(0 to "Left", 1 to "Right", 2 to "Up", 3 to "Down")),
            EffectParamSpec("distance", "Distance", 260.0, 40.0, 720.0, 10.0, "px"),
        ),
        performance = LOW_COST_TRANSITION_METADATA,
    )

    override fun evaluate(context: EffectEvaluationContext, out: EffectTransformState) {
        val rem = 1.0 - resolveTransitionProgress(context)
        val dist = context.param("distance", 260.0) * context.intensity
        when (context.param("direction", 0.0).toInt()) {
            1 -> out.dx = dist * rem
            2 -> out.dy = -dist * rem
            3 -> out.dy = dist * rem
            else -> out.dx = -dist * rem
        }
    }

    companion object {
        const val ID = "fx.transition.slide"
    }
}

/** 3. Zoom Transition (`fx.transition.zoom`) */
class ZoomTransitionEffect : BaseMatrixEffect() {
    override val definition = EffectModuleDefinition(
        id = ID,
        name = "Zoom Transition",
        category = EffectCategory.TRANSITION,
        phase = 5,
        description = "Dynamic zoom-in or zoom-out transition combining matrix scale and alpha.",
        targetTypes = ALL_LAYER_EFFECT_TARGETS,
        defaultDurationSec = 0.8,
        defaultLoop = false,
        params = listOf(
            EffectParamSpec("mode", "Transition", 0.0, 0.0, 1.0, 1.0, enumLabels = mapOf(0 to "In", 1 to "Out")),
            EffectParamSpec("zoomScale", "Edge Scale", 0.25, 0.05, 2.5, 0.05, "×"),
        ),
        performance = LOW_COST_TRANSITION_METADATA,
    )

    override fun evaluate(context: EffectEvaluationContext, out: EffectTransformState) {
        val p = resolveTransitionProgress(context)
        val edge = context.param("zoomScale", 0.25)
        val delta = (edge - 1.0) * (1.0 - p) * context.intensity
        out.dScaleX = delta
        out.dScaleY = delta
        out.overrideOpacity = p.coerceIn(0.0, 1.0)
    }

    companion object {
        const val ID = "fx.transition.zoom"
    }
}

/** 4. Wipe Transition (`fx.transition.wipe`) */
class WipeTransitionEffect : BaseMatrixEffect() {
    override val definition = EffectModuleDefinition(
        id = ID,
        name = "Wipe Transition",
        category = EffectCategory.TRANSITION,
        phase = 5,
        description = "Crisp directional wipe using hardware Canvas clipRect — zero shader overhead.",
        targetTypes = ALL_LAYER_EFFECT_TARGETS,
        defaultDurationSec = 0.9,
        defaultLoop = false,
        params = listOf(
            EffectParamSpec("mode", "Transition", 0.0, 0.0, 1.0, 1.0, enumLabels = mapOf(0 to "In", 1 to "Out")),
            EffectParamSpec("direction", "Wipe Edge", 0.0, 0.0, 3.0, 1.0, enumLabels = mapOf(0 to "L→R", 1 to "R→L", 2 to "T→B", 3 to "B→T")),
        ),
        performance = LOW_COST_TRANSITION_METADATA,
    )

    override fun evaluate(context: EffectEvaluationContext, out: EffectTransformState) {
        val p = resolveTransitionProgress(context)
        val dir = context.param("direction", 0.0).toInt().coerceIn(0, 3)
        out.visualFx = EvaluatedVisualFx(
            wipeDirection = dir,
            wipeProgress = p.toFloat().coerceIn(0f, 1f),
        )
    }

    companion object {
        const val ID = "fx.transition.wipe"
    }
}

/** 5. Dissolve Transition (`fx.transition.dissolve`) */
class DissolveTransitionEffect : BaseMatrixEffect() {
    override val definition = EffectModuleDefinition(
        id = ID,
        name = "Dissolve Transition",
        category = EffectCategory.TRANSITION,
        phase = 5,
        description = "Deterministic pixelated block dissolve combined with smooth alpha crossfade.",
        targetTypes = ALL_LAYER_EFFECT_TARGETS,
        defaultDurationSec = 1.0,
        defaultLoop = false,
        params = listOf(
            EffectParamSpec("mode", "Transition", 0.0, 0.0, 1.0, 1.0, enumLabels = mapOf(0 to "In", 1 to "Out")),
            EffectParamSpec("blockSize", "Grain Block", 16.0, 6.0, 36.0, 2.0, "px", isAdvanced = true),
        ),
        performance = LOW_COST_TRANSITION_METADATA,
    )

    override fun evaluate(context: EffectEvaluationContext, out: EffectTransformState) {
        val p = resolveTransitionProgress(context)
        val block = context.param("blockSize", 16.0).toFloat()
        out.overrideOpacity = (0.15 + 0.85 * p).coerceIn(0.0, 1.0)
        out.visualFx = EvaluatedVisualFx(
            dissolveProgress = p.toFloat().coerceIn(0f, 1f),
            dissolveBlockSize = block,
        )
    }

    companion object {
        const val ID = "fx.transition.dissolve"
    }
}

/** 6. Iris Transition (`fx.transition.iris`) */
class IrisTransitionEffect : BaseMatrixEffect() {
    override val definition = EffectModuleDefinition(
        id = ID,
        name = "Iris Transition",
        category = EffectCategory.TRANSITION,
        phase = 5,
        description = "Classic circular iris reveal/close using a lightweight single clipPath circle.",
        targetTypes = ALL_LAYER_EFFECT_TARGETS,
        defaultDurationSec = 0.9,
        defaultLoop = false,
        params = listOf(
            EffectParamSpec("mode", "Transition", 0.0, 0.0, 1.0, 1.0, enumLabels = mapOf(0 to "Open", 1 to "Close")),
        ),
        performance = LOW_COST_TRANSITION_METADATA,
    )

    override fun evaluate(context: EffectEvaluationContext, out: EffectTransformState) {
        val p = resolveTransitionProgress(context)
        out.visualFx = EvaluatedVisualFx(irisProgress = p.toFloat().coerceIn(0f, 1f))
    }

    companion object {
        const val ID = "fx.transition.iris"
    }
}

val PHASE5_TRANSITION_EFFECTS: List<Effect> = listOf(
    FadeTransitionEffect(),
    SlideTransitionEffect(),
    ZoomTransitionEffect(),
    WipeTransitionEffect(),
    DissolveTransitionEffect(),
    IrisTransitionEffect(),
)
