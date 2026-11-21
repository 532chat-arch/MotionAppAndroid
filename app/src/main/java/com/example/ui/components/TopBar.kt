package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PermMedia
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.commands.ProjectCommands
import com.example.ui.theme.StudioAmber
import com.example.ui.theme.StudioBg
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioEmerald
import com.example.ui.theme.StudioPanel
import com.example.ui.theme.StudioPrimary
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.StudioTextMuted
import com.example.ui.theme.StudioTextPrimary
import com.example.ui.theme.StudioTextSecondary
import com.example.viewmodel.SaveStatus
import com.example.viewmodel.StudioUiState
import com.example.viewmodel.StudioViewModel
import com.example.viewmodel.WorkspaceTab

private data class WorkspaceTabMeta(
    val tab: WorkspaceTab,
    val labelRes: Int,
    val icon: ImageVector,
)

private val WORKSPACE_TABS = listOf(
    WorkspaceTabMeta(WorkspaceTab.HOME, R.string.tab_home, Icons.Default.Home),
    WorkspaceTabMeta(WorkspaceTab.COMPOSITION, R.string.tab_composition, Icons.Default.Layers),
    WorkspaceTabMeta(WorkspaceTab.MOVIE_TIMELINE, R.string.tab_movie_timeline, Icons.Default.Movie),
    WorkspaceTabMeta(WorkspaceTab.ASSETS, R.string.tab_assets, Icons.Default.PermMedia),
    WorkspaceTabMeta(WorkspaceTab.CHARACTERS, R.string.tab_characters, Icons.Default.Face),
    WorkspaceTabMeta(WorkspaceTab.EXPORT, R.string.tab_export, Icons.Default.Download),
    WorkspaceTabMeta(WorkspaceTab.MCP, R.string.tab_mcp, Icons.Default.Hub),
)

