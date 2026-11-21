package com.example.effects

import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin

private fun colorFromPreset(code: Int): Int = when (code) {
    0 -> 0xFFFFFFFF.toInt() // White
    1 -> 0xFF000000.toInt() // Black
    2 -> 0xFF38BDF8.toInt() // Cyan
    3 -> 0xFFF59E0B.toInt() // Amber Gold
    4 -> 0xFFEC4899.toInt() // Neon Pink
    5 -> 0xFF22C55E.toInt() // Emerald
    6 -> 0xFFEF4444.toInt() // Crimson
    else -> 0xFF60A5FA.toInt() // Studio Blue
}

private val COLOR_PRESET_LABELS = mapOf(
    0 to "White",
    1 to "Black",
    2 to "Cyan",
    3 to "Gold",
    4 to "Pink",
    5 to "Green",
    6 to "Red",
)

private val LOW_COST_COLOR_MATRIX_META = EffectPerformanceMetadata(
    level = CostLevel.LOW,
    cpuCost = CostLevel.LOW,
    gpuCost = CostLevel.LOW,
    memoryCost = CostLevel.LOW,
    supportsLowEnd = true,
    fallbackEffect = null,
    renderingTechnique = "Single-Pass GPU ColorMatrixColorFilter",
    maxRecommendedParticles = 0,
    textureRequirements = "None (combined with other color filters into 1 pass)",
    knownLimitations = listOf("Applies uniformly across the layer bounds"),
    qualityReductionOptions = listOf("No reduction required; single matrix multiply on GPU"),
)

/**
 * Phase 3 Visual Effect: Blur (`fx.visual.blur`).
 * Soft blur with automatic Low-End reduced-radius approximation and fallback guidance.
 */
class BlurEffect : BaseMatrixEffect() {
    override val definition: EffectModuleDefinition = EffectModuleDefinition(
        id = ID,
        name = "Blur",
        category = EffectCategory.VISUAL,
        phase = 3,
        description = "Soft focus blur with automatic lightweight approximation on Low-End devices.",
        targetTypes = ALL_LAYER_EFFECT_TARGETS,
        defaultDurationSec = 2.0,
        defaultLoop = true,
        params = listOf(
            EffectParamSpec("radius", "Blur Radius", 8.0, 0.0, 28.0, 1.0, "px"),
            EffectParamSpec("animatePulse", "Animate", 0.0, 0.0, 1.0, 1.0, isAdvanced = true, enumLabels = mapOf(0 to "Static", 1 to "Pulse")),
        ),
        performance = EffectPerformanceMetadata(
            level = CostLevel.HIGH,
            cpuCost = CostLevel.MEDIUM,
            gpuCost = CostLevel.HIGH,
            memoryCost = CostLevel.MEDIUM,
            supportsLowEnd = false,
            fallbackEffect = OpacityEffect.ID,
            renderingTechnique = "Single-Pass BlurMaskFilter (Low-End soft alpha fallback)",
            maxRecommendedParticles = 0,
            textureRequirements = "Temporary offscreen layer only when active",
            lightweightAlternatives = listOf(OpacityEffect.ID, VignetteEffect.ID),
            knownLimitations = listOf("Avoid stacking multiple blurs simultaneously on 3-4 GB tablets"),
            qualityReductionOptions = listOf("Caps radius and uses lightweight soft offset in Performance mode"),
        ),
    )

    override fun evaluate(context: EffectEvaluationContext, out: EffectTransformState) {
        val baseRadius = context.param("radius", 8.0) * context.intensity
        val pulse = context.param("animatePulse", 0.0) >= 0.5
        val factor = if (pulse) (0.5 - 0.5 * kotlin.math.cos(context.normalizedTime * 2.0 * PI)) else 1.0
        val cap = if (context.deviceProfile == DevicePerformanceProfile.LOW_END || context.reducedQuality) 6.0 else 28.0
        out.visualFx = EvaluatedVisualFx(blurRadius = (baseRadius * factor).coerceIn(0.0, cap).toFloat())
    }

    companion object {
        const val ID = "fx.visual.blur"
    }
}

