package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.assets.ProceduralAssetRenderer
import com.example.commands.ProjectCommands
import com.example.schema.AssetCategory
import com.example.schema.AssetKind
import com.example.schema.AssetRecord
import com.example.schema.SceneObjectDocument
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
import com.example.ui.theme.StudioRose
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.StudioTextPrimary
import com.example.ui.theme.StudioTextSecondary
import com.example.viewmodel.StudioUiState
import com.example.viewmodel.StudioViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AssetLibraryWorkspace(
    state: StudioUiState,
    viewModel: StudioViewModel,
    modifier: Modifier = Modifier,
) {
    var selectedFilter by remember { mutableStateOf("ALL") }
    var search by remember { mutableStateOf("") }
    var pendingReplaceAssetId by remember { mutableStateOf<String?>(null) }

    val pickImagesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(12)
    ) { uris ->
        if (uris.isNotEmpty()) {
            val cat = runCatching { AssetCategory.valueOf(selectedFilter) }.getOrDefault(AssetCategory.PROP)
            viewModel.importImageUris(uris, category = if (cat == AssetCategory.AUDIO) AssetCategory.PROP else cat)
        }
    }

    val replaceImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        val targetId = pendingReplaceAssetId
        pendingReplaceAssetId = null
        if (uri != null && targetId != null) {
            viewModel.importImageUris(listOf(uri), replaceAssetId = targetId)
        }
    }

    val pickAudioLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.importAudioUri(uri, addToTimeline = false)
    }

    val filteredAssets = remember(state.project.assets, selectedFilter, search) {
        val q = search.trim().lowercase()
        state.project.assets.filter { asset ->
            val catOk = selectedFilter == "ALL" || asset.category.name == selectedFilter
            val searchOk = q.isEmpty() || asset.name.lowercase().contains(q)
            catOk && searchOk
        }
    }

    val usageCounts = remember(state.project) {
        val map = mutableMapOf<String, Int>()
        for (c in state.project.compositions) {
            for (l in c.layers) {
                for (o in l.objects) {
                    if (o is SceneObjectDocument.SpriteObject) {
                        map[o.assetId] = (map[o.assetId] ?: 0) + 1
                    }
                }
            }
            for (t in c.audioTracks) {
                for (cl in t.clips) {
                    map[cl.assetId] = (map[cl.assetId] ?: 0) + 1
                }
            }
        }
        for (ch in state.project.characters) {
            map[ch.assetId] = (map[ch.assetId] ?: 0) + 1
        }
        map
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
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(StudioPanel)
                        .border(1.dp, StudioBorder, RoundedCornerShape(16.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SectionHeader(
                        title = stringResource(R.string.assets_title),
                        trailing = {
                            Text(
                                "${state.project.assets.size} asset(s)",
                                style = StudioMonoStyle,
                                color = StudioTextSecondary,
                            )
                        },
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = {
                                pickImagesLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = StudioPrimary),
                            modifier = Modifier.testTag("assets_import_images_btn"),
                        ) {
                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.assets_import_images))
                        }
                        OutlinedButton(
                            onClick = { pickAudioLauncher.launch(arrayOf("audio/*")) },
                            modifier = Modifier.testTag("assets_import_audio_btn"),
                        ) {
                            Icon(Icons.Default.AudioFile, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.assets_import_audio))
                        }
                    }

                    StudioChipRow(
                        options = listOf(
                            "ALL" to "All",
                            AssetCategory.CHARACTER.name to "Characters",
                            AssetCategory.PROP.name to "Props",
                            AssetCategory.BACKGROUND.name to "Backgrounds",
                            AssetCategory.EFFECT.name to "Effects",
                            AssetCategory.AUDIO.name to "Audio",
                        ),
                        selectedKey = selectedFilter,
                        onSelect = { selectedFilter = it },
                    )

                    OutlinedTextField(
                        value = search,
                        onValueChange = { search = it },
                        placeholder = { Text(stringResource(R.string.assets_search)) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = StudioTextSecondary) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("assets_search_input"),
                    )
                }
            }

            if (filteredAssets.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(StudioPanel)
                            .border(1.dp, StudioBorder, RoundedCornerShape(14.dp))
                            .padding(28.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "No matching assets. Import images or audio clips to populate your project library.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = StudioTextSecondary,
                        )
                    }
                }
            } else {
                items(filteredAssets, key = { it.id }) { asset ->
                    AssetRowCard(
                        asset = asset,
                        usageCount = usageCounts[asset.id] ?: 0,
                        onPlace = { viewModel.placeAssetInScene(asset) },
                        onReplace = {
                            if (asset.kind == AssetKind.IMAGE) {
                                pendingReplaceAssetId = asset.id
                                replaceImageLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }
                        },
                        onRemoveBackground = { viewModel.openBackgroundRemoval(asset.id) },
                        onDelete = { viewModel.dispatchCommand(ProjectCommands.deleteAsset(asset.id)) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AssetRowCard(
    asset: AssetRecord,
    usageCount: Int,
    onPlace: () -> Unit,
    onReplace: () -> Unit,
    onRemoveBackground: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val bitmap = remember(asset.dataUrl) {
        if (asset.kind == AssetKind.IMAGE) ProceduralAssetRenderer.getBitmap(context, asset.dataUrl) else null
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(StudioPanel)
            .border(1.dp, StudioBorder, RoundedCornerShape(14.dp))
            .padding(12.dp)
            .testTag("asset_card_${asset.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(width = 88.dp, height = 64.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(StudioBg)
                .border(1.dp, StudioBorder, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (asset.kind == AssetKind.AUDIO) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(Icons.Default.MusicNote, contentDescription = null, tint = StudioEmerald, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.height(4.dp))
                    Canvas(modifier = Modifier.fillMaxWidth().height(20.dp)) {
                        val peaks = asset.waveformPeaks.ifEmpty { List(24) { 0.4f } }
                        val step = size.width / peaks.size.coerceAtLeast(1)
                        val mid = size.height / 2f
                        peaks.forEachIndexed { idx, peak ->
                            val x = idx * step + step * 0.5f
                            val h = peak.coerceIn(0.1f, 1f) * mid
                            drawLine(StudioEmerald, Offset(x, mid - h), Offset(x, mid + h), strokeWidth = 2f)
                        }
                    }
                }
            } else if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = asset.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(4.dp),
                )
            } else {
                Text("IMG", style = StudioMonoStyle, color = StudioTextSecondary)
            }
        }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = asset.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = StudioTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (asset.originalDataUrl != null) {
                    Text(
                        text = stringResource(R.string.bg_remove_cutout_badge),
                        style = StudioMonoStyle,
                        color = StudioEmerald,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(StudioEmerald.copy(alpha = 0.14f))
                            .border(1.dp, StudioEmerald.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                }
            }
            val metaText = if (asset.kind == AssetKind.AUDIO) {
                "AUDIO · %.1fs · %d use(s)".format(asset.durationSec ?: 0.0, usageCount)
            } else {
                "${asset.category.name} · ${asset.width ?: 0}×${asset.height ?: 0} · $usageCount use(s)"
            }
            Text(
                text = metaText,
                style = MaterialTheme.typography.labelSmall,
                color = StudioTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                OutlinedButton(
                    onClick = onPlace,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(34.dp).testTag("place_asset_${asset.id}"),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.assets_place_scene), style = MaterialTheme.typography.labelSmall)
                }
                if (asset.kind == AssetKind.IMAGE) {
                    OutlinedButton(
                        onClick = onRemoveBackground,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(34.dp).testTag("remove_bg_asset_${asset.id}"),
                    ) {
                        Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = StudioCyan, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.bg_remove_action), style = MaterialTheme.typography.labelSmall)
                    }
                    OutlinedButton(
                        onClick = onReplace,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(34.dp).testTag("replace_asset_${asset.id}"),
                    ) {
                        Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Replace", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        IconButton(
            onClick = onDelete,
            modifier = Modifier.testTag("delete_asset_${asset.id}"),
        ) {
            Icon(Icons.Default.Delete, contentDescription = "Delete ${asset.name}", tint = StudioRose.copy(alpha = 0.85f))
        }
    }
}
