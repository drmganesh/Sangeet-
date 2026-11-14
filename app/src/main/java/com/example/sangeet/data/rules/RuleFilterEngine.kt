package com.example.sangeet.data.rules

import com.example.sangeet.data.model.*

data class RuleConflict(
    val existingRuleId: Long,
    val existingListKind: RuleListKind,
    val existingRuleType: RuleType,
    val existingTitle: String,
    val existingPath: String,
    val isFolderRule: Boolean,
    val reason: String
)

object RuleFilterEngine {

    fun filterAudioTracks(
        tracks: List<AudioTrack>,
        blacklistEntries: List<BlacklistEntry>,
        whitelistEntries: List<WhitelistEntry>,
        folderRules: List<FolderRule>
    ): List<AudioTrack> {
        val activeBlacklistFolders = folderRules.filter {
            it.listKind == RuleListKind.BLACKLIST &&
                it.status != RuleStatus.DISABLED &&
                (it.ruleType == RuleType.FOLDER || it.ruleType == RuleType.MUSIC_FOLDER)
        }
        val activeWhitelistFolders = folderRules.filter {
            it.listKind == RuleListKind.WHITELIST &&
                it.status != RuleStatus.DISABLED &&
                (it.ruleType == RuleType.FOLDER || it.ruleType == RuleType.MUSIC_FOLDER)
        }

        val audioWhitelistEntries = whitelistEntries.filter {
            it.status != RuleStatus.DISABLED &&
                it.type != "VIDEO" && it.type != "VIDEO_FOLDER"
        }
        val hasActiveAudioWhitelist = audioWhitelistEntries.isNotEmpty() || activeWhitelistFolders.isNotEmpty()

        return tracks.filter { track ->
            // 1. PRIORITY: Explicit Blacklist ALWAYS excludes (items, artists, albums, folders + descendants)
            if (isAudioBlacklisted(track, blacklistEntries, activeBlacklistFolders)) {
                return@filter false
            }

            // 2. If track was excluded by general MediaStore scan (isWhitelistOnly = true),
            // it requires an active whitelist match to stay visible.
            if (track.isWhitelistOnly) {
                if (!hasActiveAudioWhitelist) return@filter false
                return@filter isAudioWhitelisted(track, audioWhitelistEntries, activeWhitelistFolders)
            }

            // 3. When explicit whitelist rules exist for audio, only whitelisted items/folders appear
            if (hasActiveAudioWhitelist) {
                return@filter isAudioWhitelisted(track, audioWhitelistEntries, activeWhitelistFolders)
            }

            true
        }
    }

    fun filterVideoTracks(
        videos: List<VideoTrack>,
        blacklistEntries: List<BlacklistEntry>,
        whitelistEntries: List<WhitelistEntry>,
        folderRules: List<FolderRule>
    ): List<VideoTrack> {
        val activeBlacklistFolders = folderRules.filter {
            it.listKind == RuleListKind.BLACKLIST &&
                it.status != RuleStatus.DISABLED &&
                (it.ruleType == RuleType.FOLDER || it.ruleType == RuleType.VIDEO_FOLDER)
        }
        val activeWhitelistFolders = folderRules.filter {
            it.listKind == RuleListKind.WHITELIST &&
                it.status != RuleStatus.DISABLED &&
                (it.ruleType == RuleType.FOLDER || it.ruleType == RuleType.VIDEO_FOLDER)
        }

        val videoWhitelistEntries = whitelistEntries.filter {
            it.status != RuleStatus.DISABLED &&
                (it.type == "VIDEO" || it.type == "VIDEO_FOLDER" || it.type == "FOLDER")
        }
        val hasActiveVideoWhitelist = videoWhitelistEntries.isNotEmpty() || activeWhitelistFolders.isNotEmpty()

        return videos.filter { video ->
            if (isVideoBlacklisted(video, blacklistEntries, activeBlacklistFolders)) {
                return@filter false
            }

            if (video.isWhitelistOnly) {
                if (!hasActiveVideoWhitelist) return@filter false
                return@filter isVideoWhitelisted(video, videoWhitelistEntries, activeWhitelistFolders)
            }

            if (hasActiveVideoWhitelist) {
                return@filter isVideoWhitelisted(video, videoWhitelistEntries, activeWhitelistFolders)
            }

            true
        }
    }

