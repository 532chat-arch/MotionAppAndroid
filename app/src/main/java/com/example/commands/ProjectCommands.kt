package com.example.commands

import com.example.engine.EnvironmentPatch
import com.example.engine.applyPatch
import com.example.engine.evaluateCompositionAtFrame
import com.example.engine.wouldCreateCompositionCycle
import com.example.engine.wouldCreateLayerParentCycle
import com.example.schema.AssetRecord
import com.example.schema.AudioClipDocument
import com.example.schema.AudioTrackDocument
import com.example.schema.CameraAnimatableProp
import com.example.schema.CameraDocument
import com.example.schema.CameraKeyframes
import com.example.schema.CharacterDefinition
import com.example.schema.CompositionRecord
import com.example.schema.CompositionSettings
import com.example.schema.ExportSettings
import com.example.schema.InterpolationType
import com.example.schema.Keyframe
import com.example.schema.LayerAnimatableProp
import com.example.schema.LayerDocument
import com.example.schema.LayerKeyframes
import com.example.schema.LayerRole
import com.example.schema.MotionModifierInstance
import com.example.schema.MotionProject
import com.example.schema.ProjectTimelineClip
import com.example.schema.SceneObjectDocument
import com.example.schema.Transform2D
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max
import kotlin.math.min

private val idCounter = AtomicLong(1000)

fun uid(prefix: String = "id"): String =
    "${prefix}_${System.currentTimeMillis().toString(36)}_${idCounter.incrementAndGet().toString(36)}"

fun nowIso(): String = Instant.now().toString()

private fun MotionProject.touch(): MotionProject = copy(updatedAt = nowIso())

private fun MotionProject.updateComp(
    compId: String,
    transform: (CompositionRecord) -> CompositionRecord,
): MotionProject = touch().copy(
    compositions = compositions.map { if (it.id == compId) transform(it) else it }
)

private fun MotionProject.updateLayerInComp(
    compId: String,
    layerId: String,
    transform: (LayerDocument) -> LayerDocument,
): MotionProject = updateComp(compId) { comp ->
    comp.copy(layers = comp.layers.map { if (it.id == layerId) transform(it) else it })
}

private fun upsertKeyframe(
    list: List<Keyframe>,
    frame: Int,
    value: Double,
    interpolation: InterpolationType = InterpolationType.EASE_IN_OUT,
): List<Keyframe> {
    val idx = list.indexOfFirst { it.frame == frame }
    return if (idx >= 0) {
        list.mapIndexed { i, k ->
            if (i == idx) k.copy(value = value, interpolation = interpolation) else k
        }
    } else {
        (list + Keyframe(uid("kf"), frame, value, interpolation)).sortedBy { it.frame }
    }
}

private fun removeKeyframeAt(list: List<Keyframe>, frame: Int): List<Keyframe> =
    list.filterNot { it.frame == frame }

private fun LayerKeyframes.getChannel(prop: LayerAnimatableProp): List<Keyframe> = when (prop) {
    LayerAnimatableProp.X -> x
    LayerAnimatableProp.Y -> y
    LayerAnimatableProp.ROTATION -> rotation
    LayerAnimatableProp.SCALE_X -> scaleX
    LayerAnimatableProp.SCALE_Y -> scaleY
    LayerAnimatableProp.OPACITY -> opacity
}

private fun LayerKeyframes.withChannel(prop: LayerAnimatableProp, list: List<Keyframe>): LayerKeyframes = when (prop) {
    LayerAnimatableProp.X -> copy(x = list)
    LayerAnimatableProp.Y -> copy(y = list)
    LayerAnimatableProp.ROTATION -> copy(rotation = list)
    LayerAnimatableProp.SCALE_X -> copy(scaleX = list)
    LayerAnimatableProp.SCALE_Y -> copy(scaleY = list)
    LayerAnimatableProp.OPACITY -> copy(opacity = list)
}

object ProjectCommands {

    fun renameProject(name: String) = ProjectCommand("Rename Project") { p ->
        p.touch().copy(name = name.trim().ifEmpty { p.name })
    }

    fun addComposition(comp: CompositionRecord) = ProjectCommand("Create Composition") { p ->
        p.touch().copy(compositions = p.compositions + comp)
    }

    fun updateComposition(compId: String, name: String? = null, settings: CompositionSettings? = null) =
        ProjectCommand("Update Composition") { p ->
            p.updateComp(compId) { c ->
                c.copy(
                    name = name?.trim()?.ifEmpty { c.name } ?: c.name,
                    settings = settings ?: c.settings,
                )
            }
        }

    fun duplicateComposition(compId: String, newId: String) = ProjectCommand("Duplicate Composition") { p ->
        val src = p.compositions.find { it.id == compId } ?: return@ProjectCommand p
        val clone = src.copy(
            id = newId,
            name = "${src.name} Copy",
            layers = src.layers.map { l ->
                l.copy(
                    id = uid("layer"),
                    objects = l.objects.map { obj -> cloneObjectId(obj) },
                )
            },
        )
        p.touch().copy(compositions = p.compositions + clone)
    }

    fun deleteComposition(compId: String) = ProjectCommand(
        label = "Delete Composition",
        validate = { p -> if (p.compositions.size <= 1) "A project must have at least one composition." else null },
        execute = { p ->
            p.touch().copy(
                compositions = p.compositions.filterNot { it.id == compId }.map { c ->
                    c.copy(layers = c.layers.filterNot { layer ->
                        layer.objects.any { it is SceneObjectDocument.NestedCompositionObject && it.compositionId == compId }
                    })
                },
                projectTimeline = p.projectTimeline.filterNot { it.compositionId == compId },
            )
        },
    )

