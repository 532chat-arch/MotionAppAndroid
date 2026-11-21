package com.example.effects

import kotlin.math.max

private fun particlePerformanceMeta(
    maxRec: Int,
    technique: String,
    level: CostLevel = CostLevel.MEDIUM,
) = EffectPerformanceMetadata(
    level = level,
    cpuCost = CostLevel.LOW,
    gpuCost = level,
    memoryCost = CostLevel.LOW,
    supportsLowEnd = true,
    fallbackEffect = null,
    renderingTechnique = technique,
    maxRecommendedParticles = maxRec,
    textureRequirements = "Shared pooled 2D sprite primitives (zero per-frame allocation)",
    knownLimitations = listOf("Particle count automatically scales with Device Performance Profile"),
    qualityReductionOptions = listOf("Capped at 45 particles on Low-End, 100 on Mid-Range, 180 on High-End"),
)

private fun buildParticleFx(
    kind: ParticleEffectKind,
    context: EffectEvaluationContext,
    defaultCount: Double,
    defaultSpeed: Double,
    defaultSize: Double,
    defaultWind: Double,
    defaultColor: Int,
): EvaluatedParticleLayerFx {
    val profileCap = context.deviceProfile.maxParticleCount
    val count = (context.param("count", defaultCount) * context.intensity).toInt().coerceIn(4, profileCap)
    val speed = (context.param("speed", defaultSpeed) * max(0.2, context.speed)).toFloat()
    val size = context.param("size", defaultSize).toFloat().coerceIn(0.4f, 3.0f)
    val wind = context.param("wind", defaultWind).toFloat()
    val spread = context.param("spread", 1.8).toFloat().coerceIn(0.6f, 4.0f)
    val seed = context.param("seed", 7.0).toInt()
    val opacity = context.param("opacity", 0.85).toFloat().coerceIn(0.1f, 1.0f)
    return EvaluatedParticleLayerFx(
        kind = kind,
        count = count,
        speed = speed,
        size = size,
        wind = wind,
        spreadWidth = (max(240.0, context.objWidth) * spread).toFloat(),
        spreadHeight = (max(240.0, context.objHeight) * spread).toFloat(),
        seed = seed,
        colorInt = defaultColor,
        opacity = opacity,
        timeSec = (context.frame / 24.0).toFloat(),
    )
}

/**
 * Phase 4 Particle Effect: Rain (`fx.particles.rain`).
 */
class RainEffect : BaseMatrixEffect() {
    override val definition: EffectModuleDefinition = EffectModuleDefinition(
        id = ID,
        name = "Rain",
        category = EffectCategory.PARTICLES,
        phase = 4,
        description = "Pooled diagonal rain streaks with wind slant and deterministic repeatability.",
        targetTypes = ALL_LAYER_EFFECT_TARGETS,
        defaultDurationSec = 4.0,
        defaultLoop = true,
        params = listOf(
            EffectParamSpec("count", "Raindrops", 48.0, 10.0, 140.0, 4.0),
            EffectParamSpec("speed", "Fall Speed", 1.4, 0.4, 3.0, 0.1, "×"),
            EffectParamSpec("wind", "Wind Slant", 0.35, -1.5, 1.5, 0.05),
            EffectParamSpec("size", "Streak Length", 1.0, 0.5, 2.5, 0.1, isAdvanced = true),
            EffectParamSpec("spread", "Area Spread", 2.2, 0.8, 4.0, 0.2, isAdvanced = true),
            EffectParamSpec("seed", "Seed", 11.0, 1.0, 99.0, 1.0, isAdvanced = true),
        ),
        performance = particlePerformanceMeta(120, "Pooled 2D Line Streak Batch"),
    )

    override fun evaluate(context: EffectEvaluationContext, out: EffectTransformState) {
        out.particleFx = buildParticleFx(
            kind = ParticleEffectKind.RAIN,
            context = context,
            defaultCount = 48.0,
            defaultSpeed = 1.4,
            defaultSize = 1.0,
            defaultWind = 0.35,
            defaultColor = 0xFFB8E1FF.toInt(),
        )
    }

    companion object {
        const val ID = "fx.particles.rain"
    }
}

/**
 * Phase 4 Particle Effect: Snow (`fx.particles.snow`).
 */
