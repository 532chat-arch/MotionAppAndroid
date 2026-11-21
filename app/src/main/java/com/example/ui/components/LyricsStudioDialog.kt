package com.example.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.commands.uid
import com.example.engine.parseLrc
import com.example.engine.parsePlainLyrics
import com.example.engine.parseSrt
import com.example.engine.splitWordsWithTiming
import com.example.schema.LyricsConfig
import com.example.schema.LyricsCue
import com.example.schema.LyricsStyle
import com.example.ui.theme.StudioAmber
import com.example.ui.theme.StudioBg
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioCyan
import com.example.ui.theme.StudioMonoStyle
import com.example.ui.theme.StudioPanel
import com.example.ui.theme.StudioPrimary
import com.example.ui.theme.StudioRose
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.StudioTextPrimary
import com.example.ui.theme.StudioTextSecondary
import kotlin.math.max

@Composable
fun LyricsStudioDialog(
    initialLyrics: LyricsConfig?,
    fps: Double,
    currentFrame: Int,
    onJumpToFrame: (Int) -> Unit,
    onSave: (LyricsConfig) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var draft by remember { mutableStateOf(initialLyrics ?: LyricsConfig(enabled = true)) }
    var rawImportText by remember { mutableStateOf("") }
    var importFormat by remember { mutableStateOf("lrc") }

    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val txt = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
            if (!txt.isNullOrBlank()) {
                rawImportText = txt
                importFormat = when {
                    txt.contains("-->") -> "srt"
                    txt.contains("[0") -> "lrc"
                    else -> "plain"
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.88f),
            shape = RoundedCornerShape(18.dp),
            color = StudioPanel,
            tonalElevation = 8.dp,
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text("Lyrics & Karaoke Studio", style = MaterialTheme.typography.titleLarge, color = StudioTextPrimary)
                        Text("Import .lrc / .srt or edit frame-synced cues (F$currentFrame)", style = MaterialTheme.typography.labelSmall, color = StudioTextSecondary)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close lyrics studio", tint = StudioTextSecondary)
                    }
                }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(StudioBg)
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("Enable Timed Lyrics", style = MaterialTheme.typography.titleSmall, color = StudioTextPrimary)
                            Switch(checked = draft.enabled, onCheckedChange = { draft = draft.copy(enabled = it) })
                        }
                    }

                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Animation Style", style = MaterialTheme.typography.labelMedium, color = StudioTextSecondary)
                            StudioChipRow(
                                options = LyricsStyle.entries.map { it.name to it.name.lowercase().replace('_', ' ') },
                                selectedKey = draft.style.name,
                                onSelect = { k -> draft = draft.copy(style = LyricsStyle.valueOf(k)) },
                                accent = StudioAmber,
                            )
                        }
                    }

                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            ColorSwatchRow("Highlight Color", draft.highlightColor, onSelect = { draft = draft.copy(highlightColor = it) })
                            ColorSwatchRow("Glow Color", draft.glowColor, onSelect = { draft = draft.copy(glowColor = it) })
                            StudioSliderRow(
                                label = "Tempo (BPM)",
                                value = draft.bpm.toFloat(),
                                valueRange = 60f..200f,
                                valueLabel = "${draft.bpm.toInt()} BPM",
                                onValueChange = { draft = draft.copy(bpm = it.toDouble()) },
                                accentColor = StudioCyan,
                            )
                            StudioSliderRow(
                                label = "Glow Strength",
                                value = draft.glowStrength.toFloat(),
                                valueRange = 0f..36f,
                                valueLabel = "${draft.glowStrength.toInt()} px",
                                onValueChange = { draft = draft.copy(glowStrength = it.toDouble()) },
                                accentColor = StudioAmber,
                            )
                        }
                    }

                    // Importer
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(StudioBg)
                                .border(1.dp, StudioBorder, RoundedCornerShape(12.dp))
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("Import LRC / SRT / Plain Text", style = MaterialTheme.typography.titleSmall, color = StudioTextPrimary)
                                OutlinedButton(onClick = { fileLauncher.launch(arrayOf("text/*", "*/*")) }) {
                                    Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.size(4.dp))
                                    Text("Load File")
                                }
                            }
                            StudioChipRow(
                                options = listOf("lrc" to "LRC Karaoke", "srt" to "SRT Subtitles", "plain" to "Plain Lines"),
                                selectedKey = importFormat,
                                onSelect = { importFormat = it },
                            )
                            OutlinedTextField(
                                value = rawImportText,
                                onValueChange = { rawImportText = it },
                                placeholder = { Text("[00:01.00] First line\n[00:04.20] Second line") },
                                minLines = 3,
                                maxLines = 5,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Button(
                                onClick = {
                                    val parsed = when (importFormat) {
                                        "lrc" -> parseLrc(rawImportText, fps)
                                        "srt" -> parseSrt(rawImportText, fps)
                                        else -> parsePlainLyrics(rawImportText, fps, currentFrame)
                                    }
                                    if (parsed.isNotEmpty()) {
                                        draft = draft.copy(enabled = true, cues = parsed)
                                    }
                                },
                                enabled = rawImportText.isNotBlank(),
                                modifier = Modifier.fillMaxWidth().testTag("parse_lyrics_button"),
                            ) {
                                Text("Parse & Replace Cues (${importFormat.uppercase()})")
                            }
                        }
                    }

                    // Cue List Header
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Timed Cues (${draft.cues.size})", style = MaterialTheme.typography.titleSmall, color = StudioTextPrimary)
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(StudioPrimary)
                                    .clickable {
                                        val start = currentFrame
                                        val end = start + 60
                                        val newCue = LyricsCue(uid("cue"), start, end, "New lyric line", splitWordsWithTiming("New lyric line", start, end))
                                        draft = draft.copy(cues = (draft.cues + newCue).sortedBy { it.startFrame })
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                Text("Add @ F$currentFrame", style = MaterialTheme.typography.labelSmall, color = Color.White)
                            }
                        }
                    }

                    items(draft.cues, key = { it.id }) { cue ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(StudioBg)
                                .border(1.dp, StudioBorder, RoundedCornerShape(10.dp))
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    "F${cue.startFrame} → F${cue.endFrame}",
                                    style = StudioMonoStyle,
                                    color = StudioAmber,
                                    modifier = Modifier.clickable { onJumpToFrame(cue.startFrame) },
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    CueQuickBtn("-6f") {
                                        val ns = max(0, cue.startFrame - 6)
                                        val ne = max(ns + 4, cue.endFrame - 6)
                                        draft = draft.copy(cues = draft.cues.map {
                                            if (it.id == cue.id) it.copy(startFrame = ns, endFrame = ne, words = splitWordsWithTiming(it.text, ns, ne)) else it
                                        })
                                    }
                                    CueQuickBtn("+6f") {
                                        val ns = cue.startFrame + 6
                                        val ne = cue.endFrame + 6
                                        draft = draft.copy(cues = draft.cues.map {
                                            if (it.id == cue.id) it.copy(startFrame = ns, endFrame = ne, words = splitWordsWithTiming(it.text, ns, ne)) else it
                                        })
                                    }
                                    CueQuickBtn("In") {
                                        val ns = currentFrame
                                        val ne = max(ns + 6, cue.endFrame)
                                        draft = draft.copy(cues = draft.cues.map {
                                            if (it.id == cue.id) it.copy(startFrame = ns, endFrame = ne, words = splitWordsWithTiming(it.text, ns, ne)) else it
                                        })
                                    }
                                    CueQuickBtn("Out") {
                                        val ne = max(cue.startFrame + 6, currentFrame)
                                        draft = draft.copy(cues = draft.cues.map {
                                            if (it.id == cue.id) it.copy(endFrame = ne, words = splitWordsWithTiming(it.text, it.startFrame, ne)) else it
                                        })
                                    }
                                    IconButton(
                                        onClick = { draft = draft.copy(cues = draft.cues.filterNot { it.id == cue.id }) },
                                        modifier = Modifier.size(28.dp),
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete cue", tint = StudioRose, modifier = Modifier.size(15.dp))
                                    }
                                }
                            }
                            OutlinedTextField(
                                value = cue.text,
                                onValueChange = { txt ->
                                    draft = draft.copy(cues = draft.cues.map {
                                        if (it.id == cue.id) it.copy(text = txt, words = splitWordsWithTiming(txt, it.startFrame, it.endFrame)) else it
                                    })
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
                    Button(
                        onClick = {
                            onSave(draft)
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f).testTag("save_lyrics_button"),
                    ) {
                        Text("Apply Lyrics", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun CueQuickBtn(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(StudioSurfaceElevated)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp),
    ) {
        Text(label, style = StudioMonoStyle, color = StudioTextPrimary)
    }
}
