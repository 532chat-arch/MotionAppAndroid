package com.example.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.commands.ProjectCommands
import com.example.engine.EvaluatedCompositionState
import com.example.schema.CameraAnimatableProp
import com.example.schema.CameraDocument
import com.example.schema.InterpolationType
import com.example.schema.Keyframe
import com.example.schema.LayerAnimatableProp
import com.example.schema.LayerDocument
import com.example.schema.LayerRole
import com.example.ui.theme.StudioAmber
import com.example.ui.theme.StudioCyan
import com.example.ui.theme.StudioEmerald
import com.example.ui.theme.StudioPrimaryBright
import com.example.ui.theme.StudioRose
import com.example.viewmodel.KeyframeOwnerType
import com.example.viewmodel.SelectedKeyframeRef
import com.example.viewmodel.StudioViewModel
import kotlin.math.roundToInt

internal val ZOOM_STEPS_DP = listOf(4f, 6f, 9f, 12f, 16f, 22f)

internal data class TimelineChannelRowData(
    val id: String,
    val label: String,
    val shortValue: String,
    val keyframes: List<Keyframe>,
    val accent: Color,
    val hasKeyAtCurrent: Boolean,
    val selectedKeyframeFrame: Int? = null,
    val onToggleKeyAtCurrent: () -> Unit,
    val onSelectKeyframe: ((frame: Int) -> Unit)? = null,
    val onMoveKeyframe: ((keyframeId: String, newFrame: Int) -> Unit)? = null,
    val onClearChannel: (() -> Unit)? = null,
)

internal fun DrawScope.drawFrameGrid(
    durationFrames: Int,
    majorInterval: Int,
    frameWidthPx: Float,
) {
    var f = 0
    var bandIdx = 0
    while (f < durationFrames) {
        val x = f * frameWidthPx
        val bandW = (majorInterval * frameWidthPx).coerceAtMost(size.width - x)
        if (bandIdx % 2 == 1 && bandW > 0f) {
            drawRect(
                color = Color(0x09FFFFFF),
                topLeft = Offset(x, 0f),
                size = Size(bandW, size.height),
            )
        }
        drawLine(
            color = Color(0x2094A3B8),
            start = Offset(x, 0f),
            end = Offset(x, size.height),
            strokeWidth = 1f,
        )
        f += majorInterval
        bandIdx++
    }
}

internal fun DrawScope.drawKeyframeDiamond(
    cx: Float,
    cy: Float,
    radius: Float,
    fillColor: Color,
    strokeColor: Color,
) {
    val diamond = Path().apply {
        moveTo(cx, cy - radius)
        lineTo(cx + radius, cy)
        lineTo(cx, cy + radius)
        lineTo(cx - radius, cy)
        close()
    }
    drawPath(diamond, fillColor)
    drawPath(diamond, strokeColor, style = Stroke(width = 1.2f))
}

internal fun DrawScope.drawKeyframeGlyph(
    cx: Float,
    cy: Float,
    radius: Float,
    interpolation: InterpolationType,
    fillColor: Color,
    strokeColor: Color,
) {
    when (interpolation) {
        InterpolationType.LINEAR -> drawKeyframeDiamond(cx, cy, radius, fillColor, strokeColor)
        InterpolationType.HOLD -> {
            val s = radius * 1.45f
            drawRect(
                color = fillColor,
                topLeft = Offset(cx - s / 2f, cy - s / 2f),
                size = Size(s, s),
            )
            drawRect(
                color = strokeColor,
                topLeft = Offset(cx - s / 2f, cy - s / 2f),
                size = Size(s, s),
                style = Stroke(width = 1.2f),
            )
        }
        InterpolationType.EASE_IN, InterpolationType.EASE_OUT, InterpolationType.EASE_IN_OUT -> {
            // Signature After Effects hourglass / Bezier dual-cone glyph
            val hourglass = Path().apply {
                moveTo(cx - radius, cy - radius * 0.85f)
                lineTo(cx + radius, cy - radius * 0.85f)
                lineTo(cx + radius * 0.25f, cy)
                lineTo(cx + radius, cy + radius * 0.85f)
                lineTo(cx - radius, cy + radius * 0.85f)
                lineTo(cx - radius * 0.25f, cy)
                close()
            }
            drawPath(hourglass, fillColor)
            drawPath(hourglass, strokeColor, style = Stroke(width = 1.1f))
        }
    }
}

