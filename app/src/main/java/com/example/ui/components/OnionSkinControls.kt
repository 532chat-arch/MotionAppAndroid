package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.FilterNone
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.StudioBg
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioBorderStrong
import com.example.ui.theme.StudioEmerald
import com.example.ui.theme.StudioMonoStyle
import com.example.ui.theme.StudioPanel
import com.example.ui.theme.StudioPrimary
import com.example.ui.theme.StudioPrimaryBright
import com.example.ui.theme.StudioRose
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.StudioTextPrimary
import com.example.ui.theme.StudioTextSecondary
import com.example.viewmodel.OnionSkinSettings
import kotlin.math.roundToInt

@Composable
internal fun OnionSkinToolbarControl(
    settings: OnionSkinSettings,
    onToggle: () -> Unit,
    onUpdate: ((OnionSkinSettings) -> OnionSkinSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    val activeBorder = Brush.horizontalGradient(
        listOf(StudioRose.copy(alpha = 0.8f), StudioEmerald.copy(alpha = 0.8f))
    )

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(if (settings.enabled) com.example.ui.theme.StudioInsetField else com.example.ui.theme.StudioInsetField)
                .then(
                    if (settings.enabled) {
                        Modifier.border(1.dp, activeBorder, RoundedCornerShape(4.dp))
                    } else {
                        Modifier.border(1.dp, StudioBorder, RoundedCornerShape(4.dp))
                    }
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 7.dp, vertical = 5.dp)
                    .testTag("timeline_onion_skin_toggle"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.FilterNone,
                    contentDescription = stringResource(R.string.timeline_onion_skin),
                    tint = if (settings.enabled) Color.White else StudioTextSecondary,
                    modifier = Modifier.size(14.dp),
                )
                if (settings.enabled) {
                    Text(
                        text = "-${settings.prevFrames}/+${settings.nextFrames}",
                        style = StudioMonoStyle.copy(fontSize = 10.sp),
                        color = StudioTextPrimary,
                        maxLines = 1,
                    )
                }
            }

            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(18.dp)
                    .background(StudioBorder),
            )

            Box(
                modifier = Modifier
                    .clickable { menuExpanded = true }
                    .padding(horizontal = 3.dp, vertical = 5.dp)
                    .testTag("timeline_onion_skin_settings"),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = stringResource(R.string.timeline_onion_skin_settings),
                    tint = if (settings.enabled) StudioPrimary else StudioTextSecondary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
            modifier = Modifier
                .width(256.dp)
                .background(StudioPanel)
                .border(1.dp, StudioBorder, RoundedCornerShape(6.dp))
                .testTag("onion_skin_settings_menu"),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Header + ON/OFF Toggle Pill
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.FilterNone,
                            contentDescription = null,
                            tint = if (settings.enabled) StudioPrimaryBright else StudioTextSecondary,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = stringResource(R.string.timeline_onion_skin),
                            style = MaterialTheme.typography.titleSmall,
                            color = StudioTextPrimary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (settings.enabled) StudioPrimary else StudioSurfaceElevated)
                            .clickable(onClick = onToggle)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                            .testTag("onion_skin_menu_enable_toggle"),
                    ) {
                        Text(
                            text = if (settings.enabled) "ON" else "OFF",
                            style = StudioMonoStyle.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                            color = if (settings.enabled) Color.White else StudioTextSecondary,
                        )
                    }
                }

                HorizontalDivider(color = StudioBorder)

                // Previous (Rose) & Next (Emerald) frame counts
                OnionSkinStepperRow(
                    label = stringResource(R.string.onion_skin_prev_frames),
                    dotColor = StudioRose,
                    value = settings.prevFrames,
                    prefix = "-",
                    minusTag = "onion_skin_prev_minus",
                    plusTag = "onion_skin_prev_plus",
                    onDecrement = { onUpdate { it.copy(enabled = true, prevFrames = it.prevFrames - 1) } },
                    onIncrement = { onUpdate { it.copy(enabled = true, prevFrames = it.prevFrames + 1) } },
                )
                OnionSkinStepperRow(
                    label = stringResource(R.string.onion_skin_next_frames),
                    dotColor = StudioEmerald,
                    value = settings.nextFrames,
                    prefix = "+",
                    minusTag = "onion_skin_next_minus",
                    plusTag = "onion_skin_next_plus",
                    onDecrement = { onUpdate { it.copy(enabled = true, nextFrames = it.nextFrames - 1) } },
                    onIncrement = { onUpdate { it.copy(enabled = true, nextFrames = it.nextFrames + 1) } },
                )

                // Frame Spacing (1f, 2f, 4f)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(R.string.onion_skin_step),
                        style = MaterialTheme.typography.labelSmall,
                        color = StudioTextSecondary,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(1, 2, 4).forEach { step ->
                            val selected = settings.frameStep == step
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (selected) StudioPrimary.copy(alpha = 0.28f) else StudioBg)
                                    .border(1.dp, if (selected) StudioPrimaryBright else StudioBorder, RoundedCornerShape(6.dp))
                                    .clickable { onUpdate { it.copy(enabled = true, frameStep = step) } }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                    .testTag("onion_skin_step_${step}f"),
                            ) {
                                Text(
                                    text = "${step}f",
                                    style = StudioMonoStyle.copy(fontSize = 10.sp),
                                    color = if (selected) Color.White else StudioTextSecondary,
                                )
                            }
                        }
                    }
                }

                // Ghost Opacity Slider
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = stringResource(R.string.onion_skin_opacity),
                            style = MaterialTheme.typography.labelSmall,
                            color = StudioTextSecondary,
                        )
                        Text(
                            text = "${(settings.opacity * 100).roundToInt()}%",
                            style = StudioMonoStyle.copy(fontSize = 10.sp),
                            color = StudioPrimaryBright,
                        )
                    }
                    Slider(
                        value = settings.opacity,
                        onValueChange = { v -> onUpdate { it.copy(enabled = true, opacity = v) } },
                        valueRange = 0.10f..0.70f,
                        colors = SliderDefaults.colors(
                            thumbColor = StudioPrimaryBright,
                            activeTrackColor = StudioPrimary,
                            inactiveTrackColor = StudioBorderStrong,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(28.dp)
                            .testTag("onion_skin_opacity_slider"),
                    )
                }

                // Scope: All Layers vs Selected Layer Only
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(StudioBg)
                        .border(1.dp, StudioBorder, RoundedCornerShape(8.dp))
                        .padding(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    OnionScopeChip(
                        label = stringResource(R.string.onion_skin_scope_all),
                        selected = !settings.selectedLayerOnly,
                        tag = "onion_skin_scope_all",
                        modifier = Modifier.weight(1f),
                        onClick = { onUpdate { it.copy(selectedLayerOnly = false) } },
                    )
                    OnionScopeChip(
                        label = stringResource(R.string.onion_skin_scope_selected),
                        selected = settings.selectedLayerOnly,
                        tag = "onion_skin_scope_selected",
                        modifier = Modifier.weight(1f),
                        onClick = { onUpdate { it.copy(selectedLayerOnly = true) } },
                    )
                }
            }
        }
    }
}

