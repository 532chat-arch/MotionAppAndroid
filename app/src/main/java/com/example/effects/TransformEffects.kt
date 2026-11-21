package com.example.effects

import com.example.engine.applyEasing
import com.example.schema.InterpolationType
import com.example.schema.PivotAnchorPreset
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin

private fun easingFromCode(code: Int): InterpolationType = when (code) {
    0 -> InterpolationType.LINEAR
    1 -> InterpolationType.EASE_IN
    2 -> InterpolationType.EASE_OUT
    4 -> InterpolationType.HOLD
    else -> InterpolationType.EASE_IN_OUT
}

private fun deterministicHash01(seed: Double): Double {
    val v = sin(seed * 12.9898 + 78.233) * 43758.5453
    return v - floor(v)
}

/**
 * Phase 1 Transform Effect: Position Slide (`fx.transform.position`).
 * Applies an eased 2D translation matrix delta without touching textures.
 */
class PositionEffect : BaseMatrixEffect() {
    override val definition: EffectModuleDefinition = EffectModuleDefinition(
        id = ID,
        name = "Position Slide",
        category = EffectCategory.TRANSFORM,
        phase = 1,
        description = "Lightweight matrix position slide from or to an X/Y offset with smooth easing.",
        targetTypes = ALL_LAYER_EFFECT_TARGETS,
        defaultDurationSec = 1.2,
        defaultLoop = false,
        params = listOf(
            EffectParamSpec("offsetX", "Offset X", 0.0, -400.0, 400.0, 5.0, "px"),
            EffectParamSpec("offsetY", "Offset Y", -80.0, -400.0, 400.0, 5.0, "px"),
            EffectParamSpec("animateFromOffset", "Slide In (1) / Out (0)", 1.0, 0.0, 1.0, 1.0, isAdvanced = true, enumLabels = mapOf(0 to "Out", 1 to "In")),
            EffectParamSpec("easingMode", "Easing", 3.0, 0.0, 3.0, 1.0, isAdvanced = true, enumLabels = mapOf(0 to "Linear", 1 to "Ease-In", 2 to "Ease-Out", 3 to "In-Out")),
        ),
        performance = LOW_COST_TRANSFORM_METADATA,
    )

    override fun evaluate(context: EffectEvaluationContext, out: EffectTransformState) {
        val ox = context.param("offsetX", 0.0) * context.intensity
        val oy = context.param("offsetY", -80.0) * context.intensity
        val mode = context.param("easingMode", 3.0).toInt()
        val reverseStart = context.param("animateFromOffset", 1.0) >= 0.5
        val eased = applyEasing(context.normalizedTime, easingFromCode(mode))
        val factor = if (reverseStart) (1.0 - eased) else eased
        out.dx = ox * factor
        out.dy = oy * factor
    }

    companion object {
        const val ID = "fx.transform.position"
    }
}

/**
 * Phase 1 Transform Effect: Scale Pop (`fx.transform.scale`).
 * GPU-friendly uniform or non-uniform 2D matrix scaling.
 */
class ScaleEffect : BaseMatrixEffect() {
    override val definition: EffectModuleDefinition = EffectModuleDefinition(
        id = ID,
        name = "Scale Pop",
        category = EffectCategory.TRANSFORM,
        phase = 1,
        description = "GPU-friendly uniform or non-uniform scale transition without bitmap reallocation.",
        targetTypes = ALL_LAYER_EFFECT_TARGETS,
        defaultDurationSec = 1.0,
        defaultLoop = false,
        params = listOf(
            EffectParamSpec("scaleX", "Start Scale", 0.35, 0.05, 2.5, 0.05, "×"),
            EffectParamSpec("uniform", "Lock Aspect", 1.0, 0.0, 1.0, 1.0, enumLabels = mapOf(0 to "Free", 1 to "Lock")),
            EffectParamSpec("scaleY", "Scale Y", 0.35, 0.05, 2.5, 0.05, "×", isAdvanced = true),
            EffectParamSpec("fromDelta", "Pop In (1) / Out (0)", 1.0, 0.0, 1.0, 1.0, isAdvanced = true, enumLabels = mapOf(0 to "Out", 1 to "In")),
            EffectParamSpec("easingMode", "Easing", 2.0, 0.0, 3.0, 1.0, isAdvanced = true, enumLabels = mapOf(0 to "Linear", 1 to "Ease-In", 2 to "Ease-Out", 3 to "In-Out")),
        ),
        performance = LOW_COST_TRANSFORM_METADATA,
    )

