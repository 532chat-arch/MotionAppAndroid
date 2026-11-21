package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Launch
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.commands.ProjectCommands
import com.example.engine.AnimationPresetRegistry
import com.example.engine.EnvironmentPatch
import com.example.engine.PresetCategory
import com.example.engine.SKY_ATMOSPHERE_PRESETS
import com.example.engine.evaluateCompositionAtFrame
import com.example.schema.BlendModeType
import com.example.schema.CameraAnimatableProp
import com.example.schema.LayerAnimatableProp
import com.example.schema.SceneObjectDocument
import com.example.schema.ShapeType
import com.example.schema.WeatherType
import com.example.schema.WindDirection
import com.example.schema.getActiveCompFps
import com.example.ui.theme.StudioAmber
import com.example.ui.theme.StudioBg
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioCyan
import com.example.ui.theme.StudioEmerald
import com.example.ui.theme.StudioMonoStyle
import com.example.ui.theme.StudioPanel
import com.example.ui.theme.StudioPrimary
import com.example.ui.theme.StudioPrimaryBright
import com.example.ui.theme.StudioRose
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.StudioTextPrimary
import com.example.ui.theme.StudioTextSecondary
import com.example.viewmodel.InspectorTarget
import com.example.viewmodel.KeyframeOwnerType
import com.example.viewmodel.SelectedKeyframeRef
import com.example.viewmodel.StudioUiState
import com.example.viewmodel.StudioViewModel
import kotlin.math.roundToInt

