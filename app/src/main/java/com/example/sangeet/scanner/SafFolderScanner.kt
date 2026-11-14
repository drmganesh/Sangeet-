package com.example.sangeet.scanner

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import com.example.sangeet.data.model.AudioTrack
import com.example.sangeet.data.model.FolderPermission
import com.example.sangeet.data.model.RuleCreationDate
import com.example.sangeet.data.model.RuleStatus
import com.example.sangeet.data.model.VideoTrack
import com.example.sangeet.data.rules.StoragePathHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.ArrayDeque

sealed class FolderInspectionResult {
    data class Success(
        val treeUri: String,
        val documentId: String,
        val displayName: String,
        val displayPath: String,
        val subfolderCount: Int,
        val subfolderNames: List<String>,
        val audioCount: Int,
        val videoCount: Int,
        val audioTracks: List<AudioTrack>,
        val videoTracks: List<VideoTrack>
    ) : FolderInspectionResult()

    data class PermissionRevoked(
        val treeUri: String,
        val message: String
    ) : FolderInspectionResult()

    data class FolderUnavailable(
        val treeUri: String,
        val message: String
    ) : FolderInspectionResult()
}

class SafFolderScanner(private val context: Context) {

    companion object {
        private val AUDIO_EXTENSIONS = setOf(
            "mp3", "flac", "wav", "aac", "m4a", "ogg", "opus", "wma", "aiff", "alac", "amr", "m4b"
        )
        private val VIDEO_EXTENSIONS = setOf(
            "mp4", "mkv", "webm", "avi", "mov", "3gp", "m4v", "ts", "flv"
        )

        private val DOC_PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )
    }

    /**
     * Persists read (and optional write) URI permission granted by ACTION_OPEN_DOCUMENT_TREE.
     */
    fun takeAndRecordPermission(treeUri: Uri): FolderPermission {
        var hasRead = false
        var hasWrite = false
        var isPersisted = false

        try {
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(treeUri, takeFlags)
            hasRead = true
            hasWrite = true
            isPersisted = true
        } catch (_: SecurityException) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                hasRead = true
                isPersisted = true
            } catch (e: Exception) {
                Log.w("SafFolderScanner", "Persistable permission not supported for $treeUri", e)
            }
        } catch (e: Exception) {
            Log.w("SafFolderScanner", "Failed to take permission for $treeUri", e)
        }

        val docId = try {
            DocumentsContract.getTreeDocumentId(treeUri)
        } catch (_: Exception) {
            StoragePathHelper.extractDocIdFromUriString(treeUri.toString())
        }

        val rawName = queryRootDisplayName(treeUri, docId)
        val info = StoragePathHelper.buildDisplayInfo(docId, rawName, treeUri.toString())

        return FolderPermission(
            treeUri = treeUri.toString(),
            documentId = docId,
            displayName = info.displayName,
            hasReadPermission = hasRead || checkPersistedReadAccess(treeUri),
            hasWritePermission = hasWrite,
            isPersisted = isPersisted,
            status = RuleStatus.ACTIVE,
            grantedAt = RuleCreationDate(System.currentTimeMillis()),
            lastVerifiedAt = System.currentTimeMillis(),
            errorMessage = ""
        )
    }

    fun checkPersistedReadAccess(treeUri: Uri): Boolean {
        return try {
            context.contentResolver.persistedUriPermissions.any {
                it.uri == treeUri && it.isReadPermission
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun queryRootDisplayName(treeUri: Uri, rootDocId: String): String {
        return try {
            val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootDocId)
            context.contentResolver.query(
                docUri,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx) ?: "" else ""
                } else ""
            } ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    /**
     * Inspects a SAF folder tree URI recursively.
     * Distinguishes carefully between permission revocation, missing/moved folders, and genuinely empty folders.
     */
    suspend fun inspectFolder(
        treeUriString: String,
        extractDeepMetadata: Boolean = true,
        isWhitelistSource: Boolean = false
    ): FolderInspectionResult = withContext(Dispatchers.IO) {
        val treeUri = try {
            Uri.parse(treeUriString)
        } catch (e: Exception) {
            return@withContext FolderInspectionResult.FolderUnavailable(
                treeUriString,
                "Invalid folder URI: ${e.message}"
            )
        }

        val rootDocId = try {
            DocumentsContract.getTreeDocumentId(treeUri)
        } catch (e: Exception) {
            return@withContext FolderInspectionResult.FolderUnavailable(
                treeUriString,
                "Unable to resolve document ID for folder."
            )
        }

        // Verify root document accessibility first
        val rootDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootDocId)
        var rootName = ""
        var rootExists = false
        try {
            context.contentResolver.query(
                rootDocUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE
                ),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    rootExists = true
                    val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    if (nameIdx >= 0) rootName = cursor.getString(nameIdx) ?: ""
                }
            }
        } catch (se: SecurityException) {
            return@withContext FolderInspectionResult.PermissionRevoked(
                treeUriString,
                "Folder access permission was revoked or expired. Tap Grant Access to restore."
            )
        } catch (e: Exception) {
            return@withContext FolderInspectionResult.FolderUnavailable(
                treeUriString,
                "Folder could not be accessed (${e.message ?: "moved or deleted"})."
            )
        }

        if (!rootExists) {
            // Confirm if permission itself is missing
            if (!checkPersistedReadAccess(treeUri)) {
                return@withContext FolderInspectionResult.PermissionRevoked(
                    treeUriString,
                    "Folder permission is not granted."
                )
            }
            return@withContext FolderInspectionResult.FolderUnavailable(
                treeUriString,
                "Selected folder was moved, renamed, or deleted."
            )
        }

        val displayInfo = StoragePathHelper.buildDisplayInfo(rootDocId, rootName, treeUriString)
        val audioTracks = mutableListOf<AudioTrack>()
        val videoTracks = mutableListOf<VideoTrack>()
        val subfolderNames = mutableListOf<String>()
        var subfolderCount = 0

        // BFS traversal using DocumentsContract children URIs
        val queue = ArrayDeque<Pair<String, String>>() // Pair<docId, folderName>
        val visited = HashSet<String>()
        queue.add(rootDocId to displayInfo.displayName)
        visited.add(rootDocId)

        while (queue.isNotEmpty()) {
            val (currentDirDocId, currentFolderName) = queue.removeFirst()
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, currentDirDocId)

            try {
                context.contentResolver.query(
                    childrenUri,
                    DOC_PROJECTION,
                    null,
                    null,
                    null
                )?.use { cursor ->
                    val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val mimeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                    val sizeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                    val modIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

                    while (cursor.moveToNext()) {
                        val childDocId = if (idIdx >= 0) cursor.getString(idIdx) ?: continue else continue
                        val childName = if (nameIdx >= 0) cursor.getString(nameIdx) ?: "Unknown" else "Unknown"
                        val childMime = if (mimeIdx >= 0) cursor.getString(mimeIdx) ?: "" else ""
                        val childSize = if (sizeIdx >= 0) cursor.getLong(sizeIdx) else 0L
                        val childMod = if (modIdx >= 0) cursor.getLong(modIdx) else System.currentTimeMillis()

                        if (childMime == DocumentsContract.Document.MIME_TYPE_DIR) {
                            if (visited.add(childDocId)) {
                                subfolderCount++
                                if (subfolderNames.size < 30) {
                                    subfolderNames.add(childName)
                                }
                                queue.add(childDocId to childName)
                            }
                        } else {
                            val ext = childName.substringAfterLast('.', "").lowercase()
                            val isAudio = childMime.startsWith("audio/", ignoreCase = true) || ext in AUDIO_EXTENSIONS
                            val isVideo = childMime.startsWith("video/", ignoreCase = true) || ext in VIDEO_EXTENSIONS

                            if (isAudio) {
                                val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childDocId)
                                val track = buildSafAudioTrack(
                                    docUri = docUri,
                                    treeUriString = treeUriString,
                                    docId = childDocId,
                                    parentDocId = currentDirDocId,
                                    fileName = childName,
                                    folderName = currentFolderName,
                                    mimeType = childMime.ifBlank { "audio/$ext" },
                                    size = childSize,
                                    modified = childMod,
                                    extractDeepMetadata = extractDeepMetadata,
                                    isWhitelistOnly = isWhitelistSource
                                )
                                audioTracks.add(track)
                            } else if (isVideo) {
                                val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childDocId)
                                val video = buildSafVideoTrack(
                                    docUri = docUri,
                                    treeUriString = treeUriString,
                                    docId = childDocId,
                                    parentDocId = currentDirDocId,
                                    fileName = childName,
                                    mimeType = childMime.ifBlank { "video/$ext" },
                                    size = childSize,
                                    modified = childMod,
                                    extractDeepMetadata = extractDeepMetadata,
                                    isWhitelistOnly = isWhitelistSource
                                )
                                videoTracks.add(video)
                            }
                        }
                    }
                }
            } catch (se: SecurityException) {
                if (currentDirDocId == rootDocId) {
                    return@withContext FolderInspectionResult.PermissionRevoked(
                        treeUriString,
                        "Access permission was revoked for ${displayInfo.displayName}."
                    )
                }
            } catch (e: Exception) {
                Log.w("SafFolderScanner", "Skipped inaccessible subfolder $currentDirDocId", e)
            }
        }

        FolderInspectionResult.Success(
            treeUri = treeUriString,
            documentId = rootDocId,
            displayName = displayInfo.displayName,
            displayPath = displayInfo.displayPath,
            subfolderCount = subfolderCount,
            subfolderNames = subfolderNames,
            audioCount = audioTracks.size,
            videoCount = videoTracks.size,
            audioTracks = audioTracks,
            videoTracks = videoTracks
        )
    }

    private fun buildSafAudioTrack(
        docUri: Uri,
        treeUriString: String,
        docId: String,
        parentDocId: String,
        fileName: String,
        folderName: String,
        mimeType: String,
        size: Long,
        modified: Long,
        extractDeepMetadata: Boolean,
        isWhitelistOnly: Boolean
    ): AudioTrack {
        var title = fileName.substringBeforeLast('.').ifBlank { fileName }
        var artist = "Unknown Artist"
        var album = folderName.ifBlank { "Unknown Album" }
        var genre = "Music"
        var duration = 0L
        var year = 0

        if (extractDeepMetadata) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, docUri)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                    ?.takeIf { it.isNotBlank() }?.let { title = it }
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                    ?.takeIf { it.isNotBlank() }?.let { artist = it }
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                    ?.takeIf { it.isNotBlank() }?.let { album = it }
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)
                    ?.takeIf { it.isNotBlank() }?.let { genre = it }
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull()?.let { duration = it }
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)
                    ?.toIntOrNull()?.let { year = it }
            } catch (_: Exception) {
            } finally {
                try {
                    retriever.release()
                } catch (_: Exception) {}
            }
        }

        // Stable deterministic ID in negative/high SAF namespace so it never collides with MediaStore _ID
        val stableId = generateStableSafId(docId, treeUriString, isVideo = false)

        return AudioTrack(
            id = stableId,
            title = title,
            artist = artist,
            album = album,
            genre = genre,
            durationMs = duration,
            path = docUri.toString(),
            contentUri = docUri.toString(),
            dateAdded = if (modified > 0) modified else System.currentTimeMillis(),
            year = year,
            mimeType = mimeType,
            fileSize = size,
            sourceTreeUri = treeUriString,
            documentId = docId,
            folderPath = parentDocId,
            isWhitelistOnly = isWhitelistOnly
        )
    }

    private fun buildSafVideoTrack(
        docUri: Uri,
        treeUriString: String,
        docId: String,
        parentDocId: String,
        fileName: String,
        mimeType: String,
        size: Long,
        modified: Long,
        extractDeepMetadata: Boolean,
        isWhitelistOnly: Boolean
    ): VideoTrack {
        var title = fileName.substringBeforeLast('.').ifBlank { fileName }
        var duration = 0L
        var width = ""
        var height = ""

        if (extractDeepMetadata) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, docUri)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                    ?.takeIf { it.isNotBlank() }?.let { title = it }
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull()?.let { duration = it }
                width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH) ?: ""
                height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT) ?: ""
            } catch (_: Exception) {
            } finally {
                try {
                    retriever.release()
                } catch (_: Exception) {}
            }
        }

        val resolution = if (width.isNotBlank() && height.isNotBlank()) "${width}x$height" else "HD"
        val stableId = generateStableSafId(docId, treeUriString, isVideo = true)

        return VideoTrack(
            id = stableId,
            title = title,
            durationMs = duration,
            path = docUri.toString(),
            contentUri = docUri.toString(),
            dateAdded = if (modified > 0) modified else System.currentTimeMillis(),
            resolution = resolution,
            mimeType = mimeType,
            fileSize = size,
            sourceTreeUri = treeUriString,
            documentId = docId,
            folderPath = parentDocId,
            isWhitelistOnly = isWhitelistOnly
        )
    }

    private fun generateStableSafId(docId: String, treeUri: String, isVideo: Boolean): Long {
        val raw = "${if (isVideo) "V" else "A"}|$treeUri|$docId"
        val hash = raw.fold(1125899906842597L) { acc, c -> acc * 31L + c.code }
        // Keep in negative range so it never overlaps positive MediaStore IDs
        return -(hash.and(0x3FFFFFFFFFFFFFFFL) + 100000L)
    }
}
