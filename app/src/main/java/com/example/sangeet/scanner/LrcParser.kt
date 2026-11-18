package com.example.sangeet.scanner

import com.example.sangeet.data.model.LyricsLine
import java.util.Locale
import kotlin.math.abs

data class LyricsWord(
    val startMs: Long,
    val endMs: Long,
    val text: String
)

data class EnhancedLyricsLine(
    val timestampMs: Long,
    val endTimestampMs: Long,
    val text: String,
    val words: List<LyricsWord> = emptyList()
) {
    val hasWordSync: Boolean get() = words.isNotEmpty()
}

enum class LyricsSyncType(val badgeLabel: String) {
    NONE("NO LYRICS"),
    PLAIN_TEXT("PLAIN TEXT"),
    SYNCED_LRC("SYNCED LRC"),
    ENHANCED_LRC("WORD SYNC")
}

data class ParsedLyricsDocument(
    val lines: List<EnhancedLyricsLine>,
    val syncType: LyricsSyncType,
    val titleTag: String = "",
    val artistTag: String = "",
    val albumTag: String = "",
    val embeddedOffsetMs: Long = 0L
) {
    val isSynchronized: Boolean
        get() = syncType == LyricsSyncType.SYNCED_LRC || syncType == LyricsSyncType.ENHANCED_LRC

    val isWordSynced: Boolean
        get() = syncType == LyricsSyncType.ENHANCED_LRC
}

object LrcParser {

    private val LINE_TIMESTAMP_REGEX = Regex("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?\\]")
    private val METADATA_TAG_REGEX = Regex("^\\[(ti|ar|al|au|by|offset|length|re|ve):(.*)\\]$", RegexOption.IGNORE_CASE)
    private val WORD_TIMESTAMP_REGEX = Regex("<(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?>")

    fun parse(lrcContent: String): List<LyricsLine> {
        val doc = parseDocument(lrcContent)
        return doc.lines.map { LyricsLine(timestampMs = it.timestampMs, text = it.text) }
    }

    fun parseDocument(rawContent: String): ParsedLyricsDocument {
        if (rawContent.isBlank()) {
            return ParsedLyricsDocument(lines = emptyList(), syncType = LyricsSyncType.NONE)
        }

        var titleTag = ""
        var artistTag = ""
        var albumTag = ""
        var embeddedOffsetMs = 0L

        val timedEntries = mutableListOf<EnhancedLyricsLine>()
        val plainLines = mutableListOf<String>()
        var hasWordTags = false

        val normalizedLines = rawContent.replace("\r\n", "\n").replace('\r', '\n').lines()
        for (rawLine in normalizedLines) {
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty()) continue

            val timestampMatches = LINE_TIMESTAMP_REGEX.findAll(trimmed).toList()
            if (timestampMatches.isNotEmpty()) {
                val contentStart = timestampMatches.last().range.last + 1
                val lyricBody = if (contentStart <= trimmed.length) trimmed.substring(contentStart).trim() else ""
                if (lyricBody.isBlank()) continue

                val (cleanText, rawWords) = parseWordsAndCleanText(lyricBody)
                if (cleanText.isBlank()) continue

                val baseWordsTimestampShiftSupported = timestampMatches.size == 1 && rawWords.isNotEmpty()
                if (rawWords.isNotEmpty()) hasWordTags = true

                for (match in timestampMatches) {
                    val lineTs = parseTimestampParts(
                        match.groupValues[1],
                        match.groupValues[2],
                        match.groupValues[3]
                    )
                    val resolvedWords = if (rawWords.isNotEmpty()) {
                        if (baseWordsTimestampShiftSupported) {
                            rawWords.map { word ->
                                val adjustedStart = if (word.startMs < 0L) lineTs else word.startMs
                                word.copy(startMs = adjustedStart)
                            }
                        } else {
                            // Repeated chorus line: rebase relative word offsets onto this occurrence's timestamp
                            val firstTimed = rawWords.firstOrNull { it.startMs >= 0L }?.startMs ?: lineTs
                            rawWords.map { word ->
                                val delta = if (word.startMs < 0L) 0L else (word.startMs - firstTimed).coerceAtLeast(0L)
                                word.copy(startMs = lineTs + delta, endMs = lineTs + delta)
                            }
                        }
                    } else {
                        emptyList()
                    }
                    timedEntries.add(
                        EnhancedLyricsLine(
                            timestampMs = lineTs,
                            endTimestampMs = lineTs + 4000L,
                            text = cleanText,
                            words = resolvedWords
                        )
                    )
                }
                continue
            }

            // Check metadata header tag such as [ar:Artist], [ti:Title], [offset:-500]
            val metaMatch = METADATA_TAG_REGEX.matchEntire(trimmed)
            if (metaMatch != null) {
                val key = metaMatch.groupValues[1].lowercase(Locale.ROOT)
                val value = metaMatch.groupValues[2].trim()
                when (key) {
                    "ti" -> if (value.isNotBlank()) titleTag = value
                    "ar" -> if (value.isNotBlank()) artistTag = value
                    "al" -> if (value.isNotBlank()) albumTag = value
                    "offset" -> embeddedOffsetMs = (value.toLongOrNull() ?: 0L).coerceIn(-10_000L, 10_000L)
                }
                continue
            }

            // Unrecognized bracket-only tag? skip if looks like pure metadata `[id:val]`
            if (trimmed.startsWith("[") && trimmed.endsWith("]") && trimmed.contains(':')) {
                continue
            }

            // Plain text line fallback
            plainLines.add(stripWordTags(trimmed))
        }

