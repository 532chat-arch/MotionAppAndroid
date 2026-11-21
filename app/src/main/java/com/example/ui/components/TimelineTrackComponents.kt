package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.schema.AssetRecord
import com.example.schema.AudioTrackDocument
import com.example.schema.InterpolationType
import com.example.schema.LayerDocument
import com.example.schema.TimelineMarker
import com.example.schema.parseHexColor
import com.example.ui.theme.StudioAmber
import com.example.ui.theme.StudioBg
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioBorderStrong
import com.example.ui.theme.StudioCyan
import com.example.ui.theme.StudioEmerald
import com.example.ui.theme.StudioInsetField
import com.example.ui.theme.StudioMonoStyle
import com.example.ui.theme.StudioPanel
import com.example.ui.theme.StudioPrimary
import com.example.ui.theme.StudioPrimaryBright
import com.example.ui.theme.StudioRose
import com.example.ui.theme.StudioSecondary
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.StudioSurfaceHigh
import com.example.ui.theme.StudioSurfaceLowest
import com.example.ui.theme.StudioTextMuted
import com.example.ui.theme.StudioTextPrimary
import com.example.ui.theme.StudioTextSecondary
import com.example.ui.theme.StudioWorkspaceCanvas
import com.example.viewmodel.OnionSkinSettings
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
internal fun FrameScrubRulerTrack(
    scrollState: ScrollState,
    contentWidthDp: Dp,
    frameWidthDp: Dp,
    durationFrames: Int,
    currentFrame: Int,
    majorInterval: Int,
    minorInterval: Int,
    markers: List<TimelineMarker>,
    summaryKeyframes: List<Int>,
    onionSkin: OnionSkinSettings = OnionSkinSettings(),
    textMeasurer: TextMeasurer,
    onScrubFrame: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val maxFrame = max(0, durationFrames - 1)
    Box(
        modifier = modifier
            .horizontalScroll(scrollState)
            .testTag("timeline_scrub_ruler"),
    ) {
        Canvas(
            modifier = Modifier
                .width(contentWidthDp)
                .fillMaxHeight()
                .pointerInput(durationFrames, frameWidthDp) {
                    val fw = frameWidthDp.toPx().coerceAtLeast(1f)
                    detectTapGestures { pos ->
                        val f = (pos.x / fw).roundToInt().coerceIn(0, maxFrame)
                        onScrubFrame(f)
                    }
                }
                .pointerInput(durationFrames, frameWidthDp) {
                    val fw = frameWidthDp.toPx().coerceAtLeast(1f)
                    detectHorizontalDragGestures { change, _ ->
                        change.consume()
                        val f = (change.position.x / fw).roundToInt().coerceIn(0, maxFrame)
                        onScrubFrame(f)
                    }
                },
        ) {
            val h = size.height
            val fw = frameWidthDp.toPx().coerceAtLeast(1f)

            // Frame tick marks & labels
            var f = 0
            while (f <= maxFrame) {
                val x = f * fw
                val isMajor = f % majorInterval == 0
                drawLine(
                    color = if (isMajor) Color(0x99CBD5E1) else Color(0x3D94A3B8),
                    start = Offset(x, if (isMajor) h * 0.45f else h * 0.72f),
                    end = Offset(x, h),
                    strokeWidth = if (isMajor) 1.5f else 1f,
                )
                if (isMajor && x < size.width - 14f) {
                    val textRes = textMeasurer.measure(
                        text = "$f",
                        style = TextStyle(
                            color = StudioTextSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                    )
                    drawText(
                        textLayoutResult = textRes,
                        topLeft = Offset(x + 3f, 2f),
                    )
                }
                f += minorInterval
            }

            // Composition Markers
            for (marker in markers) {
                val mx = marker.frame * fw
                val mColor = Color(parseHexColor(marker.color, 0xFFF59E0B.toInt()))
                val flag = Path().apply {
                    moveTo(mx, 2f)
                    lineTo(mx + 10f, 7f)
                    lineTo(mx, 12f)
                    close()
                }
                drawPath(flag, mColor)
                drawLine(mColor.copy(alpha = 0.75f), Offset(mx, 2f), Offset(mx, h), strokeWidth = 1.5f)
            }

            // Summary Keyframe dots when timeline is collapsed
            for (kf in summaryKeyframes) {
                val kx = kf * fw
                drawKeyframeDiamond(
                    cx = kx,
                    cy = h - 6f,
                    radius = 3.6f,
                    fillColor = if (kf == currentFrame) Color.White else StudioPrimary,
                    strokeColor = StudioSurfaceLowest,
                )
            }

            // Onion Skin previous (Rose) and next (Emerald) range brackets around playhead
            if (onionSkin.enabled) {
                val pxNow = (currentFrame * fw).coerceIn(0f, size.width)
                val step = onionSkin.frameStep.coerceAtLeast(1)

                if (onionSkin.prevFrames > 0 && currentFrame > 0) {
                    val startFrame = max(0, currentFrame - onionSkin.prevFrames * step)
                    val startX = (startFrame * fw).coerceIn(0f, pxNow)
                    if (pxNow > startX) {
                        drawRect(
                            color = StudioRose.copy(alpha = 0.22f),
                            topLeft = Offset(startX, 0f),
                            size = Size(pxNow - startX, h),
                        )
                        drawLine(
                            color = StudioRose.copy(alpha = 0.9f),
                            start = Offset(startX, 0f),
                            end = Offset(startX, h),
                            strokeWidth = 2f,
                        )
                        for (i in 1..onionSkin.prevFrames) {
                            val gf = currentFrame - i * step
                            if (gf >= 0) {
                                drawCircle(
                                    color = StudioRose,
                                    radius = 2.6f,
                                    center = Offset(gf * fw, h - 5f),
                                )
                            }
                        }
                    }
                }

                if (onionSkin.nextFrames > 0 && currentFrame < maxFrame) {
                    val endFrame = min(maxFrame, currentFrame + onionSkin.nextFrames * step)
                    val endX = (endFrame * fw).coerceIn(pxNow, size.width)
                    if (endX > pxNow) {
                        drawRect(
                            color = StudioEmerald.copy(alpha = 0.22f),
                            topLeft = Offset(pxNow, 0f),
                            size = Size(endX - pxNow, h),
                        )
                        drawLine(
                            color = StudioEmerald.copy(alpha = 0.9f),
                            start = Offset(endX, 0f),
                            end = Offset(endX, h),
                            strokeWidth = 2f,
                        )
                        for (i in 1..onionSkin.nextFrames) {
                            val gf = currentFrame + i * step
                            if (gf <= maxFrame) {
                                drawCircle(
                                    color = StudioEmerald,
                                    radius = 2.6f,
                                    center = Offset(gf * fw, h - 5f),
                                )
                            }
                        }
                    }
                }
            }

            // Motion Studio Pro CTI (Current Time Indicator): #4B9BE8 pennant + subtle glow laser line
            val px = (currentFrame * fw).coerceIn(0f, size.width)
            drawLine(StudioPrimary.copy(alpha = 0.28f), Offset(px, 0f), Offset(px, h), strokeWidth = 5f)
            drawLine(StudioPrimary, Offset(px, 0f), Offset(px, h), strokeWidth = 2f)
            val head = Path().apply {
                moveTo(px - 6.5f, 0f)
                lineTo(px + 6.5f, 0f)
                lineTo(px + 6.5f, 9.5f)
                lineTo(px, 16.5f)
                lineTo(px - 6.5f, 9.5f)
                close()
            }
            drawPath(head, StudioPrimary)
        }
    }
}

@Composable
internal fun TimelineStickyRow(
    headerWidthDp: Dp,
    scrollState: ScrollState,
    contentWidthDp: Dp,
    rowHeight: Dp,
    isSelected: Boolean,
    headerContent: @Composable () -> Unit,
    trackContent: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(rowHeight)
                .background(if (isSelected) StudioPrimary.copy(alpha = 0.14f) else StudioBg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(headerWidthDp)
                    .fillMaxHeight()
                    .background(if (isSelected) StudioPrimary.copy(alpha = 0.18f) else StudioPanel),
            ) {
                headerContent()
            }
            Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(StudioBorderStrong))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .horizontalScroll(scrollState),
            ) {
                Box(
                    modifier = Modifier
                        .width(contentWidthDp)
                        .fillMaxHeight(),
                ) {
                    trackContent()
                }
            }
        }
        HorizontalDivider(color = StudioBorder)
    }
}

