package com.example.effects

import android.graphics.Matrix
import com.example.engine.PresetTargetType
import com.example.schema.LayerDocument
import com.example.schema.MotionModifierInstance
import kotlin.math.max
import kotlin.math.roundToInt

/** Standard layer alias used by the modular [Effect] API. */
typealias Layer = LayerDocument

/** Standardized performance metadata alias carrying classification level, numeric cost, and low-end fallbacks. */
typealias EffectMetadata = EffectPerformanceMetadata

/** Alias for [EffectPerformanceMetadata] exposing `level` and `cost`. */
typealias EffectPerformance = EffectPerformanceMetadata

/** Standardized performance classification level (LOW, MEDIUM, HIGH). */
typealias PerformanceClassification = CostLevel

/**
 * Reusable evaluation context passed to [Effect.apply].
 * Reusing a single instance across a frame keeps per-frame heap allocations at zero.
 */
class EffectEvaluationContext(
    var modifier: MotionModifierInstance? = null,
    var normalizedTime: Double = 0.0,
    var frame: Double = 0.0,
    var objWidth: Double = 200.0,
    var objHeight: Double = 200.0,
    var deviceProfile: DevicePerformanceProfile = DevicePerformanceProfile.MID_RANGE,
    var reducedQuality: Boolean = false,
) {
    fun set(
        modifier: MotionModifierInstance,
        normalizedTime: Double,
        frame: Double,
        objWidth: Double,
        objHeight: Double,
        deviceProfile: DevicePerformanceProfile = DevicePerformanceProfile.MID_RANGE,
        reducedQuality: Boolean = false,
    ) {
        this.modifier = modifier
        this.normalizedTime = normalizedTime
        this.frame = frame
        this.objWidth = objWidth
        this.objHeight = objHeight
        this.deviceProfile = deviceProfile
        this.reducedQuality = reducedQuality
    }

    fun param(key: String, default: Double): Double =
        modifier?.params?.get(key) ?: default

    val intensity: Double
        get() = modifier?.intensity ?: 1.0

    val speed: Double
        get() = modifier?.speed ?: 1.0
}

/**
 * Reusable mutable output container populated by [Effect.apply].
 * Carries both lightweight structured transform deltas (for additive/multiplicative blending
 * with keyframed tracks and pivot anchors) and a ready-to-compose [Matrix2D].
 */
class EffectTransformState {
    var dx: Double = 0.0
    var dy: Double = 0.0
    var dScaleX: Double = 0.0
    var dScaleY: Double = 0.0
    var dRotDeg: Double = 0.0
    var dOpacity: Double = 0.0
    var dSkewXDeg: Double = 0.0
    var dSkewYDeg: Double = 0.0
    var flipX: Boolean = false
    var flipY: Boolean = false
    var anchorOffsetX: Double = 0.0
    var anchorOffsetY: Double = 0.0
    var overrideOpacity: Double? = null

    /** Optional Phase 2 lightweight mesh deformation (Hair Sway, Cloth Sway). */
    var meshDeform: com.example.engine.EvaluatedMeshDeform? = null

    /** Optional Phase 2/3/5 visual, clip-transition, or character overlay payload. */
    var visualFx: EvaluatedVisualFx? = null

    /** Optional Phase 4 pooled particle layer payload. */
    var particleFx: EvaluatedParticleLayerFx? = null

    /** Optional Phase 5 kinetic typography payload. */
    var textFx: EvaluatedTextFx? = null

    /** Optional Phase 5 audio-reactive visualization payload. */
    var audioFx: EvaluatedAudioFx? = null

    /** Local 2D affine matrix representing this effect's geometric transformation. */
    val matrix: Matrix2D = Matrix2D()

    fun reset() {
        dx = 0.0
        dy = 0.0
        dScaleX = 0.0
        dScaleY = 0.0
        dRotDeg = 0.0
        dOpacity = 0.0
        dSkewXDeg = 0.0
        dSkewYDeg = 0.0
        flipX = false
        flipY = false
        anchorOffsetX = 0.0
        anchorOffsetY = 0.0
        overrideOpacity = null
        meshDeform = null
        visualFx = null
        particleFx = null
        textFx = null
        audioFx = null
        matrix.reset()
    }

    /** Rebuilds [matrix] from the current structured transform deltas. */
    fun syncMatrixFromDeltas() {
        matrix.composeFromTransform(
            tx = dx,
            ty = dy,
            scaleX = max(0.05, 1.0 + dScaleX),
            scaleY = max(0.05, 1.0 + dScaleY),
            rotationDeg = dRotDeg,
            skewXDeg = dSkewXDeg,
            skewYDeg = dSkewYDeg,
            flipX = flipX,
            flipY = flipY,
            anchorOffsetX = anchorOffsetX,
            anchorOffsetY = anchorOffsetY,
        )
    }

