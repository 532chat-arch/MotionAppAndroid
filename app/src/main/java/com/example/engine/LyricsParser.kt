package com.example.engine

import com.example.schema.LyricsCue
import com.example.schema.LyricsWordTiming
import kotlin.math.max
import kotlin.math.roundToInt

private val LRC_LINE_TAG_REGEX = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")
private val LRC_WORD_TAG_REGEX = Regex("""<(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?>""")
private val SRT_TIME_REGEX = Regex(
    """(\d{1,2}):(\d{2}):(\d{2})[,.](\d{1,3})\s*-->\s*(\d{1,2}):(\d{2}):(\d{2})[,.](\d{1,3})"""
)

private fun parseFractionToSec(frac: String?): Double {
    if (frac.isNullOrEmpty()) return 0.0
    return when (frac.length) {
        1 -> frac.toDouble() / 10.0
        2 -> frac.toDouble() / 100.0
        else -> frac.take(3).toDouble() / 1000.0
    }
}

private fun secondsToFrame(sec: Double, fps: Double): Int =
    max(0, (sec * max(1.0, fps)).roundToInt())

fun splitWordsWithTiming(
    text: String,
    startFrame: Int,
    endFrame: Int,
): List<LyricsWordTiming> {
    val tokens = text.trim().split(Regex("""\s+""")).filter { it.isNotEmpty() }
    if (tokens.isEmpty()) return emptyList()
    val totalSpan = max(1, endFrame - startFrame)
    val totalChars = max(1, tokens.sumOf { max(1, it.length) })

    var cursor = startFrame.toDouble()
    return tokens.mapIndexed { idx, word ->
        val weight = max(1, word.length).toDouble() / totalChars.toDouble()
        val wStart = cursor.roundToInt()
        val wEnd = if (idx == tokens.lastIndex) {
            max(wStart + 1, endFrame)
        } else {
            max(wStart + 1, (cursor + totalSpan * weight).roundToInt())
        }
        cursor += totalSpan * weight
        LyricsWordTiming(text = word, startFrame = wStart, endFrame = wEnd)
    }
}

fun parseLrc(
    rawText: String,
    fps: Double,
    defaultLineDurationSec: Double = 3.0,
): List<LyricsCue> {
    data class RawLine(
        val startSec: Double,
        val rawBody: String,
        val order: Int,
    )

    val entries = mutableListOf<RawLine>()
    var order = 0
    for (line in rawText.lines()) {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) continue
        val matches = LRC_LINE_TAG_REGEX.findAll(trimmed).toList()
        if (matches.isEmpty()) continue
        val body = trimmed.replace(LRC_LINE_TAG_REGEX, "").trim()
        if (body.isEmpty()) continue
        for (m in matches) {
            val mins = m.groupValues[1].toIntOrNull() ?: 0
            val secs = m.groupValues[2].toIntOrNull() ?: 0
            val frac = parseFractionToSec(m.groupValues[3])
            val totalSec = mins * 60.0 + secs + frac
            entries += RawLine(totalSec, body, order++)
        }
    }

    entries.sortWith(compareBy<RawLine> { it.startSec }.thenBy { it.order })
    return entries.mapIndexed { idx, entry ->
        val startFrame = secondsToFrame(entry.startSec, fps)
        val nextStartSec = entries.getOrNull(idx + 1)?.startSec
        val endSec = if (nextStartSec != null && nextStartSec > entry.startSec) {
            minOf(nextStartSec, entry.startSec + max(defaultLineDurationSec, nextStartSec - entry.startSec))
        } else {
            entry.startSec + defaultLineDurationSec
        }
        val endFrame = max(startFrame + 2, secondsToFrame(endSec, fps))

        val wordMatches = LRC_WORD_TAG_REGEX.findAll(entry.rawBody).toList()
        if (wordMatches.isNotEmpty()) {
            val words = mutableListOf<LyricsWordTiming>()
            val cleanTokens = mutableListOf<String>()
            for (i in wordMatches.indices) {
                val m = wordMatches[i]
                val wMins = m.groupValues[1].toIntOrNull() ?: 0
                val wSecs = m.groupValues[2].toIntOrNull() ?: 0
                val wFrac = parseFractionToSec(m.groupValues[3])
                val wStart = secondsToFrame(wMins * 60.0 + wSecs + wFrac, fps)
                val segmentEndIdx = if (i + 1 < wordMatches.size) wordMatches[i + 1].range.first else entry.rawBody.length
                val segText = entry.rawBody.substring(m.range.last + 1, segmentEndIdx).trim()
                if (segText.isNotEmpty()) {
                    val nextWStart = if (i + 1 < wordMatches.size) {
                        val nm = wordMatches[i + 1]
                        val nMins = nm.groupValues[1].toIntOrNull() ?: 0
                        val nSecs = nm.groupValues[2].toIntOrNull() ?: 0
                        val nFrac = parseFractionToSec(nm.groupValues[3])
                        secondsToFrame(nMins * 60.0 + nSecs + nFrac, fps)
                    } else {
                        endFrame
                    }
                    cleanTokens += segText
                    words += LyricsWordTiming(
                        text = segText,
                        startFrame = wStart,
                        endFrame = max(wStart + 1, nextWStart),
                    )
                }
            }
            val cleanText = cleanTokens.joinToString(" ").ifEmpty {
                entry.rawBody.replace(LRC_WORD_TAG_REGEX, "").trim()
            }
            LyricsCue(
                id = "cue_lrc_${idx + 1}_$startFrame",
                startFrame = startFrame,
                endFrame = endFrame,
                text = cleanText,
                words = if (words.isNotEmpty()) words else splitWordsWithTiming(cleanText, startFrame, endFrame),
            )
        } else {
            LyricsCue(
                id = "cue_lrc_${idx + 1}_$startFrame",
                startFrame = startFrame,
                endFrame = endFrame,
                text = entry.rawBody,
                words = splitWordsWithTiming(entry.rawBody, startFrame, endFrame),
            )
        }
    }
}