@Composable
internal fun CameraTrackHeader(
    expanded: Boolean,
    isSelected: Boolean,
    hasKeyframeAtCurrent: Boolean,
    hasAnyKeyframes: Boolean,
    onSelect: () -> Unit,
    onToggleExpand: () -> Unit,
    onToggleKey: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .clickable(onClick = onSelect)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(RoundedCornerShape(5.dp))
                .clickable(onClick = onToggleExpand)
                .testTag("camera_expand_channels"),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (expanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                contentDescription = stringResource(R.string.timeline_expand_channels),
                tint = StudioTextSecondary,
                modifier = Modifier.size(16.dp),
            )
        }
        Icon(
            Icons.Default.Videocam,
            contentDescription = null,
            tint = StudioCyan,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = "2D Camera",
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) Color.White else StudioCyan,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 2.dp),
        )
        KeyframeDiamondButton(
            hasKeyframeAtFrame = hasKeyframeAtCurrent,
            hasAnyKeyframes = hasAnyKeyframes,
            onClick = onToggleKey,
            modifier = Modifier.size(28.dp),
        )
    }
}

@Composable
internal fun LayerTrackHeader(
    layer: LayerDocument,
    accent: Color,
    expanded: Boolean,
    isSelected: Boolean,
    hasKeyframeAtCurrent: Boolean,
    hasAnyKeyframes: Boolean,
    onSelect: () -> Unit,
    onToggleExpand: () -> Unit,
    onToggleVisibility: () -> Unit,
    onToggleKey: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .clickable(onClick = onSelect)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(RoundedCornerShape(5.dp))
                .clickable(onClick = onToggleExpand)
                .testTag("layer_expand_${layer.id}"),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (expanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                contentDescription = stringResource(R.string.timeline_expand_channels),
                tint = StudioTextSecondary,
                modifier = Modifier.size(16.dp),
            )
        }
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(if (layer.visible) accent else StudioTextMuted),
        )
        Text(
            text = layer.name,
            style = MaterialTheme.typography.labelSmall,
            color = when {
                !layer.visible -> StudioTextMuted
                isSelected -> Color.White
                else -> StudioTextPrimary
            },
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = 3.dp),
        )
        if (layer.locked) {
            Icon(
                Icons.Default.Lock,
                contentDescription = null,
                tint = StudioAmber,
                modifier = Modifier.size(12.dp),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(onClick = onToggleVisibility),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (layer.visible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                    contentDescription = stringResource(R.string.cd_toggle_visibility),
                    tint = if (layer.visible) StudioTextSecondary else StudioTextMuted,
                    modifier = Modifier.size(13.dp),
                )
            }
        }
        KeyframeDiamondButton(
            hasKeyframeAtFrame = hasKeyframeAtCurrent,
            hasAnyKeyframes = hasAnyKeyframes,
            onClick = onToggleKey,
            modifier = Modifier.size(28.dp),
        )
    }
}

