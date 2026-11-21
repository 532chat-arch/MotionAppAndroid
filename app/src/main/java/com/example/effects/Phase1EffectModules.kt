package com.example.effects

import com.example.schema.MotionModifierInstance
import com.example.schema.PivotAnchorPreset

/**
 * The 10 core Phase 1 modular [Effect] components:
 * Position, Scale, Rotation, Opacity, Follow Path, Bounce, Shake, Float, Wiggle, and Spring.
 */
val PHASE1_CORE_EFFECTS: List<Effect> = listOf(
    PositionEffect(),
    ScaleEffect(),
    RotationEffect(),
    OpacityEffect(),
    FollowPathEffect(),
    BounceEffect(),
    ShakeEffect(),
    FloatEffect(),
    WiggleEffect(),
    SpringEffect(),
)

/**
 * Full Phase 1 modular [Effect] components (16 Transform + Motion modules).
 */
val PHASE1_EFFECTS: List<Effect> = PHASE1_TRANSFORM_EFFECTS + PHASE1_MOTION_EFFECTS

/**
 * Complete modular [Effect] catalog across all 5 Phases (61 lightweight reusable effects):
 * - Phase 1: Transform (8) + Motion (8) = 16
 * - Phase 2: Character Animation = 8
 * - Phase 3: Visual Effects = 10
 * - Phase 4: Pooled Environment & Particles = 6
 * - Phase 5: Typography (10) + Transitions (6) + Audio-Reactive (5) = 21
 */
val ALL_BUILTIN_EFFECTS: List<Effect> =
    PHASE1_EFFECTS +
        PHASE2_CHARACTER_EFFECTS +
        PHASE3_VISUAL_EFFECTS +
        PHASE4_PARTICLE_EFFECTS +
        PHASE5_TYPOGRAPHY_EFFECTS +
        PHASE5_TRANSITION_EFFECTS +
        PHASE5_AUDIO_EFFECTS

/**
 * Metadata definitions derived directly from [ALL_BUILTIN_EFFECTS] (single source of truth).
 */
val PHASE1_EFFECT_MODULES: List<EffectModuleDefinition> = ALL_BUILTIN_EFFECTS.map { it.definition }

/**
 * Compatibility helper that delegates to [EffectRegistry.default].
 */
fun evaluatePhase1Effect(
    kernelId: String,
    mod: MotionModifierInstance,
    t: Double,
    frame: Double,
    objWidth: Double,
    objHeight: Double,
    out: EffectTransformState,
    context: EffectEvaluationContext = EffectEvaluationContext(),
): Boolean {
    context.set(
        modifier = mod,
        normalizedTime = t,
        frame = frame,
        objWidth = objWidth,
        objHeight = objHeight,
    )
    return EffectRegistry.default.evaluate(kernelId, context, out)
}

/**
 * Curated multi-effect starter combinations across all phases, including all 5 canonical
 * combinations from the Motion Studio Pro specification.
 */
