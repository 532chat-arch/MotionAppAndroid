package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.schema.CameraAnimatableProp
import com.example.schema.CameraKeyframes
import com.example.schema.InterpolationType
import com.example.schema.Keyframe
import com.example.schema.LayerAnimatableProp
import com.example.schema.LayerKeyframes
import com.example.ui.theme.StudioAmber
import com.example.ui.theme.StudioBg
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioCyan
import com.example.ui.theme.StudioInsetField
import com.example.ui.theme.StudioMonoStyle
import com.example.ui.theme.StudioPanel
import com.example.ui.theme.StudioPrimary
import com.example.ui.theme.StudioPrimaryBright
import com.example.ui.theme.StudioRose
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.StudioTextMuted
import com.example.ui.theme.StudioTextPrimary
import com.example.ui.theme.StudioTextSecondary
import com.example.viewmodel.InspectorTarget
import com.example.viewmodel.KeyframeOwnerType
import com.example.viewmodel.SelectedKeyframeRef
import com.example.viewmodel.StudioUiState
import com.example.viewmodel.StudioViewModel
import kotlin.math.max

internal fun layerPropDisplayName(prop: LayerAnimatableProp): String = when (prop) {
    LayerAnimatableProp.X -> "Position X"
    LayerAnimatableProp.Y -> "Position Y"
    LayerAnimatableProp.ROTATION -> "Rotation"
    LayerAnimatableProp.SCALE_X -> "Scale X"
    LayerAnimatableProp.SCALE_Y -> "Scale Y"
    LayerAnimatableProp.OPACITY -> "Opacity"
}

internal fun cameraPropDisplayName(prop: CameraAnimatableProp): String = when (prop) {
    CameraAnimatableProp.X -> "Camera X"
    CameraAnimatableProp.Y -> "Camera Y"
    CameraAnimatableProp.ZOOM -> "Camera Zoom"
}

internal fun interpolationShortLabel(type: InterpolationType): String = when (type) {
    InterpolationType.LINEAR -> "Linear"
    InterpolationType.EASE_IN -> "Ease In"
    InterpolationType.EASE_OUT -> "Ease Out"
    InterpolationType.EASE_IN_OUT -> "In-Out"
    InterpolationType.HOLD -> "Hold"
}

private fun LayerKeyframes.channelFor(prop: LayerAnimatableProp): List<Keyframe> = when (prop) {
    LayerAnimatableProp.X -> x
    LayerAnimatableProp.Y -> y
    LayerAnimatableProp.ROTATION -> rotation
    LayerAnimatableProp.SCALE_X -> scaleX
    LayerAnimatableProp.SCALE_Y -> scaleY
    LayerAnimatableProp.OPACITY -> opacity
}

private fun CameraKeyframes.channelFor(prop: CameraAnimatableProp): List<Keyframe> = when (prop) {
    CameraAnimatableProp.X -> x
    CameraAnimatableProp.Y -> y
    CameraAnimatableProp.ZOOM -> zoom
}

/**
 * Contextual, touch-friendly Keyframe Action Bar shown whenever a keyframe or
 * animation path point is selected. Synchronized with Timeline, Stage, and Inspector.
 */