@Composable
internal fun TimelineLayerLaneCanvas(
    frameWidthDp: Dp,
    durationFrames: Int,
    majorInterval: Int,
    inFrame: Int,
    outFrame: Int,
    currentFrame: Int,
    accent: Color,
    isSelected: Boolean,
    isLocked: Boolean,
    isVisible: Boolean,
    keyframeFrames: List<Int>,
    selectedKeyframeFrame: Int? = null,
    modifierSpans: List<Pair<Int, Int>>,
    lyricCues: List<Pair<Int, Int>>,
    onSelectTrack: () -> Unit,
    onSelectKeyframe: ((Int) -> Unit)? = null,
    onJumpFrame: (Int) -> Unit,
) {
    val maxFrame = max(0, durationFrames - 1)
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(durationFrames, frameWidthDp, keyframeFrames) {
                val fw = frameWidthDp.toPx().coerceAtLeast(1f)
                val hitRadiusPx = max(22.dp.toPx(), fw * 1.6f)
                detectTapGestures { pos ->
                    val tappedFrame = (pos.x / fw).roundToInt().coerceIn(0, maxFrame)
                    val nearestKf = keyframeFrames.minByOrNull { abs(it * fw - pos.x) }
                    if (nearestKf != null && abs(nearestKf * fw - pos.x) <= hitRadiusPx) {
                        if (onSelectKeyframe != null) onSelectKeyframe(nearestKf)
                        else onJumpFrame(nearestKf)
                    } else {
                        onSelectTrack()
                        onJumpFrame(tappedFrame)
                    }
                }
            },
    ) {
        val h = size.height
        val fw = frameWidthDp.toPx().coerceAtLeast(1f)
        drawFrameGrid(durationFrames, majorInterval, fw)

        // Active in/out layer span bar (4dp rounded caps, solid tonal studio fill)
        val clampedIn = inFrame.coerceIn(0, maxFrame)
        val clampedOut = outFrame.coerceIn(clampedIn, durationFrames)
        val x0 = clampedIn * fw
        val spanW = max(8f, (clampedOut - clampedIn) * fw)
        val barAlpha = if (!isVisible) 0.08f else if (isSelected) 0.34f else 0.22f

        drawRoundRect(
            color = accent.copy(alpha = barAlpha),
            topLeft = Offset(x0, 6f),
            size = Size(spanW, h - 12f),
            cornerRadius = CornerRadius(4f, 4f),
        )
        drawRoundRect(
            color = accent.copy(alpha = if (isSelected) 0.85f else if (isVisible) 0.45f else 0.16f),
            topLeft = Offset(x0, 6f),
            size = Size(spanW, h - 12f),
            cornerRadius = CornerRadius(4f, 4f),
            style = Stroke(width = if (isSelected) 1.5f else 1f),
        )

        // Modifier duration strips
        for ((ms, me) in modifierSpans) {
            val mx0 = ms.coerceIn(0, maxFrame) * fw
            val mx1 = me.coerceIn(ms + 1, durationFrames) * fw
            drawRect(
                color = StudioSecondary.copy(alpha = 0.78f),
                topLeft = Offset(mx0, h - 8f),
                size = Size(max(4f, mx1 - mx0), 2.5f),
            )
        }

        // Lyrics cue segments
        for ((cs, ce) in lyricCues) {
            val cx0 = cs.coerceIn(0, maxFrame) * fw
            val cx1 = ce.coerceIn(cs + 1, durationFrames) * fw
            drawRoundRect(
                color = StudioAmber.copy(alpha = 0.42f),
                topLeft = Offset(cx0, 9f),
                size = Size(max(4f, cx1 - cx0), h - 18f),
                cornerRadius = CornerRadius(3f, 3f),
            )
        }

        // Keyframe summary diamonds with clear selected halo
        for (kf in keyframeFrames) {
            val kx = kf * fw
            val cy = h / 2f
            val isSelKf = selectedKeyframeFrame != null && kf == selectedKeyframeFrame
            val isAtPlayhead = kf == currentFrame
            if (isSelKf) {
                drawCircle(
                    color = StudioPrimary.copy(alpha = 0.30f),
                    radius = 12f,
                    center = Offset(kx, cy),
                )
                drawCircle(
                    color = StudioPrimaryBright,
                    radius = 12f,
                    center = Offset(kx, cy),
                    style = Stroke(width = 1.5f),
                )
            }
            drawKeyframeDiamond(
                cx = kx,
                cy = cy,
                radius = when {
                    isSelKf -> 7f
                    isAtPlayhead -> 6f
                    else -> 5f
                },
                fillColor = when {
                    isSelKf -> Color.White
                    isAtPlayhead -> StudioPrimaryBright
                    else -> StudioPrimary
                },
                strokeColor = if (isSelKf) StudioPrimary else if (isLocked) StudioTextMuted else StudioSurfaceLowest,
            )
        }

        // Vertical CTI laser line (#4B9BE8 with subtle glow)
        val px = currentFrame * fw
        drawLine(StudioPrimary.copy(alpha = 0.25f), Offset(px, 0f), Offset(px, h), strokeWidth = 4.5f)
        drawLine(StudioPrimary, Offset(px, 0f), Offset(px, h), strokeWidth = 1.6f)
    }
}