    override fun evaluate(context: EffectEvaluationContext, out: EffectTransformState) {
        val uniform = context.param("uniform", 1.0) >= 0.5
        val sxTarget = context.param("scaleX", 0.35)
        val syTarget = if (uniform) sxTarget else context.param("scaleY", 0.35)
        val mode = context.param("easingMode", 2.0).toInt()
        val fromZero = context.param("fromDelta", 1.0) >= 0.5
        val eased = applyEasing(context.normalizedTime, easingFromCode(mode))
        val factor = if (fromZero) (1.0 - eased) else eased
        out.dScaleX = (sxTarget - 1.0) * factor * context.intensity
        out.dScaleY = (syTarget - 1.0) * factor * context.intensity
    }

    companion object {
        const val ID = "fx.transform.scale"
    }
}

/**
 * Phase 1 Transform Effect: Rotation Spin (`fx.transform.rotation`).
 * Smooth continuous spin or eased angle rotation around pivot.
 */
class RotationEffect : BaseMatrixEffect() {
    override val definition: EffectModuleDefinition = EffectModuleDefinition(
        id = ID,
        name = "Rotation Spin",
        category = EffectCategory.TRANSFORM,
        phase = 1,
        description = "Smooth continuous spin or eased angle turn around the selected pivot anchor.",
        targetTypes = ALL_LAYER_EFFECT_TARGETS,
        defaultDurationSec = 3.0,
        defaultLoop = true,
        params = listOf(
            EffectParamSpec("angleDeg", "Angle", 360.0, -720.0, 720.0, 15.0, "°"),
            EffectParamSpec("continuousSpin", "Spin Mode", 1.0, 0.0, 1.0, 1.0, enumLabels = mapOf(0 to "Eased", 1 to "Spin")),
            EffectParamSpec("easingMode", "Easing", 3.0, 0.0, 3.0, 1.0, isAdvanced = true, enumLabels = mapOf(0 to "Linear", 1 to "Ease-In", 2 to "Ease-Out", 3 to "In-Out")),
        ),
        performance = LOW_COST_TRANSFORM_METADATA,
    )

    override fun evaluate(context: EffectEvaluationContext, out: EffectTransformState) {
        val angleDeg = context.param("angleDeg", 360.0) * context.intensity
        val continuous = context.param("continuousSpin", 1.0) >= 0.5
        val mode = context.param("easingMode", 0.0).toInt()
        val eased = if (continuous) context.normalizedTime else applyEasing(context.normalizedTime, easingFromCode(mode))
        out.dRotDeg = angleDeg * eased
    }

    companion object {
        const val ID = "fx.transform.rotation"
    }
}

/**
 * Phase 1 Transform Effect: Opacity Fade / Pulse / Flicker (`fx.transform.opacity`).
 * Uses lightweight alpha composition with identity geometry matrix.
 */
class OpacityEffect : BaseMatrixEffect() {
    override val definition: EffectModuleDefinition = EffectModuleDefinition(
        id = ID,
        name = "Opacity Fade",
        category = EffectCategory.TRANSFORM,
        phase = 1,
        description = "Fade in, fade out, smooth pulse, or horror flicker using alpha compositing.",
        targetTypes = ALL_LAYER_EFFECT_TARGETS,
        defaultDurationSec = 1.2,
        defaultLoop = false,
        params = listOf(
            EffectParamSpec("fromOpacity", "Start Opacity", 0.0, 0.0, 1.0, 0.05),
            EffectParamSpec("toOpacity", "End Opacity", 1.0, 0.0, 1.0, 0.05),
            EffectParamSpec("fadeMode", "Mode", 0.0, 0.0, 2.0, 1.0, isAdvanced = true, enumLabels = mapOf(0 to "Fade", 1 to "Pulse", 2 to "Flicker")),
        ),
        performance = LOW_COST_TRANSFORM_METADATA,
    )

    override fun evaluate(context: EffectEvaluationContext, out: EffectTransformState) {
        val fromOp = context.param("fromOpacity", 0.0).coerceIn(0.0, 1.0)
        val toOp = context.param("toOpacity", 1.0).coerceIn(0.0, 1.0)
        val fadeMode = context.param("fadeMode", 0.0).toInt()
        val phase = context.normalizedTime * 2.0 * PI
        val raw = when (fadeMode) {
            1 -> {
                val pulse = 0.5 - 0.5 * cos(phase)
                fromOp + (toOp - fromOp) * pulse
            }
            2 -> {
                val step = floor(context.frame * max(0.5, context.speed))
                if (deterministicHash01(step * 17.3 + 4.1) > 0.35) toOp else fromOp
            }
            else -> {
                val eased = applyEasing(context.normalizedTime, InterpolationType.EASE_IN_OUT)
                fromOp + (toOp - fromOp) * eased
            }
        }
        val blended = 1.0 + (raw - 1.0) * context.intensity.coerceIn(0.0, 1.5)
        out.overrideOpacity = blended.coerceIn(0.0, 1.0)
    }

