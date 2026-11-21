package com.example.effects

import com.example.schema.StudioJson
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

@Serializable
data class PortableEffectKnowledgePackage(
    val format: String = "motion-studio-effects-knowledge",
    val version: String = "1.0.0",
    @SerialName("implemented_phase") val implementedPhase: Int = 5,
    @SerialName("ai_guidance") val aiGuidance: String =
        "When creating an effect for a low-end Android device, prefer transform-based animation, " +
            "sprite-based particles and lightweight compositing. Avoid expensive shaders, repeated blur passes " +
            "and physical simulations. Use only documented rendering capabilities and respect profile budgets.",
    @SerialName("device_profiles") val deviceProfiles: List<DeviceProfileDoc>,
    val effects: List<EffectModuleDefinition>,
    @SerialName("starter_combos") val starterCombos: List<EffectComboPreset>,
)

@Serializable
data class DeviceProfileDoc(
    val id: String,
    val name: String,
    @SerialName("ram_tier_hint") val ramTierHint: String,
    @SerialName("max_simultaneous_layer_effects") val maxSimultaneousLayerEffects: Int,
    @SerialName("max_total_composition_effects") val maxTotalCompositionEffects: Int,
    @SerialName("max_effect_budget_points") val maxEffectBudgetPoints: Int,
    @SerialName("max_particle_count") val maxParticleCount: Int,
    @SerialName("default_preview_scale") val defaultPreviewScale: Float,
    @SerialName("allow_expensive_visual_effects") val allowExpensiveVisualEffects: Boolean,
    @SerialName("max_texture_cache_mb") val maxTextureCacheMb: Int,
)

/**
 * Exports portable Markdown and JSON knowledge packages documenting all registered effects,
 * their performance classifications, supported device profiles, parameters, and limitations.
 */
object PortableEffectKnowledgeExporter {
    fun buildPackage(): PortableEffectKnowledgePackage {
        val profiles = DevicePerformanceProfile.entries.map { p ->
            DeviceProfileDoc(
                id = p.name.lowercase(),
                name = p.displayName,
                ramTierHint = p.ramTierHint,
                maxSimultaneousLayerEffects = p.maxSimultaneousLayerEffects,
                maxTotalCompositionEffects = p.maxTotalCompositionEffects,
                maxEffectBudgetPoints = p.maxEffectBudgetPoints,
                maxParticleCount = p.maxParticleCount,
                defaultPreviewScale = p.defaultPreviewScale,
                allowExpensiveVisualEffects = p.allowExpensiveVisualEffects,
                maxTextureCacheMb = p.maxTextureCacheMb,
            )
        }
        return PortableEffectKnowledgePackage(
            deviceProfiles = profiles,
            effects = EffectsLibraryRegistry.builtinModules,
            starterCombos = EffectsLibraryRegistry.allCombos(),
        )
    }

    fun exportJson(): String = StudioJson.encodeToString(buildPackage())

    fun exportMarkdown(): String = buildString {
        val pkg = buildPackage()
        appendLine("# Motion Studio Pro — Portable Effects Knowledge (Phase ${pkg.implementedPhase})")
        appendLine()
        appendLine("> **AI Guidance:** ${pkg.aiGuidance}")
        appendLine()
        appendLine("## Target Device Profiles")
        appendLine("| Profile | Tier | Max Layer FX | Max Scene FX | Budget Pts | Max Particles | Preview Scale |")
        appendLine("| --- | --- | --- | --- | --- | --- | --- |")
        for (p in pkg.deviceProfiles) {
            appendLine("| ${p.name} | ${p.ramTierHint} | ${p.maxSimultaneousLayerEffects} | ${p.maxTotalCompositionEffects} | ${p.maxEffectBudgetPoints} | ${p.maxParticleCount} | ${(p.defaultPreviewScale * 100).toInt()}% |")
        }
        appendLine()
        appendLine("## Registered Effects (${pkg.effects.size})")
        for (fx in pkg.effects) {
            val perf = fx.performance
            appendLine("### ${fx.name} (`${fx.id}`)")
            appendLine("- **Category:** ${fx.category.label} (Phase ${fx.phase})")
            appendLine("- **Description:** ${fx.description}")
            appendLine("- **Rendering Technique:** ${perf.renderingTechnique}")
            appendLine("- **Costs:** Level=${perf.level.label}, CPU=${perf.cpuCost.label}, GPU=${perf.gpuCost.label}, Memory=${perf.memoryCost.label}")
            appendLine("- **Supported Profiles:** ${perf.supportedProfiles().joinToString(", ") { it.shortLabel }}")
            appendLine("- **Max Recommended Particles:** ${perf.maxRecommendedParticles}")
            appendLine("- **Texture Requirements:** ${perf.textureRequirements}")
            appendLine("- **Fallback Effect:** ${perf.fallbackEffect ?: "None"}")
            if (perf.dependencies.isNotEmpty()) {
                appendLine("- **Dependencies:** ${perf.dependencies.joinToString(", ")}")
            }
            if (perf.lightweightAlternatives.isNotEmpty()) {
                appendLine("- **Lightweight Alternatives:** ${perf.lightweightAlternatives.joinToString(", ")}")
            }
            if (perf.knownLimitations.isNotEmpty()) {
                appendLine("- **Known Limitations:** ${perf.knownLimitations.joinToString("; ")}")
            }
            if (perf.qualityReductionOptions.isNotEmpty()) {
                appendLine("- **Quality Reduction Options:** ${perf.qualityReductionOptions.joinToString("; ")}")
            }
            if (fx.params.isNotEmpty()) {
                val pSummary = fx.params.joinToString(", ") { "${it.key}=${it.defaultValue} [${it.min}..${it.max}]" }
                appendLine("- **Parameters:** $pSummary")
            }
            appendLine()
        }
    }
}