@Composable
private fun OnionSkinStepperRow(
    label: String,
    dotColor: Color,
    value: Int,
    prefix: String,
    minusTag: String,
    plusTag: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dotColor),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = StudioTextSecondary,
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(StudioBg)
                    .border(1.dp, StudioBorder, RoundedCornerShape(6.dp))
                    .clickable(enabled = value > 0, onClick = onDecrement)
                    .testTag(minusTag),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Remove,
                    contentDescription = "Decrease $label frames",
                    tint = if (value > 0) StudioTextPrimary else StudioTextSecondary.copy(alpha = 0.35f),
                    modifier = Modifier.size(14.dp),
                )
            }
            Text(
                text = if (value == 0) "0" else "$prefix$value",
                style = StudioMonoStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                color = if (value > 0) dotColor else StudioTextSecondary,
                modifier = Modifier.width(24.dp),
            )
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(StudioBg)
                    .border(1.dp, StudioBorder, RoundedCornerShape(6.dp))
                    .clickable(enabled = value < 5, onClick = onIncrement)
                    .testTag(plusTag),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Increase $label frames",
                    tint = if (value < 5) StudioTextPrimary else StudioTextSecondary.copy(alpha = 0.35f),
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

@Composable
private fun OnionScopeChip(
    label: String,
    selected: Boolean,
    tag: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) StudioPrimary.copy(alpha = 0.3f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 5.dp)
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = if (selected) Color.White else StudioTextSecondary,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}