    fun isAudioBlacklisted(
        track: AudioTrack,
        blacklistEntries: List<BlacklistEntry>,
        activeBlacklistFolders: List<FolderRule>
    ): Boolean {
        val entryMatch = blacklistEntries.any { b ->
            if (b.status == RuleStatus.DISABLED) return@any false
            when (b.type.uppercase()) {
                "SONG" -> matchesTrackIdentity(track, b.identifier, b.documentId)
                "ARTIST" -> b.identifier.equals(track.artist, ignoreCase = true)
                "ALBUM" -> b.identifier.equals(track.album, ignoreCase = true)
                "FOLDER", "MUSIC_FOLDER" -> StoragePathHelper.isMediaInsideFolder(
                    mediaPath = track.path,
                    mediaFolderPath = track.folderPath,
                    mediaDocumentId = track.documentId,
                    mediaSourceTreeUri = track.sourceTreeUri,
                    ruleIdentifier = b.identifier,
                    ruleDocumentId = b.documentId,
                    ruleTreeUri = b.treeUri
                )
                else -> false
            }
        }
        if (entryMatch) return true

        return activeBlacklistFolders.any { rule ->
            StoragePathHelper.isMediaInsideFolder(
                mediaPath = track.path,
                mediaFolderPath = track.folderPath,
                mediaDocumentId = track.documentId,
                mediaSourceTreeUri = track.sourceTreeUri,
                ruleIdentifier = rule.displayPath,
                ruleDocumentId = rule.documentId,
                ruleTreeUri = rule.treeUri,
                includeSubfolders = rule.includeSubfolders
            )
        }
    }

    fun isAudioWhitelisted(
        track: AudioTrack,
        whitelistEntries: List<WhitelistEntry>,
        activeWhitelistFolders: List<FolderRule>
    ): Boolean {
        val entryMatch = whitelistEntries.any { w ->
            if (w.status == RuleStatus.DISABLED) return@any false
            when (w.type.uppercase()) {
                "SONG" -> matchesTrackIdentity(track, w.identifier, w.documentId)
                "ARTIST" -> w.identifier.equals(track.artist, ignoreCase = true)
                "ALBUM" -> w.identifier.equals(track.album, ignoreCase = true)
                "FOLDER", "MUSIC_FOLDER" -> StoragePathHelper.isMediaInsideFolder(
                    mediaPath = track.path,
                    mediaFolderPath = track.folderPath,
                    mediaDocumentId = track.documentId,
                    mediaSourceTreeUri = track.sourceTreeUri,
                    ruleIdentifier = w.identifier,
                    ruleDocumentId = w.documentId,
                    ruleTreeUri = w.treeUri
                )
                else -> false
            }
        }
        if (entryMatch) return true

        return activeWhitelistFolders.any { rule ->
            StoragePathHelper.isMediaInsideFolder(
                mediaPath = track.path,
                mediaFolderPath = track.folderPath,
                mediaDocumentId = track.documentId,
                mediaSourceTreeUri = track.sourceTreeUri,
                ruleIdentifier = rule.displayPath,
                ruleDocumentId = rule.documentId,
                ruleTreeUri = rule.treeUri,
                includeSubfolders = rule.includeSubfolders
            )
        }
    }

