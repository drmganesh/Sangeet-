package com.example.sangeet.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class RuleType(val displayName: String) {
    SONG("Song"),
    ARTIST("Artist"),
    ALBUM("Album"),
    MUSIC_FOLDER("Music Folder"),
    VIDEO("Video"),
    VIDEO_FOLDER("Video Folder"),
    FOLDER("Music & Video Folder");

    companion object {
        fun fromString(value: String): RuleType {
            return entries.find { it.name.equals(value, ignoreCase = true) } ?: FOLDER
        }
    }
}

enum class RuleListKind {
    BLACKLIST,
    WHITELIST
}

enum class RuleStatus(val label: String) {
    ACTIVE("Active"),
    PERMISSION_REVOKED("Permission Required"),
    FOLDER_NOT_FOUND("Folder Unavailable"),
    CONFLICT("Rule Conflict"),
    DISABLED("Paused");

    companion object {
        fun fromString(value: String): RuleStatus {
            return entries.find { it.name.equals(value, ignoreCase = true) } ?: ACTIVE
        }
    }
}

data class RuleCreationDate(
    val epochMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "audio_tracks")
data class AudioTrack(
    @PrimaryKey val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumArtist: String = "",
    val genre: String = "Unknown",
    val durationMs: Long = 0L,
    val trackNumber: Int = 0,
    val discNumber: Int = 0,
    val path: String = "",
    val contentUri: String = "",
    val dateAdded: Long = System.currentTimeMillis(),
    val year: Int = 0,
    val mimeType: String = "audio/mpeg",
    val bitRate: Int = 320,
    val sampleRate: Int = 44100,
    val fileSize: Long = 0L,
    val playCount: Int = 0,
    val lastPlayedTimestamp: Long = 0L,
    val isFavorite: Boolean = false,
    val embeddedLyrics: String = "",
    val lrcPath: String = "",
    val artworkUri: String = "",
    val sourceTreeUri: String = "",
    val documentId: String = "",
    val folderPath: String = "",
    val isWhitelistOnly: Boolean = false
)

@Entity(tableName = "video_tracks")
data class VideoTrack(
    @PrimaryKey val id: Long,
    val title: String,
    val durationMs: Long = 0L,
    val path: String = "",
    val contentUri: String = "",
    val dateAdded: Long = System.currentTimeMillis(),
    val resolution: String = "1080p",
    val mimeType: String = "video/mp4",
    val fileSize: Long = 0L,
    val watchProgressMs: Long = 0L,
    val lastWatchedTimestamp: Long = 0L,
    val isFavorite: Boolean = false,
    val inWatchLater: Boolean = false,
    val sourceTreeUri: String = "",
    val documentId: String = "",
    val folderPath: String = "",
    val isWhitelistOnly: Boolean = false
)

@Entity(tableName = "playlists")
data class Playlist(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val customArtworkUri: String = ""
)

@Entity(tableName = "playlist_tracks", primaryKeys = ["playlistId", "trackId"])
data class PlaylistTrack(
    val playlistId: Long,
    val trackId: Long,
    val sortOrder: Int = 0
)

@Entity(tableName = "playback_history")
data class PlaybackHistory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val trackId: Long,
    val title: String,
    val artist: String,
    val album: String,
    val playedAt: Long = System.currentTimeMillis(),
    val durationMs: Long = 0L,
    val completionPercentage: Float = 1.0f
)

@Entity(tableName = "search_history")
data class SearchHistory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val query: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "equalizer_presets")
data class EqualizerPreset(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val isCustom: Boolean = false,
    val bandsGainJson: String,
    val bassBoost: Float = 0f,
    val trebleBoost: Float = 0f,
    val balance: Float = 0f,
    val preampGainDb: Float = 0f
)

