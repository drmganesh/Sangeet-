package com.example.sangeet.scanner

import android.content.Context
import android.net.Uri
import android.util.Log
import android.util.LruCache
import com.example.sangeet.data.local.AppDatabase
import com.example.sangeet.data.model.AudioTrack
import com.example.sangeet.data.model.LyricsLine
import com.example.sangeet.data.model.LyricsMetadataEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.Locale

enum class LyricsSourceType(val label: String) {
    MANUAL_LOCAL("Saved / Edited"),
    EMBEDDED_TAGS("Embedded Audio Tags"),
    LOCAL_FILE("Local .LRC File"),
    LIBRARY_MATCH("Library Match"),
    NONE("No Lyrics")
}

data class ResolvedLyrics(
    val rawContent: String = "",
    val offsetMs: Long = 0L,
    val sourceType: LyricsSourceType = LyricsSourceType.NONE,
    val sourceDetail: String = "",
    val isManual: Boolean = false
) {
    val hasLyrics: Boolean get() = rawContent.isNotBlank()
}

data class LocalLyricsCandidate(
    val id: String,
    val title: String,
    val subtitle: String,
    val sourceType: LyricsSourceType,
    val rawContent: String,
    val matchScore: Int,
    val syncType: LyricsSyncType
)

class LyricsManager(private val context: Context) {

    private val appContext = context.applicationContext
    private val db = AppDatabase.getInstance(appContext)
    private val lyricsDao = db.lyricsDao()
    private val audioTrackDao = db.audioTrackDao()

    companion object {
        private val parsedDocumentCache = LruCache<String, ParsedLyricsDocument>(64)
        private val LYRICS_EXTENSIONS = setOf("lrc", "lrcx", "txt")
        private val TOKEN_SPLIT_REGEX = Regex("[^a-z0-9]+")
    }

    /**
     * Legacy signature preserved for callers expecting Pair<String, Long>.
     */
    suspend fun resolveLyrics(track: AudioTrack): Pair<String, Long> {
        val resolved = resolveLyricsDetailed(track)
        return Pair(resolved.rawContent, resolved.offsetMs)
    }

    /**
     * Resolves lyrics cleanly in priority order without modifying original audio files:
     * 1. Saved / manually associated or edited lyrics in local Room database
     * 2. Embedded lyrics stored in AudioTrack or extracted directly from local audio container (ID3/FLAC/M4A)
     * 3. Sibling .lrc / .txt file in the audio file's directory (matching filename or "Artist - Title")
     */
    suspend fun resolveLyricsDetailed(track: AudioTrack): ResolvedLyrics = withContext(Dispatchers.IO) {
        val entry = lyricsDao.getLyrics(track.id)
        if (entry != null && entry.lyricsPath.isNotBlank()) {
            val inferredSource = if (entry.isManual) {
                LyricsSourceType.MANUAL_LOCAL
            } else if (track.embeddedLyrics.isNotBlank()) {
                LyricsSourceType.EMBEDDED_TAGS
            } else {
                LyricsSourceType.LOCAL_FILE
            }
            return@withContext ResolvedLyrics(
                rawContent = entry.lyricsPath,
                offsetMs = LyricsSyncEngine.clampOffset(entry.customOffsetMs),
                sourceType = inferredSource,
                sourceDetail = if (entry.isManual) "Saved for this song" else "Cached local file",
                isManual = entry.isManual
            )
        }

        val savedOffset = LyricsSyncEngine.clampOffset(entry?.customOffsetMs ?: 0L)

        // Check embedded lyrics on track model first
        if (track.embeddedLyrics.isNotBlank()) {
            return@withContext ResolvedLyrics(
                rawContent = track.embeddedLyrics,
                offsetMs = savedOffset,
                sourceType = LyricsSourceType.EMBEDDED_TAGS,
                sourceDetail = "Track metadata",
                isManual = false
            )
        }

        // On-demand read of embedded USLT/SYLT/Vorbis/MP4 lyrics directly from local audio file
        val extractedEmbedded = EmbeddedLyricsReader.extractEmbeddedLyrics(appContext, track)
        if (extractedEmbedded.isNotBlank()) {
            return@withContext ResolvedLyrics(
                rawContent = extractedEmbedded,
                offsetMs = savedOffset,
                sourceType = LyricsSourceType.EMBEDDED_TAGS,
                sourceDetail = "Embedded ID3 / Tag",
                isManual = false
            )
        }

        // Check sibling file in track's parent directory
        val siblingLyrics = findSiblingLyricsFile(track)
        if (siblingLyrics != null) {
            val content = runCatching { siblingLyrics.readText(StandardCharsets.UTF_8) }.getOrDefault("")
            if (content.isNotBlank()) {
                lyricsDao.insertLyrics(
                    LyricsMetadataEntry(
                        trackId = track.id,
                        lyricsPath = content,
                        customOffsetMs = savedOffset,
                        isManual = false,
                        updatedAt = System.currentTimeMillis()
                    )
                )
                return@withContext ResolvedLyrics(
                    rawContent = content,
                    offsetMs = savedOffset,
                    sourceType = LyricsSourceType.LOCAL_FILE,
                    sourceDetail = siblingLyrics.name,
                    isManual = false
                )
            }
        }

        ResolvedLyrics(rawContent = "", offsetMs = savedOffset, sourceType = LyricsSourceType.NONE)
    }

