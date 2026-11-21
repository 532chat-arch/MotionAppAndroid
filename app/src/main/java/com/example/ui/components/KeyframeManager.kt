package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.engine.evaluateKeyframes
import com.example.schema.InterpolationType
import com.example.schema.Keyframe
import com.example.ui.theme.StudioAmber
import com.example.ui.theme.StudioBg
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioCyan
import com.example.ui.theme.StudioMonoStyle
import com.example.ui.theme.StudioPrimary
import com.example.ui.theme.StudioPrimaryBright
import com.example.ui.theme.StudioRose
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.StudioTextMuted
import com.example.ui.theme.StudioTextPrimary
import com.example.ui.theme.StudioTextSecondary
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

data class KeyframeChannelUi(
    val key: String,
    val label: String,
    val shortLabel: String,
    val keyframes: List<Keyframe>,
    val currentValue: Double,
    val unit: String = "",
)

private val EASING_OPTIONS = listOf(
    InterpolationType.LINEAR to "Linear",
    InterpolationType.EASE_IN to "In",
    InterpolationType.EASE_OUT to "Out",
    InterpolationType.EASE_IN_OUT to "In-Out",
    InterpolationType.HOLD to "Hold",
)

@Composable
fun KeyframeManagerSection(
    title: String,
    channels: List<KeyframeChannelUi>,
    currentFrame: Int,
    durationFrames: Int,
    onJumpToFrame: (Int) -> Unit,
    onSetKeyframe: (channelKey: String, frame: Int, value: Double, interp: InterpolationType) -> Unit,
    onUpdateEasing: (channelKey: String, keyframeId: String, interp: InterpolationType) -> Unit,
    onDeleteKeyframe: (channelKey: String, frame: Int) -> Unit,
    onClearChannel: ((channelKey: String) -> Unit)? = null,
    selectedChannelKey: String? = null,
    selectedKeyframeFrame: Int? = null,
    hasModifiers: Boolean = false,
    onSelectKeyframe: ((channelKey: String, frame: Int) -> Unit)? = null,
    onDuplicateKeyframe: ((channelKey: String, frame: Int) -> Unit)? = null,
    onClearAllAnimation: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    if (channels.isEmpty()) return
    val channelKeys = remember(channels) { channels.map { it.key } }
    var selectedKey by remember(channelKeys) {
        mutableStateOf(
            if (selectedChannelKey != null && selectedChannelKey in channelKeys) selectedChannelKey
            else channels.first().key
        )
    }
    LaunchedEffect(selectedChannelKey) {
        if (selectedChannelKey != null && selectedChannelKey in channelKeys) {
            selectedKey = selectedChannelKey
        }
    }

    var defaultInterp by remember { mutableStateOf(InterpolationType.EASE_IN_OUT) }

    val activeChannel = channels.find { it.key == selectedKey } ?: channels.first()
    val sortedKfs = remember(activeChannel.keyframes) { activeChannel.keyframes.sortedBy { it.frame } }

    val allFrames = remember(channels) {
        channels.flatMap { ch -> ch.keyframes.map { it.frame } }.distinct().sorted()
    }
    val hasAnyAnimation = allFrames.isNotEmpty() || hasModifiers
    val prevFrame = allFrames.lastOrNull { it < currentFrame }
    val nextFrame = allFrames.firstOrNull { it > currentFrame }
    val existingAtCurrent = sortedKfs.find { it.frame == currentFrame }
    val activeKf = (selectedKeyframeFrame?.let { sf -> sortedKfs.find { it.frame == sf } })
        ?: existingAtCurrent
        ?: sortedKfs.lastOrNull { it.frame <= currentFrame }
        ?: sortedKfs.firstOrNull()

    fun selectOrJump(frame: Int, preferredChannelKey: String = activeChannel.key) {
        val targetCh = channels.find { it.key == preferredChannelKey && it.keyframes.any { k -> k.frame == frame } }
            ?: channels.firstOrNull { it.keyframes.any { k -> k.frame == frame } }
        if (targetCh != null && onSelectKeyframe != null) {
            selectedKey = targetCh.key
            onSelectKeyframe(targetCh.key, frame)
        } else {
            onJumpToFrame(frame)
        }
    }

    val latestSortedKfs by rememberUpdatedState(sortedKfs)
    val latestActiveChannel by rememberUpdatedState(activeChannel)
    val latestOnSelectKeyframe by rememberUpdatedState(onSelectKeyframe)
    val latestOnJumpToFrame by rememberUpdatedState(onJumpToFrame)
    val density = LocalDensity.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(StudioBg)
            .border(1.dp, StudioBorder, RoundedCornerShape(12.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Header + Prev/Next Keyframe Jump
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = StudioTextPrimary)
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { prevFrame?.let { selectOrJump(it) } },
                    enabled = prevFrame != null,
                    modifier = Modifier
                        .size(38.dp)
                        .minimumInteractiveComponentSize()
                        .testTag("kf_mgr_prev_btn"),
                ) {
                    Icon(
                        Icons.Default.ChevronLeft,
                        contentDescription = stringResource(R.string.timeline_prev_kf),
                        tint = if (prevFrame != null) StudioTextPrimary else StudioTextSecondary.copy(alpha = 0.3f),
                        modifier = Modifier.size(18.dp),
                    )
                }
                Text("${sortedKfs.size}", style = StudioMonoStyle, color = StudioAmber)
                IconButton(
                    onClick = { nextFrame?.let { selectOrJump(it) } },
                    enabled = nextFrame != null,
                    modifier = Modifier
                        .size(38.dp)
                        .minimumInteractiveComponentSize()
                        .testTag("kf_mgr_next_btn"),
                ) {
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = stringResource(R.string.timeline_next_kf),
                        tint = if (nextFrame != null) StudioTextPrimary else StudioTextSecondary.copy(alpha = 0.3f),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }

        // Channel Selector Pills (tablet-friendly)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            for (ch in channels) {
                val active = ch.key == activeChannel.key
                Row(
                    modifier = Modifier
                        .height(34.dp)
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(7.dp))
                        .background(if (active) StudioPrimary else StudioSurfaceElevated)
                        .border(
                            1.dp,
                            if (active) StudioPrimaryBright else StudioBorder,
                            RoundedCornerShape(7.dp),
                        )
                        .clickable { selectedKey = ch.key }
                        .padding(horizontal = 10.dp)
                        .testTag("kf_channel_pill_${ch.key}"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = ch.shortLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (active) Color.White else StudioTextSecondary,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                    )
                    if (ch.keyframes.isNotEmpty()) {
                        Text(
                            text = "◆${ch.keyframes.size}",
                            style = StudioMonoStyle,
                            color = if (active) Color.White else StudioAmber,
                        )
                    }
                }
            }
        }

        // Interactive Mini Curve Visualizer (Tap diamonds to jump & select!)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF0E1015))
                .border(1.dp, StudioBorder, RoundedCornerShape(8.dp))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "${activeChannel.label} Curve",
                    style = MaterialTheme.typography.labelSmall,
                    color = StudioTextSecondary,
                )
                if (sortedKfs.isNotEmpty() && onClearChannel != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(StudioRose.copy(alpha = 0.14f))
                            .border(1.dp, StudioRose.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                            .clickable { onClearChannel(activeChannel.key) }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .testTag("kf_mgr_clear_channel_${activeChannel.key}"),
                    ) {
                        Text(
                            text = "Clear ${activeChannel.shortLabel}",
                            style = MaterialTheme.typography.labelSmall,
                            color = StudioRose,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(68.dp)
                    .pointerInput(durationFrames, activeChannel.key) {
                        detectTapGestures { tapOffset ->
                            val kfs = latestSortedKfs
                            val ch = latestActiveChannel
                            val w = size.width.toFloat().coerceAtLeast(1f)
                            val h = size.height.toFloat().coerceAtLeast(1f)
                            val maxF = max(1, durationFrames - 1).toFloat()
                            val hitRadiusPx = with(density) { 26.dp.toPx() }

                            val samples = 48
                            val vals = (0..samples).map { i ->
                                val f = (i.toDouble() / samples) * maxF
                                evaluateKeyframes(kfs, f, ch.currentValue)
                            }
                            var minV = (vals + kfs.map { it.value }).minOrNull() ?: 0.0
                            var maxV = (vals + kfs.map { it.value }).maxOrNull() ?: 1.0
                            if (abs(maxV - minV) < 1e-4) {
                                minV -= 1.0
                                maxV += 1.0
                            }
                            val pad = (maxV - minV) * 0.15
                            minV -= pad
                            maxV += pad

                            fun toX(f: Float) = 10f + (f / maxF).coerceIn(0f, 1f) * (w - 20f)
                            fun toY(v: Double) = h - 10f - (((v - minV) / (maxV - minV)).toFloat().coerceIn(0f, 1f)) * (h - 20f)

                            var bestKf: Keyframe? = null
                            var bestDist = hitRadiusPx
                            for (kf in kfs) {
                                val d = hypot(tapOffset.x - toX(kf.frame.toFloat()), tapOffset.y - toY(kf.value))
                                if (d <= bestDist) {
                                    bestDist = d
                                    bestKf = kf
                                }
                            }
                            if (bestKf != null) {
                                val selCb = latestOnSelectKeyframe
                                if (selCb != null) {
                                    selCb(ch.key, bestKf.frame)
                                } else {
                                    latestOnJumpToFrame(bestKf.frame)
                                }
                            } else {
                                val tappedFrame = (((tapOffset.x - 10f) / (w - 20f).coerceAtLeast(1f)) * maxF)
                                    .roundToInt()
                                    .coerceIn(0, max(0, durationFrames - 1))
                                latestOnJumpToFrame(tappedFrame)
                            }
                        }
                    }
                    .testTag("kf_mgr_curve_canvas"),
            ) {
                val w = size.width
                val h = size.height
                val maxF = max(1, durationFrames - 1).toFloat()
                val samples = 48
                val vals = (0..samples).map { i ->
                    val f = (i.toDouble() / samples) * maxF
                    evaluateKeyframes(sortedKfs, f, activeChannel.currentValue)
                }
                var minV = (vals + sortedKfs.map { it.value }).minOrNull() ?: 0.0
                var maxV = (vals + sortedKfs.map { it.value }).maxOrNull() ?: 1.0
                if (abs(maxV - minV) < 1e-4) {
                    minV -= 1.0
                    maxV += 1.0
                }
                val pad = (maxV - minV) * 0.15
                minV -= pad
                maxV += pad

                fun toX(f: Float) = 10f + (f / maxF).coerceIn(0f, 1f) * (w - 20f)
                fun toY(v: Double) = h - 10f - (((v - minV) / (maxV - minV)).toFloat().coerceIn(0f, 1f)) * (h - 20f)

                val playX = toX(currentFrame.toFloat())
                drawLine(StudioAmber.copy(alpha = 0.6f), Offset(playX, 0f), Offset(playX, h), strokeWidth = 2f)

                val curvePath = Path()
                vals.forEachIndexed { i, v ->
                    val x = toX((i.toFloat() / samples) * maxF)
                    val y = toY(v)
                    if (i == 0) curvePath.moveTo(x, y) else curvePath.lineTo(x, y)
                }
                drawPath(
                    curvePath,
                    color = if (sortedKfs.isNotEmpty()) StudioPrimaryBright else StudioTextSecondary.copy(alpha = 0.35f),
                    style = Stroke(width = 2.5f, cap = StrokeCap.Round),
                )

                for (kf in sortedKfs) {
                    val kx = toX(kf.frame.toFloat())
                    val ky = toY(kf.value)
                    val isSelected = selectedKeyframeFrame == kf.frame &&
                        (selectedChannelKey == null || selectedChannelKey == activeChannel.key)
                    val isAtPlayhead = kf.frame == currentFrame
                    val r = when {
                        isSelected -> 7.5f
                        isAtPlayhead -> 6f
                        else -> 5f
                    }
                    if (isSelected) {
                        drawCircle(
                            color = StudioPrimaryBright.copy(alpha = 0.28f),
                            radius = r + 6f,
                            center = Offset(kx, ky),
                        )
                        drawCircle(
                            color = StudioPrimaryBright,
                            radius = r + 3.5f,
                            center = Offset(kx, ky),
                            style = Stroke(width = 1.6f),
                        )
                    }
                    val dPath = Path().apply {
                        moveTo(kx, ky - r); lineTo(kx + r, ky); lineTo(kx, ky + r); lineTo(kx - r, ky); close()
                    }
                    drawPath(
                        dPath,
                        color = when {
                            isSelected -> StudioPrimaryBright
                            isAtPlayhead -> Color.White
                            else -> easingColor(kf.interpolation)
                        },
                    )
                    if (isSelected) {
                        drawPath(dPath, color = Color.White, style = Stroke(width = 1.5f))
                    }
                }
            }
        }

        // Transition Easing Selector (tablet-friendly)
        if (activeKf != null) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        text = "Interpolation @ F${activeKf.frame}",
                        style = MaterialTheme.typography.labelSmall,
                        color = StudioTextSecondary,
                    )
                    Text(
                        text = interpolationShortLabel(activeKf.interpolation),
                        style = StudioMonoStyle,
                        color = easingColor(activeKf.interpolation),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    for ((mode, lbl) in EASING_OPTIONS) {
                        val isSel = activeKf.interpolation == mode
                        val accent = easingColor(mode)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .minimumInteractiveComponentSize()
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSel) accent.copy(alpha = 0.24f) else StudioSurfaceElevated)
                                .border(1.dp, if (isSel) accent else StudioBorder, RoundedCornerShape(6.dp))
                                .clickable {
                                    defaultInterp = mode
                                    onUpdateEasing(activeChannel.key, activeKf.id, mode)
                                }
                                .testTag("kf_mgr_easing_${mode.name.lowercase()}"),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = lbl,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSel) accent else StudioTextSecondary,
                                fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }

        // Key at Current Frame Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .minimumInteractiveComponentSize()
                .clip(RoundedCornerShape(8.dp))
                .background(StudioAmber.copy(alpha = 0.18f))
                .border(1.dp, StudioAmber.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                .clickable {
                    onSetKeyframe(
                        activeChannel.key,
                        currentFrame,
                        activeChannel.currentValue,
                        existingAtCurrent?.interpolation ?: defaultInterp,
                    )
                }
                .testTag("kf_mgr_set_key_btn"),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Add, contentDescription = null, tint = StudioAmber, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(
                text = if (existingAtCurrent != null) "Update ${activeChannel.shortLabel} @ F$currentFrame" else "Add ${activeChannel.shortLabel} Key @ F$currentFrame",
                style = MaterialTheme.typography.labelMedium,
                color = StudioAmber,
                fontWeight = FontWeight.SemiBold,
            )
        }

        // Keyframe Points List (Tap to jump & select, Duplicate, Delete)
        if (sortedKfs.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                for (kf in sortedKfs) {
                    val isSelected = selectedKeyframeFrame == kf.frame &&
                        (selectedChannelKey == null || selectedChannelKey == activeChannel.key)
                    val isAtPlayhead = kf.frame == currentFrame
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                when {
                                    isSelected -> StudioPrimary.copy(alpha = 0.22f)
                                    isAtPlayhead -> StudioAmber.copy(alpha = 0.14f)
                                    else -> StudioSurfaceElevated.copy(alpha = 0.6f)
                                }
                            )
                            .border(
                                1.dp,
                                when {
                                    isSelected -> StudioPrimaryBright
                                    isAtPlayhead -> StudioAmber.copy(alpha = 0.45f)
                                    else -> StudioBorder
                                },
                                RoundedCornerShape(8.dp),
                            )
                            .clickable { selectOrJump(kf.frame, activeChannel.key) }
                            .padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
                            .testTag("kf_mgr_row_${activeChannel.key}_${kf.frame}"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                text = "◆ F${kf.frame}",
                                style = StudioMonoStyle,
                                color = if (isSelected) StudioPrimaryBright else StudioAmber,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            )
                            Text(
                                text = "${"%.2f".format(kf.value)}${activeChannel.unit}",
                                style = StudioMonoStyle,
                                color = StudioTextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(easingColor(kf.interpolation).copy(alpha = 0.18f))
                                    .padding(horizontal = 5.dp, vertical = 2.dp),
                            ) {
                                Text(
                                    text = interpolationShortLabel(kf.interpolation),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = easingColor(kf.interpolation),
                                    maxLines = 1,
                                )
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (onDuplicateKeyframe != null) {
                                IconButton(
                                    onClick = { onDuplicateKeyframe(activeChannel.key, kf.frame) },
                                    modifier = Modifier
                                        .size(38.dp)
                                        .minimumInteractiveComponentSize()
                                        .testTag("kf_mgr_dup_${activeChannel.key}_${kf.frame}"),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = stringResource(R.string.kf_action_duplicate),
                                        tint = StudioCyan,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                            IconButton(
                                onClick = { onDeleteKeyframe(activeChannel.key, kf.frame) },
                                modifier = Modifier
                                    .size(38.dp)
                                    .minimumInteractiveComponentSize()
                                    .testTag("kf_mgr_del_${activeChannel.key}_${kf.frame}"),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.kf_action_delete),
                                    tint = StudioRose,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        // Clear All Animation Footer (preserves object)
        if (hasAnyAnimation && onClearAllAnimation != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(8.dp))
                    .background(StudioRose.copy(alpha = 0.12f))
                    .border(1.dp, StudioRose.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    .clickable(onClick = onClearAllAnimation)
                    .padding(horizontal = 10.dp)
                    .testTag("kf_mgr_clear_all_btn"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteSweep,
                    contentDescription = null,
                    tint = StudioRose,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.kf_clear_all_object),
                    style = MaterialTheme.typography.labelMedium,
                    color = StudioRose,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text = stringResource(R.string.kf_clear_all_hint),
                style = MaterialTheme.typography.labelSmall,
                color = StudioTextMuted,
            )
        }
    }
}
