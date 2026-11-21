package com.example.effects

import com.example.engine.PresetTargetType
import com.example.schema.MotionModifierBlendMode
import com.example.schema.MotionModifierDirection
import com.example.schema.MotionModifierInstance
import com.example.schema.PivotAnchorPreset
import com.example.schema.StudioJson
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlin.math.max
import kotlin.math.roundToInt

@Serializable
enum class EffectCategory(val label: String) {
    @SerialName("transform") TRANSFORM("Transform"),
    @SerialName("motion") MOTION("Motion"),
    @SerialName("character") CHARACTER("Character"),
    @SerialName("visual") VISUAL("Visual"),
    @SerialName("particles") PARTICLES("Particles"),
    @SerialName("typography") TYPOGRAPHY("Typography"),
    @SerialName("transition") TRANSITION("Transition"),
    @SerialName("audio") AUDIO("Audio"),
}

/**
 * Standardized performance metadata attached to every effect module.
 * Used for device-profile validation, AI effect-combination checks, and
 * portable Markdown/JSON knowledge export.
 */
@Serializable
data class EffectPerformanceMetadata(
    val level: CostLevel = CostLevel.LOW,
    @SerialName("cpu_cost") val cpuCost: CostLevel = level,
    @SerialName("gpu_cost") val gpuCost: CostLevel = level,
    @SerialName("memory_cost") val memoryCost: CostLevel = level,
    val cost: Int = max(
        level.budgetPoints,
        (cpuCost.budgetPoints + gpuCost.budgetPoints + memoryCost.budgetPoints + 2) / 3,
    ),
    @SerialName("supports_low_end") val supportsLowEnd: Boolean = level == CostLevel.LOW,
    @SerialName("fallback_effect") val fallbackEffect: String? = null,
    @SerialName("rendering_technique") val renderingTechnique: String = "2D Matrix Transform",
    @SerialName("max_recommended_particles") val maxRecommendedParticles: Int = 0,
    @SerialName("texture_requirements") val textureRequirements: String = "None (pure transform math)",
    val dependencies: List<String> = emptyList(),
    @SerialName("lightweight_alternatives") val lightweightAlternatives: List<String> = emptyList(),
    @SerialName("known_limitations") val knownLimitations: List<String> = emptyList(),
    @SerialName("quality_reduction_options") val qualityReductionOptions: List<String> = emptyList(),
) {
    /** Convenience constructor for specifying performance [level] and numeric [cost]. */
    constructor(level: CostLevel, cost: Int) : this(
        level = level,
        cpuCost = level,
        gpuCost = level,
        memoryCost = level,
        cost = cost,
    )

    fun supportedProfiles(): List<DevicePerformanceProfile> = buildList {
        if (supportsLowEnd) add(DevicePerformanceProfile.LOW_END)
        if (level != CostLevel.HIGH || supportsLowEnd) add(DevicePerformanceProfile.MID_RANGE)
        add(DevicePerformanceProfile.HIGH_END)
    }

    val budgetPoints: Int
        get() = cost
}

@Serializable
data class EffectParamSpec(
    val key: String,
    val label: String,
    val defaultValue: Double,
    val min: Double,
    val max: Double,
    val step: Double,
    val unit: String = "",
    val isAdvanced: Boolean = false,
    val enumLabels: Map<Int, String> = emptyMap(),
)

@Serializable
data class EffectModuleDefinition(
    val id: String,
    val name: String,
    val category: EffectCategory,
    val phase: Int,
    val description: String,
    val targetTypes: List<PresetTargetType>,
    val defaultDurationSec: Double = 2.0,
    val defaultLoop: Boolean = true,
    val defaultDirection: MotionModifierDirection = MotionModifierDirection.FORWARD,
    val defaultBlendMode: MotionModifierBlendMode = MotionModifierBlendMode.ADD,
    val defaultPivot: PivotAnchorPreset = PivotAnchorPreset.CENTER,
    val defaultIntensity: Double = 1.0,
    val params: List<EffectParamSpec> = emptyList(),
    val performance: EffectPerformanceMetadata = EffectPerformanceMetadata(),
) {
    fun defaultParamMap(): Map<String, Double> = params.associate { it.key to it.defaultValue }
}