    fun isVideoBlacklisted(
        video: VideoTrack,
        blacklistEntries: List<BlacklistEntry>,
        activeBlacklistFolders: List<FolderRule>
    ): Boolean {
        val entryMatch = blacklistEntries.any { b ->
            if (b.status == RuleStatus.DISABLED) return@any false
            when (b.type.uppercase()) {
                "VIDEO", "SONG" -> matchesVideoIdentity(video, b.identifier, b.documentId)
                "FOLDER", "VIDEO_FOLDER" -> StoragePathHelper.isMediaInsideFolder(
                    mediaPath = video.path,
                    mediaFolderPath = video.folderPath,
                    mediaDocumentId = video.documentId,
                    mediaSourceTreeUri = video.sourceTreeUri,
                    ruleIdentifier = b.identifier,
                    ruleDocumentId = b.documentId,
                    ruleTreeUri = b.treeUri
                )
                else -> false
            }
        }
        if (entryMatch) return true

        return activeBlacklistFolders.any { rule ->
            StoragePathHelper.isMediaInsideFolder(
                mediaPath = video.path,
                mediaFolderPath = video.folderPath,
                mediaDocumentId = video.documentId,
                mediaSourceTreeUri = video.sourceTreeUri,
                ruleIdentifier = rule.displayPath,
                ruleDocumentId = rule.documentId,
                ruleTreeUri = rule.treeUri,
                includeSubfolders = rule.includeSubfolders
            )
        }
    }

    fun isVideoWhitelisted(
        video: VideoTrack,
        whitelistEntries: List<WhitelistEntry>,
        activeWhitelistFolders: List<FolderRule>
    ): Boolean {
        val entryMatch = whitelistEntries.any { w ->
            if (w.status == RuleStatus.DISABLED) return@any false
            when (w.type.uppercase()) {
                "VIDEO", "SONG" -> matchesVideoIdentity(video, w.identifier, w.documentId)
                "FOLDER", "VIDEO_FOLDER" -> StoragePathHelper.isMediaInsideFolder(
                    mediaPath = video.path,
                    mediaFolderPath = video.folderPath,
                    mediaDocumentId = video.documentId,
                    mediaSourceTreeUri = video.sourceTreeUri,
                    ruleIdentifier = w.identifier,
                    ruleDocumentId = w.documentId,
                    ruleTreeUri = w.treeUri
                )
                else -> false
            }
        }
        if (entryMatch) return true

        return activeWhitelistFolders.any { rule ->
            StoragePathHelper.isMediaInsideFolder(
                mediaPath = video.path,
                mediaFolderPath = video.folderPath,
                mediaDocumentId = video.documentId,
                mediaSourceTreeUri = video.sourceTreeUri,
                ruleIdentifier = rule.displayPath,
                ruleDocumentId = rule.documentId,
                ruleTreeUri = rule.treeUri,
                includeSubfolders = rule.includeSubfolders
            )
        }
    }

    private fun matchesTrackIdentity(track: AudioTrack, identifier: String, ruleDocId: String): Boolean {
        if (identifier == track.id.toString()) return true
        if (ruleDocId.isNotBlank() && track.documentId == ruleDocId) return true
        if (track.path.isNotBlank() && identifier.equals(track.path, ignoreCase = true)) return true
        if (track.contentUri.isNotBlank() && identifier == track.contentUri) return true
        return identifier.equals(track.title, ignoreCase = true)
    }

    private fun matchesVideoIdentity(video: VideoTrack, identifier: String, ruleDocId: String): Boolean {
        if (identifier == video.id.toString()) return true
        if (ruleDocId.isNotBlank() && video.documentId == ruleDocId) return true
        if (video.path.isNotBlank() && identifier.equals(video.path, ignoreCase = true)) return true
        if (video.contentUri.isNotBlank() && identifier == video.contentUri) return true
        return identifier.equals(video.title, ignoreCase = true)
    }

