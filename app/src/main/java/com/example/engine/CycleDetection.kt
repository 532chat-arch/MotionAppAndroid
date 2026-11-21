package com.example.engine

import com.example.schema.CompositionRecord
import com.example.schema.LayerDocument
import com.example.schema.MotionProject
import com.example.schema.SceneObjectDocument

/**
 * Checks whether nesting `childCompId` inside `parentCompId` would create
 * a circular composition reference (direct or transitive).
 */
fun wouldCreateCompositionCycle(
    project: MotionProject,
    parentCompId: String,
    childCompId: String,
): Boolean {
    if (parentCompId == childCompId) return true
    val compMap = project.compositions.associateBy { it.id }
    val visited = mutableSetOf<String>()
    val stack = ArrayDeque<String>()
    stack.addLast(childCompId)

    while (stack.isNotEmpty()) {
        val currentId = stack.removeLast()
        if (currentId == parentCompId) return true
        if (!visited.add(currentId)) continue
        val comp: CompositionRecord = compMap[currentId] ?: continue
        for (layer in comp.layers) {
            for (obj in layer.objects) {
                if (obj is SceneObjectDocument.NestedCompositionObject) {
                    if (obj.compositionId == parentCompId) return true
                    stack.addLast(obj.compositionId)
                }
            }
        }
    }
    return false
}

/**
 * Checks whether setting `candidateParentId` as the parent of `layerId`
 * would introduce a cycle in the layer hierarchy.
 */
fun wouldCreateLayerParentCycle(
    layers: List<LayerDocument>,
    layerId: String,
    candidateParentId: String?,
): Boolean {
    if (candidateParentId == null) return false
    if (candidateParentId == layerId) return true

    val byId = layers.associateBy { it.id }
    var cursor: String? = candidateParentId
    val visited = mutableSetOf<String>()
    while (cursor != null) {
        if (cursor == layerId) return true
        if (!visited.add(cursor)) return true
        cursor = byId[cursor]?.parentId
    }
    return false
}