    private fun findSiblingLyricsFile(track: AudioTrack): File? {
        if (track.path.isBlank()) return null
        return try {
            val trackFile = File(track.path)
            val parent = trackFile.parentFile ?: return null
            if (!parent.exists() || !parent.isDirectory) return null

            val baseName = trackFile.nameWithoutExtension
            val altNames = listOf(
                baseName,
                "${track.artist} - ${track.title}",
                "${track.title} - ${track.artist}",
                track.title
            ).map { it.trim() }.filter { it.isNotBlank() }.distinct()

            for (candidateBase in altNames) {
                for (ext in LYRICS_EXTENSIONS) {
                    val exact = File(parent, "$candidateBase.$ext")
                    if (exact.exists() && exact.isFile && exact.length() > 0L) {
                        return exact
                    }
                }
            }

            // Case-insensitive directory scan for matching filename
            val files = parent.listFiles() ?: return null
            for (f in files) {
                if (!f.isFile || f.length() == 0L) continue
                val ext = f.extension.lowercase(Locale.ROOT)
                if (ext !in LYRICS_EXTENSIONS) continue
                val fBase = f.nameWithoutExtension
                if (altNames.any { it.equals(fBase, ignoreCase = true) }) {
                    return f
                }
            }
            null
        } catch (e: Exception) {
            Log.d("LyricsManager", "Failed to check sibling lyrics: ${e.message}")
            null
        }
    }

