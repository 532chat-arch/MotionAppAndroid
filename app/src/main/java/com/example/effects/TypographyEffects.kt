package com.example.effects

import com.example.engine.PresetTargetType
import com.example.engine.applyEasing
import com.example.schema.InterpolationType
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin

private val TEXT_EFFECT_TARGETS = listOf(
    PresetTargetType.TEXT,
    PresetTargetType.LAYER,
    PresetTargetType.SHAPE,
)

private val LOW_COST_TEXT_METADATA = EffectPerformanceMetadata(
    level = CostLevel.LOW,
    cpuCost = CostLevel.LOW,
    gpuCost = CostLevel.LOW,
    memoryCost = CostLevel.LOW,
    supportsLowEnd = true,
    fallbackEffect = null,
    renderingTechnique = "Cached Font Glyph Positioning + 2D Matrix Transform",
    maxRecommendedParticles = 0,
    textureRequirements = "Reuses system Typeface glyph cache (no per-frame bitmap rebuild)",
    knownLimitations = listOf("Per-character effects work best on TextObject layers"),
    qualityReductionOptions = listOf("Zero reduction needed; negligible cost"),
)

/** 1. Typewriter Reveal (`fx.text.typewriter`) */
class TypewriterRevealEffect : BaseMatrixEffect() {
    override val definition = EffectModuleDefinition(
        id = ID,
        name = "Typewriter Reveal",
        category = EffectCategory.TYPOGRAPHY,
        phase = 5,
        description = "Reveals text character-by-character with an optional blinking typing cursor.",
        targetTypes = TEXT_EFFECT_TARGETS,
        defaultDurationSec = 2.0,
        defaultLoop = false,
        params = listOf(
            EffectParamSpec("showCursor", "Cursor", 1.0, 0.0, 1.0, 1.0, enumLabels = mapOf(0 to "Off", 1 to "On")),
            EffectParamSpec("startOffset", "Start Ratio", 0.0, 0.0, 0.5, 0.05, isAdvanced = true),
        ),
        performance = LOW_COST_TEXT_METADATA,
    )

    override fun evaluate(context: EffectEvaluationContext, out: EffectTransformState) {
        val start = context.param("startOffset", 0.0)
        val cursor = context.param("showCursor", 1.0) >= 0.5
        val rev = ((context.normalizedTime - start) / max(0.05, 1.0 - start)).coerceIn(0.0, 1.0)
        out.textFx = EvaluatedTextFx(typewriterReveal01 = rev.toFloat(), showCursor = cursor)
    }

    companion object {
        const val ID = "fx.text.typewriter"
    }
}

/** 2. Character Reveal (`fx.text.char_reveal`) */
class CharacterRevealEffect : BaseMatrixEffect() {
    override val definition = EffectModuleDefinition(
        id = ID,
        name = "Character Reveal",
        category = EffectCategory.TYPOGRAPHY,
        phase = 5,
        description = "Smooth staggered per-character reveal with crisp kinetic rise.",
        targetTypes = TEXT_EFFECT_TARGETS,
        defaultDurationSec = 1.6,
        defaultLoop = false,
        params = listOf(
            EffectParamSpec("risePx", "Rise Height", 16.0, 0.0, 48.0, 2.0, "px"),
        ),
        performance = LOW_COST_TEXT_METADATA,
    )

    override fun evaluate(context: EffectEvaluationContext, out: EffectTransformState) {
        val eased = applyEasing(context.normalizedTime, InterpolationType.EASE_OUT)
        val rise = context.param("risePx", 16.0) * context.intensity
        out.textFx = EvaluatedTextFx(
            charReveal01 = eased.toFloat(),
            charBounceAmplitude = ((1.0 - eased) * rise).toFloat(),
            charBouncePhase = (context.normalizedTime * 2.0 * PI).toFloat(),
        )
    }

    companion object {
        const val ID = "fx.text.char_reveal"
    }
}

/** 3. Word Reveal (`fx.text.word_reveal`) */
class WordRevealEffect : BaseMatrixEffect() {
    override val definition = EffectModuleDefinition(
        id = ID,
        name = "Word Reveal",
        category = EffectCategory.TYPOGRAPHY,
        phase = 5,
        description = "Reveals lyric lines and headlines word-by-word with gentle pop-in.",
        targetTypes = TEXT_EFFECT_TARGETS,
        defaultDurationSec = 1.8,
        defaultLoop = false,
        params = listOf(
            EffectParamSpec("popScale", "Word Pop", 0.12, 0.0, 0.4, 0.02),
        ),
        performance = LOW_COST_TEXT_METADATA,
    )

    override fun evaluate(context: EffectEvaluationContext, out: EffectTransformState) {
        val eased = applyEasing(context.normalizedTime, InterpolationType.EASE_OUT)
        val pop = context.param("popScale", 0.12) * context.intensity
        out.textFx = EvaluatedTextFx(
            wordReveal01 = eased.toFloat(),
            charScaleBoost = ((1.0 - eased) * pop).toFloat(),
        )
    }

    companion object {
        const val ID = "fx.text.word_reveal"
    }
}