    fun addLayer(compId: String, layer: LayerDocument) = ProjectCommand("Add Layer ${layer.name}") { p ->
        p.updateComp(compId) { c -> c.copy(layers = c.layers + layer) }
    }

    fun deleteLayer(compId: String, layerId: String) = ProjectCommand("Delete Layer") { p ->
        p.updateComp(compId) { c ->
            c.copy(
                layers = c.layers.filterNot { it.id == layerId }.map { l ->
                    if (l.parentId == layerId) l.copy(parentId = null) else l
                },
                camera = if (c.camera.followLayerId == layerId) c.camera.copy(followLayerId = null) else c.camera,
            )
        }
    }

    fun updateLayer(compId: String, layerId: String, updater: (LayerDocument) -> LayerDocument) =
        ProjectCommand("Update Layer") { p ->
            p.updateLayerInComp(compId, layerId, updater)
        }

    fun reorderLayer(compId: String, layerId: String, direction: Int) =
        ProjectCommand("Reorder Layer") { p ->
            p.updateComp(compId) { c ->
                val list = c.layers.toMutableList()
                val idx = list.indexOfFirst { it.id == layerId }
                if (idx < 0) return@updateComp c
                val targetIdx = (idx + direction).coerceIn(0, list.lastIndex)
                if (targetIdx == idx) return@updateComp c
                val item = list.removeAt(idx)
                list.add(targetIdx, item)
                c.copy(layers = list)
            }
        }

    fun setLayerParent(compId: String, layerId: String, parentId: String?) = ProjectCommand(
        label = "Set Layer Parent",
        validate = { p ->
            val comp = p.compositions.find { it.id == compId } ?: return@ProjectCommand "Composition not found"
            if (wouldCreateLayerParentCycle(comp.layers, layerId, parentId)) {
                "Cannot set parent: cyclic layer hierarchy detected."
            } else null
        },
        execute = { p ->
            p.updateLayerInComp(compId, layerId) { it.copy(parentId = parentId) }
        },
    )

    fun nestComposition(parentCompId: String, childCompId: String) = ProjectCommand(
        label = "Nest Composition",
        validate = { p ->
            if (wouldCreateCompositionCycle(p, parentCompId, childCompId)) {
                "Cannot nest composition: circular composition reference detected."
            } else null
        },
        execute = { p ->
            val parent = p.compositions.find { it.id == parentCompId } ?: return@ProjectCommand p
            val child = p.compositions.find { it.id == childCompId } ?: return@ProjectCommand p
            val nestedObj = SceneObjectDocument.NestedCompositionObject(
                id = uid("obj"),
                name = child.name,
                compositionId = child.id,
                width = child.settings.width.toDouble(),
                height = child.settings.height.toDouble(),
            )
            val layer = LayerDocument(
                id = uid("layer"),
                name = "Nested: ${child.name}",
                role = LayerRole.MIDGROUND,
                outFrame = parent.settings.durationFrames,
                transform = Transform2D(
                    x = parent.settings.width / 2.0,
                    y = parent.settings.height / 2.0,
                ),
                objects = listOf(nestedObj),
            )
            p.updateComp(parentCompId) { c -> c.copy(layers = c.layers + layer) }
        },
    )

    fun preComposeLayers(
        parentCompId: String,
        layerIds: List<String>,
        newCompName: String,
    ) = ProjectCommand(
        label = "Pre-Compose Layers",
        validate = { p -> if (layerIds.isEmpty()) "Select at least one layer to pre-compose." else null },
        execute = { p ->
            val parent = p.compositions.find { it.id == parentCompId } ?: return@ProjectCommand p
            val selected = parent.layers.filter { it.id in layerIds }
            if (selected.isEmpty()) return@ProjectCommand p
            val newCompId = uid("comp")
            val newComp = CompositionRecord(
                id = newCompId,
                name = newCompName.trim().ifEmpty { "Pre-Comp" },
                settings = parent.settings,
                environment = parent.environment.copy(
                    skyTopColor = "transparent",
                    skyBottomColor = "transparent",
                    hazeIntensity = 0.0,
                ),
                layers = selected.map { l ->
                    if (l.parentId !in layerIds) l.copy(parentId = null) else l
                },
            )
            val nestedLayer = LayerDocument(
                id = uid("layer"),
                name = newComp.name,
                role = LayerRole.MIDGROUND,
                outFrame = parent.settings.durationFrames,
                transform = Transform2D(
                    x = parent.settings.width / 2.0,
                    y = parent.settings.height / 2.0,
                ),
                objects = listOf(
                    SceneObjectDocument.NestedCompositionObject(
                        id = uid("obj"),
                        name = newComp.name,
                        compositionId = newCompId,
                        width = parent.settings.width.toDouble(),
                        height = parent.settings.height.toDouble(),
                    )
                ),
            )
            p.touch().copy(
                compositions = p.compositions.map { c ->
                    if (c.id == parentCompId) {
                        c.copy(layers = c.layers.filterNot { it.id in layerIds } + nestedLayer)
                    } else c
                } + newComp
            )
        },
    )

    fun updateObject(
        compId: String,
        layerId: String,
        objectId: String,
        updater: (SceneObjectDocument) -> SceneObjectDocument,
    ) = ProjectCommand("Update Layer Object") { p ->
        p.updateLayerInComp(compId, layerId) { layer ->
            layer.copy(objects = layer.objects.map { if (it.id == objectId) updater(it) else it })
        }
    }

