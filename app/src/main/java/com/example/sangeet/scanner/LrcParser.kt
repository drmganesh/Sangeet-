package com.example.sangeet.scanner

import com.example.sangeet.data.model.LyricsLine
import java.util.regex.Pattern

object LrcParser {
    private val LRC_PATTERN = Pattern.compile("\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})\\](.*)")

    fun parse(lrcContent: String): List<LyricsLine> {
        if (lrcContent.isBlank()) return emptyList()

        val lines = mutableListOf<LyricsLine>()
        val rawLines = lrcContent.lines()

        for (line in rawLines) {
            val matcher = LRC_PATTERN.matcher(line.trim())
            if (matcher.matches()) {
                val min = matcher.group(1)?.toLongOrNull() ?: 0L
                val sec = matcher.group(2)?.toLongOrNull() ?: 0L
                val msStr = matcher.group(3) ?: "0"
                val ms = if (msStr.length == 2) msStr.toLong() * 10 else msStr.toLong()
                val totalMs = (min * 60 + sec) * 1000 + ms
                val text = matcher.group(4)?.trim() ?: ""
                if (text.isNotBlank()) {
                    lines.add(LyricsLine(totalMs, text))
                }
            } else if (line.isNotBlank() && !line.startsWith("[")) {
                // Plain lyric line fallback
                lines.add(LyricsLine(lines.size * 3000L, line.trim()))
            }
        }
        return lines.sortedBy { it.timestampMs }
    }
}
