package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MovieFilter
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.DEMO_PROJECTS
import com.example.storage.SavedProjectSummary
import com.example.ui.components.ActiveProjectSpotlightCard
import com.example.ui.components.DemoTemplateCard
import com.example.ui.components.NewCompositionBottomSheet
import com.example.ui.components.RecentProjectCard
import com.example.ui.theme.StudioBg
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioInsetField
import com.example.ui.theme.StudioMonoStyle
import com.example.ui.theme.StudioPanel
import com.example.ui.theme.StudioPrimary
import com.example.ui.theme.StudioPrimaryBright
import com.example.ui.theme.StudioRose
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.StudioTextMuted
import com.example.ui.theme.StudioTextPrimary
import com.example.ui.theme.StudioTextSecondary
import com.example.viewmodel.StudioUiState
import com.example.viewmodel.StudioViewModel
import com.example.viewmodel.WorkspaceTab

private enum class AspectFilter(val labelRes: Int) {
    ALL(R.string.home_filter_all),
    LANDSCAPE(R.string.home_filter_landscape),
    PORTRAIT(R.string.home_filter_portrait),
    SQUARE(R.string.home_filter_square),
}

@Composable
fun HomeWorkspace(
    state: StudioUiState,
    viewModel: StudioViewModel,
    modifier: Modifier = Modifier,
) {
    var showCreateSheet by remember { mutableStateOf(false) }
    var searchExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var aspectFilter by remember { mutableStateOf(AspectFilter.ALL) }
    var pendingDelete by remember { mutableStateOf<SavedProjectSummary?>(null) }
    var playingPreviewKey by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val fabExpanded by remember {
        derivedStateOf { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < 72 }
    }
    val isScrolled by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 8 }
    }

    val importMotionXLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.importMotionXFromUri(uri)
    }

    val filteredProjects = remember(state.savedProjects, searchQuery, aspectFilter) {
        val q = searchQuery.trim().lowercase()
        state.savedProjects.filter { item ->
            val matchesQuery = q.isEmpty() || item.name.lowercase().contains(q)
            val matchesAspect = when (aspectFilter) {
                AspectFilter.ALL -> true
                AspectFilter.LANDSCAPE -> item.width > item.height
                AspectFilter.PORTRAIT -> item.height > item.width
                AspectFilter.SQUARE -> item.width == item.height
            }
            matchesQuery && matchesAspect
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(StudioBg),
        containerColor = StudioBg,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(StudioBg),
            ) {
                HomeStudioHeader(
                    searchActive = searchExpanded || searchQuery.isNotEmpty(),
                    onToggleSearch = {
                        if (searchExpanded) {
                            searchQuery = ""
                            searchExpanded = false
                        } else {
                            searchExpanded = true
                        }
                    },
                    onImportArchive = {
                        playingPreviewKey = null
                        importMotionXLauncher.launch(arrayOf("application/json", "application/octet-stream", "*/*"))
                    },
                )

                AnimatedVisibility(
                    visible = searchExpanded,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(
                                text = stringResource(R.string.home_search_projects),
                                color = StudioTextMuted,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = StudioTextMuted,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    if (searchQuery.isNotEmpty()) searchQuery = ""
                                    else searchExpanded = false
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = stringResource(R.string.action_close),
                                    tint = StudioTextSecondary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = StudioPanel,
                            unfocusedContainerColor = StudioPanel,
                            focusedBorderColor = StudioPrimary,
                            unfocusedBorderColor = StudioBorder,
                            focusedTextColor = StudioTextPrimary,
                            unfocusedTextColor = StudioTextPrimary,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 10.dp)
                            .testTag("home_search_input"),
                    )
                }

                if (isScrolled || searchExpanded) {
                    HorizontalDivider(thickness = 1.dp, color = StudioBorder.copy(alpha = 0.65f))
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    playingPreviewKey = null
                    showCreateSheet = true
                },
                expanded = fabExpanded,
                icon = {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.home_create_composition),
                    )
                },
                text = {
                    Text(
                        text = stringResource(R.string.home_create_composition),
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                shape = RoundedCornerShape(14.dp),
                containerColor = StudioPrimary,
                contentColor = Color.White,
                modifier = Modifier.testTag("home_new_composition_fab"),
            )
        },
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(StudioBg),
            contentAlignment = Alignment.TopCenter,
        ) {
            val isWide = maxWidth >= 720.dp
            val projectColumns = when {
                maxWidth >= 960.dp -> 4
                maxWidth >= 640.dp -> 3
                maxWidth >= 340.dp -> 2
                else -> 1
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 1160.dp)
                    .testTag("home_workspace_list"),
                contentPadding = PaddingValues(top = 8.dp, bottom = 104.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                // 1. Compact Active Session Resume Banner
                item(key = "home_spotlight") {
                    val spotlightKey = "spotlight_${state.project.id}"
                    ActiveProjectSpotlightCard(
                        project = state.project,
                        activeCompositionId = state.activeCompositionId,
                        currentFrame = state.currentFrame,
                        isPreviewPlaying = playingPreviewKey == spotlightKey,
                        onTogglePreviewPlay = {
                            playingPreviewKey = if (playingPreviewKey == spotlightKey) null else spotlightKey
                        },
                        onContinueEditing = {
                            playingPreviewKey = null
                            viewModel.setWorkspace(WorkspaceTab.COMPOSITION)
                        },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }

                // 2. Recent Projects Header + Lightweight Inline Aspect Filter
                item(key = "recent_projects_header") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.home_recent_animations),
                                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                                    fontWeight = FontWeight.SemiBold,
                                    color = StudioTextPrimary,
                                )
                                Text(
                                    text = "${filteredProjects.size}",
                                    style = StudioMonoStyle.copy(fontSize = 11.sp),
                                    color = StudioTextMuted,
                                )
                            }

                            if (isWide && state.savedProjects.size > 1) {
                                AspectFilterStrip(
                                    selected = aspectFilter,
                                    onSelect = { aspectFilter = it },
                                )
                            }
                        }

                        if (!isWide && (state.savedProjects.size > 1 || aspectFilter != AspectFilter.ALL)) {
                            AspectFilterStrip(
                                selected = aspectFilter,
                                onSelect = { aspectFilter = it },
                            )
                        }
                    }
                }

                // 3. Visual-First 2-to-4 Column Recent Projects Gallery
                if (filteredProjects.isEmpty()) {
                    item(key = "recent_projects_empty") {
                        EmptyProjectsCard(
                            hasFilters = searchQuery.isNotBlank() || aspectFilter != AspectFilter.ALL,
                            onClearFilters = {
                                searchQuery = ""
                                aspectFilter = AspectFilter.ALL
                            },
                            onCreateNew = { showCreateSheet = true },
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                } else {
                    val rows = filteredProjects.chunked(projectColumns)
                    items(rows.size, key = { idx -> "recent_row_${rows[idx].first().id}" }) { rowIdx ->
                        val rowItems = rows[rowIdx]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            rowItems.forEach { summary ->
                                val isCurrent = summary.id == state.project.id
                                val cardPreviewKey = "recent_${summary.id}"
                                RecentProjectCard(
                                    summary = summary,
                                    liveProject = if (isCurrent) state.project else summary.previewProject,
                                    liveFrame = if (isCurrent) state.currentFrame else null,
                                    isCurrent = isCurrent,
                                    isPreviewPlaying = playingPreviewKey == cardPreviewKey,
                                    onTogglePreviewPlay = {
                                        playingPreviewKey = if (playingPreviewKey == cardPreviewKey) null else cardPreviewKey
                                    },
                                    onOpen = {
                                        playingPreviewKey = null
                                        if (isCurrent) viewModel.setWorkspace(WorkspaceTab.COMPOSITION)
                                        else viewModel.openSavedProject(summary.id)
                                    },
                                    onDuplicate = { viewModel.duplicateSavedProject(summary.id) },
                                    onRequestDelete = { pendingDelete = summary },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            repeat(projectColumns - rowItems.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }

                // 4. Starter Templates Horizontal Carousel
                item(key = "starter_templates_section") {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.home_starter_templates),
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                            fontWeight = FontWeight.SemiBold,
                            color = StudioTextPrimary,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )

                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(DEMO_PROJECTS, key = { it.id }) { demo ->
                                val demoKey = "demo_${demo.id}"
                                DemoTemplateCard(
                                    demo = demo,
                                    isPreviewPlaying = playingPreviewKey == demoKey,
                                    onTogglePreviewPlay = {
                                        playingPreviewKey = if (playingPreviewKey == demoKey) null else demoKey
                                    },
                                    onOpen = {
                                        playingPreviewKey = null
                                        val built = demo.builder()
                                        viewModel.loadProjectObject(built, switchToEditor = true)
                                        viewModel.showFeedback(false, "Loaded template \"${demo.title}\"")
                                    },
                                    modifier = Modifier.width(if (isWide) 248.dp else 216.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateSheet) {
        NewCompositionBottomSheet(
            onDismiss = { showCreateSheet = false },
            onCreate = { name, width, height, fps, durationFrames ->
                viewModel.createNewProject(name, width, height, fps, durationFrames)
                showCreateSheet = false
            },
        )
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = StudioSurfaceElevated,
            shape = RoundedCornerShape(12.dp),
            title = { Text("Delete Project?", color = StudioTextPrimary) },
            text = {
                Text(
                    "Remove \"${target.name}\" from local device storage? This cannot be undone.",
                    color = StudioTextSecondary,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteSavedProject(target.id)
                        pendingDelete = null
                    }
                ) { Text(stringResource(R.string.action_delete), color = StudioRose) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.action_cancel), color = StudioTextSecondary)
                }
            },
        )
    }
}

@Composable
private fun HomeStudioHeader(
    searchActive: Boolean,
    onToggleSearch: () -> Unit,
    onImportArchive: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.ic_motion_studio_logo),
                contentDescription = stringResource(R.string.app_name),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, StudioBorder, RoundedCornerShape(8.dp))
                    .testTag("home_brand_logo"),
            )
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 18.sp,
                    letterSpacing = (-0.3).sp,
                ),
                fontWeight = FontWeight.SemiBold,
                color = StudioTextPrimary,
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onToggleSearch,
                modifier = Modifier.testTag("home_search_toggle"),
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = stringResource(R.string.home_search_toggle),
                    tint = if (searchActive) StudioPrimaryBright else StudioTextSecondary,
                )
            }
            IconButton(
                onClick = onImportArchive,
                modifier = Modifier.testTag("home_import_motionx_button"),
            ) {
                Icon(
                    imageVector = Icons.Default.UploadFile,
                    contentDescription = stringResource(R.string.home_import_archive),
                    tint = StudioTextSecondary,
                )
            }
        }
    }
}