@Composable
fun KeyframeContextActionBar(
    state: StudioUiState,
    viewModel: StudioViewModel,
    modifier: Modifier = Modifier,
) {
    val selectedRef = state.selectedKeyframe
    var showEditDialog by remember(selectedRef) { mutableStateOf(false) }
    var showInterpMenu by remember(selectedRef) { mutableStateOf(false) }
    var showClearMenu by remember(selectedRef) { mutableStateOf(false) }

    AnimatedVisibility(
        visible = selectedRef != null,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier.fillMaxWidth(),
    ) {
        if (selectedRef == null) return@AnimatedVisibility
        val comp = state.activeComposition
        val layer = if (selectedRef.ownerType == KeyframeOwnerType.LAYER) {
            comp.layers.find { it.id == selectedRef.layerId }
        } else null

        val ownerLabel = when (selectedRef.ownerType) {
            KeyframeOwnerType.LAYER -> layer?.name ?: "Layer"
            KeyframeOwnerType.CAMERA -> "2D Camera"
        }
        val scopeLabel = when {
            selectedRef.ownerType == KeyframeOwnerType.LAYER && selectedRef.layerProp != null ->
                layerPropDisplayName(selectedRef.layerProp)
            selectedRef.ownerType == KeyframeOwnerType.LAYER && selectedRef.fromMotionPath ->
                "Path (X, Y)"
            selectedRef.ownerType == KeyframeOwnerType.CAMERA && selectedRef.cameraProp != null ->
                cameraPropDisplayName(selectedRef.cameraProp)
            else -> "All Keyed"
        }

        val activeInterp: InterpolationType = remember(comp, selectedRef) {
            when (selectedRef.ownerType) {
                KeyframeOwnerType.LAYER -> {
                    if (layer == null) InterpolationType.EASE_IN_OUT
                    else {
                        val props = when {
                            selectedRef.layerProp != null -> listOf(selectedRef.layerProp)
                            selectedRef.fromMotionPath -> listOf(LayerAnimatableProp.X, LayerAnimatableProp.Y)
                            else -> LayerAnimatableProp.entries
                        }
                        props.firstNotNullOfOrNull { p ->
                            layer.keyframes.channelFor(p).find { it.frame == selectedRef.frame }?.interpolation
                        } ?: InterpolationType.EASE_IN_OUT
                    }
                }
                KeyframeOwnerType.CAMERA -> {
                    val props = selectedRef.cameraProp?.let { listOf(it) } ?: CameraAnimatableProp.entries
                    props.firstNotNullOfOrNull { p ->
                        comp.camera.keyframes.channelFor(p).find { it.frame == selectedRef.frame }?.interpolation
                    } ?: InterpolationType.EASE_IN_OUT
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(StudioSurfaceElevated)
                .testTag("keyframe_context_bar"),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // Selected Keyframe Identity Chip
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(StudioPrimary.copy(alpha = 0.18f))
                        .border(1.dp, StudioPrimary.copy(alpha = 0.65f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("◆", style = StudioMonoStyle, color = StudioPrimaryBright)
                    Text(
                        text = "$ownerLabel · $scopeLabel",
                        style = MaterialTheme.typography.labelMedium,
                        color = StudioTextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "F${selectedRef.frame}",
                        style = StudioMonoStyle,
                        color = StudioPrimaryBright,
                    )
                }

                // 1. Edit Keyframe Action
                ContextActionButton(
                    icon = Icons.Default.Edit,
                    label = stringResource(R.string.kf_action_edit),
                    accent = StudioPrimaryBright,
                    tag = "kf_ctx_edit",
                    onClick = { showEditDialog = true },
                )

                // 2. Duplicate Keyframe Action
                ContextActionButton(
                    icon = Icons.Default.ContentCopy,
                    label = stringResource(R.string.kf_action_duplicate),
                    accent = StudioCyan,
                    tag = "kf_ctx_duplicate",
                    onClick = { viewModel.duplicateSelectedKeyframe() },
                )

                // 3. Interpolation Picker Action
                Box {
                    ContextActionButton(
                        icon = Icons.Default.Timeline,
                        label = interpolationShortLabel(activeInterp),
                        accent = easingColor(activeInterp),
                        tag = "kf_ctx_interpolation",
                        onClick = { showInterpMenu = true },
                    )
                    DropdownMenu(
                        expanded = showInterpMenu,
                        onDismissRequest = { showInterpMenu = false },
                        modifier = Modifier
                            .background(StudioSurfaceElevated)
                            .border(1.dp, StudioBorder, RoundedCornerShape(8.dp)),
                    ) {
                        InterpolationType.entries.forEach { mode ->
                            val isSelected = mode == activeInterp
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(easingColor(mode)),
                                        )
                                        Text(
                                            text = interpolationShortLabel(mode),
                                            color = if (isSelected) StudioPrimaryBright else StudioTextPrimary,
                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        )
                                    }
                                },
                                trailingIcon = if (isSelected) {
                                    { Icon(Icons.Default.Check, contentDescription = null, tint = StudioPrimaryBright, modifier = Modifier.size(16.dp)) }
                                } else null,
                                onClick = {
                                    showInterpMenu = false
                                    viewModel.setSelectedKeyframeInterpolation(mode)
                                },
                                modifier = Modifier.testTag("kf_interp_${mode.name.lowercase()}"),
                            )
                        }
                    }
                }

                // 4. Delete Selected Keyframe Action
                ContextActionButton(
                    icon = Icons.Default.Delete,
                    label = stringResource(R.string.kf_action_delete),
                    accent = StudioRose,
                    tag = "kf_ctx_delete",
                    onClick = { viewModel.deleteSelectedKeyframe() },
                )

                // 5. Clear Property / Clear All Animation Menu
                Box {
                    ContextActionButton(
                        icon = Icons.Default.DeleteSweep,
                        label = stringResource(R.string.kf_action_clear),
                        accent = StudioAmber,
                        tag = "kf_ctx_clear_menu",
                        onClick = { showClearMenu = true },
                    )
                    ClearAnimationDropdownMenu(
                        expanded = showClearMenu,
                        onDismiss = { showClearMenu = false },
                        state = state,
                        viewModel = viewModel,
                        focusedRef = selectedRef,
                    )
                }

                // Dismiss Selection
                IconButton(
                    onClick = { viewModel.clearKeyframeSelection() },
                    modifier = Modifier
                        .size(40.dp)
                        .minimumInteractiveComponentSize()
                        .testTag("kf_ctx_close"),
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.action_close),
                        tint = StudioTextSecondary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            HorizontalDivider(thickness = 1.dp, color = StudioPrimary.copy(alpha = 0.4f))
        }

        if (showEditDialog) {
            EditKeyframeDialog(
                state = state,
                selectedRef = selectedRef,
                onDismiss = { showEditDialog = false },
                onSave = { newFrame, layerVals, camVals, interp ->
                    viewModel.editSelectedKeyframe(
                        newFrame = newFrame,
                        newLayerValues = layerVals,
                        newCameraValues = camVals,
                        newInterpolation = interp,
                    )
                    showEditDialog = false
                },
            )
        }
    }
}

