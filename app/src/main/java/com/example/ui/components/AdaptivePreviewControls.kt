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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.effects.DevicePerformanceProfile
import com.example.effects.PortableEffectKnowledgeExporter
import com.example.effects.PreviewQualityMode
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
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.StudioTextMuted
import com.example.ui.theme.StudioTextPrimary
import com.example.ui.theme.StudioTextSecondary
import com.example.viewmodel.StudioUiState
import com.example.viewmodel.StudioViewModel
import kotlin.math.roundToInt

/**
 * Compact stage overlay pill showing live adaptive preview tier and opening
 * the Low-End First Performance & Preview Quality settings dialog.
 */
@Composable
fun AdaptivePreviewBadge(
    state: StudioUiState,
    viewModel: StudioViewModel,
    modifier: Modifier = Modifier,
) {
    var showDialog by remember { mutableStateOf(false) }
    val ap = state.adaptivePreview
    val dotColor = when {
        ap.isDegradedByPerformance || ap.qualityMode == PreviewQualityMode.PERFORMANCE -> StudioAmber
        ap.qualityMode == PreviewQualityMode.HIGH_QUALITY -> StudioCyan
        else -> StudioEmerald
    }
    val pct = (ap.effectiveResolutionScale * 100f).roundToInt()

    Row(
        modifier = modifier
            .heightIn(min = 34.dp)
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(4.dp))
            .background(StudioPanel.copy(alpha = 0.96f))
            .border(1.dp, StudioBorder, RoundedCornerShape(4.dp))
            .clickable { showDialog = true }
            .padding(horizontal = 8.dp, vertical = 5.dp)
            .testTag("stage_preview_quality_badge"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(dotColor))
        Icon(
            imageVector = Icons.Default.Speed,
            contentDescription = stringResource(R.string.perf_quality_title),
            tint = dotColor,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = "${ap.effectiveTierLabel} · $pct%",
            style = StudioMonoStyle,
            color = StudioTextPrimary,
        )
        if (ap.avgFrameRenderMs > 0.1f) {
            Text(
                text = "${"%.0f".format(ap.avgFrameRenderMs)}ms",
                style = StudioMonoStyle,
                color = StudioTextSecondary,
            )
        }
    }

    if (showDialog) {
        PerformanceProfileDialog(
            state = state,
            viewModel = viewModel,
            onDismiss = { showDialog = false },
        )
    }
}

