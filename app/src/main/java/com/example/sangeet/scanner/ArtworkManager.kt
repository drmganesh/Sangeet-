package com.example.sangeet.scanner

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import com.example.sangeet.data.local.AppDatabase
import com.example.sangeet.data.model.AlbumArtworkEntry
import com.example.sangeet.data.model.AudioTrack
import com.example.sangeet.ui.components.ArtworkPathCache
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

class ArtworkManager(private val context: Context) {

    private val appContext = context.applicationContext
    private val db = AppDatabase.getInstance(appContext)
    private val artworkDao = db.artworkDao()
    private val cacheDir = File(appContext.cacheDir, "artwork_cache").apply { mkdirs() }

    private val folderArtworkNames = listOf(
        "cover.jpg", "cover.jpeg", "cover.png",
        "folder.jpg", "folder.jpeg", "folder.png",
        "album.jpg", "album.jpeg", "album.png",
        "front.jpg", "front.jpeg", "front.png"
    )

    fun getAlbumKey(artist: String, album: String): String {
        val a = artist.trim().lowercase()
        val al = album.trim().lowercase()
        return "${a}_$al"
    }

    private fun getCacheKeyForTrack(track: AudioTrack): String {
        val albumTrimmed = track.album.trim()
        val isGenericAlbum = albumTrimmed.isBlank() ||
            albumTrimmed.equals("Unknown Album", ignoreCase = true) ||
            albumTrimmed.equals("<unknown>", ignoreCase = true)
        return if (isGenericAlbum) {
            "track_${track.id}"
        } else {
            getAlbumKey(track.artist, track.album)
        }
    }

    /**
     * Resolves the artwork path or URI for an AudioTrack using priority:
     * 1. Track's explicit artworkUri (if non-empty)
     * 2. In-memory positive / negative cache + concurrent in-flight deduplication
     * 3. Custom or cached database record for the album
     * 4. Extracted embedded ID3/FLAC/M4A/OGG picture (only when EmbeddedArtworkProbe confirms presence)
     * 5. Sibling folder cover image (cover.jpg, folder.jpg, etc.)
     */
    suspend fun resolveArtwork(track: AudioTrack): String? = withContext(Dispatchers.IO) {
        // 1. Direct track artworkUri
        if (track.artworkUri.isNotBlank()) {
            ArtworkPathCache.put(track.id, track.artworkUri)
            return@withContext track.artworkUri
        }

        // 2. Fast in-memory positive & negative caches
        ArtworkPathCache.get(track.id)?.let { cachedPath ->
            if (cachedPath.startsWith("content://") || File(cachedPath).exists()) {
                return@withContext cachedPath
            }
        }
        if (negativeTrackCache.contains(track.id) || ArtworkPathCache.isKnownMissing(track.id)) {
            return@withContext null
        }

        // 3. Deduplicate concurrent lookups for the same track (e.g., Featured Spotlight + Quick Picks)
        val newDeferred = CompletableDeferred<String?>()
        val existingDeferred = inFlightByTrackId.putIfAbsent(track.id, newDeferred)
        if (existingDeferred != null) {
            return@withContext existingDeferred.await()
        }

        try {
            val resolved = resolveArtworkInternal(track)
            if (!resolved.isNullOrBlank()) {
                ArtworkPathCache.put(track.id, resolved)
            } else {
                negativeTrackCache.add(track.id)
                ArtworkPathCache.markNoArtwork(track.id)
            }
            newDeferred.complete(resolved)
            resolved
        } catch (t: Throwable) {
            newDeferred.complete(null)
            null
        } finally {
            inFlightByTrackId.remove(track.id, newDeferred)
        }
    }

