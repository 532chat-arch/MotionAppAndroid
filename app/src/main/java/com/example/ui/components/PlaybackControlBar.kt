package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.schema.formatTimecode
import com.example.schema.getActiveCompFps
import com.example.ui.theme.StudioAmber
import com.example.ui.theme.StudioBg
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioBorderStrong
import com.example.ui.theme.StudioEmerald
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
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun PlaybackControlBar(
    state: StudioUiState,
    viewModel: StudioViewModel,
    modifier: Modifier = Modifier,
) {
    val comp = state.activeComposition
    val fps = getActiveCompFps(state.project, comp.id)
    val duration = max(1, comp.settings.durationFrames)
    val maxFrame = max(0, duration - 1)
    val timecode = formatTimecode(state.currentFrame, fps)

    val playBg by animateColorAsState(
        targetValue = if (state.isPlaying) StudioEmerald else StudioPrimary,
        label = "playBg",
    )
    val pauseBg by animateColorAsState(
        targetValue = if (!state.isPlaying) StudioSurfaceElevated else StudioAmber.copy(alpha = 0.22f),
        label = "pauseBg",
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF171B23), StudioPanel),
                )
            )
            .testTag("playback_control_bar"),
    ) {
        val isCompact = maxWidth < 640.dp

        Column(modifier = Modifier.fillMaxWidth()) {
            HorizontalDivider(color = StudioBorderStrong)

            if (isCompact) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    // Status + Timecode + Loop Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        PlaybackStatusBadge(isPlaying = state.isPlaying, fps = fps.roundToInt())
                        FrameTimecodeChip(timecode = timecode, currentFrame = state.currentFrame, maxFrame = maxFrame)
                        LoopToggleButton(
                            loopEnabled = state.loopPlayback,
                            onClick = { viewModel.toggleLoopPlayback() },
                        )
                    }

                    // Centered Transport & Frame-by-Frame Buttons Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        TransportIconButton(
                            icon = Icons.Default.SkipPrevious,
                            description = stringResource(R.string.timeline_jump_start),
                            enabled = state.currentFrame > 0 || state.isPlaying,
                            tag = "playback_jump_start_button",
                            onClick = {
                                viewModel.stopPlayback()
                                viewModel.setCurrentFrame(0)
                            },
                        )
                        FrameStepButton(
                            icon = Icons.Default.ChevronLeft,
                            label = stringResource(R.string.playback_prev_frame_short),
                            description = stringResource(R.string.timeline_step_back),
                            leadingIcon = true,
                            enabled = state.currentFrame > 0,
                            tag = "playback_prev_frame_button",
                            onClick = { viewModel.stepFrame(-1) },
                        )
                        TransportActionPill(
                            icon = Icons.Default.PlayArrow,
                            label = stringResource(R.string.action_play),
                            containerColor = playBg,
                            borderColor = if (state.isPlaying) StudioEmerald else StudioPrimaryBright,
                            contentColor = Color.White,
                            active = state.isPlaying,
                            tag = "playback_play_button",
                            onClick = { viewModel.startPlayback() },
                        )
                        TransportActionPill(
                            icon = Icons.Default.Pause,
                            label = stringResource(R.string.action_pause),
                            containerColor = pauseBg,
                            borderColor = if (state.isPlaying) StudioAmber else StudioBorderStrong,
                            contentColor = if (state.isPlaying) StudioAmber else StudioTextPrimary,
                            active = !state.isPlaying,
                            tag = "playback_pause_button",
                            onClick = { viewModel.stopPlayback() },
                        )
                        FrameStepButton(
                            icon = Icons.Default.ChevronRight,
                            label = stringResource(R.string.playback_next_frame_short),
                            description = stringResource(R.string.timeline_step_forward),
                            leadingIcon = false,
                            enabled = state.currentFrame < maxFrame,
                            tag = "playback_next_frame_button",
                            onClick = { viewModel.stepFrame(1) },
                        )
                        TransportIconButton(
                            icon = Icons.Default.SkipNext,
                            description = stringResource(R.string.timeline_jump_end),
                            enabled = state.currentFrame < maxFrame || state.isPlaying,
                            tag = "playback_jump_end_button",
                            onClick = {
                                viewModel.stopPlayback()
                                viewModel.setCurrentFrame(maxFrame)
                            },
                        )
                    }
                }
            } else {
                // Wide / Tablet Single-Row Transport Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    PlaybackStatusBadge(isPlaying = state.isPlaying, fps = fps.roundToInt())

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        TransportIconButton(
                            icon = Icons.Default.SkipPrevious,
                            description = stringResource(R.string.timeline_jump_start),
                            enabled = state.currentFrame > 0 || state.isPlaying,
                            tag = "playback_jump_start_button",
                            onClick = {
                                viewModel.stopPlayback()
                                viewModel.setCurrentFrame(0)
                            },
                        )
                        FrameStepButton(
                            icon = Icons.Default.ChevronLeft,
                            label = stringResource(R.string.playback_prev_frame_short),
                            description = stringResource(R.string.timeline_step_back),
                            leadingIcon = true,
                            enabled = state.currentFrame > 0,
                            tag = "playback_prev_frame_button",
                            onClick = { viewModel.stepFrame(-1) },
                        )
                        TransportActionPill(
                            icon = Icons.Default.PlayArrow,
                            label = stringResource(R.string.action_play),
                            containerColor = playBg,
                            borderColor = if (state.isPlaying) StudioEmerald else StudioPrimaryBright,
                            contentColor = Color.White,
                            active = state.isPlaying,
                            tag = "playback_play_button",
                            onClick = { viewModel.startPlayback() },
                        )
                        TransportActionPill(
                            icon = Icons.Default.Pause,
                            label = stringResource(R.string.action_pause),
                            containerColor = pauseBg,
                            borderColor = if (state.isPlaying) StudioAmber else StudioBorderStrong,
                            contentColor = if (state.isPlaying) StudioAmber else StudioTextPrimary,
                            active = !state.isPlaying,
                            tag = "playback_pause_button",
                            onClick = { viewModel.stopPlayback() },
                        )
                        FrameStepButton(
                            icon = Icons.Default.ChevronRight,
                            label = stringResource(R.string.playback_next_frame_short),
                            description = stringResource(R.string.timeline_step_forward),
                            leadingIcon = false,
                            enabled = state.currentFrame < maxFrame,
                            tag = "playback_next_frame_button",
                            onClick = { viewModel.stepFrame(1) },
                        )
                        TransportIconButton(
                            icon = Icons.Default.SkipNext,
                            description = stringResource(R.string.timeline_jump_end),
                            enabled = state.currentFrame < maxFrame || state.isPlaying,
                            tag = "playback_jump_end_button",
                            onClick = {
                                viewModel.stopPlayback()
                                viewModel.setCurrentFrame(maxFrame)
                            },
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FrameTimecodeChip(timecode = timecode, currentFrame = state.currentFrame, maxFrame = maxFrame)
                        LoopToggleButton(
                            loopEnabled = state.loopPlayback,
                            onClick = { viewModel.toggleLoopPlayback() },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaybackStatusBadge(
    isPlaying: Boolean,
    fps: Int,
) {
    val dotColor = if (isPlaying) StudioEmerald else StudioTextMuted
    val statusText = stringResource(
        if (isPlaying) R.string.playback_status_playing else R.string.playback_status_paused
    )
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(StudioBg)
            .border(1.dp, if (isPlaying) StudioEmerald.copy(alpha = 0.5f) else StudioBorder, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .testTag("playback_status_badge"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(dotColor),
        )
        Text(
            text = "$statusText · ${fps}fps",
            style = MaterialTheme.typography.labelSmall,
            color = if (isPlaying) StudioEmerald else StudioTextSecondary,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun FrameTimecodeChip(
    timecode: String,
    currentFrame: Int,
    maxFrame: Int,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF0D1016))
            .border(1.dp, StudioBorder, RoundedCornerShape(8.dp))
            .padding(horizontal = 9.dp, vertical = 4.dp)
            .testTag("playback_timecode_chip"),
    ) {
        Text(
            text = "$timecode · F$currentFrame/$maxFrame",
            style = StudioMonoStyle,
            color = StudioAmber,
            maxLines = 1,
        )
    }
}

@Composable
private fun TransportActionPill(
    icon: ImageVector,
    label: String,
    containerColor: Color,
    borderColor: Color,
    contentColor: Color,
    active: Boolean,
    tag: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .heightIn(min = 38.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(containerColor)
            .border(if (active) 1.5.dp else 1.dp, borderColor, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp)
            .testTag(tag)
            .semantics { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(18.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = contentColor,
            maxLines = 1,
        )
    }
}

@Composable
private fun FrameStepButton(
    icon: ImageVector,
    label: String,
    description: String,
    leadingIcon: Boolean,
    enabled: Boolean,
    tag: String,
    onClick: () -> Unit,
) {
    val fg = if (enabled) StudioTextPrimary else StudioTextMuted.copy(alpha = 0.4f)
    Row(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .heightIn(min = 38.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(StudioBg)
            .border(1.dp, StudioBorder, RoundedCornerShape(9.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 7.dp)
            .testTag(tag)
            .semantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        if (leadingIcon) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(17.dp))
            Text(label, style = StudioMonoStyle.copy(fontSize = 10.sp), color = fg)
        } else {
            Text(label, style = StudioMonoStyle.copy(fontSize = 10.sp), color = fg)
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(17.dp))
        }
    }
}

@Composable
private fun TransportIconButton(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    tag: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .size(38.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(StudioBg)
            .border(1.dp, StudioBorder, RoundedCornerShape(9.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .testTag(tag)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (enabled) StudioTextSecondary else StudioTextMuted.copy(alpha = 0.35f),
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun LoopToggleButton(
    loopEnabled: Boolean,
    onClick: () -> Unit,
) {
    val desc = stringResource(R.string.timeline_toggle_loop)
    Box(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .size(36.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(if (loopEnabled) StudioPrimary.copy(alpha = 0.24f) else StudioBg)
            .border(
                1.dp,
                if (loopEnabled) StudioPrimaryBright.copy(alpha = 0.6f) else StudioBorder,
                RoundedCornerShape(9.dp),
            )
            .clickable(onClick = onClick)
            .testTag("playback_loop_button")
            .semantics { contentDescription = desc },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Repeat,
            contentDescription = desc,
            tint = if (loopEnabled) StudioPrimaryBright else StudioTextMuted,
            modifier = Modifier.size(16.dp),
        )
    }
}
