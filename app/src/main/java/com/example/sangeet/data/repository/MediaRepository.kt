package com.example.sangeet.data.repository

import android.content.Context
import android.net.Uri
import com.example.sangeet.data.local.AppDatabase
import com.example.sangeet.data.model.*
import com.example.sangeet.data.rules.RuleConflict
import com.example.sangeet.data.rules.RuleFilterEngine
import com.example.sangeet.data.rules.StoragePathHelper
import com.example.sangeet.scanner.ArtworkManager
import com.example.sangeet.scanner.FolderInspectionResult
import com.example.sangeet.scanner.LyricsManager
import com.example.sangeet.scanner.MediaScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class MediaFolderSummary(
    val canonicalKey: String,
    val displayName: String,
    val displayPath: String,
    val rawFolderPath: String,
    val documentId: String,
    val treeUri: String,
    val audioCount: Int,
    val videoCount: Int,
    val blacklistRuleId: Long? = null,
    val whitelistRuleId: Long? = null,
    val blacklistEntryId: Long? = null,
    val whitelistEntryId: Long? = null
) {
    val isBlacklisted: Boolean get() = blacklistRuleId != null || blacklistEntryId != null
    val isWhitelisted: Boolean get() = whitelistRuleId != null || whitelistEntryId != null
}