/**
 * Phase 3 Visual Effect: Drop Shadow (`fx.visual.drop_shadow`).
 * Lightweight offset tinted silhouette shadow (sharp pass on Low-End, soft pass on Mid/High).
 */
class DropShadowEffect : BaseMatrixEffect() {
    override val definition: EffectModuleDefinition = EffectModuleDefinition(
        id = ID,
        name = "Drop Shadow",
        category = EffectCategory.VISUAL,
        phase = 3,
        description = "Lightweight offset drop shadow that grounds characters and floating titles.",
        targetTypes = ALL_LAYER_EFFECT_TARGETS,
        defaultDurationSec = 4.0,
        defaultLoop = true,
        params = listOf(
            EffectParamSpec("offsetX", "Offset X", 8.0, -40.0, 40.0, 1.0, "px"),
            EffectParamSpec("offsetY", "Offset Y", 12.0, -40.0, 40.0, 1.0, "px"),
            EffectParamSpec("alpha", "Shadow Opacity", 0.55, 0.0, 1.0, 0.05),
            EffectParamSpec("softness", "Softness", 6.0, 0.0, 20.0, 1.0, "px", isAdvanced = true),
            EffectParamSpec("colorPreset", "Color", 1.0, 0.0, 6.0, 1.0, isAdvanced = true, enumLabels = COLOR_PRESET_LABELS),
        ),
        performance = EffectPerformanceMetadata(
            level = CostLevel.LOW,
            cpuCost = CostLevel.LOW,
            gpuCost = CostLevel.LOW,
            memoryCost = CostLevel.LOW,
            supportsLowEnd = true,
            fallbackEffect = null,
            renderingTechnique = "Single Offset Silhouette Pass (Sharp on Low-End)",
            textureRequirements = "Reuses source layer geometry",
            qualityReductionOptions = listOf("Disables shadow blur kernel on Performance preview"),
        ),
    )

    override fun evaluate(context: EffectEvaluationContext, out: EffectTransformState) {
        val ox = context.param("offsetX", 8.0) * context.intensity
        val oy = context.param("offsetY", 12.0) * context.intensity
        val alpha = (context.param("alpha", 0.55) * context.intensity).coerceIn(0.0, 1.0)
        val soft = if (context.reducedQuality) 0.0 else context.param("softness", 6.0)
        val color = colorFromPreset(context.param("colorPreset", 1.0).toInt())
        out.visualFx = EvaluatedVisualFx(
            shadowOffsetX = ox.toFloat(),
            shadowOffsetY = oy.toFloat(),
            shadowRadius = soft.toFloat(),
            shadowAlpha = alpha.toFloat(),
            shadowColorInt = color,
        )
    }

    companion object {
        const val ID = "fx.visual.drop_shadow"
    }
}

/**
 * Phase 3 Visual Effect: Outline (`fx.visual.outline`).
 * Lightweight 4-way/8-way crisp stroke silhouette around sprites, shapes, and text.
 */
class OutlineEffect : BaseMatrixEffect() {
    override val definition: EffectModuleDefinition = EffectModuleDefinition(
        id = ID,
        name = "Outline",
        category = EffectCategory.VISUAL,
        phase = 3,
        description = "Crisp sticker-style silhouette outline ideal for anime characters and bold titles.",
        targetTypes = ALL_LAYER_EFFECT_TARGETS,
        defaultDurationSec = 4.0,
        defaultLoop = true,
        params = listOf(
            EffectParamSpec("width", "Outline Width", 4.0, 1.0, 16.0, 0.5, "px"),
            EffectParamSpec("colorPreset", "Outline Color", 0.0, 0.0, 6.0, 1.0, enumLabels = COLOR_PRESET_LABELS),
        ),
        performance = EffectPerformanceMetadata(
            level = CostLevel.LOW,
            cpuCost = CostLevel.LOW,
            gpuCost = CostLevel.LOW,
            memoryCost = CostLevel.LOW,
            supportsLowEnd = true,
            renderingTechnique = "Lightweight 4-Way Offset Silhouette Stroke",
            textureRequirements = "None (reuses layer draw commands)",
        ),
    )