/** 4. Text Fade (`fx.text.fade`) */
class TextFadeEffect : BaseMatrixEffect() {
    override val definition = EffectModuleDefinition(
        id = ID,
        name = "Text Fade",
        category = EffectCategory.TYPOGRAPHY,
        phase = 5,
        description = "Cinematic fade-in and optional fade-out for captions and lyric lines.",
        targetTypes = TEXT_EFFECT_TARGETS,
        defaultDurationSec = 1.4,
        defaultLoop = false,
        params = listOf(
            EffectParamSpec("fadeOutTail", "Fade Out Tail", 0.2, 0.0, 0.45, 0.05),
        ),
        performance = LOW_COST_TEXT_METADATA,
    )

    override fun evaluate(context: EffectEvaluationContext, out: EffectTransformState) {
        val t = context.normalizedTime
        val tail = context.param("fadeOutTail", 0.2).coerceIn(0.0, 0.45)
        val fadeIn = applyEasing((t / 0.25).coerceIn(0.0, 1.0), InterpolationType.EASE_OUT)
        val fadeOut = if (tail > 0.01 && t > (1.0 - tail)) {
            applyEasing(((1.0 - t) / tail).coerceIn(0.0, 1.0), InterpolationType.EASE_OUT)
        } else 1.0
        out.overrideOpacity = (minOf(fadeIn, fadeOut) * context.intensity).coerceIn(0.0, 1.0)
    }

    companion object {
        const val ID = "fx.text.fade"
    }
}

/** 5. Text Slide (`fx.text.slide`) */
class TextSlideEffect : BaseMatrixEffect() {
    override val definition = EffectModuleDefinition(
        id = ID,
        name = "Text Slide",
        category = EffectCategory.TYPOGRAPHY,
        phase = 5,
        description = "Crisp directional slide-in paired with smooth opacity reveal.",
        targetTypes = TEXT_EFFECT_TARGETS,
        defaultDurationSec = 1.0,
        defaultLoop = false,
        params = listOf(
            EffectParamSpec("direction", "Direction", 0.0, 0.0, 3.0, 1.0, enumLabels = mapOf(0 to "Up", 1 to "Down", 2 to "Left", 3 to "Right")),
            EffectParamSpec("distance", "Slide Distance", 60.0, 10.0, 240.0, 5.0, "px"),
        ),
        performance = LOW_COST_TEXT_METADATA,
    )

    override fun evaluate(context: EffectEvaluationContext, out: EffectTransformState) {
        val dir = context.param("direction", 0.0).toInt()
        val dist = context.param("distance", 60.0) * context.intensity
        val rem = 1.0 - applyEasing(context.normalizedTime, InterpolationType.EASE_OUT)
        when (dir) {
            1 -> out.dy = -dist * rem
            2 -> out.dx = dist * rem
            3 -> out.dx = -dist * rem
            else -> out.dy = dist * rem
        }
        out.overrideOpacity = (1.0 - rem * 0.85).coerceIn(0.0, 1.0)
    }

    companion object {
        const val ID = "fx.text.slide"
    }
}

/** 6. Text Bounce (`fx.text.bounce`) */
class TextBounceEffect : BaseMatrixEffect() {
    override val definition = EffectModuleDefinition(
        id = ID,
        name = "Text Bounce",
        category = EffectCategory.TYPOGRAPHY,
        phase = 5,
        description = "Playful elastic per-character & headline bounce for lyric videos and promos.",
        targetTypes = TEXT_EFFECT_TARGETS,
        defaultDurationSec = 1.4,
        defaultLoop = true,
        params = listOf(
            EffectParamSpec("height", "Bounce Height", 22.0, 4.0, 80.0, 2.0, "px"),
            EffectParamSpec("bounces", "Bounces", 2.0, 1.0, 5.0, 1.0),
        ),
        performance = LOW_COST_TEXT_METADATA,
    )

    override fun evaluate(context: EffectEvaluationContext, out: EffectTransformState) {
        val t = context.normalizedTime
        val height = context.param("height", 22.0) * context.intensity
        val bounces = max(1.0, context.param("bounces", 2.0))
        val decay = exp(-t * 1.4)
        val hop = abs(sin(t * bounces * PI)) * height * decay
        out.dy = -hop * 0.45
        out.textFx = EvaluatedTextFx(
            charBounceAmplitude = (height * 0.55).toFloat(),
            charBouncePhase = (t * bounces * 2.0 * PI).toFloat(),
        )
    }

    companion object {
        const val ID = "fx.text.bounce"
    }
}

/** 7. Text Wave (`fx.text.wave`) */
class TextWaveEffect : BaseMatrixEffect() {
    override val definition = EffectModuleDefinition(
        id = ID,
        name = "Text Wave",
        category = EffectCategory.TYPOGRAPHY,
        phase = 5,
        description = "Smooth sinusoidal wave rippling across every character in real time.",
        targetTypes = TEXT_EFFECT_TARGETS,
        defaultDurationSec = 2.0,
        defaultLoop = true,
        params = listOf(
            EffectParamSpec("amplitude", "Wave Height", 14.0, 2.0, 48.0, 1.0, "px"),
            EffectParamSpec("frequency", "Wave Density", 0.65, 0.2, 2.0, 0.05),
        ),
        performance = LOW_COST_TEXT_METADATA,
    )