    companion object {
        const val ID = "fx.transform.opacity"
    }
}

/**
 * Phase 1 Transform Effect: Anchor Point (`fx.transform.anchor`).
 * Offsets the pivot origin in local layer space for downstream matrix rotations and scales.
 */
class AnchorPointEffect : BaseMatrixEffect() {
    override val definition: EffectModuleDefinition = EffectModuleDefinition(
        id = ID,
        name = "Anchor Point",
        category = EffectCategory.TRANSFORM,
        phase = 1,
        description = "Shifts the transform pivot origin for downstream rotation, scale, and swing effects.",
        targetTypes = ALL_LAYER_EFFECT_TARGETS,
        defaultDurationSec = 4.0,
        defaultLoop = true,
        params = listOf(
            EffectParamSpec("anchorX", "Anchor X", 0.5, 0.0, 1.0, 0.05),
            EffectParamSpec("anchorY", "Anchor Y", 0.5, 0.0, 1.0, 0.05),
        ),
        performance = LOW_COST_TRANSFORM_METADATA,
    )

    override fun evaluate(context: EffectEvaluationContext, out: EffectTransformState) {
        out.anchorOffsetX = (context.param("anchorX", 0.5) - 0.5) * context.objWidth * context.intensity
        out.anchorOffsetY = (context.param("anchorY", 0.5) - 0.5) * context.objHeight * context.intensity
    }

    companion object {
        const val ID = "fx.transform.anchor"
    }
}

/**
 * Phase 1 Transform Effect: 2D Skew / Shear (`fx.transform.skew`).
 * Applies GPU-friendly affine matrix shear.
 */
class SkewEffect : BaseMatrixEffect() {
    override val definition: EffectModuleDefinition = EffectModuleDefinition(
        id = ID,
        name = "2D Skew / Shear",
        category = EffectCategory.TRANSFORM,
        phase = 1,
        description = "Fast GPU matrix shear for wind lean, speed tilt, or kinetic typography slant.",
        targetTypes = ALL_LAYER_EFFECT_TARGETS,
        defaultDurationSec = 2.5,
        defaultLoop = true,
        defaultPivot = PivotAnchorPreset.BOTTOM,
        params = listOf(
            EffectParamSpec("skewXDeg", "Skew X", 12.0, -45.0, 45.0, 1.0, "°"),
            EffectParamSpec("skewYDeg", "Skew Y", 0.0, -45.0, 45.0, 1.0, "°"),
            EffectParamSpec("oscillate", "Oscillate", 1.0, 0.0, 1.0, 1.0, isAdvanced = true, enumLabels = mapOf(0 to "Static", 1 to "Wave")),
        ),
        performance = LOW_COST_TRANSFORM_METADATA,
    )

    override fun evaluate(context: EffectEvaluationContext, out: EffectTransformState) {
        val sx = context.param("skewXDeg", 12.0) * context.intensity
        val sy = context.param("skewYDeg", 0.0) * context.intensity
        val animateWave = context.param("oscillate", 1.0) >= 0.5
        val factor = if (animateWave) sin(context.normalizedTime * 2.0 * PI) else 1.0
        out.dSkewXDeg = sx * factor
        out.dSkewYDeg = sy * factor
    }

    companion object {
        const val ID = "fx.transform.skew"
    }
}

/**
 * Phase 1 Transform Effect: Horizontal / Vertical Flip (`fx.transform.flip`).
 * Zero-cost negative matrix scaling along X and/or Y.
 */
class FlipEffect : BaseMatrixEffect() {
    override val definition: EffectModuleDefinition = EffectModuleDefinition(
        id = ID,
        name = "Flip H / V",
        category = EffectCategory.TRANSFORM,
        phase = 1,
        description = "Zero-cost horizontal or vertical mirror flip via negative matrix scale.",
        targetTypes = ALL_LAYER_EFFECT_TARGETS,
        defaultDurationSec = 2.0,
        defaultLoop = true,
        params = listOf(
            EffectParamSpec("flipX", "Horizontal Flip", 1.0, 0.0, 1.0, 1.0, enumLabels = mapOf(0 to "Off", 1 to "On")),
            EffectParamSpec("flipY", "Vertical Flip", 0.0, 0.0, 1.0, 1.0, enumLabels = mapOf(0 to "Off", 1 to "On")),
            EffectParamSpec("alternate", "Alternate Cycle", 0.0, 0.0, 1.0, 1.0, isAdvanced = true, enumLabels = mapOf(0 to "Hold", 1 to "Toggle")),
        ),
        performance = LOW_COST_TRANSFORM_METADATA,
    )