@Composable
internal fun TimelineViewportMinimap(
    durationFrames: Int,
    currentFrame: Int,
    scrollValue: Int,
    maxScrollValue: Int,
    viewportWidthPx: Float,
    contentWidthPx: Float,
    onJumpFrame: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val maxFrame = max(0, durationFrames - 1)
    Canvas(
        modifier = modifier
            .background(StudioSurface)
            .pointerInput(durationFrames) {
                fun xToFrame(x: Float): Int {
                    val ratio = if (size.width > 0) (x / size.width.toFloat()).coerceIn(0f, 1f) else 0f
                    return (ratio * maxFrame).roundToInt().coerceIn(0, maxFrame)
                }
                detectTapGestures { pos ->
                    onJumpFrame(xToFrame(pos.x))
                }
            }
            .pointerInput(durationFrames) {
                fun xToFrame(x: Float): Int {
                    val ratio = if (size.width > 0) (x / size.width.toFloat()).coerceIn(0f, 1f) else 0f
                    return (ratio * maxFrame).roundToInt().coerceIn(0, maxFrame)
                }
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    onJumpFrame(xToFrame(change.position.x))
                }
            }
            .testTag("timeline_viewport_minimap"),
    ) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        val safeContentW = contentWidthPx.coerceAtLeast(viewportWidthPx.coerceAtLeast(1f))
        val viewFraction = (viewportWidthPx / safeContentW).coerceIn(0.06f, 1f)
        val startFraction = if (maxScrollValue > 0) {
            (scrollValue.toFloat() / safeContentW).coerceIn(0f, 1f - viewFraction)
        } else {
            0f
        }
        val thumbX = startFraction * w
        val thumbW = (viewFraction * w).coerceAtLeast(14f).coerceAtMost(w - thumbX)

        drawRoundRect(
            color = StudioPrimary.copy(alpha = if (maxScrollValue > 0) 0.28f else 0.14f),
            topLeft = Offset(thumbX, 1.5f),
            size = Size(thumbW, h - 3f),
            cornerRadius = CornerRadius(3f, 3f),
        )
        drawRoundRect(
            color = StudioPrimaryBright.copy(alpha = if (maxScrollValue > 0) 0.72f else 0.35f),
            topLeft = Offset(thumbX, 1.5f),
            size = Size(thumbW, h - 3f),
            cornerRadius = CornerRadius(3f, 3f),
            style = Stroke(width = 1f),
        )

        val playRatio = if (maxFrame > 0) currentFrame.toFloat() / maxFrame.toFloat() else 0f
        val playX = (playRatio * w).coerceIn(1f, w - 1f)
        drawLine(
            color = StudioPrimary,
            start = Offset(playX, 0f),
            end = Offset(playX, h),
            strokeWidth = 2f,
        )
    }
}