    fun updateLayerTransformValue(
        compId: String,
        layerId: String,
        frame: Int,
        x: Double? = null,
        y: Double? = null,
        rotationDeg: Double? = null,
        scaleX: Double? = null,
        scaleY: Double? = null,
        opacity: Double? = null,
    ) = ProjectCommand("Transform Layer") { p ->
        p.updateLayerInComp(compId, layerId) { l ->
            var t = l.transform
            var op = l.opacity
            var kf = l.keyframes
            if (x != null) {
                t = t.copy(x = x)
                if (kf.x.isNotEmpty()) kf = kf.copy(x = upsertKeyframe(kf.x, frame, x))
            }
            if (y != null) {
                t = t.copy(y = y)
                if (kf.y.isNotEmpty()) kf = kf.copy(y = upsertKeyframe(kf.y, frame, y))
            }
            if (rotationDeg != null) {
                t = t.copy(rotationDeg = rotationDeg)
                if (kf.rotation.isNotEmpty()) kf = kf.copy(rotation = upsertKeyframe(kf.rotation, frame, rotationDeg))
            }
            if (scaleX != null) {
                t = t.copy(scaleX = scaleX)
                if (kf.scaleX.isNotEmpty()) kf = kf.copy(scaleX = upsertKeyframe(kf.scaleX, frame, scaleX))
            }
            if (scaleY != null) {
                t = t.copy(scaleY = scaleY)
                if (kf.scaleY.isNotEmpty()) kf = kf.copy(scaleY = upsertKeyframe(kf.scaleY, frame, scaleY))
            }
            if (opacity != null) {
                val c = opacity.coerceIn(0.0, 1.0)
                op = c
                if (kf.opacity.isNotEmpty()) kf = kf.copy(opacity = upsertKeyframe(kf.opacity, frame, c))
            }
            l.copy(transform = t, opacity = op, keyframes = kf)
        }
    }

    fun setLayerKeyframe(
        compId: String,
        layerId: String,
        prop: LayerAnimatableProp,
        frame: Int,
        value: Double,
        interpolation: InterpolationType = InterpolationType.EASE_IN_OUT,
    ) = ProjectCommand("Set Keyframe (${prop.key})") { p ->
        p.updateLayerInComp(compId, layerId) { l ->
            val updatedList = upsertKeyframe(l.keyframes.getChannel(prop), frame, value, interpolation)
            l.copy(keyframes = l.keyframes.withChannel(prop, updatedList))
        }
    }

    fun updateLayerKeyframe(
        compId: String,
        layerId: String,
        prop: LayerAnimatableProp,
        keyframeId: String,
        newFrame: Int? = null,
        newValue: Double? = null,
        newInterp: InterpolationType? = null,
    ) = ProjectCommand("Edit Keyframe") { p ->
        p.updateLayerInComp(compId, layerId) { l ->
            val list = l.keyframes.getChannel(prop).map { kf ->
                if (kf.id == keyframeId) {
                    kf.copy(
                        frame = newFrame ?: kf.frame,
                        value = newValue ?: kf.value,
                        interpolation = newInterp ?: kf.interpolation,
                    )
                } else kf
            }.sortedBy { it.frame }
            l.copy(keyframes = l.keyframes.withChannel(prop, list))
        }
    }

    fun setLayerPositionKeyframe(
        compId: String,
        layerId: String,
        frame: Int,
        x: Double,
        y: Double,
        interpolation: InterpolationType = InterpolationType.EASE_IN_OUT,
    ) = ProjectCommand("Move Path Point (F$frame)") { p ->
        p.updateLayerInComp(compId, layerId) { l ->
            val nextX = upsertKeyframe(l.keyframes.x, frame, x, l.keyframes.x.find { it.frame == frame }?.interpolation ?: interpolation)
            val nextY = upsertKeyframe(l.keyframes.y, frame, y, l.keyframes.y.find { it.frame == frame }?.interpolation ?: interpolation)
            l.copy(
                transform = l.transform.copy(x = x, y = y),
                keyframes = l.keyframes.copy(x = nextX, y = nextY),
            )
        }
    }

    fun setLayerTransformKeyframesAtFrame(
        compId: String,
        layerId: String,
        frame: Int,
        x: Double,
        y: Double,
        rotationDeg: Double,
    ) = ProjectCommand("Add Transform Keyframes (F$frame)") { p ->
        p.updateLayerInComp(compId, layerId) { l ->
            l.copy(
                keyframes = l.keyframes.copy(
                    x = upsertKeyframe(l.keyframes.x, frame, x),
                    y = upsertKeyframe(l.keyframes.y, frame, y),
                    rotation = upsertKeyframe(l.keyframes.rotation, frame, rotationDeg),
                )
            )
        }
    }

    fun removeLayerKeyframe(compId: String, layerId: String, prop: LayerAnimatableProp, frame: Int) =
        ProjectCommand("Remove Keyframe") { p ->
            p.updateLayerInComp(compId, layerId) { l ->
                l.copy(keyframes = l.keyframes.withChannel(prop, removeKeyframeAt(l.keyframes.getChannel(prop), frame)))
            }
        }