@Entity(tableName = "spatial_presets")
data class SpatialPreset(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val isCustom: Boolean = false,
    val isEnabled: Boolean = true,
    val spatialIntensity: Float = 0.8f,
    val rotationSpeed: Float = 1.0f,
    val rotationDirection: Int = 2,
    val soundstageWidth: Float = 1.2f,
    val soundstageDepth: Float = 0.7f,
    val centerPosition: Float = 0.0f
)

@Entity(tableName = "blacklist_entries")
data class BlacklistEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val type: String, // "SONG", "ARTIST", "ALBUM", "MUSIC_FOLDER", "VIDEO", "VIDEO_FOLDER", "FOLDER"
    val identifier: String,
    val title: String,
    val addedAt: Long = System.currentTimeMillis(),
    val displayPath: String = "",
    val treeUri: String = "",
    val documentId: String = "",
    val affectedCount: Int = 0,
    val status: RuleStatus = RuleStatus.ACTIVE
)

@Entity(tableName = "whitelist_entries")
data class WhitelistEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val type: String, // "SONG", "ARTIST", "ALBUM", "MUSIC_FOLDER", "VIDEO", "VIDEO_FOLDER", "FOLDER"
    val identifier: String,
    val title: String,
    val addedAt: Long = System.currentTimeMillis(),
    val displayPath: String = "",
    val treeUri: String = "",
    val documentId: String = "",
    val affectedCount: Int = 0,
    val status: RuleStatus = RuleStatus.ACTIVE
)

@Entity(tableName = "folder_rules")
data class FolderRule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val listKind: RuleListKind,
    val ruleType: RuleType, // MUSIC_FOLDER, VIDEO_FOLDER, or FOLDER
    val treeUri: String,
    val documentId: String,
    val displayName: String,
    val displayPath: String,
    val includeSubfolders: Boolean = true,
    val affectedMediaCount: Int = 0,
    val subfolderCount: Int = 0,
    val creationDate: RuleCreationDate = RuleCreationDate(System.currentTimeMillis()),
    val status: RuleStatus = RuleStatus.ACTIVE,
    val lastScannedAt: Long = 0L,
    val lastError: String = ""
)

@Entity(tableName = "folder_permissions")
data class FolderPermission(
    @PrimaryKey val treeUri: String,
    val documentId: String,
    val displayName: String,
    val hasReadPermission: Boolean = true,
    val hasWritePermission: Boolean = false,
    val isPersisted: Boolean = true,
    val status: RuleStatus = RuleStatus.ACTIVE,
    val grantedAt: RuleCreationDate = RuleCreationDate(System.currentTimeMillis()),
    val lastVerifiedAt: Long = System.currentTimeMillis(),
    val errorMessage: String = ""
)

@Entity(tableName = "album_artwork_entries")
data class AlbumArtworkEntry(
    @PrimaryKey val albumKey: String,
    val artworkPath: String,
    val isCustom: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "lyrics_metadata_entries")
data class LyricsMetadataEntry(
    @PrimaryKey val trackId: Long,
    val lyricsPath: String,
    val customOffsetMs: Long = 0L,
    val isManual: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)

data class LyricsLine(
    val timestampMs: Long,
    val text: String
)

enum class PlayerStyle(val displayName: String, val description: String) {
    CLASSIC("Classic", "Vinyl & CD Turntable with tactile analog controls"),
    GESTURE("Gesture", "Minimalist layout with intuitive swipe gestures"),
    EDITORIAL("Editorial", "Magazine-style typography and quote lyrics"),
    CANVAS("Canvas", "Full-bleed artwork with ambient blur aura"),
    BENTO("Bento", "Modern modular grid cards for visual metrics"),
    STICKER("Sticker", "Playful retro pop badges and cassette vibe"),
    MORPH("Morph", "Futuristic fluid neon orb visualizer"),
    DIAL("Dial", "Audiophile rotary volume dial & analog VU meter"),
    HERO("Hero", "Cinematic widescreen hero layout with dynamic wave")
}
