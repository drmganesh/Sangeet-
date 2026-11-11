package com.example.sangeet.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

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
    val lrcPath: String = ""
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
    val inWatchLater: Boolean = false
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
    val bandsGainJson: String, // JSON array of 15 float gain values in dB
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
    val rotationDirection: Int = 2, // 0: Left-to-Right, 1: Right-to-Left, 2: Circular 360
    val soundstageWidth: Float = 1.2f,
    val soundstageDepth: Float = 0.7f,
    val centerPosition: Float = 0.0f
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
