package com.example.sangeet.data.rules

import java.io.File
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

object StoragePathHelper {

    data class FolderDisplayInfo(
        val displayName: String,
        val displayPath: String,
        val canonicalKey: String
    )

    /**
     * Converts a SAF documentId or filesystem path into a normalized canonical comparison key
     * without assuming that every SAF URI maps to a direct java.io.File path.
     *
     * Examples:
     * - "primary:Music/Rock/2026" -> "primary:music/rock/2026"
     * - "/storage/emulated/0/Music/Rock/2026" -> "primary:music/rock/2026"
     * - "/sdcard/Music/Rock" -> "primary:music/rock"
     * - "1A2B-3C4D:Movies/Action" -> "1a2b-3c4d:movies/action"
     * - "/storage/1A2B-3C4D/Movies/Action" -> "1a2b-3c4d:movies/action"
     */
    fun canonicalizeFolderKey(rawPathOrDocId: String, treeUri: String = ""): String {
        val trimmed = rawPathOrDocId.trim()
        if (trimmed.isBlank() && treeUri.isBlank()) return ""

        // If treeUri is given and rawPathOrDocId is blank, try extracting treeDocumentId string
        val candidate = if (trimmed.isBlank() && treeUri.isNotBlank()) {
            extractDocIdFromUriString(treeUri)
        } else {
            trimmed
        }

        val normalizedSlashes = candidate.replace('\\', '/').trimEnd('/')

        // 1. Handle raw:/ prefix sometimes emitted by ExternalStorageProvider
        val withoutRaw = if (normalizedSlashes.startsWith("raw:", ignoreCase = true)) {
            normalizedSlashes.substring(4)
        } else {
            normalizedSlashes
        }

        // 2. Map standard internal storage filesystem prefixes to "primary:"
        val internalPrefixes = listOf(
            "/storage/emulated/0",
            "/storage/self/primary",
            "/sdcard"
        )
        for (prefix in internalPrefixes) {
            if (withoutRaw.equals(prefix, ignoreCase = true)) {
                return "primary:"
            }
            if (withoutRaw.startsWith("$prefix/", ignoreCase = true)) {
                val rel = withoutRaw.substring(prefix.length + 1).trim('/').lowercase()
                return "primary:$rel"
            }
        }

        // 3. Map /storage/<VOLUME_UUID>/<rel> to "<volume>:<rel>"
        if (withoutRaw.startsWith("/storage/", ignoreCase = true)) {
            val rest = withoutRaw.substring("/storage/".length)
            val slashIdx = rest.indexOf('/')
            return if (slashIdx == -1) {
                "${rest.lowercase()}:"
            } else {
                val vol = rest.substring(0, slashIdx).lowercase()
                val rel = rest.substring(slashIdx + 1).trim('/').lowercase()
                "$vol:$rel"
            }
        }

        // 4. SAF documentId format "<volume>:<relPath>"
        val colonIdx = withoutRaw.indexOf(':')
        if (colonIdx > 0 && !withoutRaw.startsWith("content://", ignoreCase = true) && !withoutRaw.startsWith("file://", ignoreCase = true)) {
            val vol = withoutRaw.substring(0, colonIdx).lowercase()
            val rel = withoutRaw.substring(colonIdx + 1).trim('/').lowercase()
            return "$vol:$rel"
        }

        // 5. Fallback for opaque provider URIs or generic paths
        return withoutRaw.lowercase()
    }

    /**
     * Extracts the parent folder's canonical key for a media file (from its documentId, folderPath, or file path).
     */
    fun canonicalizeMediaParentFolder(
        filePath: String,
        folderPath: String = "",
        documentId: String = "",
        sourceTreeUri: String = ""
    ): String {
        if (folderPath.isNotBlank()) {
            return canonicalizeFolderKey(folderPath, sourceTreeUri)
        }
        if (documentId.isNotBlank()) {
            val slashIdx = documentId.lastIndexOf('/')
            val colonIdx = documentId.lastIndexOf(':')
            val parentDocId = when {
                slashIdx > colonIdx -> documentId.substring(0, slashIdx)
                colonIdx >= 0 -> documentId.substring(0, colonIdx + 1)
                else -> ""
            }
            if (parentDocId.isNotBlank()) {
                return canonicalizeFolderKey(parentDocId, sourceTreeUri)
            }
        }
        if (filePath.isNotBlank() && !filePath.startsWith("content://", ignoreCase = true)) {
            val clean = if (filePath.startsWith("file://", ignoreCase = true)) {
                try {
                    URI.create(filePath).path ?: filePath
                } catch (_: Exception) {
                    filePath.removePrefix("file://")
                }
            } else {
                filePath
            }
            val parent = File(clean).parent
            if (!parent.isNullOrBlank()) {
                return canonicalizeFolderKey(parent, sourceTreeUri)
            }
        }
        return if (sourceTreeUri.isNotBlank()) canonicalizeFolderKey("", sourceTreeUri) else ""
    }

    /**
     * Returns true if [candidateFolderKey] equals [ancestorFolderKey] or is a recursive subfolder inside it.
     */
    fun isDescendantOrEqual(candidateFolderKey: String, ancestorFolderKey: String): Boolean {
        if (candidateFolderKey.isBlank() || ancestorFolderKey.isBlank()) return false
        if (candidateFolderKey == ancestorFolderKey) return true

        // Root volume folder like "primary:" or "1234-5678:"
        if (ancestorFolderKey.endsWith(":")) {
            return candidateFolderKey.startsWith(ancestorFolderKey)
        }

        return candidateFolderKey.startsWith("$ancestorFolderKey/")
    }