@Composable
fun StudioTopBar(
    state: StudioUiState,
    viewModel: StudioViewModel,
    isCompact: Boolean,
    modifier: Modifier = Modifier,
) {
    var showRenameDialog by remember { mutableStateOf(false) }
    var showQuickExportDialog by remember { mutableStateOf(false) }
    var renameText by remember(state.project.name) { mutableStateOf(state.project.name) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(StudioPanel),
    ) {
        // Primary 48dp Studio Header Bar: Brand + Project Title + Undo/Redo/Mute + Primary Export
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(4.dp))
                    .clickable {
                        renameText = state.project.name
                        showRenameDialog = true
                    }
                    .padding(horizontal = 4.dp, vertical = 4.dp)
                    .testTag("project_title_button"),
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_motion_studio_logo),
                    contentDescription = stringResource(R.string.app_name),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .border(1.dp, StudioBorder, RoundedCornerShape(6.dp))
                        .testTag("topbar_brand_logo"),
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = state.project.name,
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 13.sp, lineHeight = 16.sp),
                            color = StudioTextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.Default.Create,
                            contentDescription = stringResource(R.string.action_rename),
                            tint = StudioTextMuted,
                            modifier = Modifier.size(12.dp),
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        val (statusText, statusColor) = when (state.saveStatus) {
                            SaveStatus.SAVED -> stringResource(R.string.status_saved) to StudioEmerald
                            SaveStatus.SAVING -> stringResource(R.string.status_saving) to StudioAmber
                            SaveStatus.UNSAVED -> stringResource(R.string.status_unsaved) to StudioTextSecondary
                        }
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(statusColor),
                        )
                        Text(
                            text = "$statusText · ${state.activeComposition.name}",
                            style = MaterialTheme.typography.labelSmall,
                            color = StudioTextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            // Right utility actions & primary Export trigger
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                IconButton(
                    onClick = { viewModel.undo() },
                    enabled = state.canUndo,
                    modifier = Modifier.size(36.dp).testTag("undo_button"),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Undo,
                        contentDescription = stringResource(R.string.action_undo),
                        tint = if (state.canUndo) StudioTextPrimary else StudioTextMuted.copy(alpha = 0.45f),
                        modifier = Modifier.size(18.dp),
                    )
                }
                IconButton(
                    onClick = { viewModel.redo() },
                    enabled = state.canRedo,
                    modifier = Modifier.size(36.dp).testTag("redo_button"),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Redo,
                        contentDescription = stringResource(R.string.action_redo),
                        tint = if (state.canRedo) StudioTextPrimary else StudioTextMuted.copy(alpha = 0.45f),
                        modifier = Modifier.size(18.dp),
                    )
                }
                IconButton(
                    onClick = { viewModel.toggleMasterMute() },
                    modifier = Modifier.size(36.dp).testTag("mute_audio_button"),
                ) {
                    Icon(
                        if (state.masterMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = stringResource(
                            if (state.masterMuted) R.string.action_unmute_audio else R.string.action_mute_audio
                        ),
                        tint = if (state.masterMuted) StudioAmber else StudioTextSecondary,
                        modifier = Modifier.size(18.dp),
                    )
                }
                if (!isCompact) {
                    val icon = if (state.saveStatus == SaveStatus.SAVING) Icons.Default.Sync else Icons.Default.CloudDone
                    Icon(
                        icon,
                        contentDescription = stringResource(R.string.status_saved),
                        tint = if (state.saveStatus == SaveStatus.SAVED) StudioEmerald else StudioAmber,
                        modifier = Modifier.padding(horizontal = 4.dp).size(16.dp),
                    )
                }

                Spacer(Modifier.width(4.dp))

                // Primary Toolbar Export Action (GIF / MP4 + Share Sheet)
                Row(
                    modifier = Modifier
                        .height(30.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (state.isExporting) StudioAmber else StudioPrimary)
                        .clickable { showQuickExportDialog = true }
                        .padding(horizontal = 10.dp)
                        .testTag("topbar_export_button"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = stringResource(R.string.action_export),
                        tint = Color.White,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = if (state.isExporting) "RENDERING" else stringResource(R.string.action_export).uppercase(),
                        style = MaterialTheme.typography.titleSmall.copy(fontSize = 10.sp),
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                }
            }
        }

        HorizontalDivider(thickness = 1.dp, color = StudioBorder)

        // Docked Studio Workspace Tab Strip (2dp #4B9BE8 active bottom underline)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .background(StudioBg)
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (meta in WORKSPACE_TABS) {
                val selected = state.workspace == meta.tab
                Column(
                    modifier = Modifier
                        .width(IntrinsicSize.Max)
                        .fillMaxHeight()
                        .background(if (selected) StudioPanel else Color.Transparent)
                        .clickable { viewModel.setWorkspace(meta.tab) }
                        .testTag("workspace_tab_${meta.tab.name.lowercase()}"),
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            meta.icon,
                            contentDescription = null,
                            tint = if (selected) StudioPrimary else StudioTextMuted,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = stringResource(meta.labelRes).uppercase(),
                            style = MaterialTheme.typography.titleSmall,
                            color = if (selected) StudioTextPrimary else StudioTextSecondary,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            maxLines = 1,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(if (selected) StudioPrimary else Color.Transparent),
                    )
                }
            }
        }

        HorizontalDivider(thickness = 1.dp, color = StudioBorder)
    }

    if (showQuickExportDialog) {
        QuickExportDialog(
            state = state,
            viewModel = viewModel,
            onDismiss = { showQuickExportDialog = false },
        )
    }

    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            containerColor = StudioSurfaceElevated,
            shape = RoundedCornerShape(6.dp),
            title = { Text(stringResource(R.string.action_rename), color = StudioTextPrimary) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    label = { Text("Project Name") },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.dispatchCommand(ProjectCommands.renameProject(renameText))
                        showRenameDialog = false
                    }
                ) { Text(stringResource(R.string.action_save), color = StudioPrimary) }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text(stringResource(R.string.action_cancel), color = StudioTextSecondary)
                }
            },
        )
    }
}