    /**
     * Counts how many audio tracks + videos are matched by a specific rule.
     */
    fun countAffectedMediaForFolderRule(
        rule: FolderRule,
        allTracks: List<AudioTrack>,
        allVideos: List<VideoTrack>
    ): Int {
        val includeAudio = rule.ruleType == RuleType.FOLDER || rule.ruleType == RuleType.MUSIC_FOLDER
        val includeVideo = rule.ruleType == RuleType.FOLDER || rule.ruleType == RuleType.VIDEO_FOLDER

        val audioCount = if (includeAudio) {
            allTracks.count { t ->
                StoragePathHelper.isMediaInsideFolder(
                    t.path, t.folderPath, t.documentId, t.sourceTreeUri,
                    rule.displayPath, rule.documentId, rule.treeUri, rule.includeSubfolders
                )
            }
        } else 0

        val videoCount = if (includeVideo) {
            allVideos.count { v ->
                StoragePathHelper.isMediaInsideFolder(
                    v.path, v.folderPath, v.documentId, v.sourceTreeUri,
                    rule.displayPath, rule.documentId, rule.treeUri, rule.includeSubfolders
                )
            }
        } else 0

        return audioCount + videoCount
    }

    fun countAffectedMediaForEntry(
        type: String,
        identifier: String,
        documentId: String,
        treeUri: String,
        allTracks: List<AudioTrack>,
        allVideos: List<VideoTrack>
    ): Int {
        return when (type.uppercase()) {
            "SONG" -> allTracks.count { matchesTrackIdentity(it, identifier, documentId) }
            "ARTIST" -> allTracks.count { it.artist.equals(identifier, ignoreCase = true) }
            "ALBUM" -> allTracks.count { it.album.equals(identifier, ignoreCase = true) }
            "VIDEO" -> allVideos.count { matchesVideoIdentity(it, identifier, documentId) }
            "MUSIC_FOLDER" -> allTracks.count {
                StoragePathHelper.isMediaInsideFolder(it.path, it.folderPath, it.documentId, it.sourceTreeUri, identifier, documentId, treeUri)
            }
            "VIDEO_FOLDER" -> allVideos.count {
                StoragePathHelper.isMediaInsideFolder(it.path, it.folderPath, it.documentId, it.sourceTreeUri, identifier, documentId, treeUri)
            }
            "FOLDER" -> {
                allTracks.count {
                    StoragePathHelper.isMediaInsideFolder(it.path, it.folderPath, it.documentId, it.sourceTreeUri, identifier, documentId, treeUri)
                } + allVideos.count {
                    StoragePathHelper.isMediaInsideFolder(it.path, it.folderPath, it.documentId, it.sourceTreeUri, identifier, documentId, treeUri)
                }
            }
            else -> 0
        }
    }

