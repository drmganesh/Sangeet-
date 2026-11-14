package com.example

import com.example.sangeet.data.model.*
import com.example.sangeet.data.rules.RuleFilterEngine
import com.example.sangeet.data.rules.StoragePathHelper
import org.junit.Assert.*
import org.junit.Test

class RuleEngineUnitTest {

    private fun sampleAudio(
        id: Long,
        title: String = "Track $id",
        artist: String = "Artist $id",
        album: String = "Album $id",
        path: String = "",
        folderPath: String = "",
        documentId: String = "",
        sourceTreeUri: String = "",
        isWhitelistOnly: Boolean = false
    ) = AudioTrack(
        id = id,
        title = title,
        artist = artist,
        album = album,
        path = path,
        folderPath = folderPath,
        documentId = documentId,
        sourceTreeUri = sourceTreeUri,
        isWhitelistOnly = isWhitelistOnly
    )

    private fun sampleVideo(
        id: Long,
        title: String = "Video $id",
        path: String = "",
        folderPath: String = "",
        documentId: String = "",
        sourceTreeUri: String = "",
        isWhitelistOnly: Boolean = false
    ) = VideoTrack(
        id = id,
        title = title,
        path = path,
        folderPath = folderPath,
        documentId = documentId,
        sourceTreeUri = sourceTreeUri,
        isWhitelistOnly = isWhitelistOnly
    )

    @Test
    fun canonicalizePaths_matchesSafAndMediaStoreAndSdCard() {
        assertEquals(
            "primary:music/rock/2026",
            StoragePathHelper.canonicalizeFolderKey("/storage/emulated/0/Music/Rock/2026/")
        )
        assertEquals(
            "primary:music/rock/2026",
            StoragePathHelper.canonicalizeFolderKey("primary:Music/Rock/2026")
        )
        assertEquals(
            "1a2b-3c4d:movies/action",
            StoragePathHelper.canonicalizeFolderKey("/storage/1A2B-3C4D/Movies/Action")
        )
        assertEquals(
            "primary:music/rock",
            StoragePathHelper.extractDocIdFromUriString(
                "content://com.android.externalstorage.documents/tree/primary%3AMusic%2FRock"
            ).lowercase()
        )
    }

    @Test
    fun folderMatching_avoidsPrefixCollisionsAndRespectsRecursionToggle() {
        // /Music/Popcorn must NOT match rule on /Music/Pop
        val popcornTrack = sampleAudio(
            id = 1L,
            path = "/storage/emulated/0/Music/Popcorn/song.mp3",
            folderPath = "/storage/emulated/0/Music/Popcorn"
        )
        val popDirectTrack = sampleAudio(
            id = 2L,
            path = "/storage/emulated/0/Music/Pop/hit.mp3",
            folderPath = "/storage/emulated/0/Music/Pop"
        )
        val popSubfolderTrack = sampleAudio(
            id = 3L,
            path = "/storage/emulated/0/Music/Pop/Live/acoustic.mp3",
            folderPath = "/storage/emulated/0/Music/Pop/Live"
        )

        val popRuleRecursive = FolderRule(
            id = 10L,
            listKind = RuleListKind.BLACKLIST,
            ruleType = RuleType.MUSIC_FOLDER,
            treeUri = "content://com.android.externalstorage.documents/tree/primary%3AMusic%2FPop",
            documentId = "primary:Music/Pop",
            displayName = "Pop",
            displayPath = "Internal Storage/Music/Pop",
            includeSubfolders = true
        )

        val filteredRecursive = RuleFilterEngine.filterAudioTracks(
            tracks = listOf(popcornTrack, popDirectTrack, popSubfolderTrack),
            blacklistEntries = emptyList(),
            whitelistEntries = emptyList(),
            folderRules = listOf(popRuleRecursive)
        )
        assertEquals(listOf(1L), filteredRecursive.map { it.id })

        // Non-recursive rule only excludes /Music/Pop directly, keeping /Music/Pop/Live
        val nonRecursive = popRuleRecursive.copy(includeSubfolders = false)
        val filteredNonRecursive = RuleFilterEngine.filterAudioTracks(
            tracks = listOf(popcornTrack, popDirectTrack, popSubfolderTrack),
            blacklistEntries = emptyList(),
            whitelistEntries = emptyList(),
            folderRules = listOf(nonRecursive)
        )
        assertEquals(listOf(1L, 3L), filteredNonRecursive.map { it.id })
    }

    @Test
    fun blacklistSubfolderOverridesWhitelistedParentFolder() {
        val allowedMain = sampleAudio(
            id = 1L,
            path = "/storage/emulated/0/Music/Library/main.flac",
            folderPath = "/storage/emulated/0/Music/Library"
        )
        val hiddenRingtoneSub = sampleAudio(
            id = 2L,
            path = "/storage/emulated/0/Music/Library/Ringtones/beep.mp3",
            folderPath = "/storage/emulated/0/Music/Library/Ringtones"
        )
        val outsideLibrary = sampleAudio(
            id = 3L,
            path = "/storage/emulated/0/Downloads/random.mp3",
            folderPath = "/storage/emulated/0/Downloads"
        )

        val wlParent = FolderRule(
            id = 1L,
            listKind = RuleListKind.WHITELIST,
            ruleType = RuleType.MUSIC_FOLDER,
            treeUri = "content://auth/tree/primary%3AMusic%2FLibrary",
            documentId = "primary:Music/Library",
            displayName = "Library",
            displayPath = "Internal Storage/Music/Library",
            includeSubfolders = true
        )
        val blChild = FolderRule(
            id = 2L,
            listKind = RuleListKind.BLACKLIST,
            ruleType = RuleType.MUSIC_FOLDER,
            treeUri = "content://auth/tree/primary%3AMusic%2FLibrary%2FRingtones",
            documentId = "primary:Music/Library/Ringtones",
            displayName = "Ringtones",
            displayPath = "Internal Storage/Music/Library/Ringtones",
            includeSubfolders = true
        )

        val result = RuleFilterEngine.filterAudioTracks(
            tracks = listOf(allowedMain, hiddenRingtoneSub, outsideLibrary),
            blacklistEntries = emptyList(),
            whitelistEntries = emptyList(),
            folderRules = listOf(wlParent, blChild)
        )

        // Library/main.flac is shown; Library/Ringtones is excluded (Blacklist priority); Downloads is hidden (not whitelisted)
        assertEquals(listOf(1L), result.map { it.id })
    }