val PHASE1_STARTER_COMBOS: List<EffectComboPreset> = listOf(
    EffectComboPreset(
        id = "combo.character-entrance",
        name = "Character Entrance",
        description = "Position + Scale + Bounce + Drop Shadow for punchy character reveals.",
        items = listOf(
            EffectComboItem(
                effectId = PositionEffect.ID,
                label = "Position Slide",
                durationSec = 0.8,
                loop = false,
                params = mapOf("offsetX" to 0.0, "offsetY" to 90.0, "animateFromOffset" to 1.0, "easingMode" to 2.0),
            ),
            EffectComboItem(
                effectId = ScaleEffect.ID,
                label = "Scale Pop",
                durationSec = 0.7,
                loop = false,
                params = mapOf("scaleX" to 0.45, "uniform" to 1.0, "fromDelta" to 1.0, "easingMode" to 2.0),
            ),
            EffectComboItem(
                effectId = BounceEffect.ID,
                label = "Bounce",
                durationSec = 1.1,
                delayFrames = 6,
                loop = false,
                pivotAnchor = PivotAnchorPreset.BOTTOM,
                params = mapOf("height" to 38.0, "bounces" to 2.0, "squash" to 0.14, "damping" to 0.55),
            ),
            EffectComboItem(
                effectId = DropShadowEffect.ID,
                label = "Drop Shadow",
                durationSec = 4.0,
                loop = true,
                params = mapOf("offsetX" to 8.0, "offsetY" to 12.0, "alpha" to 0.55, "softness" to 6.0, "colorPreset" to 1.0),
            ),
        ),
    ),
    EffectComboPreset(
        id = "combo.wind-blown-hair",
        name = "Wind-Blown Hair",
        description = "Hair Sway + Spring + Secondary Motion for lively anime hair flow.",
        items = listOf(
            EffectComboItem(
                effectId = HairSwayEffect.ID,
                label = "Hair Sway",
                durationSec = 2.8,
                loop = true,
                pivotAnchor = PivotAnchorPreset.TOP,
                params = mapOf("amplitude" to 16.0, "frequency" to 1.8, "windBias" to 9.0, "strandCount" to 10.0, "turbulence" to 0.4),
            ),
            EffectComboItem(
                effectId = SpringEffect.ID,
                label = "Spring",
                durationSec = 1.5,
                loop = true,
                pivotAnchor = PivotAnchorPreset.TOP,
                params = mapOf("displacementY" to -12.0, "stiffness" to 12.0, "damping" to 3.8, "scaleOvershoot" to 0.04),
            ),
            EffectComboItem(
                effectId = SecondaryMotionEffect.ID,
                label = "Secondary Motion",
                durationSec = 2.4,
                loop = true,
                pivotAnchor = PivotAnchorPreset.TOP,
                params = mapOf("lagPhase" to 0.24, "swayDeg" to 8.0, "driftPx" to 8.0, "springDamping" to 0.15),
            ),
        ),
    ),
    EffectComboPreset(
        id = "combo.cinematic-lyrics",
        name = "Cinematic Lyrics",
        description = "Word Reveal + Text Bounce + Glow + Beat Pulse for music lyric videos.",
        items = listOf(
            EffectComboItem(
                effectId = WordRevealEffect.ID,
                label = "Word Reveal",
                durationSec = 1.6,
                loop = false,
                params = mapOf("popScale" to 0.14),
            ),
            EffectComboItem(
                effectId = TextBounceEffect.ID,
                label = "Text Bounce",
                durationSec = 1.4,
                loop = true,
                params = mapOf("height" to 16.0, "bounces" to 2.0),
            ),
            EffectComboItem(
                effectId = GlowEffect.ID,
                label = "Glow",
                durationSec = 2.4,
                loop = true,
                params = mapOf("radius" to 16.0, "strength" to 0.8, "colorPreset" to 2.0, "pulse" to 1.0),
            ),
            EffectComboItem(
                effectId = BeatPulseEffect.ID,
                label = "Beat Pulse",
                durationSec = 4.0,
                loop = true,
                params = mapOf("bpm" to 120.0, "scalePunch" to 0.12, "sharpness" to 3.2),
            ),
        ),
    ),
    EffectComboPreset(
        id = "combo.horror-scene",
        name = "Horror Scene",
        description = "Shake + Opacity Flicker + Vignette + Film Grain for dramatic tension.",
        items = listOf(
            EffectComboItem(
                effectId = ShakeEffect.ID,
                label = "Shake",
                durationSec = 0.9,
                loop = true,
                params = mapOf("amplitudeX" to 10.0, "amplitudeY" to 7.0, "frequency" to 9.0, "rotationDeg" to 1.8, "decay" to 0.15),
            ),
            EffectComboItem(
                effectId = OpacityEffect.ID,
                label = "Opacity Fade",
                durationSec = 1.0,
                loop = true,
                params = mapOf("fromOpacity" to 0.35, "toOpacity" to 1.0, "fadeMode" to 2.0),
            ),
            EffectComboItem(
                effectId = VignetteEffect.ID,
                label = "Vignette",
                durationSec = 4.0,
                loop = true,
                params = mapOf("strength" to 0.68, "radius" to 0.58),
            ),
            EffectComboItem(
                effectId = FilmGrainEffect.ID,
                label = "Film Grain",
                durationSec = 2.0,
                loop = true,
                params = mapOf("amount" to 0.45, "fpsStep" to 12.0),
            ),
        ),
    ),
    EffectComboPreset(
        id = "combo.floating-environment",
        name = "Floating Environment",
        description = "Float + Dust Motes + Fireflies for atmospheric storytelling.",
        items = listOf(
            EffectComboItem(
                effectId = FloatEffect.ID,
                label = "Float",
                durationSec = 3.4,
                loop = true,
                params = mapOf("amplitudeY" to 14.0, "tiltDeg" to 1.8, "driftX" to 8.0),
            ),
            EffectComboItem(
                effectId = DustEffect.ID,
                label = "Dust Motes",
                durationSec = 6.0,
                loop = true,
                params = mapOf("count" to 28.0, "speed" to 0.8, "size" to 1.0, "spread" to 2.2),
            ),
            EffectComboItem(
                effectId = FirefliesEffect.ID,
                label = "Fireflies",
                durationSec = 5.0,
                loop = true,
                params = mapOf("count" to 18.0, "speed" to 1.0, "size" to 1.1, "spread" to 2.0),
            ),
        ),
    ),
    EffectComboPreset(
        id = "combo.spring-title-drop",
        name = "Elastic Spring Drop",
        description = "Spring + Opacity Fade for crisp motion-graphic title entrances (2 pts · Low-End OK).",
        items = listOf(
            EffectComboItem(
                effectId = SpringEffect.ID,
                label = "Spring",
                durationSec = 1.4,
                loop = false,
                params = mapOf("displacementY" to -110.0, "stiffness" to 15.0, "damping" to 4.5, "scaleOvershoot" to 0.18),
            ),
            EffectComboItem(
                effectId = OpacityEffect.ID,
                label = "Opacity Fade",
                durationSec = 0.45,
                loop = false,
                params = mapOf("fromOpacity" to 0.0, "toOpacity" to 1.0, "fadeMode" to 0.0),
            ),
        ),
    ),
    EffectComboPreset(
        id = "combo.orbit-wiggle",
        name = "Orbital Firefly Patrol",
        description = "Follow Path (Figure-8) + Wiggle for organic hovering movement (2 pts · Low-End OK).",
        items = listOf(
            EffectComboItem(
                effectId = FollowPathEffect.ID,
                label = "Follow Path",
                durationSec = 4.5,
                loop = true,
                params = mapOf("pathType" to 2.0, "radiusX" to 130.0, "radiusY" to 65.0, "autoOrient" to 0.0),
            ),
            EffectComboItem(
                effectId = WiggleEffect.ID,
                label = "Wiggle",
                durationSec = 2.2,
                loop = true,
                params = mapOf("posAmp" to 10.0, "rotAmp" to 4.0, "frequency" to 2.4),
            ),
        ),
    ),
)
