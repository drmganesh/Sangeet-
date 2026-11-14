package com.example.sangeet.scanner

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.sangeet.data.local.AppDatabase
import com.example.sangeet.data.model.AudioTrack
import com.example.sangeet.data.model.LyricsLine
import com.example.sangeet.data.model.LyricsMetadataEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

class LyricsManager(private val context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val lyricsDao = db.lyricsDao()

    /**
     * Resolves the raw lyrics string for a track.
     * Checks:
     * 1. Manual/cached entry in database
     * 2. Embedded lyrics in track model
     * 3. Sibling file (.lrc, .txt, .ttml) in same folder
     */
    suspend fun resolveLyrics(track: AudioTrack): Pair<String, Long> = withContext(Dispatchers.IO) {
        val entry = lyricsDao.getLyrics(track.id)
        if (entry != null && entry.lyricsPath.isNotBlank()) {
            return@withContext Pair(entry.lyricsPath, entry.customOffsetMs)
        }

        // Check if track has embedded lyrics
        if (track.embeddedLyrics.isNotBlank()) {
            return@withContext Pair(track.embeddedLyrics, 0L)
        }

        // Check sibling file in track's parent directory
        val siblingLyrics = findSiblingLyrics(track.path)
        if (siblingLyrics != null) {
            val content = try {
                siblingLyrics.readText(Charsets.UTF_8)
            } catch (_: Exception) {
                ""
            }
            if (content.isNotBlank()) {
                lyricsDao.insertLyrics(
                    LyricsMetadataEntry(
                        trackId = track.id,
                        lyricsPath = content,
                        customOffsetMs = 0L,
                        isManual = false
                    )
                )
                return@withContext Pair(content, 0L)
            }
        }

        Pair("", 0L)
    }

    private fun findFolderLyrics(trackPath: String): File? {
        if (trackPath.isBlank()) return null
        try {
            val trackFile = File(trackPath)
            val parent = trackFile.parentFile ?: return null
            if (!parent.exists() || !parent.isDirectory) return null

            val baseName = trackFile.nameWithoutExtension
            val extensions = listOf(".lrc", ".txt", ".ttml")

            for (ext in extensions) {
                val candidate = File(parent, "$baseName$ext")
                if (candidate.exists() && candidate.isFile && candidate.length() > 0) {
                    return candidate
                }
            }
        } catch (e: Exception) {
            Log.d("LyricsManager", "Failed to check sibling lyrics: ${e.message}")
        }
        return null
    }

    private fun findSiblingLyrics(trackPath: String): File? = findFolderLyrics(trackPath)

    suspend fun saveCustomLyrics(trackId: Long, lyricsContent: String, offsetMs: Long) = withContext(Dispatchers.IO) {
        lyricsDao.insertLyrics(
            LyricsMetadataEntry(
                trackId = trackId,
                lyricsPath = lyricsContent,
                customOffsetMs = offsetMs,
                isManual = true,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun updateLyricsOffset(trackId: Long, offsetMs: Long) = withContext(Dispatchers.IO) {
        val existing = lyricsDao.getLyrics(trackId)
        val text = existing?.lyricsPath ?: ""
        lyricsDao.insertLyrics(
            LyricsMetadataEntry(
                trackId = trackId,
                lyricsPath = text,
                customOffsetMs = offsetMs,
                isManual = existing?.isManual ?: false,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun readLyricsFromUri(uri: Uri): String = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                    reader.readText()
                }
            } ?: ""
        } catch (e: Exception) {
            Log.e("LyricsManager", "Error reading lyrics from URI: $uri", e)
            ""
        }
    }

    fun parseLyrics(rawLyrics: String, offsetMs: Long): List<LyricsLine> {
        val lines = LrcParser.parse(rawLyrics)
        if (offsetMs == 0L) return lines
        return lines.map {
            it.copy(timestampMs = (it.timestampMs + offsetMs).coerceAtLeast(0L))
        }
    }
}