    /** Effective opacity multiplier produced by this effect in [0, 1]. */
    fun effectiveOpacityMultiplier(): Double =
        (overrideOpacity ?: (1.0 + dOpacity)).coerceIn(0.0, 1.0)
}

/**
 * Base interface for all modular, reusable Motion Studio Pro effects in `com.example.effects`.
 *
 * Exposes standard properties ([id], [name], [performance] / [metadata]) and standard
 * transformation methods ([apply]) for applying lightweight 2D matrix transformations
 * to a [Layer] at a specific timeline [frame].
 *
 * Implementations must be stateless, deterministic for a given frame and layer state,
 * and avoid per-frame object allocations inside [apply].
 */
interface Effect {
    /** Unique stable identifier for this effect (e.g., `"fx.transform.position"`). */
    val id: String

    /** Human-readable display name shown in inspectors and registries. */
    val name: String get() = definition.name

    /**
     * Performance classification metadata documenting cost level ([CostLevel.LOW],
     * [CostLevel.MEDIUM], [CostLevel.HIGH]), CPU/GPU/memory costs, low-end tablet
     * compatibility, rendering technique, and lightweight fallbacks.
     */
    val performance: EffectPerformanceMetadata get() = definition.performance

    /** Standardized metadata alias pointing to [performance]. */
    val metadata: EffectMetadata get() = performance

    /** Full UI parameter schema and default configuration for this effect module. */
    val definition: EffectModuleDefinition

    val category: EffectCategory get() = definition.category
    val phase: Int get() = definition.phase

    /**
     * Applies this effect's lightweight 2D transformation to [layer] at [frame]
     * and returns an Android [Matrix] ready for `Canvas.concat(...)`.
     *
     * Uses any enabled modifier instances of this effect attached to [layer], or falls back
     * to this effect's default parameters when previewed standalone on a layer.
     */
    fun apply(layer: Layer, frame: Int): Matrix = apply(layer, frame, Matrix())

    /**
     * Zero-allocation overload of [apply] that writes the transformation directly into [outMatrix].
     */
    fun apply(layer: Layer, frame: Int, outMatrix: Matrix): Matrix {
        val m2d = applyMatrix2D(layer, frame)
        m2d.toAndroidMatrix(outMatrix)
        return outMatrix
    }

    /**
     * Pure-Kotlin lightweight 2D affine matrix evaluation for [layer] at [frame].
     * Ideal for deterministic evaluation, spatial hit-testing, and JVM unit tests.
     */
    fun applyMatrix2D(
        layer: Layer,
        frame: Int,
        outMatrix: Matrix2D = Matrix2D(),
    ): Matrix2D {
        outMatrix.reset()
        val objWidth = layer.objects.firstOrNull()?.width ?: 200.0
        val objHeight = layer.objects.firstOrNull()?.height ?: 200.0
        val frameDouble = frame.toDouble()
        val matching = layer.modifiers.filter { it.enabled && (it.basePresetId ?: it.presetId) == id }
        val instances = matching.ifEmpty {
            listOf(createDefaultInstance(fps = 24.0, instanceId = "ephemeral_$id", startFrame = 0))
        }

        val ctx = EffectEvaluationContext()
        val state = EffectTransformState()
        for (mod in instances) {
            val t = EffectRegistry.resolveNormalizedProgress(mod, frameDouble) ?: continue
            ctx.set(
                modifier = mod,
                normalizedTime = t,
                frame = frameDouble,
                objWidth = objWidth,
                objHeight = objHeight,
            )
            apply(ctx, state)
            outMatrix.preConcat(state.matrix)
        }
        return outMatrix
    }

    /**
     * Evaluates the effective opacity multiplier in `[0f, 1f]` produced by this effect
     * on [layer] at [frame].
     */
    fun evaluateOpacity(layer: Layer, frame: Int): Float {
        val objWidth = layer.objects.firstOrNull()?.width ?: 200.0
        val objHeight = layer.objects.firstOrNull()?.height ?: 200.0
        val frameDouble = frame.toDouble()
        val matching = layer.modifiers.filter { it.enabled && (it.basePresetId ?: it.presetId) == id }
        val instances = matching.ifEmpty {
            listOf(createDefaultInstance(fps = 24.0, instanceId = "ephemeral_$id", startFrame = 0))
        }

        val ctx = EffectEvaluationContext()
        val state = EffectTransformState()
        var opacity = 1.0
        for (mod in instances) {
            val t = EffectRegistry.resolveNormalizedProgress(mod, frameDouble) ?: continue
            ctx.set(
                modifier = mod,
                normalizedTime = t,
                frame = frameDouble,
                objWidth = objWidth,
                objHeight = objHeight,
            )
            apply(ctx, state)
            opacity = (opacity * state.effectiveOpacityMultiplier()).coerceIn(0.0, 1.0)
        }
        return opacity.toFloat()
    }