internal fun easingColor(type: InterpolationType): Color = when (type) {
    InterpolationType.LINEAR -> Color(0xFFA5A5A5)
    InterpolationType.HOLD -> StudioRose
    InterpolationType.EASE_IN, InterpolationType.EASE_OUT -> StudioEmerald
    InterpolationType.EASE_IN_OUT -> StudioPrimaryBright
}

internal fun roleAccentColor(role: LayerRole): Color = when (role) {
    LayerRole.BACKGROUND -> Color(0xFF9999CC)
    LayerRole.MIDGROUND -> Color(0xFF4B9BE8)
    LayerRole.CHARACTER -> Color(0xFFD6A24D)
    LayerRole.FOREGROUND -> Color(0xFF76A982)
    LayerRole.PROP -> StudioPrimaryBright
    LayerRole.EFFECT -> Color(0xFFB4B4E8)
    LayerRole.CONTROL -> Color(0xFFD66B67)
}

internal fun layerKeyframeFrames(layer: LayerDocument): List<Int> =
    (layer.keyframes.x + layer.keyframes.y + layer.keyframes.rotation +
        layer.keyframes.scaleX + layer.keyframes.scaleY + layer.keyframes.opacity)
        .map { it.frame }
        .distinct()
        .sorted()

internal fun hasAnyLayerKeyframeAt(layer: LayerDocument, frame: Int): Boolean =
    layer.keyframes.x.any { it.frame == frame } ||
        layer.keyframes.y.any { it.frame == frame } ||
        layer.keyframes.rotation.any { it.frame == frame } ||
        layer.keyframes.scaleX.any { it.frame == frame } ||
        layer.keyframes.scaleY.any { it.frame == frame } ||
        layer.keyframes.opacity.any { it.frame == frame }

internal fun toggleLayerTransformKeysAtFrame(
    viewModel: StudioViewModel,
    compId: String,
    layer: LayerDocument,
    frame: Int,
    evaluated: EvaluatedCompositionState?,
) {
    if (hasAnyLayerKeyframeAt(layer, frame)) {
        viewModel.dispatchCommand(ProjectCommands.removeLayerKeyframesAtFrame(compId, layer.id, frame))
    } else {
        val lt = evaluated?.layers?.find { it.layer.id == layer.id }?.localTransform
        if (viewModel.dispatchCommand(
                ProjectCommands.setLayerTransformKeyframesAtFrame(
                    compId = compId,
                    layerId = layer.id,
                    frame = frame,
                    x = lt?.x ?: layer.transform.x,
                    y = lt?.y ?: layer.transform.y,
                    rotationDeg = lt?.rotationDeg ?: layer.transform.rotationDeg,
                )
            )
        ) {
            viewModel.selectKeyframe(
                SelectedKeyframeRef(KeyframeOwnerType.LAYER, layer.id, frame),
                movePlayhead = false,
            )
        }
    }
}

internal fun toggleCameraPositionKeysAtFrame(
    viewModel: StudioViewModel,
    compId: String,
    cam: CameraDocument,
    frame: Int,
    evaluated: EvaluatedCompositionState?,
) {
    val hasAny = cam.keyframes.x.any { it.frame == frame } ||
        cam.keyframes.y.any { it.frame == frame } ||
        cam.keyframes.zoom.any { it.frame == frame }
    if (hasAny) {
        viewModel.dispatchCommand(ProjectCommands.removeCameraKeyframesAtFrame(compId, frame))
    } else {
        val evalCam = evaluated?.camera
        if (viewModel.dispatchCommand(
                ProjectCommands.setCameraTransformKeyframesAtFrame(
                    compId = compId,
                    frame = frame,
                    x = evalCam?.x ?: cam.x,
                    y = evalCam?.y ?: cam.y,
                    zoom = evalCam?.zoom ?: cam.zoom,
                )
            )
        ) {
            viewModel.selectKeyframe(
                SelectedKeyframeRef(KeyframeOwnerType.CAMERA, frame = frame),
                movePlayhead = false,
            )
        }
    }
}