    fun removeLayerKeyframesAtFrame(
        compId: String,
        layerId: String,
        frame: Int,
        props: Set<LayerAnimatableProp> = LayerAnimatableProp.entries.toSet(),
    ) = ProjectCommand("Delete Keyframe (F$frame)") { p ->
        p.updateLayerInComp(compId, layerId) { l ->
            var kf = l.keyframes
            for (prop in props) {
                kf = kf.withChannel(prop, removeKeyframeAt(kf.getChannel(prop), frame))
            }
            l.copy(keyframes = kf)
        }
    }

    fun duplicateLayerKeyframes(
        compId: String,
        layerId: String,
        sourceFrame: Int,
        targetFrame: Int,
        props: Set<LayerAnimatableProp> = LayerAnimatableProp.entries.toSet(),
    ) = ProjectCommand("Duplicate Keyframe (F$sourceFrame → F$targetFrame)") { p ->
        p.updateLayerInComp(compId, layerId) { l ->
            var kf = l.keyframes
            for (prop in props) {
                val src = kf.getChannel(prop).find { it.frame == sourceFrame }
                if (src != null) {
                    kf = kf.withChannel(prop, upsertKeyframe(kf.getChannel(prop), targetFrame, src.value, src.interpolation))
                }
            }
            l.copy(keyframes = kf)
        }
    }

    fun updateLayerKeyframesAtFrame(
        compId: String,
        layerId: String,
        sourceFrame: Int,
        props: Set<LayerAnimatableProp>,
        newFrame: Int? = null,
        newValues: Map<LayerAnimatableProp, Double> = emptyMap(),
        newInterpolation: InterpolationType? = null,
    ) = ProjectCommand("Edit Keyframe (F${newFrame ?: sourceFrame})") { p ->
        p.updateLayerInComp(compId, layerId) { l ->
            var kf = l.keyframes
            for (prop in props) {
                val channel = kf.getChannel(prop)
                val existing = channel.find { it.frame == sourceFrame }
                if (existing != null) {
                    val destFrame = newFrame ?: existing.frame
                    val destVal = newValues[prop] ?: existing.value
                    val destInterp = newInterpolation ?: existing.interpolation
                    val cleaned = channel.filterNot { it.id == existing.id || (destFrame != sourceFrame && it.frame == destFrame) }
                    val updated = (cleaned + existing.copy(frame = destFrame, value = destVal, interpolation = destInterp))
                        .sortedBy { it.frame }
                    kf = kf.withChannel(prop, updated)
                }
            }
            l.copy(keyframes = kf)
        }
    }

    fun clearLayerKeyframeChannel(
        compId: String,
        layerId: String,
        prop: LayerAnimatableProp,
        bakeValue: Double? = null,
    ) = ProjectCommand("Clear ${prop.key} Animation") { p ->
        p.updateLayerInComp(compId, layerId) { l ->
            var t = l.transform
            var op = l.opacity
            if (bakeValue != null) {
                when (prop) {
                    LayerAnimatableProp.X -> t = t.copy(x = bakeValue)
                    LayerAnimatableProp.Y -> t = t.copy(y = bakeValue)
                    LayerAnimatableProp.ROTATION -> t = t.copy(rotationDeg = bakeValue)
                    LayerAnimatableProp.SCALE_X -> t = t.copy(scaleX = bakeValue)
                    LayerAnimatableProp.SCALE_Y -> t = t.copy(scaleY = bakeValue)
                    LayerAnimatableProp.OPACITY -> op = bakeValue.coerceIn(0.0, 1.0)
                }
            }
            l.copy(
                transform = t,
                opacity = op,
                keyframes = l.keyframes.withChannel(prop, emptyList()),
            )
        }
    }

    fun clearAllLayerAnimation(
        compId: String,
        layerId: String,
        bakeTransform: Transform2D? = null,
        bakeOpacity: Double? = null,
    ) = ProjectCommand("Clear All Layer Animation") { p ->
        p.updateLayerInComp(compId, layerId) { l ->
            l.copy(
                transform = bakeTransform ?: l.transform,
                opacity = bakeOpacity?.coerceIn(0.0, 1.0) ?: l.opacity,
                keyframes = LayerKeyframes(),
                modifiers = emptyList(),
            )
        }
    }

    fun pasteLayerKeyframes(
        compId: String,
        layerId: String,
        targetFrame: Int,
        values: Map<LayerAnimatableProp, Pair<Double, InterpolationType>>,
    ) = ProjectCommand("Paste Keyframes") { p ->
        p.updateLayerInComp(compId, layerId) { l ->
            var kf = l.keyframes
            for ((prop, pair) in values) {
                kf = kf.withChannel(prop, upsertKeyframe(kf.getChannel(prop), targetFrame, pair.first, pair.second))
            }
            l.copy(keyframes = kf)
        }
    }

    fun addLayerModifier(compId: String, layerId: String, modifier: MotionModifierInstance) =
        ProjectCommand("Apply Preset ${modifier.label}") { p ->
            p.updateLayerInComp(compId, layerId) { l -> l.copy(modifiers = l.modifiers + modifier) }
        }

    fun updateLayerModifier(
        compId: String,
        layerId: String,
        modifierId: String,
        updater: (MotionModifierInstance) -> MotionModifierInstance,
    ) = ProjectCommand("Update Modifier") { p ->
        p.updateLayerInComp(compId, layerId) { l ->
            l.copy(modifiers = l.modifiers.map { if (it.id == modifierId) updater(it) else it })
        }
    }

    fun removeLayerModifier(compId: String, layerId: String, modifierId: String) =
        ProjectCommand("Remove Effect") { p ->
            p.updateLayerInComp(compId, layerId) { l ->
                l.copy(modifiers = l.modifiers.filterNot { it.id == modifierId })
            }
        }