    /**
     * Low-level zero-allocation evaluation method used inside the composition render loop.
     * Evaluates this effect for [context] and writes the resulting lightweight 2D
     * transform deltas and affine [EffectTransformState.matrix] into [out].
     */
    fun apply(context: EffectEvaluationContext, out: EffectTransformState)

    /**
     * Instantiates a serializable [MotionModifierInstance] configured with this effect's
     * default parameters, timing, and pivot anchor.
     */
    fun createDefaultInstance(
        fps: Double,
        instanceId: String,
        startFrame: Int = 0,
    ): MotionModifierInstance {
        val durFrames = max(1, (definition.defaultDurationSec * max(1.0, fps)).roundToInt())
        return MotionModifierInstance(
            id = instanceId,
            presetId = definition.id,
            basePresetId = definition.id,
            label = definition.name,
            enabled = true,
            startFrame = startFrame,
            durationFrames = durFrames,
            delayFrames = 0,
            loop = definition.defaultLoop,
            direction = definition.defaultDirection,
            blendMode = definition.defaultBlendMode,
            intensity = definition.defaultIntensity,
            speed = 1.0,
            phaseOffset = 0.0,
            pivotAnchor = definition.defaultPivot,
            params = definition.defaultParamMap(),
        )
    }

    /** Lifecycle hook invoked when this effect is registered in an [EffectRegistry]. */
    fun onRegistered() {}

    /** Lifecycle hook invoked when this effect is unregistered or replaced in an [EffectRegistry]. */
    fun onUnregistered() {}

    /** Lifecycle hook invoked when the owning [EffectRegistry] is released to free cached resources. */
    fun onRelease() {}
}

/**
 * Convenience base class for lightweight 2D matrix effects.
 * Resets [out], delegates pure deterministic math to [evaluate], and syncs [EffectTransformState.matrix].
 */
abstract class BaseMatrixEffect : Effect {
    override val id: String get() = definition.id
    override val name: String get() = definition.name
    override val performance: EffectPerformanceMetadata get() = definition.performance

    final override fun apply(context: EffectEvaluationContext, out: EffectTransformState) {
        out.reset()
        evaluate(context, out)
        out.syncMatrixFromDeltas()
    }

    protected abstract fun evaluate(context: EffectEvaluationContext, out: EffectTransformState)
}

internal val ALL_LAYER_EFFECT_TARGETS = listOf(
    PresetTargetType.SPRITE,
    PresetTargetType.CHARACTER,
    PresetTargetType.SHAPE,
    PresetTargetType.TEXT,
    PresetTargetType.IMAGE,
    PresetTargetType.LAYER,
    PresetTargetType.NESTED_COMPOSITION,
)

internal val LOW_COST_TRANSFORM_METADATA = EffectPerformanceMetadata(
    level = CostLevel.LOW,
    cpuCost = CostLevel.LOW,
    gpuCost = CostLevel.LOW,
    memoryCost = CostLevel.LOW,
    supportsLowEnd = true,
    fallbackEffect = null,
    renderingTechnique = "2D Matrix Transformation / Alpha Composition",
    maxRecommendedParticles = 0,
    textureRequirements = "None (zero texture regeneration)",
    dependencies = emptyList(),
    lightweightAlternatives = emptyList(),
    knownLimitations = listOf("Operates in 2D affine space"),
    qualityReductionOptions = listOf("No reduction needed; negligible cost"),
)

internal val LOW_COST_MOTION_METADATA = EffectPerformanceMetadata(
    level = CostLevel.LOW,
    cpuCost = CostLevel.LOW,
    gpuCost = CostLevel.LOW,
    memoryCost = CostLevel.LOW,
    supportsLowEnd = true,
    fallbackEffect = null,
    renderingTechnique = "Deterministic Analytical Math Curve + 2D Matrix Transform",
    maxRecommendedParticles = 0,
    textureRequirements = "None (pure transform math)",
    dependencies = emptyList(),
    lightweightAlternatives = emptyList(),
    knownLimitations = listOf("Procedural math curve; use Bake to Keyframes for per-frame manual tweaks"),
    qualityReductionOptions = listOf("Can be baked to static keyframes on ultra-low-end devices"),
)