internal fun buildLayerChannelRows(
    layer: LayerDocument,
    compId: String,
    currentFrame: Int,
    selectedKeyframe: SelectedKeyframeRef?,
    evalX: Double,
    evalY: Double,
    evalRot: Double,
    evalScaleX: Double,
    evalScaleY: Double,
    evalOpacity: Double,
    viewModel: StudioViewModel,
): List<TimelineChannelRowData> {
    fun toggle(prop: LayerAnimatableProp, currentVal: Double, existing: List<Keyframe>) {
        if (existing.any { it.frame == currentFrame }) {
            viewModel.dispatchCommand(ProjectCommands.removeLayerKeyframe(compId, layer.id, prop, currentFrame))
        } else {
            if (viewModel.dispatchCommand(ProjectCommands.setLayerKeyframe(compId, layer.id, prop, currentFrame, currentVal))) {
                viewModel.selectKeyframe(
                    SelectedKeyframeRef(KeyframeOwnerType.LAYER, layer.id, currentFrame, layerProp = prop),
                    movePlayhead = false,
                )
            }
        }
    }
    fun selectKf(prop: LayerAnimatableProp): (Int) -> Unit = { f ->
        viewModel.selectKeyframe(
            SelectedKeyframeRef(KeyframeOwnerType.LAYER, layer.id, f, layerProp = prop),
            movePlayhead = true,
        )
    }
    fun move(prop: LayerAnimatableProp): (String, Int) -> Unit = { kfId, targetFrame ->
        if (viewModel.dispatchCommand(ProjectCommands.updateLayerKeyframe(compId, layer.id, prop, kfId, newFrame = targetFrame))) {
            viewModel.selectKeyframe(
                SelectedKeyframeRef(KeyframeOwnerType.LAYER, layer.id, targetFrame, layerProp = prop),
                movePlayhead = true,
            )
        }
    }
    fun selectedFrameFor(prop: LayerAnimatableProp): Int? {
        val ref = selectedKeyframe ?: return null
        if (ref.ownerType != KeyframeOwnerType.LAYER || ref.layerId != layer.id) return null
        return when {
            ref.layerProp == prop -> ref.frame
            ref.layerProp == null && ref.fromMotionPath && (prop == LayerAnimatableProp.X || prop == LayerAnimatableProp.Y) -> ref.frame
            ref.layerProp == null && !ref.fromMotionPath -> ref.frame
            else -> null
        }
    }
    return listOf(
        TimelineChannelRowData(
            id = "${layer.id}_x",
            label = "Pos X",
            shortValue = "${evalX.roundToInt()}",
            keyframes = layer.keyframes.x,
            accent = StudioRose,
            hasKeyAtCurrent = layer.keyframes.x.any { it.frame == currentFrame },
            selectedKeyframeFrame = selectedFrameFor(LayerAnimatableProp.X),
            onToggleKeyAtCurrent = { toggle(LayerAnimatableProp.X, evalX, layer.keyframes.x) },
            onSelectKeyframe = selectKf(LayerAnimatableProp.X),
            onMoveKeyframe = move(LayerAnimatableProp.X),
            onClearChannel = { viewModel.clearLayerPropertyAnimation(layer.id, LayerAnimatableProp.X) },
        ),
        TimelineChannelRowData(
            id = "${layer.id}_y",
            label = "Pos Y",
            shortValue = "${evalY.roundToInt()}",
            keyframes = layer.keyframes.y,
            accent = StudioEmerald,
            hasKeyAtCurrent = layer.keyframes.y.any { it.frame == currentFrame },
            selectedKeyframeFrame = selectedFrameFor(LayerAnimatableProp.Y),
            onToggleKeyAtCurrent = { toggle(LayerAnimatableProp.Y, evalY, layer.keyframes.y) },
            onSelectKeyframe = selectKf(LayerAnimatableProp.Y),
            onMoveKeyframe = move(LayerAnimatableProp.Y),
            onClearChannel = { viewModel.clearLayerPropertyAnimation(layer.id, LayerAnimatableProp.Y) },
        ),
        TimelineChannelRowData(
            id = "${layer.id}_rot",
            label = "Rotation",
            shortValue = "${evalRot.roundToInt()}°",
            keyframes = layer.keyframes.rotation,
            accent = StudioAmber,
            hasKeyAtCurrent = layer.keyframes.rotation.any { it.frame == currentFrame },
            selectedKeyframeFrame = selectedFrameFor(LayerAnimatableProp.ROTATION),
            onToggleKeyAtCurrent = { toggle(LayerAnimatableProp.ROTATION, evalRot, layer.keyframes.rotation) },
            onSelectKeyframe = selectKf(LayerAnimatableProp.ROTATION),
            onMoveKeyframe = move(LayerAnimatableProp.ROTATION),
            onClearChannel = { viewModel.clearLayerPropertyAnimation(layer.id, LayerAnimatableProp.ROTATION) },
        ),
        TimelineChannelRowData(
            id = "${layer.id}_sx",
            label = "Scale X",
            shortValue = "%.2f".format(evalScaleX),
            keyframes = layer.keyframes.scaleX,
            accent = StudioCyan,
            hasKeyAtCurrent = layer.keyframes.scaleX.any { it.frame == currentFrame },
            selectedKeyframeFrame = selectedFrameFor(LayerAnimatableProp.SCALE_X),
            onToggleKeyAtCurrent = { toggle(LayerAnimatableProp.SCALE_X, evalScaleX, layer.keyframes.scaleX) },
            onSelectKeyframe = selectKf(LayerAnimatableProp.SCALE_X),
            onMoveKeyframe = move(LayerAnimatableProp.SCALE_X),
            onClearChannel = { viewModel.clearLayerPropertyAnimation(layer.id, LayerAnimatableProp.SCALE_X) },
        ),
        TimelineChannelRowData(
            id = "${layer.id}_sy",
            label = "Scale Y",
            shortValue = "%.2f".format(evalScaleY),
            keyframes = layer.keyframes.scaleY,
            accent = StudioPrimaryBright,
            hasKeyAtCurrent = layer.keyframes.scaleY.any { it.frame == currentFrame },
            selectedKeyframeFrame = selectedFrameFor(LayerAnimatableProp.SCALE_Y),
            onToggleKeyAtCurrent = { toggle(LayerAnimatableProp.SCALE_Y, evalScaleY, layer.keyframes.scaleY) },
            onSelectKeyframe = selectKf(LayerAnimatableProp.SCALE_Y),
            onMoveKeyframe = move(LayerAnimatableProp.SCALE_Y),
            onClearChannel = { viewModel.clearLayerPropertyAnimation(layer.id, LayerAnimatableProp.SCALE_Y) },
        ),
        TimelineChannelRowData(
            id = "${layer.id}_op",
            label = "Opacity",
            shortValue = "${(evalOpacity * 100).roundToInt()}%",
            keyframes = layer.keyframes.opacity,
            accent = Color(0xFFA855F7),
            hasKeyAtCurrent = layer.keyframes.opacity.any { it.frame == currentFrame },
            selectedKeyframeFrame = selectedFrameFor(LayerAnimatableProp.OPACITY),
            onToggleKeyAtCurrent = { toggle(LayerAnimatableProp.OPACITY, evalOpacity, layer.keyframes.opacity) },
            onSelectKeyframe = selectKf(LayerAnimatableProp.OPACITY),
            onMoveKeyframe = move(LayerAnimatableProp.OPACITY),
            onClearChannel = { viewModel.clearLayerPropertyAnimation(layer.id, LayerAnimatableProp.OPACITY) },
        ),
    )
}
