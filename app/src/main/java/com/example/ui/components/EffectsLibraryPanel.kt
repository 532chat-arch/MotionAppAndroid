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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.commands.ProjectCommands
import com.example.effects.CostLevel
import com.example.effects.EffectBudgetValidator
import com.example.effects.EffectCategory
import com.example.effects.EffectComboPreset
import com.example.effects.EffectModuleDefinition
import com.example.effects.EffectParamSpec
import com.example.effects.EffectsLibraryRegistry
import com.example.engine.AnimationPresetRegistry
import com.example.schema.MotionModifierBlendMode
import com.example.schema.MotionModifierDirection
import com.example.schema.MotionModifierInstance
import com.example.schema.PivotAnchorPreset
import com.example.schema.getActiveCompFps
import com.example.ui.theme.StudioAmber
import com.example.ui.theme.StudioBg
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioCyan
import com.example.ui.theme.StudioEmerald
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
import com.example.viewmodel.StudioUiState
import com.example.viewmodel.StudioViewModel

/**
 * Modular Lightweight Effects Library Browser (Phase 1 Transform & Motion modules,
 * reusable Effect Combinations, and legacy Hair/Cloth/Character/Lyrics presets).
 */
@Composable
fun EffectsLibraryBrowser(
    state: StudioUiState,
    viewModel: StudioViewModel,
    layerId: String?,
    modifier: Modifier = Modifier,
) {
    var section by remember { mutableStateOf("all") }
    var search by remember { mutableStateOf("") }

    val modules = remember(section, search) {
        val base = when (section) {
            "transform" -> EffectsLibraryRegistry.getModulesByCategory(EffectCategory.TRANSFORM)
            "motion" -> EffectsLibraryRegistry.getModulesByCategory(EffectCategory.MOTION)
            "character" -> EffectsLibraryRegistry.getModulesByCategory(EffectCategory.CHARACTER)
            "visual" -> EffectsLibraryRegistry.getModulesByCategory(EffectCategory.VISUAL)
            "particles" -> EffectsLibraryRegistry.getModulesByCategory(EffectCategory.PARTICLES)
            "typography" -> EffectsLibraryRegistry.getModulesByCategory(EffectCategory.TYPOGRAPHY)
            "transition" -> EffectsLibraryRegistry.getModulesByCategory(EffectCategory.TRANSITION)
            "audio" -> EffectsLibraryRegistry.getModulesByCategory(EffectCategory.AUDIO)
            "all" -> EffectsLibraryRegistry.builtinModules
            else -> emptyList()
        }
        if (search.isBlank()) base
        else base.filter {
            it.name.contains(search, ignoreCase = true) ||
                it.description.contains(search, ignoreCase = true) ||
                it.category.label.contains(search, ignoreCase = true)
        }
    }

    val combos = remember(state.customEffectCombos, section, search) {
        if (section != "all" && section != "combos") emptyList()
        else {
            val all = EffectsLibraryRegistry.allCombos()
            if (search.isBlank()) all
            else all.filter {
                it.name.contains(search, ignoreCase = true) ||
                    it.description.contains(search, ignoreCase = true)
            }
        }
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            placeholder = { Text(stringResource(R.string.effects_search_placeholder), style = MaterialTheme.typography.bodySmall) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("effects_library_search"),
        )

        StudioChipRow(
            options = listOf(
                "all" to "All FX",
                "combos" to "Combos",
                "transform" to "Transform",
                "motion" to "Motion",
                "character" to "Character",
                "visual" to "Visual",
                "particles" to "Particles",
                "typography" to "Text",
                "transition" to "Transitions",
                "audio" to "Audio",
                "presets" to "Presets",
            ),
            selectedKey = section,
            onSelect = { section = it },
            accent = StudioPrimaryBright,
        )

        if (section == "presets") {
            MotionPresetsBrowser(
                state = state,
                viewModel = viewModel,
                scope = "layer",
                layerId = layerId,
            )
            return@Column
        }

        // Reusable Combos Section
        if (combos.isNotEmpty()) {
            SectionHeader(title = "Effect Combinations (${combos.size})")
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (combo in combos) {
                    EffectComboCard(
                        combo = combo,
                        onApply = {
                            if (layerId != null) {
                                viewModel.applyEffectComboToLayer(layerId, combo)
                            } else {
                                viewModel.showFeedback(true, "Select a layer first")
                            }
                        },
                        onDelete = if (combo.isCustom) {
                            { viewModel.deleteCustomEffectCombo(combo.id) }
                        } else null,
                    )
                }
            }
        }

        // Modular Effects Section
        if (modules.isNotEmpty()) {
            SectionHeader(title = "Lightweight Effect Modules (${modules.size})")
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (modDef in modules) {
                    EffectModuleCard(
                        def = modDef,
                        onAdd = {
                            if (layerId != null) {
                                viewModel.addEffectModuleToLayer(layerId, modDef.id)
                            } else {
                                viewModel.showFeedback(true, "Select a layer first")
                            }
                        },
                    )
                }
            }
        }

        if (modules.isEmpty() && combos.isEmpty()) {
            Text(
                text = "No effects match \"$search\".",
                style = MaterialTheme.typography.bodySmall,
                color = StudioTextSecondary,
                modifier = Modifier.padding(vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun EffectModuleCard(
    def: EffectModuleDefinition,
    onAdd: () -> Unit,
) {
    val costColor = costLevelColor(def.performance.level)
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
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = def.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = StudioTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(costColor.copy(alpha = 0.16f))
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                ) {
                    Text(
                        text = "${def.category.label} · ${def.performance.level.label}",
                        style = StudioMonoStyle,
                        color = costColor,
                    )
                }
            }
            Text(
                text = def.description,
                style = MaterialTheme.typography.labelSmall,
                color = StudioTextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            modifier = Modifier
                .height(36.dp)
                .minimumInteractiveComponentSize()
                .clip(RoundedCornerShape(8.dp))
                .background(StudioPrimary)
                .clickable(onClick = onAdd)
                .padding(horizontal = 12.dp)
                .testTag("add_effect_${def.id}"),
            contentAlignment = Alignment.Center,
        ) {
            Text("Add", style = MaterialTheme.typography.labelMedium, color = Color.White, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun EffectComboCard(
    combo: EffectComboPreset,
    onApply: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(StudioBg)
            .border(1.dp, if (combo.isCustom) StudioAmber.copy(alpha = 0.5f) else StudioBorder, RoundedCornerShape(10.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.Layers, contentDescription = null, tint = StudioAmber, modifier = Modifier.size(14.dp))
                Text(
                    text = combo.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = StudioTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text("${combo.items.size} FX", style = StudioMonoStyle, color = StudioAmber)
            }
            Text(
                text = combo.description,
                style = MaterialTheme.typography.labelSmall,
                color = StudioTextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (onDelete != null) {
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(36.dp).minimumInteractiveComponentSize(),
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Delete custom combo", tint = StudioRose, modifier = Modifier.size(16.dp))
            }
        }
        Box(
            modifier = Modifier
                .height(36.dp)
                .minimumInteractiveComponentSize()
                .clip(RoundedCornerShape(8.dp))
                .background(StudioAmber.copy(alpha = 0.22f))
                .border(1.dp, StudioAmber.copy(alpha = 0.65f), RoundedCornerShape(8.dp))
                .clickable(onClick = onApply)
                .padding(horizontal = 10.dp)
                .testTag("apply_combo_${combo.id}"),
            contentAlignment = Alignment.Center,
        ) {
            Text("Apply", style = MaterialTheme.typography.labelMedium, color = StudioAmber, fontWeight = FontWeight.SemiBold)
        }
    }
}

/**
 * Full-featured, beginner-friendly Effect Combination Stack with:
 * Enable/Disable, Add/Remove, Duplicate, Reorder (Up/Down), Reset, Save as Combo,
 * contextual Basic sliders + expandable Advanced controls, and Low-End budget meter.
 */
@Composable
fun ModularEffectStackSection(
    compositionId: String,
    layerId: String,
    modifiers: List<MotionModifierInstance>,
    state: StudioUiState,
    viewModel: StudioViewModel,
    onBrowseEffects: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fps = getActiveCompFps(state.project, compositionId)
    val profile = state.adaptivePreview.deviceProfile
    val budget = remember(modifiers, profile) {
        EffectBudgetValidator.validateLayerStack(modifiers, profile)
    }

    var saveComboDialogOpen by remember { mutableStateOf(false) }
    var comboName by remember { mutableStateOf("") }
    var saveSingleDialogMod by remember { mutableStateOf<MotionModifierInstance?>(null) }
    var singlePresetName by remember { mutableStateOf("") }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Section Header + Add Effect + Save Combo
        SectionHeader(
            title = "Effect Stack (${modifiers.size})",
            trailing = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (modifiers.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .height(30.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(StudioAmber.copy(alpha = 0.16f))
                                .border(1.dp, StudioAmber.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                .clickable {
                                    comboName = "My Combo (${modifiers.size} FX)"
                                    saveComboDialogOpen = true
                                }
                                .padding(horizontal = 8.dp)
                                .testTag("save_effect_combo_btn"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(Icons.Default.BookmarkAdd, contentDescription = null, tint = StudioAmber, modifier = Modifier.size(14.dp))
                            Text("Save Combo", style = MaterialTheme.typography.labelSmall, color = StudioAmber, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Row(
                        modifier = Modifier
                            .height(30.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(StudioPrimary)
                            .clickable(onClick = onBrowseEffects)
                            .padding(horizontal = 8.dp)
                            .testTag("browse_effects_library_btn"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                        Text("Add FX", style = MaterialTheme.typography.labelSmall, color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }
            },
        )

        // Low-End First Performance Budget Meter
        val budgetColor = if (budget.isWithinBudget) StudioEmerald else StudioAmber
        val fillFrac = (budget.usedBudgetPoints.toFloat() / budget.maxBudgetPoints.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(StudioInsetField)
                .border(1.dp, if (budget.isWithinBudget) StudioBorder else StudioAmber.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                .padding(8.dp)
                .testTag("effect_stack_budget_meter"),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${profile.shortLabel} Budget · ${budget.activeEffectCount}/${profile.maxSimultaneousLayerEffects} FX",
                    style = MaterialTheme.typography.labelSmall,
                    color = StudioTextSecondary,
                )
                Text(
                    text = "${budget.usedBudgetPoints} / ${budget.maxBudgetPoints} pts",
                    style = StudioMonoStyle,
                    color = budgetColor,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(StudioBg),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fillFrac)
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(budgetColor),
                )
            }
            for (warn in budget.warnings) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = StudioAmber, modifier = Modifier.size(13.dp))
                    Text(warn, style = MaterialTheme.typography.labelSmall, color = StudioAmber)
                }
            }
        }

        if (modifiers.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(StudioBg)
                    .border(1.dp, StudioBorder, RoundedCornerShape(10.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "No effects on this layer yet.",
                    style = MaterialTheme.typography.titleSmall,
                    color = StudioTextPrimary,
                )
                Text(
                    text = "Combine lightweight Transform, Motion, Character, Visual, Particle, Text, Transition, and Audio effects or apply a starter combo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = StudioTextSecondary,
                )
            }
        }

        modifiers.forEachIndexed { index, mod ->
            ModularEffectCard(
                mod = mod,
                index = index,
                totalCount = modifiers.size,
                fps = fps,
                onUpdate = { updater ->
                    viewModel.dispatchCommand(ProjectCommands.updateLayerModifier(compositionId, layerId, mod.id, updater))
                },
                onMoveUp = {
                    viewModel.dispatchCommand(ProjectCommands.reorderLayerModifier(compositionId, layerId, mod.id, moveUp = true))
                },
                onMoveDown = {
                    viewModel.dispatchCommand(ProjectCommands.reorderLayerModifier(compositionId, layerId, mod.id, moveUp = false))
                },
                onDuplicate = {
                    viewModel.dispatchCommand(ProjectCommands.duplicateLayerModifier(compositionId, layerId, mod.id))
                },
                onReset = {
                    viewModel.dispatchCommand(ProjectCommands.resetLayerModifier(compositionId, layerId, mod.id, fps))
                    viewModel.showFeedback(false, "Reset ${mod.label} parameters")
                },
                onSaveSingle = {
                    singlePresetName = "${mod.label} Custom"
                    saveSingleDialogMod = mod
                },
                onRemove = {
                    viewModel.dispatchCommand(ProjectCommands.removeLayerModifier(compositionId, layerId, mod.id))
                },
            )
        }
    }

    if (saveComboDialogOpen) {
        AlertDialog(
            onDismissRequest = { saveComboDialogOpen = false },
            containerColor = StudioSurfaceElevated,
            title = { Text("Save Effect Combination", color = StudioTextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Saves ${modifiers.size} combined effects as a reusable preset.",
                        style = MaterialTheme.typography.bodySmall,
                        color = StudioTextSecondary,
                    )
                    OutlinedTextField(
                        value = comboName,
                        onValueChange = { comboName = it },
                        singleLine = true,
                        label = { Text("Combo Name") },
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.saveLayerStackAsEffectCombo(layerId, comboName)
                        saveComboDialogOpen = false
                    },
                ) { Text("Save Combo", color = StudioPrimaryBright) }
            },
            dismissButton = {
                TextButton(onClick = { saveComboDialogOpen = false }) {
                    Text(stringResource(R.string.action_cancel), color = StudioTextSecondary)
                }
            },
        )
    }

    saveSingleDialogMod?.let { targetMod ->
        AlertDialog(
            onDismissRequest = { saveSingleDialogMod = null },
            containerColor = StudioSurfaceElevated,
            title = { Text("Save Custom Effect Preset", color = StudioTextPrimary) },
            text = {
                OutlinedTextField(
                    value = singlePresetName,
                    onValueChange = { singlePresetName = it },
                    singleLine = true,
                    label = { Text("Preset Name") },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.saveCustomPresetFromModifier(targetMod, singlePresetName)
                        saveSingleDialogMod = null
                    },
                ) { Text("Save Preset", color = StudioPrimaryBright) }
            },
            dismissButton = {
                TextButton(onClick = { saveSingleDialogMod = null }) {
                    Text(stringResource(R.string.action_cancel), color = StudioTextSecondary)
                }
            },
        )
    }
}

@Composable
private fun ModularEffectCard(
    mod: MotionModifierInstance,
    index: Int,
    totalCount: Int,
    fps: Double,
    onUpdate: ((MotionModifierInstance) -> MotionModifierInstance) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDuplicate: () -> Unit,
    onReset: () -> Unit,
    onSaveSingle: () -> Unit,
    onRemove: () -> Unit,
) {
    var expanded by remember(mod.id) { mutableStateOf(index == totalCount - 1) }
    var showAdvanced by remember(mod.id) { mutableStateOf(false) }

    val kernelId = mod.basePresetId ?: mod.presetId
    val moduleDef = remember(kernelId) { EffectsLibraryRegistry.getModuleById(kernelId) }
    val legacyDef = remember(kernelId) { AnimationPresetRegistry.getPresetById(kernelId) }
    val perfMeta = remember(kernelId) { EffectsLibraryRegistry.getPerformanceMetadata(kernelId) }
    val costColor = costLevelColor(perfMeta.level)

    val basicParams = remember(moduleDef) { moduleDef?.params?.filter { !it.isAdvanced }.orEmpty() }
    val advancedParams = remember(moduleDef) { moduleDef?.params?.filter { it.isAdvanced }.orEmpty() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(StudioBg)
            .border(1.dp, if (mod.enabled) StudioPrimary.copy(alpha = 0.55f) else StudioBorder, RoundedCornerShape(10.dp))
            .padding(8.dp)
            .testTag("effect_stack_card_${mod.id}"),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Card Header Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = StudioTextSecondary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(4.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = mod.label,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (mod.enabled) StudioTextPrimary else StudioTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${perfMeta.level.label} cost · ${if (perfMeta.supportsLowEnd) "Low-End OK" else "Mid/High"}",
                    style = StudioMonoStyle,
                    color = costColor,
                )
            }
            Switch(
                checked = mod.enabled,
                onCheckedChange = { checked -> onUpdate { it.copy(enabled = checked) } },
                modifier = Modifier.testTag("effect_enable_${mod.id}"),
            )
        }

        if (expanded) {
            // Contextual Basic Parameters (Beginner-Friendly)
            StudioSliderRow(
                label = "Intensity",
                value = mod.intensity.toFloat(),
                valueRange = 0f..2.5f,
                valueLabel = "${"%.2f".format(mod.intensity)}×",
                onValueChange = { v -> onUpdate { it.copy(intensity = v.toDouble()) } },
                accentColor = StudioPrimaryBright,
            )

            if (moduleDef != null) {
                for (spec in basicParams) {
                    EffectParamControlRow(
                        spec = spec,
                        currentValue = mod.params[spec.key] ?: spec.defaultValue,
                        onChange = { v -> onUpdate { it.copy(params = it.params + (spec.key to v)) } },
                    )
                }
            } else {
                legacyDef?.parameterControls?.forEach { ctrl ->
                    val cur = (mod.params[ctrl.key] ?: ctrl.defaultValue).toFloat()
                    StudioSliderRow(
                        label = ctrl.label,
                        value = cur,
                        valueRange = ctrl.min.toFloat()..ctrl.max.toFloat(),
                        valueLabel = "%.2f".format(cur),
                        onValueChange = { v -> onUpdate { it.copy(params = it.params + (ctrl.key to v.toDouble())) } },
                    )
                }
            }

            // Expandable Advanced Section (keeps inspector clean & beginner-friendly)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(StudioInsetField)
                    .border(1.dp, StudioBorder, RoundedCornerShape(6.dp))
                    .clickable { showAdvanced = !showAdvanced }
                    .padding(horizontal = 8.dp)
                    .testTag("effect_advanced_toggle_${mod.id}"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Icon(Icons.Default.Tune, contentDescription = null, tint = StudioCyan, modifier = Modifier.size(14.dp))
                    Text("Advanced & Timing", style = MaterialTheme.typography.labelSmall, color = StudioTextSecondary)
                }
                Icon(
                    imageVector = if (showAdvanced) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = StudioTextSecondary,
                    modifier = Modifier.size(16.dp),
                )
            }

            if (showAdvanced) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(StudioInsetField.copy(alpha = 0.65f))
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    for (spec in advancedParams) {
                        EffectParamControlRow(
                            spec = spec,
                            currentValue = mod.params[spec.key] ?: spec.defaultValue,
                            onChange = { v -> onUpdate { it.copy(params = it.params + (spec.key to v)) } },
                        )
                    }
                    StudioSliderRow(
                        label = "Speed",
                        value = mod.speed.toFloat(),
                        valueRange = 0.1f..4f,
                        valueLabel = "${"%.2f".format(mod.speed)}×",
                        onValueChange = { v -> onUpdate { it.copy(speed = v.toDouble()) } },
                        accentColor = StudioCyan,
                    )
                    NumberScrubber(
                        label = "Start Frame",
                        value = mod.startFrame.toDouble(),
                        step = 2.0,
                        min = 0.0,
                        max = 1200.0,
                        suffix = "f",
                        decimals = 0,
                        onChange = { v -> onUpdate { it.copy(startFrame = v.toInt().coerceAtLeast(0)) } },
                    )
                    NumberScrubber(
                        label = "Duration",
                        value = mod.durationFrames.toDouble(),
                        step = 6.0,
                        min = 4.0,
                        max = 1200.0,
                        suffix = "f",
                        decimals = 0,
                        onChange = { v -> onUpdate { it.copy(durationFrames = v.toInt().coerceAtLeast(1)) } },
                    )
                    NumberScrubber(
                        label = "Delay",
                        value = mod.delayFrames.toDouble(),
                        step = 2.0,
                        min = 0.0,
                        max = 600.0,
                        suffix = "f",
                        decimals = 0,
                        onChange = { v -> onUpdate { it.copy(delayFrames = v.toInt().coerceAtLeast(0)) } },
                    )
                    StudioChipRow(
                        options = listOf(
                            MotionModifierDirection.FORWARD.name to "Forward",
                            MotionModifierDirection.REVERSE.name to "Reverse",
                            MotionModifierDirection.PINGPONG.name to "PingPong",
                        ),
                        selectedKey = mod.direction.name,
                        onSelect = { k -> onUpdate { it.copy(direction = MotionModifierDirection.valueOf(k)) } },
                    )
                    StudioChipRow(
                        options = listOf(
                            MotionModifierBlendMode.ADD.name to "Add",
                            MotionModifierBlendMode.MULTIPLY.name to "Multiply",
                            MotionModifierBlendMode.OVERRIDE.name to "Override",
                        ),
                        selectedKey = mod.blendMode.name,
                        onSelect = { k -> onUpdate { it.copy(blendMode = MotionModifierBlendMode.valueOf(k)) } },
                    )
                    StudioChipRow(
                        options = listOf(
                            PivotAnchorPreset.CENTER.name to "Center",
                            PivotAnchorPreset.TOP.name to "Top",
                            PivotAnchorPreset.BOTTOM.name to "Bottom",
                            PivotAnchorPreset.LEFT.name to "Left",
                            PivotAnchorPreset.RIGHT.name to "Right",
                        ),
                        selectedKey = mod.pivotAnchor.name,
                        onSelect = { k -> onUpdate { it.copy(pivotAnchor = PivotAnchorPreset.valueOf(k)) } },
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = mod.loop, onCheckedChange = { l -> onUpdate { it.copy(loop = l) } })
                        Text("Loop Effect", style = MaterialTheme.typography.bodySmall, color = StudioTextPrimary)
                    }
                    Text(
                        text = "${perfMeta.renderingTechnique} · CPU ${perfMeta.cpuCost.label} · GPU ${perfMeta.gpuCost.label} · Mem ${perfMeta.memoryCost.label}",
                        style = StudioMonoStyle,
                        color = StudioTextMuted,
                    )
                }
            }

            HorizontalDivider(color = StudioBorder)

            // Tablet-friendly Action Bar: Reorder Up/Down, Duplicate, Reset, Save, Remove
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    IconButton(
                        onClick = onMoveUp,
                        enabled = index > 0,
                        modifier = Modifier.size(36.dp).minimumInteractiveComponentSize().testTag("effect_up_${mod.id}"),
                    ) {
                        Icon(
                            Icons.Default.ArrowUpward,
                            contentDescription = "Move effect up",
                            tint = if (index > 0) StudioTextPrimary else StudioTextMuted.copy(alpha = 0.35f),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    IconButton(
                        onClick = onMoveDown,
                        enabled = index < totalCount - 1,
                        modifier = Modifier.size(36.dp).minimumInteractiveComponentSize().testTag("effect_down_${mod.id}"),
                    ) {
                        Icon(
                            Icons.Default.ArrowDownward,
                            contentDescription = "Move effect down",
                            tint = if (index < totalCount - 1) StudioTextPrimary else StudioTextMuted.copy(alpha = 0.35f),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    IconButton(
                        onClick = onDuplicate,
                        modifier = Modifier.size(36.dp).minimumInteractiveComponentSize().testTag("effect_dup_${mod.id}"),
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Duplicate effect", tint = StudioCyan, modifier = Modifier.size(16.dp))
                    }
                    IconButton(
                        onClick = onReset,
                        modifier = Modifier.size(36.dp).minimumInteractiveComponentSize().testTag("effect_reset_${mod.id}"),
                    ) {
                        Icon(Icons.Default.RestartAlt, contentDescription = "Reset parameters", tint = StudioTextSecondary, modifier = Modifier.size(17.dp))
                    }
                    IconButton(
                        onClick = onSaveSingle,
                        modifier = Modifier.size(36.dp).minimumInteractiveComponentSize().testTag("effect_save_${mod.id}"),
                    ) {
                        Icon(Icons.Default.BookmarkAdd, contentDescription = "Save as preset", tint = StudioAmber, modifier = Modifier.size(16.dp))
                    }
                    IconButton(
                        onClick = onRemove,
                        modifier = Modifier.size(36.dp).minimumInteractiveComponentSize().testTag("effect_del_${mod.id}"),
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Remove effect", tint = StudioRose, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun EffectParamControlRow(
    spec: EffectParamSpec,
    currentValue: Double,
    onChange: (Double) -> Unit,
) {
    if (spec.enumLabels.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(spec.label, style = MaterialTheme.typography.labelSmall, color = StudioTextSecondary)
            val opts = spec.enumLabels.entries.sortedBy { it.key }.map { it.key.toString() to it.value }
            StudioChipRow(
                options = opts,
                selectedKey = currentValue.toInt().toString(),
                onSelect = { k -> k.toDoubleOrNull()?.let(onChange) },
                accent = StudioPrimaryBright,
            )
        }
    } else {
        val formatted = if (spec.step >= 1.0) "${currentValue.toInt()}${spec.unit}" else "${"%.2f".format(currentValue)}${spec.unit}"
        StudioSliderRow(
            label = spec.label,
            value = currentValue.toFloat(),
            valueRange = spec.min.toFloat()..spec.max.toFloat(),
            valueLabel = formatted,
            onValueChange = { onChange(it.toDouble()) },
        )
    }
}

private fun costLevelColor(level: CostLevel): Color = when (level) {
    CostLevel.LOW -> StudioEmerald
    CostLevel.MEDIUM -> StudioAmber
    CostLevel.HIGH -> StudioRose
}
