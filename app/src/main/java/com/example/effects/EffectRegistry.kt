package com.example.effects

import android.graphics.Matrix
import com.example.schema.MotionModifierDirection
import com.example.schema.MotionModifierInstance
import kotlin.math.floor
import kotlin.math.max

/**
 * Lifecycle states managed by [EffectRegistry].
 */
enum class RegistryLifecycleState {
    /** Registry has been constructed without initial effects and has not yet been initialized. */
    UNINITIALIZED,

    /** Registry is active and ready for effect registration, retrieval, and evaluation. */
    ACTIVE,

    /** Registry has been released; all registered effects have been torn down and further writes are rejected. */
    RELEASED,
}

/**
 * Thread-safe, lifecycle-managed modular registry for [Effect] components.
 *
 * Manages effect lifecycle (`initialize`, `onRegistered`, `onUnregistered`, `clear`, `reset`,
 * `release`), registration, retrieval (`get`, `getOrThrow`, `findByName`, `byCategory`,
 * `byPhase`, `compatibleWith`), instance creation, and zero-allocation 2D matrix chain
 * evaluation for single or combined effects.
 *
 * Use [EffectRegistry.createPhase1Registry] for a dedicated Phase 1 registry containing the
 * 10 core Phase 1 effects (Position, Scale, Rotation, Opacity, Follow Path, Bounce, Shake,
 * Float, Wiggle, Spring), or [EffectRegistry.default] for the application-wide catalog.
 */
