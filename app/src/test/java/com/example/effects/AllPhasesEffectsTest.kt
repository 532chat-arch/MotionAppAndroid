package com.example.effects

import com.example.commands.CommandManager
import com.example.commands.ProjectCommands
import com.example.data.createLostKingdomProject
import com.example.engine.EvaluatedTransform
import com.example.engine.applyLayerModifiers
import com.example.engine.evaluateCompositionAtFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AllPhasesEffectsTest {

    @Test
    fun `all 61 modular effects across Phases 1 to 5 register and evaluate deterministically`() {
        val registry = EffectRegistry.default
        assertEquals(61, registry.size)
        assertEquals(16, registry.byPhase(1).size)
        assertEquals(8, registry.byPhase(2).size)
        assertEquals(10, registry.byPhase(3).size)
        assertEquals(6, registry.byPhase(4).size)
        assertEquals(21, registry.byPhase(5).size)

        val base = EvaluatedTransform(x = 400.0, y = 260.0, scaleX = 1.0, scaleY = 1.0, rotationDeg = 0.0, opacity = 1.0)
        for (effect in registry.allEffects()) {
            val inst = effect.createDefaultInstance(fps = 24.0, instanceId = "all_${effect.id}", startFrame = 0)
            val r1 = applyLayerModifiers(base, listOf(inst), frame = 14.0, objWidth = 220.0, objHeight = 220.0)
            val r2 = applyLayerModifiers(base, listOf(inst), frame = 14.0, objWidth = 220.0, objHeight = 220.0)
            assertEquals("Effect ${effect.id} transform must be deterministic", r1.transform, r2.transform)
            assertEquals("Effect ${effect.id} visualFx must be deterministic", r1.visualFx, r2.visualFx)
            assertEquals("Effect ${effect.id} particleFx must be deterministic", r1.particleFxList, r2.particleFxList)
            assertEquals("Effect ${effect.id} textFx must be deterministic", r1.textFx, r2.textFx)
            assertEquals("Effect ${effect.id} audioFx must be deterministic", r1.audioFx, r2.audioFx)
        }
    }

    @Test
    fun `phase 2 character effects emit mesh deform, facial overlays, and low-end strand capping`() {
        val hairFx = HairSwayEffect()
        val hairMod = hairFx.createDefaultInstance(24.0, "h1")
            .copy(params = mapOf("amplitude" to 18.0, "frequency" to 2.0, "windBias" to 8.0, "strandCount" to 16.0, "turbulence" to 0.4))

        val lowCtx = EffectEvaluationContext().apply {
            set(hairMod, 0.25, 12.0, 200.0, 200.0, deviceProfile = DevicePerformanceProfile.LOW_END, reducedQuality = true)
        }
        val highCtx = EffectEvaluationContext().apply {
            set(hairMod, 0.25, 12.0, 200.0, 200.0, deviceProfile = DevicePerformanceProfile.HIGH_END, reducedQuality = false)
        }
        val lowState = EffectTransformState()
        val highState = EffectTransformState()
        hairFx.apply(lowCtx, lowState)
        hairFx.apply(highCtx, highState)

        assertNotNull(lowState.meshDeform)
        assertNotNull(highState.meshDeform)
        assertTrue("Low-End must cap hair strands to <= 6", lowState.meshDeform!!.strandCount <= 6)
        assertEquals(16, highState.meshDeform!!.strandCount)

        // Verify Eye Blink, Eye Look, Mouth Shapes payloads
        val base = EvaluatedTransform(0.0, 0.0, 1.0, 1.0, 0.0, 1.0)
        val look = EyeLookEffect().createDefaultInstance(24.0, "el1")
        val mouth = MouthShapesEffect().createDefaultInstance(24.0, "ms1")
        val merged = applyLayerModifiers(base, listOf(look, mouth), frame = 10.0, objWidth = 200.0, objHeight = 200.0)
        assertNotNull(merged.visualFx)
        assertTrue(merged.visualFx!!.hasEyeLook)
        assertTrue(merged.visualFx!!.mouthShapeIndex in 0..4)
    }

    @Test
    fun `phase 3 visual effects combine color matrix and trigger low-end fallback for high-cost blur`() {
        val blur = BlurEffect().createDefaultInstance(24.0, "b1")
        val lowValidation = EffectBudgetValidator.validateLayerStack(listOf(blur), DevicePerformanceProfile.LOW_END)
        assertFalse("High-cost Blur should warn on Low-End profile", lowValidation.isWithinBudget)
        assertTrue("Blur should recommend fallback on Low-End", lowValidation.substitutions.any { it.fallbackEffectId == OpacityEffect.ID })

        val base = EvaluatedTransform(0.0, 0.0, 1.0, 1.0, 0.0, 1.0)
        val bright = BrightnessEffect().createDefaultInstance(24.0, "br1")
        val contrast = ContrastEffect().createDefaultInstance(24.0, "ct1")
        val sat = SaturationEffect().createDefaultInstance(24.0, "sa1")
        val vig = VignetteEffect().createDefaultInstance(24.0, "vg1")
        val merged = applyLayerModifiers(base, listOf(bright, contrast, sat, vig), frame = 8.0, objWidth = 300.0, objHeight = 300.0)
        val vfx = merged.visualFx!!
        assertTrue(vfx.hasColorMatrix())
        assertTrue(vfx.brightness > 0.1f)
        assertTrue(vfx.contrast > 1.1f)
        assertTrue(vfx.saturation > 1.1f)
        assertTrue(vfx.vignetteIntensity > 0.3f)
    }

    @Test
    fun `phase 4 particle pool reuses preallocated instances and enforces device caps`() {
        val pool = ParticlePool(capacity = 120)
        val firstRef = pool.get(0)

        for (fx in PHASE4_PARTICLE_EFFECTS) {
            val mod = fx.createDefaultInstance(24.0, "p_${fx.id}")
            val lowCtx = EffectEvaluationContext().apply {
                set(mod, 0.3, 18.0, 320.0, 240.0, deviceProfile = DevicePerformanceProfile.LOW_END)
            }
            val state = EffectTransformState()
            fx.apply(lowCtx, state)
            assertNotNull("Particle effect ${fx.id} must emit particleFx", state.particleFx)
            assertTrue(
                "Particle count must respect Low-End cap",
                state.particleFx!!.count <= DevicePerformanceProfile.LOW_END.maxParticleCount,
            )

            val populated = pool.populateDeterministic(state.particleFx!!, particleScale = 0.6f, maxAllowed = 45)
            assertTrue("Populated particle count should be in 1..45", populated in 1..45)
            assertSame("ParticlePool must reuse PooledParticle objects without reallocation", firstRef, pool.get(0))
        }
    }

    @Test
    fun `phase 5 typography, transitions, audio-reactive, and all 5 signature combos evaluate cleanly`() {
        // Typography + Audio + Transitions
        val base = EvaluatedTransform(0.0, 0.0, 1.0, 1.0, 0.0, 1.0)
        val typeFx = TypewriterRevealEffect().createDefaultInstance(24.0, "tw1")
        val waveFx = TextWaveEffect().createDefaultInstance(24.0, "wv1")
        val wipeFx = WipeTransitionEffect().createDefaultInstance(24.0, "wp1")
        val waveformFx = AudioWaveformEffect().createDefaultInstance(24.0, "aw1")
        val res = applyLayerModifiers(base, listOf(typeFx, waveFx, wipeFx, waveformFx), frame = 12.0, objWidth = 300.0, objHeight = 120.0)
        assertNotNull(res.textFx)
        assertTrue(res.textFx!!.typewriterReveal01 in 0.1f..0.9f)
        assertTrue(res.textFx!!.waveAmplitude > 5f)
        assertNotNull(res.visualFx)
        assertTrue(res.visualFx!!.wipeDirection == 0)
        assertNotNull(res.audioFx)
        assertTrue(res.audioFx!!.showWaveform)

        // All 5 canonical multi-phase combos evaluate cleanly inside a real project
        val canonicalComboIds = listOf(
            "combo.character-entrance",
            "combo.wind-blown-hair",
            "combo.cinematic-lyrics",
            "combo.horror-scene",
            "combo.floating-environment",
        )
        for (comboId in canonicalComboIds) {
            val combo = EffectsLibraryRegistry.allCombos().find { it.id == comboId }
            assertNotNull("Missing canonical combo $comboId", combo)
            var project = createLostKingdomProject()
            val comp = project.compositions.first()
            val layerId = comp.layers.first().id
            val mgr = CommandManager()
            val items = EffectsLibraryRegistry.instantiateCombo(combo!!, 24.0, 0) { "c_${comboId}_${System.nanoTime()}" }
            project = mgr.execute(project, ProjectCommands.addLayerModifiersBatch(comp.id, layerId, items, combo.name)).first
            val eval = evaluateCompositionAtFrame(project, comp.id, frame = 12)
            assertNotNull("Composition evaluation failed for $comboId", eval)
        }
    }
}