/**
 * Reusable combination of multiple modular effects saved as a single preset.
 */
@Serializable
data class EffectComboItem(
    val effectId: String,
    val label: String,
    val enabled: Boolean = true,
    val durationSec: Double = 2.0,
    val delayFrames: Int = 0,
    val loop: Boolean = true,
    val direction: MotionModifierDirection = MotionModifierDirection.FORWARD,
    val blendMode: MotionModifierBlendMode = MotionModifierBlendMode.ADD,
    val intensity: Double = 1.0,
    val speed: Double = 1.0,
    val phaseOffset: Double = 0.0,
    val pivotAnchor: PivotAnchorPreset = PivotAnchorPreset.CENTER,
    val params: Map<String, Double> = emptyMap(),
)

@Serializable
data class EffectComboPreset(
    val id: String,
    val name: String,
    val description: String,
    val isCustom: Boolean = false,
    val items: List<EffectComboItem>,
)

data class EffectSubstitution(
    val originalEffectId: String,
    val fallbackEffectId: String,
    val reason: String,
)

data class EffectStackValidationResult(
    val profile: DevicePerformanceProfile,
    val activeEffectCount: Int,
    val usedBudgetPoints: Int,
    val maxBudgetPoints: Int,
    val isWithinBudget: Boolean,
    val warnings: List<String>,
    val substitutions: List<EffectSubstitution>,
    val recommendedProfile: DevicePerformanceProfile,
)

/**
 * Validates single and combined effect stacks against the active device performance profile.
 * Accounts for non-linear cumulative stacking overhead so combining many individually low-cost
 * effects is never assumed to remain free on low-end tablets.
 */
object EffectBudgetValidator {
    fun validateLayerStack(
        modifiers: List<MotionModifierInstance>,
        profile: DevicePerformanceProfile,
    ): EffectStackValidationResult {
        val enabled = modifiers.filter { it.enabled }
        val warnings = mutableListOf<String>()
        val substitutions = mutableListOf<EffectSubstitution>()
        var rawPoints = 0

        for (mod in enabled) {
            val kernelId = mod.basePresetId ?: mod.presetId
            val meta = EffectsLibraryRegistry.getPerformanceMetadata(kernelId)
            rawPoints += meta.budgetPoints

            if (profile == DevicePerformanceProfile.LOW_END && !meta.supportsLowEnd) {
                val fb = meta.fallbackEffect
                if (fb != null) {
                    substitutions.add(
                        EffectSubstitution(
                            originalEffectId = kernelId,
                            fallbackEffectId = fb,
                            reason = "${mod.label} exceeds Low-End GPU/CPU budget; use lightweight fallback '$fb'.",
                        )
                    )
                }
                warnings.add("${mod.label} is not recommended for Low-End devices (${meta.level.label} cost).")
            }
        }

        // Non-linear stacking penalty: combining >3 simultaneous effects adds compositing/eval overhead
        val stackPenalty = if (enabled.size > 3) (enabled.size - 3) * 2 else 0
        val totalPoints = rawPoints + stackPenalty

        if (enabled.size > profile.maxSimultaneousLayerEffects) {
            warnings.add(
                "Layer has ${enabled.size} active effects (recommended max ${profile.maxSimultaneousLayerEffects} for ${profile.shortLabel})."
            )
        }
        if (totalPoints > profile.maxEffectBudgetPoints) {
            warnings.add(
                "Combined effect cost ($totalPoints pts) exceeds ${profile.shortLabel} budget (${profile.maxEffectBudgetPoints} pts)."
            )
        }

        val recommended = when {
            enabled.size <= DevicePerformanceProfile.LOW_END.maxSimultaneousLayerEffects &&
                totalPoints <= DevicePerformanceProfile.LOW_END.maxEffectBudgetPoints &&
                substitutions.isEmpty() -> DevicePerformanceProfile.LOW_END
            enabled.size <= DevicePerformanceProfile.MID_RANGE.maxSimultaneousLayerEffects &&
                totalPoints <= DevicePerformanceProfile.MID_RANGE.maxEffectBudgetPoints -> DevicePerformanceProfile.MID_RANGE
            else -> DevicePerformanceProfile.HIGH_END
        }

        return EffectStackValidationResult(
            profile = profile,
            activeEffectCount = enabled.size,
            usedBudgetPoints = totalPoints,
            maxBudgetPoints = profile.maxEffectBudgetPoints,
            isWithinBudget = warnings.isEmpty(),
            warnings = warnings,
            substitutions = substitutions,
            recommendedProfile = recommended,
        )
    }