@Composable
internal fun TimelineChannelSubRow(
    channel: TimelineChannelRowData,
    headerWidthDp: Dp,
    scrollState: ScrollState,
    contentWidthDp: Dp,
    frameWidthDp: Dp,
    durationFrames: Int,
    majorInterval: Int,
    currentFrame: Int,
    textMeasurer: TextMeasurer,
    onJumpFrame: (Int) -> Unit,
) {
    val maxFrame = max(0, durationFrames - 1)
    val sortedKfs = remember(channel.keyframes) { channel.keyframes.sortedBy { it.frame } }
    val haptic = LocalHapticFeedback.current
    var draggingKfId by remember { mutableStateOf<String?>(null) }
    var draggingTargetFrame by remember { mutableIntStateOf(0) }

    TimelineStickyRow(
        headerWidthDp = headerWidthDp,
        scrollState = scrollState,
        contentWidthDp = contentWidthDp,
        rowHeight = 34.dp,
        isSelected = channel.selectedKeyframeFrame != null,
        headerContent = {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(StudioWorkspaceCanvas)
                    .padding(start = 18.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(channel.accent),
                )
                Text(
                    text = channel.label,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = if (channel.selectedKeyframeFrame != null) StudioTextPrimary else StudioTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (draggingKfId != null) "F$draggingTargetFrame" else channel.shortValue,
                    style = StudioMonoStyle.copy(fontSize = 10.sp),
                    color = when {
                        draggingKfId != null -> StudioPrimaryBright
                        channel.hasKeyAtCurrent -> StudioPrimary
                        else -> StudioTextMuted
                    },
                    maxLines = 1,
                )
                if (sortedKfs.isNotEmpty() && channel.onClearChannel != null) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .clickable(onClick = channel.onClearChannel)
                            .testTag("clear_channel_${channel.id}"),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear ${channel.label} animation",
                            tint = StudioRose.copy(alpha = 0.8f),
                            modifier = Modifier.size(13.dp),
                        )
                    }
                }
                KeyframeDiamondButton(
                    hasKeyframeAtFrame = channel.hasKeyAtCurrent,
                    hasAnyKeyframes = sortedKfs.isNotEmpty(),
                    onClick = channel.onToggleKeyAtCurrent,
                    modifier = Modifier.size(26.dp),
                )
            }
        },
        trackContent = {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .background(StudioInsetField)
                    .pointerInput(durationFrames, frameWidthDp, sortedKfs, channel.onSelectKeyframe) {
                        val fw = frameWidthDp.toPx().coerceAtLeast(1f)
                        val hitRadiusPx = max(22.dp.toPx(), fw * 1.6f)
                        detectTapGestures { pos ->
                            val tappedFrame = (pos.x / fw).roundToInt().coerceIn(0, maxFrame)
                            val nearestKf = sortedKfs.minByOrNull { abs(it.frame * fw - pos.x) }
                            if (nearestKf != null && abs(nearestKf.frame * fw - pos.x) <= hitRadiusPx) {
                                if (channel.onSelectKeyframe != null) channel.onSelectKeyframe.invoke(nearestKf.frame)
                                else onJumpFrame(nearestKf.frame)
                            } else {
                                onJumpFrame(tappedFrame)
                            }
                        }
                    }
                    .pointerInput(durationFrames, frameWidthDp, sortedKfs, channel.onMoveKeyframe) {
                        val moveCallback = channel.onMoveKeyframe ?: return@pointerInput
                        val fw = frameWidthDp.toPx().coerceAtLeast(1f)
                        val hitRadiusPx = max(24.dp.toPx(), fw * 1.6f)
                        detectDragGesturesAfterLongPress(
                            onDragStart = { startOffset ->
                                val nearest = sortedKfs.minByOrNull { abs(it.frame * fw - startOffset.x) }
                                if (nearest != null && abs(nearest.frame * fw - startOffset.x) <= hitRadiusPx) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    draggingKfId = nearest.id
                                    draggingTargetFrame = nearest.frame
                                    channel.onSelectKeyframe?.invoke(nearest.frame)
                                }
                            },
                            onDrag = { change, _ ->
                                if (draggingKfId != null) {
                                    change.consume()
                                    draggingTargetFrame = (change.position.x / fw).roundToInt().coerceIn(0, maxFrame)
                                }
                            },
                            onDragEnd = {
                                val kfId = draggingKfId
                                val targetFrame = draggingTargetFrame
                                draggingKfId = null
                                if (kfId != null) {
                                    moveCallback(kfId, targetFrame)
                                    onJumpFrame(targetFrame)
                                }
                            },
                            onDragCancel = {
                                draggingKfId = null
                            },
                        )
                    },
            ) {
                val h = size.height
                val fw = frameWidthDp.toPx().coerceAtLeast(1f)
                drawFrameGrid(durationFrames, majorInterval, fw)

                val cy = h / 2f
                for (i in 0 until sortedKfs.lastIndex) {
                    val k0 = sortedKfs[i]
                    val k1 = sortedKfs[i + 1]
                    val spanColor = easingColor(k0.interpolation)
                    drawLine(
                        color = spanColor.copy(alpha = 0.65f),
                        start = Offset(k0.frame * fw, cy),
                        end = Offset(k1.frame * fw, cy),
                        strokeWidth = if (k0.interpolation == InterpolationType.HOLD) 1.5f else 2f,
                    )
                }

                // Distinct Motion Studio Pro Keyframe Glyphs + clear selected highlight halo
                for (kf in sortedKfs) {
                    val isDraggingThis = kf.id == draggingKfId
                    val kx = kf.frame * fw
                    val isSelectedKf = channel.selectedKeyframeFrame != null && kf.frame == channel.selectedKeyframeFrame
                    val isAtPlayhead = kf.frame == currentFrame
                    if (isSelectedKf && !isDraggingThis) {
                        drawCircle(
                            color = StudioPrimary.copy(alpha = 0.30f),
                            radius = 11f,
                            center = Offset(kx, cy),
                        )
                        drawCircle(
                            color = StudioPrimaryBright,
                            radius = 11f,
                            center = Offset(kx, cy),
                            style = Stroke(width = 1.4f),
                        )
                    }
                    drawKeyframeGlyph(
                        cx = kx,
                        cy = cy,
                        radius = when {
                            isSelectedKf -> 6.4f
                            isAtPlayhead -> 5.6f
                            else -> 4.6f
                        },
                        interpolation = kf.interpolation,
                        fillColor = when {
                            isDraggingThis -> Color.White.copy(alpha = 0.3f)
                            isSelectedKf -> Color.White
                            isAtPlayhead -> StudioPrimary
                            else -> easingColor(kf.interpolation)
                        },
                        strokeColor = if (isSelectedKf) StudioPrimary else StudioSurfaceLowest,
                    )
                }

                val px = currentFrame * fw
                drawLine(StudioPrimary.copy(alpha = 0.25f), Offset(px, 0f), Offset(px, h), strokeWidth = 4f)
                drawLine(StudioPrimary, Offset(px, 0f), Offset(px, h), strokeWidth = 1.5f)

                if (draggingKfId != null) {
                    val dx = draggingTargetFrame * fw
                    drawCircle(
                        color = StudioPrimary.copy(alpha = 0.32f),
                        radius = 10f,
                        center = Offset(dx, cy),
                    )
                    drawKeyframeDiamond(
                        cx = dx,
                        cy = cy,
                        radius = 6f,
                        fillColor = Color.White,
                        strokeColor = StudioPrimary,
                    )
                    val labelLayout = textMeasurer.measure(
                        text = "F$draggingTargetFrame",
                        style = TextStyle(color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold),
                    )
                    val pillW = labelLayout.size.width + 8f
                    val pillH = labelLayout.size.height + 2f
                    val pillX = (dx + 9f).coerceAtMost((size.width - pillW - 2f).coerceAtLeast(0f))
                    val pillY = ((h - pillH) / 2f).coerceAtLeast(0f)
                    drawRoundRect(
                        color = StudioPrimary,
                        topLeft = Offset(pillX, pillY),
                        size = Size(pillW, pillH),
                        cornerRadius = CornerRadius(3f, 3f),
                    )
                    drawText(labelLayout, topLeft = Offset(pillX + 4f, pillY + 1f))
                }
            }
        },
    )
}

