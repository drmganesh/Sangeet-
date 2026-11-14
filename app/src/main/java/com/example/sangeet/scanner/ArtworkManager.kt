package com.example.sangeet.scanner

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import com.example.sangeet.data.local.AppDatabase
import com.example.sangeet.data.model.AlbumArtworkEntry
import com.example.sangeet.data.model.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

class ArtworkManager(private val context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val artworkDao = db.artworkDao()
    private val cacheDir = File(context.cacheDir, "artwork_cache").apply { mkdirs() }

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

    /**
     * Resolves the artwork path or URI for an AudioTrack using priority:
     * 1. Track's explicit artworkUri (if non-empty)
     * 2. Custom or cached database record for the album
     * 3. Extracted embedded ID3/FLAC/M4A/OGG picture
     * 4. Sibling folder cover image (cover.jpg, folder.jpg, etc.)
     */
    suspend fun resolveArtwork(track: AudioTrack): String? = withContext(Dispatchers.IO) {
        // 1. Direct track artworkUri
        if (track.artworkUri.isNotBlank()) {
            return@withContext track.artworkUri
        }

        val albumKey = getAlbumKey(track.artist, track.album)

        // 2. Database entry (custom user-chosen or cached)
        val entry = artworkDao.getArtwork(albumKey)
        if (entry != null && File(entry.artworkPath).exists()) {
            return@withContext entry.artworkPath
        } else if (entry != null && entry.artworkPath.startsWith("content://")) {
            return@withContext entry.artworkPath
        }

        // 3. Cached extracted file
        val hash = md5(albumKey)
        val cachedFile = File(cacheDir, "art_$hash.jpg")
        if (cachedFile.exists() && cachedFile.length() > 0) {
            val path = cachedFile.absolutePath
            artworkDao.insertArtwork(AlbumArtworkEntry(albumKey, path, isCustom = false))
            return@withContext path
        }

        // 4. Try extracting embedded picture
        val embeddedFile = extractEmbeddedArtwork(track, cachedFile)
        if (embeddedFile != null) {
            val path = embeddedFile.absolutePath
            artworkDao.insertArtwork(AlbumArtworkEntry(albumKey, path, isCustom = false))
            return@withContext path
        }

        // 5. Try sibling folder artwork
        val folderArt = findFolderArtwork(track.path)
        if (folderArt != null) {
            val path = folderArt.absolutePath
            artworkDao.insertArtwork(AlbumArtworkEntry(albumKey, path, isCustom = false))
            return@withContext path
        }

        null
    }

    private fun extractEmbeddedArtwork(track: AudioTrack, targetFile: File): File? {
        val retriever = MediaMetadataRetriever()
        try {
            if (track.contentUri.isNotBlank() && track.contentUri.startsWith("content://")) {
                retriever.setDataSource(context, Uri.parse(track.contentUri))
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
    }

    suspend fun removeCustomArtwork(artist: String, album: String) = withContext(Dispatchers.IO) {
        val albumKey = getAlbumKey(artist, album)
        artworkDao.deleteArtwork(albumKey)
    }

    suspend fun clearArtworkCache() = withContext(Dispatchers.IO) {
        cacheDir.listFiles()?.forEach { it.delete() }
        artworkDao.clearAll()
    }

    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digested = md.digest(input.toByteArray())
        return digested.joinToString("") { "%02x".format(it) }
    }
}