    override fun evaluate(context: EffectEvaluationContext, out: EffectTransformState) {
        val amp = context.param("amplitude", 14.0) * context.intensity
        val freq = context.param("frequency", 0.65)
        out.textFx = EvaluatedTextFx(
            waveAmplitude = amp.toFloat(),
            waveFrequency = freq.toFloat(),
            wavePhase = (context.normalizedTime * 2.0 * PI).toFloat(),
        )
    }

    companion object {
        const val ID = "fx.text.wave"
    }
}

/** 8. Tracking Animation (`fx.text.tracking`) */
class TrackingAnimationEffect : BaseMatrixEffect() {
    override val definition = EffectModuleDefinition(
        id = ID,
        name = "Tracking Animation",
        category = EffectCategory.TYPOGRAPHY,
        phase = 5,
        description = "Cinematic letter-spacing expansion or contraction for movie titles.",
        targetTypes = TEXT_EFFECT_TARGETS,
        defaultDurationSec = 2.2,
        defaultLoop = false,
        params = listOf(
            EffectParamSpec("fromTracking", "Start Spacing", 18.0, -4.0, 40.0, 1.0, "px"),
            EffectParamSpec("toTracking", "End Spacing", 2.0, -4.0, 40.0, 1.0, "px"),
        ),
        performance = LOW_COST_TEXT_METADATA,
    )

    override fun evaluate(context: EffectEvaluationContext, out: EffectTransformState) {
        val fromT = context.param("fromTracking", 18.0)
        val toT = context.param("toTracking", 2.0)
        val eased = applyEasing(context.normalizedTime, InterpolationType.EASE_OUT)
        val cur = (fromT + (toT - fromT) * eased) * context.intensity
        out.textFx = EvaluatedTextFx(trackingPx = cur.toFloat())
    }

    companion object {
        const val ID = "fx.text.tracking"
    }
}

/** 9. Text Scale (`fx.text.scale`) */
class TextScaleEffect : BaseMatrixEffect() {
    override val definition = EffectModuleDefinition(
        id = ID,
        name = "Text Scale",
        category = EffectCategory.TYPOGRAPHY,
        phase = 5,
        description = "Bold kinetic zoom-in or punch scale transition for impact titles.",
        targetTypes = TEXT_EFFECT_TARGETS,
        defaultDurationSec = 1.0,
        defaultLoop = false,
        params = listOf(
            EffectParamSpec("startScale", "Start Scale", 0.5, 0.1, 2.2, 0.05, "×"),
        ),
        performance = LOW_COST_TEXT_METADATA,
    )

    override fun evaluate(context: EffectEvaluationContext, out: EffectTransformState) {
        val start = context.param("startScale", 0.5)
        val eased = applyEasing(context.normalizedTime, InterpolationType.EASE_OUT)
        val delta = (start - 1.0) * (1.0 - eased) * context.intensity
        out.dScaleX = delta
        out.dScaleY = delta
    }

    companion object {
        const val ID = "fx.text.scale"
    }
}

/** 10. Text Rotation (`fx.text.rotation`) */
class TextRotationEffect : BaseMatrixEffect() {
    override val definition = EffectModuleDefinition(
        id = ID,
        name = "Text Rotation",
        category = EffectCategory.TYPOGRAPHY,
        phase = 5,
        description = "Playful tilted character and line rotation swing for kinetic typography.",
        targetTypes = TEXT_EFFECT_TARGETS,
        defaultDurationSec = 1.8,
        defaultLoop = true,
        params = listOf(
            EffectParamSpec("angleDeg", "Tilt Angle", 12.0, -45.0, 45.0, 1.0, "°"),
            EffectParamSpec("perChar", "Apply Mode", 1.0, 0.0, 1.0, 1.0, isAdvanced = true, enumLabels = mapOf(0 to "Line", 1 to "Per-Char")),
        ),
        performance = LOW_COST_TEXT_METADATA,
    )

    override fun evaluate(context: EffectEvaluationContext, out: EffectTransformState) {
        val angle = context.param("angleDeg", 12.0) * context.intensity
        val perChar = context.param("perChar", 1.0) >= 0.5
        val s = sin(context.normalizedTime * 2.0 * PI)
        if (perChar) {
            out.textFx = EvaluatedTextFx(charRotationDeg = (angle * s).toFloat())
        } else {
            out.dRotDeg = angle * s
        }
    }

    companion object {
        const val ID = "fx.text.rotation"
    }
}

val PHASE5_TYPOGRAPHY_EFFECTS: List<Effect> = listOf(
    TypewriterRevealEffect(),
    CharacterRevealEffect(),
    WordRevealEffect(),
    TextFadeEffect(),
    TextSlideEffect(),
    TextBounceEffect(),
    TextWaveEffect(),
    TrackingAnimationEffect(),
    TextScaleEffect(),
    TextRotationEffect(),
)