    override fun evaluate(context: EffectEvaluationContext, out: EffectTransformState) {
        val w = (context.param("width", 4.0) * context.intensity).coerceIn(0.0, 18.0)
        val color = colorFromPreset(context.param("colorPreset", 0.0).toInt())
        out.visualFx = EvaluatedVisualFx(
            outlineWidth = w.toFloat(),
            outlineColorInt = color,
        )
    }

    companion object {
        const val ID = "fx.visual.outline"
    }
}

/**
 * Phase 3 Visual Effect: Glow (`fx.visual.glow`).
 * Additive halo glow with lightweight scaled-translucent approximation on Low-End tablets.
 */
class GlowEffect : BaseMatrixEffect() {
    override val definition: EffectModuleDefinition = EffectModuleDefinition(
        id = ID,
        name = "Glow",
        category = EffectCategory.VISUAL,
        phase = 3,
        description = "Radiant neon or cinematic halo with lightweight low-end halo fallback.",
        targetTypes = ALL_LAYER_EFFECT_TARGETS,
        defaultDurationSec = 2.4,
        defaultLoop = true,
        params = listOf(
            EffectParamSpec("radius", "Glow Size", 16.0, 2.0, 42.0, 1.0, "px"),
            EffectParamSpec("strength", "Glow Strength", 0.75, 0.1, 1.5, 0.05),
            EffectParamSpec("colorPreset", "Glow Color", 2.0, 0.0, 6.0, 1.0, enumLabels = COLOR_PRESET_LABELS),
            EffectParamSpec("pulse", "Pulse Glow", 1.0, 0.0, 1.0, 1.0, isAdvanced = true, enumLabels = mapOf(0 to "Steady", 1 to "Pulse")),
        ),
        performance = EffectPerformanceMetadata(
            level = CostLevel.MEDIUM,
            cpuCost = CostLevel.LOW,
            gpuCost = CostLevel.MEDIUM,
            memoryCost = CostLevel.LOW,
            supportsLowEnd = true,
            fallbackEffect = PulseEffect.ID,
            renderingTechnique = "Single Additive Halo (2-Circle/Scaled Approx on Low-End)",
            textureRequirements = "No separate bloom pyramid required",
            lightweightAlternatives = listOf(PulseEffect.ID, OutlineEffect.ID),
            qualityReductionOptions = listOf("Uses single soft radial halo instead of blur pass on Low-End"),
        ),
    )

    override fun evaluate(context: EffectEvaluationContext, out: EffectTransformState) {
        val radius = context.param("radius", 16.0) * context.intensity
        val strength = context.param("strength", 0.75) * context.intensity
        val color = colorFromPreset(context.param("colorPreset", 2.0).toInt())
        val pulse = context.param("pulse", 1.0) >= 0.5
        val factor = if (pulse) (0.72 + 0.28 * sin(context.normalizedTime * 2.0 * PI)) else 1.0
        out.visualFx = EvaluatedVisualFx(
            glowRadius = (radius * factor).coerceIn(0.0, 48.0).toFloat(),
            glowIntensity = (strength * factor).coerceIn(0.0, 1.5).toFloat(),
            glowColorInt = color,
        )
    }

    companion object {
        const val ID = "fx.visual.glow"
    }
}

/**
 * Phase 3 Visual Effect: Brightness (`fx.visual.brightness`).
 */
class BrightnessEffect : BaseMatrixEffect() {
    override val definition: EffectModuleDefinition = EffectModuleDefinition(
        id = ID,
        name = "Brightness",
        category = EffectCategory.VISUAL,
        phase = 3,
        description = "GPU-friendly color-matrix brightness adjustment for flash hits and exposure shifts.",
        targetTypes = ALL_LAYER_EFFECT_TARGETS,
        defaultDurationSec = 2.0,
        defaultLoop = true,
        params = listOf(
            EffectParamSpec("amount", "Brightness", 0.25, -0.8, 0.8, 0.05),
            EffectParamSpec("animateFlash", "Mode", 0.0, 0.0, 1.0, 1.0, isAdvanced = true, enumLabels = mapOf(0 to "Constant", 1 to "Flash")),
        ),
        performance = LOW_COST_COLOR_MATRIX_META,
    )