    /**
     * 100% offline local search for lyrics across:
     *  - Sibling folders & Lyrics subfolders of current track + indexed music folders
     *  - Existing embedded/cached lyrics across library tracks
     * Ranks matches cleanly and preserves ambiguous/duplicate candidates so users can preview & pick.
     */
    suspend fun searchLocalLyrics(
        queryTitle: String,
        queryArtist: String,
        currentTrack: AudioTrack
    ): List<LocalLyricsCandidate> = withContext(Dispatchers.IO) {
        val cleanTitle = queryTitle.trim().ifBlank { currentTrack.title }
        val cleanArtist = queryArtist.trim()
        val queryTokens = tokenize("$cleanTitle $cleanArtist")

        val candidates = mutableListOf<LocalLyricsCandidate>()
        val seenSignatures = HashSet<String>()

        fun addCandidateIfUnique(
            id: String,
            title: String,
            subtitle: String,
            sourceType: LyricsSourceType,
            raw: String,
            score: Int
        ) {
            val trimmed = raw.trim()
            if (trimmed.isBlank()) return
            val signature = "${trimmed.take(120).hashCode()}_${trimmed.length}"
            if (!seenSignatures.add(signature)) return
            val doc = getOrParseDocument(id.hashCode().toLong(), trimmed)
            if (doc.lines.isEmpty()) return
            candidates.add(
                LocalLyricsCandidate(
                    id = id,
                    title = title,
                    subtitle = subtitle,
                    sourceType = sourceType,
                    rawContent = trimmed,
                    matchScore = score,
                    syncType = doc.syncType
                )
            )
        }

        // 1. Embedded tags directly from current track (useful if overridden)
        val embedded = currentTrack.embeddedLyrics.ifBlank {
            EmbeddedLyricsReader.extractEmbeddedLyrics(appContext, currentTrack)
        }
        if (embedded.isNotBlank()) {
            addCandidateIfUnique(
                id = "embedded_${currentTrack.id}",
                title = "${currentTrack.title} (Embedded Tags)",
                subtitle = currentTrack.artist,
                sourceType = LyricsSourceType.EMBEDDED_TAGS,
                raw = embedded,
                score = 120
            )
        }

        // 2. Scan directories containing music files + Lyrics subfolders
        val allTracks = audioTrackDao.getAllTracksSync()
        val dirsToScan = LinkedHashSet<File>()
        if (currentTrack.path.isNotBlank()) {
            File(currentTrack.path).parentFile?.let { parent ->
                dirsToScan.add(parent)
                File(parent, "Lyrics").takeIf { it.isDirectory }?.let { dirsToScan.add(it) }
                parent.parentFile?.let { gp ->
                    File(gp, "Lyrics").takeIf { it.isDirectory }?.let { dirsToScan.add(it) }
                }
            }
        }
        for (t in allTracks) {
            if (t.path.isNotBlank()) {
                File(t.path).parentFile?.let { dirsToScan.add(it) }
            }
            if (dirsToScan.size >= 40) break
        }

        for (dir in dirsToScan) {
            if (!dir.exists() || !dir.isDirectory) continue
            val files = runCatching { dir.listFiles() }.getOrNull() ?: continue
            for (file in files) {
                if (!file.isFile || file.length() <= 0L || file.length() > 1024 * 1024) continue
                val ext = file.extension.lowercase(Locale.ROOT)
                if (ext !in LYRICS_EXTENSIONS) continue

                val content = runCatching { file.readText(StandardCharsets.UTF_8) }.getOrDefault("")
                if (content.isBlank()) continue

                val fileTokens = tokenize(file.nameWithoutExtension)
                val doc = LrcParser.parseDocument(content)
                val docTokens = tokenize("${doc.titleTag} ${doc.artistTag}")
                val combinedTokens = fileTokens + docTokens

                val overlap = queryTokens.count { q -> combinedTokens.any { it.contains(q) || q.contains(it) } }
                val baseName = File(currentTrack.path).nameWithoutExtension
                val exactFileBonus = if (baseName.isNotBlank() && file.nameWithoutExtension.equals(baseName, ignoreCase = true)) 80 else 0
                val syncedBonus = if (doc.isSynchronized) 25 else 5
                val score = overlap * 25 + exactFileBonus + syncedBonus

                if (score > 10 || queryTokens.isEmpty()) {
                    val displayTitle = doc.titleTag.ifBlank { file.nameWithoutExtension }
                    val displayArtist = doc.artistTag.ifBlank { dir.name }
                    addCandidateIfUnique(
                        id = "file_${file.absolutePath}",
                        title = displayTitle,
                        subtitle = "$displayArtist • ${file.name}",
                        sourceType = LyricsSourceType.LOCAL_FILE,
                        raw = content,
                        score = score
                    )
                }
            }
        }

        // 3. Scan existing lyrics inside library tracks
        for (other in allTracks) {
            val savedEntry = lyricsDao.getLyrics(other.id)
            val content = savedEntry?.lyricsPath?.takeIf { it.isNotBlank() } ?: other.embeddedLyrics
            if (content.isBlank()) continue
            val trackTokens = tokenize("${other.title} ${other.artist} ${other.album}")
            val overlap = queryTokens.count { q -> trackTokens.any { it.contains(q) || q.contains(it) } }
            val titleExact = if (other.title.equals(cleanTitle, ignoreCase = true)) 60 else 0
            val score = overlap * 20 + titleExact
            if (score > 15) {
                addCandidateIfUnique(
                    id = "lib_${other.id}",
                    title = other.title,
                    subtitle = "${other.artist} • In Library",
                    sourceType = LyricsSourceType.LIBRARY_MATCH,
                    raw = content,
                    score = score
                )
            }
        }

        candidates.sortedByDescending { it.matchScore }.take(30)
    }