class EffectRegistry(
    initialEffects: Iterable<Effect> = emptyList(),
) {
    private val lock = Any()
    private val effectsById = LinkedHashMap<String, Effect>()

    @Volatile
    private var state: RegistryLifecycleState = RegistryLifecycleState.UNINITIALIZED

    init {
        val list = initialEffects.toList()
        if (list.isNotEmpty()) {
            initialize(list)
        }
    }

    /** Current lifecycle state of this registry. */
    val lifecycleState: RegistryLifecycleState
        get() = state

    /** True when the registry is active and accepting lookups/registrations. */
    val isActive: Boolean
        get() = state == RegistryLifecycleState.ACTIVE

    /** True once [release] has been called. */
    val isReleased: Boolean
        get() = state == RegistryLifecycleState.RELEASED

    /**
     * Initializes (or re-initializes) the registry with [effects] (defaults to the 10 core
     * Phase 1 effects), invoking [Effect.onUnregistered] on any previously registered effects
     * and [Effect.onRegistered] on each newly registered effect.
     *
     * @throws IllegalStateException if [release] has already been called.
     */
    fun initialize(effects: Iterable<Effect> = PHASE1_CORE_EFFECTS): EffectRegistry {
        synchronized(lock) {
            checkNotReleased()
            unregisterAllLocked()
            state = RegistryLifecycleState.ACTIVE
            for (effect in effects) {
                registerLocked(effect)
            }
        }
        return this
    }

    /**
     * Convenience helper that registers the Phase 1 effects (Position, Scale, Rotation,
     * Opacity, Follow Path, Bounce, Shake, Float, Wiggle, Spring).
     *
     * Pass `includeExtendedTransformMotion = true` to also include the 6 additional
     * Phase 1 Transform/Motion modules (Anchor Point, Skew, Flip, Pulse, Swing, Stagger).
     */
    fun registerPhase1Effects(includeExtendedTransformMotion: Boolean = false): EffectRegistry =
        registerAll(if (includeExtendedTransformMotion) PHASE1_EFFECTS else PHASE1_CORE_EFFECTS)

    /**
     * Registers or replaces a modular [Effect] by its unique [Effect.id].
     * Transitions [RegistryLifecycleState.UNINITIALIZED] to [RegistryLifecycleState.ACTIVE]
     * and invokes lifecycle hooks ([Effect.onUnregistered] on replacement, [Effect.onRegistered]
     * on the newly registered effect).
     *
     * @throws IllegalStateException if [release] has already been called.
     */
    fun register(effect: Effect): EffectRegistry {
        synchronized(lock) {
            checkNotReleased()
            if (state == RegistryLifecycleState.UNINITIALIZED) {
                state = RegistryLifecycleState.ACTIVE
            }
            registerLocked(effect)
        }
        return this
    }

    /** Registers a collection of [Effect] components in deterministic order. */
    fun registerAll(effects: Iterable<Effect>): EffectRegistry {
        synchronized(lock) {
            checkNotReleased()
            if (state == RegistryLifecycleState.UNINITIALIZED) {
                state = RegistryLifecycleState.ACTIVE
            }
            for (effect in effects) {
                registerLocked(effect)
            }
        }
        return this
    }

    /**
     * Removes a registered effect by ID and invokes [Effect.onUnregistered].
     * Returns true if an effect was removed.
     */
    fun unregister(effectId: String): Boolean {
        val removed = synchronized(lock) {
            effectsById.remove(effectId)
        } ?: return false
        removed.onUnregistered()
        return true
    }

    /**
     * Removes all registered effects and invokes [Effect.onUnregistered] on each,
     * leaving the registry active (unless already released) and ready for reuse.
     */
    fun clear() {
        synchronized(lock) {
            unregisterAllLocked()
        }
    }

    /**
     * Resets the registry by clearing all current effects and registering [effects]
     * (defaults to [PHASE1_CORE_EFFECTS]).
     *
     * @throws IllegalStateException if [release] has already been called.
     */
    fun reset(effects: Iterable<Effect> = PHASE1_CORE_EFFECTS): EffectRegistry =
        initialize(effects)

    /**
     * Releases this registry and tears down all registered effects, invoking [Effect.onRelease]
     * followed by [Effect.onUnregistered] on each registered effect. Idempotent.
     */
    fun release() {
        val toRelease = synchronized(lock) {
            if (state == RegistryLifecycleState.RELEASED) return
            val snapshot = effectsById.values.toList()
            effectsById.clear()
            state = RegistryLifecycleState.RELEASED
            snapshot
        }
        for (effect in toRelease) {
            effect.onRelease()
            effect.onUnregistered()
        }
    }

    /** Looks up a registered [Effect] by ID, or null if not registered. */
    fun get(effectId: String): Effect? = synchronized(lock) { effectsById[effectId] }

    /**
     * Looks up a registered [Effect] by ID, throwing [NoSuchElementException] if absent.
     */
    fun getOrThrow(effectId: String): Effect =
        get(effectId) ?: throw NoSuchElementException("Effect '$effectId' is not registered in EffectRegistry.")

    /** Returns true if an [Effect] with [effectId] is registered. */
    operator fun contains(effectId: String): Boolean = synchronized(lock) { effectsById.containsKey(effectId) }

    /** Number of registered modular effects. */
    val size: Int
        get() = synchronized(lock) { effectsById.size }

    /** Returns all registered effect IDs in deterministic registration order. */
    fun ids(): Set<String> = synchronized(lock) { LinkedHashSet(effectsById.keys) }

    /** Returns all registered [Effect] instances in registration order. */
    fun allEffects(): List<Effect> = synchronized(lock) { effectsById.values.toList() }

    /** Returns metadata definitions for all registered effects. */
    fun allDefinitions(): List<EffectModuleDefinition> =
        synchronized(lock) { effectsById.values.map { it.definition } }

    /** Searches registered effects by case-insensitive display name or effect ID substring. */
    fun findByName(query: String): List<Effect> {
        val needle = query.trim()
        if (needle.isEmpty()) return allEffects()
        return synchronized(lock) {
            effectsById.values.filter {
                it.name.contains(needle, ignoreCase = true) ||
                    it.id.contains(needle, ignoreCase = true)
            }
        }
    }

    /** Filters registered effects by [EffectCategory]. */
    fun byCategory(category: EffectCategory): List<Effect> =
        synchronized(lock) { effectsById.values.filter { it.category == category } }

    /** Filters registered effects by implementation phase (e.g., Phase 1). */
    fun byPhase(phase: Int): List<Effect> =
        synchronized(lock) { effectsById.values.filter { it.phase == phase } }

    /** Returns effects compatible with the given [DevicePerformanceProfile]. */
    fun compatibleWith(profile: DevicePerformanceProfile): List<Effect> =
        synchronized(lock) { effectsById.values.filter { profile in it.metadata.supportedProfiles() } }

    /** Creates a serializable [MotionModifierInstance] using the registered effect's defaults. */
    fun createInstance(
        effectId: String,
        fps: Double,
        instanceId: String,
        startFrame: Int = 0,
    ): MotionModifierInstance? = get(effectId)?.createDefaultInstance(fps, instanceId, startFrame)

    /**
     * Evaluates a single registered effect into [out] without allocating.
     * Returns true if [effectId] was found and evaluated, false otherwise.
     */
    fun evaluate(
        effectId: String,
        context: EffectEvaluationContext,
        out: EffectTransformState,
    ): Boolean {
        val effect = get(effectId) ?: return false
        effect.apply(context, out)
        return true
    }

    /**
     * Evaluates and composes an ordered stack of [MotionModifierInstance] effects into a single
     * combined 2D affine [outMatrix], returning the combined opacity multiplier in `[0.0, 1.0]`.
     *
     * Reuses [scratchContext] and [scratchState] to remain allocation-free inside render loops.
     */
    fun composeMatrixChain(
        modifiers: List<MotionModifierInstance>,
        frame: Double,
        objWidth: Double,
        objHeight: Double,
        outMatrix: Matrix2D,
        deviceProfile: DevicePerformanceProfile = DevicePerformanceProfile.MID_RANGE,
        reducedQuality: Boolean = false,
        scratchContext: EffectEvaluationContext = EffectEvaluationContext(),
        scratchState: EffectTransformState = EffectTransformState(),
    ): Double {
        outMatrix.reset()
        var opacity = 1.0

        for (mod in modifiers) {
            val normalizedTime = resolveNormalizedProgress(mod, frame) ?: continue
            val kernelId = mod.basePresetId ?: mod.presetId
            val effect = get(kernelId) ?: continue

            scratchContext.set(
                modifier = mod,
                normalizedTime = normalizedTime,
                frame = frame,
                objWidth = objWidth,
                objHeight = objHeight,
                deviceProfile = deviceProfile,
                reducedQuality = reducedQuality,
            )
            effect.apply(scratchContext, scratchState)
            outMatrix.preConcat(scratchState.matrix)
            opacity = (opacity * scratchState.effectiveOpacityMultiplier()).coerceIn(0.0, 1.0)
        }
        return opacity
    }

    /**
     * Applies all enabled registered effects on [layer] at [frame] into a single [Matrix2D].
     */
    fun applyAllMatrix2D(
        layer: Layer,
        frame: Int,
        outMatrix: Matrix2D = Matrix2D(),
        deviceProfile: DevicePerformanceProfile = DevicePerformanceProfile.MID_RANGE,
        reducedQuality: Boolean = false,
    ): Matrix2D {
        val objWidth = layer.objects.firstOrNull()?.width ?: 200.0
        val objHeight = layer.objects.firstOrNull()?.height ?: 200.0
        composeMatrixChain(
            modifiers = layer.modifiers,
            frame = frame.toDouble(),
            objWidth = objWidth,
            objHeight = objHeight,
            outMatrix = outMatrix,
            deviceProfile = deviceProfile,
            reducedQuality = reducedQuality,
        )
        return outMatrix
    }

    /**
     * Applies all enabled registered effects on [layer] at [frame] and returns an Android [Matrix].
     */
    fun applyAll(
        layer: Layer,
        frame: Int,
        outMatrix: Matrix = Matrix(),
        deviceProfile: DevicePerformanceProfile = DevicePerformanceProfile.MID_RANGE,
        reducedQuality: Boolean = false,
    ): Matrix {
        val m2d = applyAllMatrix2D(
            layer = layer,
            frame = frame,
            deviceProfile = deviceProfile,
            reducedQuality = reducedQuality,
        )
        m2d.toAndroidMatrix(outMatrix)
        return outMatrix
    }

    private fun checkNotReleased() {
        check(state != RegistryLifecycleState.RELEASED) {
            "EffectRegistry has been released and cannot be modified."
        }
    }

    private fun registerLocked(effect: Effect) {
        val previous = effectsById.put(effect.id, effect)
        if (previous != null && previous !== effect) {
            previous.onUnregistered()
        }
        effect.onRegistered()
    }

    private fun unregisterAllLocked() {
        if (effectsById.isEmpty()) return
        val existing = effectsById.values.toList()
        effectsById.clear()
        for (effect in existing) {
            effect.onUnregistered()
        }
    }

    companion object {
        /**
         * Shared default registry pre-loaded with all 61 modular effects across Phases 1–5:
         * Transform (8), Motion (8), Character (8), Visual (10), Particles (6),
         * Typography (10), Transitions (6), and Audio-Reactive (5).
         */
        val default: EffectRegistry = EffectRegistry(ALL_BUILTIN_EFFECTS)

        /**
         * Creates and initializes a lifecycle-managed [EffectRegistry] pre-loaded with the
         * 10 core Phase 1 effects: Position, Scale, Rotation, Opacity, Follow Path,
         * Bounce, Shake, Float, Wiggle, and Spring.
         *
         * Set [includeExtendedTransformMotion] to `true` to also register the 6 extended
         * Phase 1 Transform/Motion modules (Anchor Point, Skew, Flip, Pulse, Swing, Stagger).
         */
        fun createPhase1Registry(includeExtendedTransformMotion: Boolean = false): EffectRegistry =
            EffectRegistry().initialize(
                if (includeExtendedTransformMotion) PHASE1_EFFECTS else PHASE1_CORE_EFFECTS,
            )

        internal fun resolveNormalizedProgress(
            modifier: MotionModifierInstance,
            frame: Double,
        ): Double? {
            if (!modifier.enabled) return null
            val effectiveStart = modifier.startFrame + modifier.delayFrames
            val rel = (frame - effectiveStart) * max(0.05, modifier.speed)
            if (rel < 0.0) return null

            val dur = max(1, modifier.durationFrames).toDouble()
            val rawCycle = rel / dur + modifier.phaseOffset
            if (!modifier.loop && rel > dur) return 1.0

            val norm = if (modifier.loop) {
                val mod = rawCycle - floor(rawCycle)
                if (mod < 0.0) mod + 1.0 else mod
            } else {
                rawCycle.coerceIn(0.0, 1.0)
            }

            return when (modifier.direction) {
                MotionModifierDirection.FORWARD -> norm
                MotionModifierDirection.REVERSE -> 1.0 - norm
                MotionModifierDirection.PINGPONG -> {
                    val cycleIdx = floor(rawCycle).toInt()
                    val p = rawCycle - floor(rawCycle)
                    if (cycleIdx % 2 == 0) p else 1.0 - p
                }
            }
        }
    }
}
