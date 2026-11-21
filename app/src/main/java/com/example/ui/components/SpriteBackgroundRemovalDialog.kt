package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.assets.BackgroundRemovalMode
import com.example.assets.BackgroundRemovalOptions
import com.example.assets.ProceduralAssetRenderer
import com.example.assets.SpriteBackgroundRemover
import com.example.schema.AssetRecord
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
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.StudioTextPrimary
import com.example.ui.theme.StudioTextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.math.roundToInt

private data class KeyColorPreset(
    val label: String,
    val argb: Int?,
    val swatchColor: Color,
)

private val QUICK_KEY_PRESETS = listOf(
    KeyColorPreset("Auto", null, Color(0xFF38BDF8)),
    KeyColorPreset("White", 0xFFFFFFFF.toInt(), Color.White),
    KeyColorPreset("Black", 0xFF000000.toInt(), Color.Black),
    KeyColorPreset("Green", 0xFF00FF00.toInt(), Color(0xFF22C55E)),
    KeyColorPreset("Blue", 0xFF0000FF.toInt(), Color(0xFF3B82F6)),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SpriteBackgroundRemovalDialog(
    asset: AssetRecord,
    isApplying: Boolean,
    onDismiss: () -> Unit,
    onApply: (options: BackgroundRemovalOptions, useOriginalSource: Boolean, saveAsCopy: Boolean) -> Unit,
    onRestoreOriginal: () -> Unit,
) {
    val context = LocalContext.current

    var useOriginalSource by remember(asset.id) { mutableStateOf(asset.originalDataUrl != null) }
    val activeSourceUrl = if (useOriginalSource && asset.originalDataUrl != null) {
        asset.originalDataUrl
    } else {
        asset.dataUrl
    }

    var mode by remember(asset.id) { mutableStateOf(BackgroundRemovalMode.SMART_EDGE) }
    var targetColor by remember(asset.id) { mutableStateOf<Int?>(null) }
    var tolerance by remember(asset.id) { mutableIntStateOf(28) }
    var feather by remember(asset.id) { mutableIntStateOf(12) }
    var despill by remember(asset.id) { mutableFloatStateOf(0.35f) }
    var edgeShrinkPx by remember(asset.id) { mutableIntStateOf(0) }
    var showAdvanced by remember(asset.id) { mutableStateOf(false) }
    var compareOriginal by remember(asset.id) { mutableStateOf(false) }
    var pickedNormPoint by remember(asset.id) { mutableStateOf<Offset?>(null) }

    var previewSource by remember(activeSourceUrl) { mutableStateOf<Bitmap?>(null) }
    var processedPreview by remember(activeSourceUrl) { mutableStateOf<Bitmap?>(null) }
    var detectedKeyColor by remember(activeSourceUrl) { mutableIntStateOf(0xFFFFFFFF.toInt()) }
    var removedPercent by remember(activeSourceUrl) { mutableFloatStateOf(0f) }
    var isPreviewProcessing by remember(activeSourceUrl) { mutableStateOf(false) }

    // Decode and downscale preview source off main thread (max 512px for low-end tablet speed)
    LaunchedEffect(activeSourceUrl) {
        val loaded = withContext(Dispatchers.IO) {
            val raw = ProceduralAssetRenderer.getBitmap(context, activeSourceUrl)
            raw?.let { SpriteBackgroundRemover.createPreviewBitmap(it, maxDimension = 512) }
        }
        val prev = previewSource
        previewSource = loaded
        if (prev != null && prev !== loaded && !prev.isRecycled) {
            prev.recycle()
        }
    }

    val currentOptions = remember(mode, targetColor, tolerance, feather, despill, edgeShrinkPx) {
        BackgroundRemovalOptions(
            mode = mode,
            targetColor = targetColor,
            tolerance = tolerance,
            feather = feather,
            despill = despill,
            edgeShrinkPx = edgeShrinkPx,
        )
    }

    // Recompute live cutout preview whenever source or options change
    LaunchedEffect(previewSource, currentOptions) {
        val src = previewSource ?: return@LaunchedEffect
        isPreviewProcessing = true
        val (bmp, result) = withContext(Dispatchers.Default) {
            SpriteBackgroundRemover.removeBackground(src, currentOptions)
        }
        val old = processedPreview
        processedPreview = bmp
        detectedKeyColor = result.detectedKeyColor
        removedPercent = result.removedPercent
        isPreviewProcessing = false
        if (old != null && old !== bmp && !old.isRecycled) {
            old.recycle()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            processedPreview?.let { if (!it.isRecycled) it.recycle() }
            previewSource?.let { if (!it.isRecycled) it.recycle() }
        }
    }

    Dialog(
        onDismissRequest = { if (!isApplying) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .widthIn(max = 940.dp)
                .heightIn(max = 680.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(StudioPanel)
                .border(1.dp, StudioBorderStrong, RoundedCornerShape(16.dp))
                .testTag("sprite_bg_removal_dialog"),
        ) {
            val isWide = maxWidth >= 660.dp

            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(StudioBg)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(StudioPrimary.copy(alpha = 0.2f))
                            .border(1.dp, StudioPrimaryBright.copy(alpha = 0.45f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoFixHigh,
                            contentDescription = null,
                            tint = StudioPrimaryBright,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.bg_remove_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = StudioTextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "${asset.name} · ${asset.width ?: 0}×${asset.height ?: 0} · ${removedPercent.roundToInt()}% transparent",
                            style = StudioMonoStyle,
                            color = StudioTextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(
                        onClick = { if (!isApplying) onDismiss() },
                        modifier = Modifier.testTag("bg_remove_close_btn"),
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.action_close),
                            tint = StudioTextSecondary,
                        )
                    }
                }

                HorizontalDivider(color = StudioBorder)

                // Body (adaptive side-by-side on tablets, stacked on narrow screens)
                if (isWide) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        SpriteCutoutPreviewPane(
                            sourceBitmap = previewSource,
                            processedBitmap = processedPreview,
                            compareOriginal = compareOriginal,
                            isProcessing = isPreviewProcessing,
                            pickedNormPoint = pickedNormPoint,
                            supportsColorPick = mode == BackgroundRemovalMode.SMART_EDGE || mode == BackgroundRemovalMode.COLOR_KEY,
                            onToggleCompare = { compareOriginal = !compareOriginal },
                            onPickPixel = { normPt, pickedArgb ->
                                pickedNormPoint = normPt
                                targetColor = pickedArgb
                            },
                            modifier = Modifier
                                .weight(1.15f)
                                .fillMaxHeight(),
                        )

                        SpriteCutoutControlsPane(
                            asset = asset,
                            mode = mode,
                            onModeChange = { mode = it },
                            targetColor = targetColor,
                            detectedKeyColor = detectedKeyColor,
                            onSelectTargetColor = { c ->
                                targetColor = c
                                if (c == null) pickedNormPoint = null
                            },
                            tolerance = tolerance,
                            onToleranceChange = { tolerance = it },
                            feather = feather,
                            onFeatherChange = { feather = it },
                            despill = despill,
                            onDespillChange = { despill = it },
                            edgeShrinkPx = edgeShrinkPx,
                            onEdgeShrinkChange = { edgeShrinkPx = it },
                            showAdvanced = showAdvanced,
                            onToggleAdvanced = { showAdvanced = !showAdvanced },
                            useOriginalSource = useOriginalSource,
                            onToggleUseOriginalSource = { useOriginalSource = it },
                            modifier = Modifier
                                .weight(0.95f)
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState()),
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        SpriteCutoutPreviewPane(
                            sourceBitmap = previewSource,
                            processedBitmap = processedPreview,
                            compareOriginal = compareOriginal,
                            isProcessing = isPreviewProcessing,
                            pickedNormPoint = pickedNormPoint,
                            supportsColorPick = mode == BackgroundRemovalMode.SMART_EDGE || mode == BackgroundRemovalMode.COLOR_KEY,
                            onToggleCompare = { compareOriginal = !compareOriginal },
                            onPickPixel = { normPt, pickedArgb ->
                                pickedNormPoint = normPt
                                targetColor = pickedArgb
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(250.dp),
                        )

                        SpriteCutoutControlsPane(
                            asset = asset,
                            mode = mode,
                            onModeChange = { mode = it },
                            targetColor = targetColor,
                            detectedKeyColor = detectedKeyColor,
                            onSelectTargetColor = { c ->
                                targetColor = c
                                if (c == null) pickedNormPoint = null
                            },
                            tolerance = tolerance,
                            onToleranceChange = { tolerance = it },
                            feather = feather,
                            onFeatherChange = { feather = it },
                            despill = despill,
                            onDespillChange = { despill = it },
                            edgeShrinkPx = edgeShrinkPx,
                            onEdgeShrinkChange = { edgeShrinkPx = it },
                            showAdvanced = showAdvanced,
                            onToggleAdvanced = { showAdvanced = !showAdvanced },
                            useOriginalSource = useOriginalSource,
                            onToggleUseOriginalSource = { useOriginalSource = it },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                HorizontalDivider(color = StudioBorder)

                // Footer Actions
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(StudioBg)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (asset.originalDataUrl != null) {
                        OutlinedButton(
                            onClick = onRestoreOriginal,
                            enabled = !isApplying,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("bg_remove_restore_btn"),
                        ) {
                            Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.bg_remove_restore))
                        }
                    }

                    OutlinedButton(
                        onClick = { onApply(currentOptions, useOriginalSource, true) },
                        enabled = !isApplying && previewSource != null,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("bg_remove_save_copy_btn"),
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.bg_remove_save_copy))
                    }

                    Button(
                        onClick = { onApply(currentOptions, useOriginalSource, false) },
                        enabled = !isApplying && previewSource != null,
                        colors = ButtonDefaults.buttonColors(containerColor = StudioPrimary),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("bg_remove_apply_btn"),
                    ) {
                        if (isApplying) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = Color.White,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Processing…")
                        } else {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.bg_remove_apply))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SpriteCutoutPreviewPane(
    sourceBitmap: Bitmap?,
    processedBitmap: Bitmap?,
    compareOriginal: Boolean,
    isProcessing: Boolean,
    pickedNormPoint: Offset?,
    supportsColorPick: Boolean,
    onToggleCompare: () -> Unit,
    onPickPixel: (normPt: Offset, argb: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    val displayBitmap = if (compareOriginal) sourceBitmap else (processedBitmap ?: sourceBitmap)

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, StudioBorder, RoundedCornerShape(12.dp))
                .onSizeChanged { boxSize = it }
                .pointerInput(sourceBitmap, supportsColorPick, boxSize) {
                    if (!supportsColorPick || sourceBitmap == null) return@pointerInput
                    detectTapGestures { tapOffset ->
                        val bw = sourceBitmap.width.toFloat().coerceAtLeast(1f)
                        val bh = sourceBitmap.height.toFloat().coerceAtLeast(1f)
                        val cw = boxSize.width.toFloat().coerceAtLeast(1f)
                        val ch = boxSize.height.toFloat().coerceAtLeast(1f)
                        val fitScale = min(cw / bw, ch / bh)
                        val drawW = bw * fitScale
                        val drawH = bh * fitScale
                        val left = (cw - drawW) * 0.5f
                        val top = (ch - drawH) * 0.5f
                        val nx = ((tapOffset.x - left) / drawW).coerceIn(0f, 1f)
                        val ny = ((tapOffset.y - top) / drawH).coerceIn(0f, 1f)
                        val sampled = SpriteBackgroundRemover.sampleColorAt(sourceBitmap, nx, ny)
                        onPickPixel(Offset(nx, ny), sampled)
                    }
                }
                .testTag("bg_remove_preview_canvas"),
            contentAlignment = Alignment.Center,
        ) {
            // Transparency checkerboard backdrop
            Canvas(modifier = Modifier.fillMaxSize()) {
                val tile = 14.dp.toPx()
                val cols = (size.width / tile).toInt() + 1
                val rows = (size.height / tile).toInt() + 1
                val light = Color(0xFF232937)
                val dark = Color(0xFF171B24)
                for (r in 0 until rows) {
                    for (c in 0 until cols) {
                        drawRect(
                            color = if ((r + c) % 2 == 0) light else dark,
                            topLeft = Offset(c * tile, r * tile),
                            size = Size(tile, tile),
                        )
                    }
                }
            }

            if (displayBitmap != null && !displayBitmap.isRecycled) {
                Image(
                    bitmap = displayBitmap.asImageBitmap(),
                    contentDescription = "Sprite cutout preview",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    color = StudioPrimaryBright,
                    strokeWidth = 2.5.dp,
                )
            }

            // Picked eyedropper crosshair overlay
            if (supportsColorPick && pickedNormPoint != null && sourceBitmap != null && boxSize.width > 0 && boxSize.height > 0) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val bw = sourceBitmap.width.toFloat().coerceAtLeast(1f)
                    val bh = sourceBitmap.height.toFloat().coerceAtLeast(1f)
                    val fitScale = min(size.width / bw, size.height / bh)
                    val drawW = bw * fitScale
                    val drawH = bh * fitScale
                    val left = (size.width - drawW) * 0.5f
                    val top = (size.height - drawH) * 0.5f
                    val cx = left + pickedNormPoint.x * drawW
                    val cy = top + pickedNormPoint.y * drawH
                    drawCircle(
                        color = Color.Black.copy(alpha = 0.7f),
                        radius = 11.dp.toPx(),
                        center = Offset(cx, cy),
                        style = Stroke(width = 3.dp.toPx()),
                    )
                    drawCircle(
                        color = StudioCyan,
                        radius = 9.dp.toPx(),
                        center = Offset(cx, cy),
                        style = Stroke(width = 2.dp.toPx()),
                    )
                }
            }

            // Top-left live status pill
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xCC0D111A))
                    .border(1.dp, StudioBorder, RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = if (compareOriginal) "ORIGINAL" else "CUTOUT PREVIEW",
                    style = StudioMonoStyle,
                    color = if (compareOriginal) StudioAmber else StudioEmerald,
                )
                if (isProcessing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(11.dp),
                        strokeWidth = 1.5.dp,
                        color = StudioCyan,
                    )
                }
            }

            // Bottom-right Compare toggle
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(10.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (compareOriginal) StudioAmber.copy(alpha = 0.25f) else Color(0xCC0D111A))
                    .border(
                        1.dp,
                        if (compareOriginal) StudioAmber else StudioBorderStrong,
                        RoundedCornerShape(8.dp),
                    )
                    .clickable(onClick = onToggleCompare)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .testTag("bg_remove_compare_btn"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Compare,
                    contentDescription = stringResource(R.string.bg_remove_compare),
                    tint = if (compareOriginal) StudioAmber else StudioTextPrimary,
                    modifier = Modifier.size(15.dp),
                )
                Text(
                    text = stringResource(R.string.bg_remove_compare),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (compareOriginal) StudioAmber else StudioTextPrimary,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        if (supportsColorPick) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Colorize,
                    contentDescription = null,
                    tint = StudioCyan,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = stringResource(R.string.bg_remove_tap_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = StudioTextSecondary,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SpriteCutoutControlsPane(
    asset: AssetRecord,
    mode: BackgroundRemovalMode,
    onModeChange: (BackgroundRemovalMode) -> Unit,
    targetColor: Int?,
    detectedKeyColor: Int,
    onSelectTargetColor: (Int?) -> Unit,
    tolerance: Int,
    onToleranceChange: (Int) -> Unit,
    feather: Int,
    onFeatherChange: (Int) -> Unit,
    despill: Float,
    onDespillChange: (Float) -> Unit,
    edgeShrinkPx: Int,
    onEdgeShrinkChange: (Int) -> Unit,
    showAdvanced: Boolean,
    onToggleAdvanced: () -> Unit,
    useOriginalSource: Boolean,
    onToggleUseOriginalSource: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Source selector when re-editing an already cutout sprite
        if (asset.originalDataUrl != null) {
            StudioChipRow(
                options = listOf(
                    "orig" to "Edit From Original",
                    "current" to "Refine Current Cutout",
                ),
                selectedKey = if (useOriginalSource) "orig" else "current",
                onSelect = { onToggleUseOriginalSource(it == "orig") },
                accent = StudioCyan,
            )
        }

        // Mode Selection
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Removal Mode",
                style = MaterialTheme.typography.labelMedium,
                color = StudioTextPrimary,
                fontWeight = FontWeight.SemiBold,
            )
            StudioChipRow(
                options = BackgroundRemovalMode.entries.map { it.name to it.label },
                selectedKey = mode.name,
                onSelect = { onModeChange(BackgroundRemovalMode.valueOf(it)) },
                accent = StudioPrimaryBright,
            )
            Text(
                text = mode.subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = StudioTextSecondary,
            )
        }

        // Key Color Picker Swatches (for Smart Edge & Color Key modes)
        if (mode == BackgroundRemovalMode.SMART_EDGE || mode == BackgroundRemovalMode.COLOR_KEY) {
            val activeArgb = targetColor ?: detectedKeyColor
            val hex = "#%06X".format(activeArgb and 0xFFFFFF)

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Backdrop Color",
                        style = MaterialTheme.typography.labelMedium,
                        color = StudioTextPrimary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(Color(activeArgb or 0xFF000000.toInt()))
                                .border(1.dp, StudioBorderStrong, CircleShape),
                        )
                        Text(
                            text = if (targetColor == null) "Auto ($hex)" else "Picked ($hex)",
                            style = StudioMonoStyle,
                            color = StudioTextSecondary,
                        )
                    }
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    QUICK_KEY_PRESETS.forEach { preset ->
                        val selected = targetColor == preset.argb
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) StudioPrimary.copy(alpha = 0.22f) else StudioSurfaceElevated)
                                .border(
                                    1.dp,
                                    if (selected) StudioPrimaryBright else StudioBorder,
                                    RoundedCornerShape(8.dp),
                                )
                                .clickable { onSelectTargetColor(preset.argb) }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                .testTag("bg_color_preset_${preset.label.lowercase()}"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(preset.swatchColor)
                                    .border(1.dp, StudioBorderStrong, CircleShape),
                            )
                            Text(
                                text = preset.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected) StudioTextPrimary else StudioTextSecondary,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        }
                    }
                }
            }
        }

        // Core Sliders
        StudioSliderRow(
            label = stringResource(R.string.bg_remove_tolerance),
            value = tolerance.toFloat(),
            valueRange = 0f..100f,
            valueLabel = "$tolerance%",
            onValueChange = { onToleranceChange(it.roundToInt()) },
            accentColor = StudioPrimaryBright,
        )

        StudioSliderRow(
            label = stringResource(R.string.bg_remove_feather),
            value = feather.toFloat(),
            valueRange = 0f..40f,
            valueLabel = "$feather",
            onValueChange = { onFeatherChange(it.roundToInt()) },
            accentColor = StudioCyan,
        )

        // Expandable Edge Refinement Section
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(StudioBg)
                .border(1.dp, StudioBorder, RoundedCornerShape(10.dp)),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleAdvanced)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .testTag("bg_remove_advanced_toggle"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Advanced Edge Refinement",
                    style = MaterialTheme.typography.labelMedium,
                    color = StudioTextPrimary,
                    fontWeight = FontWeight.Medium,
                )
                Icon(
                    imageVector = if (showAdvanced) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = "Toggle edge refinement options",
                    tint = StudioTextSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }

            AnimatedVisibility(visible = showAdvanced) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    StudioSliderRow(
                        label = stringResource(R.string.bg_remove_despill),
                        value = despill,
                        valueRange = 0f..1f,
                        valueLabel = "${(despill * 100f).roundToInt()}%",
                        onValueChange = onDespillChange,
                        accentColor = StudioEmerald,
                    )

                    StudioSliderRow(
                        label = stringResource(R.string.bg_remove_shrink),
                        value = edgeShrinkPx.toFloat(),
                        valueRange = 0f..3f,
                        valueLabel = "${edgeShrinkPx}px",
                        onValueChange = { onEdgeShrinkChange(it.roundToInt()) },
                        accentColor = StudioAmber,
                    )
                }
            }
        }
    }
}
