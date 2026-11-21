package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.schema.parseHexColor
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioBorderStrong
import com.example.ui.theme.StudioInsetField
import com.example.ui.theme.StudioMonoStyle
import com.example.ui.theme.StudioPrimary
import com.example.ui.theme.StudioPrimaryActive
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.StudioTextPrimary
import com.example.ui.theme.StudioTextSecondary
import kotlin.math.roundToInt

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.titleSmall,
            color = StudioTextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        trailing?.invoke()
    }
}

@Composable
fun KeyframeDiamondButton(
    hasKeyframeAtFrame: Boolean,
    hasAnyKeyframes: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val desc = stringResource(R.string.cd_toggle_keyframe)
    val tint = when {
        hasKeyframeAtFrame -> StudioPrimary
        hasAnyKeyframes -> StudioPrimary.copy(alpha = 0.6f)
        else -> StudioTextSecondary.copy(alpha = 0.6f)
    }
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .semantics { contentDescription = desc },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .rotate(45f)
                .background(if (hasKeyframeAtFrame) tint else Color.Transparent, RoundedCornerShape(1.dp))
                .border(1.5.dp, tint, RoundedCornerShape(1.dp)),
        )
    }
}

@Composable
fun NumberScrubber(
    label: String,
    value: Double,
    step: Double = 1.0,
    min: Double? = null,
    max: Double? = null,
    suffix: String = "",
    decimals: Int = 1,
    hasKeyframeAtFrame: Boolean? = null,
    hasAnyKeyframes: Boolean = false,
    onToggleKeyframe: (() -> Unit)? = null,
    onChange: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    fun clamp(v: Double): Double {
        var out = v
        if (min != null) out = kotlin.math.max(min, out)
        if (max != null) out = kotlin.math.min(max, out)
        return out
    }

    val currentVal by rememberUpdatedState(value)
    val currentOnChange by rememberUpdatedState(onChange)
    var dragAccum by remember { mutableFloatStateOf(0f) }

    val formatted = if (decimals == 0) {
        "${value.roundToInt()}$suffix"
    } else {
        "%.${decimals}f%s".format(value, suffix)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(StudioInsetField)
            .border(1.dp, StudioBorder, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (onToggleKeyframe != null && hasKeyframeAtFrame != null) {
            KeyframeDiamondButton(
                hasKeyframeAtFrame = hasKeyframeAtFrame,
                hasAnyKeyframes = hasAnyKeyframes,
                onClick = onToggleKeyframe,
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = StudioTextSecondary,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(StudioSurfaceElevated)
                .border(1.dp, StudioBorder, RoundedCornerShape(4.dp))
                .clickable { onChange(clamp(value - step)) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Remove, contentDescription = "Decrease $label", tint = StudioTextPrimary, modifier = Modifier.size(15.dp))
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(3.dp))
                .pointerInput(step, min, max) {
                    detectHorizontalDragGestures(
                        onDragStart = { dragAccum = 0f },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            dragAccum += dragAmount
                            val pxPerStep = 8f
                            val steps = (dragAccum / pxPerStep).toInt()
                            if (steps != 0) {
                                dragAccum -= steps * pxPerStep
                                currentOnChange(clamp(currentVal + steps * step))
                            }
                        },
                    )
                }
                .padding(horizontal = 6.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = formatted,
                style = StudioMonoStyle,
                color = StudioPrimary,
            )
        }
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(StudioSurfaceElevated)
                .border(1.dp, StudioBorder, RoundedCornerShape(4.dp))
                .clickable { onChange(clamp(value + step)) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Add, contentDescription = "Increase $label", tint = StudioTextPrimary, modifier = Modifier.size(15.dp))
        }
    }
}

@Composable
fun StudioSliderRow(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    valueLabel: String,
    onValueChange: (Float) -> Unit,
    accentColor: Color = StudioPrimary,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium, color = StudioTextSecondary)
            Text(text = valueLabel, style = StudioMonoStyle, color = accentColor)
        }
        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = StudioTextPrimary,
                activeTrackColor = accentColor,
                inactiveTrackColor = StudioInsetField,
            ),
            modifier = Modifier.height(30.dp),
        )
    }
}

val STUDIO_SWATCH_COLORS = listOf(
    "#E8E8E8", "#A5A5A5", "#64748B", "#131313",
    "#4B9BE8", "#9ECAFF", "#9999CC", "#76A982",
    "#D6A24D", "#FB923C", "#D66B67", "#A855F7",
)

@Composable
fun ColorSwatchRow(
    label: String,
    selectedHex: String,
    onSelect: (String) -> Unit,
    swatches: List<String> = STUDIO_SWATCH_COLORS,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = StudioTextSecondary)
            Text(selectedHex.uppercase(), style = StudioMonoStyle, color = StudioPrimary)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            for (hex in swatches) {
                val color = Color(parseHexColor(hex))
                val isSelected = selectedHex.equals(hex, ignoreCase = true)
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(color)
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) StudioPrimary else StudioBorderStrong,
                            shape = RoundedCornerShape(4.dp),
                        )
                        .clickable { onSelect(hex) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSelected) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Selected color $hex",
                            tint = if (hex.equals("#E8E8E8", true) || hex.equals("#A5A5A5", true) || hex.equals("#9ECAFF", true)) Color.Black else Color.White,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StudioChipRow(
    options: List<Pair<String, String>>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = StudioPrimary,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for ((key, label) in options) {
            val active = key == selectedKey
            Box(
                modifier = Modifier
                    .heightIn(min = 32.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (active) StudioPrimaryActive.copy(alpha = 0.28f) else StudioInsetField)
                    .border(
                        1.dp,
                        if (active) accent else StudioBorder,
                        RoundedCornerShape(4.dp),
                    )
                    .clickable { onSelect(key) }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
                    .testTag("chip_$key"),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (active) StudioTextPrimary else StudioTextSecondary,
                    maxLines = 1,
                )
            }
        }
    }
}