    fun addLayerModifiersBatch(
        compId: String,
        layerId: String,
        modifiers: List<MotionModifierInstance>,
        comboName: String,
    ) = ProjectCommand("Apply Combo $comboName") { p ->
        if (modifiers.isEmpty()) return@ProjectCommand p
        p.updateLayerInComp(compId, layerId) { l -> l.copy(modifiers = l.modifiers + modifiers) }
    }

    fun duplicateLayerModifier(
        compId: String,
        layerId: String,
        modifierId: String,
        newId: String = uid("mod"),
    ) = ProjectCommand("Duplicate Effect") { p ->
        p.updateLayerInComp(compId, layerId) { l ->
            val idx = l.modifiers.indexOfFirst { it.id == modifierId }
            if (idx < 0) return@updateLayerInComp l
            val src = l.modifiers[idx]
            val copy = src.copy(
                id = newId,
                label = if (src.label.endsWith(" Copy")) src.label else "${src.label} Copy",
            )
            val next = l.modifiers.toMutableList()
            next.add(idx + 1, copy)
            l.copy(modifiers = next)
        }
    }

    fun reorderLayerModifier(
        compId: String,
        layerId: String,
        modifierId: String,
        moveUp: Boolean,
    ) = ProjectCommand(if (moveUp) "Move Effect Up" else "Move Effect Down") { p ->
        p.updateLayerInComp(compId, layerId) { l ->
            val idx = l.modifiers.indexOfFirst { it.id == modifierId }
            if (idx < 0) return@updateLayerInComp l
            val targetIdx = if (moveUp) idx - 1 else idx + 1
            if (targetIdx !in l.modifiers.indices) return@updateLayerInComp l
            val next = l.modifiers.toMutableList()
            val item = next.removeAt(idx)
            next.add(targetIdx, item)
            l.copy(modifiers = next)
        }
    }

    fun resetLayerModifier(
        compId: String,
        layerId: String,
        modifierId: String,
        fps: Double,
    ) = ProjectCommand("Reset Effect Parameters") { p ->
        p.updateLayerInComp(compId, layerId) { l ->
            l.copy(
                modifiers = l.modifiers.map { m ->
                    if (m.id == modifierId) com.example.effects.EffectsLibraryRegistry.resetInstanceParameters(m, fps)
                    else m
                }
            )
        }
    }

    fun bakeLayerModifiersToKeyframes(
        compId: String,
        layerId: String,
        stepFrames: Int,
        startFrame: Int,
        endFrame: Int,
        removeAfterBake: Boolean,
    ) = ProjectCommand("Bake Layer Modifiers") { p ->
        val comp = p.compositions.find { it.id == compId } ?: return@ProjectCommand p
        val layer = comp.layers.find { it.id == layerId } ?: return@ProjectCommand p
        val step = max(1, stepFrames)
        val sF = max(0, min(startFrame, endFrame))
        val eF = min(comp.settings.durationFrames - 1, max(sF, endFrame))

        val xList = mutableListOf<Keyframe>()
        val yList = mutableListOf<Keyframe>()
        val rotList = mutableListOf<Keyframe>()
        val sxList = mutableListOf<Keyframe>()
        val syList = mutableListOf<Keyframe>()
        val opList = mutableListOf<Keyframe>()

        val sampleFrames = mutableListOf<Int>()
        var f = sF
        while (f <= eF) {
            sampleFrames += f
            f += step
        }
        if (sampleFrames.lastOrNull() != eF) sampleFrames += eF

        for (sf in sampleFrames) {
            val eval = evaluateCompositionAtFrame(p, compId, sf)
            val lState = eval?.layers?.find { it.layer.id == layerId } ?: continue
            val lt = lState.localTransform
            xList += Keyframe(uid("kf"), sf, lt.x, InterpolationType.LINEAR)
            yList += Keyframe(uid("kf"), sf, lt.y, InterpolationType.LINEAR)
            rotList += Keyframe(uid("kf"), sf, lt.rotationDeg, InterpolationType.LINEAR)
            sxList += Keyframe(uid("kf"), sf, lt.scaleX, InterpolationType.LINEAR)
            syList += Keyframe(uid("kf"), sf, lt.scaleY, InterpolationType.LINEAR)
            opList += Keyframe(uid("kf"), sf, lt.opacity, InterpolationType.LINEAR)
        }

        p.updateLayerInComp(compId, layerId) { l ->
            l.copy(
                keyframes = LayerKeyframes(xList, yList, rotList, sxList, syList, opList),
                modifiers = if (removeAfterBake) emptyList() else l.modifiers.map { it.copy(enabled = false) },
            )
        }
    }

    fun updateCamera(compId: String, updater: (CameraDocument) -> CameraDocument) =
        ProjectCommand("Update Camera") { p ->
            p.updateComp(compId) { c -> c.copy(camera = updater(c.camera)) }
        }