    private suspend fun resolveArtworkInternal(track: AudioTrack): String? {
        val albumKey = getAlbumKey(track.artist, track.album)
        val cacheKey = getCacheKeyForTrack(track)

        // Check custom/cached DB entry (honor explicit album-level custom entries first)
        val customOrAlbumEntry = artworkDao.getArtwork(albumKey)
            ?: if (cacheKey != albumKey) artworkDao.getArtwork(cacheKey) else null
        if (customOrAlbumEntry != null) {
            if (customOrAlbumEntry.artworkPath.startsWith("content://") ||
                File(customOrAlbumEntry.artworkPath).exists()
            ) {
                return customOrAlbumEntry.artworkPath
            }
        }

        // Cached extracted file
        val hash = md5(cacheKey)
        val cachedFile = File(cacheDir, "art_$hash.jpg")
        if (cachedFile.exists() && cachedFile.length() > 0) {
            val path = cachedFile.absolutePath
            artworkDao.insertArtwork(AlbumArtworkEntry(cacheKey, path, isCustom = false))
            return path
        }

        // Probe container headers before invoking MediaMetadataRetriever.getEmbeddedPicture()
        // so tracks without embedded art never emit native MediaMetadataRetrieverJNI error logs.
        val embeddedFile = if (
            EmbeddedArtworkProbe.mightContainEmbeddedArtwork(
                context = appContext,
                contentUri = track.contentUri,
                filePath = track.path,
                mimeType = track.mimeType
            )
        ) {
            extractEmbeddedArtwork(track, cachedFile)
        } else {
            null
        }

        if (embeddedFile != null) {
            val path = embeddedFile.absolutePath
            artworkDao.insertArtwork(AlbumArtworkEntry(cacheKey, path, isCustom = false))
            return path
        }

        // Try sibling folder artwork
        val folderArt = findFolderArtwork(track.path)
        if (folderArt != null) {
            val path = folderArt.absolutePath
            artworkDao.insertArtwork(AlbumArtworkEntry(cacheKey, path, isCustom = false))
            return path
        }

        return null
    }

    private fun extractEmbeddedArtwork(track: AudioTrack, targetFile: File): File? {
        val retriever = MediaMetadataRetriever()
        try {
            if (track.contentUri.isNotBlank() && track.contentUri.startsWith("content://")) {
                retriever.setDataSource(appContext, Uri.parse(track.contentUri))
            } else if (track.path.isNotBlank() && File(track.path).exists()) {
                retriever.setDataSource(track.path)
            } else {
                return null
            }

            val picture = retriever.embeddedPicture
            if (picture != null && picture.isNotEmpty()) {
                FileOutputStream(targetFile).use { fos ->
                    fos.write(picture)
                    fos.flush()
                }
                return targetFile
            }
        } catch (e: Exception) {
            Log.d("ArtworkManager", "No embedded artwork for ${track.title}: ${e.message}")
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
        }
        return null
    }

    private fun findFolderArtwork(trackPath: String): File? {
        if (trackPath.isBlank()) return null
        try {
            val trackFile = File(trackPath)
            val parent = trackFile.parentFile ?: return null
            if (!parent.exists() || !parent.isDirectory) return null

            for (name in folderArtworkNames) {
                val candidate = File(parent, name)
                if (candidate.exists() && candidate.isFile && candidate.length() > 0) {
                    return candidate
                }
            }

            // Also check case-insensitively
            val files = parent.listFiles() ?: return null
            for (f in files) {
                if (f.isFile && f.name.lowercase() in folderArtworkNames && f.length() > 0) {
                    return f
                }
            }
        } catch (e: Exception) {
            Log.d("ArtworkManager", "Folder artwork search failed: ${e.message}")
        }
        return null
    }

    suspend fun setCustomArtwork(artist: String, album: String, imageUriOrPath: String) = withContext(Dispatchers.IO) {
        val albumKey = getAlbumKey(artist, album)
        artworkDao.insertArtwork(
            AlbumArtworkEntry(
                albumKey = albumKey,
                artworkPath = imageUriOrPath,
                isCustom = true,
                updatedAt = System.currentTimeMillis()
            )
        )
        negativeTrackCache.clear()
        ArtworkPathCache.clear()
    }

    suspend fun removeCustomArtwork(artist: String, album: String) = withContext(Dispatchers.IO) {
        val albumKey = getAlbumKey(artist, album)
        artworkDao.deleteArtwork(albumKey)
        negativeTrackCache.clear()
        ArtworkPathCache.clear()
    }

    suspend fun clearArtworkCache() = withContext(Dispatchers.IO) {
        cacheDir.listFiles()?.forEach { it.delete() }
        artworkDao.clearAll()
        negativeTrackCache.clear()
        ArtworkPathCache.clear()
    }

    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digested = md.digest(input.toByteArray())
        return digested.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private val inFlightByTrackId = ConcurrentHashMap<Long, CompletableDeferred<String?>>()
        private val negativeTrackCache = ConcurrentHashMap.newKeySet<Long>()
    }
}
