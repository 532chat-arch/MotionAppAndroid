package com.example

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.SpriteBackgroundRemovalDialog
import com.example.ui.components.StudioTopBar
import com.example.ui.screens.AssetLibraryWorkspace
import com.example.ui.screens.CharacterStudioWorkspace
import com.example.ui.screens.CompositionWorkspace
import com.example.ui.screens.ExportWorkspace
import com.example.ui.screens.HomeWorkspace
import com.example.ui.screens.McpWorkspace
import com.example.ui.screens.ProjectTimelineWorkspace
import com.example.ui.theme.MotionStudioTheme
import com.example.ui.theme.StudioBg
import com.example.ui.theme.StudioBorderStrong
import com.example.ui.theme.StudioEmerald
import com.example.ui.theme.StudioPanel
import com.example.ui.theme.StudioRose
import com.example.ui.theme.StudioTextPrimary
import com.example.viewmodel.StudioViewModel
import com.example.viewmodel.WorkspaceTab

class MainActivity : ComponentActivity() {
    private val viewModel: StudioViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
        )
        setContent {
            MotionStudioTheme {
                MotionStudioApp(viewModel = viewModel)
            }
        }
    }
}

@Composable
private fun MotionStudioApp(viewModel: StudioViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Back gesture steps cleanly back through workspaces with HOME as the root hub
    BackHandler(enabled = state.workspace != WorkspaceTab.HOME) {
        if (state.workspace == WorkspaceTab.COMPOSITION) {
            viewModel.setWorkspace(WorkspaceTab.HOME)
        } else {
            viewModel.setWorkspace(WorkspaceTab.COMPOSITION)
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(StudioBg),
        containerColor = StudioBg,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
        ) {
            val isCompact = maxWidth < 840.dp

            Column(modifier = Modifier.fillMaxSize()) {
                if (state.workspace != WorkspaceTab.HOME) {
                    StudioTopBar(
                        state = state,
                        viewModel = viewModel,
                        isCompact = isCompact,
                    )
                }

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when (state.workspace) {
                        WorkspaceTab.HOME -> HomeWorkspace(state = state, viewModel = viewModel)
                        WorkspaceTab.COMPOSITION -> CompositionWorkspace(state = state, viewModel = viewModel)
                        WorkspaceTab.MOVIE_TIMELINE -> ProjectTimelineWorkspace(state = state, viewModel = viewModel)
                        WorkspaceTab.ASSETS -> AssetLibraryWorkspace(state = state, viewModel = viewModel)
                        WorkspaceTab.CHARACTERS -> CharacterStudioWorkspace(state = state, viewModel = viewModel)
                        WorkspaceTab.EXPORT -> ExportWorkspace(state = state, viewModel = viewModel)
                        WorkspaceTab.MCP -> McpWorkspace(state = state, viewModel = viewModel)
                    }

                    // Floating Toast / Feedback Banner
                    androidx.compose.animation.AnimatedVisibility(
                        visible = state.feedback != null,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                    ) {
                        val fb = state.feedback
                        if (fb != null) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (fb.isError) Color(0xF02B1919) else StudioPanel)
                                    .border(
                                        1.dp,
                                        if (fb.isError) StudioRose.copy(alpha = 0.85f) else StudioEmerald.copy(alpha = 0.85f),
                                        RoundedCornerShape(4.dp),
                                    )
                                    .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp)
                                    .testTag("feedback_banner"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    imageVector = if (fb.isError) Icons.Default.Error else Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = if (fb.isError) StudioRose else StudioEmerald,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(
                                    text = fb.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = StudioTextPrimary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                IconButton(
                                    onClick = { viewModel.clearFeedback() },
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = stringResource(R.string.cd_dismiss_feedback),
                                        tint = StudioTextPrimary.copy(alpha = 0.75f),
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            val bgTargetAsset = state.bgRemovalAssetId?.let { id ->
                state.project.assets.find { it.id == id }
            }
            if (bgTargetAsset != null) {
                SpriteBackgroundRemovalDialog(
                    asset = bgTargetAsset,
                    isApplying = state.isRemovingBackground,
                    onDismiss = { viewModel.closeBackgroundRemoval() },
                    onApply = { options, useOriginalSource, saveAsCopy ->
                        viewModel.applySpriteBackgroundRemoval(
                            assetId = bgTargetAsset.id,
                            options = options,
                            useOriginalSource = useOriginalSource,
                            saveAsCopy = saveAsCopy,
                        )
                    },
                    onRestoreOriginal = {
                        viewModel.restoreSpriteOriginalBackground(bgTargetAsset.id)
                    },
                )
            }
        }
    }
}