/**
 * Touch-friendly Toolbar Button providing instant access to Delete Current Keyframe,
 * Clear Property Animation, and Clear All Animation on Selected Object (without deleting it).
 */
@Composable
fun AnimationClearMenuButton(
    state: StudioUiState,
    viewModel: StudioViewModel,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val comp = state.activeComposition
    val selectedLayer = comp.layers.find { it.id == state.selectedLayerId }
    val hasAnimation = remember(comp, selectedLayer, state.inspectorTarget) {
        if (state.inspectorTarget == InspectorTarget.CAMERA) {
            comp.camera.keyframes.x.isNotEmpty() ||
                comp.camera.keyframes.y.isNotEmpty() ||
                comp.camera.keyframes.zoom.isNotEmpty() ||
                comp.camera.modifiers.isNotEmpty()
        } else if (selectedLayer != null) {
            layerKeyframeFrames(selectedLayer).isNotEmpty() || selectedLayer.modifiers.isNotEmpty()
        } else false
    }

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .minimumInteractiveComponentSize()
                .clip(RoundedCornerShape(4.dp))
                .background(if (expanded) StudioRose.copy(alpha = 0.22f) else StudioInsetField)
                .border(
                    1.dp,
                    if (expanded) StudioRose else StudioBorder,
                    RoundedCornerShape(4.dp),
                )
                .clickable(enabled = hasAnimation) { expanded = true }
                .testTag("timeline_clear_animation_btn"),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.DeleteSweep,
                contentDescription = stringResource(R.string.kf_clear_menu_desc),
                tint = if (hasAnimation) StudioRose else StudioTextMuted.copy(alpha = 0.4f),
                modifier = Modifier.size(17.dp),
            )
        }

        ClearAnimationDropdownMenu(
            expanded = expanded,
            onDismiss = { expanded = false },
            state = state,
            viewModel = viewModel,
            focusedRef = state.selectedKeyframe,
        )
    }
}