    fun validateCompositionTotalEffects(
        totalActiveEffects: Int,
        profile: DevicePerformanceProfile,
    ): String? {
        return if (totalActiveEffects > profile.maxTotalCompositionEffects) {
            "Scene has $totalActiveEffects active effects (recommended max ${profile.maxTotalCompositionEffects} on ${profile.shortLabel})."
        } else null
    }
}

/**
 * Central registry for all modular effects, built-in starter combinations, and user-saved custom combos.
 */
object EffectsLibraryRegistry {
    private val customCombos = mutableListOf<EffectComboPreset>()

    /** Underlying modular [EffectRegistry] holding all registered [Effect] components. */
    val registry: EffectRegistry
        get() = EffectRegistry.default

    val builtinModules: List<EffectModuleDefinition>
        get() = registry.allDefinitions()

    val builtinCombos: List<EffectComboPreset>
        get() = PHASE1_STARTER_COMBOS

    fun allCombos(): List<EffectComboPreset> = customCombos + builtinCombos

    fun getCustomCombos(): List<EffectComboPreset> = customCombos.toList()

    fun setCustomCombos(list: List<EffectComboPreset>) {
        customCombos.clear()
        customCombos.addAll(list.map { it.copy(isCustom = true) })
    }

    fun getEffectById(id: String): Effect? = registry.get(id)

    fun getModuleById(id: String): EffectModuleDefinition? = registry.get(id)?.definition

    fun isEffectModule(id: String): Boolean = id in registry

    fun getModulesByCategory(category: EffectCategory? = null): List<EffectModuleDefinition> =
        if (category == null) builtinModules else registry.byCategory(category).map { it.definition }

    fun getPerformanceMetadata(effectOrPresetId: String): EffectPerformanceMetadata {
        registry.get(effectOrPresetId)?.let { return it.performance }
        // Controlled default metadata for legacy hair/cloth/character/fx presets
        return when {
            effectOrPresetId.startsWith("hair.") -> EffectPerformanceMetadata(
                level = CostLevel.MEDIUM,
                cpuCost = CostLevel.MEDIUM,
                gpuCost = CostLevel.MEDIUM,
                memoryCost = CostLevel.LOW,
                supportsLowEnd = true,
                fallbackEffect = SwingEffect.ID,
                renderingTechnique = "Segmented 2D Strand Strip Mesh",
                textureRequirements = "Single source sprite bitmap (reused)",
                lightweightAlternatives = listOf(SwingEffect.ID, WiggleEffect.ID),
                qualityReductionOptions = listOf("Reduce strand count in Performance preview"),
            )
            effectOrPresetId.startsWith("cloth.") -> EffectPerformanceMetadata(
                level = CostLevel.MEDIUM,
                cpuCost = CostLevel.LOW,
                gpuCost = CostLevel.MEDIUM,
                memoryCost = CostLevel.LOW,
                supportsLowEnd = true,
                fallbackEffect = SkewEffect.ID,
                renderingTechnique = "Segmented 2D Sine Strip Deformation",
                textureRequirements = "Single source sprite bitmap (reused)",
                lightweightAlternatives = listOf(SkewEffect.ID, SwingEffect.ID),
            )
            else -> EffectPerformanceMetadata(
                level = CostLevel.LOW,
                cpuCost = CostLevel.LOW,
                gpuCost = CostLevel.LOW,
                memoryCost = CostLevel.LOW,
                supportsLowEnd = true,
                renderingTechnique = "2D Matrix Transform + Math Curve",
            )
        }
    }

