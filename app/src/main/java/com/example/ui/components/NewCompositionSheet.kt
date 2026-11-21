package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop169
import androidx.compose.material.icons.filled.CropPortrait
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioInsetField
import com.example.ui.theme.StudioMonoStyle
import com.example.ui.theme.StudioPanel
import com.example.ui.theme.StudioPrimary
import com.example.ui.theme.StudioSecondary
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.StudioTextPrimary
import com.example.ui.theme.StudioTextSecondary

internal data class CompositionCanvasPreset(
    val id: String,
    val label: String,
    val dimensionsLabel: String,
    val width: Int,
    val height: Int,
    val icon: ImageVector,
)

internal val COMPOSITION_CANVAS_PRESETS = listOf(
    CompositionCanvasPreset("hd169", "16:9 HD", "1280×720", 1280, 720, Icons.Default.Crop169),
    CompositionCanvasPreset("fhd169", "16:9 Full HD", "1920×1080", 1920, 1080, Icons.Default.Crop169),
    CompositionCanvasPreset("vertical916", "9:16 Reel", "1080×1920", 1080, 1920, Icons.Default.CropPortrait),
    CompositionCanvasPreset("square11", "1:1 Square", "1080×1080", 1080, 1080, Icons.Default.CropSquare),
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun NewCompositionBottomSheet(
    onDismiss: () -> Unit,
    onCreate: (name: String, width: Int, height: Int, fps: Int, durationFrames: Int) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var projectName by remember { mutableStateOf("Untitled Composition") }
    var selectedPresetId by remember { mutableStateOf("hd169") }
    var selectedFps by remember { mutableIntStateOf(24) }
    var selectedDurationSec by remember { mutableIntStateOf(10) }

    val activePreset = COMPOSITION_CANVAS_PRESETS.find { it.id == selectedPresetId }
        ?: COMPOSITION_CANVAS_PRESETS.first()
    val totalFrames = selectedFps * selectedDurationSec

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = StudioPanel,
        contentColor = StudioTextPrimary,
        shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
        modifier = Modifier.testTag("new_composition_sheet"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Sheet Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(StudioPrimary.copy(alpha = 0.18f))
                            .border(1.dp, StudioPrimary.copy(alpha = 0.5f), RoundedCornerShape(6.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.MovieCreation,
                            contentDescription = null,
                            tint = StudioPrimary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Column {
                        Text(
                            text = stringResource(R.string.home_create_sheet_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = StudioTextPrimary,
                        )
                        Text(
                            text = stringResource(R.string.home_create_sheet_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = StudioTextSecondary,
                        )
                    }
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("new_composition_sheet_close"),
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.action_close),
                        tint = StudioTextSecondary,
                    )
                }
            }

            HorizontalDivider(thickness = 1.dp, color = StudioBorder)

            // Composition Name Input
            OutlinedTextField(
                value = projectName,
                onValueChange = { projectName = it },
                label = { Text(stringResource(R.string.home_project_name_label)) },
                singleLine = true,
                shape = RoundedCornerShape(6.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = StudioInsetField,
                    unfocusedContainerColor = StudioInsetField,
                    focusedBorderColor = StudioPrimary,
                    unfocusedBorderColor = StudioBorder,
                    focusedTextColor = StudioTextPrimary,
                    unfocusedTextColor = StudioTextPrimary,
                    focusedLabelColor = StudioPrimary,
                    unfocusedLabelColor = StudioTextSecondary,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("new_project_name_input"),
            )

            // Canvas Preset Selection (Material 3 FilterChips)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.home_canvas_preset_label).uppercase(),
                    style = MaterialTheme.typography.titleSmall,
                    color = StudioTextSecondary,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    COMPOSITION_CANVAS_PRESETS.forEach { preset ->
                        val selected = preset.id == selectedPresetId
                        FilterChip(
                            selected = selected,
                            onClick = { selectedPresetId = preset.id },
                            label = {
                                Text(
                                    text = "${preset.label} · ${preset.dimensionsLabel}",
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (selected) Icons.Default.Check else preset.icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                            },
                            shape = RoundedCornerShape(6.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = StudioInsetField,
                                labelColor = StudioTextSecondary,
                                iconColor = StudioTextSecondary,
                                selectedContainerColor = StudioPrimary.copy(alpha = 0.22f),
                                selectedLabelColor = StudioTextPrimary,
                                selectedLeadingIconColor = StudioPrimary,
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selected,
                                borderColor = StudioBorder,
                                selectedBorderColor = StudioPrimary,
                            ),
                            modifier = Modifier.testTag("preset_chip_${preset.id}"),
                        )
                    }
                }
            }

            // Frame Rate Selection
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.home_fps_label).uppercase(),
                    style = MaterialTheme.typography.titleSmall,
                    color = StudioTextSecondary,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(
                        24 to "24 fps · Cinema",
                        30 to "30 fps · Video",
                        60 to "60 fps · Fluid",
                    ).forEach { (fpsValue, label) ->
                        val selected = selectedFps == fpsValue
                        FilterChip(
                            selected = selected,
                            onClick = { selectedFps = fpsValue },
                            label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                            leadingIcon = if (selected) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(15.dp)) }
                            } else null,
                            shape = RoundedCornerShape(6.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = StudioInsetField,
                                labelColor = StudioTextSecondary,
                                selectedContainerColor = StudioSecondary.copy(alpha = 0.22f),
                                selectedLabelColor = StudioTextPrimary,
                                selectedLeadingIconColor = StudioSecondary,
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selected,
                                borderColor = StudioBorder,
                                selectedBorderColor = StudioSecondary,
                            ),
                            modifier = Modifier.testTag("fps_chip_$fpsValue"),
                        )
                    }
                }
            }

            // Duration Selection
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.home_duration_label).uppercase(),
                    style = MaterialTheme.typography.titleSmall,
                    color = StudioTextSecondary,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(5, 10, 15, 30).forEach { sec ->
                        val selected = selectedDurationSec == sec
                        FilterChip(
                            selected = selected,
                            onClick = { selectedDurationSec = sec },
                            label = { Text("${sec}s (${sec * selectedFps}f)", style = MaterialTheme.typography.labelMedium) },
                            shape = RoundedCornerShape(6.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = StudioInsetField,
                                labelColor = StudioTextSecondary,
                                selectedContainerColor = StudioPrimary.copy(alpha = 0.22f),
                                selectedLabelColor = StudioTextPrimary,
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selected,
                                borderColor = StudioBorder,
                                selectedBorderColor = StudioPrimary,
                            ),
                            modifier = Modifier.testTag("duration_chip_${sec}s"),
                        )
                    }
                }
            }

            // Live Specification Readout Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(StudioInsetField)
                    .border(BorderStroke(1.dp, StudioBorder), RoundedCornerShape(6.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "OUTPUT SPEC",
                    style = MaterialTheme.typography.titleSmall,
                    color = StudioTextSecondary,
                )
                Text(
                    text = "${activePreset.width}×${activePreset.height} · ${selectedFps}fps · ${totalFrames}f",
                    style = StudioMonoStyle,
                    color = StudioPrimary,
                )
            }

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.height(48.dp),
                ) {
                    Text(stringResource(R.string.action_cancel), color = StudioTextSecondary)
                }
                Button(
                    onClick = {
                        onCreate(
                            projectName.trim().ifEmpty { "Untitled Composition" },
                            activePreset.width,
                            activePreset.height,
                            selectedFps,
                            totalFrames,
                        )
                    },
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StudioPrimary,
                        contentColor = Color.White,
                    ),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("confirm_create_project_button"),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.home_create_composition),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}