class MediaRepository(private val context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val scanner = MediaScanner(context)
    val safScanner = scanner.safScanner
    val artworkManager = ArtworkManager(context)
    val lyricsManager = LyricsManager(context)

    // Blacklist, Whitelist, Folder Rules & Permissions
    val blacklist: Flow<List<BlacklistEntry>> = db.blacklistDao().getAllBlacklist()
    val whitelist: Flow<List<WhitelistEntry>> = db.whitelistDao().getAllWhitelist()
    val folderRules: Flow<List<FolderRule>> = db.folderRuleDao().getAllFolderRules()
    val folderPermissions: Flow<List<FolderPermission>> = db.folderPermissionDao().getAllPermissions()

    // Raw Tracks & Videos
    val rawAllTracks: Flow<List<AudioTrack>> = db.audioTrackDao().getAllTracks()
    val rawAllVideos: Flow<List<VideoTrack>> = db.videoTrackDao().getAllVideos()
    private val rawFavoriteTracks: Flow<List<AudioTrack>> = db.audioTrackDao().getFavoriteTracks()
    private val rawWatchLaterVideos: Flow<List<VideoTrack>> = db.videoTrackDao().getWatchLaterVideos()
    private val rawRecentlyWatchedVideos: Flow<List<VideoTrack>> = db.videoTrackDao().getRecentlyWatchedVideos()

    // Filtered Audio Tracks (Applying Blacklist/Whitelist & Recursive Folder Priority Rules)
    val allTracks: Flow<List<AudioTrack>> = combine(
        rawAllTracks, blacklist, whitelist, folderRules
    ) { tracks, bl, wl, fr ->
        RuleFilterEngine.filterAudioTracks(tracks, bl, wl, fr)
    }

    val favoriteTracks: Flow<List<AudioTrack>> = combine(
        rawFavoriteTracks, blacklist, whitelist, folderRules
    ) { tracks, bl, wl, fr ->
        RuleFilterEngine.filterAudioTracks(tracks, bl, wl, fr)
    }

    val recentlyAdded: Flow<List<AudioTrack>> = allTracks.map { tracks ->
        tracks.sortedByDescending { it.dateAdded }.take(30)
    }

    val recentlyPlayed: Flow<List<AudioTrack>> = allTracks.map { tracks ->
        tracks.filter { it.lastPlayedTimestamp > 0L }
            .sortedByDescending { it.lastPlayedTimestamp }
            .take(30)
    }

    val mostPlayed: Flow<List<AudioTrack>> = allTracks.map { tracks ->
        tracks.filter { it.playCount > 0 }
            .sortedByDescending { it.playCount }
            .take(30)
    }

    val allArtists: Flow<List<String>> = allTracks.map { tracks ->
        tracks.map { it.artist }.filter { it.isNotBlank() }.distinct().sorted()
    }

    val allAlbums: Flow<List<String>> = allTracks.map { tracks ->
        tracks.map { it.album }.filter { it.isNotBlank() }.distinct().sorted()
    }

    val allGenres: Flow<List<String>> = allTracks.map { tracks ->
        tracks.map { it.genre }.filter { it.isNotBlank() && it != "Unknown" }.distinct().sorted()
    }

    // Filtered Videos
    val allVideos: Flow<List<VideoTrack>> = combine(
        rawAllVideos, blacklist, whitelist, folderRules
    ) { videos, bl, wl, fr ->
        RuleFilterEngine.filterVideoTracks(videos, bl, wl, fr)
    }

    val watchLaterVideos: Flow<List<VideoTrack>> = combine(
        rawWatchLaterVideos, blacklist, whitelist, folderRules
    ) { videos, bl, wl, fr ->
        RuleFilterEngine.filterVideoTracks(videos, bl, wl, fr)
    }

    val recentlyWatchedVideos: Flow<List<VideoTrack>> = combine(
        rawRecentlyWatchedVideos, blacklist, whitelist, folderRules
    ) { videos, bl, wl, fr ->
        RuleFilterEngine.filterVideoTracks(videos, bl, wl, fr)
    }

    // Discovered Folders Summary (for contextual Library & Files folder actions)
    val mediaFolders: Flow<List<MediaFolderSummary>> = combine(
        rawAllTracks, rawAllVideos, blacklist, whitelist, folderRules
    ) { tracks, videos, bl, wl, fr ->
        buildMediaFoldersSummary(tracks, videos, bl, wl, fr)
    }

    // Playlists
    val allPlaylists: Flow<List<Playlist>> = db.playlistDao().getAllPlaylists()

    // History & Stats
    val recentSearches: Flow<List<String>> = db.historyAndStatsDao().getRecentSearches()
    val totalSongsPlayed: Flow<Int> = db.historyAndStatsDao().getTotalSongsPlayed()
    val totalListeningTimeMs: Flow<Long?> = db.historyAndStatsDao().getTotalListeningTimeMs()

    // Presets
    val allEqPresets: Flow<List<EqualizerPreset>> = db.presetDao().getAllEqualizerPresets()
    val allSpatialPresets: Flow<List<SpatialPreset>> = db.presetDao().getAllSpatialPresets()

    fun getTracksByArtist(artist: String): Flow<List<AudioTrack>> =
        combine(db.audioTrackDao().getTracksByArtist(artist), blacklist, whitelist, folderRules) { tracks, bl, wl, fr ->
            RuleFilterEngine.filterAudioTracks(tracks, bl, wl, fr)
        }

    fun getTracksByAlbum(album: String): Flow<List<AudioTrack>> =
        combine(db.audioTrackDao().getTracksByAlbum(album), blacklist, whitelist, folderRules) { tracks, bl, wl, fr ->
            RuleFilterEngine.filterAudioTracks(tracks, bl, wl, fr)
        }

    fun getTracksByGenre(genre: String): Flow<List<AudioTrack>> =
        combine(db.audioTrackDao().getTracksByGenre(genre), blacklist, whitelist, folderRules) { tracks, bl, wl, fr ->
            RuleFilterEngine.filterAudioTracks(tracks, bl, wl, fr)
        }

    fun searchTracks(query: String): Flow<List<AudioTrack>> =
        combine(db.audioTrackDao().searchTracks(query), blacklist, whitelist, folderRules) { tracks, bl, wl, fr ->
            RuleFilterEngine.filterAudioTracks(tracks, bl, wl, fr)
        }

    fun getPlaylistTracks(playlistId: Long): Flow<List<AudioTrack>> =
        combine(db.playlistDao().getTracksForPlaylist(playlistId), blacklist, whitelist, folderRules) { tracks, bl, wl, fr ->
            RuleFilterEngine.filterAudioTracks(tracks, bl, wl, fr)
        }

    fun getFolderRuleFlow(ruleId: Long): Flow<FolderRule?> =
        db.folderRuleDao().getFolderRuleByIdFlow(ruleId)

    // --- Folder Inspection & SAF Permission Persistence ---

    suspend fun inspectPickedSafFolder(treeUri: Uri): FolderInspectionResult = withContext(Dispatchers.IO) {
        val perm = safScanner.takeAndRecordPermission(treeUri)
        db.folderPermissionDao().insertOrUpdate(perm)
        safScanner.inspectFolder(
            treeUriString = treeUri.toString(),
            extractDeepMetadata = false,
            isWhitelistSource = false
        )
    }

    suspend fun detectConflictsForNewRule(
        targetList: RuleListKind,
        ruleType: RuleType,
        identifier: String,
        documentId: String = "",
        treeUri: String = "",
        mediaPath: String = "",
        mediaArtist: String = "",
        mediaAlbum: String = ""
    ): List<RuleConflict> = withContext(Dispatchers.IO) {
        RuleFilterEngine.detectConflictsForNewRule(
            targetList = targetList,
            ruleType = ruleType,
            identifier = identifier,
            documentId = documentId,
            treeUri = treeUri,
            mediaPath = mediaPath,
            mediaArtist = mediaArtist,
            mediaAlbum = mediaAlbum,
            blacklistEntries = db.blacklistDao().getAllBlacklistSync(),
            whitelistEntries = db.whitelistDao().getAllWhitelistSync(),
            folderRules = db.folderRuleDao().getAllFolderRulesSync()
        )
    }

    suspend fun removeConflictingRule(conflict: RuleConflict) = withContext(Dispatchers.IO) {
        if (conflict.isFolderRule) {
            removeFolderRule(conflict.existingRuleId)
        } else if (conflict.existingListKind == RuleListKind.BLACKLIST) {
            db.blacklistDao().deleteById(conflict.existingRuleId)
        } else {
            db.whitelistDao().deleteById(conflict.existingRuleId)
        }
    }

    suspend fun addFolderRule(
        listKind: RuleListKind,
        ruleType: RuleType,
        treeUri: String,
        documentId: String,
        displayName: String,
        displayPath: String,
        includeSubfolders: Boolean = true,
        subfolderCount: Int = 0,
        initialMediaCount: Int = 0
    ): Long = withContext(Dispatchers.IO) {
        // Avoid duplicating an identical treeUri + listKind rule; update instead if present
        val existing = db.folderRuleDao().getAllFolderRulesSync().find {
            it.listKind == listKind && (
                (treeUri.isNotBlank() && it.treeUri == treeUri) ||
                    (documentId.isNotBlank() && it.documentId.equals(documentId, ignoreCase = true))
                )
        }

        val rule = if (existing != null) {
            existing.copy(
                ruleType = ruleType,
                treeUri = treeUri,
                documentId = documentId,
                displayName = displayName,
                displayPath = displayPath,
                includeSubfolders = includeSubfolders,
                subfolderCount = subfolderCount,
                affectedMediaCount = initialMediaCount,
                status = RuleStatus.ACTIVE,
                lastError = ""
            )
        } else {
            FolderRule(
                listKind = listKind,
                ruleType = ruleType,
                treeUri = treeUri,
                documentId = documentId,
                displayName = displayName,
                displayPath = displayPath,
                includeSubfolders = includeSubfolders,
                affectedMediaCount = initialMediaCount,
                subfolderCount = subfolderCount,
                creationDate = RuleCreationDate(System.currentTimeMillis()),
                status = RuleStatus.ACTIVE
            )
        }

        val ruleId = if (existing != null) {
            db.folderRuleDao().update(rule)
            existing.id
        } else {
            db.folderRuleDao().insert(rule)
        }

        val savedRule = db.folderRuleDao().getFolderRuleById(ruleId) ?: rule.copy(id = ruleId)
        scanner.scanSingleFolderRule(savedRule)
        ruleId
    }

    suspend fun replaceFolderRuleSelection(
        ruleId: Long,
        newTreeUri: Uri,
        ruleType: RuleType,
        includeSubfolders: Boolean = true
    ): FolderInspectionResult = withContext(Dispatchers.IO) {
        val oldRule = db.folderRuleDao().getFolderRuleById(ruleId)
        val perm = safScanner.takeAndRecordPermission(newTreeUri)
        db.folderPermissionDao().insertOrUpdate(perm)

        val result = safScanner.inspectFolder(
            treeUriString = newTreeUri.toString(),
            extractDeepMetadata = false,
            isWhitelistSource = false
        )

        if (oldRule != null && result is FolderInspectionResult.Success) {
            if (oldRule.listKind == RuleListKind.WHITELIST && oldRule.treeUri != newTreeUri.toString()) {
                db.audioTrackDao().deleteWhitelistTracksByTreeUri(oldRule.treeUri)
                db.videoTrackDao().deleteWhitelistVideosByTreeUri(oldRule.treeUri)
            }
            val updated = oldRule.copy(
                ruleType = ruleType,
                treeUri = result.treeUri,
                documentId = result.documentId,
                displayName = result.displayName,
                displayPath = result.displayPath,
                includeSubfolders = includeSubfolders,
                subfolderCount = result.subfolderCount,
                affectedMediaCount = result.audioCount + result.videoCount,
                status = RuleStatus.ACTIVE,
                lastError = ""
            )
            db.folderRuleDao().update(updated)
            scanner.scanSingleFolderRule(updated)
        }
        result
    }

    suspend fun regrantFolderPermission(ruleId: Long, treeUri: Uri): FolderInspectionResult = withContext(Dispatchers.IO) {
        val rule = db.folderRuleDao().getFolderRuleById(ruleId)
        val perm = safScanner.takeAndRecordPermission(treeUri)
        db.folderPermissionDao().insertOrUpdate(perm)

        val result = safScanner.inspectFolder(
            treeUriString = treeUri.toString(),
            extractDeepMetadata = rule?.listKind == RuleListKind.WHITELIST,
            isWhitelistSource = rule?.listKind == RuleListKind.WHITELIST
        )

        if (rule != null) {
            val updated = rule.copy(
                treeUri = treeUri.toString(),
                status = if (result is FolderInspectionResult.Success) RuleStatus.ACTIVE else rule.status,
                lastError = ""
            )
            db.folderRuleDao().update(updated)
            scanner.scanSingleFolderRule(updated)
        }
        result
    }

    suspend fun toggleFolderRuleIncludeSubfolders(ruleId: Long, includeSubfolders: Boolean) = withContext(Dispatchers.IO) {
        val rule = db.folderRuleDao().getFolderRuleById(ruleId) ?: return@withContext
        val updated = rule.copy(includeSubfolders = includeSubfolders)
        db.folderRuleDao().update(updated)
        scanner.scanSingleFolderRule(updated)
    }

    suspend fun rescanFolderRule(ruleId: Long): Int = withContext(Dispatchers.IO) {
        val rule = db.folderRuleDao().getFolderRuleById(ruleId) ?: return@withContext 0
        scanner.scanSingleFolderRule(rule)
    }

    suspend fun removeFolderRule(ruleId: Long) = withContext(Dispatchers.IO) {
        val rule = db.folderRuleDao().getFolderRuleById(ruleId)
        db.folderRuleDao().deleteById(ruleId)
        if (rule != null && rule.listKind == RuleListKind.WHITELIST && rule.treeUri.isNotBlank()) {
            val stillReferenced = db.folderRuleDao().getAllFolderRulesSync().any {
                it.listKind == RuleListKind.WHITELIST && it.treeUri == rule.treeUri
            }
            if (!stillReferenced) {
                db.audioTrackDao().deleteWhitelistTracksByTreeUri(rule.treeUri)
                db.videoTrackDao().deleteWhitelistVideosByTreeUri(rule.treeUri)
            }
        }
    }

    // Blacklist & Whitelist Item Operations
    suspend fun addToBlacklist(
        type: String,
        identifier: String,
        title: String,
        displayPath: String = "",
        treeUri: String = "",
        documentId: String = "",
        affectedCount: Int = 0
    ): Long = withContext(Dispatchers.IO) {
        val existing = db.blacklistDao().getAllBlacklistSync().find {
            it.type.equals(type, ignoreCase = true) && it.identifier.equals(identifier, ignoreCase = true)
        }
        if (existing != null) return@withContext existing.id

        db.blacklistDao().insert(
            BlacklistEntry(
                type = type.uppercase(),
                identifier = identifier,
                title = title,
                displayPath = displayPath,
                treeUri = treeUri,
                documentId = documentId,
                affectedCount = affectedCount,
                status = RuleStatus.ACTIVE
            )
        )
    }

    suspend fun removeFromBlacklist(id: Long) {
        db.blacklistDao().deleteById(id)
    }

    suspend fun removeFromBlacklistByTypeAndIdentifier(type: String, identifier: String) {
        db.blacklistDao().deleteByTypeAndIdentifier(type, identifier)
    }

    suspend fun clearBlacklist() = withContext(Dispatchers.IO) {
        db.blacklistDao().clearAll()
        db.folderRuleDao().clearByKind(RuleListKind.BLACKLIST)
    }

    suspend fun addToWhitelist(
        type: String,
        identifier: String,
        title: String,
        displayPath: String = "",
        treeUri: String = "",
        documentId: String = "",
        affectedCount: Int = 0
    ): Long = withContext(Dispatchers.IO) {
        val existing = db.whitelistDao().getAllWhitelistSync().find {
            it.type.equals(type, ignoreCase = true) && it.identifier.equals(identifier, ignoreCase = true)
        }
        if (existing != null) return@withContext existing.id

        db.whitelistDao().insert(
            WhitelistEntry(
                type = type.uppercase(),
                identifier = identifier,
                title = title,
                displayPath = displayPath,
                treeUri = treeUri,
                documentId = documentId,
                affectedCount = affectedCount,
                status = RuleStatus.ACTIVE
            )
        )
    }

    suspend fun removeFromWhitelist(id: Long) {
        db.whitelistDao().deleteById(id)
    }

    suspend fun removeFromWhitelistByTypeAndIdentifier(type: String, identifier: String) {
        db.whitelistDao().deleteByTypeAndIdentifier(type, identifier)
    }

    suspend fun clearWhitelist() = withContext(Dispatchers.IO) {
        val wlFolders = db.folderRuleDao().getAllFolderRulesSync().filter { it.listKind == RuleListKind.WHITELIST }
        db.whitelistDao().clearAll()
        db.folderRuleDao().clearByKind(RuleListKind.WHITELIST)
        for (rule in wlFolders) {
            if (rule.treeUri.isNotBlank()) {
                db.audioTrackDao().deleteWhitelistTracksByTreeUri(rule.treeUri)
                db.videoTrackDao().deleteWhitelistVideosByTreeUri(rule.treeUri)
            }
        }
    }

    suspend fun bulkDeleteRules(
        listKind: RuleListKind,
        entryIds: List<Long>,
        folderRuleIds: List<Long>
    ) = withContext(Dispatchers.IO) {
        if (entryIds.isNotEmpty()) {
            if (listKind == RuleListKind.BLACKLIST) {
                db.blacklistDao().deleteByIds(entryIds)
            } else {
                db.whitelistDao().deleteByIds(entryIds)
            }
        }
        for (fid in folderRuleIds) {
            removeFolderRule(fid)
        }
    }

    private fun buildMediaFoldersSummary(
        tracks: List<AudioTrack>,
        videos: List<VideoTrack>,
        blacklist: List<BlacklistEntry>,
        whitelist: List<WhitelistEntry>,
        folderRules: List<FolderRule>
    ): List<MediaFolderSummary> {
        val map = linkedMapOf<String, MutableFolderAccumulator>()

        for (t in tracks) {
            val rawFolder = t.folderPath.ifBlank {
                if (t.path.isNotBlank() && !t.path.startsWith("content://")) java.io.File(t.path).parent ?: "" else ""
            }
            val key = StoragePathHelper.canonicalizeMediaParentFolder(t.path, rawFolder, t.documentId, t.sourceTreeUri)
            if (key.isBlank()) continue
            val info = StoragePathHelper.buildDisplayInfo(t.documentId.substringBeforeLast('/', rawFolder), "", t.sourceTreeUri)
            val acc = map.getOrPut(key) {
                MutableFolderAccumulator(
                    canonicalKey = key,
                    displayName = info.displayName,
                    displayPath = info.displayPath,
                    rawFolderPath = rawFolder.ifBlank { key },
                    documentId = if (t.documentId.contains("/")) t.documentId.substringBeforeLast('/') else key,
                    treeUri = t.sourceTreeUri
                )
            }
            acc.audioCount++
        }

        for (v in videos) {
            val rawFolder = v.folderPath.ifBlank {
                if (v.path.isNotBlank() && !v.path.startsWith("content://")) java.io.File(v.path).parent ?: "" else ""
            }
            val key = StoragePathHelper.canonicalizeMediaParentFolder(v.path, rawFolder, v.documentId, v.sourceTreeUri)
            if (key.isBlank()) continue
            val info = StoragePathHelper.buildDisplayInfo(v.documentId.substringBeforeLast('/', rawFolder), "", v.sourceTreeUri)
            val acc = map.getOrPut(key) {
                MutableFolderAccumulator(
                    canonicalKey = key,
                    displayName = info.displayName,
                    displayPath = info.displayPath,
                    rawFolderPath = rawFolder.ifBlank { key },
                    documentId = if (v.documentId.contains("/")) v.documentId.substringBeforeLast('/') else key,
                    treeUri = v.sourceTreeUri
                )
            }
            acc.videoCount++
        }

        // Also include explicit FolderRules so blacklisted folders still appear in folder management
        for (rule in folderRules) {
            val key = StoragePathHelper.canonicalizeFolderKey(rule.documentId.ifBlank { rule.displayPath }, rule.treeUri)
            if (key.isBlank()) continue
            val acc = map.getOrPut(key) {
                MutableFolderAccumulator(
                    canonicalKey = key,
                    displayName = rule.displayName,
                    displayPath = rule.displayPath,
                    rawFolderPath = rule.displayPath,
                    documentId = rule.documentId,
                    treeUri = rule.treeUri
                )
            }
            if (rule.listKind == RuleListKind.BLACKLIST) acc.blacklistRuleId = rule.id
            if (rule.listKind == RuleListKind.WHITELIST) acc.whitelistRuleId = rule.id
        }

        // Match rule IDs onto each discovered folder
        return map.values.map { acc ->
            val blRule = acc.blacklistRuleId ?: folderRules.find {
                it.listKind == RuleListKind.BLACKLIST &&
                    StoragePathHelper.canonicalizeFolderKey(it.documentId.ifBlank { it.displayPath }, it.treeUri) == acc.canonicalKey
            }?.id
            val wlRule = acc.whitelistRuleId ?: folderRules.find {
                it.listKind == RuleListKind.WHITELIST &&
                    StoragePathHelper.canonicalizeFolderKey(it.documentId.ifBlank { it.displayPath }, it.treeUri) == acc.canonicalKey
            }?.id
            val blEntry = blacklist.find {
                it.type in listOf("FOLDER", "MUSIC_FOLDER", "VIDEO_FOLDER") &&
                    StoragePathHelper.canonicalizeFolderKey(it.documentId.ifBlank { it.identifier }, it.treeUri) == acc.canonicalKey
            }?.id
            val wlEntry = whitelist.find {
                it.type in listOf("FOLDER", "MUSIC_FOLDER", "VIDEO_FOLDER") &&
                    StoragePathHelper.canonicalizeFolderKey(it.documentId.ifBlank { it.identifier }, it.treeUri) == acc.canonicalKey
            }?.id

            MediaFolderSummary(
                canonicalKey = acc.canonicalKey,
                displayName = acc.displayName,
                displayPath = acc.displayPath,
                rawFolderPath = acc.rawFolderPath,
                documentId = acc.documentId,
                treeUri = acc.treeUri,
                audioCount = acc.audioCount,
                videoCount = acc.videoCount,
                blacklistRuleId = blRule,
                whitelistRuleId = wlRule,
                blacklistEntryId = blEntry,
                whitelistEntryId = wlEntry
            )
        }.sortedBy { it.displayName.lowercase() }
    }

    private data class MutableFolderAccumulator(
        val canonicalKey: String,
        val displayName: String,
        val displayPath: String,
        val rawFolderPath: String,
        val documentId: String,
        val treeUri: String,
        var audioCount: Int = 0,
        var videoCount: Int = 0,
        var blacklistRuleId: Long? = null,
        var whitelistRuleId: Long? = null
    )

    suspend fun setTrackFavorite(trackId: Long, isFavorite: Boolean) {
        db.audioTrackDao().setFavorite(trackId, isFavorite)
    }

    suspend fun createPlaylist(name: String): Long {
        return db.playlistDao().insertPlaylist(Playlist(name = name))
    }

    suspend fun deletePlaylist(playlistId: Long) {
        db.playlistDao().deletePlaylist(playlistId)
        db.playlistDao().clearPlaylistTracks(playlistId)
    }

    suspend fun addTrackToPlaylist(playlistId: Long, trackId: Long) {
        db.playlistDao().addTrackToPlaylist(PlaylistTrack(playlistId = playlistId, trackId = trackId))
    }

    suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: Long) {
        db.playlistDao().removeTrackFromPlaylist(playlistId, trackId)
    }

    suspend fun scanMusic(): Int = scanner.scanLocalAudio()
    suspend fun scanVideos(): Int = scanner.scanLocalVideos()
    suspend fun generateDemoTracks(): List<AudioTrack> = scanner.generateDemoOfflineTracks()
    suspend fun importAudioUri(uri: Uri): AudioTrack? = scanner.importAudioUri(uri)

    suspend fun recordSearch(query: String) {
        if (query.isNotBlank()) {
            db.historyAndStatsDao().insertSearchQuery(SearchHistory(query = query.trim()))
        }
    }

    suspend fun clearHistory() {
        db.historyAndStatsDao().clearHistory()
    }

    suspend fun clearSearches() {
        db.historyAndStatsDao().clearSearches()
    }

    suspend fun updateVideoProgress(id: Long, progressMs: Long) {
        db.videoTrackDao().updateWatchProgress(id, progressMs)
    }

    suspend fun setVideoWatchLater(id: Long, inWatchLater: Boolean) {
        db.videoTrackDao().setWatchLater(id, inWatchLater)
    }

    suspend fun saveCustomEqPreset(
        name: String,
        bandGains: FloatArray,
        bassBoost: Float,
        trebleBoost: Float,
        balance: Float,
        preamp: Float
    ): Long {
        val jsonArray = JSONArray()
        for (g in bandGains) jsonArray.put(g.toDouble())
        return db.presetDao().insertEqualizerPreset(
            EqualizerPreset(
                name = name,
                isCustom = true,
                bandsGainJson = jsonArray.toString(),
                bassBoost = bassBoost,
                trebleBoost = trebleBoost,
                balance = balance,
                preampGainDb = preamp
            )
        )
    }

    suspend fun deleteEqPreset(preset: EqualizerPreset) {
        if (preset.isCustom) {
            db.presetDao().deleteEqualizerPreset(preset)
        }
    }

    suspend fun saveCustomSpatialPreset(
        name: String,
        intensity: Float,
        speed: Float,
        direction: Int,
        width: Float,
        depth: Float
    ): Long {
        return db.presetDao().insertSpatialPreset(
            SpatialPreset(
                name = name,
                isCustom = true,
                isEnabled = true,
                spatialIntensity = intensity,
                rotationSpeed = speed,
                rotationDirection = direction,
                soundstageWidth = width,
                soundstageDepth = depth
            )
        )
    }

    suspend fun deleteSpatialPreset(preset: SpatialPreset) {
        if (preset.isCustom) {
            db.presetDao().deleteSpatialPreset(preset)
        }
    }

    suspend fun exportBackupJson(): String = withContext(Dispatchers.IO) {
        val root = JSONObject()
        root.put("version", 3)
        root.put("appName", "SANGEET")
        root.put("timestamp", System.currentTimeMillis())
        root.toString(2)
    }

    suspend fun restoreBackupJson(jsonString: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val root = JSONObject(jsonString)
            root.optString("appName") == "SANGEET"
        } catch (_: Exception) {
            false
        }
    }
}