        if (timedEntries.isNotEmpty()) {
            val sorted = timedEntries.sortedBy { it.timestampMs }
            val finalized = sorted.mapIndexed { index, line ->
                val nextLineStart = sorted.getOrNull(index + 1)?.timestampMs ?: (line.timestampMs + 5000L)
                val lineEnd = maxOf(line.timestampMs + 400L, nextLineStart)
                val finalizedWords = if (line.words.isEmpty()) {
                    emptyList()
                } else {
                    line.words.mapIndexed { wIdx, w ->
                        val wEnd = line.words.getOrNull(wIdx + 1)?.startMs ?: lineEnd
                        w.copy(endMs = maxOf(w.startMs + 80L, wEnd))
                    }
                }
                line.copy(endTimestampMs = lineEnd, words = finalizedWords)
            }
            return ParsedLyricsDocument(
                lines = finalized,
                syncType = if (hasWordTags) LyricsSyncType.ENHANCED_LRC else LyricsSyncType.SYNCED_LRC,
                titleTag = titleTag,
                artistTag = artistTag,
                albumTag = albumTag,
                embeddedOffsetMs = embeddedOffsetMs
            )
        }

        if (plainLines.isNotEmpty()) {
            val mappedPlain = plainLines.mapIndexed { idx, line ->
                val ts = idx * 3000L
                EnhancedLyricsLine(
                    timestampMs = ts,
                    endTimestampMs = ts + 3000L,
                    text = line,
                    words = emptyList()
                )
            }
            return ParsedLyricsDocument(
                lines = mappedPlain,
                syncType = LyricsSyncType.PLAIN_TEXT,
                titleTag = titleTag,
                artistTag = artistTag,
                albumTag = albumTag,
                embeddedOffsetMs = embeddedOffsetMs
            )
        }

        return ParsedLyricsDocument(
            lines = emptyList(),
            syncType = LyricsSyncType.NONE,
            titleTag = titleTag,
            artistTag = artistTag,
            albumTag = albumTag,
            embeddedOffsetMs = embeddedOffsetMs
        )
    }

    private fun parseWordsAndCleanText(body: String): Pair<String, List<LyricsWord>> {
        val wordMatches = WORD_TIMESTAMP_REGEX.findAll(body).toList()
        if (wordMatches.isEmpty()) {
            return body.trim() to emptyList()
        }

        val words = mutableListOf<LyricsWord>()
        val cleanBuilder = StringBuilder()

        // Text appearing before the very first inline <mm:ss.xx> tag
        val firstMatch = wordMatches.first()
        if (firstMatch.range.first > 0) {
            val prefix = body.substring(0, firstMatch.range.first)
            if (prefix.isNotEmpty()) {
                cleanBuilder.append(prefix)
                if (prefix.isNotBlank()) {
                    words.add(LyricsWord(startMs = -1L, endMs = -1L, text = prefix))
                }
            }
        }

        for (i in wordMatches.indices) {
            val current = wordMatches[i]
            val startMs = parseTimestampParts(
                current.groupValues[1],
                current.groupValues[2],
                current.groupValues[3]
            )
            val segStart = current.range.last + 1
            val segEnd = wordMatches.getOrNull(i + 1)?.range?.first ?: body.length
            val segment = if (segStart < segEnd) body.substring(segStart, segEnd) else ""
            if (segment.isNotEmpty()) {
                cleanBuilder.append(segment)
                words.add(LyricsWord(startMs = startMs, endMs = startMs, text = segment))
            }
        }

        val cleanText = cleanBuilder.toString().trim()
        return if (cleanText.isBlank()) "" to emptyList() else cleanText to words
    }

    fun stripWordTags(text: String): String {
        return WORD_TIMESTAMP_REGEX.replace(text, "").replace(Regex("\\s{2,}"), " ").trim()
    }

    private fun parseTimestampParts(minStr: String, secStr: String, fracStr: String): Long {
        val min = minStr.toLongOrNull() ?: 0L
        val sec = secStr.toLongOrNull() ?: 0L
        val ms = when (fracStr.length) {
            0 -> 0L
            1 -> (fracStr.toLongOrNull() ?: 0L) * 100L
            2 -> (fracStr.toLongOrNull() ?: 0L) * 10L
            else -> fracStr.take(3).toLongOrNull() ?: 0L
        }
        return (min * 60L + sec) * 1000L + ms
    }

    fun formatTimestamp(ms: Long): String {
        val clamped = ms.coerceAtLeast(0L)
        val totalSeconds = clamped / 1000L
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        val centis = (clamped % 1000L) / 10L
        return String.format(Locale.US, "%02d:%02d.%02d", minutes, seconds, centis)
    }

    fun exportToLrc(
        lines: List<EnhancedLyricsLine>,
        title: String = "",
        artist: String = "",
        album: String = "",
        offsetMs: Long = 0L
    ): String {
        val sb = StringBuilder()
        if (title.isNotBlank()) sb.appendLine("[ti:${title.trim()}]")
        if (artist.isNotBlank()) sb.appendLine("[ar:${artist.trim()}]")
        if (album.isNotBlank()) sb.appendLine("[al:${album.trim()}]")
        if (offsetMs != 0L) sb.appendLine("[offset:${offsetMs.coerceIn(-10_000L, 10_000L)}]")

        for (line in lines.sortedBy { it.timestampMs }) {
            val cleanText = line.text.trim()
            if (cleanText.isBlank()) continue
            val tag = formatTimestamp(line.timestampMs)
            if (line.words.isNotEmpty()) {
                val wordText = buildString {
                    for (word in line.words) {
                        append("<${formatTimestamp(word.startMs)}>")
                        append(word.text)
                    }
                }
                sb.appendLine("[$tag]$wordText")
            } else {
                sb.appendLine("[$tag]$cleanText")
            }
        }
        return sb.toString().trimEnd()
    }
}