    override fun evaluate(context: EffectEvaluationContext, out: EffectTransformState) {
        val amt = context.param("amount", 0.25) * context.intensity
        val flash = context.param("animateFlash", 0.0) >= 0.5
        val f = if (flash) (1.0 - context.normalizedTime) else 1.0
        out.visualFx = EvaluatedVisualFx(brightness = (amt * f).coerceIn(-1.0, 1.0).toFloat())
    }

    companion object {
        const val ID = "fx.visual.brightness"
    }
}

/**
 * Phase 3 Visual Effect: Contrast (`fx.visual.contrast`).
 */
class ContrastEffect : BaseMatrixEffect() {
    override val definition: EffectModuleDefinition = EffectModuleDefinition(
        id = ID,
        name = "Contrast",
        category = EffectCategory.VISUAL,
        phase = 3,
        description = "Single-pass color-matrix contrast boost or matte wash.",
        targetTypes = ALL_LAYER_EFFECT_TARGETS,
        defaultDurationSec = 4.0,
        defaultLoop = true,
        params = listOf(
            EffectParamSpec("contrast", "Contrast", 1.3, 0.3, 2.2, 0.05, "×"),
        ),
        performance = LOW_COST_COLOR_MATRIX_META,
    )

    override fun evaluate(context: EffectEvaluationContext, out: EffectTransformState) {
        val target = context.param("contrast", 1.3)
        val blended = 1.0 + (target - 1.0) * context.intensity
        out.visualFx = EvaluatedVisualFx(contrast = blended.coerceIn(0.2, 2.5).toFloat())
    }

    companion object {
        const val ID = "fx.visual.contrast"
    }
}

/**
 * Phase 3 Visual Effect: Saturation (`fx.visual.saturation`).
 */
class SaturationEffect : BaseMatrixEffect() {
    override val definition: EffectModuleDefinition = EffectModuleDefinition(
        id = ID,
        name = "Saturation",
        category = EffectCategory.VISUAL,
        phase = 3,
        description = "Boost vibrance or desaturate to dramatic noir using GPU ColorMatrix.",
        targetTypes = ALL_LAYER_EFFECT_TARGETS,
        defaultDurationSec = 4.0,
        defaultLoop = true,
        params = listOf(
            EffectParamSpec("saturation", "Saturation", 1.4, 0.0, 2.2, 0.05, "×"),
        ),
        performance = LOW_COST_COLOR_MATRIX_META,
    )

    override fun evaluate(context: EffectEvaluationContext, out: EffectTransformState) {
        val target = context.param("saturation", 1.4)
        val blended = 1.0 + (target - 1.0) * context.intensity
        out.visualFx = EvaluatedVisualFx(saturation = blended.coerceIn(0.0, 2.5).toFloat())
    }

    companion object {
        const val ID = "fx.visual.saturation"
    }
}

/**
 * Phase 3 Visual Effect: Vignette (`fx.visual.vignette`).
 */
class VignetteEffect : BaseMatrixEffect() {
    override val definition: EffectModuleDefinition = EffectModuleDefinition(
        id = ID,
        name = "Vignette",
        category = EffectCategory.VISUAL,
        phase = 3,
        description = "Cinematic edge darkening using a single lightweight radial gradient.",
        targetTypes = ALL_LAYER_EFFECT_TARGETS,
        defaultDurationSec = 4.0,
        defaultLoop = true,
        params = listOf(
            EffectParamSpec("strength", "Darkness", 0.55, 0.0, 1.0, 0.05),
            EffectParamSpec("radius", "Clear Radius", 0.68, 0.3, 0.95, 0.05, isAdvanced = true),
        ),
        performance = EffectPerformanceMetadata(
            level = CostLevel.LOW,
            cpuCost = CostLevel.LOW,
            gpuCost = CostLevel.LOW,
            memoryCost = CostLevel.LOW,
            supportsLowEnd = true,
            renderingTechnique = "Single RadialGradient Vignette Overlay",
            textureRequirements = "None",
        ),
    )

