package com.example.effects

import android.app.ActivityManager
import android.content.Context
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.max

/**
 * Controlled resource cost classification for effects.
 * Avoids claiming hardware-independent millisecond timings; instead encodes
 * clear relative CPU, GPU, and memory cost tiers for budget validation.
 */
@Serializable
enum class CostLevel(val label: String, val budgetPoints: Int) {
    @SerialName("low") LOW("Low", 1),
    @SerialName("medium") MEDIUM("Medium", 3),
    @SerialName("high") HIGH("High", 6),
}

/**
 * Three configurable target device profiles for Low-End First architecture.
 */
@Serializable
enum class DevicePerformanceProfile(
    val displayName: String,
    val shortLabel: String,
    val ramTierHint: String,
    val maxSimultaneousLayerEffects: Int,
    val maxTotalCompositionEffects: Int,
    val maxEffectBudgetPoints: Int,
    val maxParticleCount: Int,
    val defaultPreviewScale: Float,
    val allowExpensiveVisualEffects: Boolean,
    val maxTextureCacheMb: Int,
) {
    @SerialName("low_end")
    LOW_END(
        displayName = "Profile A — Low-End",
        shortLabel = "Low-End",
        ramTierHint = "3–4 GB RAM · Entry-level CPU/GPU",
        maxSimultaneousLayerEffects = 4,
        maxTotalCompositionEffects = 10,
        maxEffectBudgetPoints = 12,
        maxParticleCount = 32,
        defaultPreviewScale = 0.60f,
        allowExpensiveVisualEffects = false,
        maxTextureCacheMb = 24,
    ),

    @SerialName("mid_range")
    MID_RANGE(
        displayName = "Profile B — Mid-Range",
        shortLabel = "Mid-Range",
        ramTierHint = "4–6 GB RAM · Mid-range GPU",
        maxSimultaneousLayerEffects = 7,
        maxTotalCompositionEffects = 20,
        maxEffectBudgetPoints = 24,
        maxParticleCount = 72,
        defaultPreviewScale = 0.80f,
        allowExpensiveVisualEffects = true,
        maxTextureCacheMb = 48,
    ),

    @SerialName("high_end")
    HIGH_END(
        displayName = "Profile C — High-End",
        shortLabel = "High-End",
        ramTierHint = "8+ GB RAM · High-performance GPU",
        maxSimultaneousLayerEffects = 12,
        maxTotalCompositionEffects = 40,
        maxEffectBudgetPoints = 48,
        maxParticleCount = 140,
        defaultPreviewScale = 1.0f,
        allowExpensiveVisualEffects = true,
        maxTextureCacheMb = 96,
    );

    companion object {
        /**
         * Estimates a starting profile using RAM, low-RAM system flag, and CPU core count.
         * Users can override the profile at any time; RAM alone is never treated as definitive.
         */
        fun detectRecommended(context: Context): DevicePerformanceProfile {
            return runCatching {
                val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                val memInfo = ActivityManager.MemoryInfo()
                am?.getMemoryInfo(memInfo)
                val totalGb = memInfo.totalMem.toDouble() / (1024.0 * 1024.0 * 1024.0)
                val isLowRam = am?.isLowRamDevice == true
                val cores = Runtime.getRuntime().availableProcessors()

                when {
                    isLowRam || totalGb <= 4.2 || cores <= 4 -> LOW_END
                    totalGb <= 6.5 || cores <= 6 -> MID_RANGE
                    else -> HIGH_END
                }
            }.getOrDefault(LOW_END)
        }
    }
}

/**
 * Preview quality modes. Final export quality is strictly independent of preview quality.
 */
@Serializable
enum class PreviewQualityMode(
    val displayName: String,
    val shortLabel: String,
) {
    @SerialName("performance") PERFORMANCE("Performance", "Perf"),
    @SerialName("balanced") BALANCED("Balanced", "Balanced"),
    @SerialName("high_quality") HIGH_QUALITY("High Quality", "High"),
    @SerialName("automatic") AUTOMATIC("Automatic", "Auto"),
}

/**
 * Snapshot of live adaptive preview settings and telemetry.
 */