    fun setCameraKeyframe(
        compId: String,
        prop: CameraAnimatableProp,
        frame: Int,
        value: Double,
        interpolation: InterpolationType = InterpolationType.EASE_IN_OUT,
    ) = ProjectCommand("Set Camera Keyframe") { p ->
        p.updateComp(compId) { c ->
            val cam = c.camera
            val kf = cam.keyframes
            val nextKf = when (prop) {
                CameraAnimatableProp.X -> kf.copy(x = upsertKeyframe(kf.x, frame, value, interpolation))
                CameraAnimatableProp.Y -> kf.copy(y = upsertKeyframe(kf.y, frame, value, interpolation))
                CameraAnimatableProp.ZOOM -> kf.copy(zoom = upsertKeyframe(kf.zoom, frame, value, interpolation))
            }
            val nextCam = when (prop) {
                CameraAnimatableProp.X -> cam.copy(x = value, keyframes = nextKf)
                CameraAnimatableProp.Y -> cam.copy(y = value, keyframes = nextKf)
                CameraAnimatableProp.ZOOM -> cam.copy(zoom = value, keyframes = nextKf)
            }
            c.copy(camera = nextCam)
        }
    }

    fun updateCameraKeyframe(
        compId: String,
        prop: CameraAnimatableProp,
        keyframeId: String,
        newFrame: Int? = null,
        newValue: Double? = null,
        newInterp: InterpolationType? = null,
    ) = ProjectCommand("Edit Camera Keyframe") { p ->
        p.updateComp(compId) { c ->
            val kf = c.camera.keyframes
            fun mapList(list: List<Keyframe>) = list.map { k ->
                if (k.id == keyframeId) k.copy(
                    frame = newFrame ?: k.frame,
                    value = newValue ?: k.value,
                    interpolation = newInterp ?: k.interpolation,
                ) else k
            }.sortedBy { it.frame }
            val nextKf = when (prop) {
                CameraAnimatableProp.X -> kf.copy(x = mapList(kf.x))
                CameraAnimatableProp.Y -> kf.copy(y = mapList(kf.y))
                CameraAnimatableProp.ZOOM -> kf.copy(zoom = mapList(kf.zoom))
            }
            c.copy(camera = c.camera.copy(keyframes = nextKf))
        }
    }

    fun removeCameraKeyframe(compId: String, prop: CameraAnimatableProp, frame: Int) =
        ProjectCommand("Remove Camera Keyframe") { p ->
            p.updateComp(compId) { c ->
                val kf = c.camera.keyframes
                val nextKf = when (prop) {
                    CameraAnimatableProp.X -> kf.copy(x = removeKeyframeAt(kf.x, frame))
                    CameraAnimatableProp.Y -> kf.copy(y = removeKeyframeAt(kf.y, frame))
                    CameraAnimatableProp.ZOOM -> kf.copy(zoom = removeKeyframeAt(kf.zoom, frame))
                }
                c.copy(camera = c.camera.copy(keyframes = nextKf))
            }
        }

    fun setCameraTransformKeyframesAtFrame(
        compId: String,
        frame: Int,
        x: Double,
        y: Double,
        zoom: Double,
    ) = ProjectCommand("Add Camera Keyframes (F$frame)") { p ->
        p.updateComp(compId) { c ->
            val kf = c.camera.keyframes
            c.copy(
                camera = c.camera.copy(
                    keyframes = kf.copy(
                        x = upsertKeyframe(kf.x, frame, x),
                        y = upsertKeyframe(kf.y, frame, y),
                        zoom = upsertKeyframe(kf.zoom, frame, zoom),
                    )
                )
            )
        }
    }

    fun removeCameraKeyframesAtFrame(
        compId: String,
        frame: Int,
        props: Set<CameraAnimatableProp> = CameraAnimatableProp.entries.toSet(),
    ) = ProjectCommand("Delete Camera Keyframe (F$frame)") { p ->
        p.updateComp(compId) { c ->
            var kf = c.camera.keyframes
            if (CameraAnimatableProp.X in props) kf = kf.copy(x = removeKeyframeAt(kf.x, frame))
            if (CameraAnimatableProp.Y in props) kf = kf.copy(y = removeKeyframeAt(kf.y, frame))
            if (CameraAnimatableProp.ZOOM in props) kf = kf.copy(zoom = removeKeyframeAt(kf.zoom, frame))
            c.copy(camera = c.camera.copy(keyframes = kf))
        }
    }

    fun duplicateCameraKeyframes(
        compId: String,
        sourceFrame: Int,
        targetFrame: Int,
        props: Set<CameraAnimatableProp> = CameraAnimatableProp.entries.toSet(),
    ) = ProjectCommand("Duplicate Camera Keyframe (F$sourceFrame → F$targetFrame)") { p ->
        p.updateComp(compId) { c ->
            var kf = c.camera.keyframes
            if (CameraAnimatableProp.X in props) {
                kf.x.find { it.frame == sourceFrame }?.let { kf = kf.copy(x = upsertKeyframe(kf.x, targetFrame, it.value, it.interpolation)) }
            }
            if (CameraAnimatableProp.Y in props) {
                kf.y.find { it.frame == sourceFrame }?.let { kf = kf.copy(y = upsertKeyframe(kf.y, targetFrame, it.value, it.interpolation)) }
            }
            if (CameraAnimatableProp.ZOOM in props) {
                kf.zoom.find { it.frame == sourceFrame }?.let { kf = kf.copy(zoom = upsertKeyframe(kf.zoom, targetFrame, it.value, it.interpolation)) }
            }
            c.copy(camera = c.camera.copy(keyframes = kf))
        }
    }