    /**
     * Detects conflicts when adding a rule to Whitelist or Blacklist.
     * Ensures:
     * - Whitelisting a folder/song/video inside a blacklisted folder shows a clear conflict warning.
     * - Whitelisting an explicitly blacklisted item shows a clear conflict warning.
     * - Blacklisting an item/folder that overlaps with a whitelisted rule shows a warning.
     */
    fun detectConflictsForNewRule(
        targetList: RuleListKind,
        ruleType: RuleType,
        identifier: String,
        documentId: String = "",
        treeUri: String = "",
        mediaPath: String = "",
        mediaArtist: String = "",
        mediaAlbum: String = "",
        blacklistEntries: List<BlacklistEntry>,
        whitelistEntries: List<WhitelistEntry>,
        folderRules: List<FolderRule>
    ): List<RuleConflict> {
        val conflicts = mutableListOf<RuleConflict>()

        if (targetList == RuleListKind.WHITELIST) {
            val blFolders = folderRules.filter { it.listKind == RuleListKind.BLACKLIST }

            when (ruleType) {
                RuleType.FOLDER, RuleType.MUSIC_FOLDER, RuleType.VIDEO_FOLDER -> {
                    val newKey = StoragePathHelper.canonicalizeFolderKey(documentId.ifBlank { identifier }, treeUri)

                    // Check against blacklisted FolderRules (is new folder same as or inside blacklisted folder, or parent of blacklisted subfolder)
                    for (bl in blFolders) {
                        val blKey = StoragePathHelper.canonicalizeFolderKey(bl.documentId.ifBlank { bl.displayPath }, bl.treeUri)
                        val isInsideBl = StoragePathHelper.isDescendantOrEqual(newKey, blKey) || (treeUri.isNotBlank() && bl.treeUri == treeUri)
                        val containsBlSubfolder = StoragePathHelper.isDescendantOrEqual(blKey, newKey)

                        if (isInsideBl) {
                            conflicts.add(
                                RuleConflict(
                                    existingRuleId = bl.id,
                                    existingListKind = RuleListKind.BLACKLIST,
                                    existingRuleType = bl.ruleType,
                                    existingTitle = bl.displayName,
                                    existingPath = bl.displayPath,
                                    isFolderRule = true,
                                    reason = "This folder is inside (or identical to) blacklisted folder '${bl.displayName}'. Blacklist rules take precedence and will keep excluding its files unless removed."
                                )
                            )
                        } else if (containsBlSubfolder) {
                            conflicts.add(
                                RuleConflict(
                                    existingRuleId = bl.id,
                                    existingListKind = RuleListKind.BLACKLIST,
                                    existingRuleType = bl.ruleType,
                                    existingTitle = bl.displayName,
                                    existingPath = bl.displayPath,
                                    isFolderRule = true,
                                    reason = "Subfolder '${bl.displayName}' inside this folder is blacklisted. Files in that subfolder will remain excluded due to Blacklist priority."
                                )
                            )
                        }
                    }

                    // Check legacy folder blacklist entries
                    for (bl in blacklistEntries.filter { it.type in listOf("FOLDER", "MUSIC_FOLDER", "VIDEO_FOLDER") }) {
                        val blKey = StoragePathHelper.canonicalizeFolderKey(bl.documentId.ifBlank { bl.identifier }, bl.treeUri)
                        if (StoragePathHelper.isDescendantOrEqual(newKey, blKey)) {
                            conflicts.add(
                                RuleConflict(
                                    existingRuleId = bl.id,
                                    existingListKind = RuleListKind.BLACKLIST,
                                    existingRuleType = RuleType.fromString(bl.type),
                                    existingTitle = bl.title,
                                    existingPath = bl.displayPath.ifBlank { bl.identifier },
                                    isFolderRule = false,
                                    reason = "Inside blacklisted folder '${bl.title}'. Blacklist takes priority."
                                )
                            )
                        }
                    }
                }
                RuleType.SONG, RuleType.VIDEO -> {
                    // Check if item's folder is blacklisted
                    for (bl in blFolders) {
                        if (StoragePathHelper.isMediaInsideFolder(
                                mediaPath = mediaPath,
                                mediaFolderPath = "",
                                mediaDocumentId = documentId,
                                mediaSourceTreeUri = treeUri,
                                ruleIdentifier = bl.displayPath,
                                ruleDocumentId = bl.documentId,
                                ruleTreeUri = bl.treeUri
                            )
                        ) {
                            conflicts.add(
                                RuleConflict(
                                    existingRuleId = bl.id,
                                    existingListKind = RuleListKind.BLACKLIST,
                                    existingRuleType = bl.ruleType,
                                    existingTitle = bl.displayName,
                                    existingPath = bl.displayPath,
                                    isFolderRule = true,
                                    reason = "This file is inside blacklisted folder '${bl.displayName}'. Blacklisted folders exclude all descendants."
                                )
                            )
                        }
                    }
                    // Check if item itself, its artist, or its album is blacklisted
                    for (bl in blacklistEntries) {
                        val match = when (bl.type.uppercase()) {
                            "SONG", "VIDEO" -> bl.identifier.equals(identifier, ignoreCase = true) ||
                                (mediaPath.isNotBlank() && bl.identifier.equals(mediaPath, ignoreCase = true))
                            "ARTIST" -> mediaArtist.isNotBlank() && bl.identifier.equals(mediaArtist, ignoreCase = true)
                            "ALBUM" -> mediaAlbum.isNotBlank() && bl.identifier.equals(mediaAlbum, ignoreCase = true)
                            else -> false
                        }
                        if (match) {
                            conflicts.add(
                                RuleConflict(
                                    existingRuleId = bl.id,
                                    existingListKind = RuleListKind.BLACKLIST,
                                    existingRuleType = RuleType.fromString(bl.type),
                                    existingTitle = bl.title,
                                    existingPath = bl.displayPath.ifBlank { bl.identifier },
                                    isFolderRule = false,
                                    reason = "Blocked by existing Blacklist ${bl.type.lowercase()} rule '${bl.title}'. Explicit blacklist overrides whitelist."
                                )
                            )
                        }
                    }
                }
                RuleType.ARTIST, RuleType.ALBUM -> {
                    for (bl in blacklistEntries.filter { it.type.equals(ruleType.name, ignoreCase = true) }) {
                        if (bl.identifier.equals(identifier, ignoreCase = true)) {
                            conflicts.add(
                                RuleConflict(
                                    existingRuleId = bl.id,
                                    existingListKind = RuleListKind.BLACKLIST,
                                    existingRuleType = RuleType.fromString(bl.type),
                                    existingTitle = bl.title,
                                    existingPath = bl.identifier,
                                    isFolderRule = false,
                                    reason = "'${bl.title}' is currently blacklisted. Explicit blacklist overrides whitelist."
                                )
                            )
                        }
                    }
                }
            }
        } else {
            // Adding to BLACKLIST -> check if it conflicts with existing WHITELIST
            val wlFolders = folderRules.filter { it.listKind == RuleListKind.WHITELIST }
            if (ruleType == RuleType.FOLDER || ruleType == RuleType.MUSIC_FOLDER || ruleType == RuleType.VIDEO_FOLDER) {
                val newKey = StoragePathHelper.canonicalizeFolderKey(documentId.ifBlank { identifier }, treeUri)
                for (wl in wlFolders) {
                    val wlKey = StoragePathHelper.canonicalizeFolderKey(wl.documentId.ifBlank { wl.displayPath }, wl.treeUri)
                    if (StoragePathHelper.isDescendantOrEqual(newKey, wlKey) ||
                        StoragePathHelper.isDescendantOrEqual(wlKey, newKey) ||
                        (treeUri.isNotBlank() && wl.treeUri == treeUri)
                    ) {
                        conflicts.add(
                            RuleConflict(
                                existingRuleId = wl.id,
                                existingListKind = RuleListKind.WHITELIST,
                                existingRuleType = wl.ruleType,
                                existingTitle = wl.displayName,
                                existingPath = wl.displayPath,
                                isFolderRule = true,
                                reason = "Overlaps with whitelisted folder '${wl.displayName}'. Because Blacklist takes priority, matching files will be hidden."
                            )
                        )
                    }
                }
            } else {
                for (wl in whitelistEntries) {
                    if (wl.type.equals(ruleType.name, ignoreCase = true) && wl.identifier.equals(identifier, ignoreCase = true)) {
                        conflicts.add(
                            RuleConflict(
                                existingRuleId = wl.id,
                                existingListKind = RuleListKind.WHITELIST,
                                existingRuleType = RuleType.fromString(wl.type),
                                existingTitle = wl.title,
                                existingPath = wl.identifier,
                                isFolderRule = false,
                                reason = "'${wl.title}' is currently whitelisted. Adding it to Blacklist will exclude it."
                            )
                        )
                    }
                }
            }
        }

        return conflicts
    }
}