class SnowEffect : BaseMatrixEffect() {
    override val definition: EffectModuleDefinition = EffectModuleDefinition(
        id = ID,
        name = "Snow",
        category = EffectCategory.PARTICLES,
        phase = 4,
        description = "Pooled soft snowflakes drifting gently with sinusoidal flutter.",
        targetTypes = ALL_LAYER_EFFECT_TARGETS,
        defaultDurationSec = 5.0,
        defaultLoop = true,
        params = listOf(
            EffectParamSpec("count", "Snowflakes", 42.0, 8.0, 120.0, 4.0),
            EffectParamSpec("speed", "Fall Speed", 1.0, 0.3, 2.5, 0.1, "×"),
            EffectParamSpec("wind", "Drift Wind", 0.25, -1.5, 1.5, 0.05),
            EffectParamSpec("size", "Flake Size", 1.0, 0.5, 2.4, 0.1, isAdvanced = true),
            EffectParamSpec("spread", "Area Spread", 2.2, 0.8, 4.0, 0.2, isAdvanced = true),
            EffectParamSpec("seed", "Seed", 19.0, 1.0, 99.0, 1.0, isAdvanced = true),
        ),
        performance = particlePerformanceMeta(110, "Pooled 2D Soft Circle Sprite Batch"),
    )

    override fun evaluate(context: EffectEvaluationContext, out: EffectTransformState) {
        out.particleFx = buildParticleFx(
            kind = ParticleEffectKind.SNOW,
            context = context,
            defaultCount = 42.0,
            defaultSpeed = 1.0,
            defaultSize = 1.0,
            defaultWind = 0.25,
            defaultColor = 0xFFFFFFFF.toInt(),
        )
    }

    companion object {
        const val ID = "fx.particles.snow"
    }
}

/**
 * Phase 4 Particle Effect: Dust (`fx.particles.dust`).
 */
class DustEffect : BaseMatrixEffect() {
    override val definition: EffectModuleDefinition = EffectModuleDefinition(
        id = ID,
        name = "Dust Motes",
        category = EffectCategory.PARTICLES,
        phase = 4,
        description = "Floating atmospheric dust motes with gentle drift and subtle twinkle.",
        targetTypes = ALL_LAYER_EFFECT_TARGETS,
        defaultDurationSec = 6.0,
        defaultLoop = true,
        params = listOf(
            EffectParamSpec("count", "Motes", 32.0, 6.0, 90.0, 2.0),
            EffectParamSpec("speed", "Drift Speed", 0.8, 0.2, 2.0, 0.1, "×"),
            EffectParamSpec("size", "Mote Size", 1.0, 0.5, 2.2, 0.1),
            EffectParamSpec("wind", "Breeze", 0.15, -1.0, 1.0, 0.05, isAdvanced = true),
            EffectParamSpec("spread", "Area Spread", 2.0, 0.8, 4.0, 0.2, isAdvanced = true),
            EffectParamSpec("seed", "Seed", 29.0, 1.0, 99.0, 1.0, isAdvanced = true),
        ),
        performance = particlePerformanceMeta(80, "Pooled 2D Twinkle Dot Batch", CostLevel.LOW),
    )

    override fun evaluate(context: EffectEvaluationContext, out: EffectTransformState) {
        out.particleFx = buildParticleFx(
            kind = ParticleEffectKind.DUST,
            context = context,
            defaultCount = 32.0,
            defaultSpeed = 0.8,
            defaultSize = 1.0,
            defaultWind = 0.15,
            defaultColor = 0xFFFDE68A.toInt(),
        )
    }

    companion object {
        const val ID = "fx.particles.dust"
    }
}

/**
 * Phase 4 Particle Effect: Fireflies (`fx.particles.fireflies`).
 */
class FirefliesEffect : BaseMatrixEffect() {
    override val definition: EffectModuleDefinition = EffectModuleDefinition(
        id = ID,
        name = "Fireflies",
        category = EffectCategory.PARTICLES,
        phase = 4,
        description = "Warm glowing fireflies using a lightweight 2-circle glow approximation.",
        targetTypes = ALL_LAYER_EFFECT_TARGETS,
        defaultDurationSec = 5.0,
        defaultLoop = true,
        params = listOf(
            EffectParamSpec("count", "Fireflies", 22.0, 4.0, 64.0, 2.0),
            EffectParamSpec("speed", "Flight Speed", 1.0, 0.3, 2.4, 0.1, "×"),
            EffectParamSpec("size", "Orb Size", 1.1, 0.5, 2.2, 0.1),
            EffectParamSpec("spread", "Area Spread", 2.0, 0.8, 4.0, 0.2, isAdvanced = true),
            EffectParamSpec("seed", "Seed", 37.0, 1.0, 99.0, 1.0, isAdvanced = true),
        ),
        performance = particlePerformanceMeta(60, "Pooled 2-Circle Halo Sprite Batch (No RadialGradient)", CostLevel.LOW),
    )