    fun updateCameraKeyframesAtFrame(
        compId: String,
        sourceFrame: Int,
        props: Set<CameraAnimatableProp>,
        newFrame: Int? = null,
        newValues: Map<CameraAnimatableProp, Double> = emptyMap(),
        newInterpolation: InterpolationType? = null,
    ) = ProjectCommand("Edit Camera Keyframe (F${newFrame ?: sourceFrame})") { p ->
        p.updateComp(compId) { c ->
            var kf = c.camera.keyframes
            fun editList(list: List<Keyframe>, prop: CameraAnimatableProp): List<Keyframe> {
                val existing = list.find { it.frame == sourceFrame } ?: return list
                val destFrame = newFrame ?: existing.frame
                val destVal = newValues[prop] ?: existing.value
                val destInterp = newInterpolation ?: existing.interpolation
                val cleaned = list.filterNot { it.id == existing.id || (destFrame != sourceFrame && it.frame == destFrame) }
                return (cleaned + existing.copy(frame = destFrame, value = destVal, interpolation = destInterp)).sortedBy { it.frame }
            }
            if (CameraAnimatableProp.X in props) kf = kf.copy(x = editList(kf.x, CameraAnimatableProp.X))
            if (CameraAnimatableProp.Y in props) kf = kf.copy(y = editList(kf.y, CameraAnimatableProp.Y))
            if (CameraAnimatableProp.ZOOM in props) kf = kf.copy(zoom = editList(kf.zoom, CameraAnimatableProp.ZOOM))
            c.copy(camera = c.camera.copy(keyframes = kf))
        }
    }

    fun clearCameraKeyframeChannel(
        compId: String,
        prop: CameraAnimatableProp,
        bakeValue: Double? = null,
    ) = ProjectCommand("Clear Camera ${prop.key} Animation") { p ->
        p.updateComp(compId) { c ->
            val cam = c.camera
            val kf = cam.keyframes
            val nextCam = when (prop) {
                CameraAnimatableProp.X -> cam.copy(x = bakeValue ?: cam.x, keyframes = kf.copy(x = emptyList()))
                CameraAnimatableProp.Y -> cam.copy(y = bakeValue ?: cam.y, keyframes = kf.copy(y = emptyList()))
                CameraAnimatableProp.ZOOM -> cam.copy(zoom = bakeValue ?: cam.zoom, keyframes = kf.copy(zoom = emptyList()))
            }
            c.copy(camera = nextCam)
        }
    }

    fun clearAllCameraAnimation(
        compId: String,
        bakeX: Double? = null,
        bakeY: Double? = null,
        bakeZoom: Double? = null,
    ) = ProjectCommand("Clear All Camera Animation") { p ->
        p.updateComp(compId) { c ->
            val cam = c.camera
            c.copy(
                camera = cam.copy(
                    x = bakeX ?: cam.x,
                    y = bakeY ?: cam.y,
                    zoom = bakeZoom ?: cam.zoom,
                    keyframes = CameraKeyframes(),
                    modifiers = emptyList(),
                )
            )
        }
    }

    fun addCameraModifier(compId: String, modifier: MotionModifierInstance) =
        ProjectCommand("Apply Camera Preset") { p ->
            p.updateComp(compId) { c -> c.copy(camera = c.camera.copy(modifiers = c.camera.modifiers + modifier)) }
        }

    fun updateCameraModifier(
        compId: String,
        modifierId: String,
        updater: (MotionModifierInstance) -> MotionModifierInstance,
    ) = ProjectCommand("Update Camera Preset") { p ->
        p.updateComp(compId) { c ->
            c.copy(camera = c.camera.copy(modifiers = c.camera.modifiers.map { if (it.id == modifierId) updater(it) else it }))
        }
    }

    fun removeCameraModifier(compId: String, modifierId: String) =
        ProjectCommand("Remove Camera Preset") { p ->
            p.updateComp(compId) { c ->
                c.copy(camera = c.camera.copy(modifiers = c.camera.modifiers.filterNot { it.id == modifierId }))
            }
        }

    fun bakeCameraModifiersToKeyframes(
        compId: String,
        stepFrames: Int,
        startFrame: Int,
        endFrame: Int,
        removeAfterBake: Boolean,
    ) = ProjectCommand("Bake Camera Modifiers") { p ->
        val comp = p.compositions.find { it.id == compId } ?: return@ProjectCommand p
        val step = max(1, stepFrames)
        val sF = max(0, min(startFrame, endFrame))
        val eF = min(comp.settings.durationFrames - 1, max(sF, endFrame))
        val xList = mutableListOf<Keyframe>()
        val yList = mutableListOf<Keyframe>()
        val zList = mutableListOf<Keyframe>()

        var f = sF
        val frames = mutableListOf<Int>()
        while (f <= eF) {
            frames += f
            f += step
        }
        if (frames.lastOrNull() != eF) frames += eF

        for (sf in frames) {
            val eval = evaluateCompositionAtFrame(p, compId, sf) ?: continue
            xList += Keyframe(uid("kf"), sf, eval.camera.x, InterpolationType.LINEAR)
            yList += Keyframe(uid("kf"), sf, eval.camera.y, InterpolationType.LINEAR)
            zList += Keyframe(uid("kf"), sf, eval.camera.zoom, InterpolationType.LINEAR)
        }
        p.updateComp(compId) { c ->
            c.copy(
                camera = c.camera.copy(
                    keyframes = c.camera.keyframes.copy(x = xList, y = yList, zoom = zList),
                    modifiers = if (removeAfterBake) emptyList() else c.camera.modifiers.map { it.copy(enabled = false) },
                )
            )
        }
    }

    fun updateEnvironment(compId: String, patch: EnvironmentPatch) =
        ProjectCommand("Update Environment") { p ->
            p.updateComp(compId) { c -> c.copy(environment = c.environment.applyPatch(patch)) }
        }