    override fun evaluate(context: EffectEvaluationContext, out: EffectTransformState) {
        val str = (context.param("strength", 0.55) * context.intensity).coerceIn(0.0, 1.0)
        val rad = context.param("radius", 0.68).coerceIn(0.25, 0.95)
        out.visualFx = EvaluatedVisualFx(
            vignetteIntensity = str.toFloat(),
            vignetteRadius = rad.toFloat(),
        )
    }

    companion object {
        const val ID = "fx.visual.vignette"
    }
}

/**
 * Phase 3 Visual Effect: Pixelate (`fx.visual.pixelate`).
 */
class PixelateEffect : BaseMatrixEffect() {
    override val definition: EffectModuleDefinition = EffectModuleDefinition(
        id = ID,
        name = "Pixelate",
        category = EffectCategory.VISUAL,
        phase = 3,
        description = "Retro 8-bit mosaic look using lightweight coarse pixel grid rendering.",
        targetTypes = ALL_LAYER_EFFECT_TARGETS,
        defaultDurationSec = 3.0,
        defaultLoop = true,
        params = listOf(
            EffectParamSpec("blockSize", "Pixel Size", 10.0, 2.0, 32.0, 2.0, "px"),
        ),
        performance = EffectPerformanceMetadata(
            level = CostLevel.MEDIUM,
            cpuCost = CostLevel.LOW,
            gpuCost = CostLevel.MEDIUM,
            memoryCost = CostLevel.LOW,
            supportsLowEnd = true,
            fallbackEffect = OutlineEffect.ID,
            renderingTechnique = "Lightweight Coarse Grid Mosaic Overlay",
            textureRequirements = "None (no full-screen shader)",
        ),
    )

    override fun evaluate(context: EffectEvaluationContext, out: EffectTransformState) {
        val block = (context.param("blockSize", 10.0) * context.intensity).coerceIn(2.0, 40.0)
        out.visualFx = EvaluatedVisualFx(pixelateBlockSize = block.toFloat())
    }

    companion object {
        const val ID = "fx.visual.pixelate"
    }
}

/**
 * Phase 3 Visual Effect: Film Grain (`fx.visual.film_grain`).
 */
class FilmGrainEffect : BaseMatrixEffect() {
    override val definition: EffectModuleDefinition = EffectModuleDefinition(
        id = ID,
        name = "Film Grain",
        category = EffectCategory.VISUAL,
        phase = 3,
        description = "Cinematic seeded film speckles and dust with low-end density scaling.",
        targetTypes = ALL_LAYER_EFFECT_TARGETS,
        defaultDurationSec = 2.0,
        defaultLoop = true,
        params = listOf(
            EffectParamSpec("amount", "Grain Amount", 0.35, 0.05, 1.0, 0.05),
            EffectParamSpec("fpsStep", "Grain Rate", 12.0, 4.0, 24.0, 2.0, "fps", isAdvanced = true),
        ),
        performance = EffectPerformanceMetadata(
            level = CostLevel.LOW,
            cpuCost = CostLevel.LOW,
            gpuCost = CostLevel.LOW,
            memoryCost = CostLevel.LOW,
            supportsLowEnd = true,
            renderingTechnique = "Deterministic Seeded Point Batch (Capped on Low-End)",
            textureRequirements = "None",
            qualityReductionOptions = listOf("Halves grain dot budget in Performance preview"),
        ),
    )

    override fun evaluate(context: EffectEvaluationContext, out: EffectTransformState) {
        val amt = (context.param("amount", 0.35) * context.intensity).coerceIn(0.0, 1.0)
        val stepRate = max(1.0, context.param("fpsStep", 12.0))
        val seed = floor(context.frame * (stepRate / 24.0)).toInt()
        out.visualFx = EvaluatedVisualFx(
            filmGrainIntensity = amt.toFloat(),
            filmGrainSeed = seed,
        )
    }

    companion object {
        const val ID = "fx.visual.film_grain"
    }
}

val PHASE3_VISUAL_EFFECTS: List<Effect> = listOf(
    BlurEffect(),
    DropShadowEffect(),
    OutlineEffect(),
    GlowEffect(),
    BrightnessEffect(),
    ContrastEffect(),
    SaturationEffect(),
    VignetteEffect(),
    PixelateEffect(),
    FilmGrainEffect(),
)
