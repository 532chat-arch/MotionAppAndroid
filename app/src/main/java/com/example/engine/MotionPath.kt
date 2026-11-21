package com.example.engine

import com.example.schema.CompositionRecord
import com.example.schema.Keyframe
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

data class MotionPathSample(
    val frame: Int,
    val x: Double,
    val y: Double,
)

data class MotionPathKeyframeNode(
    val frame: Int,
    val x: Double,
    val y: Double,
    val hasXKey: Boolean,
    val hasYKey: Boolean,
)

data class LayerMotionPath(
    val layerId: String,
    val samples: List<MotionPathSample>,
    val keyframeNodes: List<MotionPathKeyframeNode>,
)

data class MotionPathDragOverride(
    val layerId: String,
    val frame: Int,
    val worldX: Double,
    val worldY: Double,
)

fun computeLayerMotionPath(
    comp: CompositionRecord,
    layerId: String,
    dragOverride: MotionPathDragOverride? = null,
): LayerMotionPath? {
    val layer = comp.layers.find { it.id == layerId } ?: return null
    val xKfs = layer.keyframes.x
    val yKfs = layer.keyframes.y
    if (xKfs.isEmpty() && yKfs.isEmpty()) return null

    val allFrames = (xKfs.map { it.frame } + yKfs.map { it.frame }).distinct().sorted()
    val startF = max(0, allFrames.first())
    val endF = min(comp.settings.durationFrames - 1, allFrames.last())
    if (endF <= startF) {
        val px = evaluateKeyframes(xKfs, startF.toDouble(), layer.transform.x)
        val py = evaluateKeyframes(yKfs, startF.toDouble(), layer.transform.y)
        return LayerMotionPath(
            layerId = layerId,
            samples = listOf(MotionPathSample(startF, px, py)),
            keyframeNodes = listOf(
                MotionPathKeyframeNode(startF, px, py, xKfs.any { it.frame == startF }, yKfs.any { it.frame == startF })
            ),
        )
    }

    val byId = comp.layers.associateBy { it.id }

    fun evalParentWorld(parentId: String?, frame: Double, visited: MutableSet<String>): EvaluatedTransform? {
        if (parentId == null) return null
        val pLayer = byId[parentId] ?: return null
        if (!visited.add(parentId)) return null
        val local = EvaluatedTransform(
            x = evaluateKeyframes(pLayer.keyframes.x, frame, pLayer.transform.x),
            y = evaluateKeyframes(pLayer.keyframes.y, frame, pLayer.transform.y),
            scaleX = evaluateKeyframes(pLayer.keyframes.scaleX, frame, pLayer.transform.scaleX),
            scaleY = evaluateKeyframes(pLayer.keyframes.scaleY, frame, pLayer.transform.scaleY),
            rotationDeg = evaluateKeyframes(pLayer.keyframes.rotation, frame, pLayer.transform.rotationDeg),
            opacity = 1.0,
        )
        val grand = evalParentWorld(pLayer.parentId, frame, visited) ?: return local
        val rad = grand.rotationDeg * PI / 180.0
        val c = cos(rad)
        val s = sin(rad)
        val sx = local.x * grand.scaleX
        val sy = local.y * grand.scaleY
        return EvaluatedTransform(
            x = grand.x + sx * c - sy * s,
            y = grand.y + sx * s + sy * c,
            scaleX = grand.scaleX * local.scaleX,
            scaleY = grand.scaleY * local.scaleY,
            rotationDeg = grand.rotationDeg + local.rotationDeg,
            opacity = 1.0,
        )
    }

    val effectiveXKfs = if (dragOverride != null && dragOverride.layerId == layerId) {
        upsertOverrideKeyframe(xKfs, dragOverride.frame, dragOverride.worldX)
    } else xKfs

    val effectiveYKfs = if (dragOverride != null && dragOverride.layerId == layerId) {
        upsertOverrideKeyframe(yKfs, dragOverride.frame, dragOverride.worldY)
    } else yKfs

    fun worldPosAt(frame: Int): Pair<Double, Double> {
        val f = frame.toDouble()
        val lx = evaluateKeyframes(effectiveXKfs, f, layer.transform.x)
        val ly = evaluateKeyframes(effectiveYKfs, f, layer.transform.y)
        val parent = evalParentWorld(layer.parentId, f, mutableSetOf()) ?: return lx to ly
        val rad = parent.rotationDeg * PI / 180.0
        val c = cos(rad)
        val s = sin(rad)
        val sx = lx * parent.scaleX
        val sy = ly * parent.scaleY
        return (parent.x + sx * c - sy * s) to (parent.y + sx * s + sy * c)
    }

    val step = max(1, (endF - startF) / 72)
    val samples = mutableListOf<MotionPathSample>()
    var f = startF
    while (f <= endF) {
        val (wx, wy) = worldPosAt(f)
        samples += MotionPathSample(f, wx, wy)
        f += step
    }
    if (samples.lastOrNull()?.frame != endF) {
        val (wx, wy) = worldPosAt(endF)
        samples += MotionPathSample(endF, wx, wy)
    }

    val nodes = allFrames.map { kfFrame ->
        val (wx, wy) = worldPosAt(kfFrame)
        MotionPathKeyframeNode(
            frame = kfFrame,
            x = wx,
            y = wy,
            hasXKey = xKfs.any { it.frame == kfFrame },
            hasYKey = yKfs.any { it.frame == kfFrame },
        )
    }

    return LayerMotionPath(layerId, samples, nodes)
}

private fun upsertOverrideKeyframe(
    kfs: List<Keyframe>,
    frame: Int,
    value: Double,
): List<Keyframe> {
    val existing = kfs.indexOfFirst { it.frame == frame }
    return if (existing >= 0) {
        kfs.mapIndexed { idx, kf -> if (idx == existing) kf.copy(value = value) else kf }
    } else {
        (kfs + Keyframe(id = "override_$frame", frame = frame, value = value)).sortedBy { it.frame }
    }
}