@Composable
private fun ClearAnimationDropdownMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    state: StudioUiState,
    viewModel: StudioViewModel,
    focusedRef: SelectedKeyframeRef?,
) {
    val comp = state.activeComposition
    val isCamera = focusedRef?.ownerType == KeyframeOwnerType.CAMERA ||
        (focusedRef == null && state.inspectorTarget == InspectorTarget.CAMERA)
    val layer = if (!isCamera) {
        val targetId = focusedRef?.layerId ?: state.selectedLayerId
        comp.layers.find { it.id == targetId }
    } else null

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier
            .background(StudioSurfaceElevated)
            .border(1.dp, StudioBorder, RoundedCornerShape(8.dp)),
    ) {
        if (focusedRef != null) {
            DropdownMenuItem(
                text = {
                    Text(
                        "${stringResource(R.string.kf_clear_selected)} (F${focusedRef.frame})",
                        color = StudioRose,
                        fontWeight = FontWeight.Medium,
                    )
                },
                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = StudioRose, modifier = Modifier.size(18.dp)) },
                onClick = {
                    onDismiss()
                    viewModel.deleteSelectedKeyframe()
                },
                modifier = Modifier.testTag("menu_delete_selected_kf"),
            )
            HorizontalDivider(color = StudioBorder)
        }

        if (isCamera) {
            val ckf = comp.camera.keyframes
            val animatedCamProps = CameraAnimatableProp.entries.filter { ckf.channelFor(it).isNotEmpty() }
            animatedCamProps.forEach { prop ->
                DropdownMenuItem(
                    text = { Text("Clear ${cameraPropDisplayName(prop)}", color = StudioTextPrimary) },
                    onClick = {
                        onDismiss()
                        viewModel.clearCameraPropertyAnimation(prop)
                    },
                    modifier = Modifier.testTag("menu_clear_cam_${prop.key}"),
                )
            }
            if (animatedCamProps.isNotEmpty() || comp.camera.modifiers.isNotEmpty()) {
                if (animatedCamProps.isNotEmpty()) HorizontalDivider(color = StudioBorder)
                DropdownMenuItem(
                    text = {
                        Column {
                            Text("Clear All Camera Animation", color = StudioRose, fontWeight = FontWeight.SemiBold)
                            Text("Keeps current camera framing", style = MaterialTheme.typography.labelSmall, color = StudioTextMuted)
                        }
                    },
                    leadingIcon = { Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = StudioRose, modifier = Modifier.size(18.dp)) },
                    onClick = {
                        onDismiss()
                        viewModel.clearAllCameraAnimation()
                    },
                    modifier = Modifier.testTag("menu_clear_all_cam_anim"),
                )
            }
        } else if (layer != null) {
            val animatedProps = LayerAnimatableProp.entries.filter { layer.keyframes.channelFor(it).isNotEmpty() }
            val focusedProp = focusedRef?.layerProp
            val orderedProps = if (focusedProp != null && focusedProp in animatedProps) {
                listOf(focusedProp) + animatedProps.filterNot { it == focusedProp }
            } else animatedProps

            orderedProps.forEach { prop ->
                val count = layer.keyframes.channelFor(prop).size
                DropdownMenuItem(
                    text = { Text("Clear ${layerPropDisplayName(prop)} ($count)", color = StudioTextPrimary) },
                    onClick = {
                        onDismiss()
                        viewModel.clearLayerPropertyAnimation(layer.id, prop)
                    },
                    modifier = Modifier.testTag("menu_clear_prop_${prop.key}"),
                )
            }
            if (orderedProps.isNotEmpty() || layer.modifiers.isNotEmpty()) {
                if (orderedProps.isNotEmpty()) HorizontalDivider(color = StudioBorder)
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(
                                text = "Clear All on \"${layer.name}\"",
                                color = StudioRose,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = stringResource(R.string.kf_clear_all_hint),
                                style = MaterialTheme.typography.labelSmall,
                                color = StudioTextMuted,
                            )
                        }
                    },
                    leadingIcon = { Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = StudioRose, modifier = Modifier.size(18.dp)) },
                    onClick = {
                        onDismiss()
                        viewModel.clearAllLayerAnimation(layer.id)
                    },
                    modifier = Modifier.testTag("menu_clear_all_layer_anim"),
                )
            }
        }
    }
}