    @Test
    fun explicitBlacklistOverridesExplicitWhitelist_andWhitelistIncludesNomediaSafFiles() {
        val safHiddenSong = sampleAudio(
            id = 10L,
            title = "Secret Demo",
            documentId = "primary:PrivateFolder/Sub/secret.flac",
            sourceTreeUri = "content://auth/tree/primary%3APrivateFolder",
            isWhitelistOnly = true
        )
        val normalTrack = sampleAudio(
            id = 11L,
            title = "Regular Song",
            path = "/storage/emulated/0/Music/regular.mp3"
        )

        val wlFolder = FolderRule(
            id = 5L,
            listKind = RuleListKind.WHITELIST,
            ruleType = RuleType.FOLDER,
            treeUri = "content://auth/tree/primary%3APrivateFolder",
            documentId = "primary:PrivateFolder",
            displayName = "PrivateFolder",
            displayPath = "Internal Storage/PrivateFolder",
            includeSubfolders = true
        )

        // When PrivateFolder is whitelisted, SAF-only track in subfolder is included
        val includedResult = RuleFilterEngine.filterAudioTracks(
            tracks = listOf(safHiddenSong, normalTrack),
            blacklistEntries = emptyList(),
            whitelistEntries = listOf(WhitelistEntry(id = 1L, type = "SONG", identifier = "/storage/emulated/0/Music/regular.mp3", title = "Regular Song")),
            folderRules = listOf(wlFolder)
        )
        assertEquals(listOf(10L, 11L), includedResult.map { it.id })

        // Explicit blacklist on Regular Song overrides explicit whitelist on Regular Song
        val overriddenResult = RuleFilterEngine.filterAudioTracks(
            tracks = listOf(safHiddenSong, normalTrack),
            blacklistEntries = listOf(BlacklistEntry(id = 2L, type = "SONG", identifier = "/storage/emulated/0/Music/regular.mp3", title = "Regular Song")),
            whitelistEntries = listOf(WhitelistEntry(id = 1L, type = "SONG", identifier = "/storage/emulated/0/Music/regular.mp3", title = "Regular Song")),
            folderRules = listOf(wlFolder)
        )
        assertEquals(listOf(10L), overriddenResult.map { it.id })
    }

    @Test
    fun musicAndVideoFolderScopesStayProperlyFiltered() {
        val songInMovies = sampleAudio(id = 1L, folderPath = "/storage/emulated/0/Movies/Clips", path = "/storage/emulated/0/Movies/Clips/ost.mp3")
        val videoInMovies = sampleVideo(id = 2L, folderPath = "/storage/emulated/0/Movies/Clips", path = "/storage/emulated/0/Movies/Clips/clip.mp4")

        val videoOnlyBlacklist = FolderRule(
            id = 9L,
            listKind = RuleListKind.BLACKLIST,
            ruleType = RuleType.VIDEO_FOLDER,
            treeUri = "",
            documentId = "primary:Movies/Clips",
            displayName = "Clips",
            displayPath = "Internal Storage/Movies/Clips",
            includeSubfolders = true
        )

        val filteredAudio = RuleFilterEngine.filterAudioTracks(listOf(songInMovies), emptyList(), emptyList(), listOf(videoOnlyBlacklist))
        val filteredVideos = RuleFilterEngine.filterVideoTracks(listOf(videoInMovies), emptyList(), emptyList(), listOf(videoOnlyBlacklist))

        assertEquals(listOf(1L), filteredAudio.map { it.id })
        assertTrue(filteredVideos.isEmpty())
    }

    @Test
    fun detectConflicts_reportsParentAndChildOverlapWarnings() {
        val blParent = FolderRule(
            id = 1L,
            listKind = RuleListKind.BLACKLIST,
            ruleType = RuleType.FOLDER,
            treeUri = "content://auth/tree/primary%3AAudio",
            documentId = "primary:Audio",
            displayName = "Audio",
            displayPath = "Internal Storage/Audio"
        )

        val folderConflicts = RuleFilterEngine.detectConflictsForNewRule(
            targetList = RuleListKind.WHITELIST,
            ruleType = RuleType.FOLDER,
            identifier = "Internal Storage/Audio/Sub",
            documentId = "primary:Audio/Sub",
            treeUri = "content://auth/tree/primary%3AAudio%2FSub",
            blacklistEntries = emptyList(),
            whitelistEntries = emptyList(),
            folderRules = listOf(blParent)
        )
        assertEquals(1, folderConflicts.size)
        assertTrue(folderConflicts.first().reason.contains("Blacklist", ignoreCase = true))

        val songConflicts = RuleFilterEngine.detectConflictsForNewRule(
            targetList = RuleListKind.WHITELIST,
            ruleType = RuleType.SONG,
            identifier = "/storage/emulated/0/Audio/Sub/track.mp3",
            mediaPath = "/storage/emulated/0/Audio/Sub/track.mp3",
            blacklistEntries = emptyList(),
            whitelistEntries = emptyList(),
            folderRules = listOf(blParent)
        )
        assertEquals(1, songConflicts.size)
    }
}