    fun createInstanceFromModule(
        moduleId: String,
        fps: Double,
        instanceId: String,
        startFrame: Int = 0,
    ): MotionModifierInstance? = registry.createInstance(moduleId, fps, instanceId, startFrame)

    fun resetInstanceParameters(
        instance: MotionModifierInstance,
        fps: Double,
    ): MotionModifierInstance {
        val def = getModuleById(instance.basePresetId ?: instance.presetId) ?: return instance.copy(
            intensity = 1.0,
            speed = 1.0,
            delayFrames = 0,
            phaseOffset = 0.0,
        )
        return instance.copy(
            durationFrames = max(1, (def.defaultDurationSec * max(1.0, fps)).roundToInt()),
            delayFrames = 0,
            loop = def.defaultLoop,
            direction = def.defaultDirection,
            blendMode = def.defaultBlendMode,
            intensity = def.defaultIntensity,
            speed = 1.0,
            phaseOffset = 0.0,
            pivotAnchor = def.defaultPivot,
            params = def.defaultParamMap(),
        )
    }

    fun instantiateCombo(
        combo: EffectComboPreset,
        fps: Double,
        startFrame: Int,
        idGenerator: () -> String,
    ): List<MotionModifierInstance> {
        return combo.items.map { item ->
            val durFrames = max(1, (item.durationSec * max(1.0, fps)).roundToInt())
            MotionModifierInstance(
                id = idGenerator(),
                presetId = item.effectId,
                basePresetId = item.effectId,
                label = item.label,
                enabled = item.enabled,
                startFrame = startFrame,
                durationFrames = durFrames,
                delayFrames = item.delayFrames,
                loop = item.loop,
                direction = item.direction,
                blendMode = item.blendMode,
                intensity = item.intensity,
                speed = item.speed,
                phaseOffset = item.phaseOffset,
                pivotAnchor = item.pivotAnchor,
                params = item.params,
            )
        }
    }

    fun saveCustomComboFromStack(
        name: String,
        description: String,
        modifiers: List<MotionModifierInstance>,
        fps: Double,
    ): EffectComboPreset {
        val safeName = name.trim().ifEmpty { "Custom Effect Combo" }
        val newId = "combo.custom.${safeName.lowercase().replace(Regex("[^a-z0-9]+"), "-")}.${System.currentTimeMillis() % 100000}"
        val items = modifiers.map { m ->
            EffectComboItem(
                effectId = m.basePresetId ?: m.presetId,
                label = m.label,
                enabled = m.enabled,
                durationSec = max(0.15, m.durationFrames.toDouble() / max(1.0, fps)),
                delayFrames = m.delayFrames,
                loop = m.loop,
                direction = m.direction,
                blendMode = m.blendMode,
                intensity = m.intensity,
                speed = m.speed,
                phaseOffset = m.phaseOffset,
                pivotAnchor = m.pivotAnchor,
                params = m.params,
            )
        }
        val combo = EffectComboPreset(
            id = newId,
            name = safeName,
            description = description.trim().ifEmpty {
                items.joinToString(" + ") { it.label }
            },
            isCustom = true,
            items = items,
        )
        customCombos.add(0, combo)
        return combo
    }

    fun deleteCustomCombo(id: String): Boolean = customCombos.removeAll { it.id == id }

    fun exportCustomCombosJson(): String = StudioJson.encodeToString(customCombos.toList())

    fun importCustomCombosJson(json: String): Int {
        val parsed = StudioJson.decodeFromString<List<EffectComboPreset>>(json)
        var added = 0
        for (c in parsed) {
            if (customCombos.none { it.id == c.id }) {
                customCombos.add(c.copy(isCustom = true))
                added++
            }
        }
        return added
    }
}