    /**
     * Checks if a media item matches a folder rule by SAF tree URI, SAF documentId hierarchy, or canonical storage path.
     */
    fun isMediaInsideFolder(
        mediaPath: String,
        mediaFolderPath: String,
        mediaDocumentId: String,
        mediaSourceTreeUri: String,
        ruleIdentifier: String,
        ruleDocumentId: String,
        ruleTreeUri: String,
        includeSubfolders: Boolean = true
    ): Boolean {
        // 1. Direct SAF document ID hierarchy match (handles opaque or provider-scoped docIds safely)
        if (mediaDocumentId.isNotBlank() && ruleDocumentId.isNotBlank()) {
            val normMediaDoc = mediaDocumentId.trim().trimEnd('/').lowercase()
            val normRuleDoc = ruleDocumentId.trim().trimEnd('/').lowercase()
            val mediaParentDoc = extractParentDocId(normMediaDoc)
            if (mediaParentDoc == normRuleDoc) return true
            if (includeSubfolders) {
                if (normRuleDoc.endsWith(":") && mediaParentDoc.startsWith(normRuleDoc)) return true
                if (mediaParentDoc.startsWith("$normRuleDoc/")) return true
            }
        }

        // 2. Direct sourceTreeUri match (for tracks imported directly from that SAF tree)
        if (ruleTreeUri.isNotBlank() && mediaSourceTreeUri.isNotBlank() && mediaSourceTreeUri == ruleTreeUri) {
            return true
        }

        // 3. Canonical key comparison (bridges MediaStore filesystem paths and SAF tree documentIds)
        val ruleKey = canonicalizeFolderKey(ruleDocumentId.ifBlank { ruleIdentifier }, ruleTreeUri)
        val mediaParentKey = canonicalizeMediaParentFolder(
            filePath = mediaPath,
            folderPath = mediaFolderPath,
            documentId = mediaDocumentId,
            sourceTreeUri = mediaSourceTreeUri
        )

        if (ruleKey.isNotBlank() && mediaParentKey.isNotBlank()) {
            return if (includeSubfolders) {
                isDescendantOrEqual(mediaParentKey, ruleKey)
            } else {
                mediaParentKey == ruleKey
            }
        }

        return false
    }

    private fun extractParentDocId(docId: String): String {
        val slashIdx = docId.lastIndexOf('/')
        val colonIdx = docId.lastIndexOf(':')
        return when {
            slashIdx > colonIdx -> docId.substring(0, slashIdx)
            colonIdx >= 0 -> docId.substring(0, colonIdx + 1)
            else -> docId
        }
    }

    /**
     * Pure string parsing of SAF tree URI path segment without requiring Android runtime in unit tests.
     */
    fun extractDocIdFromUriString(treeUriString: String): String {
        if (treeUriString.isBlank()) return ""
        return try {
            val safeEncoded = treeUriString.replace("+", "%2B")
            val decoded = URLDecoder.decode(safeEncoded, StandardCharsets.UTF_8.name())
            val treeMarker = "/tree/"
            val treeIdx = decoded.indexOf(treeMarker)
            if (treeIdx >= 0) {
                val afterTree = decoded.substring(treeIdx + treeMarker.length)
                val docIdx = afterTree.indexOf("/document/")
                if (docIdx >= 0) {
                    afterTree.substring(0, docIdx)
                } else {
                    afterTree
                }
            } else {
                decoded
            }
        } catch (_: Exception) {
            treeUriString
        }
    }

    /**
     * Builds human-readable displayName and displayPath from a SAF documentId or filesystem path.
     */
    fun buildDisplayInfo(documentId: String, rawName: String = "", treeUri: String = ""): FolderDisplayInfo {
        val docId = documentId.ifBlank { extractDocIdFromUriString(treeUri) }
        val canonical = canonicalizeFolderKey(docId, treeUri)

        if (docId.contains(":")) {
            val vol = docId.substringBefore(":")
            val rel = docId.substringAfter(":").trim('/')
            val storageLabel = when {
                vol.equals("primary", ignoreCase = true) -> "Internal Storage"
                vol.equals("home", ignoreCase = true) -> "Documents"
                else -> "Storage ($vol)"
            }
            val displayPath = if (rel.isBlank()) storageLabel else "$storageLabel/$rel"
            val displayName = rawName.ifBlank {
                if (rel.isBlank()) storageLabel else rel.substringAfterLast('/')
            }
            return FolderDisplayInfo(displayName, displayPath, canonical)
        }

        val cleanPath = docId.trimEnd('/')
        val displayName = rawName.ifBlank {
            cleanPath.substringAfterLast('/').ifBlank { "Selected Folder" }
        }
        val displayPath = if (cleanPath.startsWith("/storage/emulated/0", ignoreCase = true)) {
            "Internal Storage" + cleanPath.removePrefix("/storage/emulated/0")
        } else {
            cleanPath.ifBlank { displayName }
        }
        return FolderDisplayInfo(displayName, displayPath, canonical)
    }

    fun getParentDisplayPath(filePath: String, folderPath: String = ""): String {
        if (folderPath.isNotBlank()) {
            return buildDisplayInfo(folderPath).displayPath
        }
        if (filePath.isBlank()) return "Unknown Location"
        if (filePath.startsWith("content://", ignoreCase = true)) return "Storage Access Framework"
        val parent = File(filePath).parent ?: return filePath
        return buildDisplayInfo(parent).displayPath
    }
}