    override fun evaluate(context: EffectEvaluationContext, out: EffectTransformState) {
        val fx = context.param("flipX", 1.0) >= 0.5
        val fy = context.param("flipY", 0.0) >= 0.5
        val alternate = context.param("alternate", 0.0) >= 0.5
        val activePhase = if (alternate) (context.normalizedTime < 0.5) else true
        out.flipX = fx && activePhase
        out.flipY = fy && activePhase
    }

    companion object {
        const val ID = "fx.transform.flip"
    }
}

/**
 * Phase 1 Transform Effect: Follow Path (`fx.transform.follow_path`).
 * Parametric 2D path motion (Orbit, Arc, Figure-8, Wave) with optional analytical tangent auto-orient.
 */
class FollowPathEffect : BaseMatrixEffect() {
    override val definition: EffectModuleDefinition = EffectModuleDefinition(
        id = ID,
        name = "Follow Path",
        category = EffectCategory.TRANSFORM,
        phase = 1,
        description = "Moves layer along an orbit, arc, figure-8, or wave path with optional tangent auto-orient.",
        targetTypes = ALL_LAYER_EFFECT_TARGETS,
        defaultDurationSec = 4.0,
        defaultLoop = true,
        params = listOf(
            EffectParamSpec("pathType", "Path Shape", 0.0, 0.0, 3.0, 1.0, enumLabels = mapOf(0 to "Orbit", 1 to "Arc", 2 to "Figure-8", 3 to "Wave")),
            EffectParamSpec("radiusX", "Width / Radius X", 140.0, 10.0, 450.0, 5.0, "px"),
            EffectParamSpec("radiusY", "Height / Radius Y", 70.0, 0.0, 350.0, 5.0, "px"),
            EffectParamSpec("autoOrient", "Auto-Orient", 1.0, 0.0, 1.0, 1.0, enumLabels = mapOf(0 to "Off", 1 to "On")),
            EffectParamSpec("orientOffsetDeg", "Orient Offset", 0.0, -180.0, 180.0, 5.0, "°", isAdvanced = true),
            EffectParamSpec("arcSpanDeg", "Arc Span", 180.0, 30.0, 360.0, 10.0, "°", isAdvanced = true),
        ),
        performance = LOW_COST_TRANSFORM_METADATA,
    )

    private fun evalPathX(pathType: Int, u: Double, rx: Double, arcSpanRad: Double): Double = when (pathType) {
        1 -> sin(-arcSpanRad * 0.5 + u * arcSpanRad) * rx
        2 -> sin(u * 2.0 * PI) * rx
        3 -> (u - 0.5) * 2.0 * rx
        else -> cos(u * 2.0 * PI) * rx
    }

    private fun evalPathY(pathType: Int, u: Double, ry: Double, arcSpanRad: Double): Double = when (pathType) {
        1 -> (1.0 - cos(-arcSpanRad * 0.5 + u * arcSpanRad)) * -ry
        2 -> sin(u * 4.0 * PI) * ry * 0.5
        3 -> sin(u * 2.0 * PI) * ry
        else -> sin(u * 2.0 * PI) * ry
    }

    override fun evaluate(context: EffectEvaluationContext, out: EffectTransformState) {
        val pathType = context.param("pathType", 0.0).toInt()
        val rx = context.param("radiusX", 140.0) * context.intensity
        val ry = context.param("radiusY", 70.0) * context.intensity
        val autoOrient = context.param("autoOrient", 1.0) >= 0.5
        val orientOffset = context.param("orientOffsetDeg", 0.0)
        val arcSpanRad = context.param("arcSpanDeg", 180.0) * PI / 180.0
        val t = context.normalizedTime

        out.dx = evalPathX(pathType, t, rx, arcSpanRad)
        out.dy = evalPathY(pathType, t, ry, arcSpanRad)

        if (autoOrient) {
            val eps = 0.004
            val uNext = (t + eps).coerceAtMost(1.0)
            val uPrev = (t - eps).coerceAtLeast(0.0)
            val tx = evalPathX(pathType, uNext, rx, arcSpanRad) - evalPathX(pathType, uPrev, rx, arcSpanRad)
            val ty = evalPathY(pathType, uNext, ry, arcSpanRad) - evalPathY(pathType, uPrev, ry, arcSpanRad)
            if (abs(tx) > 1e-4 || abs(ty) > 1e-4) {
                out.dRotDeg = atan2(ty, tx) * 180.0 / PI + orientOffset
            }
        }
    }

    companion object {
        const val ID = "fx.transform.follow_path"
    }
}

val PHASE1_TRANSFORM_EFFECTS: List<Effect> = listOf(
    PositionEffect(),
    ScaleEffect(),
    RotationEffect(),
    OpacityEffect(),
    AnchorPointEffect(),
    SkewEffect(),
    FlipEffect(),
    FollowPathEffect(),
)
