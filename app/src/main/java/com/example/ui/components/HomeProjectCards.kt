package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.DemoProjectInfo
import com.example.renderer.ProjectThumbnailCache
import com.example.renderer.extractNormalizedKeyframePositions
import com.example.renderer.pickRepresentativeFrame
import com.example.schema.MotionProject
import com.example.schema.fpsToDouble
import com.example.storage.SavedProjectSummary
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioEmerald
import com.example.ui.theme.StudioMonoStyle
import com.example.ui.theme.StudioPanel
import com.example.ui.theme.StudioPrimary
import com.example.ui.theme.StudioPrimaryBright
import com.example.ui.theme.StudioRose
import com.example.ui.theme.StudioSecondary
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.StudioTextMuted
import com.example.ui.theme.StudioTextPrimary
import com.example.ui.theme.StudioTextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Renders a cached, off-thread dynamic thumbnail preview of a MotionProject composition.
 * Supports optional live animated playback and interactive horizontal timeline scrubbing.
 */
@Composable
fun ProjectThumbnailCanvas(
    project: MotionProject?,
    compositionId: String? = null,
    preferredFrame: Int? = null,
    isPlaying: Boolean = false,
    interactiveScrub: Boolean = false,
    showBottomScrim: Boolean = false,
    widthPx: Int = 440,
    heightPx: Int = 248,
    onFrameChanged: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val comp = remember(project, compositionId) {
        if (project == null) null
        else compositionId?.let { id -> project.compositions.find { it.id == id } }
            ?: project.compositions.firstOrNull()
    }
    val durationFrames = max(1, comp?.settings?.durationFrames ?: 1)
    val maxFrame = max(0, durationFrames - 1)
    val fps = remember(project, comp) {
        if (project == null) 24.0
        else fpsToDouble(comp?.settings?.fpsOverride ?: project.fps).coerceAtLeast(1.0)
    }

    val defaultFrame = remember(project?.id, project?.updatedAt, preferredFrame) {
        if (project != null) pickRepresentativeFrame(project, preferredFrame) else 0
    }
    var displayFrame by remember(project?.id, preferredFrame) { mutableIntStateOf(defaultFrame) }
    var isScrubbing by remember { mutableStateOf(false) }

    LaunchedEffect(defaultFrame, isPlaying, isScrubbing) {
        if (!isPlaying && !isScrubbing) {
            displayFrame = defaultFrame
        }
    }

    LaunchedEffect(project?.id, comp?.id, isPlaying, isScrubbing, durationFrames, fps) {
        if (!isPlaying || isScrubbing || project == null || durationFrames <= 1) return@LaunchedEffect
        val tickMs = 66L
        val frameStep = max(1, (fps * tickMs / 1000.0).roundToInt())
        while (isActive) {
            delay(tickMs)
            displayFrame = (displayFrame + frameStep) % durationFrames
            onFrameChanged?.invoke(displayFrame)
        }
    }

    val cachedInitial = remember(project?.id, project?.updatedAt, comp?.id, displayFrame, widthPx, heightPx) {
        if (project != null && comp != null) {
            ProjectThumbnailCache.getCached(project, comp.id, displayFrame, widthPx, heightPx)
        } else null
    }
    var bitmap by remember(project?.id, comp?.id) { mutableStateOf<ImageBitmap?>(cachedInitial) }

    LaunchedEffect(project, comp?.id, displayFrame, widthPx, heightPx) {
        if (project == null || comp == null) {
            bitmap = null
            return@LaunchedEffect
        }
        val rendered = ProjectThumbnailCache.renderThumbnail(
            context = context,
            project = project,
            compId = comp.id,
            frame = displayFrame,
            widthPx = widthPx,
            heightPx = heightPx,
        )
        if (rendered != null) {
            bitmap = rendered
        }
    }

    val keyframeTicks = remember(comp) { extractNormalizedKeyframePositions(comp) }

    Box(
        modifier = modifier.background(StudioSurface),
        contentAlignment = Alignment.Center,
    ) {
        val currentBitmap = bitmap
        if (currentBitmap != null) {
            Image(
                bitmap = currentBitmap,
                contentDescription = project?.name?.let { "$it preview frame $displayFrame" },
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                imageVector = Icons.Default.Movie,
                contentDescription = null,
                tint = StudioTextMuted,
                modifier = Modifier.size(24.dp),
            )
        }

        if (showBottomScrim) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(42.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color(0xCC090909)),
                        )
                    ),
            )
        }

        // Hairline interactive bottom scrub strip layered crisply above scrim
        if (interactiveScrub && project != null && durationFrames > 1) {
            val progress = (displayFrame.toFloat() / max(1, maxFrame).toFloat()).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(20.dp)
                    .pointerInput(project.id, durationFrames) {
                        detectTapGestures { offset ->
                            val frac = (offset.x / size.width.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
                            val target = (frac * maxFrame).roundToInt().coerceIn(0, maxFrame)
                            displayFrame = target
                            onFrameChanged?.invoke(target)
                        }
                    }
                    .pointerInput(project.id, durationFrames) {
                        detectHorizontalDragGestures(
                            onDragStart = { isScrubbing = true },
                            onDragEnd = { isScrubbing = false },
                            onDragCancel = { isScrubbing = false },
                            onHorizontalDrag = { change, _ ->
                                change.consume()
                                val frac = (change.position.x / size.width.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
                                val target = (frac * maxFrame).roundToInt().coerceIn(0, maxFrame)
                                displayFrame = target
                                onFrameChanged?.invoke(target)
                            },
                        )
                    },
                contentAlignment = Alignment.BottomCenter,
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (isPlaying || isScrubbing) 3.5.dp else 2.5.dp),
                ) {
                    drawRect(color = Color(0x880B0B0B))
                    drawRect(
                        color = if (isPlaying || isScrubbing) StudioPrimary else StudioPrimary.copy(alpha = 0.78f),
                        size = size.copy(width = size.width * progress),
                    )
                    if (isPlaying || isScrubbing) {
                        keyframeTicks.forEach { norm ->
                            val kx = norm * size.width
                            drawLine(
                                color = StudioPrimaryBright.copy(alpha = 0.92f),
                                start = Offset(kx, 0f),
                                end = Offset(kx, size.height),
                                strokeWidth = 2f,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Compact, high-focus "Continue Editing" banner card so recent projects stay immediately visible.
 */
@Composable
internal fun ActiveProjectSpotlightCard(
    project: MotionProject,
    activeCompositionId: String,
    currentFrame: Int,
    isPreviewPlaying: Boolean,
    onTogglePreviewPlay: () -> Unit,
    onContinueEditing: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeComp = project.compositions.find { it.id == activeCompositionId } ?: project.compositions.first()
    val fps = fpsToDouble(activeComp.settings.fpsOverride ?: project.fps).roundToInt().coerceAtLeast(1)
    val durationSec = activeComp.settings.durationFrames / fps.toFloat()
    val aspectLabel = formatAspectLabel(activeComp.settings.width, activeComp.settings.height)

    OutlinedCard(
        onClick = onContinueEditing,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = StudioPanel,
            contentColor = StudioTextPrimary,
        ),
        border = BorderStroke(1.dp, StudioPrimary.copy(alpha = 0.4f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("home_resume_button"),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(StudioPrimary.copy(alpha = 0.12f), Color.Transparent),
                    )
                )
                .padding(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Compact live 16:9 thumbnail preview
                Box(
                    modifier = Modifier
                        .width(96.dp)
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, StudioBorder, RoundedCornerShape(8.dp)),
                ) {
                    ProjectThumbnailCanvas(
                        project = project,
                        compositionId = activeCompositionId,
                        preferredFrame = currentFrame,
                        isPlaying = isPreviewPlaying,
                        interactiveScrub = false,
                        widthPx = 320,
                        heightPx = 180,
                        modifier = Modifier.fillMaxSize(),
                    )
                    ThumbnailPlayButton(
                        isPlaying = isPreviewPlaying,
                        onClick = onTogglePreviewPlay,
                        tag = "spotlight_preview_play",
                        visualSize = 26.dp,
                        iconSize = 14.dp,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }

                // Project title & concise single-line specs
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (isPreviewPlaying) StudioPrimary else StudioEmerald),
                        )
                        Text(
                            text = stringResource(R.string.home_continue_label).uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                letterSpacing = 0.6.sp,
                            ),
                            color = StudioPrimaryBright,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                        )
                    }
                    Text(
                        text = project.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                        fontWeight = FontWeight.SemiBold,
                        color = StudioTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "$aspectLabel · ${fps}fps · %.1fs".format(durationSec),
                        style = StudioMonoStyle.copy(fontSize = 11.sp),
                        color = StudioTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // Clean single primary Resume button
                Button(
                    onClick = onContinueEditing,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StudioPrimary,
                        contentColor = Color.White,
                    ),
                    modifier = Modifier
                        .height(36.dp)
                        .minimumInteractiveComponentSize(),
                ) {
                    Text(
                        text = stringResource(R.string.home_open_short),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

/**
 * Clean visual-first recent project card with unobstructed dynamic thumbnail preview.
 */
@Composable
internal fun RecentProjectCard(
    summary: SavedProjectSummary,
    liveProject: MotionProject?,
    liveFrame: Int?,
    isCurrent: Boolean,
    isPreviewPlaying: Boolean,
    onTogglePreviewPlay: () -> Unit,
    onOpen: () -> Unit,
    onDuplicate: () -> Unit,
    onRequestDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val resolvedProject = liveProject ?: summary.previewProject
    val resolvedComp = resolvedProject?.compositions?.firstOrNull()
    val width = resolvedComp?.settings?.width ?: summary.width
    val height = resolvedComp?.settings?.height ?: summary.height
    val durationFrames = resolvedComp?.settings?.durationFrames ?: summary.durationFrames
    val fps = if (resolvedProject != null) {
        fpsToDouble(resolvedComp?.settings?.fpsOverride ?: resolvedProject.fps).roundToInt().coerceAtLeast(1)
    } else summary.fps.coerceAtLeast(1)
    val durationSec = durationFrames / fps.toFloat()
    val aspectLabel = formatAspectLabel(width, height)

    var activePreviewFrame by remember(summary.id, liveFrame) {
        mutableIntStateOf(
            if (resolvedProject != null) pickRepresentativeFrame(resolvedProject, liveFrame) else 0
        )
    }

    OutlinedCard(
        onClick = onOpen,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = StudioPanel,
            contentColor = StudioTextPrimary,
        ),
        border = BorderStroke(
            width = if (isCurrent) 1.5.dp else 1.dp,
            color = if (isCurrent) StudioPrimary else StudioBorder,
        ),
        modifier = modifier.testTag("saved_project_${summary.id}"),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Unobstructed 16:9 Live Dynamic Thumbnail Preview
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(StudioSurface),
            ) {
                ProjectThumbnailCanvas(
                    project = resolvedProject,
                    preferredFrame = liveFrame,
                    isPlaying = isPreviewPlaying,
                    interactiveScrub = true,
                    showBottomScrim = true,
                    onFrameChanged = { activePreviewFrame = it },
                    modifier = Modifier.fillMaxSize(),
                )

                // Minimal duration / live frame text bottom-left
                Text(
                    text = if (isPreviewPlaying) {
                        "F$activePreviewFrame/$durationFrames"
                    } else {
                        "%.1fs".format(durationSec)
                    },
                    style = StudioMonoStyle.copy(fontSize = 10.sp),
                    color = if (isPreviewPlaying) StudioPrimaryBright else Color.White.copy(alpha = 0.92f),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 8.dp, bottom = 8.dp),
                )

                // Compact live Play/Pause preview toggle bottom-right
                if (resolvedProject != null && durationFrames > 1) {
                    ThumbnailPlayButton(
                        isPlaying = isPreviewPlaying,
                        onClick = onTogglePreviewPlay,
                        tag = "preview_play_${summary.id}",
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 2.dp, bottom = 4.dp),
                    )
                }
            }

            // Clean 2-Line Footer + Overflow Menu
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = 2.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        if (isCurrent) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(StudioPrimary),
                            )
                        }
                        Text(
                            text = resolvedProject?.name ?: summary.name,
                            style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.sp),
                            fontWeight = FontWeight.SemiBold,
                            color = StudioTextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = "$aspectLabel · ${fps}fps",
                        style = StudioMonoStyle.copy(fontSize = 10.sp),
                        color = StudioTextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier
                            .size(36.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("project_menu_${summary.id}"),
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.home_project_more_options),
                            tint = StudioTextSecondary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        modifier = Modifier
                            .background(StudioSurfaceElevated)
                            .border(1.dp, StudioBorder, RoundedCornerShape(8.dp)),
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.home_action_open), color = StudioTextPrimary) },
                            leadingIcon = { Icon(Icons.Default.FolderOpen, contentDescription = null, tint = StudioPrimary) },
                            onClick = {
                                menuExpanded = false
                                onOpen()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.home_action_duplicate), color = StudioTextPrimary) },
                            leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, tint = StudioSecondary) },
                            onClick = {
                                menuExpanded = false
                                onDuplicate()
                            },
                            modifier = Modifier.testTag("duplicate_project_${summary.id}"),
                        )
                        HorizontalDivider(color = StudioBorder)
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_delete), color = StudioRose) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = StudioRose) },
                            onClick = {
                                menuExpanded = false
                                onRequestDelete()
                            },
                            modifier = Modifier.testTag("delete_project_${summary.id}"),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Compact horizontal-carousel card for Starter Templates.
 */