/**
 * Pure Kotlin synchronization calculator using binary search over parsed timestamps.
 * Offset convention:
 *  - Negative offsetMs (-ms): Advance lyrics (lyrics appear earlier; fixes late lyrics)
 *  - Positive offsetMs (+ms): Delay lyrics (lyrics appear later; fixes early lyrics)
 */
object LyricsSyncEngine {

    const val MIN_OFFSET_MS = -10_000L
    const val MAX_OFFSET_MS = 10_000L

    fun clampOffset(offsetMs: Long): Long = offsetMs.coerceIn(MIN_OFFSET_MS, MAX_OFFSET_MS)

    fun effectiveLineTimestamp(rawTimestampMs: Long, offsetMs: Long): Long {
        return (rawTimestampMs + clampOffset(offsetMs)).coerceAtLeast(0L)
    }

    fun findActiveLineIndex(
        lines: List<EnhancedLyricsLine>,
        playbackPositionMs: Long,
        offsetMs: Long
    ): Int {
        if (lines.isEmpty()) return -1
        val clampedOffset = clampOffset(offsetMs)
        val pos = playbackPositionMs.coerceAtLeast(0L)

        // Binary search for last line whose effective timestamp <= pos
        var low = 0
        var high = lines.lastIndex
        var result = -1

        while (low <= high) {
            val mid = (low + high) ushr 1
            val effectiveTs = (lines[mid].timestampMs + clampedOffset).coerceAtLeast(0L)
            if (effectiveTs <= pos) {
                result = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return result
    }

    fun findActiveWordIndex(
        line: EnhancedLyricsLine,
        playbackPositionMs: Long,
        offsetMs: Long
    ): Int {
        val words = line.words
        if (words.isEmpty()) return -1
        val clampedOffset = clampOffset(offsetMs)
        val pos = playbackPositionMs.coerceAtLeast(0L)

        var active = -1
        for (i in words.indices) {
            val wordStart = (words[i].startMs + clampedOffset).coerceAtLeast(0L)
            if (wordStart <= pos) {
                active = i
            } else {
                break
            }
        }
        return active
    }

    fun wordFillFraction(
        word: LyricsWord,
        playbackPositionMs: Long,
        offsetMs: Long
    ): Float {
        val clampedOffset = clampOffset(offsetMs)
        val start = (word.startMs + clampedOffset).coerceAtLeast(0L)
        val end = maxOf(start + 80L, (word.endMs + clampedOffset).coerceAtLeast(start + 80L))
        return when {
            playbackPositionMs < start -> 0f
            playbackPositionMs >= end -> 1f
            else -> ((playbackPositionMs - start).toFloat() / (end - start).toFloat()).coerceIn(0f, 1f)
        }
    }

    fun formatOffsetDescription(offsetMs: Long): String {
        val clamped = clampOffset(offsetMs)
        return when {
            clamped == 0L -> "0 ms (Synced)"
            clamped < 0L -> "${clamped} ms (Advanced ${abs(clamped)} ms earlier)"
            else -> "+$clamped ms (Delayed $clamped ms later)"
        }
    }
}
