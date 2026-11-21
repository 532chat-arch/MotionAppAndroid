package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.commands.ProjectCommands
import com.example.commands.uid
import com.example.engine.evaluateProjectTimelineAtFrame
import com.example.engine.getProjectMovieDurationFrames
import com.example.renderer.AndroidCanvasRenderer
import com.example.schema.ProjectTimelineClip
import com.example.schema.TransitionType
import com.example.schema.formatTimecode
import com.example.schema.fpsToDouble
import com.example.ui.components.NumberScrubber
import com.example.ui.components.SectionHeader
import com.example.ui.components.StudioChipRow
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
import com.example.viewmodel.WorkspaceTab
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun ProjectTimelineWorkspace(
    state: StudioUiState,
    viewModel: StudioViewModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val project = state.project
    val fps = remember(project.fps) { fpsToDouble(project.fps) }
    val totalFrames = remember(project.projectTimeline) { getProjectMovieDurationFrames(project) }
    var selectedClipId by remember(project.projectTimeline.size) {
        mutableStateOf(project.projectTimeline.firstOrNull()?.id)
    }
    val selectedClip = project.projectTimeline.find { it.id == selectedClipId } ?: project.projectTimeline.firstOrNull()

    val movieState = remember(project, state.movieFrame) {
        evaluateProjectTimelineAtFrame(project, state.movieFrame)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(StudioBg),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 1040.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Movie Monitor Preview
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(StudioPanel)
                        .border(1.dp, StudioBorder, RoundedCornerShape(16.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SectionHeader(
                        title = stringResource(R.string.movie_title),
                        trailing = {
                            Text(
                                text = "${formatTimecode(state.movieFrame, fps)} / ${formatTimecode(totalFrames, fps)}  (F${state.movieFrame})",
                                style = StudioMonoStyle,
                                color = StudioCyan,
                            )
                        },
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(
                                (project.defaultWidth.toFloat() / max(1, project.defaultHeight).toFloat()).coerceIn(0.6f, 2.2f)
                            )
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black)
                            .border(1.dp, StudioBorderStrong, RoundedCornerShape(12.dp)),
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val w = project.defaultWidth.toFloat().coerceAtLeast(1f)
                            val h = project.defaultHeight.toFloat().coerceAtLeast(1f)
                            val scale = min(size.width / w, size.height / h)
                            val dx = (size.width - w * scale) * 0.5f
                            val dy = (size.height - h * scale) * 0.5f
                            drawIntoCanvas { canvas ->
                                val nc = canvas.nativeCanvas
                                nc.save()
                                nc.translate(dx, dy)
                                nc.scale(scale, scale)
                                nc.clipRect(0f, 0f, w, h)
                                AndroidCanvasRenderer.renderMovieFrame(
                                    canvas = nc,
                                    context = context,
                                    project = project,
                                    movieState = movieState,
                                    width = project.defaultWidth,
                                    height = project.defaultHeight,
                                )
                                nc.restore()
                            }
                        }

                        if (movieState.transitionType != TransitionType.CUT && movieState.transitionProgress < 1.0) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(10.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xCC111317))
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            ) {
                                Text(
                                    text = "${movieState.transitionType.name.replace('_', ' ')} ${(movieState.transitionProgress * 100).roundToInt()}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = StudioAmber,
                                )
                            }
                        }
                    }

                    // Transport Controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        IconButton(
                            onClick = { viewModel.setMovieFrame(0) },
                            modifier = Modifier.testTag("movie_jump_start"),
                        ) {
                            Icon(Icons.Default.SkipPrevious, contentDescription = "Jump to movie start", tint = StudioTextPrimary)
                        }
                        Button(
                            onClick = { viewModel.toggleMoviePlayback() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (state.isMoviePlaying) StudioAmber else StudioPrimary
                            ),
                            modifier = Modifier.testTag("movie_play_toggle"),
                        ) {
                            Icon(
                                if (state.isMoviePlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(if (state.isMoviePlaying) stringResource(R.string.action_pause) else stringResource(R.string.action_play))
                        }
                        IconButton(
                            onClick = { viewModel.setMovieFrame(max(0, totalFrames - 1)) },
                            modifier = Modifier.testTag("movie_jump_end"),
                        ) {
                            Icon(Icons.Default.SkipNext, contentDescription = "Jump to movie end", tint = StudioTextPrimary)
                        }
                        Slider(
                            value = state.movieFrame.toFloat().coerceIn(0f, max(1, totalFrames - 1).toFloat()),
                            onValueChange = { viewModel.setMovieFrame(it.roundToInt()) },
                            valueRange = 0f..max(1, totalFrames - 1).toFloat(),
                            colors = SliderDefaults.colors(
                                thumbColor = StudioCyan,
                                activeTrackColor = StudioCyan,
                                inactiveTrackColor = StudioSurfaceElevated,
                            ),
                            modifier = Modifier.weight(1f).testTag("movie_scrub_slider"),
                        )
                    }
                }
            }

            // Append Composition Strip
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(StudioPanel)
                        .border(1.dp, StudioBorder, RoundedCornerShape(14.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SectionHeader(title = stringResource(R.string.movie_append_comp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        project.compositions.forEach { comp ->
                            OutlinedButton(
                                onClick = {
                                    val end = project.projectTimeline.maxOfOrNull { it.startFrame + it.durationFrames } ?: 0
                                    val clip = ProjectTimelineClip(
                                        id = uid("mclip"),
                                        compositionId = comp.id,
                                        startFrame = end,
                                        durationFrames = comp.settings.durationFrames,
                                        transitionIn = if (project.projectTimeline.isEmpty()) TransitionType.CUT else TransitionType.CROSS_DISSOLVE,
                                        transitionDurationFrames = if (project.projectTimeline.isEmpty()) 0 else 18,
                                    )
                                    viewModel.dispatchCommand(ProjectCommands.addMovieClip(clip))
                                    selectedClipId = clip.id
                                },
                                modifier = Modifier.testTag("append_comp_${comp.id}"),
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("${comp.name} (${comp.settings.durationFrames}f)")
                            }
                        }
                    }
                }
            }

            // Sequencer Track Strip
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(StudioPanel)
                        .border(1.dp, StudioBorder, RoundedCornerShape(14.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SectionHeader(
                        title = "Sequenced Clips",
                        trailing = {
                            Text("${project.projectTimeline.size} clip(s)", style = StudioMonoStyle, color = StudioTextSecondary)
                        },
                    )
                    if (project.projectTimeline.isEmpty()) {
                        Text(
                            text = stringResource(R.string.movie_no_clips),
                            style = MaterialTheme.typography.bodyMedium,
                            color = StudioTextSecondary,
                            modifier = Modifier.padding(vertical = 12.dp),
                        )
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            project.projectTimeline.forEachIndexed { idx, clip ->
                                val comp = project.compositions.find { it.id == clip.compositionId }
                                val isSelected = selectedClip?.id == clip.id
                                Column(
                                    modifier = Modifier
                                        .width(200.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isSelected) StudioPrimary.copy(alpha = 0.22f) else StudioBg)
                                        .border(
                                            1.5.dp,
                                            if (isSelected) StudioPrimaryBright else StudioBorder,
                                            RoundedCornerShape(12.dp),
                                        )
                                        .clickable {
                                            selectedClipId = clip.id
                                            viewModel.setMovieFrame(clip.startFrame)
                                        }
                                        .padding(12.dp)
                                        .testTag("movie_clip_card_${clip.id}"),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                            Icon(Icons.Default.Movie, contentDescription = null, tint = StudioCyan, modifier = Modifier.size(15.dp))
                                            Text(
                                                text = "#${idx + 1}",
                                                style = StudioMonoStyle,
                                                color = StudioCyan,
                                            )
                                        }
                                        if (clip.transitionIn != TransitionType.CUT) {
                                            Text(
                                                text = clip.transitionIn.name.replace('_', ' '),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = StudioAmber,
                                            )
                                        }
                                    }
                                    Text(
                                        text = comp?.name ?: "Missing Comp",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = StudioTextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = "F${clip.startFrame} → F${clip.startFrame + clip.durationFrames} (${clip.durationFrames}f)",
                                        style = StudioMonoStyle,
                                        color = StudioTextSecondary,
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        IconButton(
                                            onClick = { viewModel.dispatchCommand(ProjectCommands.reorderMovieClip(clip.id, -1)) },
                                            enabled = idx > 0,
                                            modifier = Modifier.size(36.dp),
                                        ) {
                                            Icon(
                                                Icons.AutoMirrored.Filled.ArrowBack,
                                                contentDescription = "Move clip earlier",
                                                tint = if (idx > 0) StudioTextPrimary else StudioTextSecondary.copy(alpha = 0.3f),
                                                modifier = Modifier.size(18.dp),
                                            )
                                        }
                                        IconButton(
                                            onClick = { viewModel.dispatchCommand(ProjectCommands.reorderMovieClip(clip.id, 1)) },
                                            enabled = idx < project.projectTimeline.lastIndex,
                                            modifier = Modifier.size(36.dp),
                                        ) {
                                            Icon(
                                                Icons.AutoMirrored.Filled.ArrowForward,
                                                contentDescription = "Move clip later",
                                                tint = if (idx < project.projectTimeline.lastIndex) StudioTextPrimary else StudioTextSecondary.copy(alpha = 0.3f),
                                                modifier = Modifier.size(18.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Selected Clip Inspector
            if (selectedClip != null) {
                item {
                    val clipComp = project.compositions.find { it.id == selectedClip.compositionId }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(StudioPanel)
                            .border(1.dp, StudioBorder, RoundedCornerShape(14.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        SectionHeader(
                            title = "${stringResource(R.string.movie_clip_inspector)} · ${clipComp?.name ?: ""}",
                            trailing = {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = {
                                            viewModel.setActiveComposition(selectedClip.compositionId)
                                            viewModel.setWorkspace(WorkspaceTab.COMPOSITION)
                                        },
                                        modifier = Modifier.testTag("movie_open_comp_editor"),
                                    ) {
                                        Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(stringResource(R.string.action_open_editor))
                                    }
                                    IconButton(
                                        onClick = {
                                            viewModel.dispatchCommand(ProjectCommands.deleteMovieClip(selectedClip.id))
                                            selectedClipId = null
                                        },
                                        modifier = Modifier.testTag("movie_delete_clip"),
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Remove clip", tint = StudioRose)
                                    }
                                }
                            },
                        )

                        NumberScrubber(
                            label = "Start Frame",
                            value = selectedClip.startFrame.toDouble(),
                            step = 6.0,
                            min = 0.0,
                            decimals = 0,
                            suffix = "f",
                            onChange = { v ->
                                viewModel.dispatchCommand(
                                    ProjectCommands.updateMovieClip(selectedClip.id) { it.copy(startFrame = max(0, v.roundToInt())) }
                                )
                            },
                        )

                        NumberScrubber(
                            label = "Duration",
                            value = selectedClip.durationFrames.toDouble(),
                            step = 12.0,
                            min = 12.0,
                            decimals = 0,
                            suffix = "f",
                            onChange = { v ->
                                viewModel.dispatchCommand(
                                    ProjectCommands.updateMovieClip(selectedClip.id) { it.copy(durationFrames = max(12, v.roundToInt())) }
                                )
                            },
                        )

                        Text("Transition In", style = MaterialTheme.typography.labelMedium, color = StudioTextSecondary)
                        StudioChipRow(
                            options = listOf(
                                TransitionType.CUT.name to "Hard Cut",
                                TransitionType.CROSS_DISSOLVE.name to "Cross Dissolve",
                                TransitionType.FADE_BLACK.name to "Fade via Black",
                            ),
                            selectedKey = selectedClip.transitionIn.name,
                            onSelect = { key ->
                                val t = TransitionType.valueOf(key)
                                viewModel.dispatchCommand(
                                    ProjectCommands.updateMovieClip(selectedClip.id) {
                                        it.copy(
                                            transitionIn = t,
                                            transitionDurationFrames = if (t == TransitionType.CUT) 0 else max(12, it.transitionDurationFrames),
                                        )
                                    }
                                )
                            },
                            accent = StudioAmber,
                        )

                        if (selectedClip.transitionIn != TransitionType.CUT) {
                            NumberScrubber(
                                label = "Transition Duration",
                                value = selectedClip.transitionDurationFrames.toDouble(),
                                step = 4.0,
                                min = 4.0,
                                max = max(12.0, selectedClip.durationFrames / 2.0),
                                decimals = 0,
                                suffix = "f",
                                onChange = { v ->
                                    viewModel.dispatchCommand(
                                        ProjectCommands.updateMovieClip(selectedClip.id) {
                                            it.copy(transitionDurationFrames = v.roundToInt().coerceAtLeast(4))
                                        }
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
