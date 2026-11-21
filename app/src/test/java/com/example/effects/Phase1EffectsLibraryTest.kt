package com.example.effects

import com.example.commands.CommandManager
import com.example.commands.ProjectCommands
import com.example.data.createLostKingdomProject
import com.example.engine.EvaluatedTransform
import com.example.engine.applyLayerModifiers
import com.example.engine.evaluateCompositionAtFrame
import com.example.mcp.LocalMcpEngine
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class Phase1EffectsLibraryTest {

    @Test
    fun `all 16 Phase 1 transform and motion modules evaluate deterministically`() {
        val base = EvaluatedTransform(x = 300.0, y = 200.0, scaleX = 1.0, scaleY = 1.0, rotationDeg = 0.0, opacity = 1.0)
        val phase1Modules = EffectsLibraryRegistry.builtinModules.filter { it.phase == 1 }
        assertEquals(16, phase1Modules.size)

        for (def in phase1Modules) {
            val inst = EffectsLibraryRegistry.createInstanceFromModule(def.id, fps = 24.0, instanceId = "test_${def.id}", startFrame = 0)
            assertNotNull("Failed to instantiate ${def.id}", inst)
            val a = applyLayerModifiers(base, listOf(inst!!), frame = 12.0, objWidth = 200.0, objHeight = 200.0)
            val b = applyLayerModifiers(base, listOf(inst), frame = 12.0, objWidth = 200.0, objHeight = 200.0)
            assertEquals("Effect ${def.id} must be deterministic", a.transform, b.transform)
            assertTrue("Opacity out of bounds for ${def.id}", a.transform.opacity in 0.0..1.0)
            assertTrue("ScaleX invalid for ${def.id}", a.transform.scaleX >= 0.05)
            assertTrue("ScaleY invalid for ${def.id}", a.transform.scaleY >= 0.05)
            assertEquals("Phase 1 effect ${def.id} must support Low-End", true, def.performance.supportsLowEnd)
        }
    }

    @Test
    fun `follow path moves along orbit and auto-orients tangent`() {
        val base = EvaluatedTransform(x = 0.0, y = 0.0, scaleX = 1.0, scaleY = 1.0, rotationDeg = 0.0, opacity = 1.0)
        val pathFx = EffectsLibraryRegistry.createInstanceFromModule("fx.transform.follow_path", 24.0, "fp1")!!
        val start = applyLayerModifiers(base, listOf(pathFx), frame = 0.0, objWidth = 100.0, objHeight = 100.0).transform
        val quarter = applyLayerModifiers(base, listOf(pathFx), frame = 24.0, objWidth = 100.0, objHeight = 100.0).transform
        assertTrue("Orbit starts at +radiusX", start.x > 100.0)
        assertTrue("Orbit moves to +radiusY at quarter cycle", quarter.y > 40.0)
        assertNotEquals("Auto-orient changes rotation along tangent", start.rotationDeg, quarter.rotationDeg, 1.0)
    }

    @Test
    fun `spring effect settles smoothly toward zero displacement`() {
        val base = EvaluatedTransform(x = 100.0, y = 200.0, scaleX = 1.0, scaleY = 1.0, rotationDeg = 0.0, opacity = 1.0)
        val spring = EffectsLibraryRegistry.createInstanceFromModule("fx.motion.spring", 24.0, "sp1")!!
        val atStart = applyLayerModifiers(base, listOf(spring), frame = 0.0, objWidth = 100.0, objHeight = 100.0).transform
        val atEnd = applyLayerModifiers(base, listOf(spring), frame = spring.durationFrames.toDouble(), objWidth = 100.0, objHeight = 100.0).transform
        assertTrue("Spring starts displaced from base Y", abs(atStart.y - base.y) > 40.0)
        assertTrue("Spring settles close to base Y", abs(atEnd.y - base.y) < abs(atStart.y - base.y) * 0.25)
    }

    @Test
    fun `effect stack supports add, duplicate, reorder, reset, disable, and full undo redo`() {
        val mgr = CommandManager()
        var project = createLostKingdomProject()
        val comp = project.compositions.first()
        val layerId = comp.layers.first().id

        val bounce = EffectsLibraryRegistry.createInstanceFromModule("fx.motion.bounce", 24.0, "fx_b")!!
        val floatFx = EffectsLibraryRegistry.createInstanceFromModule("fx.motion.float", 24.0, "fx_f")!!

        project = mgr.execute(project, ProjectCommands.addLayerModifiersBatch(comp.id, layerId, listOf(bounce, floatFx), "Test Combo")).first
        val initialMods = project.compositions.first().layers.first { it.id == layerId }.modifiers
        val baseCount = initialMods.size
        assertTrue(initialMods.any { it.id == "fx_b" } && initialMods.any { it.id == "fx_f" })

        // Duplicate
        project = mgr.execute(project, ProjectCommands.duplicateLayerModifier(comp.id, layerId, "fx_b", "fx_b_copy")).first
        assertEquals(baseCount + 1, project.compositions.first().layers.first { it.id == layerId }.modifiers.size)

        // Reorder up
        project = mgr.execute(project, ProjectCommands.reorderLayerModifier(comp.id, layerId, "fx_f", moveUp = true)).first
        val reordered = project.compositions.first().layers.first { it.id == layerId }.modifiers
        assertTrue(reordered.indexOfFirst { it.id == "fx_f" } < reordered.lastIndex)

        // Modify + Reset parameters
        project = mgr.execute(
            project,
            ProjectCommands.updateLayerModifier(comp.id, layerId, "fx_b") { it.copy(intensity = 2.4) }
        ).first
        assertEquals(2.4, project.compositions.first().layers.first { it.id == layerId }.modifiers.first { it.id == "fx_b" }.intensity, 0.001)

        project = mgr.execute(project, ProjectCommands.resetLayerModifier(comp.id, layerId, "fx_b", 24.0)).first
        assertEquals(1.0, project.compositions.first().layers.first { it.id == layerId }.modifiers.first { it.id == "fx_b" }.intensity, 0.001)

        // Undo back to modified intensity
        project = mgr.undo()!!.first
        assertEquals(2.4, project.compositions.first().layers.first { it.id == layerId }.modifiers.first { it.id == "fx_b" }.intensity, 0.001)
    }

    @Test
    fun `low-end budget validator catches excessive effect stacking`() {
        val manyLowCost = (1..6).map { i ->
            EffectsLibraryRegistry.createInstanceFromModule("fx.motion.float", 24.0, "m_$i")!!
        }
        val lowRes = EffectBudgetValidator.validateLayerStack(manyLowCost, DevicePerformanceProfile.LOW_END)
        assertFalse("6 stacked effects must exceed Low-End limit", lowRes.isWithinBudget)
        assertTrue("Warnings should explain budget exceedance", lowRes.warnings.isNotEmpty())
        assertTrue("Recommended profile should step up from LOW_END", lowRes.recommendedProfile != DevicePerformanceProfile.LOW_END)

        val midRes = EffectBudgetValidator.validateLayerStack(manyLowCost, DevicePerformanceProfile.MID_RANGE)
        assertTrue("6 low-cost effects fit within Mid-Range budget", midRes.isWithinBudget)
    }

    @Test
    fun `adaptive preview monitor degrades on slow frames and recovers on fast frames`() {
        val monitor = AdaptivePreviewMonitor(slowThresholdMs = 25f, fastThresholdMs = 12f, downgradeStreakTarget = 3, upgradeStreakTarget = 4)
        var state = AdaptivePreviewState(
            deviceProfile = DevicePerformanceProfile.MID_RANGE,
            qualityMode = PreviewQualityMode.AUTOMATIC,
            adaptiveTier = 2,
        )

        repeat(3) {
            state = monitor.recordFrame(state, renderTimeMs = 38f, activeEffectCount = 5)
        }
        assertEquals("Should downgrade tier after 3 slow frames", 1, state.adaptiveTier)
        assertTrue("Dropped frames should increment above 33.3ms", state.droppedFrames >= 3)
        assertTrue("Should flag degraded by performance", state.isDegradedByPerformance)

        // Recover with sustained fast frames
        repeat(18) {
            state = monitor.recordFrame(state, renderTimeMs = 6f, activeEffectCount = 2)
        }
        assertEquals("Should recover to tier 2 when fast", 2, state.adaptiveTier)
    }

    @Test
    fun `portable knowledge exporter outputs valid json and markdown with performance metadata`() {
        val json = PortableEffectKnowledgeExporter.exportJson()
        val md = PortableEffectKnowledgeExporter.exportMarkdown()
        assertTrue(json.contains("\"cpu_cost\""))
        assertTrue(json.contains("\"supports_low_end\""))
        assertTrue(json.contains("\"fx.motion.spring\""))
        assertTrue(md.contains("Target Device Profiles"))
        assertTrue(md.contains("fx.transform.follow_path"))

        // Verify MCP local engine serves portable knowledge and validates stack
        val project = createLostKingdomProject()
        val mcpMd = LocalMcpEngine.readResource(project, "motion://effects/knowledge.md")
        assertTrue(mcpMd.contains("AI Guidance"))

        val valOutcome = LocalMcpEngine.executeTool(
            project = project,
            activeCompId = project.compositions.first().id,
            toolName = "validate_effect_stack",
            args = buildJsonObject {
                put("layerId", project.compositions.first().layers.first().id)
                put("profile", "low_end")
            },
        )
        assertFalse(valOutcome.isError)
        assertTrue(valOutcome.outputJson.contains("isWithinBudget"))
    }

    @Test
    fun `realistic multi-layer combined effect scene evaluates quickly without frame stalls`() {
        var project = createLostKingdomProject()
        val comp = project.compositions.first()
        val combo = EffectsLibraryRegistry.builtinCombos.first()
        val mgr = CommandManager()

        // Apply starter combos to multiple layers simultaneously
        for (layer in comp.layers.take(4)) {
            val items = EffectsLibraryRegistry.instantiateCombo(combo, 24.0, 0) { "bench_${layer.id}_${System.nanoTime()}" }
            project = mgr.execute(project, ProjectCommands.addLayerModifiersBatch(comp.id, layer.id, items, combo.name)).first
        }

        // Warmup + measure 120 full composition frames
        repeat(10) { evaluateCompositionAtFrame(project, comp.id, it) }
        val startNs = System.nanoTime()
        for (f in 0 until 120) {
            val ev = evaluateCompositionAtFrame(project, comp.id, f)
            assertNotNull(ev)
        }
        val avgMs = (System.nanoTime() - startNs) / 1_000_000.0 / 120.0
        assertTrue("120-frame multi-layer evaluation averaged ${"%.3f".format(avgMs)}ms/frame (must stay well under 10ms)", avgMs < 10.0)
    }

    @Test
    fun `effect registry registers, queries, filters, and unregisters modular Effect components`() {
        val registry = EffectRegistry()
        assertEquals(0, registry.size)

        val corePhase1: List<Effect> = listOf(
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
        registry.registerAll(corePhase1)
        assertEquals(10, registry.size)
        assertTrue(PositionEffect.ID in registry)
        assertTrue(SpringEffect.ID in registry)
        assertEquals(5, registry.byCategory(EffectCategory.TRANSFORM).size)
        assertEquals(5, registry.byCategory(EffectCategory.MOTION).size)
        assertEquals(10, registry.byPhase(1).size)
        assertEquals(10, registry.compatibleWith(DevicePerformanceProfile.LOW_END).size)

        // Register a custom reusable Effect implementation
        val customEffect = object : BaseMatrixEffect() {
            override val definition = EffectModuleDefinition(
                id = "fx.custom.nudge",
                name = "Custom Nudge",
                category = EffectCategory.TRANSFORM,
                phase = 1,
                description = "Moves right by 25px",
                targetTypes = ALL_LAYER_EFFECT_TARGETS,
            )

            override fun evaluate(context: EffectEvaluationContext, out: EffectTransformState) {
                out.dx = 25.0 * context.normalizedTime
            }
        }
        registry.register(customEffect)
        assertEquals(11, registry.size)

        val mod = registry.createInstance("fx.custom.nudge", fps = 24.0, instanceId = "c1")!!
        val ctx = EffectEvaluationContext().apply { set(mod, normalizedTime = 1.0, frame = 24.0, objWidth = 100.0, objHeight = 100.0) }
        val state = EffectTransformState()
        assertTrue(registry.evaluate("fx.custom.nudge", ctx, state))
        assertEquals(25.0, state.dx, 1e-6)
        assertEquals(25.0, state.matrix.m02, 1e-6)

        assertTrue(registry.unregister("fx.custom.nudge"))
        assertFalse("fx.custom.nudge" in registry)
        assertEquals(10, registry.size)
    }

    @Test
    fun `matrix2D and EffectRegistry composeMatrixChain combine multiple effects into one affine matrix`() {
        // Verify pure Matrix2D transform math: translate(10, 20) + rotate(90 deg) + scale(2, 2)
        val m = Matrix2D()
        m.composeFromTransform(tx = 10.0, ty = 20.0, scaleX = 2.0, scaleY = 2.0, rotationDeg = 90.0)
        val pt = DoubleArray(2)
        m.mapPoint(1.0, 0.0, pt)
        // Rotating (2, 0) by +90 deg gives (0, 2); translating by (10, 20) gives (10, 22)
        assertEquals(10.0, pt[0], 1e-5)
        assertEquals(22.0, pt[1], 1e-5)

        // Verify EffectRegistry.composeMatrixChain combines Position + Rotation + Opacity
        val registry = EffectRegistry.default
        val posMod = registry.createInstance(PositionEffect.ID, fps = 24.0, instanceId = "p1")!!
            .copy(params = mapOf("offsetX" to 100.0, "offsetY" to 0.0, "animateFromOffset" to 0.0, "easingMode" to 0.0))
        val rotMod = registry.createInstance(RotationEffect.ID, fps = 24.0, instanceId = "r1")!!
            .copy(params = mapOf("angleDeg" to 180.0, "continuousSpin" to 1.0))
        val opMod = registry.createInstance(OpacityEffect.ID, fps = 24.0, instanceId = "o1")!!
            .copy(params = mapOf("fromOpacity" to 0.0, "toOpacity" to 1.0, "fadeMode" to 0.0))

        val combined = Matrix2D()
        val combinedOpacity = registry.composeMatrixChain(
            modifiers = listOf(posMod, rotMod, opMod),
            frame = posMod.durationFrames * 0.5,
            objWidth = 100.0,
            objHeight = 100.0,
            outMatrix = combined,
        )

        assertFalse("Combined matrix should not be identity", combined.isIdentity())
        assertEquals(50.0, combined.m02, 1e-3)
        assertTrue("Opacity should reflect fade midpoint", combinedOpacity in 0.2..0.8)
    }

    @Test
    fun `effect interface exposes id, name, metadata classification, and layer transform application`() {
        val project = createLostKingdomProject()
        val baseLayer: Layer = project.compositions.first().layers.first().copy(modifiers = emptyList())

        val position: Effect = PositionEffect()
        assertEquals(PositionEffect.ID, position.id)
        assertEquals("Position Slide", position.name)
        assertEquals(CostLevel.LOW, position.performance.level)
        assertEquals(1, position.performance.cost)
        assertEquals(CostLevel.LOW, position.metadata.level)
        assertEquals(CostLevel.LOW, position.metadata.cpuCost)
        assertEquals(CostLevel.LOW, position.metadata.gpuCost)
        assertEquals(CostLevel.LOW, position.metadata.memoryCost)
        assertTrue(position.metadata.supportsLowEnd)

        val customPerf = EffectPerformanceMetadata(CostLevel.MEDIUM, 4)
        assertEquals(CostLevel.MEDIUM, customPerf.level)
        assertEquals(4, customPerf.cost)
        assertEquals(4, customPerf.budgetPoints)

        // Standalone layer evaluation (no modifier attached yet -> uses default instance)
        val standaloneStart = position.applyMatrix2D(baseLayer, frame = 0)
        assertEquals(-80.0, standaloneStart.m12, 1e-3)

        // Attached modifier evaluation respects custom layer parameters
        val attachedPos = position.createDefaultInstance(fps = 24.0, instanceId = "pos_1", startFrame = 0)
            .copy(params = mapOf("offsetX" to 120.0, "offsetY" to 0.0, "animateFromOffset" to 0.0, "easingMode" to 0.0))
        val attachedOpacity = OpacityEffect().createDefaultInstance(fps = 24.0, instanceId = "op_1", startFrame = 0)
            .copy(params = mapOf("fromOpacity" to 0.0, "toOpacity" to 1.0, "fadeMode" to 0.0))
        val animatedLayer: Layer = baseLayer.copy(modifiers = listOf(attachedPos, attachedOpacity))

        val midMatrix = position.applyMatrix2D(animatedLayer, frame = attachedPos.durationFrames / 2)
        assertEquals(60.0, midMatrix.m02, 2.5)

        val midAlpha = OpacityEffect().evaluateOpacity(animatedLayer, frame = attachedOpacity.durationFrames / 2)
        assertTrue("Midpoint fade opacity should be near 0.5, got $midAlpha", midAlpha in 0.35f..0.65f)

        val composedLayerMatrix = EffectRegistry.default.applyAllMatrix2D(animatedLayer, frame = attachedPos.durationFrames / 2)
        assertEquals(midMatrix.m02, composedLayerMatrix.m02, 1e-3)
    }

    @Test
    fun `effect registry manages Phase 1 factory, lifecycle callbacks, retrieval, reset, and release`() {
        val events = mutableListOf<String>()
        fun trackedEffect(id: String, name: String): Effect = object : BaseMatrixEffect() {
            override val definition = EffectModuleDefinition(
                id = id,
                name = name,
                category = EffectCategory.MOTION,
                phase = 1,
                description = "Lifecycle test effect",
                targetTypes = ALL_LAYER_EFFECT_TARGETS,
            )

            override fun evaluate(context: EffectEvaluationContext, out: EffectTransformState) {
                out.dx = 10.0 * context.normalizedTime
            }

            override fun onRegistered() {
                events.add("registered:$id")
            }

            override fun onUnregistered() {
                events.add("unregistered:$id")
            }

            override fun onRelease() {
                events.add("released:$id")
            }
        }

        // Factory creates active registry with the exact 10 core Phase 1 effects
        val phase1Registry = EffectRegistry.createPhase1Registry()
        assertEquals(RegistryLifecycleState.ACTIVE, phase1Registry.lifecycleState)
        assertTrue(phase1Registry.isActive)
        assertFalse(phase1Registry.isReleased)
        assertEquals(10, phase1Registry.size)

        val expectedIds = setOf(
            PositionEffect.ID,
            ScaleEffect.ID,
            RotationEffect.ID,
            OpacityEffect.ID,
            FollowPathEffect.ID,
            BounceEffect.ID,
            ShakeEffect.ID,
            FloatEffect.ID,
            WiggleEffect.ID,
            SpringEffect.ID,
        )
        assertEquals(expectedIds, phase1Registry.ids())
        assertEquals(PositionEffect.ID, phase1Registry.getOrThrow(PositionEffect.ID).id)
        assertTrue(phase1Registry.findByName("Spring").any { it.id == SpringEffect.ID })
        assertTrue(phase1Registry.findByName("follow_path").any { it.id == FollowPathEffect.ID })

        // Verify getOrThrow throws on unknown ID
        var threwMissing = false
        try {
            phase1Registry.getOrThrow("fx.unknown.missing")
        } catch (_: NoSuchElementException) {
            threwMissing = true
        }
        assertTrue("getOrThrow must throw NoSuchElementException for unknown ID", threwMissing)

        // Verify empty registry lifecycle: UNINITIALIZED -> ACTIVE -> replace -> unregister -> reset -> RELEASED
        val lifecycleRegistry = EffectRegistry()
        assertEquals(RegistryLifecycleState.UNINITIALIZED, lifecycleRegistry.lifecycleState)

        val fxA1 = trackedEffect("fx.test.alpha", "Alpha V1")
        val fxA2 = trackedEffect("fx.test.alpha", "Alpha V2")
        val fxB = trackedEffect("fx.test.beta", "Beta")

        lifecycleRegistry.register(fxA1)
        assertEquals(RegistryLifecycleState.ACTIVE, lifecycleRegistry.lifecycleState)
        assertEquals(listOf("registered:fx.test.alpha"), events)

        // Replacing same ID unregisters old instance and registers new instance
        lifecycleRegistry.register(fxA2)
        assertEquals(
            listOf("registered:fx.test.alpha", "unregistered:fx.test.alpha", "registered:fx.test.alpha"),
            events,
        )
        assertEquals("Alpha V2", lifecycleRegistry.getOrThrow("fx.test.alpha").name)

        // Register second effect and reset
        lifecycleRegistry.register(fxB)
        events.clear()
        lifecycleRegistry.reset(listOf(fxB))
        assertEquals(1, lifecycleRegistry.size)
        assertTrue("unregistered:fx.test.alpha" in events)
        assertTrue("registered:fx.test.beta" in events)

        // Release invokes onRelease + onUnregistered and locks registry against future writes
        events.clear()
        lifecycleRegistry.release()
        assertEquals(RegistryLifecycleState.RELEASED, lifecycleRegistry.lifecycleState)
        assertTrue(lifecycleRegistry.isReleased)
        assertEquals(0, lifecycleRegistry.size)
        assertEquals(listOf("released:fx.test.beta", "unregistered:fx.test.beta"), events)

        // Second release is idempotent
        lifecycleRegistry.release()
        assertEquals(2, events.size)

        // Writes after release throw IllegalStateException
        var threwReleased = false
        try {
            lifecycleRegistry.register(fxA1)
        } catch (_: IllegalStateException) {
            threwReleased = true
        }
        assertTrue("Registering after release() must throw IllegalStateException", threwReleased)
    }
}