@Composable
internal fun TimelineAudioTrackRow(
    track: AudioTrackDocument,
    assets: List<AssetRecord>,
    headerWidthDp: Dp,
    scrollState: ScrollState,
    contentWidthDp: Dp,
    frameWidthDp: Dp,
    durationFrames: Int,
    majorInterval: Int,
    currentFrame: Int,
    textMeasurer: TextMeasurer,
    onToggleMute: () -> Unit,
    onToggleSolo: () -> Unit,
    onDeleteTrack: () -> Unit,
    onJumpFrame: (Int) -> Unit,
) {
    val maxFrame = max(0, durationFrames - 1)
    TimelineStickyRow(
        headerWidthDp = headerWidthDp,
        scrollState = scrollState,
        contentWidthDp = contentWidthDp,
        rowHeight = 52.dp,
        isSelected = false,
        headerContent = {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(Icons.Default.GraphicEq, contentDescription = null, tint = StudioSecondary, modifier = Modifier.size(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        track.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = StudioTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        MiniBadgeButton("M", track.muted, StudioRose, onToggleMute)
                        MiniBadgeButton("S", track.solo, StudioAmber, onToggleSolo)
                    }
                }
                IconButton(
                    onClick = onDeleteTrack,
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete audio track", tint = StudioTextSecondary, modifier = Modifier.size(15.dp))
                }
            }
        },
        trackContent = {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(durationFrames, frameWidthDp) {
                        val fw = frameWidthDp.toPx().coerceAtLeast(1f)
                        detectTapGestures { pos ->
                            val f = (pos.x / fw).roundToInt().coerceIn(0, maxFrame)
                            onJumpFrame(f)
                        }
                    },
            ) {
                val h = size.height
                val fw = frameWidthDp.toPx().coerceAtLeast(1f)
                drawFrameGrid(durationFrames, majorInterval, fw)

                for (clip in track.clips) {
                    val x0 = clip.startFrame * fw
                    val cw = max(8f, clip.durationFrames * fw)
                    drawRoundRect(
                        color = StudioSecondary.copy(alpha = 0.24f),
                        topLeft = Offset(x0, 5f),
                        size = Size(cw, h - 10f),
                        cornerRadius = CornerRadius(4f, 4f),
                    )
                    drawRoundRect(
                        color = StudioSecondary.copy(alpha = 0.72f),
                        topLeft = Offset(x0, 5f),
                        size = Size(cw, h - 10f),
                        cornerRadius = CornerRadius(4f, 4f),
                        style = Stroke(1.2f),
                    )
                    val asset = assets.find { it.id == clip.assetId }
                    val peaks = asset?.waveformPeaks?.ifEmpty { List(32) { 0.4f } } ?: List(32) { 0.4f }
                    val barW = cw / max(1, peaks.size)
                    peaks.forEachIndexed { i, pk ->
                        val bh = pk * (h - 14f)
                        val bx = x0 + i * barW + barW / 2f
                        drawLine(
                            color = StudioSecondary.copy(alpha = 0.8f),
                            start = Offset(bx, (h - bh) / 2f),
                            end = Offset(bx, (h + bh) / 2f),
                            strokeWidth = max(1.5f, barW * 0.55f),
                            cap = StrokeCap.Round,
                        )
                    }
                    if (cw >= 40f && clip.name.isNotBlank()) {
                        clipRect(left = x0 + 4f, top = 6f, right = x0 + cw - 4f, bottom = h - 6f) {
                            val labelLayout = textMeasurer.measure(
                                text = clip.name,
                                style = TextStyle(color = StudioTextPrimary, fontSize = 9.sp, fontWeight = FontWeight.Medium),
                                maxLines = 1,
                            )
                            drawRoundRect(
                                color = StudioInsetField.copy(alpha = 0.88f),
                                topLeft = Offset(x0 + 5f, 7f),
                                size = Size(labelLayout.size.width + 6f, labelLayout.size.height + 2f),
                                cornerRadius = CornerRadius(3f, 3f),
                            )
                            drawText(labelLayout, topLeft = Offset(x0 + 8f, 8f))
                        }
                    }
                }
                val playX = currentFrame * fw
                drawLine(StudioPrimary.copy(alpha = 0.25f), Offset(playX, 0f), Offset(playX, h), strokeWidth = 4.5f)
                drawLine(StudioPrimary, Offset(playX, 0f), Offset(playX, h), strokeWidth = 1.6f)
            }
        },
    )
}

@Composable
internal fun TimelineModeTab(
    label: String,
    active: Boolean,
    tag: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(if (active) StudioSurfaceElevated else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 4.dp)
            .testTag(tag),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (active) StudioTextPrimary else StudioTextSecondary,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

@Composable
internal fun MiniBadgeButton(label: String, active: Boolean, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(if (active) color.copy(alpha = 0.28f) else StudioSurfaceElevated)
            .clickable(onClick = onClick)
            .padding(horizontal = 5.dp, vertical = 1.dp),
    ) {
        Text(label, style = StudioMonoStyle, color = if (active) color else StudioTextSecondary)
    }
}

@Composable
internal fun SmallTimelineIconBtn(
    icon: ImageVector,
    contentDesc: String,
    tint: Color = StudioTextSecondary,
    enabled: Boolean = true,
    tag: String = "",
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(3.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .then(if (tag.isNotEmpty()) Modifier.testTag(tag) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDesc, tint = tint, modifier = Modifier.size(15.dp))
    }
}
