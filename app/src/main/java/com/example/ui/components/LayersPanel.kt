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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.example.commands.uid
import com.example.data.createBlankComposition
import com.example.schema.CompositionSettings
import com.example.schema.LayerDocument
import com.example.schema.SceneObjectDocument
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
fun LayersPanel(
    state: StudioUiState,
    viewModel: StudioViewModel,
    modifier: Modifier = Modifier,
) {
    val comp = state.activeComposition
    var showCompDropdown by remember { mutableStateOf(false) }
    var showNestDropdown by remember { mutableStateOf(false) }
    var showCompSettings by remember { mutableStateOf(false) }
    var showNewCompDialog by remember { mutableStateOf(false) }
    var newCompName by remember { mutableStateOf("Scene ${state.project.compositions.size + 1}") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(StudioPanel)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Composition Switcher + Controls
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("COMPOSITION", style = MaterialTheme.typography.labelSmall, color = StudioTextSecondary)
                Text(
                    "${comp.settings.width}×${comp.settings.height} · ${comp.settings.durationFrames}f",
                    style = StudioMonoStyle,
                    color = StudioTextSecondary,
                )
            }
            Box {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(StudioBg)
                        .border(1.dp, StudioBorder, RoundedCornerShape(10.dp))
                        .clickable { showCompDropdown = true }
                        .padding(horizontal = 10.dp, vertical = 10.dp)
                        .testTag("comp_selector_button"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = comp.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = StudioTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text("▾", color = StudioTextSecondary)
                }
                DropdownMenu(
                    expanded = showCompDropdown,
                    onDismissRequest = { showCompDropdown = false },
                    modifier = Modifier.background(StudioSurfaceElevated),
                ) {
                    for (c in state.project.compositions) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    c.name,
                                    color = if (c.id == comp.id) StudioPrimaryBright else StudioTextPrimary,
                                    fontWeight = if (c.id == comp.id) FontWeight.SemiBold else FontWeight.Normal,
                                )
                            },
                            onClick = {
                                viewModel.setActiveComposition(c.id)
                                showCompDropdown = false
                            },
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                SmallActionPill("New", Icons.Default.Add, Modifier.weight(1f)) {
                    newCompName = "Scene ${state.project.compositions.size + 1}"
                    showNewCompDialog = true
                }
                SmallActionPill("Dup", Icons.Default.ContentCopy, Modifier.weight(1f)) {
                    val dupId = uid("comp")
                    if (viewModel.dispatchCommand(ProjectCommands.duplicateComposition(comp.id, dupId))) {
                        viewModel.setActiveComposition(dupId)
                    }
                }
                SmallActionPill("Props", Icons.Default.Settings, Modifier.weight(1f)) {
                    showCompSettings = true
                }
                Box(modifier = Modifier.weight(1f)) {
                    SmallActionPill("Nest", Icons.Default.AccountTree, Modifier.fillMaxWidth()) {
                        showNestDropdown = true
                    }
                    DropdownMenu(
                        expanded = showNestDropdown,
                        onDismissRequest = { showNestDropdown = false },
                        modifier = Modifier.background(StudioSurfaceElevated),
                    ) {
                        val candidates = state.project.compositions.filter { it.id != comp.id }
                        if (candidates.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("Create another comp to nest", color = StudioTextSecondary) },
                                onClick = { showNestDropdown = false },
                            )
                        } else {
                            for (other in candidates) {
                                DropdownMenuItem(
                                    text = { Text("Nest: ${other.name}", color = StudioTextPrimary) },
                                    onClick = {
                                        viewModel.dispatchCommand(ProjectCommands.nestComposition(comp.id, other.id))
                                        showNestDropdown = false
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = StudioBorder)

        // Layer Actions Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Layers (${comp.layers.size})",
                style = MaterialTheme.typography.titleSmall,
                color = StudioTextPrimary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (state.selectedLayerIds.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(7.dp))
                            .background(StudioCyan.copy(alpha = 0.16f))
                            .border(1.dp, StudioCyan.copy(alpha = 0.4f), RoundedCornerShape(7.dp))
                            .clickable {
                                viewModel.dispatchCommand(
                                    ProjectCommands.preComposeLayers(
                                        comp.id,
                                        state.selectedLayerIds.toList(),
                                        "Pre-Comp ${state.project.compositions.size + 1}",
                                    )
                                )
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .testTag("precompose_button"),
                    ) {
                        Text("Pre-Comp", style = MaterialTheme.typography.labelSmall, color = StudioCyan)
                    }
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(7.dp))
                        .background(StudioPrimary)
                        .clickable { viewModel.addQuickShapeLayer() }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .testTag("add_layer_button"),
                ) {
                    Text("+ Layer", style = MaterialTheme.typography.labelSmall, color = Color.White)
                }
            }
        }

        // Layer Stack (topmost rendered layer shown first, matching NLE/After Effects order)
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(comp.layers.asReversed(), key = { it.id }) { layer ->
                LayerRowItem(
                    layer = layer,
                    isSelected = layer.id == state.selectedLayerId || layer.id in state.selectedLayerIds,
                    onClick = { viewModel.selectLayer(layer.id) },
                    onToggleVisible = {
                        viewModel.dispatchCommand(ProjectCommands.updateLayer(comp.id, layer.id) { it.copy(visible = !it.visible) })
                    },
                    onToggleLock = {
                        viewModel.dispatchCommand(ProjectCommands.updateLayer(comp.id, layer.id) { it.copy(locked = !it.locked) })
                    },
                )
            }
        }

        // Bottom Layer Order & Delete Bar
        if (state.selectedLayerId != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(StudioBg)
                    .border(1.dp, StudioBorder, RoundedCornerShape(10.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = { viewModel.dispatchCommand(ProjectCommands.reorderLayer(comp.id, state.selectedLayerId, +1)) },
                    modifier = Modifier.testTag("layer_move_up"),
                ) {
                    Icon(Icons.Default.ArrowUpward, contentDescription = "Bring layer forward", tint = StudioTextPrimary, modifier = Modifier.size(18.dp))
                }
                IconButton(
                    onClick = { viewModel.dispatchCommand(ProjectCommands.reorderLayer(comp.id, state.selectedLayerId, -1)) },
                    modifier = Modifier.testTag("layer_move_down"),
                ) {
                    Icon(Icons.Default.ArrowDownward, contentDescription = "Send layer backward", tint = StudioTextPrimary, modifier = Modifier.size(18.dp))
                }
                IconButton(
                    onClick = { viewModel.dispatchCommand(ProjectCommands.deleteLayer(comp.id, state.selectedLayerId)) },
                    modifier = Modifier.testTag("layer_delete_button"),
                ) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete), tint = StudioRose, modifier = Modifier.size(18.dp))
                }
            }
        }
    }

    if (showNewCompDialog) {
        AlertDialog(
            onDismissRequest = { showNewCompDialog = false },
            containerColor = StudioPanel,
            title = { Text("New Composition", color = StudioTextPrimary) },
            text = {
                OutlinedTextField(
                    value = newCompName,
                    onValueChange = { newCompName = it },
                    singleLine = true,
                    label = { Text("Name") },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val c = createBlankComposition(newCompName, state.project.defaultWidth, state.project.defaultHeight, 240)
                        if (viewModel.dispatchCommand(ProjectCommands.addComposition(c))) {
                            viewModel.setActiveComposition(c.id)
                        }
                        showNewCompDialog = false
                    }
                ) { Text(stringResource(R.string.action_create)) }
            },
            dismissButton = {
                TextButton(onClick = { showNewCompDialog = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    if (showCompSettings) {
        var nameInput by remember(comp.id) { mutableStateOf(comp.name) }
        var durInput by remember(comp.id) { mutableStateOf(comp.settings.durationFrames.toString()) }
        AlertDialog(
            onDismissRequest = { showCompSettings = false },
            containerColor = StudioPanel,
            title = { Text("Composition Settings", color = StudioTextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = nameInput, onValueChange = { nameInput = it }, label = { Text("Composition Name") }, singleLine = true)
                    OutlinedTextField(value = durInput, onValueChange = { durInput = it.filter { ch -> ch.isDigit() } }, label = { Text("Duration (Frames)") }, singleLine = true)
                    if (state.project.compositions.size > 1) {
                        TextButton(
                            onClick = {
                                viewModel.dispatchCommand(ProjectCommands.deleteComposition(comp.id))
                                showCompSettings = false
                            }
                        ) { Text("Delete This Composition", color = StudioRose) }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val dur = (durInput.toIntOrNull() ?: comp.settings.durationFrames).coerceIn(24, 7200)
                        viewModel.dispatchCommand(
                            ProjectCommands.updateComposition(
                                comp.id,
                                name = nameInput,
                                settings = CompositionSettings(comp.settings.width, comp.settings.height, dur, comp.settings.fpsOverride),
                            )
                        )
                        showCompSettings = false
                    }
                ) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showCompSettings = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun SmallActionPill(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .height(34.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(StudioBg)
            .border(1.dp, StudioBorder, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = label, tint = StudioTextSecondary, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(3.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = StudioTextPrimary, maxLines = 1)
    }
}

@Composable
private fun LayerRowItem(
    layer: LayerDocument,
    isSelected: Boolean,
    onClick: () -> Unit,
    onToggleVisible: () -> Unit,
    onToggleLock: () -> Unit,
) {
    val obj = layer.objects.firstOrNull()
    val typeIcon = when (obj) {
        is SceneObjectDocument.CharacterObject -> Icons.Default.Face
        is SceneObjectDocument.SpriteObject -> Icons.Default.Image
        is SceneObjectDocument.TextObject -> Icons.Default.TextFields
        is SceneObjectDocument.NestedCompositionObject -> Icons.Default.AccountTree
        else -> Icons.Default.Layers
    }
    val kfCount = layer.keyframes.x.size + layer.keyframes.y.size + layer.keyframes.rotation.size +
        layer.keyframes.scaleX.size + layer.keyframes.scaleY.size + layer.keyframes.opacity.size

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .background(if (isSelected) StudioPrimary.copy(alpha = 0.2f) else StudioBg)
            .border(1.dp, if (isSelected) StudioPrimaryBright.copy(alpha = 0.65f) else StudioBorder, RoundedCornerShape(9.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .testTag("layer_row_${layer.id}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            typeIcon,
            contentDescription = null,
            tint = if (obj is SceneObjectDocument.NestedCompositionObject) StudioCyan else StudioTextSecondary,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(7.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = layer.name,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (layer.visible) StudioTextPrimary else StudioTextSecondary.copy(alpha = 0.5f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "z:${"%.1f".format(layer.depth)}",
                    style = StudioMonoStyle,
                    color = StudioTextSecondary,
                )
                if (kfCount > 0) {
                    Text("◆$kfCount", style = StudioMonoStyle, color = StudioAmber)
                }
                if (layer.modifiers.any { it.enabled }) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = "Has active presets", tint = StudioCyan, modifier = Modifier.size(11.dp))
                }
            }
        }
        IconButton(onClick = onToggleVisible, modifier = Modifier.size(32.dp)) {
            Icon(
                if (layer.visible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                contentDescription = stringResource(R.string.cd_toggle_visibility),
                tint = if (layer.visible) StudioTextSecondary else StudioTextSecondary.copy(alpha = 0.35f),
                modifier = Modifier.size(16.dp),
            )
        }
        IconButton(onClick = onToggleLock, modifier = Modifier.size(32.dp)) {
            Icon(
                if (layer.locked) Icons.Default.Lock else Icons.Default.LockOpen,
                contentDescription = stringResource(R.string.cd_toggle_lock),
                tint = if (layer.locked) StudioAmber else StudioTextSecondary.copy(alpha = 0.45f),
                modifier = Modifier.size(15.dp),
            )
        }
    }
}
