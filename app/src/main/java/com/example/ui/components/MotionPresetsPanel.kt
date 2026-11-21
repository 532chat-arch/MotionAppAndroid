package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.commands.ProjectCommands
import com.example.commands.uid
import com.example.engine.AnimationPresetRegistry
import com.example.engine.MotionPresetDefinition
import com.example.engine.PresetCategory
import com.example.engine.PresetTargetType
import com.example.schema.LyricsConfig
import com.example.schema.MotionModifierDirection
import com.example.schema.MotionModifierInstance
import com.example.schema.PivotAnchorPreset
import com.example.schema.SceneObjectDocument
import com.example.schema.getActiveCompFps
import com.example.ui.theme.StudioAmber
import com.example.ui.theme.StudioBg
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioCyan
import com.example.ui.theme.StudioMonoStyle
import com.example.ui.theme.StudioPanel
import com.example.ui.theme.StudioPrimary
import com.example.ui.theme.StudioPrimaryBright
import com.example.ui.theme.StudioRose
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.StudioTextPrimary
import com.example.ui.theme.StudioTextSecondary
import com.example.viewmodel.StudioUiState
import com.example.viewmodel.StudioViewModel

@Composable
fun MotionPresetsBrowser(
    state: StudioUiState,
    viewModel: StudioViewModel,
    scope: String, // "layer" or "camera"
    layerId: String?,
    modifier: Modifier = Modifier,
) {
    val comp = state.activeComposition
    val fps = getActiveCompFps(state.project, comp.id)
    val layer = comp.layers.find { it.id == layerId }
    val primaryObj = layer?.objects?.firstOrNull()

    var selectedCat by remember(scope) {
        mutableStateOf(if (scope == "camera") "camera" else "all")
    }
    var search by remember { mutableStateOf("") }

    val categories = if (scope == "camera") {
        listOf("camera" to "Camera", "custom" to "Custom")
    } else {
        listOf(
            "all" to "All",
            "hair" to "Hair",
            "clothing" to "Cloth",
            "character" to "Char",
            "effects" to "FX",
            "lyrics" to "Lyrics",
            "custom" to "Custom",
        )
    }

    val allPresets = remember(state.customPresets) { AnimationPresetRegistry.allPresets() }
    val filtered = remember(allPresets, selectedCat, search, scope) {
        allPresets.filter { p ->
            val scopeMatch = if (scope == "camera") {
                PresetTargetType.CAMERA in p.targetType
            } else {
                p.category != PresetCategory.CAMERA && p.category != PresetCategory.ENVIRONMENT
            }
            val catMatch = when (selectedCat) {
                "all" -> true
                "custom" -> p.isCustom
                else -> p.category.name.equals(selectedCat, ignoreCase = true)
            }
            val queryMatch = search.isBlank() ||
                p.name.contains(search, ignoreCase = true) ||
                p.description.contains(search, ignoreCase = true)
            scopeMatch && catMatch && queryMatch
        }
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            placeholder = { Text("Search motion presets…", style = MaterialTheme.typography.bodySmall) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        StudioChipRow(
            options = categories,
            selectedKey = selectedCat,
            onSelect = { selectedCat = it },
            accent = StudioAmber,
        )

        if (filtered.isEmpty()) {
            Text(
                "No presets match this filter.",
                style = MaterialTheme.typography.bodySmall,
                color = StudioTextSecondary,
                modifier = Modifier.padding(vertical = 12.dp),
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (preset in filtered) {
                    PresetCard(
                        preset = preset,
                        onApply = {
                            if (preset.lyricsStyle != null) {
                                if (layer != null && primaryObj is SceneObjectDocument.TextObject) {
                                    val cfg = (primaryObj.lyrics ?: LyricsConfig()).copy(enabled = true, style = preset.lyricsStyle)
                                    viewModel.dispatchCommand(
                                        ProjectCommands.updateObject(comp.id, layer.id, primaryObj.id) { o ->
                                            if (o is SceneObjectDocument.TextObject) o.copy(lyrics = cfg) else o
                                        }
                                    )
                                    viewModel.showFeedback(false, "Applied ${preset.name} to ${layer.name}")
                                } else {
                                    viewModel.showFeedback(true, "Select a Text layer to apply lyrics presets")
                                }
                            } else if (scope == "camera") {
                                val mod = AnimationPresetRegistry.createModifierFromPreset(preset.id, fps, uid("mod"), state.currentFrame)
                                if (mod != null) {
                                    viewModel.dispatchCommand(ProjectCommands.addCameraModifier(comp.id, mod))
                                    viewModel.showFeedback(false, "Added ${preset.name} to Camera")
                                }
                            } else if (layer != null) {
                                val mod = AnimationPresetRegistry.createModifierFromPreset(preset.id, fps, uid("mod"), state.currentFrame)
                                if (mod != null) {
                                    viewModel.dispatchCommand(ProjectCommands.addLayerModifier(comp.id, layer.id, mod))
                                    viewModel.showFeedback(false, "Added ${preset.name} to ${layer.name}")
                                }
                            } else {
                                viewModel.showFeedback(true, "Select a layer first")
                            }
                        },
                        onDeleteCustom = if (preset.isCustom) {
                            { viewModel.deleteCustomPreset(preset.id) }
                        } else null,
                    )
                }
            }
        }
    }
}

@Composable
private fun PresetCard(
    preset: MotionPresetDefinition,
    onApply: () -> Unit,
    onDeleteCustom: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(StudioBg)
            .border(1.dp, StudioBorder, RoundedCornerShape(10.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(preset.name, style = MaterialTheme.typography.titleSmall, color = StudioTextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(preset.category.label, style = StudioMonoStyle, color = StudioAmber)
            }
            Text(preset.description, style = MaterialTheme.typography.labelSmall, color = StudioTextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (onDeleteCustom != null) {
            IconButton(onClick = onDeleteCustom, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Delete, contentDescription = "Delete custom preset", tint = StudioRose, modifier = Modifier.size(16.dp))
            }
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(StudioPrimary)
                .clickable(onClick = onApply)
                .padding(horizontal = 10.dp, vertical = 7.dp)
                .testTag("apply_preset_${preset.id}"),
        ) {
            Text("Apply", style = MaterialTheme.typography.labelSmall, color = Color.White, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun MotionModifierStackSection(
    compositionId: String,
    scope: String, // "layer" or "camera"
    layerId: String?,
    modifiers: List<MotionModifierInstance>,
    viewModel: StudioViewModel,
    onBrowsePresets: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var saveDialogMod by remember { mutableStateOf<MotionModifierInstance?>(null) }
    var customName by remember { mutableStateOf("") }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(
            title = "Motion Modifiers (${modifiers.size})",
            trailing = {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(7.dp))
                        .background(StudioAmber.copy(alpha = 0.2f))
                        .clickable(onClick = onBrowsePresets)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = StudioAmber, modifier = Modifier.size(13.dp))
                    Text("Presets", style = MaterialTheme.typography.labelSmall, color = StudioAmber)
                }
            },
        )

        if (modifiers.isEmpty()) {
            Text(
                "No active motion modifiers. Apply a Hair, Cloth, Character, Camera, or FX preset.",
                style = MaterialTheme.typography.bodySmall,
                color = StudioTextSecondary,
            )
        }

        for (mod in modifiers) {
            var expanded by remember(mod.id) { mutableStateOf(false) }
            val presetDef = remember(mod.presetId, mod.basePresetId) {
                AnimationPresetRegistry.getPresetById(mod.basePresetId ?: mod.presetId)
            }

            fun updateMod(updater: (MotionModifierInstance) -> MotionModifierInstance) {
                if (scope == "camera") {
                    viewModel.dispatchCommand(ProjectCommands.updateCameraModifier(compositionId, mod.id, updater))
                } else if (layerId != null) {
                    viewModel.dispatchCommand(ProjectCommands.updateLayerModifier(compositionId, layerId, mod.id, updater))
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(StudioBg)
                    .border(1.dp, if (mod.enabled) StudioCyan.copy(alpha = 0.35f) else StudioBorder, RoundedCornerShape(10.dp))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = !expanded },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = StudioTextSecondary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        mod.label,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (mod.enabled) StudioTextPrimary else StudioTextSecondary,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Switch(
                        checked = mod.enabled,
                        onCheckedChange = { checked -> updateMod { it.copy(enabled = checked) } },
                    )
                }

                if (expanded) {
                    StudioSliderRow(
                        label = "Intensity",
                        value = mod.intensity.toFloat(),
                        valueRange = 0f..3f,
                        valueLabel = "${"%.2f".format(mod.intensity)}×",
                        onValueChange = { v -> updateMod { it.copy(intensity = v.toDouble()) } },
                        accentColor = StudioCyan,
                    )
                    StudioSliderRow(
                        label = "Speed",
                        value = mod.speed.toFloat(),
                        valueRange = 0.1f..4f,
                        valueLabel = "${"%.2f".format(mod.speed)}×",
                        onValueChange = { v -> updateMod { it.copy(speed = v.toDouble()) } },
                        accentColor = StudioCyan,
                    )
                    NumberScrubber(
                        label = "Duration",
                        value = mod.durationFrames.toDouble(),
                        step = 6.0,
                        min = 6.0,
                        max = 1200.0,
                        suffix = "f",
                        decimals = 0,
                        onChange = { v -> updateMod { it.copy(durationFrames = v.toInt()) } },
                    )

                    // Preset-specific parameter sliders
                    presetDef?.parameterControls?.forEach { ctrl ->
                        val cur = (mod.params[ctrl.key] ?: ctrl.defaultValue).toFloat()
                        StudioSliderRow(
                            label = ctrl.label,
                            value = cur,
                            valueRange = ctrl.min.toFloat()..ctrl.max.toFloat(),
                            valueLabel = "%.2f".format(cur),
                            onValueChange = { v -> updateMod { it.copy(params = it.params + (ctrl.key to v.toDouble())) } },
                        )
                    }

                    // Direction & Pivot selectors
                    StudioChipRow(
                        options = listOf(
                            MotionModifierDirection.FORWARD.name to "Forward",
                            MotionModifierDirection.REVERSE.name to "Reverse",
                            MotionModifierDirection.PINGPONG.name to "PingPong",
                        ),
                        selectedKey = mod.direction.name,
                        onSelect = { k -> updateMod { it.copy(direction = MotionModifierDirection.valueOf(k)) } },
                    )
                    if (scope == "layer") {
                        StudioChipRow(
                            options = listOf(
                                PivotAnchorPreset.CENTER.name to "Center",
                                PivotAnchorPreset.TOP.name to "Top",
                                PivotAnchorPreset.BOTTOM.name to "Bottom",
                                PivotAnchorPreset.LEFT.name to "Left",
                                PivotAnchorPreset.RIGHT.name to "Right",
                            ),
                            selectedKey = mod.pivotAnchor.name,
                            onSelect = { k -> updateMod { it.copy(pivotAnchor = PivotAnchorPreset.valueOf(k)) } },
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = mod.loop, onCheckedChange = { l -> updateMod { it.copy(loop = l) } })
                            Text("Loop", style = MaterialTheme.typography.bodySmall, color = StudioTextPrimary)
                        }
                        Row {
                            IconButton(
                                onClick = {
                                    customName = "${mod.label} Custom"
                                    saveDialogMod = mod
                                },
                                modifier = Modifier.size(34.dp),
                            ) {
                                Icon(Icons.Default.BookmarkAdd, contentDescription = "Save as custom preset", tint = StudioAmber, modifier = Modifier.size(18.dp))
                            }
                            IconButton(
                                onClick = {
                                    if (scope == "camera") {
                                        viewModel.dispatchCommand(ProjectCommands.removeCameraModifier(compositionId, mod.id))
                                    } else if (layerId != null) {
                                        viewModel.dispatchCommand(ProjectCommands.removeLayerModifier(compositionId, layerId, mod.id))
                                    }
                                },
                                modifier = Modifier.size(34.dp),
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove modifier", tint = StudioRose, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    saveDialogMod?.let { targetMod ->
        AlertDialog(
            onDismissRequest = { saveDialogMod = null },
            containerColor = StudioPanel,
            title = { Text("Save Custom Preset", color = StudioTextPrimary) },
            text = {
                OutlinedTextField(
                    value = customName,
                    onValueChange = { customName = it },
                    singleLine = true,
                    label = { Text("Preset Name") },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.saveCustomPresetFromModifier(targetMod, customName)
                        saveDialogMod = null
                    }
                ) { Text("Save Preset") }
            },
            dismissButton = {
                TextButton(onClick = { saveDialogMod = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
fun BakeModifiersSection(
    compositionId: String,
    scope: String, // "layer" or "camera"
    layerId: String?,
    modifiers: List<MotionModifierInstance>,
    durationFrames: Int,
    viewModel: StudioViewModel,
) {
    if (modifiers.isEmpty()) return
    var expanded by remember { mutableStateOf(false) }
    var stepFrames by remember { mutableIntStateOf(6) }
    var removeAfter by remember { mutableStateOf(false) }
    val activeCount = modifiers.count { it.enabled }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(StudioBg)
            .border(1.dp, StudioBorder, RoundedCornerShape(10.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = StudioPrimaryBright, modifier = Modifier.size(16.dp))
                Text("Bake Modifiers to Keyframes", style = MaterialTheme.typography.titleSmall, color = StudioTextPrimary)
            }
            Text("$activeCount active", style = StudioMonoStyle, color = StudioTextSecondary)
        }

        if (expanded) {
            StudioChipRow(
                options = listOf("1" to "1f", "2" to "2f", "4" to "4f", "6" to "6f", "12" to "12f"),
                selectedKey = stepFrames.toString(),
                onSelect = { stepFrames = it.toIntOrNull() ?: 6 },
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = removeAfter, onCheckedChange = { removeAfter = it })
                Text("Remove modifiers after baking", style = MaterialTheme.typography.bodySmall, color = StudioTextSecondary)
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (activeCount > 0) StudioPrimary else StudioSurfaceElevated)
                    .clickable(enabled = activeCount > 0) {
                        val endF = (durationFrames - 1).coerceAtLeast(0)
                        val ok = if (scope == "camera") {
                            viewModel.dispatchCommand(
                                ProjectCommands.bakeCameraModifiersToKeyframes(compositionId, stepFrames, 0, endF, removeAfter)
                            )
                        } else if (layerId != null) {
                            viewModel.dispatchCommand(
                                ProjectCommands.bakeLayerModifiersToKeyframes(compositionId, layerId, stepFrames, 0, endF, removeAfter)
                            )
                        } else false
                        if (ok) viewModel.showFeedback(false, "Baked $activeCount modifier(s) every ${stepFrames}f")
                    }
                    .padding(vertical = 10.dp)
                    .testTag("bake_modifiers_button"),
                contentAlignment = Alignment.Center,
            ) {
                Text("Bake to Timeline Keyframes", style = MaterialTheme.typography.labelLarge, color = Color.White)
            }
        }
    }
}