data class AdaptivePreviewState(
    val deviceProfile: DevicePerformanceProfile = DevicePerformanceProfile.LOW_END,
    val qualityMode: PreviewQualityMode = PreviewQualityMode.AUTOMATIC,
    val adaptiveTier: Int = 1, // 0 = Performance, 1 = Balanced, 2 = High
    val avgFrameRenderMs: Float = 0f,
    val droppedFrames: Int = 0,
    val activeEffectCount: Int = 0,
    val isDegradedByPerformance: Boolean = false,
) {
    val effectiveResolutionScale: Float
        get() = when (qualityMode) {
            PreviewQualityMode.PERFORMANCE -> 0.55f
            PreviewQualityMode.BALANCED -> minOf(0.80f, max(0.65f, deviceProfile.defaultPreviewScale))
            PreviewQualityMode.HIGH_QUALITY -> 1.0f
            PreviewQualityMode.AUTOMATIC -> when (adaptiveTier) {
                0 -> 0.55f
                1 -> minOf(0.80f, deviceProfile.defaultPreviewScale)
                else -> minOf(1.0f, deviceProfile.defaultPreviewScale + 0.15f)
            }
        }

    val effectiveParticleScale: Float
        get() = when (qualityMode) {
            PreviewQualityMode.PERFORMANCE -> 0.35f
            PreviewQualityMode.BALANCED -> 0.65f
            PreviewQualityMode.HIGH_QUALITY -> 1.0f
            PreviewQualityMode.AUTOMATIC -> when (adaptiveTier) {
                0 -> 0.35f
                1 -> 0.70f
                else -> 1.0f
            }
        }

    val effectiveMaxParticles: Int
        get() = max(8, (deviceProfile.maxParticleCount * effectiveParticleScale).toInt())

    val simplifyExpensiveEffects: Boolean
        get() = when (qualityMode) {
            PreviewQualityMode.PERFORMANCE -> true
            PreviewQualityMode.BALANCED -> !deviceProfile.allowExpensiveVisualEffects
            PreviewQualityMode.HIGH_QUALITY -> false
            PreviewQualityMode.AUTOMATIC -> adaptiveTier == 0 || !deviceProfile.allowExpensiveVisualEffects
        }

    val effectiveTierLabel: String
        get() = when (qualityMode) {
            PreviewQualityMode.PERFORMANCE -> "PERF"
            PreviewQualityMode.BALANCED -> "BAL"
            PreviewQualityMode.HIGH_QUALITY -> "HIGH"
            PreviewQualityMode.AUTOMATIC -> when (adaptiveTier) {
                0 -> "AUTO·LOW"
                1 -> "AUTO·MID"
                else -> "AUTO·HI"
            }
        }
}

/**
 * Pure, deterministic state machine that monitors frame rendering time and adapts
 * preview quality automatically without touching export quality.
 */
class AdaptivePreviewMonitor(
    private val slowThresholdMs: Float = 26f,
    private val fastThresholdMs: Float = 13f,
    private val downgradeStreakTarget: Int = 3,
    private val upgradeStreakTarget: Int = 12,
) {
    private var slowStreak = 0
    private var fastStreak = 0

    fun reset() {
        slowStreak = 0
        fastStreak = 0
    }

    fun recordFrame(
        current: AdaptivePreviewState,
        renderTimeMs: Float,
        activeEffectCount: Int,
        memoryPressureHigh: Boolean = false,
    ): AdaptivePreviewState {
        val clampedMs = renderTimeMs.coerceIn(0.1f, 500f)
        val nextAvg = if (current.avgFrameRenderMs <= 0.01f) {
            clampedMs
        } else {
            current.avgFrameRenderMs * 0.78f + clampedMs * 0.22f
        }
        val dropped = if (clampedMs > 33.3f) current.droppedFrames + 1 else current.droppedFrames

        if (current.qualityMode != PreviewQualityMode.AUTOMATIC) {
            return current.copy(
                avgFrameRenderMs = nextAvg,
                droppedFrames = dropped,
                activeEffectCount = activeEffectCount,
                isDegradedByPerformance = false,
            )
        }

        val maxTierForProfile = when (current.deviceProfile) {
            DevicePerformanceProfile.LOW_END -> 1
            DevicePerformanceProfile.MID_RANGE -> 2
            DevicePerformanceProfile.HIGH_END -> 2
        }
        var nextTier = current.adaptiveTier.coerceIn(0, maxTierForProfile)
        val overEffectSoftLimit = activeEffectCount > current.deviceProfile.maxTotalCompositionEffects

        if (memoryPressureHigh || clampedMs > slowThresholdMs || overEffectSoftLimit) {
            slowStreak++
            fastStreak = 0
            if (slowStreak >= downgradeStreakTarget && nextTier > 0) {
                nextTier--
                slowStreak = 0
            }
        } else if (nextAvg < fastThresholdMs) {
            fastStreak++
            slowStreak = 0
            if (fastStreak >= upgradeStreakTarget && nextTier < maxTierForProfile) {
                nextTier++
                fastStreak = 0
            }
        } else {
            slowStreak = 0
            fastStreak = 0
        }

        return current.copy(
            adaptiveTier = nextTier,
            avgFrameRenderMs = nextAvg,
            droppedFrames = dropped,
            activeEffectCount = activeEffectCount,
            isDegradedByPerformance = nextTier < maxTierForProfile,
        )
    }
}
