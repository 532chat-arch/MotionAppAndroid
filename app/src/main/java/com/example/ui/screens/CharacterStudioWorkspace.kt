package com.example.ui.screens

import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.assets.ProceduralAssetRenderer
import com.example.commands.ProjectCommands
import com.example.commands.uid
import com.example.schema.AssetCategory
import com.example.schema.AssetKind
import com.example.schema.CharacterDefinition
import com.example.schema.SpriteAnimationClip
import com.example.schema.SpriteGridConfig
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
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CharacterStudioWorkspace(
    state: StudioUiState,
    viewModel: StudioViewModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val characters = state.project.characters
    var selectedCharId by remember(characters.size) { mutableStateOf(characters.firstOrNull()?.id) }
    val activeChar = characters.find { it.id == selectedCharId } ?: characters.firstOrNull()

    var draftChar by remember(activeChar?.id, activeChar) {
        mutableStateOf(
            activeChar ?: CharacterDefinition(
                id = uid("char"),
                name = "New Character",
                assetId = state.project.assets.firstOrNull { it.kind == AssetKind.IMAGE }?.id ?: "",
                grid = SpriteGridConfig(rows = 2, cols = 4, cellWidth = 128, cellHeight = 128),
                clips = listOf(SpriteAnimationClip(uid("clip"), "Walk", listOf(0, 1, 2, 3), 10.0, true)),
                defaultClipId = "",
            )
        )
    }

    var activeClipId by remember(draftChar.id) {
        mutableStateOf(draftChar.clips.firstOrNull()?.id ?: "")
    }
    val activeClip = draftChar.clips.find { it.id == activeClipId } ?: draftChar.clips.firstOrNull()
    var previewPlaying by remember { mutableStateOf(true) }
    var previewTick by remember { mutableIntStateOf(0) }

    LaunchedEffect(previewPlaying, activeClip?.id, activeClip?.fps, activeClip?.frameIndices) {
        if (!previewPlaying || activeClip == null || activeClip.frameIndices.isEmpty()) return@LaunchedEffect
        val delayMs = (1000.0 / max(1.0, activeClip.fps)).toLong().coerceIn(33L, 1000L)
        while (true) {
            delay(delayMs)
            previewTick = (previewTick + 1) % max(1, activeClip.frameIndices.size)
        }
    }

    val sheetLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.importImageUris(listOf(uri), category = AssetCategory.CHARACTER) { imported ->
                val cols = max(1, draftChar.grid.cols)
                val rows = max(1, draftChar.grid.rows)
                val cw = (imported.width ?: 512) / cols
                val ch = (imported.height ?: 256) / rows
                draftChar = draftChar.copy(
                    assetId = imported.id,
                    grid = draftChar.grid.copy(cellWidth = max(16, cw), cellHeight = max(16, ch)),
                )
            }
        }
    }

    val sheetAsset = state.project.assets.find { it.id == draftChar.assetId }
    val sheetBitmap = remember(sheetAsset?.dataUrl) {
        sheetAsset?.let { ProceduralAssetRenderer.getBitmap(context, it.dataUrl) }
    }
    val totalCells = max(1, draftChar.grid.rows * draftChar.grid.cols)

    fun updateActiveClip(transform: (SpriteAnimationClip) -> SpriteAnimationClip) {
        val cid = activeClip?.id ?: return
        draftChar = draftChar.copy(clips = draftChar.clips.map { if (it.id == cid) transform(it) else it })
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
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Character Selector & Actions
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
                        title = stringResource(R.string.characters_title),
                        trailing = {
                            OutlinedButton(
                                onClick = {
                                    val newClip = SpriteAnimationClip(uid("clip"), "Idle", listOf(0, 1), 8.0, true)
                                    val created = CharacterDefinition(
                                        id = uid("char"),
                                        name = "Character ${characters.size + 1}",
                                        assetId = state.project.assets.firstOrNull { it.kind == AssetKind.IMAGE }?.id ?: "",
                                        grid = SpriteGridConfig(2, 4, 128, 128),
                                        clips = listOf(newClip),
                                        defaultClipId = newClip.id,
                                    )
                                    draftChar = created
                                    activeClipId = newClip.id
                                    selectedCharId = created.id
                                },
                                modifier = Modifier.testTag("characters_new_btn"),
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.characters_new))
                            }
                        },
                    )

                    if (characters.isNotEmpty()) {
                        StudioChipRow(
                            options = characters.map { it.id to it.name },
                            selectedKey = draftChar.id,
                            onSelect = { id ->
                                selectedCharId = id
                                characters.find { it.id == id }?.let {
                                    draftChar = it
                                    activeClipId = it.clips.firstOrNull()?.id ?: ""
                                }
                            },
                        )
                    }

                    OutlinedTextField(
                        value = draftChar.name,
                        onValueChange = { draftChar = draftChar.copy(name = it) },
                        label = { Text("Character Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("character_name_input"),
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = {
                                sheetLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            modifier = Modifier.testTag("characters_import_sheet_btn"),
                        ) {
                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.characters_import_sheet))
                        }

                        if (sheetAsset != null) {
                            OutlinedButton(
                                onClick = { viewModel.openBackgroundRemoval(sheetAsset.id) },
                                modifier = Modifier.testTag("characters_remove_bg_btn"),
                            ) {
                                Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = StudioCyan, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.bg_remove_action))
                            }
                        }

                        Button(
                            onClick = {
                                val normalized = draftChar.copy(
                                    defaultClipId = draftChar.defaultClipId.ifEmpty { draftChar.clips.firstOrNull()?.id ?: "" }
                                )
                                viewModel.dispatchCommand(ProjectCommands.upsertCharacter(normalized))
                                selectedCharId = normalized.id
                                viewModel.showFeedback(false, "Saved character \"${normalized.name}\"")
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = StudioPrimary),
                            modifier = Modifier.testTag("characters_save_btn"),
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.action_save))
                        }

                        OutlinedButton(
                            onClick = {
                                val normalized = draftChar.copy(
                                    defaultClipId = draftChar.defaultClipId.ifEmpty { draftChar.clips.firstOrNull()?.id ?: "" }
                                )
                                viewModel.dispatchCommand(ProjectCommands.upsertCharacter(normalized))
                                viewModel.placeCharacterInScene(normalized)
                            },
                            modifier = Modifier.testTag("characters_place_scene_btn"),
                        ) {
                            Icon(Icons.Default.Face, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.characters_place_scene))
                        }
                    }
                }
            }

            // Live Sprite Playback Preview
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
                    val currentFrameIndex = activeClip?.frameIndices?.getOrNull(
                        previewTick % max(1, activeClip.frameIndices.size)
                    ) ?: 0

                    SectionHeader(
                        title = "Live Clip Preview · ${activeClip?.name ?: "None"}",
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Cell #$currentFrameIndex", style = StudioMonoStyle, color = StudioAmber)
                                IconButton(
                                    onClick = { previewPlaying = !previewPlaying },
                                    modifier = Modifier.testTag("character_preview_toggle"),
                                ) {
                                    Icon(
                                        if (previewPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = "Toggle sprite preview",
                                        tint = StudioTextPrimary,
                                    )
                                }
                            }
                        },
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(210.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF0C0F14))
                            .border(1.dp, StudioBorderStrong, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (sheetBitmap != null) {
                            Canvas(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                                val g = draftChar.grid
                                val cols = max(1, g.cols)
                                val row = currentFrameIndex / cols
                                val col = currentFrameIndex % cols
                                val sx = g.marginX + col * (g.cellWidth + g.spacingX)
                                val sy = g.marginY + row * (g.cellHeight + g.spacingY)
                                val src = Rect(
                                    sx.coerceIn(0, max(0, sheetBitmap.width - 1)),
                                    sy.coerceIn(0, max(0, sheetBitmap.height - 1)),
                                    (sx + g.cellWidth).coerceIn(1, sheetBitmap.width),
                                    (sy + g.cellHeight).coerceIn(1, sheetBitmap.height),
                                )
                                val aspect = max(1, g.cellWidth).toFloat() / max(1, g.cellHeight).toFloat()
                                val dstH = min(size.height, size.width / aspect)
                                val dstW = dstH * aspect
                                val left = (size.width - dstW) * 0.5f
                                val top = (size.height - dstH) * 0.5f
                                drawIntoCanvas { c ->
                                    c.nativeCanvas.drawBitmap(
                                        sheetBitmap,
                                        src,
                                        RectF(left, top, left + dstW, top + dstH),
                                        Paint(Paint.FILTER_BITMAP_FLAG),
                                    )
                                }
                            }
                        } else {
                            Text("Import or pick a sprite sheet image", color = StudioTextSecondary)
                        }
                    }
                }
            }

            // Sprite Sheet Grid Slicing
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(StudioPanel)
                        .border(1.dp, StudioBorder, RoundedCornerShape(16.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SectionHeader(title = "Sprite Sheet Grid Slicing")
                    NumberScrubber(
                        label = "Columns",
                        value = draftChar.grid.cols.toDouble(),
                        step = 1.0,
                        min = 1.0,
                        max = 16.0,
                        decimals = 0,
                        onChange = { v ->
                            val cols = v.roundToInt().coerceAtLeast(1)
                            val autoW = sheetBitmap?.let { max(16, it.width / cols) } ?: draftChar.grid.cellWidth
                            draftChar = draftChar.copy(grid = draftChar.grid.copy(cols = cols, cellWidth = autoW))
                        },
                    )
                    NumberScrubber(
                        label = "Rows",
                        value = draftChar.grid.rows.toDouble(),
                        step = 1.0,
                        min = 1.0,
                        max = 16.0,
                        decimals = 0,
                        onChange = { v ->
                            val rows = v.roundToInt().coerceAtLeast(1)
                            val autoH = sheetBitmap?.let { max(16, it.height / rows) } ?: draftChar.grid.cellHeight
                            draftChar = draftChar.copy(grid = draftChar.grid.copy(rows = rows, cellHeight = autoH))
                        },
                    )
                    NumberScrubber(
                        label = "Cell Width",
                        value = draftChar.grid.cellWidth.toDouble(),
                        step = 8.0,
                        min = 16.0,
                        decimals = 0,
                        suffix = "px",
                        onChange = { v -> draftChar = draftChar.copy(grid = draftChar.grid.copy(cellWidth = v.roundToInt())) },
                    )
                    NumberScrubber(
                        label = "Cell Height",
                        value = draftChar.grid.cellHeight.toDouble(),
                        step = 8.0,
                        min = 16.0,
                        decimals = 0,
                        suffix = "px",
                        onChange = { v -> draftChar = draftChar.copy(grid = draftChar.grid.copy(cellHeight = v.roundToInt())) },
                    )
                }
            }

            // Animation Clips & Interactive Frame Picker
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
                        title = "Animation Clips",
                        trailing = {
                            OutlinedButton(
                                onClick = {
                                    val c = SpriteAnimationClip(uid("clip"), "Clip ${draftChar.clips.size + 1}", listOf(0, 1), 10.0, true)
                                    draftChar = draftChar.copy(clips = draftChar.clips + c)
                                    activeClipId = c.id
                                },
                                modifier = Modifier.testTag("add_sprite_clip_btn"),
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Add Clip")
                            }
                        },
                    )

                    StudioChipRow(
                        options = draftChar.clips.map { it.id to "${it.name} (${it.frameIndices.size}f)" },
                        selectedKey = activeClip?.id ?: "",
                        onSelect = { activeClipId = it },
                        accent = StudioCyan,
                    )

                    if (activeClip != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedTextField(
                                value = activeClip.name,
                                onValueChange = { txt -> updateActiveClip { it.copy(name = txt) } },
                                label = { Text("Clip Name") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            if (draftChar.clips.size > 1) {
                                IconButton(
                                    onClick = {
                                        val remaining = draftChar.clips.filterNot { it.id == activeClip.id }
                                        draftChar = draftChar.copy(clips = remaining)
                                        activeClipId = remaining.firstOrNull()?.id ?: ""
                                    },
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete clip", tint = StudioRose)
                                }
                            }
                        }

                        NumberScrubber(
                            label = "Clip FPS",
                            value = activeClip.fps,
                            step = 1.0,
                            min = 1.0,
                            max = 60.0,
                            decimals = 0,
                            suffix = " fps",
                            onChange = { v -> updateActiveClip { it.copy(fps = v) } },
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Loop Clip", style = MaterialTheme.typography.bodyMedium, color = StudioTextPrimary)
                            Switch(
                                checked = activeClip.loop,
                                onCheckedChange = { checked -> updateActiveClip { it.copy(loop = checked) } },
                            )
                        }

                        Text(
                            text = "Tap sprite cells to toggle frames in \"${activeClip.name}\" (Active: ${activeClip.frameIndices.joinToString(", ")})",
                            style = MaterialTheme.typography.labelMedium,
                            color = StudioTextSecondary,
                        )

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            for (cellIdx in 0 until totalCells.coerceAtMost(32)) {
                                val inClip = cellIdx in activeClip.frameIndices
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (inClip) StudioPrimary.copy(alpha = 0.28f) else StudioBg)
                                        .border(
                                            1.5.dp,
                                            if (inClip) StudioPrimaryBright else StudioBorder,
                                            RoundedCornerShape(10.dp),
                                        )
                                        .clickable {
                                            updateActiveClip { clip ->
                                                val next = if (cellIdx in clip.frameIndices && clip.frameIndices.size > 1) {
                                                    clip.frameIndices - cellIdx
                                                } else if (cellIdx !in clip.frameIndices) {
                                                    clip.frameIndices + cellIdx
                                                } else clip.frameIndices
                                                clip.copy(frameIndices = next)
                                            }
                                        }
                                        .testTag("sprite_cell_$cellIdx"),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (sheetBitmap != null) {
                                        Canvas(modifier = Modifier.fillMaxSize().padding(4.dp)) {
                                            val g = draftChar.grid
                                            val cols = max(1, g.cols)
                                            val row = cellIdx / cols
                                            val col = cellIdx % cols
                                            val sx = g.marginX + col * (g.cellWidth + g.spacingX)
                                            val sy = g.marginY + row * (g.cellHeight + g.spacingY)
                                            val src = Rect(
                                                sx.coerceIn(0, max(0, sheetBitmap.width - 1)),
                                                sy.coerceIn(0, max(0, sheetBitmap.height - 1)),
                                                (sx + g.cellWidth).coerceIn(1, sheetBitmap.width),
                                                (sy + g.cellHeight).coerceIn(1, sheetBitmap.height),
                                            )
                                            drawIntoCanvas { c ->
                                                c.nativeCanvas.drawBitmap(
                                                    sheetBitmap,
                                                    src,
                                                    RectF(0f, 0f, size.width, size.height),
                                                    Paint(Paint.FILTER_BITMAP_FLAG),
                                                )
                                            }
                                        }
                                    }
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .padding(3.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color(0xBB111317))
                                            .padding(horizontal = 4.dp, vertical = 1.dp),
                                    ) {
                                        Text(
                                            "$cellIdx",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (inClip) StudioEmerald else StudioTextSecondary,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