@Composable
fun PerformanceProfileDialog(
    state: StudioUiState,
    viewModel: StudioViewModel,
    onDismiss: () -> Unit,
) {
    val ap = state.adaptivePreview
    var showKnowledgeFormat by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = StudioSurfaceElevated,
        shape = RoundedCornerShape(12.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Default.Memory, contentDescription = null, tint = StudioPrimaryBright, modifier = Modifier.size(20.dp))
                Text(
                    text = stringResource(R.string.perf_quality_title),
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
                // Device Performance Profile Selector (A / B / C)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(R.string.perf_device_profile_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = StudioTextSecondary,
                    )
                    for (profile in DevicePerformanceProfile.entries) {
                        val selected = ap.deviceProfile == profile
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) StudioPrimary.copy(alpha = 0.18f) else StudioBg)
                                .border(1.dp, if (selected) StudioPrimaryBright else StudioBorder, RoundedCornerShape(8.dp))
                                .clickable { viewModel.setDeviceProfile(profile) }
                                .padding(10.dp)
                                .testTag("perf_profile_${profile.name.lowercase()}"),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = profile.displayName,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (selected) StudioPrimaryBright else StudioTextPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = "${profile.maxEffectBudgetPoints} pts · ${profile.maxParticleCount} ptcl",
                                    style = StudioMonoStyle,
                                    color = StudioTextSecondary,
                                )
                            }
                            Text(
                                text = "${profile.ramTierHint} · Max ${profile.maxSimultaneousLayerEffects} FX/layer · ${profile.maxTextureCacheMb}MB cache",
                                style = MaterialTheme.typography.labelSmall,
                                color = StudioTextMuted,
                            )
                        }
                    }
                }

                HorizontalDivider(color = StudioBorder)

                // Adaptive Preview Quality Mode Selector
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(R.string.perf_preview_mode_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = StudioTextSecondary,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        for (mode in PreviewQualityMode.entries) {
                            val selected = ap.qualityMode == mode
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                    .minimumInteractiveComponentSize()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (selected) StudioPrimary else StudioBg)
                                    .border(1.dp, if (selected) StudioPrimaryBright else StudioBorder, RoundedCornerShape(6.dp))
                                    .clickable { viewModel.setPreviewQualityMode(mode) }
                                    .testTag("preview_mode_${mode.name.lowercase()}"),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = mode.shortLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (selected) Color.White else StudioTextSecondary,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                    Text(
                        text = stringResource(R.string.perf_export_independent_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = StudioEmerald,
                    )
                }

                // Live Telemetry Card
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(StudioInsetField)
                        .border(1.dp, StudioBorder, RoundedCornerShape(8.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("Live Preview Telemetry", style = MaterialTheme.typography.labelMedium, color = StudioTextPrimary, fontWeight = FontWeight.SemiBold)
                    TelemetryRow("Preview Resolution", "${(ap.effectiveResolutionScale * 100).roundToInt()}%")
                    TelemetryRow("Effective Particle Cap", "${ap.effectiveMaxParticles} sprites")
                    TelemetryRow("Avg Draw Time", if (ap.avgFrameRenderMs > 0.1f) "${"%.1f".format(ap.avgFrameRenderMs)} ms" else "Idle")
                    TelemetryRow("Active Effects", "${ap.activeEffectCount} / ${ap.deviceProfile.maxTotalCompositionEffects}")
                    TelemetryRow("Dropped Frames", "${ap.droppedFrames}")
                }

                // Portable AI Knowledge Export Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    KnowledgeExportChip(
                        label = "Effects JSON",
                        tag = "export_effects_knowledge_json",
                        modifier = Modifier.weight(1f),
                        onClick = { showKnowledgeFormat = "json" },
                    )
                    KnowledgeExportChip(
                        label = "Effects Markdown",
                        tag = "export_effects_knowledge_md",
                        modifier = Modifier.weight(1f),
                        onClick = { showKnowledgeFormat = "md" },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close), color = StudioPrimaryBright)
            }
        },
    )

    showKnowledgeFormat?.let { fmt ->
        PortableKnowledgePreviewDialog(
            format = fmt,
            onDismiss = { showKnowledgeFormat = null },
            onCopied = { viewModel.showFeedback(false, "Copied portable effects $it to clipboard") },
        )
    }
}

@Composable
private fun TelemetryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = StudioTextSecondary)
        Text(value, style = StudioMonoStyle, color = StudioTextPrimary)
    }
}

@Composable
private fun KnowledgeExportChip(
    label: String,
    tag: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .height(38.dp)
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(6.dp))
            .background(StudioPanel)
            .border(1.dp, StudioBorder, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Default.Description, contentDescription = null, tint = StudioCyan, modifier = Modifier.size(14.dp))
        Spacer(Modifier.size(5.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = StudioTextPrimary, maxLines = 1)
    }
}

@Composable
fun PortableKnowledgePreviewDialog(
    format: String,
    onDismiss: () -> Unit,
    onCopied: (String) -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val content = remember(format) {
        if (format == "md") PortableEffectKnowledgeExporter.exportMarkdown()
        else PortableEffectKnowledgeExporter.exportJson()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = StudioSurfaceElevated,
        shape = RoundedCornerShape(12.dp),
        title = {
            Text(
                text = if (format == "md") "Portable Effects Knowledge (Markdown)" else "Portable Effects Knowledge (JSON)",
                style = MaterialTheme.typography.titleSmall,
                color = StudioTextPrimary,
            )
        },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(StudioBg)
                    .border(1.dp, StudioBorder, RoundedCornerShape(8.dp))
                    .verticalScroll(rememberScrollState())
                    .padding(10.dp),
            ) {
                Text(content, style = StudioMonoStyle, color = StudioTextSecondary)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    clipboard.setText(AnnotatedString(content))
                    onCopied(format.uppercase())
                    onDismiss()
                },
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, tint = StudioPrimaryBright, modifier = Modifier.size(15.dp))
                Spacer(Modifier.size(4.dp))
                Text("Copy", color = StudioPrimaryBright)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close), color = StudioTextSecondary)
            }
        },
    )
}