@Composable
fun InspectorPanel(
    state: StudioUiState,
    viewModel: StudioViewModel,
    modifier: Modifier = Modifier,
) {
    val comp = state.activeComposition
    val fps = getActiveCompFps(state.project, comp.id)
    val evaluated = remember(state.project, comp.id, state.currentFrame) {
        evaluateCompositionAtFrame(state.project, comp.id, state.currentFrame)
    }
    val selectedLayer = comp.layers.find { it.id == state.selectedLayerId }
    val evalLayer = evaluated?.layers?.find { it.layer.id == state.selectedLayerId }
    val primaryObj = selectedLayer?.objects?.firstOrNull()

    var layerSubTab by remember { mutableStateOf("props") }
    var cameraSubTab by remember { mutableStateOf("controls") }
    var showLyricsModal by remember { mutableStateOf(false) }

    LaunchedEffect(state.selectedKeyframe) {
        val sel = state.selectedKeyframe ?: return@LaunchedEffect
        when (sel.ownerType) {
            KeyframeOwnerType.LAYER -> layerSubTab = "props"
            KeyframeOwnerType.CAMERA -> cameraSubTab = "controls"
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(StudioPanel),
    ) {
        // 4-Tab Target Header: Layer | Effects | Cam | World
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(StudioBg)
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            InspectorTabPill("Layer", state.inspectorTarget == InspectorTarget.LAYER, Modifier.weight(1f)) {
                viewModel.setInspectorTarget(InspectorTarget.LAYER)
            }
            InspectorTabPill("Effects", state.inspectorTarget == InspectorTarget.PRESETS, Modifier.weight(1f), StudioAmber) {
                viewModel.setInspectorTarget(InspectorTarget.PRESETS)
            }
            InspectorTabPill("Cam", state.inspectorTarget == InspectorTarget.CAMERA, Modifier.weight(1f), StudioCyan) {
                viewModel.setInspectorTarget(InspectorTarget.CAMERA)
            }
            InspectorTabPill("World", state.inspectorTarget == InspectorTarget.WORLD, Modifier.weight(1f), StudioEmerald) {
                viewModel.setInspectorTarget(InspectorTarget.WORLD)
            }
        }

        HorizontalDivider(color = StudioBorder)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when (state.inspectorTarget) {
                InspectorTarget.LAYER -> {
                    if (selectedLayer == null || evalLayer == null) {
                        Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            Text(
                                stringResource(R.string.inspector_select_layer_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = StudioTextSecondary,
                            )
                        }
                    } else {
                        // Layer Properties vs Effects Sub-tabs
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(StudioBg)
                                .padding(3.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            InspectorTabPill("Properties", layerSubTab == "props", Modifier.weight(1f)) { layerSubTab = "props" }
                            InspectorTabPill("Effects (${selectedLayer.modifiers.size})", layerSubTab == "motion", Modifier.weight(1f), StudioAmber) {
                                layerSubTab = "motion"
                            }
                        }

                        if (layerSubTab == "motion") {
                            ModularEffectStackSection(
                                compositionId = comp.id,
                                layerId = selectedLayer.id,
                                modifiers = selectedLayer.modifiers,
                                state = state,
                                viewModel = viewModel,
                                onBrowseEffects = { viewModel.setInspectorTarget(InspectorTarget.PRESETS) },
                            )
                            BakeModifiersSection(
                                compositionId = comp.id,
                                scope = "layer",
                                layerId = selectedLayer.id,
                                modifiers = selectedLayer.modifiers,
                                durationFrames = comp.settings.durationFrames,
                                viewModel = viewModel,
                            )
                        } else {
                            // Layer Identity
                            OutlinedTextField(
                                value = selectedLayer.name,
                                onValueChange = { n ->
                                    viewModel.dispatchCommand(ProjectCommands.updateLayer(comp.id, selectedLayer.id) { it.copy(name = n) })
                                },
                                label = { Text("Layer Name") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )

                            // Transform Scrubbers with Keyframe Diamonds
                            val lt = evalLayer.localTransform
                            val kf = selectedLayer.keyframes
                            val layerHasAnim = layerKeyframeFrames(selectedLayer).isNotEmpty() || selectedLayer.modifiers.isNotEmpty()
                            SectionHeader(
                                title = "Transform (F${state.currentFrame})",
                                trailing = if (layerHasAnim) {
                                    {
                                        Row(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(StudioRose.copy(alpha = 0.14f))
                                                .border(1.dp, StudioRose.copy(alpha = 0.45f), RoundedCornerShape(4.dp))
                                                .clickable { viewModel.clearAllLayerAnimation(selectedLayer.id) }
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                                .testTag("inspector_clear_all_layer_anim"),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        ) {
                                            Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = StudioRose, modifier = Modifier.size(13.dp))
                                            Text("Clear Anim", style = MaterialTheme.typography.labelSmall, color = StudioRose, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                } else null,
                            )

                            fun toggleKf(prop: LayerAnimatableProp, currentVal: Double) {
                                val list = when (prop) {
                                    LayerAnimatableProp.X -> kf.x
                                    LayerAnimatableProp.Y -> kf.y
                                    LayerAnimatableProp.ROTATION -> kf.rotation
                                    LayerAnimatableProp.SCALE_X -> kf.scaleX
                                    LayerAnimatableProp.SCALE_Y -> kf.scaleY
                                    LayerAnimatableProp.OPACITY -> kf.opacity
                                }
                                if (list.any { it.frame == state.currentFrame }) {
                                    viewModel.dispatchCommand(ProjectCommands.removeLayerKeyframe(comp.id, selectedLayer.id, prop, state.currentFrame))
                                } else {
                                    viewModel.dispatchCommand(ProjectCommands.setLayerKeyframe(comp.id, selectedLayer.id, prop, state.currentFrame, currentVal))
                                    viewModel.selectKeyframe(
                                        SelectedKeyframeRef(
                                            ownerType = KeyframeOwnerType.LAYER,
                                            layerId = selectedLayer.id,
                                            frame = state.currentFrame,
                                            layerProp = prop,
                                        ),
                                        movePlayhead = false,
                                    )
                                }
                            }

                            NumberScrubber(
                                label = "Position X", value = lt.x, step = 10.0, suffix = "px", decimals = 0,
                                hasKeyframeAtFrame = kf.x.any { it.frame == state.currentFrame },
                                hasAnyKeyframes = kf.x.isNotEmpty(),
                                onToggleKeyframe = { toggleKf(LayerAnimatableProp.X, lt.x) },
                                onChange = { viewModel.dispatchCommand(ProjectCommands.updateLayerTransformValue(comp.id, selectedLayer.id, state.currentFrame, x = it)) },
                            )
                            NumberScrubber(
                                label = "Position Y", value = lt.y, step = 10.0, suffix = "px", decimals = 0,
                                hasKeyframeAtFrame = kf.y.any { it.frame == state.currentFrame },
                                hasAnyKeyframes = kf.y.isNotEmpty(),
                                onToggleKeyframe = { toggleKf(LayerAnimatableProp.Y, lt.y) },
                                onChange = { viewModel.dispatchCommand(ProjectCommands.updateLayerTransformValue(comp.id, selectedLayer.id, state.currentFrame, y = it)) },
                            )
                            NumberScrubber(
                                label = "Rotation", value = lt.rotationDeg, step = 5.0, suffix = "°", decimals = 1,
                                hasKeyframeAtFrame = kf.rotation.any { it.frame == state.currentFrame },
                                hasAnyKeyframes = kf.rotation.isNotEmpty(),
                                onToggleKeyframe = { toggleKf(LayerAnimatableProp.ROTATION, lt.rotationDeg) },
                                onChange = { viewModel.dispatchCommand(ProjectCommands.updateLayerTransformValue(comp.id, selectedLayer.id, state.currentFrame, rotationDeg = it)) },
                            )
                            NumberScrubber(
                                label = "Scale X", value = lt.scaleX, step = 0.05, min = 0.05, max = 6.0, decimals = 2,
                                hasKeyframeAtFrame = kf.scaleX.any { it.frame == state.currentFrame },
                                hasAnyKeyframes = kf.scaleX.isNotEmpty(),
                                onToggleKeyframe = { toggleKf(LayerAnimatableProp.SCALE_X, lt.scaleX) },
                                onChange = { viewModel.dispatchCommand(ProjectCommands.updateLayerTransformValue(comp.id, selectedLayer.id, state.currentFrame, scaleX = it)) },
                            )
                            NumberScrubber(
                                label = "Scale Y", value = lt.scaleY, step = 0.05, min = 0.05, max = 6.0, decimals = 2,
                                hasKeyframeAtFrame = kf.scaleY.any { it.frame == state.currentFrame },
                                hasAnyKeyframes = kf.scaleY.isNotEmpty(),
                                onToggleKeyframe = { toggleKf(LayerAnimatableProp.SCALE_Y, lt.scaleY) },
                                onChange = { viewModel.dispatchCommand(ProjectCommands.updateLayerTransformValue(comp.id, selectedLayer.id, state.currentFrame, scaleY = it)) },
                            )
                            NumberScrubber(
                                label = "Opacity", value = lt.opacity * 100.0, step = 5.0, min = 0.0, max = 100.0, suffix = "%", decimals = 0,
                                hasKeyframeAtFrame = kf.opacity.any { it.frame == state.currentFrame },
                                hasAnyKeyframes = kf.opacity.isNotEmpty(),
                                onToggleKeyframe = { toggleKf(LayerAnimatableProp.OPACITY, lt.opacity) },
                                onChange = { viewModel.dispatchCommand(ProjectCommands.updateLayerTransformValue(comp.id, selectedLayer.id, state.currentFrame, opacity = it / 100.0)) },
                            )

                            // Parallax Depth & Blend Mode & Parenting
                            StudioSliderRow(
                                label = "Parallax Depth",
                                value = selectedLayer.depth.toFloat(),
                                valueRange = -1.5f..1.5f,
                                valueLabel = "%.2f".format(selectedLayer.depth),
                                onValueChange = { d ->
                                    viewModel.dispatchCommand(ProjectCommands.updateLayer(comp.id, selectedLayer.id) { it.copy(depth = d.toDouble()) })
                                },
                                accentColor = StudioCyan,
                            )

                            Text("Blend Mode", style = MaterialTheme.typography.labelSmall, color = StudioTextSecondary)
                            StudioChipRow(
                                options = BlendModeType.entries.map { it.name to it.name.lowercase().replaceFirstChar { c -> c.uppercase() } },
                                selectedKey = selectedLayer.blendMode.name,
                                onSelect = { k ->
                                    viewModel.dispatchCommand(ProjectCommands.updateLayer(comp.id, selectedLayer.id) { it.copy(blendMode = BlendModeType.valueOf(k)) })
                                },
                            )

                            Text("Parent Layer", style = MaterialTheme.typography.labelSmall, color = StudioTextSecondary)
                            val parentOptions = listOf("" to "None") + comp.layers.filter { it.id != selectedLayer.id }.map { it.id to it.name }
                            StudioChipRow(
                                options = parentOptions,
                                selectedKey = selectedLayer.parentId ?: "",
                                onSelect = { pid ->
                                    viewModel.dispatchCommand(ProjectCommands.setLayerParent(comp.id, selectedLayer.id, pid.ifEmpty { null }))
                                },
                            )

                            // Object-Specific Editors
                            when (primaryObj) {
                                is SceneObjectDocument.TextObject -> {
                                    HorizontalDivider(color = StudioBorder)
                                    SectionHeader("Typography & Lyrics")
                                    OutlinedTextField(
                                        value = primaryObj.text,
                                        onValueChange = { txt ->
                                            viewModel.dispatchCommand(ProjectCommands.updateObject(comp.id, selectedLayer.id, primaryObj.id) { o ->
                                                if (o is SceneObjectDocument.TextObject) o.copy(text = txt) else o
                                            })
                                        },
                                        label = { Text("Text Content") },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    NumberScrubber(
                                        label = "Font Size", value = primaryObj.fontSize, step = 4.0, min = 14.0, max = 180.0, suffix = "px", decimals = 0,
                                        onChange = { sz ->
                                            viewModel.dispatchCommand(ProjectCommands.updateObject(comp.id, selectedLayer.id, primaryObj.id) { o ->
                                                if (o is SceneObjectDocument.TextObject) o.copy(fontSize = sz) else o
                                            })
                                        },
                                    )
                                    ColorSwatchRow("Text Color", primaryObj.color, onSelect = { hex ->
                                        viewModel.dispatchCommand(ProjectCommands.updateObject(comp.id, selectedLayer.id, primaryObj.id) { o ->
                                            if (o is SceneObjectDocument.TextObject) o.copy(color = hex) else o
                                        })
                                    })
                                    Button(
                                        onClick = { showLyricsModal = true },
                                        modifier = Modifier.fillMaxWidth().testTag("open_lyrics_studio_button"),
                                    ) {
                                        Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.size(6.dp))
                                        Text(
                                            if (primaryObj.lyrics?.enabled == true) "Edit Timed Lyrics (${primaryObj.lyrics.cues.size} cues)"
                                            else "Open Lyrics & Karaoke Studio"
                                        )
                                    }
                                }
                                is SceneObjectDocument.ShapeObject -> {
                                    HorizontalDivider(color = StudioBorder)
                                    SectionHeader("Shape Geometry & Paint")
                                    StudioChipRow(
                                        options = ShapeType.entries.map { it.name to it.name.lowercase().replaceFirstChar { c -> c.uppercase() } },
                                        selectedKey = primaryObj.shapeType.name,
                                        onSelect = { k ->
                                            viewModel.dispatchCommand(ProjectCommands.updateObject(comp.id, selectedLayer.id, primaryObj.id) { o ->
                                                if (o is SceneObjectDocument.ShapeObject) o.copy(shapeType = ShapeType.valueOf(k)) else o
                                            })
                                        },
                                    )
                                    ColorSwatchRow("Fill Color", primaryObj.fill, onSelect = { hex ->
                                        viewModel.dispatchCommand(ProjectCommands.updateObject(comp.id, selectedLayer.id, primaryObj.id) { o ->
                                            if (o is SceneObjectDocument.ShapeObject) o.copy(fill = hex) else o
                                        })
                                    })
                                    ColorSwatchRow("Stroke Color", primaryObj.stroke, onSelect = { hex ->
                                        viewModel.dispatchCommand(ProjectCommands.updateObject(comp.id, selectedLayer.id, primaryObj.id) { o ->
                                            if (o is SceneObjectDocument.ShapeObject) o.copy(stroke = hex) else o
                                        })
                                    })
                                }
                                is SceneObjectDocument.SpriteObject -> {
                                    HorizontalDivider(color = StudioBorder)
                                    val spriteAsset = state.project.assets.find { it.id == primaryObj.assetId }
                                    SectionHeader(
                                        title = "Sprite & Cutout",
                                        trailing = if (spriteAsset?.originalDataUrl != null) {
                                            {
                                                Text(
                                                    text = stringResource(R.string.bg_remove_cutout_badge),
                                                    style = StudioMonoStyle,
                                                    color = StudioEmerald,
                                                )
                                            }
                                        } else null,
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text("Flip Horizontal", style = MaterialTheme.typography.bodySmall, color = StudioTextSecondary)
                                        Switch(
                                            checked = primaryObj.flipX,
                                            onCheckedChange = { flip ->
                                                viewModel.dispatchCommand(ProjectCommands.updateObject(comp.id, selectedLayer.id, primaryObj.id) { o ->
                                                    if (o is SceneObjectDocument.SpriteObject) o.copy(flipX = flip) else o
                                                })
                                            },
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text("Flip Vertical", style = MaterialTheme.typography.bodySmall, color = StudioTextSecondary)
                                        Switch(
                                            checked = primaryObj.flipY,
                                            onCheckedChange = { flip ->
                                                viewModel.dispatchCommand(ProjectCommands.updateObject(comp.id, selectedLayer.id, primaryObj.id) { o ->
                                                    if (o is SceneObjectDocument.SpriteObject) o.copy(flipY = flip) else o
                                                })
                                            },
                                        )
                                    }
                                    if (spriteAsset != null) {
                                        Button(
                                            onClick = { viewModel.openBackgroundRemoval(spriteAsset.id) },
                                            colors = ButtonDefaults.buttonColors(containerColor = StudioPrimary),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("inspector_remove_bg_button"),
                                        ) {
                                            Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.size(6.dp))
                                            Text(stringResource(R.string.bg_remove_button))
                                        }
                                        if (spriteAsset.originalDataUrl != null) {
                                            OutlinedButton(
                                                onClick = { viewModel.restoreSpriteOriginalBackground(spriteAsset.id) },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .testTag("inspector_restore_bg_button"),
                                            ) {
                                                Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.size(6.dp))
                                                Text(stringResource(R.string.bg_remove_restore))
                                            }
                                        }
                                    }
                                }
                                is SceneObjectDocument.CharacterObject -> {
                                    HorizontalDivider(color = StudioBorder)
                                    val charDef = state.project.characters.find { it.id == primaryObj.characterId }
                                    val charSheetAsset = charDef?.let { cd -> state.project.assets.find { it.id == cd.assetId } }
                                    SectionHeader("Character Clip")
                                    if (charDef != null && charDef.clips.isNotEmpty()) {
                                        StudioChipRow(
                                            options = charDef.clips.map { it.id to it.name },
                                            selectedKey = primaryObj.activeClipId,
                                            onSelect = { cid ->
                                                viewModel.dispatchCommand(ProjectCommands.updateObject(comp.id, selectedLayer.id, primaryObj.id) { o ->
                                                    if (o is SceneObjectDocument.CharacterObject) o.copy(activeClipId = cid) else o
                                                })
                                            },
                                            accent = StudioAmber,
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text("Flip Horizontal", style = MaterialTheme.typography.bodySmall, color = StudioTextSecondary)
                                        Switch(
                                            checked = primaryObj.flipX,
                                            onCheckedChange = { flip ->
                                                viewModel.dispatchCommand(ProjectCommands.updateObject(comp.id, selectedLayer.id, primaryObj.id) { o ->
                                                    if (o is SceneObjectDocument.CharacterObject) o.copy(flipX = flip) else o
                                                })
                                            },
                                        )
                                    }
                                    if (charSheetAsset != null) {
                                        OutlinedButton(
                                            onClick = { viewModel.openBackgroundRemoval(charSheetAsset.id) },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("inspector_char_remove_bg_button"),
                                        ) {
                                            Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.size(6.dp))
                                            Text(stringResource(R.string.bg_remove_sheet_button))
                                        }
                                    }
                                }
                                is SceneObjectDocument.NestedCompositionObject -> {
                                    HorizontalDivider(color = StudioBorder)
                                    SectionHeader("Nested Composition")
                                    Button(
                                        onClick = { viewModel.setActiveComposition(primaryObj.compositionId) },
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Icon(Icons.Default.Launch, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.size(6.dp))
                                        Text("Open Nested Composition")
                                    }
                                    NumberScrubber(
                                        label = "Time Offset", value = primaryObj.timeOffsetFrames.toDouble(), step = 6.0, suffix = "f", decimals = 0,
                                        onChange = { off ->
                                            viewModel.dispatchCommand(ProjectCommands.updateObject(comp.id, selectedLayer.id, primaryObj.id) { o ->
                                                if (o is SceneObjectDocument.NestedCompositionObject) o.copy(timeOffsetFrames = off.roundToInt()) else o
                                            })
                                        },
                                    )
                                }
                                else -> Unit
                            }

                            // Keyframes & Curve Editor
                            val selectedLayerKf = state.selectedKeyframe?.takeIf {
                                it.ownerType == KeyframeOwnerType.LAYER && it.layerId == selectedLayer.id
                            }
                            KeyframeManagerSection(
                                title = "Layer Keyframes",
                                channels = listOf(
                                    KeyframeChannelUi("x", "Position X", "X", kf.x, lt.x, "px"),
                                    KeyframeChannelUi("y", "Position Y", "Y", kf.y, lt.y, "px"),
                                    KeyframeChannelUi("rotation", "Rotation", "Rot", kf.rotation, lt.rotationDeg, "°"),
                                    KeyframeChannelUi("scaleX", "Scale X", "ScX", kf.scaleX, lt.scaleX),
                                    KeyframeChannelUi("scaleY", "Scale Y", "ScY", kf.scaleY, lt.scaleY),
                                    KeyframeChannelUi("opacity", "Opacity", "Op", kf.opacity, lt.opacity),
                                ),
                                currentFrame = state.currentFrame,
                                durationFrames = comp.settings.durationFrames,
                                selectedChannelKey = selectedLayerKf?.layerProp?.key,
                                selectedKeyframeFrame = selectedLayerKf?.frame,
                                hasModifiers = selectedLayer.modifiers.isNotEmpty(),
                                onJumpToFrame = { viewModel.setCurrentFrame(it) },
                                onSelectKeyframe = { ch, f ->
                                    val prop = LayerAnimatableProp.fromKey(ch)
                                    viewModel.selectKeyframe(
                                        SelectedKeyframeRef(
                                            ownerType = KeyframeOwnerType.LAYER,
                                            layerId = selectedLayer.id,
                                            frame = f,
                                            layerProp = prop,
                                        ),
                                        movePlayhead = true,
                                    )
                                },
                                onSetKeyframe = { ch, f, v, interp ->
                                    LayerAnimatableProp.fromKey(ch)?.let { prop ->
                                        viewModel.dispatchCommand(ProjectCommands.setLayerKeyframe(comp.id, selectedLayer.id, prop, f, v, interp))
                                        viewModel.selectKeyframe(
                                            SelectedKeyframeRef(
                                                ownerType = KeyframeOwnerType.LAYER,
                                                layerId = selectedLayer.id,
                                                frame = f,
                                                layerProp = prop,
                                            ),
                                            movePlayhead = false,
                                        )
                                    }
                                },
                                onUpdateEasing = { ch, kfId, interp ->
                                    LayerAnimatableProp.fromKey(ch)?.let { prop ->
                                        viewModel.dispatchCommand(ProjectCommands.updateLayerKeyframe(comp.id, selectedLayer.id, prop, kfId, newInterp = interp))
                                    }
                                },
                                onDuplicateKeyframe = { ch, f ->
                                    val prop = LayerAnimatableProp.fromKey(ch)
                                    viewModel.selectKeyframe(
                                        SelectedKeyframeRef(
                                            ownerType = KeyframeOwnerType.LAYER,
                                            layerId = selectedLayer.id,
                                            frame = f,
                                            layerProp = prop,
                                        ),
                                        movePlayhead = false,
                                    )
                                    viewModel.duplicateSelectedKeyframe()
                                },
                                onDeleteKeyframe = { ch, f ->
                                    LayerAnimatableProp.fromKey(ch)?.let { prop ->
                                        viewModel.dispatchCommand(ProjectCommands.removeLayerKeyframe(comp.id, selectedLayer.id, prop, f))
                                    }
                                },
                                onClearChannel = { ch ->
                                    LayerAnimatableProp.fromKey(ch)?.let { prop ->
                                        viewModel.clearLayerPropertyAnimation(selectedLayer.id, prop)
                                    }
                                },
                                onClearAllAnimation = {
                                    viewModel.clearAllLayerAnimation(selectedLayer.id)
                                },
                            )
                        }
                    }
                }

                InspectorTarget.PRESETS -> {
                    EffectsLibraryBrowser(
                        state = state,
                        viewModel = viewModel,
                        layerId = state.selectedLayerId,
                    )
                }

                InspectorTarget.CAMERA -> {
                    val camEval = evaluated?.camera
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(StudioBg)
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        InspectorTabPill("Camera & KF", cameraSubTab == "controls", Modifier.weight(1f), StudioCyan) { cameraSubTab = "controls" }
                        InspectorTabPill("Presets (${comp.camera.modifiers.size})", cameraSubTab == "presets", Modifier.weight(1f), StudioAmber) {
                            cameraSubTab = "presets"
                        }
                    }

                    if (cameraSubTab == "presets") {
                        Text("Follow Target Layer", style = MaterialTheme.typography.labelSmall, color = StudioTextSecondary)
                        StudioChipRow(
                            options = listOf("" to "Free Camera") + comp.layers.map { it.id to it.name },
                            selectedKey = comp.camera.followLayerId ?: "",
                            onSelect = { fid ->
                                viewModel.dispatchCommand(ProjectCommands.updateCamera(comp.id) { it.copy(followLayerId = fid.ifEmpty { null }) })
                            },
                            accent = StudioCyan,
                        )
                        MotionPresetsBrowser(state = state, viewModel = viewModel, scope = "camera", layerId = null)
                    } else if (camEval != null) {
                        val ckf = comp.camera.keyframes
                        val camHasAnim = ckf.x.isNotEmpty() || ckf.y.isNotEmpty() || ckf.zoom.isNotEmpty() || comp.camera.modifiers.isNotEmpty()
                        SectionHeader(
                            title = "Camera (F${state.currentFrame})",
                            trailing = if (camHasAnim) {
                                {
                                    Row(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(StudioRose.copy(alpha = 0.14f))
                                            .border(1.dp, StudioRose.copy(alpha = 0.45f), RoundedCornerShape(4.dp))
                                            .clickable { viewModel.clearAllCameraAnimation() }
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                            .testTag("inspector_clear_all_cam_anim"),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = StudioRose, modifier = Modifier.size(13.dp))
                                        Text("Clear Anim", style = MaterialTheme.typography.labelSmall, color = StudioRose, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            } else null,
                        )
                        NumberScrubber(
                            label = "Camera X", value = camEval.x, step = 20.0, decimals = 0,
                            hasKeyframeAtFrame = ckf.x.any { it.frame == state.currentFrame },
                            hasAnyKeyframes = ckf.x.isNotEmpty(),
                            onToggleKeyframe = {
                                if (ckf.x.any { it.frame == state.currentFrame }) {
                                    viewModel.dispatchCommand(ProjectCommands.removeCameraKeyframe(comp.id, CameraAnimatableProp.X, state.currentFrame))
                                } else {
                                    viewModel.dispatchCommand(ProjectCommands.setCameraKeyframe(comp.id, CameraAnimatableProp.X, state.currentFrame, camEval.x))
                                    viewModel.selectKeyframe(
                                        SelectedKeyframeRef(KeyframeOwnerType.CAMERA, frame = state.currentFrame, cameraProp = CameraAnimatableProp.X),
                                        movePlayhead = false,
                                    )
                                }
                            },
                            onChange = { viewModel.dispatchCommand(ProjectCommands.setCameraKeyframe(comp.id, CameraAnimatableProp.X, state.currentFrame, it)) },
                        )
                        NumberScrubber(
                            label = "Camera Y", value = camEval.y, step = 20.0, decimals = 0,
                            hasKeyframeAtFrame = ckf.y.any { it.frame == state.currentFrame },
                            hasAnyKeyframes = ckf.y.isNotEmpty(),
                            onToggleKeyframe = {
                                if (ckf.y.any { it.frame == state.currentFrame }) {
                                    viewModel.dispatchCommand(ProjectCommands.removeCameraKeyframe(comp.id, CameraAnimatableProp.Y, state.currentFrame))
                                } else {
                                    viewModel.dispatchCommand(ProjectCommands.setCameraKeyframe(comp.id, CameraAnimatableProp.Y, state.currentFrame, camEval.y))
                                    viewModel.selectKeyframe(
                                        SelectedKeyframeRef(KeyframeOwnerType.CAMERA, frame = state.currentFrame, cameraProp = CameraAnimatableProp.Y),
                                        movePlayhead = false,
                                    )
                                }
                            },
                            onChange = { viewModel.dispatchCommand(ProjectCommands.setCameraKeyframe(comp.id, CameraAnimatableProp.Y, state.currentFrame, it)) },
                        )
                        NumberScrubber(
                            label = "Camera Zoom", value = camEval.zoom, step = 0.05, min = 0.25, max = 4.0, decimals = 2,
                            hasKeyframeAtFrame = ckf.zoom.any { it.frame == state.currentFrame },
                            hasAnyKeyframes = ckf.zoom.isNotEmpty(),
                            onToggleKeyframe = {
                                if (ckf.zoom.any { it.frame == state.currentFrame }) {
                                    viewModel.dispatchCommand(ProjectCommands.removeCameraKeyframe(comp.id, CameraAnimatableProp.ZOOM, state.currentFrame))
                                } else {
                                    viewModel.dispatchCommand(ProjectCommands.setCameraKeyframe(comp.id, CameraAnimatableProp.ZOOM, state.currentFrame, camEval.zoom))
                                    viewModel.selectKeyframe(
                                        SelectedKeyframeRef(KeyframeOwnerType.CAMERA, frame = state.currentFrame, cameraProp = CameraAnimatableProp.ZOOM),
                                        movePlayhead = false,
                                    )
                                }
                            },
                            onChange = { viewModel.dispatchCommand(ProjectCommands.setCameraKeyframe(comp.id, CameraAnimatableProp.ZOOM, state.currentFrame, it)) },
                        )
                        StudioSliderRow(
                            label = "Parallax Strength",
                            value = comp.camera.parallaxStrength.toFloat(),
                            valueRange = 0f..2f,
                            valueLabel = "${"%.2f".format(comp.camera.parallaxStrength)}×",
                            onValueChange = { v -> viewModel.dispatchCommand(ProjectCommands.updateCamera(comp.id) { it.copy(parallaxStrength = v.toDouble()) }) },
                            accentColor = StudioCyan,
                        )

                        MotionModifierStackSection(
                            compositionId = comp.id,
                            scope = "camera",
                            layerId = null,
                            modifiers = comp.camera.modifiers,
                            viewModel = viewModel,
                            onBrowsePresets = { cameraSubTab = "presets" },
                        )
                        BakeModifiersSection(
                            compositionId = comp.id,
                            scope = "camera",
                            layerId = null,
                            modifiers = comp.camera.modifiers,
                            durationFrames = comp.settings.durationFrames,
                            viewModel = viewModel,
                        )
                        val selectedCamKf = state.selectedKeyframe?.takeIf { it.ownerType == KeyframeOwnerType.CAMERA }
                        KeyframeManagerSection(
                            title = "Camera Keyframes",
                            channels = listOf(
                                KeyframeChannelUi("x", "Camera X", "X", ckf.x, camEval.x),
                                KeyframeChannelUi("y", "Camera Y", "Y", ckf.y, camEval.y),
                                KeyframeChannelUi("zoom", "Zoom", "Zoom", ckf.zoom, camEval.zoom),
                            ),
                            currentFrame = state.currentFrame,
                            durationFrames = comp.settings.durationFrames,
                            selectedChannelKey = selectedCamKf?.cameraProp?.key,
                            selectedKeyframeFrame = selectedCamKf?.frame,
                            hasModifiers = comp.camera.modifiers.isNotEmpty(),
                            onJumpToFrame = { viewModel.setCurrentFrame(it) },
                            onSelectKeyframe = { ch, f ->
                                val prop = CameraAnimatableProp.fromKey(ch)
                                viewModel.selectKeyframe(
                                    SelectedKeyframeRef(
                                        ownerType = KeyframeOwnerType.CAMERA,
                                        frame = f,
                                        cameraProp = prop,
                                    ),
                                    movePlayhead = true,
                                )
                            },
                            onSetKeyframe = { ch, f, v, interp ->
                                CameraAnimatableProp.fromKey(ch)?.let { p ->
                                    viewModel.dispatchCommand(ProjectCommands.setCameraKeyframe(comp.id, p, f, v, interp))
                                    viewModel.selectKeyframe(
                                        SelectedKeyframeRef(KeyframeOwnerType.CAMERA, frame = f, cameraProp = p),
                                        movePlayhead = false,
                                    )
                                }
                            },
                            onUpdateEasing = { ch, kfId, interp ->
                                CameraAnimatableProp.fromKey(ch)?.let { p ->
                                    viewModel.dispatchCommand(ProjectCommands.updateCameraKeyframe(comp.id, p, kfId, newInterp = interp))
                                }
                            },
                            onDuplicateKeyframe = { ch, f ->
                                val prop = CameraAnimatableProp.fromKey(ch)
                                viewModel.selectKeyframe(
                                    SelectedKeyframeRef(KeyframeOwnerType.CAMERA, frame = f, cameraProp = prop),
                                    movePlayhead = false,
                                )
                                viewModel.duplicateSelectedKeyframe()
                            },
                            onDeleteKeyframe = { ch, f ->
                                CameraAnimatableProp.fromKey(ch)?.let { p ->
                                    viewModel.dispatchCommand(ProjectCommands.removeCameraKeyframe(comp.id, p, f))
                                }
                            },
                            onClearChannel = { ch ->
                                CameraAnimatableProp.fromKey(ch)?.let { p ->
                                    viewModel.clearCameraPropertyAnimation(p)
                                }
                            },
                            onClearAllAnimation = {
                                viewModel.clearAllCameraAnimation()
                            },
                        )
                    }
                }

                InspectorTarget.WORLD -> {
                    val env = comp.environment
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(StudioBg)
                            .border(1.dp, StudioBorder, RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Reduced Preview Quality", style = MaterialTheme.typography.bodySmall, color = StudioTextPrimary)
                        Switch(checked = state.reducedQuality, onCheckedChange = { viewModel.toggleReducedQuality() })
                    }

                    SectionHeader("Sky Atmosphere Presets")
                    StudioChipRow(
                        options = SKY_ATMOSPHERE_PRESETS.map { it.id to it.label },
                        selectedKey = env.presetId,
                        onSelect = { pid ->
                            SKY_ATMOSPHERE_PRESETS.find { it.id == pid }?.let { p ->
                                viewModel.dispatchCommand(ProjectCommands.updateEnvironment(comp.id, p.patch))
                            }
                        },
                    )

                    SectionHeader("Environment Motion Presets")
                    val envMotion = remember { AnimationPresetRegistry.getAvailablePresets(PresetCategory.ENVIRONMENT) }
                    StudioChipRow(
                        options = envMotion.map { it.id to it.name },
                        selectedKey = "",
                        onSelect = { eid ->
                            envMotion.find { it.id == eid }?.environmentPatch?.let { patch ->
                                viewModel.dispatchCommand(ProjectCommands.updateEnvironment(comp.id, patch))
                            }
                        },
                        accent = StudioEmerald,
                    )

                    ColorSwatchRow("Sky Top", env.skyTopColor, onSelect = {
                        viewModel.dispatchCommand(ProjectCommands.updateEnvironment(comp.id, EnvironmentPatch(skyTopColor = it, presetId = "custom")))
                    })
                    ColorSwatchRow("Sky Horizon", env.skyBottomColor, onSelect = {
                        viewModel.dispatchCommand(ProjectCommands.updateEnvironment(comp.id, EnvironmentPatch(skyBottomColor = it, presetId = "custom")))
                    })
                    StudioSliderRow(
                        label = "Atmosphere Haze",
                        value = env.hazeIntensity.toFloat(),
                        valueRange = 0f..1f,
                        valueLabel = "${(env.hazeIntensity * 100).roundToInt()}%",
                        onValueChange = { viewModel.dispatchCommand(ProjectCommands.updateEnvironment(comp.id, EnvironmentPatch(hazeIntensity = it.toDouble()))) },
                    )

                    SectionHeader("Weather Particles")
                    StudioChipRow(
                        options = WeatherType.entries.map { it.name to it.name.lowercase().replaceFirstChar { c -> c.uppercase() } },
                        selectedKey = env.weatherType.name,
                        onSelect = { w ->
                            viewModel.dispatchCommand(ProjectCommands.updateEnvironment(comp.id, EnvironmentPatch(weatherType = WeatherType.valueOf(w))))
                        },
                        accent = StudioCyan,
                    )
                    if (env.weatherType != WeatherType.NONE) {
                        StudioSliderRow(
                            "Particle Density", env.weatherDensity.toFloat(), 0f..1f, "${(env.weatherDensity * 100).roundToInt()}%",
                            onValueChange = { viewModel.dispatchCommand(ProjectCommands.updateEnvironment(comp.id, EnvironmentPatch(weatherDensity = it.toDouble()))) },
                        )
                        StudioSliderRow(
                            "Particle Speed", env.weatherSpeed.toFloat(), 0.2f..3.5f, "${"%.1f".format(env.weatherSpeed)}×",
                            onValueChange = { viewModel.dispatchCommand(ProjectCommands.updateEnvironment(comp.id, EnvironmentPatch(weatherSpeed = it.toDouble()))) },
                        )
                        StudioSliderRow(
                            "Direction Angle", env.weatherDirectionDeg.toFloat(), -70f..70f, "${env.weatherDirectionDeg.roundToInt()}°",
                            onValueChange = { viewModel.dispatchCommand(ProjectCommands.updateEnvironment(comp.id, EnvironmentPatch(weatherDirectionDeg = it.toDouble()))) },
                        )
                    }

                    HorizontalDivider(color = StudioBorder)
                    SectionHeader("Fog, Clouds & Wind Grass")
                    StudioSliderRow(
                        "Fog Overlay", env.fogOpacity.toFloat(), 0f..1f, "${(env.fogOpacity * 100).roundToInt()}%",
                        onValueChange = { viewModel.dispatchCommand(ProjectCommands.updateEnvironment(comp.id, EnvironmentPatch(fogOpacity = it.toDouble()))) },
                    )
                    StudioSliderRow(
                        "Moving Clouds", env.cloudDensity.toFloat(), 0f..1f, "${(env.cloudDensity * 100).roundToInt()}%",
                        onValueChange = { viewModel.dispatchCommand(ProjectCommands.updateEnvironment(comp.id, EnvironmentPatch(cloudDensity = it.toDouble()))) },
                    )
                    StudioSliderRow(
                        "Wind-Blown Grass", env.grassDensity.toFloat(), 0f..1f, "${(env.grassDensity * 100).roundToInt()}%",
                        onValueChange = { viewModel.dispatchCommand(ProjectCommands.updateEnvironment(comp.id, EnvironmentPatch(grassDensity = it.toDouble()))) },
                        accentColor = StudioEmerald,
                    )
                    StudioChipRow(
                        options = listOf(WindDirection.RIGHT.name to "Wind → Right", WindDirection.LEFT.name to "Wind ← Left"),
                        selectedKey = env.windDirection.name,
                        onSelect = { wd ->
                            viewModel.dispatchCommand(ProjectCommands.updateEnvironment(comp.id, EnvironmentPatch(windDirection = WindDirection.valueOf(wd))))
                        },
                        accent = StudioEmerald,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (showLyricsModal && selectedLayer != null && primaryObj is SceneObjectDocument.TextObject) {
        LyricsStudioDialog(
            initialLyrics = primaryObj.lyrics,
            fps = fps,
            currentFrame = state.currentFrame,
            onJumpToFrame = { viewModel.setCurrentFrame(it) },
            onSave = { updatedLyrics ->
                viewModel.dispatchCommand(
                    ProjectCommands.updateObject(comp.id, selectedLayer.id, primaryObj.id) { o ->
                        if (o is SceneObjectDocument.TextObject) o.copy(lyrics = updatedLyrics) else o
                    }
                )
            },
            onDismiss = { showLyricsModal = false },
        )
    }
}

@Composable
private fun InspectorTabPill(
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    accent: Color = StudioPrimary,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .height(32.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(if (active) StudioSurfaceElevated else com.example.ui.theme.StudioInsetField)
            .border(1.dp, if (active) accent.copy(alpha = 0.75f) else StudioBorder, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .testTag("inspector_tab_${label.lowercase().replace(" ", "_")}"),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
            color = if (active) StudioTextPrimary else StudioTextSecondary,
            maxLines = 1,
        )
        if (active) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(accent),
            )
        }
    }
}