@Composable
private fun ContextActionButton(
    icon: ImageVector,
    label: String,
    accent: Color,
    tag: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .height(38.dp)
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(6.dp))
            .background(StudioPanel)
            .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(icon, contentDescription = label, tint = accent, modifier = Modifier.size(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = StudioTextPrimary,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditKeyframeDialog(
    state: StudioUiState,
    selectedRef: SelectedKeyframeRef,
    onDismiss: () -> Unit,
    onSave: (
        newFrame: Int,
        layerValues: Map<LayerAnimatableProp, Double>,
        cameraValues: Map<CameraAnimatableProp, Double>,
        interpolation: InterpolationType,
    ) -> Unit,
) {
    val comp = state.activeComposition
    val maxFrame = max(0, comp.settings.durationFrames - 1)
    var editedFrame by remember(selectedRef) { mutableIntStateOf(selectedRef.frame.coerceIn(0, maxFrame)) }

    val layer = if (selectedRef.ownerType == KeyframeOwnerType.LAYER) {
        comp.layers.find { it.id == selectedRef.layerId }
    } else null

    val initialLayerProps: Map<LayerAnimatableProp, Keyframe> = remember(comp, selectedRef) {
        if (selectedRef.ownerType != KeyframeOwnerType.LAYER || layer == null) emptyMap()
        else {
            val props = when {
                selectedRef.layerProp != null -> listOf(selectedRef.layerProp)
                selectedRef.fromMotionPath -> listOf(LayerAnimatableProp.X, LayerAnimatableProp.Y)
                else -> LayerAnimatableProp.entries
            }
            props.mapNotNull { p ->
                layer.keyframes.channelFor(p).find { it.frame == selectedRef.frame }?.let { p to it }
            }.toMap()
        }
    }

    val initialCamProps: Map<CameraAnimatableProp, Keyframe> = remember(comp, selectedRef) {
        if (selectedRef.ownerType != KeyframeOwnerType.CAMERA) emptyMap()
        else {
            val props = selectedRef.cameraProp?.let { listOf(it) } ?: CameraAnimatableProp.entries
            props.mapNotNull { p ->
                comp.camera.keyframes.channelFor(p).find { it.frame == selectedRef.frame }?.let { p to it }
            }.toMap()
        }
    }

    val layerValues = remember(selectedRef) {
        mutableStateMapOf<LayerAnimatableProp, Double>().apply {
            initialLayerProps.forEach { (p, kf) -> put(p, kf.value) }
        }
    }
    val camValues = remember(selectedRef) {
        mutableStateMapOf<CameraAnimatableProp, Double>().apply {
            initialCamProps.forEach { (p, kf) -> put(p, kf.value) }
        }
    }
    var selectedInterp by remember(selectedRef) {
        mutableStateOf(
            initialLayerProps.values.firstOrNull()?.interpolation
                ?: initialCamProps.values.firstOrNull()?.interpolation
                ?: InterpolationType.EASE_IN_OUT
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = StudioSurfaceElevated,
        shape = RoundedCornerShape(12.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("◆", style = StudioMonoStyle, color = StudioPrimaryBright)
                Text(
                    text = stringResource(R.string.kf_edit_dialog_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = StudioTextPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Touch-friendly Frame Position Stepper
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "${stringResource(R.string.kf_frame_label)} (0 – $maxFrame)",
                        style = MaterialTheme.typography.labelMedium,
                        color = StudioTextSecondary,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        FrameStepChip("-5f") { editedFrame = (editedFrame - 5).coerceIn(0, maxFrame) }
                        FrameStepChip("-1f") { editedFrame = (editedFrame - 1).coerceIn(0, maxFrame) }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(StudioInsetField)
                                .border(1.dp, StudioPrimary.copy(alpha = 0.6f), RoundedCornerShape(6.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "F$editedFrame",
                                style = StudioMonoStyle.copy(fontSize = 14.sp),
                                color = StudioPrimaryBright,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        FrameStepChip("+1f") { editedFrame = (editedFrame + 1).coerceIn(0, maxFrame) }
                        FrameStepChip("+5f") { editedFrame = (editedFrame + 5).coerceIn(0, maxFrame) }
                    }
                }

                // Editable Value Scrubbers for each affected property
                if (layerValues.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        LayerAnimatableProp.entries.filter { it in layerValues }.forEach { prop ->
                            val v = layerValues[prop] ?: 0.0
                            when (prop) {
                                LayerAnimatableProp.X -> NumberScrubber(
                                    label = "Position X", value = v, step = 10.0, suffix = "px", decimals = 0,
                                    onChange = { layerValues[prop] = it },
                                )
                                LayerAnimatableProp.Y -> NumberScrubber(
                                    label = "Position Y", value = v, step = 10.0, suffix = "px", decimals = 0,
                                    onChange = { layerValues[prop] = it },
                                )
                                LayerAnimatableProp.ROTATION -> NumberScrubber(
                                    label = "Rotation", value = v, step = 5.0, suffix = "°", decimals = 1,
                                    onChange = { layerValues[prop] = it },
                                )
                                LayerAnimatableProp.SCALE_X -> NumberScrubber(
                                    label = "Scale X", value = v, step = 0.05, min = 0.05, max = 6.0, decimals = 2,
                                    onChange = { layerValues[prop] = it },
                                )
                                LayerAnimatableProp.SCALE_Y -> NumberScrubber(
                                    label = "Scale Y", value = v, step = 0.05, min = 0.05, max = 6.0, decimals = 2,
                                    onChange = { layerValues[prop] = it },
                                )
                                LayerAnimatableProp.OPACITY -> NumberScrubber(
                                    label = "Opacity", value = v * 100.0, step = 5.0, min = 0.0, max = 100.0, suffix = "%", decimals = 0,
                                    onChange = { layerValues[prop] = (it / 100.0).coerceIn(0.0, 1.0) },
                                )
                            }
                        }
                    }
                }

                if (camValues.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        CameraAnimatableProp.entries.filter { it in camValues }.forEach { prop ->
                            val v = camValues[prop] ?: 0.0
                            when (prop) {
                                CameraAnimatableProp.X -> NumberScrubber(
                                    label = "Camera X", value = v, step = 20.0, decimals = 0,
                                    onChange = { camValues[prop] = it },
                                )
                                CameraAnimatableProp.Y -> NumberScrubber(
                                    label = "Camera Y", value = v, step = 20.0, decimals = 0,
                                    onChange = { camValues[prop] = it },
                                )
                                CameraAnimatableProp.ZOOM -> NumberScrubber(
                                    label = "Camera Zoom", value = v, step = 0.05, min = 0.25, max = 4.0, decimals = 2,
                                    onChange = { camValues[prop] = it },
                                )
                            }
                        }
                    }
                }

                // Interpolation Selector
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(R.string.kf_interpolation_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = StudioTextSecondary,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        InterpolationType.entries.forEach { mode ->
                            val active = mode == selectedInterp
                            val color = easingColor(mode)
                            Box(
                                modifier = Modifier
                                    .height(38.dp)
                                    .minimumInteractiveComponentSize()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (active) color.copy(alpha = 0.24f) else StudioPanel)
                                    .border(1.dp, if (active) color else StudioBorder, RoundedCornerShape(6.dp))
                                    .clickable { selectedInterp = mode }
                                    .padding(horizontal = 10.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = interpolationShortLabel(mode),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (active) color else StudioTextSecondary,
                                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(editedFrame, layerValues.toMap(), camValues.toMap(), selectedInterp) },
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = StudioPrimary, contentColor = Color.White),
                modifier = Modifier.height(44.dp).testTag("kf_edit_save_btn"),
            ) {
                Text(stringResource(R.string.action_apply), fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.height(44.dp),
            ) {
                Text(stringResource(R.string.action_cancel), color = StudioTextSecondary)
            }
        },
    )
}

@Composable
private fun FrameStepChip(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(42.dp)
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(6.dp))
            .background(StudioPanel)
            .border(1.dp, StudioBorder, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = StudioMonoStyle, color = StudioTextPrimary)
    }
}
