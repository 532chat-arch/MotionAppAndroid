package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GifBox
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import com.example.engine.getProjectMovieDurationFrames
import com.example.export.ExportScope
import com.example.schema.fpsToDouble
import com.example.ui.components.SectionHeader
import com.example.ui.components.StudioChipRow
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
import com.example.viewmodel.StudioUiState
import com.example.viewmodel.StudioViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExportWorkspace(
    state: StudioUiState,
    viewModel: StudioViewModel,
    modifier: Modifier = Modifier,
) {
    var scope by remember { mutableStateOf(ExportScope.ACTIVE_COMP) }
    val project = state.project
    val comp = state.activeComposition
    val exportSettings = project.exportSettings
    val safeSlug = project.name.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').ifEmpty { "motion_studio" }

    val mp4Launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("video/mp4")
    ) { uri ->
        if (uri != null) viewModel.startMp4Export(uri, scope)
    }

    val gifLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/gif")
    ) { uri ->
        if (uri != null) viewModel.startGifExport(uri, scope)
    }

    val pngLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/png")
    ) { uri ->
        if (uri != null) viewModel.exportPngSnapshot(uri, scope)
    }

    val motionXSaveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) viewModel.exportMotionXToUri(uri)
    }

    val motionXOpenLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.importMotionXFromUri(uri)
    }

    val durationFrames = if (scope == ExportScope.FULL_MOVIE) {
        getProjectMovieDurationFrames(project)
    } else {
        comp.settings.durationFrames
    }
    val fps = fpsToDouble(project.fps)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(StudioBg),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 960.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Render Video & PNG Panel
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(StudioPanel)
                        .border(1.dp, StudioBorder, RoundedCornerShape(6.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    SectionHeader(
                        title = stringResource(R.string.export_title),
                        trailing = {
                            Text(
                                text = "$durationFrames frames · %.1fs".format(durationFrames / fps),
                                style = StudioMonoStyle,
                                color = StudioCyan,
                            )
                        },
                    )

                    Text("Render Scope", style = MaterialTheme.typography.labelMedium, color = StudioTextSecondary)
                    StudioChipRow(
                        options = listOf(
                            ExportScope.ACTIVE_COMP.name to "Active Comp (${comp.name})",
                            ExportScope.FULL_MOVIE.name to "Full Movie (${project.projectTimeline.size} clips)",
                        ),
                        selectedKey = scope.name,
                        onSelect = { scope = ExportScope.valueOf(it) },
                    )

                    Text("Output Resolution", style = MaterialTheme.typography.labelMedium, color = StudioTextSecondary)
                    val resKey = "${exportSettings.width}x${exportSettings.height}"
                    StudioChipRow(
                        options = listOf(
                            "1280x720" to "720p (1280×720)",
                            "1920x1080" to "1080p (1920×1080)",
                            "1080x1920" to "Vertical (1080×1920)",
                            "1080x1080" to "Square (1080×1080)",
                        ),
                        selectedKey = resKey,
                        onSelect = { key ->
                            val parts = key.split("x")
                            val w = parts.getOrNull(0)?.toIntOrNull() ?: 1280
                            val h = parts.getOrNull(1)?.toIntOrNull() ?: 720
                            viewModel.dispatchCommand(ProjectCommands.updateExportSettings { it.copy(width = w, height = h) })
                        },
                        accent = StudioCyan,
                    )

                    Text("Encoder Quality", style = MaterialTheme.typography.labelMedium, color = StudioTextSecondary)
                    StudioChipRow(
                        options = listOf("medium" to "Standard", "high" to "High", "ultra" to "Master"),
                        selectedKey = exportSettings.quality,
                        onSelect = { q ->
                            viewModel.dispatchCommand(ProjectCommands.updateExportSettings { it.copy(quality = q) })
                        },
                        accent = StudioAmber,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Include Audio Flag", style = MaterialTheme.typography.bodyMedium, color = StudioTextPrimary)
                            Text(
                                "Note: On-device MediaCodec renders deterministic H.264 video frames.",
                                style = MaterialTheme.typography.labelSmall,
                                color = StudioTextSecondary,
                            )
                        }
                        Switch(
                            checked = exportSettings.includeAudio,
                            onCheckedChange = { checked ->
                                viewModel.dispatchCommand(ProjectCommands.updateExportSettings { it.copy(includeAudio = checked) })
                            },
                        )
                    }

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
                                Text(prog.stage, style = MaterialTheme.typography.bodySmall, color = StudioTextPrimary)
                                Text(
                                    "${prog.currentFrame}/${prog.totalFrames}",
                                    style = StudioMonoStyle,
                                    color = StudioPrimary,
                                )
                            }
                            LinearProgressIndicator(
                                progress = { frac },
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                color = StudioPrimary,
                                trackColor = StudioSurfaceElevated,
                            )
                            OutlinedButton(
                                onClick = { viewModel.cancelExport() },
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.align(Alignment.End).testTag("export_cancel_btn"),
                            ) {
                                Icon(Icons.Default.Cancel, contentDescription = null, tint = StudioRose, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.export_cancel_render), color = StudioRose)
                            }
                        }
                    } else {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Button(
                                onClick = { mp4Launcher.launch("${safeSlug}.mp4") },
                                shape = RoundedCornerShape(4.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = StudioPrimary, contentColor = Color.White),
                                modifier = Modifier.testTag("export_mp4_btn"),
                            ) {
                                Icon(Icons.Default.VideoFile, contentDescription = null, modifier = Modifier.size(17.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.export_render_video))
                            }
                            Button(
                                onClick = { gifLauncher.launch("${safeSlug}.gif") },
                                shape = RoundedCornerShape(4.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = StudioEmerald, contentColor = Color.Black),
                                modifier = Modifier.testTag("export_gif_btn"),
                            ) {
                                Icon(Icons.Default.GifBox, contentDescription = null, modifier = Modifier.size(17.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.export_render_gif), fontWeight = FontWeight.SemiBold)
                            }
                            OutlinedButton(
                                onClick = { pngLauncher.launch("${safeSlug}_f${state.currentFrame}.png") },
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.testTag("export_png_btn"),
                            ) {
                                Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(17.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.export_png_snapshot))
                            }
                        }
                    }
                }
            }

            // Portable Project Archive (.motionx)
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(StudioPanel)
                        .border(1.dp, StudioBorder, RoundedCornerShape(6.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SectionHeader(title = "Portable .motionx Project Archive")
                    Text(
                        text = "Export or import full wire-compatible Motion Studio project JSON archives including compositions, keyframes, modifiers, characters, and timed lyrics.",
                        style = MaterialTheme.typography.bodySmall,
                        color = StudioTextSecondary,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = { motionXSaveLauncher.launch("${safeSlug}.motionx.json") },
                            shape = RoundedCornerShape(4.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = StudioEmerald),
                            modifier = Modifier.testTag("export_motionx_btn"),
                        ) {
                            Icon(Icons.Default.SaveAlt, contentDescription = null, tint = Color.Black, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.export_motionx), color = Color.Black, fontWeight = FontWeight.SemiBold)
                        }
                        OutlinedButton(
                            onClick = { motionXOpenLauncher.launch(arrayOf("application/json", "application/octet-stream", "*/*")) },
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.testTag("import_motionx_btn"),
                        ) {
                            Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.export_import_motionx))
                        }
                    }
                }
            }

            // Quick Local Projects Switcher
            item {
                SectionHeader(title = "Saved Local Projects")
            }

            items(state.savedProjects, key = { it.id }) { summary ->
                val liveProj = if (summary.id == state.project.id) state.project else summary.previewProject
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(StudioPanel)
                        .border(1.dp, StudioBorder, RoundedCornerShape(4.dp))
                        .clickable { viewModel.openSavedProject(summary.id) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    com.example.ui.components.ProjectThumbnailCanvas(
                        project = liveProj,
                        preferredFrame = if (summary.id == state.project.id) state.currentFrame else null,
                        widthPx = 192,
                        heightPx = 108,
                        modifier = Modifier
                            .width(72.dp)
                            .height(42.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .border(1.dp, StudioBorder, RoundedCornerShape(4.dp)),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            liveProj?.name ?: summary.name,
                            style = MaterialTheme.typography.titleSmall,
                            color = StudioTextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "${summary.width}×${summary.height} · ${summary.compositionCount} comp(s) · ${summary.updatedAt.take(16).replace('T', ' ')}",
                            style = StudioMonoStyle,
                            color = StudioTextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Icon(Icons.Default.Download, contentDescription = "Open project", tint = StudioTextSecondary, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