fun parseSrt(rawText: String, fps: Double): List<LyricsCue> {
    val blocks = rawText.replace("\r\n", "\n").split(Regex("""\n\s*\n"""))
    val cues = mutableListOf<LyricsCue>()
    var idx = 0
    for (block in blocks) {
        val lines = block.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.size < 2) continue
        val timeIdx = lines.indexOfFirst { SRT_TIME_REGEX.containsMatchIn(it) }
        if (timeIdx < 0) continue
        val m = SRT_TIME_REGEX.find(lines[timeIdx]) ?: continue
        val sH = m.groupValues[1].toIntOrNull() ?: 0
        val sM = m.groupValues[2].toIntOrNull() ?: 0
        val sS = m.groupValues[3].toIntOrNull() ?: 0
        val sMs = m.groupValues[4].padEnd(3, '0').take(3).toIntOrNull() ?: 0
        val eH = m.groupValues[5].toIntOrNull() ?: 0
        val eM = m.groupValues[6].toIntOrNull() ?: 0
        val eS = m.groupValues[7].toIntOrNull() ?: 0
        val eMs = m.groupValues[8].padEnd(3, '0').take(3).toIntOrNull() ?: 0

        val startSec = sH * 3600.0 + sM * 60.0 + sS + sMs / 1000.0
        val endSec = eH * 3600.0 + eM * 60.0 + eS + eMs / 1000.0
        val text = lines.drop(timeIdx + 1).joinToString(" ").replace(Regex("""<[^>]+>"""), "").trim()
        if (text.isEmpty()) continue

        val startFrame = secondsToFrame(startSec, fps)
        val endFrame = max(startFrame + 2, secondsToFrame(endSec, fps))
        idx++
        cues += LyricsCue(
            id = "cue_srt_${idx}_$startFrame",
            startFrame = startFrame,
            endFrame = endFrame,
            text = text,
            words = splitWordsWithTiming(text, startFrame, endFrame),
        )
    }
    return cues.sortedBy { it.startFrame }
}

fun parsePlainLyrics(
    rawText: String,
    fps: Double,
    startFrame: Int = 0,
    lineDurationSec: Double = 2.5,
): List<LyricsCue> {
    val lines = rawText.lines().map { it.trim() }.filter { it.isNotEmpty() }
    val span = max(6, (lineDurationSec * max(1.0, fps)).roundToInt())
    return lines.mapIndexed { idx, text ->
        val s = startFrame + idx * span
        val e = s + span - 2
        LyricsCue(
            id = "cue_plain_${idx + 1}_$s",
            startFrame = s,
            endFrame = max(s + 2, e),
            text = text,
            words = splitWordsWithTiming(text, s, max(s + 2, e)),
        )
    }
}
