package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddBox
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.commands.ProjectCommands
import com.example.engine.EvaluatedCompositionState
import com.example.engine.LayerMotionPath
import com.example.engine.MotionPathDragOverride
import com.example.engine.computeLayerMotionPath
import com.example.engine.evaluateCompositionAtFrame
import com.example.renderer.AndroidCanvasRenderer
import com.example.renderer.GizmoHandle
import com.example.renderer.OnionSkinFrameOverlay
import com.example.renderer.RenderOverlayOptions
import com.example.schema.LayerAnimatableProp
import com.example.ui.theme.StudioBg
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioCyan
import com.example.ui.theme.StudioEmerald
import com.example.ui.theme.StudioMonoStyle
import com.example.ui.theme.StudioPanel
import com.example.ui.theme.StudioPrimary
import com.example.ui.theme.StudioRose
import com.example.ui.theme.StudioTextPrimary
import com.example.ui.theme.StudioTextSecondary
import com.example.viewmodel.KeyframeOwnerType
import com.example.viewmodel.SelectedKeyframeRef
import com.example.viewmodel.StageTool
import com.example.viewmodel.StudioUiState
import com.example.viewmodel.StudioViewModel
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun CompositionStage(
    state: StudioUiState,
    viewModel: StudioViewModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val comp = state.activeComposition
    var viewportSize by remember { mutableStateOf(IntSize(1, 1)) }
    var userZoom by remember(comp.id) { mutableFloatStateOf(1f) }
    var panOffset by remember(comp.id) { mutableStateOf(Offset.Zero) }
    var pathDragOverride by remember { mutableStateOf<MotionPathDragOverride?>(null) }

    val evaluated: EvaluatedCompositionState? = remember(state.project, comp.id, state.currentFrame) {
        evaluateCompositionAtFrame(state.project, comp.id, state.currentFrame)
    }

    val onionSkinOverlays: List<OnionSkinFrameOverlay> = remember(
        state.project,
        comp.id,
        state.currentFrame,
        state.onionSkin,
        state.selectedLayerId,
    ) {
        val os = state.onionSkin
        if (!os.enabled) return@remember emptyList()
        val duration = max(1, comp.settings.durationFrames)
        val step = os.frameStep.coerceAtLeast(1)
        val selectedOnly = if (os.selectedLayerOnly) state.selectedLayerId else null
        val prevTint = android.graphics.Color.argb(145, 244, 63, 94)   // Rose tint for previous frames
        val nextTint = android.graphics.Color.argb(145, 16, 185, 129)  // Emerald tint for next frames

        buildList {
            for (i in os.prevFrames downTo 1) {
                val frameIdx = state.currentFrame - i * step
                if (frameIdx >= 0) {
                    evaluateCompositionAtFrame(state.project, comp.id, frameIdx)?.let { ev ->
                        val alpha = (os.opacity * (1f - (i - 1) * 0.22f)).coerceIn(0.08f, 0.70f)
                        add(OnionSkinFrameOverlay(ev, alpha, prevTint, selectedOnly))
                    }
                }
            }
            for (i in os.nextFrames downTo 1) {
                val frameIdx = state.currentFrame + i * step
                if (frameIdx < duration) {
                    evaluateCompositionAtFrame(state.project, comp.id, frameIdx)?.let { ev ->
                        val alpha = (os.opacity * (1f - (i - 1) * 0.22f)).coerceIn(0.08f, 0.70f)
                        add(OnionSkinFrameOverlay(ev, alpha, nextTint, selectedOnly))
                    }
                }
            }
        }
    }

    val motionPath: LayerMotionPath? = remember(comp, state.selectedLayerId, pathDragOverride) {
        state.selectedLayerId?.let { computeLayerMotionPath(comp, it, pathDragOverride) }
    }

    val baseScale = remember(viewportSize, comp.settings.width, comp.settings.height) {
        val pad = 32f
        val availW = max(64f, viewportSize.width - pad)
        val availH = max(64f, viewportSize.height - pad)
        min(availW / comp.settings.width.toFloat(), availH / comp.settings.height.toFloat())
    }

    val totalScale = baseScale * userZoom
    val compDrawW = comp.settings.width * totalScale
    val compDrawH = comp.settings.height * totalScale
    val originX = (viewportSize.width - compDrawW) / 2f + panOffset.x
    val originY = (viewportSize.height - compDrawH) / 2f + panOffset.y

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(com.example.ui.theme.StudioSurface)
            .onSizeChanged { viewportSize = it }
            .testTag("composition_stage"),
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(comp.id, state.stageTool, state.selectedLayerId, state.currentFrame, totalScale, originX, originY) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val startCompX = ((down.position.x - originX) / totalScale).toDouble()
                        val startCompY = ((down.position.y - originY) / totalScale).toDouble()
                        val evalSnap = evaluateCompositionAtFrame(state.project, comp.id, state.currentFrame)

                        var activeHandle = GizmoHandle.NONE
                        var draggingLayerId: String? = null
                        var draggingPathFrame: Int? = null
                        var startLayerX = 0.0
                        var startLayerY = 0.0
                        var startScaleX = 1.0
                        var startScaleY = 1.0
                        var startRot = 0.0
                        var startDist = 1.0
                        var startAngle = 0.0
                        var moved = false

                        if (state.stageTool == StageTool.SELECT && evalSnap != null) {
                            // 1. Check spatial motion path keyframe nodes (generous tablet-friendly hit target)
                            if (state.showMotionPath && motionPath != null && state.selectedLayerId != null) {
                                val selState = evalSnap.layers.find { it.layer.id == state.selectedLayerId }
                                val parallax = 1.0 + (selState?.depth ?: 0.0) * evalSnap.camera.parallaxStrength * 0.45
                                val touchHitRadiusComp = max(
                                    28.0 / max(0.3, evalSnap.camera.zoom),
                                    (28.dp.toPx() / max(0.2f, totalScale)).toDouble(),
                                )
                                val nearestNode = motionPath.keyframeNodes
                                    .map { node ->
                                        val sx = node.x - evalSnap.camera.x * parallax
                                        val sy = node.y - evalSnap.camera.y * parallax
                                        node to hypot(startCompX - sx, startCompY - sy)
                                    }
                                    .filter { it.second <= touchHitRadiusComp }
                                    .minByOrNull { it.second }
                                    ?.first
                                if (nearestNode != null) {
                                    draggingPathFrame = nearestNode.frame
                                    draggingLayerId = state.selectedLayerId
                                    // Immediately move playhead to this exact keyframe & select it
                                    viewModel.selectKeyframe(
                                        SelectedKeyframeRef(
                                            ownerType = KeyframeOwnerType.LAYER,
                                            layerId = state.selectedLayerId,
                                            frame = nearestNode.frame,
                                            fromMotionPath = true,
                                        ),
                                        movePlayhead = true,
                                    )
                                }
                            }

                            // 2. Check gizmo handles on selected layer
                            if (draggingPathFrame == null && state.selectedLayerId != null) {
                                activeHandle = AndroidCanvasRenderer.hitTestGizmoHandle(evalSnap, state.selectedLayerId, startCompX, startCompY)
                                if (activeHandle != GizmoHandle.NONE) {
                                    draggingLayerId = state.selectedLayerId
                                }
                            }

                            // 3. Hit test layer body
                            if (draggingLayerId == null) {
                                val hit = AndroidCanvasRenderer.hitTestLayer(evalSnap, startCompX, startCompY)
                                viewModel.selectLayer(hit)
                                if (hit == null) viewModel.clearKeyframeSelection()
                                draggingLayerId = hit
                            }

                            if (draggingLayerId != null) {
                                val ls = evalSnap.layers.find { it.layer.id == draggingLayerId }
                                if (ls != null && !ls.layer.locked) {
                                    startLayerX = ls.localTransform.x
                                    startLayerY = ls.localTransform.y
                                    startScaleX = ls.localTransform.scaleX
                                    startScaleY = ls.localTransform.scaleY
                                    startRot = ls.localTransform.rotationDeg
                                    val parallax = 1.0 + ls.depth * evalSnap.camera.parallaxStrength * 0.45
                                    val cx = ls.worldTransform.x - evalSnap.camera.x * parallax
                                    val cy = ls.worldTransform.y - evalSnap.camera.y * parallax
                                    startDist = max(12.0, hypot(startCompX - cx, startCompY - cy))
                                    startAngle = atan2(startCompY - cy, startCompX - cx) * 180.0 / PI
                                } else {
                                    draggingLayerId = null
                                }
                            }
                        }

                        var totalDragX = 0f
                        var totalDragY = 0f

                        while (true) {
                            val event = awaitPointerEvent()
                            val activePointers = event.changes.filter { it.pressed }
                            if (activePointers.isEmpty()) break

                            if (activePointers.size >= 2) {
                                val zoomChange = event.calculateZoom()
                                val panChange = event.calculatePan()
                                userZoom = (userZoom * zoomChange).coerceIn(0.25f, 4.0f)
                                panOffset += panChange
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                                continue
                            }

                            val change = activePointers.first()
                            val delta = change.position - change.previousPosition
                            if (delta == Offset.Zero) continue
                            moved = true
                            totalDragX += delta.x
                            totalDragY += delta.y
                            change.consume()

                            if (state.stageTool == StageTool.PAN) {
                                panOffset += delta
                            } else if (draggingPathFrame != null && draggingLayerId != null && evalSnap != null) {
                                val curCompX = ((change.position.x - originX) / totalScale).toDouble()
                                val curCompY = ((change.position.y - originY) / totalScale).toDouble()
                                val ls = evalSnap.layers.find { it.layer.id == draggingLayerId }
                                val parallax = 1.0 + (ls?.depth ?: 0.0) * evalSnap.camera.parallaxStrength * 0.45
                                val wx = curCompX + evalSnap.camera.x * parallax
                                val wy = curCompY + evalSnap.camera.y * parallax
                                pathDragOverride = MotionPathDragOverride(draggingLayerId, draggingPathFrame, wx, wy)
                            } else if (draggingLayerId != null && evalSnap != null) {
                                val curCompX = ((change.position.x - originX) / totalScale).toDouble()
                                val curCompY = ((change.position.y - originY) / totalScale).toDouble()
                                val camZoom = max(0.2, evalSnap.camera.zoom)
                                val ls = evalSnap.layers.find { it.layer.id == draggingLayerId }
                                if (ls != null) {
                                    val parallax = 1.0 + ls.depth * evalSnap.camera.parallaxStrength * 0.45
                                    val cx = ls.worldTransform.x - evalSnap.camera.x * parallax
                                    val cy = ls.worldTransform.y - evalSnap.camera.y * parallax
                                    when (activeHandle) {
                                        GizmoHandle.ROTATE -> {
                                            val angleNow = atan2(curCompY - cy, curCompX - cx) * 180.0 / PI
                                            val newRot = startRot + (angleNow - startAngle)
                                            viewModel.dispatchCommand(
                                                ProjectCommands.updateLayerTransformValue(
                                                    comp.id, draggingLayerId, state.currentFrame, rotationDeg = newRot
                                                )
                                            )
                                        }
                                        GizmoHandle.TOP_LEFT, GizmoHandle.TOP_RIGHT, GizmoHandle.BOTTOM_LEFT, GizmoHandle.BOTTOM_RIGHT -> {
                                            val distNow = max(12.0, hypot(curCompX - cx, curCompY - cy))
                                            val ratio = (distNow / startDist).coerceIn(0.15, 6.0)
                                            viewModel.dispatchCommand(
                                                ProjectCommands.updateLayerTransformValue(
                                                    comp.id,
                                                    draggingLayerId,
                                                    state.currentFrame,
                                                    scaleX = startScaleX * ratio,
                                                    scaleY = startScaleY * ratio,
                                                )
                                            )
                                        }
                                        GizmoHandle.NONE -> {
                                            val newX = startLayerX + (totalDragX / totalScale) / camZoom
                                            val newY = startLayerY + (totalDragY / totalScale) / camZoom
                                            viewModel.dispatchCommand(
                                                ProjectCommands.updateLayerTransformValue(
                                                    comp.id, draggingLayerId, state.currentFrame, x = newX, y = newY
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Commit motion path node drag as a single atomic undoable command
                        val override = pathDragOverride
                        if (override != null && moved) {
                            viewModel.dispatchCommand(
                                ProjectCommands.setLayerPositionKeyframe(
                                    compId = comp.id,
                                    layerId = override.layerId,
                                    frame = override.frame,
                                    x = override.worldX,
                                    y = override.worldY,
                                )
                            )
                            viewModel.selectKeyframe(
                                SelectedKeyframeRef(
                                    ownerType = KeyframeOwnerType.LAYER,
                                    layerId = override.layerId,
                                    frame = override.frame,
                                    fromMotionPath = true,
                                ),
                                movePlayhead = true,
                            )
                        }
                        pathDragOverride = null
                    }
                }
        ) {
            drawIntoCanvas { canvas ->
                val startNs = System.nanoTime()
                val nc = canvas.nativeCanvas
                nc.save()
                nc.translate(originX, originY)
                nc.scale(totalScale, totalScale)
                val selectedKfFrame = state.selectedKeyframe
                    ?.takeIf { it.ownerType == KeyframeOwnerType.LAYER && it.layerId == state.selectedLayerId }
                    ?.frame
                val ap = state.adaptivePreview
                if (evaluated != null) {
                    AndroidCanvasRenderer.renderComposition(
                        canvas = nc,
                        context = context,
                        project = state.project,
                        evaluated = evaluated,
                        overlays = RenderOverlayOptions(
                            selectedLayerId = state.selectedLayerId,
                            selectedKeyframeFrame = selectedKfFrame,
                            showGuides = state.showGuides,
                            showMotionPath = state.showMotionPath,
                            motionPath = motionPath,
                            reducedQuality = state.reducedQuality || ap.simplifyExpensiveEffects,
                            particleScale = ap.effectiveParticleScale,
                            maxParticles = ap.effectiveMaxParticles,
                            onionSkinFrames = onionSkinOverlays,
                        ),
                    )
                }
                // Crisp outer canvas frame border (#3C3C3C hairline)
                val borderPaint = android.graphics.Paint().apply {
                    style = android.graphics.Paint.Style.STROKE
                    strokeWidth = 1.5f / max(0.2f, totalScale)
                    color = android.graphics.Color.argb(180, 60, 60, 60)
                }
                nc.drawRect(0f, 0f, comp.settings.width.toFloat(), comp.settings.height.toFloat(), borderPaint)
                nc.restore()
                val renderMs = (System.nanoTime() - startNs) / 1_000_000f
                viewModel.reportPreviewFrameRenderMs(renderMs)
            }
        }

        // Docked Left Vertical Tool Strip (Motion Studio Pro: 44dp tools, 2dp #4B9BE8 active left border)
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(StudioPanel.copy(alpha = 0.96f))
                .border(1.dp, StudioBorder, RoundedCornerShape(4.dp)),
        ) {
            StageToolButton(
                icon = Icons.Default.NearMe,
                contentDesc = stringResource(R.string.tool_select),
                active = state.stageTool == StageTool.SELECT,
                onClick = { viewModel.setStageTool(StageTool.SELECT) },
                tag = "stage_tool_select",
            )
            StageToolButton(
                icon = Icons.Default.OpenWith,
                contentDesc = stringResource(R.string.tool_pan),
                active = state.stageTool == StageTool.PAN,
                onClick = { viewModel.setStageTool(StageTool.PAN) },
                tag = "stage_tool_pan",
            )
            StageToolButton(
                icon = Icons.Default.AddBox,
                contentDesc = stringResource(R.string.tool_add_shape),
                active = false,
                onClick = { viewModel.addQuickShapeLayer() },
                tag = "stage_add_shape",
            )
            StageToolButton(
                icon = Icons.Default.TextFields,
                contentDesc = stringResource(R.string.tool_add_text),
                active = false,
                onClick = { viewModel.addQuickTextLayer() },
                tag = "stage_add_text",
            )
            StageToolButton(
                icon = Icons.Default.GridOn,
                contentDesc = stringResource(R.string.tool_guides),
                active = state.showGuides,
                activeColor = StudioPrimary,
                onClick = { viewModel.toggleGuides() },
                tag = "stage_toggle_guides",
            )
            StageToolButton(
                icon = Icons.Default.Route,
                contentDesc = stringResource(R.string.tool_motion_path),
                active = state.showMotionPath,
                activeColor = StudioPrimary,
                onClick = { viewModel.toggleMotionPath() },
                tag = "stage_toggle_motion_path",
            )
        }

        // Bottom-start Adaptive Preview Quality Badge + Onion Skin Legend Pill
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            AdaptivePreviewBadge(state = state, viewModel = viewModel)
            if (state.onionSkin.enabled) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(StudioPanel.copy(alpha = 0.96f))
                        .border(1.dp, StudioBorder, RoundedCornerShape(4.dp))
                        .clickable { viewModel.toggleOnionSkin() }
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                        .testTag("stage_onion_skin_badge"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(StudioRose))
                    Text(
                        text = "-${state.onionSkin.prevFrames}",
                        style = StudioMonoStyle,
                        color = StudioRose,
                    )
                    Text(
                        text = if (state.onionSkin.frameStep > 1) "ONION · ${state.onionSkin.frameStep}f" else "ONION",
                        style = StudioMonoStyle,
                        color = StudioTextPrimary,
                    )
                    Text(
                        text = "+${state.onionSkin.nextFrames}",
                        style = StudioMonoStyle,
                        color = StudioEmerald,
                    )
                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(StudioEmerald))
                }
            }
        }

        // Bottom-right Recessed Zoom & Fit Bar
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(10.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(StudioPanel.copy(alpha = 0.96f))
                .border(1.dp, StudioBorder, RoundedCornerShape(4.dp))
                .padding(horizontal = 3.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            StageCompactIconButton(
                icon = Icons.Default.ZoomOut,
                contentDesc = stringResource(R.string.tool_zoom_out),
                onClick = { userZoom = (userZoom - 0.15f).coerceAtLeast(0.25f) },
                tag = "stage_zoom_out",
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .background(com.example.ui.theme.StudioInsetField)
                    .border(1.dp, StudioBorder, RoundedCornerShape(3.dp))
                    .clickable {
                        userZoom = 1f
                        panOffset = Offset.Zero
                    }
                    .padding(horizontal = 7.dp, vertical = 3.dp),
            ) {
                Text(
                    text = "${(userZoom * 100).roundToInt()}%",
                    style = StudioMonoStyle,
                    color = StudioPrimary,
                )
            }
            StageCompactIconButton(
                icon = Icons.Default.ZoomIn,
                contentDesc = stringResource(R.string.tool_zoom_in),
                onClick = { userZoom = (userZoom + 0.15f).coerceAtMost(4f) },
                tag = "stage_zoom_in",
            )
            StageCompactIconButton(
                icon = Icons.Default.CenterFocusStrong,
                contentDesc = stringResource(R.string.tool_zoom_fit),
                onClick = {
                    userZoom = 1f
                    panOffset = Offset.Zero
                },
                tag = "stage_zoom_fit",
            )
        }
    }
}

@Composable
private fun StageToolButton(
    icon: ImageVector,
    contentDesc: String,
    active: Boolean,
    onClick: () -> Unit,
    tag: String,
    activeColor: Color = StudioPrimary,
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .background(if (active) com.example.ui.theme.StudioSurfaceElevated else Color.Transparent)
            .clickable(onClick = onClick)
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        if (active) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(width = 2.dp, height = 44.dp)
                    .background(activeColor),
            )
        }
        Icon(
            icon,
            contentDescription = contentDesc,
            tint = if (active) StudioTextPrimary else StudioTextSecondary,
            modifier = Modifier.size(19.dp),
        )
    }
}

@Composable
private fun StageCompactIconButton(
    icon: ImageVector,
    contentDesc: String,
    onClick: () -> Unit,
    tag: String,
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(3.dp))
            .clickable(onClick = onClick)
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDesc,
            tint = StudioTextSecondary,
            modifier = Modifier.size(17.dp),
        )
    }
}