    override fun evaluate(context: EffectEvaluationContext, out: EffectTransformState) {
        out.particleFx = buildParticleFx(
            kind = ParticleEffectKind.FIREFLIES,
            context = context,
            defaultCount = 22.0,
            defaultSpeed = 1.0,
            defaultSize = 1.1,
            defaultWind = 0.0,
            defaultColor = 0xFFFBBF24.toInt(),
        )
    }

    companion object {
        const val ID = "fx.particles.fireflies"
    }
}

/**
 * Phase 4 Particle Effect: Sprite-based Smoke (`fx.particles.smoke`).
 */
class SmokeEffect : BaseMatrixEffect() {
    override val definition: EffectModuleDefinition = EffectModuleDefinition(
        id = ID,
        name = "Sprite Smoke",
        category = EffectCategory.PARTICLES,
        phase = 4,
        description = "Lightweight rising & expanding 2D smoke puffs — zero fluid simulation.",
        targetTypes = ALL_LAYER_EFFECT_TARGETS,
        defaultDurationSec = 4.0,
        defaultLoop = true,
        params = listOf(
            EffectParamSpec("count", "Puffs", 24.0, 6.0, 64.0, 2.0),
            EffectParamSpec("speed", "Rise Speed", 0.9, 0.3, 2.5, 0.1, "×"),
            EffectParamSpec("size", "Puff Size", 1.1, 0.5, 2.4, 0.1),
            EffectParamSpec("wind", "Wind Drift", 0.2, -1.5, 1.5, 0.05, isAdvanced = true),
            EffectParamSpec("opacity", "Density", 0.65, 0.15, 1.0, 0.05, isAdvanced = true),
            EffectParamSpec("seed", "Seed", 43.0, 1.0, 99.0, 1.0, isAdvanced = true),
        ),
        performance = particlePerformanceMeta(55, "Pooled Soft Layered Sprite Puffs (Zero Fluid Sim)"),
    )

    override fun evaluate(context: EffectEvaluationContext, out: EffectTransformState) {
        out.particleFx = buildParticleFx(
            kind = ParticleEffectKind.SMOKE,
            context = context,
            defaultCount = 24.0,
            defaultSpeed = 0.9,
            defaultSize = 1.1,
            defaultWind = 0.2,
            defaultColor = 0xFFD1D5DB.toInt(),
        )
    }

    companion object {
        const val ID = "fx.particles.smoke"
    }
}

/**
 * Phase 4 Particle Effect: Falling Leaves (`fx.particles.falling_leaves`).
 */
class FallingLeavesEffect : BaseMatrixEffect() {
    override val definition: EffectModuleDefinition = EffectModuleDefinition(
        id = ID,
        name = "Falling Leaves",
        category = EffectCategory.PARTICLES,
        phase = 4,
        description = "Pooled tumbling autumn leaves with pendulum sway and breeze drift.",
        targetTypes = ALL_LAYER_EFFECT_TARGETS,
        defaultDurationSec = 5.0,
        defaultLoop = true,
        params = listOf(
            EffectParamSpec("count", "Leaves", 26.0, 6.0, 80.0, 2.0),
            EffectParamSpec("speed", "Fall Speed", 1.0, 0.3, 2.5, 0.1, "×"),
            EffectParamSpec("wind", "Breeze", 0.45, -1.5, 1.5, 0.05),
            EffectParamSpec("size", "Leaf Size", 1.0, 0.5, 2.2, 0.1, isAdvanced = true),
            EffectParamSpec("spread", "Area Spread", 2.2, 0.8, 4.0, 0.2, isAdvanced = true),
            EffectParamSpec("seed", "Seed", 53.0, 1.0, 99.0, 1.0, isAdvanced = true),
        ),
        performance = particlePerformanceMeta(75, "Pooled Rotated Oval Leaf Primitives", CostLevel.LOW),
    )

    override fun evaluate(context: EffectEvaluationContext, out: EffectTransformState) {
        out.particleFx = buildParticleFx(
            kind = ParticleEffectKind.FALLING_LEAVES,
            context = context,
            defaultCount = 26.0,
            defaultSpeed = 1.0,
            defaultSize = 1.0,
            defaultWind = 0.45,
            defaultColor = 0xFFEA580C.toInt(),
        )
    }

    companion object {
        const val ID = "fx.particles.falling_leaves"
    }
}

val PHASE4_PARTICLE_EFFECTS: List<Effect> = listOf(
    RainEffect(),
    SnowEffect(),
    DustEffect(),
    FirefliesEffect(),
    SmokeEffect(),
    FallingLeavesEffect(),
)