@Composable
internal fun DemoTemplateCard(
    demo: DemoProjectInfo,
    isPreviewPlaying: Boolean,
    onTogglePreviewPlay: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val previewProject = remember(demo.id) { demo.builder() }
    val previewComp = previewProject.compositions.first()
    val aspectLabel = formatAspectLabel(previewComp.settings.width, previewComp.settings.height)

    OutlinedCard(
        onClick = onOpen,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = StudioPanel,
            contentColor = StudioTextPrimary,
        ),
        border = BorderStroke(1.dp, StudioBorder),
        modifier = modifier.testTag("demo_card_${demo.id}"),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(StudioSurface),
            ) {
                ProjectThumbnailCanvas(
                    project = previewProject,
                    preferredFrame = 36,
                    isPlaying = isPreviewPlaying,
                    interactiveScrub = true,
                    showBottomScrim = true,
                    widthPx = 380,
                    heightPx = 214,
                    modifier = Modifier.fillMaxSize(),
                )
                Text(
                    text = demo.badge.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.sp,
                        letterSpacing = 0.5.sp,
                    ),
                    color = StudioSecondary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 8.dp, bottom = 8.dp),
                )
                ThumbnailPlayButton(
                    isPlaying = isPreviewPlaying,
                    onClick = onTogglePreviewPlay,
                    tag = "demo_preview_play_${demo.id}",
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 2.dp, bottom = 4.dp),
                )
            }

            Column(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = demo.title,
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = StudioTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${previewComp.layers.size} layers · $aspectLabel",
                    style = StudioMonoStyle.copy(fontSize = 10.sp),
                    color = StudioTextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ThumbnailPlayButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    tag: String,
    visualSize: Dp = 30.dp,
    iconSize: Dp = 16.dp,
    modifier: Modifier = Modifier,
) {
    // Full 48dp accessible hit area wrapping a subtle, clean studio preview toggle
    Box(
        modifier = modifier
            .size(44.dp)
            .minimumInteractiveComponentSize()
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(visualSize)
                .clip(CircleShape)
                .background(if (isPlaying) StudioPrimary else Color(0xB8111111))
                .border(
                    width = 1.dp,
                    color = if (isPlaying) StudioPrimaryBright else Color.White.copy(alpha = 0.22f),
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = stringResource(
                    if (isPlaying) R.string.home_preview_pause else R.string.home_preview_play
                ),
                tint = Color.White,
                modifier = Modifier.size(iconSize),
            )
        }
    }
}

private fun formatAspectLabel(width: Int, height: Int): String {
    if (width <= 0 || height <= 0) return "16:9"
    val ratio = width.toFloat() / height.toFloat()
    return when {
        abs(ratio - 16f / 9f) < 0.08f -> "16:9"
        abs(ratio - 9f / 16f) < 0.08f -> "9:16"
        abs(ratio - 1f) < 0.05f -> "1:1"
        abs(ratio - 4f / 3f) < 0.06f -> "4:3"
        else -> "${width}×${height}"
    }
}