    private fun tokenize(input: String): Set<String> {
        return input.lowercase(Locale.ROOT)
            .split(TOKEN_SPLIT_REGEX)
            .filter { it.length >= 2 }
            .toSet()
    }

    suspend fun saveCustomLyrics(
        trackId: Long,
        lyricsContent: String,
        offsetMs: Long,
        isManual: Boolean = true
    ) = withContext(Dispatchers.IO) {
        lyricsDao.insertLyrics(
            LyricsMetadataEntry(
                trackId = trackId,
                lyricsPath = lyricsContent,
                customOffsetMs = LyricsSyncEngine.clampOffset(offsetMs),
                isManual = isManual,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun updateLyricsOffset(trackId: Long, offsetMs: Long) = withContext(Dispatchers.IO) {
        val clamped = LyricsSyncEngine.clampOffset(offsetMs)
        val existing = lyricsDao.getLyrics(trackId)
        val text = existing?.lyricsPath ?: ""
        lyricsDao.insertLyrics(
            LyricsMetadataEntry(
                trackId = trackId,
                lyricsPath = text,
                customOffsetMs = clamped,
                isManual = existing?.isManual ?: false,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun clearCustomLyrics(trackId: Long) = withContext(Dispatchers.IO) {
        lyricsDao.deleteLyrics(trackId)
    }

    suspend fun readLyricsFromUri(uri: Uri): String = withContext(Dispatchers.IO) {
        try {
            appContext.contentResolver.openInputStream(uri)?.use { stream ->
                BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { reader ->
                    reader.readText()
                }
            } ?: ""
        } catch (e: Exception) {
            Log.e("LyricsManager", "Error reading lyrics from URI: $uri", e)
            ""
        }
    }

    suspend fun writeLyricsToUri(uri: Uri, lrcContent: String): Boolean = withContext(Dispatchers.IO) {
        try {
            appContext.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(lrcContent.toByteArray(StandardCharsets.UTF_8))
                out.flush()
            } != null
        } catch (e: Exception) {
            Log.e("LyricsManager", "Error writing lyrics to URI: $uri", e)
            false
        }
    }

    fun getOrParseDocument(trackId: Long, rawLyrics: String): ParsedLyricsDocument {
        if (rawLyrics.isBlank()) {
            return ParsedLyricsDocument(lines = emptyList(), syncType = LyricsSyncType.NONE)
        }
        val key = "${trackId}_${rawLyrics.length}_${rawLyrics.hashCode()}"
        parsedDocumentCache.get(key)?.let { return it }
        val parsed = LrcParser.parseDocument(rawLyrics)
        parsedDocumentCache.put(key, parsed)
        return parsed
    }

    /**
     * Kept for backward compatibility; applies offset cleanly once.
     */
    fun parseLyrics(rawLyrics: String, offsetMs: Long): List<LyricsLine> {
        val lines = LrcParser.parse(rawLyrics)
        val clampedOffset = LyricsSyncEngine.clampOffset(offsetMs)
        if (clampedOffset == 0L) return lines
        return lines.map {
            it.copy(timestampMs = (it.timestampMs + clampedOffset).coerceAtLeast(0L))
        }
    }
}