@Composable
private fun AspectFilterStrip(
    selected: AspectFilter,
    onSelect: (AspectFilter) -> Unit,
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AspectFilter.entries.forEach { filter ->
            val isSelected = filter == selected
            Box(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .height(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) StudioPrimary.copy(alpha = 0.2f) else StudioPanel)
                    .border(
                        width = 1.dp,
                        color = if (isSelected) StudioPrimary else StudioBorder,
                        shape = RoundedCornerShape(8.dp),
                    )
                    .clickable { onSelect(filter) }
                    .padding(horizontal = 12.dp)
                    .testTag("home_filter_${filter.name.lowercase()}"),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(filter.labelRes),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSelected) StudioPrimaryBright else StudioTextSecondary,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun EmptyProjectsCard(
    hasFilters: Boolean,
    onClearFilters: () -> Unit,
    onCreateNew: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(StudioPanel)
            .border(1.dp, StudioBorder, RoundedCornerShape(12.dp))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(StudioInsetField)
                .border(1.dp, StudioBorder, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.MovieFilter,
                contentDescription = null,
                tint = StudioPrimary,
                modifier = Modifier.size(24.dp),
            )
        }
        Text(
            text = stringResource(if (hasFilters) R.string.home_empty_search else R.string.home_no_projects),
            style = MaterialTheme.typography.bodyMedium,
            color = StudioTextSecondary,
            textAlign = TextAlign.Center,
        )
        if (hasFilters) {
            OutlinedButton(
                onClick = onClearFilters,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, StudioBorder),
                modifier = Modifier.height(44.dp),
            ) {
                Text(stringResource(R.string.home_clear_filters), color = StudioTextPrimary)
            }
        } else {
            Button(
                onClick = onCreateNew,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = StudioPrimary,
                    contentColor = Color.White,
                ),
                modifier = Modifier.height(44.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.home_create_composition))
            }
        }
    }
}
