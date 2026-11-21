package com.example.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.commands.ProjectCommands
import com.example.engine.evaluateCompositionAtFrame
import com.example.schema.CameraAnimatableProp
import com.example.schema.SceneObjectDocument
import com.example.schema.formatTimecode
import com.example.schema.getActiveCompFps
import com.example.ui.theme.StudioAmber
import com.example.ui.theme.StudioBg
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioBorderStrong
import com.example.ui.theme.StudioCyan
import com.example.ui.theme.StudioEmerald
import com.example.ui.theme.StudioInsetField
import com.example.ui.theme.StudioMonoStyle
import com.example.ui.theme.StudioPanel
import com.example.ui.theme.StudioPrimary
import com.example.ui.theme.StudioPrimaryBright
import com.example.ui.theme.StudioSecondary
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceLowest
import com.example.ui.theme.StudioTextMuted
import com.example.ui.theme.StudioTextPrimary
import com.example.ui.theme.StudioTextSecondary
import com.example.ui.theme.StudioWorkspaceCanvas
import com.example.viewmodel.InspectorTarget
import com.example.viewmodel.KeyframeOwnerType
import com.example.viewmodel.SelectedKeyframeRef
import com.example.viewmodel.StudioUiState
import com.example.viewmodel.StudioViewModel
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@Composable
fun CompositionTimeline(
    state: StudioUiState,
    viewModel: StudioViewModel,
    collapsed: Boolean,
    onToggleCollapse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val comp = state.activeComposition
    val fps = getActiveCompFps(state.project, comp.id)
    val duration = max(1, comp.settings.durationFrames)
    val maxFrame = max(0, duration - 1)

    var activeTab by remember { mutableStateOf("tracks") }
    var fitToWidth by remember { mutableStateOf(false) }
    var frameStepDp by remember { mutableFloatStateOf(9f) }
    var followPlayhead by remember { mutableStateOf(true) }

    val expandedTrackIds = remember { mutableStateMapOf<String, Boolean>() }
    val timelineScrollState = rememberScrollState()
    val controlsScrollState = rememberScrollState()
    val timelineCoroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()

    val evaluated = remember(state.project, comp.id, state.currentFrame) {
        evaluateCompositionAtFrame(state.project, comp.id, state.currentFrame)
    }

    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importAudioUri(uri, addToTimeline = true)
    }

    val selectedOrAllKeyframes = remember(comp, state.selectedLayerId, state.inspectorTarget) {
        val selectedLayer = comp.layers.find { it.id == state.selectedLayerId }
        val raw = when {
            state.inspectorTarget == InspectorTarget.CAMERA -> {
                (comp.camera.keyframes.x + comp.camera.keyframes.y + comp.camera.keyframes.zoom + comp.camera.keyframes.rotation)
                    .map { it.frame }
            }
            selectedLayer != null -> layerKeyframeFrames(selectedLayer)
            else -> {
                comp.layers.flatMap { layerKeyframeFrames(it) } +
                    (comp.camera.keyframes.x + comp.camera.keyframes.y + comp.camera.keyframes.zoom + comp.camera.keyframes.rotation)
                        .map { it.frame }
            }
        }
        raw.distinct().sorted()
    }
    val prevKf = selectedOrAllKeyframes.lastOrNull { it < state.currentFrame }
    val nextKf = selectedOrAllKeyframes.firstOrNull { it > state.currentFrame }

    val selectedLayer = comp.layers.find { it.id == state.selectedLayerId }
    val selectedHasKfAtCurrent = remember(selectedLayer, comp.camera, state.inspectorTarget, state.currentFrame) {
        if (state.inspectorTarget == InspectorTarget.CAMERA) {
            listOf(comp.camera.keyframes.x, comp.camera.keyframes.y, comp.camera.keyframes.zoom)
                .any { list -> list.any { it.frame == state.currentFrame } }
        } else if (selectedLayer != null) {
            hasAnyLayerKeyframeAt(selectedLayer, state.currentFrame)
        } else false
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .background(StudioPanel)
            .border(1.dp, StudioBorder),
    ) {
        val isCompact = maxWidth < 700.dp
        val headerWidthDp = if (isCompact) 128.dp else 168.dp
        val trackViewportWidthDp = (maxWidth - headerWidthDp - 1.dp).coerceAtLeast(160.dp)
        val effectiveFrameDp = remember(fitToWidth, frameStepDp, trackViewportWidthDp, duration) {
            if (fitToWidth) (trackViewportWidthDp.value / duration.toFloat()).coerceIn(1.5f, 32f)
            else frameStepDp
        }
        val majorInterval = remember(effectiveFrameDp) {
            when {
                effectiveFrameDp >= 18f -> 5
                effectiveFrameDp >= 10f -> 10
                effectiveFrameDp >= 5.5f -> 20
                effectiveFrameDp >= 3f -> 30
                else -> 60
            }
        }
        val minorInterval = remember(effectiveFrameDp) {
            when {
                effectiveFrameDp >= 14f -> 1
                effectiveFrameDp >= 7f -> 2
                effectiveFrameDp >= 3.5f -> 5
                else -> 10
            }
        }
        val trackContentWidthDp = remember(duration, effectiveFrameDp, trackViewportWidthDp) {
            max(trackViewportWidthDp.value, duration * effectiveFrameDp).dp
        }
        val frameWidthPx = with(density) { effectiveFrameDp.dp.toPx() }
        val trackViewportPx = with(density) { trackViewportWidthDp.toPx() }
        val trackContentPx = with(density) { trackContentWidthDp.toPx() }

        LaunchedEffect(state.currentFrame, followPlayhead, frameWidthPx, trackViewportPx) {
            if (!followPlayhead || timelineScrollState.isScrollInProgress) return@LaunchedEffect
            val playheadX = state.currentFrame * frameWidthPx
            val viewLeft = timelineScrollState.value.toFloat()
            val viewRight = viewLeft + trackViewportPx
            val margin = (trackViewportPx * 0.16f).coerceAtMost(72f)
            if (playheadX < viewLeft + margin || playheadX > viewRight - margin) {
                val target = (playheadX - trackViewportPx * 0.35f)
                    .roundToInt()
                    .coerceIn(0, timelineScrollState.maxValue)
                if (state.isPlaying) timelineScrollState.scrollTo(target)
                else timelineScrollState.animateScrollTo(target)
            }
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            // Transport, Timecode, Tabs & Zoom Header (Motion Studio Pro)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(StudioPanel)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                IconButton(
                    onClick = onToggleCollapse,
                    modifier = Modifier.size(36.dp).testTag("timeline_collapse_toggle"),
                ) {
                    Icon(
                        if (collapsed) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = stringResource(R.string.panel_timeline),
                        tint = StudioTextSecondary,
                    )
                }

                // Playback Transport Cluster (flat 4dp industrial controls)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    IconButton(
                        onClick = { viewModel.stopPlayback(); viewModel.setCurrentFrame(0) },
                        modifier = Modifier.size(32.dp).testTag("timeline_jump_start"),
                    ) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = stringResource(R.string.timeline_jump_start), tint = StudioTextSecondary, modifier = Modifier.size(17.dp))
                    }
                    IconButton(
                        onClick = { viewModel.stepFrame(-1) },
                        modifier = Modifier.size(32.dp).testTag("timeline_step_back"),
                    ) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = stringResource(R.string.timeline_step_back), tint = StudioTextSecondary, modifier = Modifier.size(17.dp))
                    }
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (state.isPlaying) StudioInsetField else StudioPrimary)
                            .border(
                                1.dp,
                                if (state.isPlaying) StudioPrimary else Color.Transparent,
                                RoundedCornerShape(4.dp),
                            )
                            .clickable { viewModel.togglePlayback() }
                            .testTag("timeline_play_pause"),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = stringResource(if (state.isPlaying) R.string.action_pause else R.string.action_play),
                            tint = if (state.isPlaying) StudioPrimary else Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    IconButton(
                        onClick = { viewModel.stepFrame(1) },
                        modifier = Modifier.size(32.dp).testTag("timeline_step_forward"),
                    ) {
                        Icon(Icons.Default.ChevronRight, contentDescription = stringResource(R.string.timeline_step_forward), tint = StudioTextSecondary, modifier = Modifier.size(17.dp))
                    }
                    IconButton(
                        onClick = { viewModel.stopPlayback(); viewModel.setCurrentFrame(maxFrame) },
                        modifier = Modifier.size(32.dp).testTag("timeline_jump_end"),
                    ) {
                        Icon(Icons.Default.SkipNext, contentDescription = stringResource(R.string.timeline_jump_end), tint = StudioTextSecondary, modifier = Modifier.size(17.dp))
                    }
                    IconButton(
                        onClick = { viewModel.toggleLoopPlayback() },
                        modifier = Modifier.size(32.dp).testTag("timeline_loop_toggle"),
                    ) {
                        Icon(
                            Icons.Default.Repeat,
                            contentDescription = stringResource(R.string.timeline_toggle_loop),
                            tint = if (state.loopPlayback) StudioPrimary else StudioTextMuted.copy(alpha = 0.55f),
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }

                // Horizontally scrollable secondary controls
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(controlsScrollState),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                ) {
                    // Recessed Timecode Display (#191919 field, #4B9BE8 JetBrains Mono)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(StudioInsetField)
                            .border(1.dp, StudioBorder, RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = "${formatTimecode(state.currentFrame, fps)} · F${state.currentFrame}/$maxFrame",
                            style = StudioMonoStyle,
                            color = StudioPrimary,
                            maxLines = 1,
                        )
                    }

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(StudioInsetField)
                            .border(1.dp, StudioBorder, RoundedCornerShape(4.dp))
                            .padding(2.dp),
                    ) {
                        TimelineModeTab(stringResource(R.string.timeline_tab_tracks), activeTab == "tracks", "timeline_tab_tracks") { activeTab = "tracks" }
                        TimelineModeTab("${stringResource(R.string.timeline_tab_audio)} (${comp.audioTracks.sumOf { it.clips.size }})", activeTab == "audio", "timeline_tab_audio") { activeTab = "audio" }
                    }

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(StudioInsetField)
                            .border(1.dp, StudioBorder, RoundedCornerShape(4.dp))
                            .padding(horizontal = 2.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SmallTimelineIconBtn(Icons.Default.Remove, stringResource(R.string.timeline_zoom_out), tag = "timeline_zoom_out") {
                            fitToWidth = false
                            val curIdx = ZOOM_STEPS_DP.indexOfFirst { it >= frameStepDp - 0.1f }.let {
                                if (it < 0) ZOOM_STEPS_DP.lastIndex else it
                            }
                            frameStepDp = ZOOM_STEPS_DP[max(0, curIdx - 1)]
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(3.dp))
                                .background(if (fitToWidth) StudioPrimary.copy(alpha = 0.24f) else Color.Transparent)
                                .clickable { fitToWidth = !fitToWidth }
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                                .testTag("timeline_zoom_fit"),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                Icon(Icons.Default.FitScreen, contentDescription = stringResource(R.string.timeline_zoom_fit), tint = if (fitToWidth) StudioPrimary else StudioTextSecondary, modifier = Modifier.size(13.dp))
                                Text(if (fitToWidth) "FIT" else "${effectiveFrameDp.roundToInt()}px", style = StudioMonoStyle.copy(fontSize = 10.sp), color = if (fitToWidth) StudioTextPrimary else StudioTextSecondary)
                            }
                        }
                        SmallTimelineIconBtn(Icons.Default.Add, stringResource(R.string.timeline_zoom_in), tag = "timeline_zoom_in") {
                            fitToWidth = false
                            frameStepDp = ZOOM_STEPS_DP.firstOrNull { it > frameStepDp + 0.1f } ?: ZOOM_STEPS_DP.last()
                        }
                    }

                    OnionSkinToolbarControl(
                        settings = state.onionSkin,
                        onToggle = { viewModel.toggleOnionSkin() },
                        onUpdate = { transform -> viewModel.updateOnionSkin(transform) },
                    )

                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (followPlayhead) StudioPrimary.copy(alpha = 0.22f) else StudioInsetField)
                            .border(1.dp, if (followPlayhead) StudioPrimary else StudioBorder, RoundedCornerShape(4.dp))
                            .clickable { followPlayhead = !followPlayhead }
                            .testTag("timeline_follow_toggle"),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.CenterFocusStrong,
                            contentDescription = stringResource(R.string.timeline_follow_playhead),
                            tint = if (followPlayhead) StudioPrimary else StudioTextSecondary,
                            modifier = Modifier.size(15.dp),
                        )
                    }

                    KeyframeDiamondButton(
                        hasKeyframeAtFrame = selectedHasKfAtCurrent,
                        hasAnyKeyframes = selectedOrAllKeyframes.isNotEmpty(),
                        onClick = {
                            if (state.inspectorTarget == InspectorTarget.CAMERA) {
                                toggleCameraPositionKeysAtFrame(viewModel, comp.id, comp.camera, state.currentFrame, evaluated)
                            } else if (selectedLayer != null) {
                                toggleLayerTransformKeysAtFrame(viewModel, comp.id, selectedLayer, state.currentFrame, evaluated)
                            }
                        },
                        modifier = Modifier.testTag("timeline_key_toggle"),
                    )

                    SmallTimelineIconBtn(Icons.Default.ContentCopy, stringResource(R.string.timeline_copy_kf), tag = "copy_keyframes_button") {
                        viewModel.copyKeyframesAtCurrentFrame()
                    }
                    SmallTimelineIconBtn(
                        icon = Icons.Default.ContentPaste,
                        contentDesc = stringResource(R.string.timeline_paste_kf),
                        tint = if (state.copiedKeyframes.isNotEmpty()) StudioPrimary else StudioTextMuted.copy(alpha = 0.45f),
                        enabled = state.copiedKeyframes.isNotEmpty(),
                        tag = "paste_keyframes_button",
                    ) {
                        viewModel.pasteKeyframesAtCurrentFrame()
                    }

                    AnimationClearMenuButton(
                        state = state,
                        viewModel = viewModel,
                    )
                }
            }

            HorizontalDivider(thickness = 1.dp, color = StudioBorder)

            // Contextual Edit / Duplicate / Interpolation / Delete / Clear Action Bar
            KeyframeContextActionBar(
                state = state,
                viewModel = viewModel,
            )

            // Horizontal Frame Ruler Row (always visible, scrolls frames horizontally in sync)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp)
                    .background(StudioBg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier
                        .width(headerWidthDp)
                        .fillMaxHeight()
                        .background(StudioWorkspaceCanvas)
                        .padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "${comp.layers.size} LAYERS · ${fps.roundToInt()}FPS",
                        style = StudioMonoStyle.copy(fontSize = 10.sp),
                        color = StudioTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .clickable(enabled = prevKf != null) {
                                    prevKf?.let { f ->
                                        if (state.inspectorTarget == InspectorTarget.CAMERA) {
                                            viewModel.selectKeyframe(SelectedKeyframeRef(KeyframeOwnerType.CAMERA, frame = f), movePlayhead = true)
                                        } else if (selectedLayer != null) {
                                            viewModel.selectKeyframe(SelectedKeyframeRef(KeyframeOwnerType.LAYER, selectedLayer.id, f), movePlayhead = true)
                                        } else {
                                            viewModel.stopPlayback()
                                            viewModel.setCurrentFrame(f)
                                        }
                                    }
                                }
                                .testTag("timeline_prev_kf"),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Default.ChevronLeft, contentDescription = stringResource(R.string.timeline_prev_kf), tint = if (prevKf != null) StudioPrimary else StudioTextMuted.copy(alpha = 0.4f), modifier = Modifier.size(16.dp))
                        }
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .clickable(enabled = nextKf != null) {
                                    nextKf?.let { f ->
                                        if (state.inspectorTarget == InspectorTarget.CAMERA) {
                                            viewModel.selectKeyframe(SelectedKeyframeRef(KeyframeOwnerType.CAMERA, frame = f), movePlayhead = true)
                                        } else if (selectedLayer != null) {
                                            viewModel.selectKeyframe(SelectedKeyframeRef(KeyframeOwnerType.LAYER, selectedLayer.id, f), movePlayhead = true)
                                        } else {
                                            viewModel.stopPlayback()
                                            viewModel.setCurrentFrame(f)
                                        }
                                    }
                                }
                                .testTag("timeline_next_kf"),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Default.ChevronRight, contentDescription = stringResource(R.string.timeline_next_kf), tint = if (nextKf != null) StudioPrimary else StudioTextMuted.copy(alpha = 0.4f), modifier = Modifier.size(16.dp))
                        }
                    }
                }

                Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(StudioBorder))

                FrameScrubRulerTrack(
                    scrollState = timelineScrollState,
                    contentWidthDp = trackContentWidthDp,
                    frameWidthDp = effectiveFrameDp.dp,
                    durationFrames = duration,
                    currentFrame = state.currentFrame,
                    majorInterval = majorInterval,
                    minorInterval = minorInterval,
                    markers = comp.markers,
                    summaryKeyframes = if (collapsed) selectedOrAllKeyframes else emptyList(),
                    onionSkin = state.onionSkin,
                    textMeasurer = textMeasurer,
                    onScrubFrame = { f -> viewModel.stopPlayback(); viewModel.setCurrentFrame(f) },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }

            // Full-duration Viewport Overview / Minimap Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .background(StudioSurface),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .width(headerWidthDp)
                        .fillMaxHeight()
                        .background(StudioInsetField)
                        .padding(horizontal = 6.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        text = stringResource(R.string.timeline_hold_drag_hint),
                        style = StudioMonoStyle.copy(fontSize = 8.sp),
                        color = StudioTextMuted.copy(alpha = 0.78f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(StudioBorderStrong))
                TimelineViewportMinimap(
                    durationFrames = duration,
                    currentFrame = state.currentFrame,
                    scrollValue = timelineScrollState.value,
                    maxScrollValue = timelineScrollState.maxValue,
                    viewportWidthPx = trackViewportPx,
                    contentWidthPx = trackContentPx,
                    onJumpFrame = { f ->
                        viewModel.stopPlayback()
                        viewModel.setCurrentFrame(f)
                        if (!followPlayhead && timelineScrollState.maxValue > 0) {
                            val target = (f * frameWidthPx - trackViewportPx * 0.5f)
                                .roundToInt()
                                .coerceIn(0, timelineScrollState.maxValue)
                            timelineCoroutineScope.launch {
                                timelineScrollState.scrollTo(target)
                            }
                        }
                    },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }

            // Collapsible Multi-Layer & Keyframe Dope Sheet / Audio Tracks
            AnimatedVisibility(visible = !collapsed, enter = expandVertically(), exit = shrinkVertically()) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    HorizontalDivider(color = StudioBorder)

                    if (activeTab == "tracks") {
                        val timelineHeight = if (isCompact) 186.dp else 214.dp
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(timelineHeight)
                                .background(StudioBg)
                                .testTag("timeline_tracks_list"),
                        ) {
                            item(key = "camera_summary_row") {
                                val camKfFrames = remember(comp.camera.keyframes) {
                                    (comp.camera.keyframes.x + comp.camera.keyframes.y + comp.camera.keyframes.zoom + comp.camera.keyframes.rotation)
                                        .map { it.frame }.distinct().sorted()
                                }
                                val camExpanded = expandedTrackIds["__camera__"] == true

                                TimelineStickyRow(
                                    headerWidthDp = headerWidthDp,
                                    scrollState = timelineScrollState,
                                    contentWidthDp = trackContentWidthDp,
                                    rowHeight = 38.dp,
                                    isSelected = state.inspectorTarget == InspectorTarget.CAMERA,
                                    headerContent = {
                                        CameraTrackHeader(
                                            expanded = camExpanded,
                                            isSelected = state.inspectorTarget == InspectorTarget.CAMERA,
                                            hasKeyframeAtCurrent = state.currentFrame in camKfFrames,
                                            hasAnyKeyframes = camKfFrames.isNotEmpty(),
                                            onSelect = { viewModel.setInspectorTarget(InspectorTarget.CAMERA) },
                                            onToggleExpand = { expandedTrackIds["__camera__"] = !camExpanded },
                                            onToggleKey = { toggleCameraPositionKeysAtFrame(viewModel, comp.id, comp.camera, state.currentFrame, evaluated) },
                                        )
                                    },
                                    trackContent = {
                                        val selectedCamKfFrame = state.selectedKeyframe
                                            ?.takeIf { it.ownerType == KeyframeOwnerType.CAMERA }
                                            ?.frame
                                        TimelineLayerLaneCanvas(
                                            frameWidthDp = effectiveFrameDp.dp,
                                            durationFrames = duration,
                                            majorInterval = majorInterval,
                                            inFrame = 0,
                                            outFrame = maxFrame,
                                            currentFrame = state.currentFrame,
                                            accent = StudioCyan,
                                            isSelected = state.inspectorTarget == InspectorTarget.CAMERA,
                                            isLocked = false,
                                            isVisible = true,
                                            keyframeFrames = camKfFrames,
                                            selectedKeyframeFrame = selectedCamKfFrame,
                                            modifierSpans = comp.camera.modifiers.filter { it.enabled }.map { it.startFrame to (it.startFrame + it.durationFrames) },
                                            lyricCues = emptyList(),
                                            onSelectTrack = {
                                                viewModel.setInspectorTarget(InspectorTarget.CAMERA)
                                                viewModel.clearKeyframeSelection()
                                            },
                                            onSelectKeyframe = { f ->
                                                viewModel.selectKeyframe(
                                                    SelectedKeyframeRef(KeyframeOwnerType.CAMERA, frame = f),
                                                    movePlayhead = true,
                                                )
                                            },
                                            onJumpFrame = { f ->
                                                viewModel.stopPlayback()
                                                viewModel.setCurrentFrame(f)
                                                viewModel.setInspectorTarget(InspectorTarget.CAMERA)
                                            },
                                        )
                                    },
                                )

                                if (camExpanded) {
                                    val evalCam = evaluated?.camera
                                    fun camSelFrame(prop: CameraAnimatableProp): Int? {
                                        val ref = state.selectedKeyframe ?: return null
                                        if (ref.ownerType != KeyframeOwnerType.CAMERA) return null
                                        return if (ref.cameraProp == null || ref.cameraProp == prop) ref.frame else null
                                    }
                                    val camChannels = listOf(
                                        TimelineChannelRowData(
                                            id = "cam_x",
                                            label = "Cam X",
                                            shortValue = "${(evalCam?.x ?: comp.camera.x).roundToInt()}",
                                            keyframes = comp.camera.keyframes.x,
                                            accent = StudioCyan,
                                            hasKeyAtCurrent = comp.camera.keyframes.x.any { it.frame == state.currentFrame },
                                            selectedKeyframeFrame = camSelFrame(CameraAnimatableProp.X),
                                            onToggleKeyAtCurrent = {
                                                if (comp.camera.keyframes.x.any { it.frame == state.currentFrame }) {
                                                    viewModel.dispatchCommand(ProjectCommands.removeCameraKeyframe(comp.id, CameraAnimatableProp.X, state.currentFrame))
                                                } else if (viewModel.dispatchCommand(ProjectCommands.setCameraKeyframe(comp.id, CameraAnimatableProp.X, state.currentFrame, evalCam?.x ?: comp.camera.x))) {
                                                    viewModel.selectKeyframe(SelectedKeyframeRef(KeyframeOwnerType.CAMERA, frame = state.currentFrame, cameraProp = CameraAnimatableProp.X), movePlayhead = false)
                                                }
                                            },
                                            onSelectKeyframe = { f ->
                                                viewModel.selectKeyframe(SelectedKeyframeRef(KeyframeOwnerType.CAMERA, frame = f, cameraProp = CameraAnimatableProp.X), movePlayhead = true)
                                            },
                                            onMoveKeyframe = { kfId, targetFrame ->
                                                if (viewModel.dispatchCommand(ProjectCommands.updateCameraKeyframe(comp.id, CameraAnimatableProp.X, kfId, newFrame = targetFrame))) {
                                                    viewModel.selectKeyframe(SelectedKeyframeRef(KeyframeOwnerType.CAMERA, frame = targetFrame, cameraProp = CameraAnimatableProp.X), movePlayhead = true)
                                                }
                                            },
                                            onClearChannel = { viewModel.clearCameraPropertyAnimation(CameraAnimatableProp.X) },
                                        ),
                                        TimelineChannelRowData(
                                            id = "cam_y",
                                            label = "Cam Y",
                                            shortValue = "${(evalCam?.y ?: comp.camera.y).roundToInt()}",
                                            keyframes = comp.camera.keyframes.y,
                                            accent = StudioCyan,
                                            hasKeyAtCurrent = comp.camera.keyframes.y.any { it.frame == state.currentFrame },
                                            selectedKeyframeFrame = camSelFrame(CameraAnimatableProp.Y),
                                            onToggleKeyAtCurrent = {
                                                if (comp.camera.keyframes.y.any { it.frame == state.currentFrame }) {
                                                    viewModel.dispatchCommand(ProjectCommands.removeCameraKeyframe(comp.id, CameraAnimatableProp.Y, state.currentFrame))
                                                } else if (viewModel.dispatchCommand(ProjectCommands.setCameraKeyframe(comp.id, CameraAnimatableProp.Y, state.currentFrame, evalCam?.y ?: comp.camera.y))) {
                                                    viewModel.selectKeyframe(SelectedKeyframeRef(KeyframeOwnerType.CAMERA, frame = state.currentFrame, cameraProp = CameraAnimatableProp.Y), movePlayhead = false)
                                                }
                                            },
                                            onSelectKeyframe = { f ->
                                                viewModel.selectKeyframe(SelectedKeyframeRef(KeyframeOwnerType.CAMERA, frame = f, cameraProp = CameraAnimatableProp.Y), movePlayhead = true)
                                            },
                                            onMoveKeyframe = { kfId, targetFrame ->
                                                if (viewModel.dispatchCommand(ProjectCommands.updateCameraKeyframe(comp.id, CameraAnimatableProp.Y, kfId, newFrame = targetFrame))) {
                                                    viewModel.selectKeyframe(SelectedKeyframeRef(KeyframeOwnerType.CAMERA, frame = targetFrame, cameraProp = CameraAnimatableProp.Y), movePlayhead = true)
                                                }
                                            },
                                            onClearChannel = { viewModel.clearCameraPropertyAnimation(CameraAnimatableProp.Y) },
                                        ),
                                        TimelineChannelRowData(
                                            id = "cam_zoom",
                                            label = "Zoom",
                                            shortValue = "%.2fx".format(evalCam?.zoom ?: comp.camera.zoom),
                                            keyframes = comp.camera.keyframes.zoom,
                                            accent = StudioEmerald,
                                            hasKeyAtCurrent = comp.camera.keyframes.zoom.any { it.frame == state.currentFrame },
                                            selectedKeyframeFrame = camSelFrame(CameraAnimatableProp.ZOOM),
                                            onToggleKeyAtCurrent = {
                                                if (comp.camera.keyframes.zoom.any { it.frame == state.currentFrame }) {
                                                    viewModel.dispatchCommand(ProjectCommands.removeCameraKeyframe(comp.id, CameraAnimatableProp.ZOOM, state.currentFrame))
                                                } else if (viewModel.dispatchCommand(ProjectCommands.setCameraKeyframe(comp.id, CameraAnimatableProp.ZOOM, state.currentFrame, evalCam?.zoom ?: comp.camera.zoom))) {
                                                    viewModel.selectKeyframe(SelectedKeyframeRef(KeyframeOwnerType.CAMERA, frame = state.currentFrame, cameraProp = CameraAnimatableProp.ZOOM), movePlayhead = false)
                                                }
                                            },
                                            onSelectKeyframe = { f ->
                                                viewModel.selectKeyframe(SelectedKeyframeRef(KeyframeOwnerType.CAMERA, frame = f, cameraProp = CameraAnimatableProp.ZOOM), movePlayhead = true)
                                            },
                                            onMoveKeyframe = { kfId, targetFrame ->
                                                if (viewModel.dispatchCommand(ProjectCommands.updateCameraKeyframe(comp.id, CameraAnimatableProp.ZOOM, kfId, newFrame = targetFrame))) {
                                                    viewModel.selectKeyframe(SelectedKeyframeRef(KeyframeOwnerType.CAMERA, frame = targetFrame, cameraProp = CameraAnimatableProp.ZOOM), movePlayhead = true)
                                                }
                                            },
                                            onClearChannel = { viewModel.clearCameraPropertyAnimation(CameraAnimatableProp.ZOOM) },
                                        ),
                                    )
                                    camChannels.forEach { ch ->
                                        TimelineChannelSubRow(
                                            channel = ch,
                                            headerWidthDp = headerWidthDp,
                                            scrollState = timelineScrollState,
                                            contentWidthDp = trackContentWidthDp,
                                            frameWidthDp = effectiveFrameDp.dp,
                                            durationFrames = duration,
                                            majorInterval = majorInterval,
                                            currentFrame = state.currentFrame,
                                            textMeasurer = textMeasurer,
                                            onJumpFrame = { f ->
                                                viewModel.stopPlayback()
                                                viewModel.setCurrentFrame(f)
                                                viewModel.setInspectorTarget(InspectorTarget.CAMERA)
                                                viewModel.clearKeyframeSelection()
                                            },
                                        )
                                    }
                                }
                            }

                            items(comp.layers.asReversed(), key = { it.id }) { layer ->
                                val isSelected = state.selectedLayerId == layer.id && state.inspectorTarget != InspectorTarget.CAMERA
                                val layerExpanded = expandedTrackIds[layer.id] == true
                                val kfFrames = remember(layer.keyframes) { layerKeyframeFrames(layer) }
                                val textObj = layer.objects.firstOrNull() as? SceneObjectDocument.TextObject
                                val cues = if (textObj?.lyrics?.enabled == true) textObj.lyrics.cues else emptyList()
                                val roleAccent = if (cues.isNotEmpty()) StudioAmber else roleAccentColor(layer.role)
                                val selectedLayerKfFrame = state.selectedKeyframe
                                    ?.takeIf { it.ownerType == KeyframeOwnerType.LAYER && it.layerId == layer.id }
                                    ?.frame

                                TimelineStickyRow(
                                    headerWidthDp = headerWidthDp,
                                    scrollState = timelineScrollState,
                                    contentWidthDp = trackContentWidthDp,
                                    rowHeight = 40.dp,
                                    isSelected = isSelected,
                                    headerContent = {
                                        LayerTrackHeader(
                                            layer = layer,
                                            accent = roleAccent,
                                            expanded = layerExpanded,
                                            isSelected = isSelected,
                                            hasKeyframeAtCurrent = state.currentFrame in kfFrames,
                                            hasAnyKeyframes = kfFrames.isNotEmpty(),
                                            onSelect = {
                                                viewModel.selectLayer(layer.id)
                                                viewModel.setInspectorTarget(InspectorTarget.LAYER)
                                            },
                                            onToggleExpand = { expandedTrackIds[layer.id] = !layerExpanded },
                                            onToggleVisibility = {
                                                viewModel.dispatchCommand(ProjectCommands.updateLayer(comp.id, layer.id) { it.copy(visible = !it.visible) })
                                            },
                                            onToggleKey = { toggleLayerTransformKeysAtFrame(viewModel, comp.id, layer, state.currentFrame, evaluated) },
                                        )
                                    },
                                    trackContent = {
                                        TimelineLayerLaneCanvas(
                                            frameWidthDp = effectiveFrameDp.dp,
                                            durationFrames = duration,
                                            majorInterval = majorInterval,
                                            inFrame = layer.inFrame,
                                            outFrame = layer.outFrame,
                                            currentFrame = state.currentFrame,
                                            accent = roleAccent,
                                            isSelected = isSelected,
                                            isLocked = layer.locked,
                                            isVisible = layer.visible,
                                            keyframeFrames = kfFrames,
                                            selectedKeyframeFrame = selectedLayerKfFrame,
                                            modifierSpans = layer.modifiers.filter { it.enabled }.map { it.startFrame to (it.startFrame + it.durationFrames) },
                                            lyricCues = cues.map { it.startFrame to it.endFrame },
                                            onSelectTrack = {
                                                viewModel.selectLayer(layer.id)
                                                viewModel.setInspectorTarget(InspectorTarget.LAYER)
                                                viewModel.clearKeyframeSelection()
                                            },
                                            onSelectKeyframe = { f ->
                                                viewModel.selectKeyframe(
                                                    SelectedKeyframeRef(
                                                        ownerType = KeyframeOwnerType.LAYER,
                                                        layerId = layer.id,
                                                        frame = f,
                                                    ),
                                                    movePlayhead = true,
                                                )
                                            },
                                            onJumpFrame = { f ->
                                                viewModel.stopPlayback()
                                                viewModel.setCurrentFrame(f)
                                                viewModel.selectLayer(layer.id)
                                                viewModel.setInspectorTarget(InspectorTarget.LAYER)
                                            },
                                        )
                                    },
                                )

                                if (layerExpanded) {
                                    val evalTransform = evaluated?.layers?.find { it.layer.id == layer.id }?.localTransform
                                    val channels = buildLayerChannelRows(
                                        layer = layer,
                                        compId = comp.id,
                                        currentFrame = state.currentFrame,
                                        selectedKeyframe = state.selectedKeyframe,
                                        evalX = evalTransform?.x ?: layer.transform.x,
                                        evalY = evalTransform?.y ?: layer.transform.y,
                                        evalRot = evalTransform?.rotationDeg ?: layer.transform.rotationDeg,
                                        evalScaleX = evalTransform?.scaleX ?: layer.transform.scaleX,
                                        evalScaleY = evalTransform?.scaleY ?: layer.transform.scaleY,
                                        evalOpacity = evalTransform?.opacity ?: layer.opacity,
                                        viewModel = viewModel,
                                    )
                                    channels.forEach { ch ->
                                        TimelineChannelSubRow(
                                            channel = ch,
                                            headerWidthDp = headerWidthDp,
                                            scrollState = timelineScrollState,
                                            contentWidthDp = trackContentWidthDp,
                                            frameWidthDp = effectiveFrameDp.dp,
                                            durationFrames = duration,
                                            majorInterval = majorInterval,
                                            currentFrame = state.currentFrame,
                                            textMeasurer = textMeasurer,
                                            onJumpFrame = { f ->
                                                viewModel.stopPlayback()
                                                viewModel.setCurrentFrame(f)
                                                viewModel.selectLayer(layer.id)
                                                viewModel.setInspectorTarget(InspectorTarget.LAYER)
                                                viewModel.clearKeyframeSelection()
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        val audioHeight = if (isCompact) 186.dp else 214.dp
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(audioHeight)
                                .background(StudioBg),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("AUDIO TRACKS & FRAME SYNC", style = MaterialTheme.typography.titleSmall, color = StudioTextSecondary)
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(StudioSecondary.copy(alpha = 0.2f))
                                        .border(1.dp, StudioSecondary.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                                        .clickable { audioPicker.launch(arrayOf("audio/*")) }
                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                        .testTag("timeline_import_audio"),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, tint = StudioSecondary, modifier = Modifier.size(14.dp))
                                    Text("Import Audio", style = MaterialTheme.typography.labelSmall, color = StudioSecondary)
                                }
                            }
                            HorizontalDivider(color = StudioBorder)

                            if (comp.audioTracks.isEmpty()) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("No audio tracks yet. Import an MP3 or WAV track to sync playback.", style = MaterialTheme.typography.bodySmall, color = StudioTextSecondary)
                                }
                            } else {
                                LazyColumn(modifier = Modifier.fillMaxSize()) {
                                    items(comp.audioTracks, key = { it.id }) { track ->
                                        TimelineAudioTrackRow(
                                            track = track,
                                            assets = state.project.assets,
                                            headerWidthDp = headerWidthDp,
                                            scrollState = timelineScrollState,
                                            contentWidthDp = trackContentWidthDp,
                                            frameWidthDp = effectiveFrameDp.dp,
                                            durationFrames = duration,
                                            majorInterval = majorInterval,
                                            currentFrame = state.currentFrame,
                                            textMeasurer = textMeasurer,
                                            onToggleMute = {
                                                viewModel.dispatchCommand(ProjectCommands.updateAudioTrack(comp.id, track.id) { it.copy(muted = !it.muted) })
                                            },
                                            onToggleSolo = {
                                                viewModel.dispatchCommand(ProjectCommands.updateAudioTrack(comp.id, track.id) { it.copy(solo = !it.solo) })
                                            },
                                            onDeleteTrack = {
                                                viewModel.dispatchCommand(ProjectCommands.deleteAudioTrack(comp.id, track.id))
                                            },
                                            onJumpFrame = { f ->
                                                viewModel.stopPlayback()
                                                viewModel.setCurrentFrame(f)
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