    fun addAudioTrack(compId: String, track: AudioTrackDocument) =
        ProjectCommand("Add Audio Track") { p ->
            p.updateComp(compId) { c -> c.copy(audioTracks = c.audioTracks + track) }
        }

    fun updateAudioTrack(compId: String, trackId: String, updater: (AudioTrackDocument) -> AudioTrackDocument) =
        ProjectCommand("Update Audio Track") { p ->
            p.updateComp(compId) { c ->
                c.copy(audioTracks = c.audioTracks.map { if (it.id == trackId) updater(it) else it })
            }
        }

    fun deleteAudioTrack(compId: String, trackId: String) =
        ProjectCommand("Delete Audio Track") { p ->
            p.updateComp(compId) { c -> c.copy(audioTracks = c.audioTracks.filterNot { it.id == trackId }) }
        }

    fun addAudioClip(compId: String, trackId: String, clip: AudioClipDocument) =
        ProjectCommand("Add Audio Clip") { p ->
            p.updateComp(compId) { c ->
                c.copy(audioTracks = c.audioTracks.map { t ->
                    if (t.id == trackId) t.copy(clips = t.clips + clip) else t
                })
            }
        }

    fun updateAudioClip(
        compId: String,
        trackId: String,
        clipId: String,
        updater: (AudioClipDocument) -> AudioClipDocument,
    ) = ProjectCommand("Update Audio Clip") { p ->
        p.updateComp(compId) { c ->
            c.copy(audioTracks = c.audioTracks.map { t ->
                if (t.id == trackId) t.copy(clips = t.clips.map { if (it.id == clipId) updater(it) else it }) else t
            })
        }
    }

    fun deleteAudioClip(compId: String, trackId: String, clipId: String) =
        ProjectCommand("Delete Audio Clip") { p ->
            p.updateComp(compId) { c ->
                c.copy(audioTracks = c.audioTracks.map { t ->
                    if (t.id == trackId) t.copy(clips = t.clips.filterNot { it.id == clipId }) else t
                })
            }
        }

    fun addAsset(asset: AssetRecord) = ProjectCommand("Import Asset ${asset.name}") { p ->
        p.touch().copy(assets = p.assets + asset)
    }

    fun replaceAsset(assetId: String, updater: (AssetRecord) -> AssetRecord) =
        ProjectCommand("Replace Asset") { p ->
            p.touch().copy(assets = p.assets.map { if (it.id == assetId) updater(it) else it })
        }

    fun deleteAsset(assetId: String) = ProjectCommand("Delete Asset") { p ->
        p.touch().copy(assets = p.assets.filterNot { it.id == assetId })
    }

    fun upsertCharacter(character: CharacterDefinition) = ProjectCommand("Save Character ${character.name}") { p ->
        val exists = p.characters.any { it.id == character.id }
        p.touch().copy(
            characters = if (exists) p.characters.map { if (it.id == character.id) character else it }
            else p.characters + character
        )
    }

    fun deleteCharacter(characterId: String) = ProjectCommand("Delete Character") { p ->
        p.touch().copy(characters = p.characters.filterNot { it.id == characterId })
    }

    fun addMovieClip(clip: ProjectTimelineClip) = ProjectCommand("Add Movie Clip") { p ->
        p.touch().copy(projectTimeline = p.projectTimeline + clip)
    }

    fun updateMovieClip(clipId: String, updater: (ProjectTimelineClip) -> ProjectTimelineClip) =
        ProjectCommand("Update Movie Clip") { p ->
            p.touch().copy(projectTimeline = p.projectTimeline.map { if (it.id == clipId) updater(it) else it })
        }

    fun deleteMovieClip(clipId: String) = ProjectCommand("Delete Movie Clip") { p ->
        p.touch().copy(projectTimeline = p.projectTimeline.filterNot { it.id == clipId })
    }

    fun reorderMovieClip(clipId: String, direction: Int) = ProjectCommand("Reorder Movie Clip") { p ->
        val list = p.projectTimeline.toMutableList()
        val idx = list.indexOfFirst { it.id == clipId }
        if (idx < 0) return@ProjectCommand p
        val target = (idx + direction).coerceIn(0, list.lastIndex)
        if (target == idx) return@ProjectCommand p
        val item = list.removeAt(idx)
        list.add(target, item)
        var cursor = 0
        val reflowed = list.map { c ->
            val overlap = if (cursor > 0 && c.transitionIn != com.example.schema.TransitionType.CUT) c.transitionDurationFrames else 0
            val start = max(0, cursor - overlap)
            cursor = start + c.durationFrames
            c.copy(startFrame = start)
        }
        p.touch().copy(projectTimeline = reflowed)
    }

    fun updateExportSettings(updater: (ExportSettings) -> ExportSettings) =
        ProjectCommand("Update Export Settings") { p ->
            p.touch().copy(exportSettings = updater(p.exportSettings))
        }

    private fun cloneObjectId(obj: SceneObjectDocument): SceneObjectDocument = when (obj) {
        is SceneObjectDocument.SpriteObject -> obj.copy(id = uid("obj"))
        is SceneObjectDocument.CharacterObject -> obj.copy(id = uid("obj"))
        is SceneObjectDocument.ShapeObject -> obj.copy(id = uid("obj"))
        is SceneObjectDocument.TextObject -> obj.copy(id = uid("obj"))
        is SceneObjectDocument.NestedCompositionObject -> obj.copy(id = uid("obj"))
    }
}
