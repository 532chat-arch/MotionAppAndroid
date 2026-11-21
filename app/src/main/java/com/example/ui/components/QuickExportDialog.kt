package com.example.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GifBox
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.commands.ProjectCommands
import com.example.engine.getProjectMovieDurationFrames
import com.example.export.ExportFormat
import com.example.export.ExportScope
import com.example.export.StudioExporter
import com.example.schema.fpsToDouble
import com.example.ui.theme.StudioAmber
import com.example.ui.theme.StudioBg
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioBorderStrong
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
import com.example.viewmodel.StudioUiState
import com.example.viewmodel.StudioViewModel

@Composable
internal fun QuickExportDialog(
    state: StudioUiState,
    viewModel: StudioViewModel,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val project = state.project
    val comp = state.activeComposition
    val exportSettings = project.exportSettings
    val safeSlug = project.name.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').ifEmpty { "motion_studio" }

    var selectedFormat by remember { mutableStateOf(ExportFormat.GIF) }
    var selectedScope by remember { mutableStateOf(ExportScope.ACTIVE_COMP) }
    var gifFps by remember { mutableIntStateOf(15) }
    var gifMaxDim by remember { mutableIntStateOf(480) }

    val saveGifLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/gif")
    ) { uri ->
        if (uri != null) {
            val artifact = state.lastExportedArtifact
            if (artifact != null && artifact.format == ExportFormat.GIF && artifact.file.exists()) {
                viewModel.saveArtifactToUri(artifact, uri)
            } else {
                viewModel.startGifExport(uri, selectedScope, gifFps, gifMaxDim)
            }
        }
    }

    val saveMp4Launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("video/mp4")
    ) { uri ->
        if (uri != null) {
            val artifact = state.lastExportedArtifact
            if (artifact != null && artifact.format == ExportFormat.MP4 && artifact.file.exists()) {
                viewModel.saveArtifactToUri(artifact, uri)
            } else {
                viewModel.startMp4Export(uri, selectedScope)
            }
        }
    }

    val durationFrames = if (selectedScope == ExportScope.FULL_MOVIE) {
        getProjectMovieDurationFrames(project)
    } else {
        comp.settings.durationFrames
    }
    val fps = fpsToDouble(comp.settings.fpsOverride ?: project.fps)

    Dialog(
        onDismissRequest = { if (!state.isExporting) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(6.dp))
                .border(1.dp, StudioBorder, RoundedCornerShape(6.dp))
                .testTag("quick_export_dialog"),
            color = StudioPanel,
            tonalElevation = 4.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.export_quick_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = StudioTextPrimary,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "$durationFrames frames · %.1fs".format(durationFrames / fps),
                            style = StudioMonoStyle,
                            color = StudioPrimary,
                        )
                    }
                    IconButton(
                        onClick = { if (!state.isExporting) onDismiss() },
                        enabled = !state.isExporting,
                        modifier = Modifier.testTag("quick_export_close_button"),
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.action_close),
                            tint = StudioTextSecondary,
                        )
                    }
                }

                HorizontalDivider(color = StudioBorder)

                // Format Picker (GIF vs MP4)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ExportFormatCard(
                        icon = Icons.Default.GifBox,
                        title = stringResource(R.string.export_format_gif),
                        subtitle = "Looping shareable .gif",
                        selected = selectedFormat == ExportFormat.GIF,
                        accent = StudioEmerald,
                        tag = "export_format_gif_card",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (!state.isExporting) {
                                selectedFormat = ExportFormat.GIF
                                viewModel.clearLastExportedArtifact()
                            }
                        },
                    )
                    ExportFormatCard(
                        icon = Icons.Default.VideoFile,
                        title = stringResource(R.string.export_format_mp4),
                        subtitle = "HD H.264 video .mp4",
                        selected = selectedFormat == ExportFormat.MP4,
                        accent = StudioPrimaryBright,
                        tag = "export_format_mp4_card",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (!state.isExporting) {
                                selectedFormat = ExportFormat.MP4
                                viewModel.clearLastExportedArtifact()
                            }
                        },
                    )
                }

                // Timeline Scope
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Timeline Scope", style = MaterialTheme.typography.labelMedium, color = StudioTextSecondary)
                    StudioChipRow(
                        options = listOf(
                            ExportScope.ACTIVE_COMP.name to "Active Comp (${comp.name})",
                            ExportScope.FULL_MOVIE.name to "Full Movie (${project.projectTimeline.size} clips)",
                        ),
                        selectedKey = selectedScope.name,
                        onSelect = {
                            if (!state.isExporting) {
                                selectedScope = ExportScope.valueOf(it)
                                viewModel.clearLastExportedArtifact()
                            }
                        },
                    )
                }

                // Format-specific encoding options
                if (selectedFormat == ExportFormat.GIF) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("GIF Size", style = MaterialTheme.typography.labelMedium, color = StudioTextSecondary)
                        StudioChipRow(
                            options = listOf("360" to "360p Compact", "480" to "480p Balanced", "640" to "640p Crisp"),
                            selectedKey = gifMaxDim.toString(),
                            onSelect = {
                                if (!state.isExporting) {
                                    gifMaxDim = it.toIntOrNull() ?: 480
                                    viewModel.clearLastExportedArtifact()
                                }
                            },
                            accent = StudioEmerald,
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("GIF Frame Rate", style = MaterialTheme.typography.labelMedium, color = StudioTextSecondary)
                        StudioChipRow(
                            options = listOf("12" to "12 fps", "15" to "15 fps", "24" to "24 fps"),
                            selectedKey = gifFps.toString(),
                            onSelect = {
                                if (!state.isExporting) {
                                    gifFps = it.toIntOrNull() ?: 15
                                    viewModel.clearLastExportedArtifact()
                                }
                            },
                            accent = StudioAmber,
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Video Resolution", style = MaterialTheme.typography.labelMedium, color = StudioTextSecondary)
                        val resKey = "${exportSettings.width}x${exportSettings.height}"
                        StudioChipRow(
                            options = listOf(
                                "1280x720" to "720p",
                                "1920x1080" to "1080p",
                                "1080x1080" to "Square",
                            ),
                            selectedKey = resKey,
                            onSelect = { key ->
                                if (!state.isExporting) {
                                    val parts = key.split("x")
                                    val w = parts.getOrNull(0)?.toIntOrNull() ?: 1280
                                    val h = parts.getOrNull(1)?.toIntOrNull() ?: 720
                                    viewModel.dispatchCommand(ProjectCommands.updateExportSettings { it.copy(width = w, height = h) })
                                    viewModel.clearLastExportedArtifact()
                                }
                            },
                            accent = StudioCyan,
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Video Quality", style = MaterialTheme.typography.labelMedium, color = StudioTextSecondary)
                        StudioChipRow(
                            options = listOf("medium" to "Standard", "high" to "High", "ultra" to "Master"),
                            selectedKey = exportSettings.quality,
                            onSelect = { q ->
                                if (!state.isExporting) {
                                    viewModel.dispatchCommand(ProjectCommands.updateExportSettings { it.copy(quality = q) })
                                    viewModel.clearLastExportedArtifact()
                                }
                            },
                            accent = StudioAmber,
                        )
                    }
                }

                // Live Progress State
                if (state.isExporting && state.exportProgress != null) {
                    val prog = state.exportProgress
                    val frac = (prog.currentFrame.toFloat() / prog.totalFrames.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .background(com.example.ui.theme.StudioInsetField)
                            .border(1.dp, StudioBorder, RoundedCornerShape(4.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = prog.stage,
                                style = MaterialTheme.typography.bodySmall,
                                color = StudioTextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = "${prog.currentFrame}/${prog.totalFrames}",
                                style = StudioMonoStyle,
                                color = StudioPrimary,
                            )
                        }
                        LinearProgressIndicator(
                            progress = { frac },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color = if (selectedFormat == ExportFormat.GIF) StudioEmerald else StudioPrimary,
                            trackColor = StudioSurfaceElevated,
                        )
                        OutlinedButton(
                            onClick = { viewModel.cancelExport() },
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.align(Alignment.End).testTag("quick_export_cancel_btn"),
                        ) {
                            Icon(Icons.Default.Cancel, contentDescription = null, tint = StudioRose, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.export_cancel_render), color = StudioRose)
                        }
                    }
                } else {
                    // Ready Artifact Banner (allows immediate re-share or save without re-rendering)
                    val readyArtifact = state.lastExportedArtifact
                    if (readyArtifact != null && readyArtifact.format == selectedFormat && readyArtifact.file.exists()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .background(StudioEmerald.copy(alpha = 0.12f))
                                .border(1.dp, StudioEmerald.copy(alpha = 0.55f), RoundedCornerShape(4.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = StudioEmerald, modifier = Modifier.size(18.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.export_ready_title),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = StudioEmerald,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        text = "${readyArtifact.displayName} · ${formatFileSize(readyArtifact.sizeBytes)}",
                                        style = StudioMonoStyle,
                                        color = StudioTextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Button(
                                    onClick = {
                                        StudioExporter.launchShareIntent(context, readyArtifact).onFailure { err ->
                                            viewModel.showFeedback(true, "Share failed: ${err.message}")
                                        }
                                    },
                                    shape = RoundedCornerShape(4.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = StudioEmerald),
                                    modifier = Modifier.weight(1f).testTag("quick_export_share_now_btn"),
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(R.string.export_share_now), color = Color.Black, fontWeight = FontWeight.SemiBold)
                                }
                                OutlinedButton(
                                    onClick = {
                                        if (readyArtifact.format == ExportFormat.GIF) {
                                            saveGifLauncher.launch("${safeSlug}.gif")
                                        } else {
                                            saveMp4Launcher.launch("${safeSlug}.mp4")
                                        }
                                    },
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier.weight(1f).testTag("quick_export_save_copy_btn"),
                                ) {
                                    Icon(Icons.Default.SaveAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(R.string.export_save_copy))
                                }
                            }
                        }
                    }

                    // Primary Compile Actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = {
                                viewModel.startQuickExport(
                                    format = selectedFormat,
                                    scope = selectedScope,
                                    gifFps = gifFps,
                                    gifMaxDimension = gifMaxDim,
                                    autoShare = true,
                                )
                            },
                            shape = RoundedCornerShape(4.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selectedFormat == ExportFormat.GIF) StudioEmerald else StudioPrimary,
                            ),
                            modifier = Modifier.weight(1f).testTag("quick_export_compile_share_btn"),
                        ) {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = null,
                                tint = if (selectedFormat == ExportFormat.GIF) Color.Black else Color.White,
                                modifier = Modifier.size(17.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.export_compile_and_share),
                                color = if (selectedFormat == ExportFormat.GIF) Color.Black else Color.White,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                if (selectedFormat == ExportFormat.GIF) {
                                    saveGifLauncher.launch("${safeSlug}.gif")
                                } else {
                                    saveMp4Launcher.launch("${safeSlug}.mp4")
                                }
                            },
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.testTag("quick_export_compile_save_btn"),
                        ) {
                            Icon(Icons.Default.SaveAlt, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(5.dp))
                            Text(stringResource(R.string.export_compile_and_save), maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExportFormatCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    selected: Boolean,
    accent: Color,
    tag: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(if (selected) accent.copy(alpha = 0.16f) else com.example.ui.theme.StudioInsetField)
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) accent else StudioBorder,
                shape = RoundedCornerShape(4.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag(tag),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) accent else StudioTextSecondary,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = if (selected) StudioTextPrimary else StudioTextSecondary,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = StudioTextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun formatFileSize(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> "%.2f MB".format(bytes / (1024.0 * 1024.0))
    bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}
